package dev.gateworks.trigger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * First activation engine for configured physical trigger devices.
 *
 * Commands run as the console. Pulse devices are activated on their rising
 * edge; state devices are processed on either physical state transition.
 */
public final class TriggerActivationListener implements Listener {
    private final JavaPlugin plugin;
    private final Map<String, Long> lastAccepted = new HashMap<>();
    private final Map<String, Long> lockouts = new HashMap<>();
    private final Map<String, Map<UUID, AttemptWindow>> attempts = new HashMap<>();

    public TriggerActivationListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Synchronizes state without executing actions. Safe to call on startup. */
    public void initializeExistingTriggers() {
        ConfigurationSection triggers = plugin.getConfig().getConfigurationSection("triggers");
        if (triggers == null) return;
        for (String id : triggers.getKeys(false)) {
            String base = "triggers." + id + ".";
            Block block = configuredBlock(base);
            if (block == null) continue;
            boolean powered = readPowered(block);
            if (!plugin.getConfig().contains(base + "action.mode")) {
                plugin.getConfig().set(base + "action.mode", isStateBased(block.getType()) ? "TOGGLE" : "SINGLE");
            }
            if (plugin.getConfig().getInt(base + "selection-blocks", 0) > 0) {
                plugin.getConfig().set(base + "action.mode", "TOGGLE");
            }
            if (!plugin.getConfig().contains(base + "action.single-command")) {
                plugin.getConfig().set(base + "action.single-command", "say GateWorks says hello!");
            }
            if (!plugin.getConfig().contains(base + "action.on-command")) {
                plugin.getConfig().set(base + "action.on-command", "say GateWorks is ON!");
            }
            if (!plugin.getConfig().contains(base + "action.off-command")) {
                plugin.getConfig().set(base + "action.off-command", "say GateWorks is OFF!");
            }
            plugin.getConfig().set(base + "protection.enabled", plugin.getConfig().getBoolean(base + "protection.enabled", true));
            plugin.getConfig().set(base + "protection.cooldown-ms", plugin.getConfig().getLong(base + "protection.cooldown-ms", 1000L));
            plugin.getConfig().set(base + "protection.spam-threshold", plugin.getConfig().getInt(base + "protection.spam-threshold", 5));
            plugin.getConfig().set(base + "protection.spam-window-seconds", plugin.getConfig().getLong(base + "protection.spam-window-seconds", 10L));
            plugin.getConfig().set(base + "protection.lockout-seconds", plugin.getConfig().getLong(base + "protection.lockout-seconds", 15L));
            plugin.getConfig().set(base + "protection.notify-player", plugin.getConfig().getBoolean(base + "protection.notify-player", true));
            plugin.getConfig().set(base + "runtime.observed-state", powered);
            if (isStateBased(block.getType())
                    && "TOGGLE".equalsIgnoreCase(plugin.getConfig().getString(base + "action.mode", "SINGLE"))) {
                plugin.getConfig().set(base + "runtime.logical-state", powered);
            } else if (!plugin.getConfig().contains(base + "runtime.logical-state")) {
                plugin.getConfig().set(base + "runtime.logical-state", false);
            }
        }
        plugin.saveConfig();
    }

    @EventHandler
    public void onRedstoneChange(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        String id = findTrigger(block);
        if (id == null) return;

        boolean wasPowered = event.getOldCurrent() > 0;
        boolean isPowered = event.getNewCurrent() > 0;
        if (wasPowered == isPowered) return;

        String base = "triggers." + id + ".";
        boolean pulse = !isStateBased(block.getType());
        // A pulse is one activation on its rising edge. Its release is not a
        // second activation. State devices report both ON and OFF transitions.
        if (pulse && !isPowered) {
            plugin.getConfig().set(base + "runtime.observed-state", false);
            return;
        }

        plugin.getConfig().set(base + "runtime.observed-state", isPowered);
        Player actor = nearestPlayer(block);
        if (!acceptActivation(id, base, actor)) return;

        String mode = plugin.getConfig().getString(base + "action.mode", "SINGLE");
        String single = clean(plugin.getConfig().getString(base + "action.single-command", ""));
        String on = clean(plugin.getConfig().getString(base + "action.on-command", ""));
        String off = clean(plugin.getConfig().getString(base + "action.off-command", ""));

        if ("TOGGLE".equalsIgnoreCase(mode) && !on.isEmpty() && !off.isEmpty()) {
            boolean nextState;
            if (isStateBased(block.getType())) {
                // Physical ON/OFF state is authoritative for state-based devices.
                nextState = isPowered;
            } else {
                nextState = !plugin.getConfig().getBoolean(base + "runtime.logical-state", false);
            }
            plugin.getConfig().set(base + "runtime.logical-state", nextState);
            dispatch(nextState ? on : off);
        } else {
            // With only one populated ON/OFF command, it behaves as a single
            // command on every activation. The dedicated single command is
            // used only while the trigger is in SINGLE mode.
            String command;
            if ("TOGGLE".equalsIgnoreCase(mode)) {
                if (!on.isEmpty() && off.isEmpty()) command = on;
                else if (on.isEmpty() && !off.isEmpty()) command = off;
                else command = "";
            } else {
                command = single;
            }
            if (!command.isEmpty()) dispatch(command);
        }
        plugin.saveConfig();
    }

