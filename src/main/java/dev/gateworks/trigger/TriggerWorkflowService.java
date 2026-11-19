package dev.gateworks.trigger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import dev.gateworks.GateWorksPlugin;
import dev.gateworks.selection.SelectionService;
import dev.gateworks.selection.BlockPosition;
import java.util.*;

/** First-stage book workflow and server-side trigger registry. */
public final class TriggerWorkflowService {
    private final GateWorksPlugin plugin;
    private final SelectionService selections;
    private final NamespacedKey bookType;
    private final NamespacedKey bookId;
    private final Map<UUID, String> naming = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, String> awaitingButton = new HashMap<>();

    public TriggerWorkflowService(GateWorksPlugin plugin, SelectionService selections) {
        this.plugin = plugin;
        this.selections = selections;
        this.bookType = new NamespacedKey(plugin, "book_type");
        this.bookId = new NamespacedKey(plugin, "book_id");
    }

    public void refreshSelectionBook(Player player) {
        ItemStack book = findBook(player, "selection");
        boolean newBook = book == null;
        if (newBook) book = new ItemStack(Material.WRITTEN_BOOK);
        ItemStack target = book;
        target.editMeta(meta -> {
            BookMeta bm = (BookMeta) meta;
            bm.title(Component.text("GateWorks Selection"));
            bm.author(Component.text("GateWorks"));
            bm.pages(selectionPages(player));
            bm.getPersistentDataContainer().set(bookType, PersistentDataType.STRING, "selection");
            bm.getPersistentDataContainer().set(bookId, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        if (newBook) player.getInventory().addItem(book).values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private List<Component> selectionPages(Player player) {
        var session = selections.get(player.getUniqueId());
        List<Component> pages = new ArrayList<>();
        pages.add(Component.text("GATEWORKS\nSelection Workspace\n\n", NamedTextColor.DARK_AQUA)
                .append(Component.text("Blocks: ", NamedTextColor.BLACK))
                .append(Component.text(String.valueOf(session.getBlockCount()), NamedTextColor.DARK_GREEN))
                .append(Component.text("\nGroups: ", NamedTextColor.BLACK))
                .append(Component.text(String.valueOf(session.getGroupCount()), NamedTextColor.DARK_GREEN))
                .append(Component.text("\n\n", NamedTextColor.BLACK))
                .append(link("Refresh selection", "/gateworks book refresh"))
                .append(Component.text("\n\n", NamedTextColor.BLACK))
                .append(link("Create a new trigger", "/gateworks book name"))
                .append(Component.text("\n\nExisting triggers:\n", NamedTextColor.BLACK)));
        int count = 0;
        for (String id : triggerIds()) {
            if (count++ >= 12) break;
            String name = plugin.getConfig().getString("triggers." + id + ".name", "Unnamed");
            pages.add(Component.text(name + "\n", NamedTextColor.DARK_AQUA)
                    .append(link("Attach this selection", "/gateworks book attach " + id)));
        }
        if (count == 0) pages.add(Component.text("No triggers have been created yet.", NamedTextColor.GRAY));
        return pages;
    }

    public void promptForName(Player player) {
        naming.put(player.getUniqueId(), "new");
        player.closeInventory();
        player.sendMessage(Component.text("[GateWorks] Type the new trigger name in chat. Type cancel to stop.", NamedTextColor.GOLD));
    }

    public void acceptName(Player player, String rawName) {
        if (!naming.containsKey(player.getUniqueId())) return;
        naming.remove(player.getUniqueId());
        if (rawName.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("Trigger creation cancelled.", NamedTextColor.YELLOW));
            return;
        }
        String name = rawName.trim();
        if (name.isEmpty() || name.length() > 32) {
            player.sendMessage(Component.text("Trigger names must be between 1 and 32 characters. Please try again with /gateworks book name.", NamedTextColor.RED));
            return;
        }
        String id = UUID.randomUUID().toString();
        plugin.getConfig().set("triggers." + id + ".name", name);
        plugin.getConfig().set("triggers." + id + ".created-by", player.getUniqueId().toString());
        plugin.getConfig().set("triggers." + id + ".selection-blocks", selections.get(player.getUniqueId()).getBlockCount());
        plugin.saveConfig();
        removeSelectionBook(player);
        giveTriggerBook(player, id);
        player.sendMessage(Component.text("Created trigger '" + name + "'. Its permanent Trigger Book is in your inventory.", NamedTextColor.GREEN));
    }

    public boolean isNaming(UUID playerId) { return naming.containsKey(playerId); }

    public boolean attachSelection(Player player, String id) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return false;
        plugin.getConfig().set("triggers." + id + ".selection-blocks", selections.get(player.getUniqueId()).getBlockCount());
        plugin.getConfig().set("triggers." + id + ".last-attached-by", player.getUniqueId().toString());
        plugin.saveConfig();
        removeSelectionBook(player);
        giveTriggerBook(player, id);
        player.sendMessage(Component.text("Selection attached to '" + plugin.getConfig().getString("triggers." + id + ".name") + "'.", NamedTextColor.GREEN));
        return true;
    }

    public void beginButtonSelection(Player player, String id) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) {
            player.sendMessage(Component.text("That trigger could not be found.", NamedTextColor.RED));
            return;
        }
        awaitingButton.put(player.getUniqueId(), id);
        player.sendMessage(Component.text("Use your GateWorks wand to click the physical button you want to attach.", NamedTextColor.GOLD));
    }

