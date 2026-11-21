package dev.gateworks.trigger;

import dev.gateworks.GateWorksPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

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

    /** Runs a one-shot diagnostic without changing listener health, logical state, or regions. */
    public DiagnosticResult testOnce(String id) {
        String base = "triggers." + id + ".listener.";
        String command = plugin.getConfig().getString(base + "command", "").trim();
        String stateA = plugin.getConfig().getString(base + "state-a", "").trim();
        String stateB = plugin.getConfig().getString(base + "state-b", "").trim();
        if (command.isEmpty()) return new DiagnosticResult("", "", stateA, stateB, "No monitoring command is configured.");
        String response;
        try {
            response = executeAndCapture(command);
        } catch (Throwable error) {
            response = "";
            return new DiagnosticResult(command, response, stateA, stateB,
                    "Command execution threw " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
        }
        String actual = response == null ? "" : response.trim();
        String match = !actual.isEmpty() && !stateA.isEmpty() && actual.equalsIgnoreCase(stateA) ? "MATCHED STATE A"
                : !actual.isEmpty() && !stateB.isEmpty() && actual.equalsIgnoreCase(stateB) ? "MATCHED STATE B"
                : "MATCHED NEITHER";
        return new DiagnosticResult(command, actual, stateA, stateB, match);
    }

    public record DiagnosticResult(String command, String response, String stateA, String stateB, String result) {}

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

    /**
     * Reads vanilla scoreboard-get commands directly from the main scoreboard.
     *
     * Minecraft command feedback is presentation output and can be suppressed or
     * routed elsewhere by server/game-rule settings. The score itself is the
     * authoritative value, so this path avoids depending on chat feedback.
     *
     * Returns null when the command is not a simple scoreboard players get query,
     * or when the objective/entry has no stored score. Other commands continue
     * through Paper's feedback-capturing command sender below.
     */
    private String readScoreboardGet(String command) {
        String normalized = command.startsWith("/") ? command.substring(1).trim() : command.trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length != 5
                || !(parts[0].equalsIgnoreCase("scoreboard") || parts[0].equalsIgnoreCase("minecraft:scoreboard"))
                || !parts[1].equalsIgnoreCase("players")
                || !parts[2].equalsIgnoreCase("get")) {
            return null;
        }

        var manager = Bukkit.getScoreboardManager();
        if (manager == null) return null;
        var objective = manager.getMainScoreboard().getObjective(parts[4]);
        if (objective == null) return null;

        var score = objective.getScore(parts[3]);
        if (!score.isScoreSet()) return null;

        String displayName = PlainTextComponentSerializer.plainText().serialize(objective.displayName());
        return parts[3] + " has " + score.getScore() + " [" + displayName + "]";
    }

    /**
     * Paper's supported feedback sender captures command messages without implementing
     * CommandSender ourselves. This remains the fallback for general monitoring commands.
     */
    private String executeAndCapture(String command) {
        String normalized = command.startsWith("/") ? command.substring(1).trim() : command;
        if (normalized.isEmpty()) return "";

        String scoreboardResponse = readScoreboardGet(normalized);
        if (scoreboardResponse != null) return scoreboardResponse;

        StringBuilder captured = new StringBuilder();
        CommandSender sender = Bukkit.createCommandSender((Component message) -> {
            String plain = PlainTextComponentSerializer.plainText().serialize(message);
            if (plain == null || plain.isBlank()) return;
            if (!captured.isEmpty()) captured.append('\n');
            captured.append(plain);
        });

        Bukkit.dispatchCommand(sender, normalized);
        return captured.toString();
    }
}
