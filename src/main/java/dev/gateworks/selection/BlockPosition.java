package dev.gateworks.selection;

import java.util.UUID;

/** A world-qualified block coordinate without a live Bukkit object reference. */
public record BlockPosition(UUID worldId, int x, int y, int z) {
}