    private boolean acceptActivation(String id, String base, Player actor) {
        if (!plugin.getConfig().getBoolean(base + "protection.enabled", true)) return true;
        long now = System.currentTimeMillis();
        long lockoutUntil = lockouts.getOrDefault(id, 0L);
        if (now < lockoutUntil) {
            notifyActor(actor, base, "This control is temporarily locked because of repeated activation.");
            recordAttempt(id, actor, now, base);
            return false;
        }

        recordAttempt(id, actor, now, base);
        long cooldown = Math.max(0L, plugin.getConfig().getLong(base + "protection.cooldown-ms", 1000L));
        long previous = lastAccepted.getOrDefault(id, 0L);
        if (now - previous < cooldown) {
            notifyActor(actor, base, "Please wait before activating this control again.");
            return false;
        }
        lastAccepted.put(id, now);
        return true;
    }

    private void recordAttempt(String id, Player actor, long now, String base) {
        if (actor == null) return;
        int threshold = Math.max(2, plugin.getConfig().getInt(base + "protection.spam-threshold", 5));
        long windowMs = Math.max(1000L,
                plugin.getConfig().getLong(base + "protection.spam-window-seconds", 10L) * 1000L);
        Map<UUID, AttemptWindow> byPlayer = attempts.computeIfAbsent(id, ignored -> new HashMap<>());
        AttemptWindow window = byPlayer.get(actor.getUniqueId());
        if (window == null || now - window.startedAt > windowMs) {
            window = new AttemptWindow(now, 0);
            byPlayer.put(actor.getUniqueId(), window);
        }
        window.count++;
        if (window.count >= threshold) {
            long lockoutMs = Math.max(1L,
                    plugin.getConfig().getLong(base + "protection.lockout-seconds", 15L)) * 1000L;
            lockouts.put(id, now + lockoutMs);
            byPlayer.remove(actor.getUniqueId());
            notifyActor(actor, base, "GateWorks has temporarily locked this control due to repeated activation.");
        }
    }

    private void dispatch(String command) {
        String normalized = command.startsWith("/") ? command.substring(1).trim() : command.trim();
        if (!normalized.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), normalized);
    }

    private String findTrigger(Block block) {
        ConfigurationSection triggers = plugin.getConfig().getConfigurationSection("triggers");
        if (triggers == null) return null;
        for (String id : triggers.getKeys(false)) {
            String base = "triggers." + id + ".device.";
            if (!block.getWorld().getUID().toString().equals(plugin.getConfig().getString(base + "world"))) continue;
            if (block.getX() == plugin.getConfig().getInt(base + "x")
                    && block.getY() == plugin.getConfig().getInt(base + "y")
                    && block.getZ() == plugin.getConfig().getInt(base + "z")) {
                String expected = plugin.getConfig().getString(base + "material");
                if (expected == null || expected.equals(block.getType().name())) return id;
            }
        }
        return null;
    }

    private Block configuredBlock(String base) {
        String worldId = plugin.getConfig().getString(base + "device.world");
        if (worldId == null) return null;
        try {
            var world = Bukkit.getWorld(UUID.fromString(worldId));
            if (world == null) return null;
            Block block = world.getBlockAt(plugin.getConfig().getInt(base + "device.x"),
                    plugin.getConfig().getInt(base + "device.y"),
                    plugin.getConfig().getInt(base + "device.z"));
            String expected = plugin.getConfig().getString(base + "device.material");
            return expected == null || expected.equals(block.getType().name()) ? block : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean readPowered(Block block) {
        if (block.getBlockData() instanceof Powerable powerable) return powerable.isPowered();
        return block.getBlockPower() > 0;
    }

    private boolean isStateBased(Material material) {
        String name = material.name();
        return material == Material.LEVER || name.endsWith("_PRESSURE_PLATE")
                || material == Material.TRIPWIRE_HOOK;
    }

    private Player nearestPlayer(Block block) {
        return block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 6, 6, 6)
                .stream().filter(Player.class::isInstance).map(Player.class::cast)
                .min((a, b) -> Double.compare(a.getLocation().distanceSquared(block.getLocation()),
                        b.getLocation().distanceSquared(block.getLocation()))).orElse(null);
    }

    private void notifyActor(Player actor, String base, String message) {
        if (actor != null && plugin.getConfig().getBoolean(base + "protection.notify-player", true)) {
            actor.sendMessage("§6[GateWorks] §e" + message);
        }
    }

    private String clean(String command) {
        return command == null ? "" : command.trim();
    }

    private static final class AttemptWindow {
        private final long startedAt;
        private int count;
        private AttemptWindow(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}
