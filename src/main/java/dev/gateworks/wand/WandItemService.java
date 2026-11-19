package dev.gateworks.wand;

import dev.gateworks.GateWorksPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Creates and identifies the GateWorks selection wand. */
public final class WandItemService {
    private final NamespacedKey wandKey;
    public WandItemService(GateWorksPlugin plugin) {
        wandKey = new NamespacedKey(plugin, "selection_wand");
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("GateWorks Selection Wand", NamedTextColor.GOLD));
        meta.lore(java.util.List.of(
                Component.text("Left-click: add a block", NamedTextColor.GRAY),
                Component.text("Shift + left-click: add a cuboid", NamedTextColor.GRAY),
                Component.text("Right-click: remove a block", NamedTextColor.GRAY),
                Component.text("Shift + right-click: remove its group", NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(wandKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }
}
