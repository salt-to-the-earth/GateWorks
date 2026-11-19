package dev.gateworks.visual;

import dev.gateworks.selection.BlockPosition;
import dev.gateworks.selection.SelectionService;
import dev.gateworks.selection.SelectionSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;

/**
 * Renders per-player selection feedback.
 *
 * The outer group outline communicates the region bounds. A marker at the
 * center of every selected block communicates actual membership, so removing
 * a block from the middle of a cuboid is immediately visible as a missing marker.
 * No world blocks are changed and particles are sent only to the selecting player.
 */
public final class SelectionVisualizer {
    private static final long REFRESH_TICKS = 10L;
    private static final double PARTICLE_SPACING = 0.5;
    private static final int MAX_POINTS_PER_EDGE = 400;
    private static final int MAX_PARTICLES_PER_PLAYER_PER_REFRESH = 24000;

    private final JavaPlugin plugin;
    private final SelectionService selections;
    private BukkitTask task;

    public SelectionVisualizer(JavaPlugin plugin, SelectionService selections) {
        this.plugin = plugin;
        this.selections = selections;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::renderOnlinePlayers, 1L, REFRESH_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void renderOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("gateworks.admin")) continue;
            SelectionSession session = selections.get(player.getUniqueId());
            if (session.getBlockCount() == 0) continue;

            int[] emitted = {0};
            for (Set<BlockPosition> group : session.getGroupsSnapshot()) {
                if (emitted[0] >= MAX_PARTICLES_PER_PLAYER_PER_REFRESH) break;
                renderGroup(player, group, emitted);
            }
        }
    }

    private void renderGroup(Player player, Set<BlockPosition> group, int[] emitted) {
        if (group.isEmpty()) return;
        BlockPosition first = group.iterator().next();
        World world = Bukkit.getWorld(first.worldId());
        if (world == null || !player.getWorld().getUID().equals(world.getUID())) return;

        // Outline every selected block individually. This deliberately draws
        // internal edges as well as the outside boundary so removed interior
        // blocks leave a clearly visible gap.
        for (BlockPosition block : group) {
            if (emitted[0] >= MAX_PARTICLES_PER_PLAYER_PER_REFRESH) return;
            double x0 = block.x(), y0 = block.y(), z0 = block.z();
            double x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
            line(player, world, x0, y0, z0, x1, y0, z0, emitted);
            line(player, world, x0, y0, z1, x1, y0, z1, emitted);
            line(player, world, x0, y1, z0, x1, y1, z0, emitted);
            line(player, world, x0, y1, z1, x1, y1, z1, emitted);
            line(player, world, x0, y0, z0, x0, y1, z0, emitted);
            line(player, world, x1, y0, z0, x1, y1, z0, emitted);
            line(player, world, x0, y0, z1, x0, y1, z1, emitted);
            line(player, world, x1, y0, z1, x1, y1, z1, emitted);
            line(player, world, x0, y0, z0, x0, y0, z1, emitted);
            line(player, world, x1, y0, z0, x1, y0, z1, emitted);
            line(player, world, x0, y1, z0, x0, y1, z1, emitted);
            line(player, world, x1, y1, z0, x1, y1, z1, emitted);
        }
    }

    private void line(Player player, World world,
                      double x0, double y0, double z0,
                      double x1, double y1, double z1, int[] emitted) {
        if (emitted[0] >= MAX_PARTICLES_PER_PLAYER_PER_REFRESH) return;
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, Math.min(MAX_POINTS_PER_EDGE, (int) Math.ceil(length / PARTICLE_SPACING)));
        for (int i = 0; i <= steps && emitted[0] < MAX_PARTICLES_PER_PLAYER_PER_REFRESH; i++) {
            double t = (double) i / steps;
            Location point = new Location(world, x0 + dx * t, y0 + dy * t, z0 + dz * t);
            player.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
            emitted[0]++;
        }
    }
}
