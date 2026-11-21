package dev.gateworks.trigger;

import dev.gateworks.GateWorksPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Polls an administrator-defined command and synchronizes only confirmed states. */
public final class StatusListenerService {
    private static final String GENERIC_ERROR = "Monitoring command failed or returned an unrecognized response.";
    private final GateWorksPlugin plugin;
    private final RegionActionService regions;
    private final Map<String, Long> nextPoll = new HashMap<>();

    public StatusListenerService(GateWorksPlugin plugin) {
        this.plugin = plugin;
        this.regions = new RegionActionService(plugin);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        ConfigurationSection triggers = plugin.getConfig().getConfigurationSection("triggers");
        if (triggers == null) return;
        long now = System.currentTimeMillis();
        for (String id : triggers.getKeys(false)) {
            String base = "triggers." + id + ".listener.";
            String command = plugin.getConfig().getString(base + "command", "").trim();
            if (!plugin.getConfig().getBoolean(base + "enabled", false) || command.isEmpty()) continue;
            long interval = Math.max(1, Math.min(3600, plugin.getConfig().getLong(base + "interval-seconds", 5))) * 1000L;
            if (now < nextPoll.getOrDefault(id, 0L)) continue;
            nextPoll.put(id, now + interval);
            check(id, command, base);
        }
    }

    private void check(String id, String command, String base) {
        String response;
        try {
            response = executeAndCapture(command);
        } catch (Throwable error) {
            response = "";
        }
        String stateA = plugin.getConfig().getString(base + "state-a", "0").trim();
        String stateB = plugin.getConfig().getString(base + "state-b", "1").trim();
        String actual = response == null ? "" : response.trim();
        // The vanilla scoreboard command reports a sentence ending in "is <score>".
        // Normalize that standard response so the configured 0/1 values can be used.
        java.util.regex.Matcher scoreboard = java.util.regex.Pattern
                .compile("(?i)^Score of .+ for objective .+ is (-?\\d+)\\s*$")
                .matcher(actual);
        if (scoreboard.matches()) actual = scoreboard.group(1);
        boolean confirmed;
        boolean secondary;
        if (!actual.isEmpty() && !stateA.isEmpty() && actual.equalsIgnoreCase(stateA)) {
            confirmed = true;
            secondary = false;
        } else if (!actual.isEmpty() && !stateB.isEmpty() && actual.equalsIgnoreCase(stateB)) {
            confirmed = true;
            secondary = true;
        } else {
            confirmed = false;
            secondary = false;
        }

        plugin.getConfig().set(base + "last-checked", System.currentTimeMillis());
        if (!confirmed) {
            String detail = actual.isEmpty() ? GENERIC_ERROR : "Unrecognized monitoring response: " + actual;
            plugin.getConfig().set(base + "status", "OFFLINE");
            plugin.getConfig().set(base + "last-error", detail.length() > 240 ? detail.substring(0, 237) + "..." : detail);
            plugin.saveConfig();
            return;
        }

        boolean firstConfirmation = !plugin.getConfig().contains(base + "last-confirmed-state");
        boolean previous = plugin.getConfig().getBoolean(base + "last-confirmed-state", false);
        plugin.getConfig().set(base + "status", "ONLINE");
        plugin.getConfig().set(base + "last-error", "");
        plugin.getConfig().set(base + "last-confirmed-state", secondary);
        plugin.getConfig().set("triggers." + id + ".runtime.logical-state", secondary);
        if (firstConfirmation || previous != secondary) {
            if (plugin.getConfig().getInt("triggers." + id + ".selection-blocks", 0) > 0
                    && regions.apply(id, secondary)) {
                plugin.getConfig().set("triggers." + id + ".region.runtime-secondary", secondary);
            }
        }
        plugin.saveConfig();
    }

    private String executeAndCapture(String command) {
        String normalized = command.startsWith("/") ? command.substring(1).trim() : command;
        if (normalized.isEmpty()) return "";
        StringBuilder captured = new StringBuilder();
        CommandSender console = Bukkit.getConsoleSender();
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "GateWorks monitoring console";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if (method.getName().startsWith("sendMessage") && args != null) {
                        for (Object arg : args) {
                            String message = null;
                            if (arg instanceof Component component) {
                                message = PlainTextComponentSerializer.plainText().serialize(component);
                            } else if (arg instanceof String string) {
                                message = string;
                            } else if (arg instanceof Component[] components) {
                                for (Component component : components) {
                                    if (!captured.isEmpty()) captured.append('\n');
                                    captured.append(PlainTextComponentSerializer.plainText().serialize(component));
                                }
                            }
                            if (message != null && !message.isBlank()) {
                                if (!captured.isEmpty()) captured.append('\n');
                                captured.append(message);
                            }
                        }
                        return null;
                    }
                    try {
                        return method.invoke(console, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
        Bukkit.dispatchCommand(sender, normalized);
        return captured.toString();
    }
}
