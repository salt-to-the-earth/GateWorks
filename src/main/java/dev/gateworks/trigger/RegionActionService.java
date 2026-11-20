package dev.gateworks.trigger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/** Applies the configured two-material region state for one trigger. */
public final class RegionActionService {
    private final JavaPlugin plugin;

    public RegionActionService(JavaPlugin plugin) {
        this.plugin = plugin;
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
        if (target == null || !target.isBlock() || !target.isSolid()) {
            plugin.getLogger().warning("Region action for trigger " + triggerId
                    + " was not applied: " + materialKey + " is not configured as a valid solid block.");
            return false;
        }

        List<String> entries = plugin.getConfig().getStringList(base + "selection");
        if (entries.isEmpty()) {
            plugin.getLogger().warning("Region action for trigger " + triggerId + " has no saved positions.");
            return false;
        }

        boolean valid = true;
        int changed = 0;
        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            if (parts.length != 5) {
                valid = false;
                continue;
            }
            try {
                World world = Bukkit.getWorld(UUID.fromString(parts[1]));
                if (world == null) {
                    valid = false;
                    continue;
                }
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                int z = Integer.parseInt(parts[4]);
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() != target) {
                    block.setType(target, false);
                    changed++;
                }
            } catch (IllegalArgumentException ex) {
                valid = false;
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
