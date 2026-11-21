package dev.gateworks.trigger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
/** Explicit optional compatibility mappings verified against Advanced Portals 2.8.0. */
final class AdvancedPortalsMaterialDiscovery {
    private static final Set<Material> MATERIALS = Set.copyOf(EnumSet.of(Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY));
    private AdvancedPortalsMaterialDiscovery() {}
    static boolean isAvailable() {
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            String normalized = candidate.getName().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
            if (normalized.equals("advancedportals") && candidate.isEnabled()) return true;
        }
        return false;
    }
    static Set<Material> materials() { return MATERIALS; }
    /** Recreates the item material, display name and lore used by AP 2.8.0's placer commands. */
    static ItemStack createDisplayItem(Material target) {
        Material itemMaterial; String displayName; List<String> lore;
        switch (target) {
            case NETHER_PORTAL -> { itemMaterial = Material.PURPLE_WOOL; displayName = "§5Portal Block Placer";
                lore = List.of("§r§7This wool is made of a magical substance", "§r§eLeft Click§7: Rotate portal block", "§r§eRight Click§7: Place portal block"); }
            case END_PORTAL -> { itemMaterial = Material.BLACK_WOOL; displayName = "§8End Portal Block Placer";
                lore = List.of("§r§7This wool is made of a magical substance", "§r§eRight Click§7: Place portal block"); }
            case END_GATEWAY -> { itemMaterial = Material.BLACK_WOOL; displayName = "§8Gateway Block Placer";
                lore = List.of("§r§7This wool is made of a magical substance", "§r§eRight Click§7: Place portal block"); }
            default -> { return new ItemStack(target.isItem() ? target : Material.BARRIER); }
        }
        ItemStack item = new ItemStack(itemMaterial); ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(displayName); meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }
}
