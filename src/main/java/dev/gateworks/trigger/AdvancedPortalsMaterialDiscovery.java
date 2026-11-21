package dev.gateworks.trigger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Discovers Advanced Portals block materials only when the administrator opens
 * the material selector. It asks the registered command for tab suggestions;
 * no command is dispatched or executed.
 */
final class AdvancedPortalsMaterialDiscovery {
    private AdvancedPortalsMaterialDiscovery() {
    }

    static Set<Material> discover(Player player) {
        Plugin advancedPortals = findAdvancedPortals();
        if (advancedPortals == null) return Set.of();

        Command portalCommand = Bukkit.getCommandMap().getCommand("portal");
        if (portalCommand == null || !(portalCommand instanceof PluginIdentifiableCommand identifiable)
                || !identifiable.getPlugin().equals(advancedPortals)) {
            return Set.of();
        }

        try {
            // Invoke the registered /portal command completer directly with
            // an empty first argument, matching Tab after "/portal ".
            // CommandMap.tabComplete(commandLine) can lose a trailing empty
            // argument during server-side tokenization.
            List<String> suggestions = portalCommand.tabComplete(player, "portal", new String[]{""});
            if (suggestions == null || suggestions.isEmpty()) {
                advancedPortals.getLogger().fine("GateWorks found Advanced Portals but /portal returned no suggestions.");
                return Set.of();
            }

            Set<Material> discovered = EnumSet.noneOf(Material.class);
            for (String suggestion : suggestions) {
                Material material = materialFromSuggestion(suggestion);
                if (BlockSelectionMenu.isSelectableMaterial(material)) discovered.add(material);
            }
            return Set.copyOf(discovered);
        } catch (RuntimeException exception) {
            advancedPortals.getLogger().fine("GateWorks could not read Advanced Portals command suggestions: "
                    + exception.getMessage());
            return Set.of();
        }
    }

    private static Plugin findAdvancedPortals() {
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            String normalized = candidate.getName().replaceAll("[^A-Za-z0-9]", "")
                    .toLowerCase(Locale.ROOT);
            if (normalized.equals("advancedportals") && candidate.isEnabled()) return candidate;
        }
        return null;
    }

    static Material materialFromSuggestion(String suggestion) {
        if (suggestion == null || suggestion.isBlank()) return null;

        String[] words = suggestion.trim().split("\\s+");
        String token = words[words.length - 1].toLowerCase(Locale.ROOT);
        while (token.startsWith("/")) token = token.substring(1);
        if (!token.endsWith("block") || token.length() <= "block".length()) return null;

        String materialName = token.substring(0, token.length() - "block".length())
                .replaceAll("[^a-z0-9]", "");

        // These Advanced Portals command names are meaningful aliases rather
        // than literal Bukkit Material names.
        if (materialName.equals("portal")) return Material.NETHER_PORTAL;
        if (materialName.equals("gateway")) return Material.END_GATEWAY;

        for (Material material : Material.values()) {
            String compactName = material.name().replace("_", "").toLowerCase(Locale.ROOT);
            if (compactName.equals(materialName)) return material;
        }
        return null;
    }
}
