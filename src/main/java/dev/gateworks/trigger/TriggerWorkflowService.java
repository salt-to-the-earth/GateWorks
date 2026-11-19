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
    private final Map<UUID, String> regionTargets = new HashMap<>();
    private final Map<UUID, String> awaitingButton = new HashMap<>();
    private final Map<UUID, BlockPosition> pendingDevices = new HashMap<>();

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
            bm.title(Component.text("Temp Selection"));
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
        pages.add(Component.text("GATEWORKS\\nTemp Selection\\n\\n", NamedTextColor.DARK_AQUA)
                .append(Component.text("Blocks selected: " + session.getBlockCount() + "\\n", NamedTextColor.BLACK))
                .append(Component.text("Selection groups: " + session.getGroupCount() + "\\n\\n", NamedTextColor.BLACK))
                .append(link("Refresh this page", "/gateworks book refresh"))
                .append(Component.text("\\n\\nTurn to the next page to attach this selection to a trigger.", NamedTextColor.GRAY)));
        Component pageTwo = Component.text("SELECTION OPTIONS\\n\\n", NamedTextColor.DARK_AQUA);
        String targetId = regionTargets.get(player.getUniqueId());
        if (targetId != null) {
            pageTwo = pageTwo.append(Component.text("Editing trigger: " + plugin.getConfig().getString("triggers." + targetId + ".name", "Temp Trigger") + "\\n\\n", NamedTextColor.BLACK))
                    .append(link("Save selection to this trigger", "/gateworks book attach " + targetId))
                    .append(Component.text("\\n\\n", NamedTextColor.BLACK));
        }
        pageTwo = pageTwo.append(Component.text("Attach selection to an existing trigger:\\n", NamedTextColor.BLACK));
        int count = 0;
        for (String id : triggerIds()) {
            if (count++ >= 12) break;
            String name = plugin.getConfig().getString("triggers." + id + ".name", "Temp Trigger");
            pageTwo = pageTwo.append(Component.text("\\n" + name + "  ", NamedTextColor.DARK_AQUA))
                    .append(link("Attach selection", "/gateworks book attach " + id));
        }
        if (count == 0) pageTwo = pageTwo.append(Component.text("No triggers have been created yet.", NamedTextColor.GRAY));
        pages.add(pageTwo);
        return pages;
    }

    public void promptForName(Player player, String id) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) {
            player.sendMessage(Component.text("[GateWorks] Trigger could not be found.", NamedTextColor.RED));
            return;
        }
        naming.put(player.getUniqueId(), id);
        player.closeInventory();
        player.sendMessage(Component.text("[GateWorks] Type the trigger's new name in chat. Type cancel to keep its current name.", NamedTextColor.GOLD));
    }

    public void acceptName(Player player, String rawName) {
        String id = naming.remove(player.getUniqueId());
        if (id == null) return;
        if (rawName.equalsIgnoreCase("cancel")) {
            player.sendMessage(Component.text("Trigger name unchanged.", NamedTextColor.YELLOW));
            giveTriggerBook(player, id);
            return;
        }
        String name = rawName.trim();
        if (name.isEmpty() || name.length() > 32) {
            naming.put(player.getUniqueId(), id);
            player.sendMessage(Component.text("Names must be between 1 and 32 characters. Please try again.", NamedTextColor.RED));
            return;
        }
        plugin.getConfig().set("triggers." + id + ".name", name);
        plugin.saveConfig();
        giveTriggerBook(player, id);
        player.sendMessage(Component.text("Trigger renamed to '" + name + "'.", NamedTextColor.GREEN));
    }

    private void persistSelection(Player player, String triggerId) {
        String base = "triggers." + triggerId + ".selection";
        List<String> saved = new ArrayList<>();
        int groupIndex = 0;
        for (var group : selections.get(player.getUniqueId()).getGroupsSnapshot()) {
            for (BlockPosition position : group) {
                saved.add(groupIndex + "|" + position.worldId() + "|" + position.x()
                        + "|" + position.y() + "|" + position.z());
            }
            groupIndex++;
        }
        plugin.getConfig().set(base, saved);
        plugin.getConfig().set("triggers." + triggerId + ".selection-blocks", saved.size());
    }

    public boolean isNaming(UUID playerId) { return naming.containsKey(playerId); }

    public void selectTriggerDevice(Player player, org.bukkit.block.Block block) {
        BlockPosition position = new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        String existingId = findTriggerAt(position);
        String id = existingId;
        if (id == null) {
            id = UUID.randomUUID().toString();
            plugin.getConfig().set("triggers." + id + ".name", "Temp Trigger");
            plugin.getConfig().set("triggers." + id + ".created-by", player.getUniqueId().toString());
            plugin.getConfig().set("triggers." + id + ".device.world", position.worldId().toString());
            plugin.getConfig().set("triggers." + id + ".device.x", position.x());
            plugin.getConfig().set("triggers." + id + ".device.y", position.y());
            plugin.getConfig().set("triggers." + id + ".device.z", position.z());
            plugin.getConfig().set("triggers." + id + ".device.material", block.getType().name());
            plugin.saveConfig();
            player.sendMessage(Component.text("[GateWorks] New trigger device registered. Click its name in the Temp Trigger book to name it.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("[GateWorks] Existing trigger device selected.", NamedTextColor.GREEN));
        }
        pendingDevices.put(player.getUniqueId(), position);
        giveTriggerBook(player, id);
        refreshSelectionBook(player);
    }

    private String findTriggerAt(BlockPosition position) {
        for (String id : triggerIds()) {
            String base = "triggers." + id + ".device.";
            if (position.worldId().toString().equals(plugin.getConfig().getString(base + "world"))
                    && position.x() == plugin.getConfig().getInt(base + "x")
                    && position.y() == plugin.getConfig().getInt(base + "y")
                    && position.z() == plugin.getConfig().getInt(base + "z")) return id;
        }
        return null;
    }

    public void clearPendingTriggerDevice(Player player) {
        if (pendingDevices.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("[GateWorks] Pending trigger device selection cleared.", NamedTextColor.YELLOW));
            refreshSelectionBook(player);
        } else {
            player.sendMessage(Component.text("[GateWorks] No pending trigger device to clear.", NamedTextColor.GRAY));
        }
    }

    public boolean isTriggerDeviceBlock(org.bukkit.block.Block block) {
        Material material = block.getType();
        String name = material.name();
        return name.endsWith("_BUTTON") || material == Material.LEVER
                || name.endsWith("_PRESSURE_PLATE") || material == Material.TRIPWIRE_HOOK;
    }

    private String describePendingDevice(Player player) {
        BlockPosition p = pendingDevices.get(player.getUniqueId());
        if (p == null) return "None selected";
        org.bukkit.World world = plugin.getServer().getWorld(p.worldId());
        if (world == null) return "Selected device (world unavailable)";
        return world.getBlockAt(p.x(), p.y(), p.z()).getType().name()
                + " at " + p.x() + ", " + p.y() + ", " + p.z();
    }

    private void persistDevice(Player player, String triggerId) {
        BlockPosition p = pendingDevices.get(player.getUniqueId());
        if (p == null) return;
        String base = "triggers." + triggerId + ".device.";
        org.bukkit.World world = plugin.getServer().getWorld(p.worldId());
        plugin.getConfig().set(base + "world", p.worldId().toString());
        plugin.getConfig().set(base + "x", p.x());
        plugin.getConfig().set(base + "y", p.y());
        plugin.getConfig().set(base + "z", p.z());
        if (world != null) plugin.getConfig().set(base + "material", world.getBlockAt(p.x(), p.y(), p.z()).getType().name());
    }

    public boolean attachSelection(Player player, String id) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return false;
        persistSelection(player, id);
        selections.get(player.getUniqueId()).clear();
        regionTargets.remove(player.getUniqueId());
        if (pendingDevices.containsKey(player.getUniqueId())) {
            persistDevice(player, id);
            pendingDevices.remove(player.getUniqueId());
        }
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
        player.sendMessage(Component.text("Use your GateWorks wand to select the trigger device you want to attach.", NamedTextColor.GOLD));
    }

    public boolean isAwaitingButton(Player player) { return awaitingButton.containsKey(player.getUniqueId()); }

    public boolean attachButton(Player player, org.bukkit.block.Block block) {
        String id = awaitingButton.get(player.getUniqueId());
        if (id == null) return false;
        String material = block.getType().name();
        if (!isTriggerDevice(block.getType())) {
            player.sendMessage(Component.text("That block is not a supported trigger device. Select a button, lever, or pressure plate.", NamedTextColor.RED));
            return true;
        }
        awaitingButton.remove(player.getUniqueId());
        String path = "triggers." + id + ".device.";
        plugin.getConfig().set(path + "world", block.getWorld().getUID().toString());
        plugin.getConfig().set(path + "x", block.getX());
        plugin.getConfig().set(path + "y", block.getY());
        plugin.getConfig().set(path + "z", block.getZ());
        plugin.getConfig().set(path + "material", material);
        plugin.saveConfig();
        giveTriggerBook(player, id);
        player.sendMessage(Component.text("Trigger device attached to trigger '" + plugin.getConfig().getString("triggers." + id + ".name") + "'.", NamedTextColor.GREEN));
        return true;
    }

    public void beginRegionEdit(Player player, String id) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return;
        selections.get(player.getUniqueId()).clear();
        regionTargets.put(player.getUniqueId(), id);
        refreshSelectionBook(player);
        player.sendMessage(Component.text("Use the wand to select the blocks for this trigger, then save from page 2 of Temp Selection.", NamedTextColor.GOLD));
    }

    public void giveTriggerBook(Player player, String id) {
        ItemStack book = findBook(player, "trigger:" + id);
        boolean newBook = book == null;
        if (newBook) book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(meta -> {
            BookMeta bm = (BookMeta) meta;
            String name = plugin.getConfig().getString("triggers." + id + ".name", "Temp Trigger");
            bm.title(Component.text(name.length() > 32 ? name.substring(0, 32) : name));
            bm.author(Component.text("GateWorks"));
            String deviceType = plugin.getConfig().getString("triggers." + id + ".device.material", "Not set");
            int blocks = plugin.getConfig().getInt("triggers." + id + ".selection-blocks", 0);
            Component overview = Component.text("GATEWORKS\\n" + name + "\\n\\n", NamedTextColor.DARK_AQUA)
                    .append(Component.text("Trigger device: " + deviceType + "\\n", NamedTextColor.BLACK))
                    .append(Component.text("Selected blocks: " + blocks + "\\n\\n", NamedTextColor.BLACK))
                    .append(link("Change trigger name", "/gateworks book rename " + id));
            Component region = Component.text("REGION SELECTION\\n\\n", NamedTextColor.DARK_AQUA)
                    .append(Component.text("This trigger currently has " + blocks + " selected blocks.\\n\\n", NamedTextColor.BLACK))
                    .append(link("Change region selection", "/gateworks book edit-selection " + id));
            bm.pages(overview, region);
            bm.getPersistentDataContainer().set(bookType, PersistentDataType.STRING, "trigger");
            bm.getPersistentDataContainer().set(bookId, PersistentDataType.STRING, id);
        });
        if (newBook) player.getInventory().addItem(book).values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private boolean isTriggerDevice(Material material) {
        String name = material.name();
        return name.endsWith("_BUTTON") || material == Material.LEVER
                || name.endsWith("_PRESSURE_PLATE") || material == Material.TRIPWIRE_HOOK;
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