    public boolean isAwaitingButton(Player player) { return awaitingButton.containsKey(player.getUniqueId()); }

    public boolean attachButton(Player player, org.bukkit.block.Block block) {
        String id = awaitingButton.get(player.getUniqueId());
        if (id == null) return false;
        String material = block.getType().name();
        if (!material.endsWith("_BUTTON")) {
            player.sendMessage(Component.text("That block is not a button. Click a Minecraft button with the wand.", NamedTextColor.RED));
            return true;
        }
        awaitingButton.remove(player.getUniqueId());
        String path = "triggers." + id + ".button.";
        plugin.getConfig().set(path + "world", block.getWorld().getUID().toString());
        plugin.getConfig().set(path + "x", block.getX());
        plugin.getConfig().set(path + "y", block.getY());
        plugin.getConfig().set(path + "z", block.getZ());
        plugin.saveConfig();
        giveTriggerBook(player, id);
        player.sendMessage(Component.text("Button attached to trigger '" + plugin.getConfig().getString("triggers." + id + ".name") + "'.", NamedTextColor.GREEN));
        return true;
    }

    public void giveTriggerBook(Player player, String id) {
        ItemStack book = findBook(player, "trigger:" + id);
        boolean newBook = book == null;
        if (newBook) book = new ItemStack(Material.WRITTEN_BOOK);
        ItemStack target = book;
        target.editMeta(meta -> {
            BookMeta bm = (BookMeta) meta;
            String name = plugin.getConfig().getString("triggers." + id + ".name", "Trigger");
            bm.title(Component.text(name.length() > 32 ? name.substring(0, 32) : name));
            bm.author(Component.text("GateWorks"));
            boolean hasButton = plugin.getConfig().contains("triggers." + id + ".button.world");
            int blocks = plugin.getConfig().getInt("triggers." + id + ".selection-blocks", 0);
            bm.pages(Component.text("GATEWORKS\\n" + name + "\\n\\n", NamedTextColor.DARK_AQUA)
                    .append(Component.text("Selected blocks: " + blocks + "\\n", NamedTextColor.BLACK))
                    .append(Component.text("Activation button: " + (hasButton ? "Attached" : "Not set") + "\\n\\n", hasButton ? NamedTextColor.DARK_GREEN : NamedTextColor.RED))
                    .append(link("Set / change activation button", "/gateworks book button " + id))
                    .append(Component.text("\\n\\nTrigger ID:\\n" + id, NamedTextColor.GRAY)));
            bm.getPersistentDataContainer().set(bookType, PersistentDataType.STRING, "trigger");
            bm.getPersistentDataContainer().set(bookId, PersistentDataType.STRING, id);
        });
        if (newBook) player.getInventory().addItem(book).values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private Component link(String label, String command) {
        return Component.text("▶ " + label, NamedTextColor.BLUE)
                .clickEvent(ClickEvent.runCommand(command));
    }

    private void removeSelectionBook(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() != Material.WRITTEN_BOOK) continue;
            String found = item.getItemMeta().getPersistentDataContainer().get(bookType, PersistentDataType.STRING);
            if ("selection".equals(found)) player.getInventory().setItem(slot, null);
        }
    }

    private ItemStack findBook(Player player, String type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.WRITTEN_BOOK) continue;
            String found = item.getItemMeta().getPersistentDataContainer().get(bookType, PersistentDataType.STRING);
            if (type.equals(found)) return item;
            if (type.startsWith("trigger:") && "trigger".equals(found)) {
                String id = item.getItemMeta().getPersistentDataContainer().get(bookId, PersistentDataType.STRING);
                if (type.substring(8).equals(id)) return item;
            }
        }
        return null;
    }

    private List<String> triggerIds() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("triggers");
        if (section == null) return List.of();
        return section.getKeys(false).stream().sorted().toList();
    }
}
