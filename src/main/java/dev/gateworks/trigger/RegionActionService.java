package dev.gateworks.trigger;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/** Applies the configured two-material region state for one trigger. */
public final class RegionActionService {
    private final JavaPlugin plugin;

    public RegionActionService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private record SelectedBlock(World world, int x, int y, int z) { }

    /**
     * Nether portal axis follows the long direction of the selected plane.
     * A vertical plane with fixed X runs along Z; one with fixed Z runs along X.
     * For a volumetric selection, the greater horizontal dimension wins.
     * Equal or otherwise ambiguous dimensions consistently default to X.
     */
    private Axis determinePortalAxis(int sizeX, int sizeY, int sizeZ) {
        if (sizeX == 1 && sizeZ > 1) return Axis.Z;
        if (sizeZ == 1 && sizeX > 1) return Axis.X;
        return sizeX > sizeZ ? Axis.X : Axis.Z;
    }

    /**
     * Replaces every saved selected position with the target material.
     * Returns false if configuration or any saved coordinate is invalid.
     */
    public boolean apply(String triggerId, boolean secondary) {
        String base = "triggers." + triggerId + ".";
        String materialKey = secondary ? "secondary-material" : "primary-material";
        String materialName = plugin.getConfig().getString(base + "region." + materialKey, "");
        Material target = Material.matchMaterial(materialName == null ? "" : materialName);
        if (target == null || (!target.isBlock() && target != Material.AIR)) {
            plugin.getLogger().warning("Region action for trigger " + triggerId
                    + " was not applied: " + materialKey + " is not configured as a valid block.");
            return false;
        }

        List<String> entries = plugin.getConfig().getStringList(base + "selection");
        if (entries.isEmpty()) {
            plugin.getLogger().warning("Region action for trigger " + triggerId + " has no saved positions.");
            return false;
        }

        boolean valid = true;
        int changed = 0;
        List<SelectedBlock> positions = new java.util.ArrayList<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        UUID selectedWorld = null;

        // Parse the complete selection first so Nether portal orientation is
        // determined from the region as a whole, not click or save order.
        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            if (parts.length != 5) {
                valid = false;
                continue;
            }
            try {
                UUID worldId = UUID.fromString(parts[1]);
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                int z = Integer.parseInt(parts[4]);
                World world = Bukkit.getWorld(worldId);
                if (world == null || (selectedWorld != null && !selectedWorld.equals(worldId))) {
                    valid = false;
                    continue;
                }
                selectedWorld = worldId;
                positions.add(new SelectedBlock(world, x, y, z));
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            } catch (IllegalArgumentException ex) {
                valid = false;
            }
        }

        Axis portalAxis = target == Material.NETHER_PORTAL && !positions.isEmpty()
                ? determinePortalAxis(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1)
                : null;

        for (SelectedBlock position : positions) {
            try {
                Block block = position.world().getBlockAt(position.x(), position.y(), position.z());
                boolean blockChanged = false;
                if (block.getType() != target) {
                    block.setType(target, false);
                    blockChanged = true;
                }
                if (portalAxis != null && block.getBlockData() instanceof Orientable orientable
                        && orientable.getAxis() != portalAxis) {
                    orientable.setAxis(portalAxis);
                    block.setBlockData(orientable, false);
                    blockChanged = true;
                }
                if (blockChanged) changed++;

                // GateWorks uses the maximum built-in Light Block level by default.
                if (target == Material.LIGHT
                        && block.getBlockData() instanceof org.bukkit.block.data.type.Light light
                        && light.getLevel() != 15) {
                    light.setLevel(15);
                    block.setBlockData(light, false);
                    changed++;
                }
            } catch (RuntimeException ex) {
                valid = false;
                plugin.getLogger().warning("Could not replace a selected block for trigger " + triggerId
                        + ": " + ex.getMessage());
            }
        }
        if (!valid) {
            plugin.getLogger().warning("Region action for trigger " + triggerId
                    + " completed with one or more invalid or unavailable positions.");
        } else {
            plugin.getLogger().info("Region action for trigger " + triggerId + " applied "
                    + target.name() + " to " + entries.size() + " selected positions ("
                    + changed + " changed).");
        }
        return valid;
    }
}
