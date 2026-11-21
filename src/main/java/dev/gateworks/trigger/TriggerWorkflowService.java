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
import dev.gateworks.selection.SelectionSession;
import java.util.*;

/** First-stage book workflow and server-side trigger registry. */
public final class TriggerWorkflowService {
    private final GateWorksPlugin plugin;
    private final SelectionService selections;
    private final NamespacedKey bookType;
    private final NamespacedKey bookId;
    private final Map<UUID, String> naming = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, String> regionTargets = new HashMap<>();
    private final Map<UUID, List<String>> regionBackups = new HashMap<>();
    private final Map<UUID, String> awaitingButton = new HashMap<>();
    private final Map<UUID, BlockPosition> pendingDevices = new HashMap<>();
    private final Map<UUID, PendingEdit> pendingEdits = new HashMap<>();
    private BlockSelectionMenu blockSelectionMenu;
    private record PendingEdit(String triggerId, String path, String label, boolean numeric) { }

    public TriggerWorkflowService(GateWorksPlugin plugin, SelectionService selections) {
        this.plugin = plugin;
        this.selections = selections;
        this.bookType = new NamespacedKey(plugin, "book_type");
        this.bookId = new NamespacedKey(plugin, "book_id");
    }

    public void setBlockSelectionMenu(BlockSelectionMenu menu) {
        this.blockSelectionMenu = menu;
    }

    public void openMaterialSelector(Player player, String id, String slot) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")
                || plugin.getConfig().getInt("triggers." + id + ".selection-blocks", 0) < 1) {
            player.sendMessage(Component.text("This trigger does not have a saved region.", NamedTextColor.RED));
            return;
        }
        if (blockSelectionMenu == null) {
            player.sendMessage(Component.text("The block selector is unavailable.", NamedTextColor.RED));
            return;
        }
        // Book click commands execute while the client still displays the book screen.
        // Defer the inventory transition until the command has returned to the server loop.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.closeInventory();
            blockSelectionMenu.open(player, id, slot);
        });
    }

    public void refreshTriggerBook(Player player, String id) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return;
        giveTriggerBook(player, id);
        scheduleTriggerBookOpen(player, id);
    }

    private void scheduleTriggerBookOpen(Player player, String id) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack updatedBook = findBook(player, "trigger:" + id);
            if (updatedBook != null) player.openBook(updatedBook);
        });
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
        pages.add(Component.text("GATEWORKS\nTemp Selection\n\n", NamedTextColor.DARK_AQUA)
                .append(Component.text("Blocks selected: " + session.getBlockCount() + "\n", NamedTextColor.BLACK))
                .append(Component.text("Selection groups: " + session.getGroupCount() + "\n\n", NamedTextColor.BLACK))
                .append(link("Refresh this page", "/gateworks book refresh"))
                .append(Component.text("\n\nTurn to the next page to attach this selection to a trigger.", NamedTextColor.GRAY)));
        Component pageTwo = Component.text("SELECTION OPTIONS\n\n", NamedTextColor.DARK_AQUA);
        String targetId = regionTargets.get(player.getUniqueId());
        if (targetId != null) {
            pageTwo = pageTwo.append(Component.text("Editing trigger: " + plugin.getConfig().getString("triggers." + targetId + ".name", "Temp Trigger") + "\n\n", NamedTextColor.BLACK))
                    .append(link("Save selection to this trigger", "/gateworks book attach " + targetId))
                    .append(Component.text("\n\n", NamedTextColor.BLACK));
        }
        pageTwo = pageTwo.append(Component.text("Attach selection to an existing trigger:\n", NamedTextColor.BLACK));
        int count = 0;
        for (String id : triggerIds()) {
            if (count++ >= 12) break;
            String name = plugin.getConfig().getString("triggers." + id + ".name", "Temp Trigger");
            pageTwo = pageTwo.append(Component.text("\n" + name + "  ", NamedTextColor.DARK_AQUA))
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
        PendingEdit edit = pendingEdits.remove(player.getUniqueId());
        if (edit != null) {
            if (rawName.equalsIgnoreCase("cancel")) {
                player.sendMessage(Component.text("Configuration unchanged.", NamedTextColor.YELLOW));
                giveTriggerBook(player, edit.triggerId());
                return;
            }
            if (edit.numeric()) {
                try {
                    long value = Long.parseLong(rawName.trim());
                    long minimum = edit.path().endsWith("spam-threshold") || edit.path().endsWith("spam-window-seconds")
                            || edit.path().endsWith("lockout-seconds") ? 1L
                            : 0L;
                    if (edit.path().endsWith("spam-threshold")) minimum = 2L;
                    if (edit.path().endsWith("listener.interval-seconds")) minimum = 1L;
                    if (value < minimum || (edit.path().endsWith("listener.interval-seconds") && value > 3600L)) throw new NumberFormatException();
                    plugin.getConfig().set(edit.path(), value);
                } catch (NumberFormatException ex) {
                    pendingEdits.put(player.getUniqueId(), edit);
                    player.sendMessage(Component.text("Please enter a valid whole number (threshold must be at least 2), or CANCEL.", NamedTextColor.RED));
                    return;
                }
            } else {
                String value = rawName.trim().equalsIgnoreCase("clear") ? "" : rawName.trim();
                if (value.length() > 256) {
                    pendingEdits.put(player.getUniqueId(), edit);
                    player.sendMessage(Component.text("Commands are limited to 256 characters. Please try again or enter CANCEL.", NamedTextColor.RED));
                    return;
                }
                plugin.getConfig().set(edit.path(), value);
            }
            if (edit.path().contains(".listener.")) {
                plugin.getConfig().set("triggers." + edit.triggerId() + ".listener.status", "OFFLINE");
                plugin.getConfig().set("triggers." + edit.triggerId() + ".listener.last-error", "Waiting for the next monitoring check.");
            }
            plugin.saveConfig();
            giveTriggerBook(player, edit.triggerId());
            player.sendMessage(Component.text(edit.label() + " updated.", NamedTextColor.GREEN));
            return;
        }
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

    public boolean isNaming(UUID playerId) {
        return naming.containsKey(playerId) || pendingEdits.containsKey(playerId);
    }

    public void promptForCommand(Player player, String id, String slot) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return;
        String path = switch (slot.toLowerCase(Locale.ROOT)) {
            case "single" -> "triggers." + id + ".action.single-command";
            case "on" -> "triggers." + id + ".action.on-command";
            case "off" -> "triggers." + id + ".action.off-command";
            default -> null;
        };
        if (path == null) return;
        pendingEdits.put(player.getUniqueId(), new PendingEdit(id, path, slot.toUpperCase(Locale.ROOT) + " command", false));
        player.closeInventory();
        player.sendMessage(Component.text("[GateWorks] Enter the " + slot.toUpperCase(Locale.ROOT)
                + " command in chat. Enter CLEAR to remove it, or CANCEL to leave it unchanged.", NamedTextColor.GOLD));
    }

    public void promptForProtectionSetting(Player player, String id, String setting) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return;
        String key = switch (setting.toLowerCase(Locale.ROOT)) {
            case "cooldown" -> "cooldown-ms";
            case "threshold" -> "spam-threshold";
            case "window" -> "spam-window-seconds";
            case "lockout" -> "lockout-seconds";
            default -> null;
        };
        if (key == null) return;
        pendingEdits.put(player.getUniqueId(), new PendingEdit(id, "triggers." + id + ".protection." + key, setting, true));
        player.closeInventory();
        player.sendMessage(Component.text("[GateWorks] Enter a whole number for " + setting
                + ". Enter CANCEL to leave it unchanged.", NamedTextColor.GOLD));
    }

    public void promptForListenerSetting(Player player, String id, String setting) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return;
        String key = switch (setting.toLowerCase(Locale.ROOT)) {
            case "command" -> "command";
            case "state-a" -> "state-a";
            case "state-b" -> "state-b";
            case "interval" -> "interval-seconds";
            default -> null;
        };
        if (key == null) return;
        boolean numeric = key.equals("interval-seconds");
        pendingEdits.put(player.getUniqueId(), new PendingEdit(id,
                "triggers." + id + ".listener." + key, "Listener " + setting, numeric));
        player.closeInventory();
        player.sendMessage(Component.text("[GateWorks] Enter " + (numeric
                ? "a polling interval in seconds (1-3600)"
                : setting.equals("command") ? "the monitoring command" : "the exact response text for " + setting.toUpperCase(Locale.ROOT))
                + ". Enter CLEAR to empty the value or CANCEL to leave it unchanged.", NamedTextColor.GOLD));
    }

    public void toggleListener(Player player, String id) {
        String base = "triggers." + id + ".listener.";
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return;
        boolean enabled = plugin.getConfig().getBoolean(base + "enabled", false);
        if (!enabled && plugin.getConfig().getString(base + "command", "").isBlank()) {
            player.sendMessage(Component.text("Set a monitoring command before enabling the listener.", NamedTextColor.RED));
            return;
        }
        plugin.getConfig().set(base + "enabled", !enabled);
        plugin.getConfig().set(base + "status", !enabled ? "OFFLINE" : "DISABLED");
        plugin.getConfig().set(base + "last-error", !enabled ? "Waiting for the next monitoring check." : "Listener disabled.");
        plugin.saveConfig();
        refreshTriggerBook(player, id);
    }

    public void showListenerError(Player player, String id) {
        String base = "triggers." + id + ".listener.";
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return;
        String error = plugin.getConfig().getString(base + "last-error", "");
        if (error == null || error.isBlank()) error = "No listener error is currently recorded.";
        player.sendMessage(Component.text("[GateWorks] Listener: ", NamedTextColor.GOLD)
                .append(Component.text(error, NamedTextColor.YELLOW)));
    }

    public void toggleActionMode(Player player, String id) {
        String base = "triggers." + id + ".";
        if (!plugin.getConfig().contains(base + "name")) return;
        boolean hasRegion = plugin.getConfig().getInt(base + "selection-blocks", 0) > 0;
        String current = plugin.getConfig().getString(base + "action.mode", "SINGLE");
        if (hasRegion && "TOGGLE".equalsIgnoreCase(current)) {
            player.sendMessage(Component.text("A trigger with a region must retain its ON/OFF command toggle.", NamedTextColor.YELLOW));
            return;
        }
        String next = "TOGGLE".equalsIgnoreCase(current) ? "SINGLE" : "TOGGLE";
        plugin.getConfig().set(base + "action.mode", next);
        if ("TOGGLE".equals(next)) {
            if (!plugin.getConfig().contains(base + "action.on-command")) plugin.getConfig().set(base + "action.on-command", "say GateWorks is ON!");
            if (!plugin.getConfig().contains(base + "action.off-command")) plugin.getConfig().set(base + "action.off-command", "say GateWorks is OFF!");
            alignToggleToDevice(id);
        }
        plugin.saveConfig();
        giveTriggerBook(player, id);
    }

    public void selectRegionMaterial(Player player, String id, String slot, String materialName) {
        String base = "triggers." + id + ".";
        if (!plugin.getConfig().contains(base + "name") || plugin.getConfig().getInt(base + "selection-blocks", 0) < 1) {
            player.sendMessage(Component.text("This trigger does not have a saved region.", NamedTextColor.RED));
            return;
        }
        String key = switch (slot.toLowerCase(Locale.ROOT)) {
            case "primary" -> "primary-material";
            case "secondary" -> "secondary-material";
            default -> null;
        };
        Material material = Material.matchMaterial(materialName);
        if (key == null || !BlockSelectionMenu.isSelectableMaterial(material)) {
            player.sendMessage(Component.text("Choose a valid block from the GateWorks selector.", NamedTextColor.RED));
            return;
        }
        plugin.getConfig().set(base + "region." + key, material.name());
        plugin.saveConfig();
        giveTriggerBook(player, id);
        scheduleTriggerBookOpen(player, id);
        player.sendMessage(Component.text((slot.equalsIgnoreCase("primary") ? "Primary" : "Secondary")
                + " block type set to " + prettyMaterial(material) + ".", NamedTextColor.GREEN));
    }

    private String prettyMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringJoiner result = new StringJoiner(" ");
        for (String word : words) result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        return result.toString();
    }

    private void initializeRegionMaterialDefaults(String id, List<String> savedSelection) {
        String base = "triggers." + id + ".region.";
        if (!plugin.getConfig().contains(base + "secondary-material")) {
            plugin.getConfig().set(base + "secondary-material", Material.AIR.name());
        }
        if (plugin.getConfig().contains(base + "primary-material") || savedSelection.isEmpty()) {
            plugin.saveConfig();
            return;
        }

        String[] first = savedSelection.get(0).split("\\|");
        if (first.length != 5) return;
        try {
            UUID worldId = UUID.fromString(first[1]);
            org.bukkit.World world = plugin.getServer().getWorld(worldId);
            if (world == null) return;
            Material primary = world.getBlockAt(Integer.parseInt(first[2]),
                    Integer.parseInt(first[3]), Integer.parseInt(first[4])).getType();
            if (primary.isAir()) primary = Material.AIR;
            if (primary == Material.AIR || primary.isBlock()) {
                plugin.getConfig().set(base + "primary-material", primary.name());
            }
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Could not determine the default Primary material for trigger " + id + ".");
        }
        plugin.saveConfig();
    }

    private String displayMaterial(String materialName) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        return material == null ? "Not configured" : prettyMaterial(material) + " (" + material.name() + ")";
    }

    public void toggleProtection(Player player, String id) {
        String path = "triggers." + id + ".protection.enabled";
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return;
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path, true));
        plugin.saveConfig();
        giveTriggerBook(player, id);
    }

    private void alignToggleToDevice(String id) {
        String base = "triggers." + id + ".";
        String worldId = plugin.getConfig().getString(base + "device.world");
        if (worldId == null) return;
        try {
            org.bukkit.World world = plugin.getServer().getWorld(UUID.fromString(worldId));
            if (world == null) return;
            org.bukkit.block.Block block = world.getBlockAt(plugin.getConfig().getInt(base + "device.x"),
                    plugin.getConfig().getInt(base + "device.y"), plugin.getConfig().getInt(base + "device.z"));
            boolean powered = block.getBlockData() instanceof org.bukkit.block.data.Powerable powerable
                    ? powerable.isPowered() : block.getBlockPower() > 0;
            plugin.getConfig().set(base + "runtime.logical-state", powered);
            plugin.getConfig().set(base + "runtime.observed-state", powered);
        } catch (IllegalArgumentException ignored) { }
    }

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
            boolean pulseDevice = block.getType().name().endsWith("_BUTTON");
            String base = "triggers." + id + ".";
            plugin.getConfig().set(base + "action.mode", pulseDevice ? "SINGLE" : "TOGGLE");
            plugin.getConfig().set(base + "action.single-command", "say GateWorks says hello!");
            plugin.getConfig().set(base + "action.on-command", "say GateWorks is ON!");
            plugin.getConfig().set(base + "action.off-command", "say GateWorks is OFF!");
            boolean initiallyPowered = block.getBlockData() instanceof org.bukkit.block.data.Powerable powerable
                    && powerable.isPowered();
            plugin.getConfig().set(base + "runtime.observed-state", initiallyPowered);
            plugin.getConfig().set(base + "runtime.logical-state", initiallyPowered);
            plugin.getConfig().set(base + "protection.enabled", true);
            plugin.getConfig().set(base + "protection.cooldown-ms", 1000L);
            plugin.getConfig().set(base + "protection.spam-threshold", 5);
            plugin.getConfig().set(base + "protection.spam-window-seconds", 10L);
            plugin.getConfig().set(base + "protection.lockout-seconds", 15L);
            plugin.getConfig().set(base + "protection.notify-player", true);
            plugin.saveConfig();
            player.sendMessage(Component.text("[GateWorks] New trigger device registered. Click its name in the Temp Trigger book to name it.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("[GateWorks] Existing trigger device selected.", NamedTextColor.GREEN));
        }
        pendingDevices.put(player.getUniqueId(), position);
        giveTriggerBook(player, id);
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

    private boolean selectionContainsTriggerDevice(Player player, String id) {
        String base = "triggers." + id + ".device.";
        String worldId = plugin.getConfig().getString(base + "world");
        if (worldId == null) return false;
        try {
            BlockPosition device = new BlockPosition(UUID.fromString(worldId),
                    plugin.getConfig().getInt(base + "x"),
                    plugin.getConfig().getInt(base + "y"),
                    plugin.getConfig().getInt(base + "z"));
            return selections.get(player.getUniqueId()).contains(device);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean attachSelection(Player player, String id) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return false;
        if (selectionContainsTriggerDevice(player, id)) {
            player.sendMessage(Component.text("The trigger device itself cannot be included in its region. Remove it from the selection so GateWorks can continue to use it.", NamedTextColor.RED));
            return false;
        }
        persistSelection(player, id);
        initializeRegionMaterialDefaults(id, plugin.getConfig().getStringList("triggers." + id + ".selection"));
        selections.get(player.getUniqueId()).clear();
        regionTargets.remove(player.getUniqueId());
        if (pendingDevices.containsKey(player.getUniqueId())) {
            persistDevice(player, id);
            pendingDevices.remove(player.getUniqueId());
        }
        plugin.getConfig().set("triggers." + id + ".last-attached-by", player.getUniqueId().toString());
        if (plugin.getConfig().getInt("triggers." + id + ".selection-blocks", 0) > 0) {
            plugin.getConfig().set("triggers." + id + ".action.mode", "TOGGLE");
            alignToggleToDevice(id);
        }
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

    public boolean isEditingRegion(Player player) { return regionTargets.containsKey(player.getUniqueId()); }

    public void refreshTriggerBook(Player player) {
        String id = regionTargets.get(player.getUniqueId());
        if (id != null) giveTriggerBook(player, id);
    }

    public void beginRegionEdit(Player player, String id) {
        if (!plugin.getConfig().contains("triggers." + id + ".name")) return;
        String base = "triggers." + id + ".selection";
        List<String> saved = plugin.getConfig().getStringList(base);
        if (!saved.isEmpty()) {
            initializeRegionMaterialDefaults(id, saved);
            plugin.getConfig().set("triggers." + id + ".action.mode", "TOGGLE");
            alignToggleToDevice(id);
        }
        regionBackups.put(player.getUniqueId(), new ArrayList<>(saved));
        SelectionSession session = selections.get(player.getUniqueId());
        session.clear();
        Map<Integer, List<BlockPosition>> groups = new TreeMap<>();
        for (String entry : saved) {
            String[] parts = entry.split("\\|");
            if (parts.length != 5) continue;
            try {
                int group = Integer.parseInt(parts[0]);
                UUID world = UUID.fromString(parts[1]);
                groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(
                        new BlockPosition(world, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
            } catch (IllegalArgumentException ignored) { }
        }
        groups.values().forEach(session::addGroup);
        regionTargets.put(player.getUniqueId(), id);
        giveTriggerBook(player, id);
        player.sendMessage(Component.text("Existing region loaded. Modify it with the wand, then save or cancel from the Trigger Book.", NamedTextColor.GOLD));
    }

    public void saveRegionEdit(Player player, String id) {
        if (!id.equals(regionTargets.get(player.getUniqueId()))) return;
        if (selectionContainsTriggerDevice(player, id)) {
            player.sendMessage(Component.text("The trigger device itself cannot be included in its region. Remove it from the selection, then save again.", NamedTextColor.RED));
            return;
        }
        persistSelection(player, id);
        // Saved configuration is authoritative; clear the temporary working
        // selection so the particle visualizer stops drawing the edit.
        selections.get(player.getUniqueId()).clear();
        regionTargets.remove(player.getUniqueId());
        regionBackups.remove(player.getUniqueId());
        plugin.saveConfig();
        giveTriggerBook(player, id);
        player.sendMessage(Component.text("Region changes saved.", NamedTextColor.GREEN));
    }

    public void cancelRegionEdit(Player player, String id) {
        if (!id.equals(regionTargets.get(player.getUniqueId()))) return;
        List<String> backup = regionBackups.remove(player.getUniqueId());
        selections.get(player.getUniqueId()).clear();
        if (backup != null) restoreSelection(player, backup);
        regionTargets.remove(player.getUniqueId());
        giveTriggerBook(player, id);
        player.sendMessage(Component.text("Region editing cancelled. The saved region was restored.", NamedTextColor.YELLOW));
    }

    private void restoreSelection(Player player, List<String> saved) {
        Map<Integer, List<BlockPosition>> groups = new TreeMap<>();
        for (String entry : saved) {
            String[] parts = entry.split("\\|");
            if (parts.length != 5) continue;
            try {
                groups.computeIfAbsent(Integer.parseInt(parts[0]), ignored -> new ArrayList<>()).add(
                        new BlockPosition(UUID.fromString(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
            } catch (IllegalArgumentException ignored) { }
        }
        groups.values().forEach(selections.get(player.getUniqueId())::addGroup);
    }

    public void deleteTrigger(Player player, String id) {
        if (!plugin.getConfig().contains("triggers." + id)) {
            player.sendMessage(Component.text("That trigger no longer exists.", NamedTextColor.RED));
            return;
        }
        plugin.getConfig().set("triggers." + id, null);
        plugin.saveConfig();
        removeTriggerBooks(player, id);
        selections.get(player.getUniqueId()).clear();
        regionTargets.remove(player.getUniqueId());
        regionBackups.remove(player.getUniqueId());
        pendingDevices.remove(player.getUniqueId());
        awaitingButton.remove(player.getUniqueId());
        player.sendMessage(Component.text("Trigger and its saved region have been deleted.", NamedTextColor.GREEN));
    }

    private void removeTriggerBooks(Player player, String id) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() != Material.WRITTEN_BOOK) continue;
            var pdc = item.getItemMeta().getPersistentDataContainer();
            if ("trigger".equals(pdc.get(bookType, PersistentDataType.STRING))
                    && id.equals(pdc.get(bookId, PersistentDataType.STRING))) player.getInventory().setItem(slot, null);
        }
    }

    public void giveTriggerBook(Player player, String id) {
        if (plugin.getConfig().getInt("triggers." + id + ".selection-blocks", 0) > 0) {
            initializeRegionMaterialDefaults(id,
                    plugin.getConfig().getStringList("triggers." + id + ".selection"));
        }
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
            Component overview = Component.text("GATEWORKS\n" + name + "\n\n", NamedTextColor.DARK_AQUA)
                    .append(link("Name: " + name, "/gateworks book rename " + id))
                    .append(Component.text("\nTrigger device: " + deviceType + "\n", NamedTextColor.BLACK))
                    .append(Component.text("Selected blocks: " + blocks, NamedTextColor.BLACK));
            Component region = Component.text("REGION PREVIEW\n\n", NamedTextColor.DARK_AQUA)
                    .append(Component.text("Selected blocks: " + blocks + "\n\n", NamedTextColor.BLACK))
                    .append(selectionPreview(id))
                    .append(Component.text("\n\nUse the wand to review the complete selection in the world.", NamedTextColor.GRAY));
            Component regionControls = Component.text("REGION EDITING\n\n", NamedTextColor.DARK_AQUA)
                    .append(Component.text("Edit the saved selection with your GateWorks wand.\n\n", NamedTextColor.BLACK))
                    .append(link("Edit region selection", "/gateworks book edit-selection " + id))
                    .append(Component.text("\n\n", NamedTextColor.BLACK))
                    .append(link("Save region changes", "/gateworks book save-region " + id))
                    .append(Component.text("\n", NamedTextColor.BLACK))
                    .append(link("Cancel region changes", "/gateworks book cancel-region " + id));
            String primaryMaterial = displayMaterial(plugin.getConfig().getString("triggers." + id + ".region.primary-material", ""));
            String secondaryMaterial = displayMaterial(plugin.getConfig().getString("triggers." + id + ".region.secondary-material", ""));
            Component regionAction = Component.text("ACTION 2 — BLOCK TOGGLE\n\n", NamedTextColor.DARK_AQUA)
                    .append(link("Primary: " + primaryMaterial, "/gateworks book material-menu " + id + " primary"))
                    .append(Component.text("\n", NamedTextColor.BLACK))
                    .append(link("Secondary: " + secondaryMaterial, "/gateworks book material-menu " + id + " secondary"))
                    .append(Component.text("\n\nEvery selected position is replaced with the Secondary material when ON, and the Primary material when OFF.\n\n", NamedTextColor.GRAY))
                    .append(Component.text("Click either material to open the block selector.", NamedTextColor.BLUE));
            String actionMode = plugin.getConfig().getString("triggers." + id + ".action.mode", "SINGLE");
            String singleCommand = previewCommand(plugin.getConfig().getString("triggers." + id + ".action.single-command", "say GateWorks says hello!"));
            String onCommand = previewCommand(plugin.getConfig().getString("triggers." + id + ".action.on-command", "say GateWorks is ON!"));
            String offCommand = previewCommand(plugin.getConfig().getString("triggers." + id + ".action.off-command", "say GateWorks is OFF!"));
            Component actions = Component.text("ACTION 1 — COMMANDS\n\n", NamedTextColor.DARK_AQUA);
            if ("TOGGLE".equalsIgnoreCase(actionMode)) {
                actions = actions.append(Component.text("Mode: ON / OFF command toggle\n\n", NamedTextColor.BLACK))
                        .append(link("Edit ON command", "/gateworks book command " + id + " on"))
                        .append(Component.text("\n" + (onCommand.isBlank() ? "(empty)" : onCommand) + "\n\n", NamedTextColor.DARK_GRAY))
                        .append(link("Edit OFF command", "/gateworks book command " + id + " off"))
                        .append(Component.text("\n" + (offCommand.isBlank() ? "(empty)" : offCommand) + "\n\n", NamedTextColor.DARK_GRAY));
                if (blocks == 0) actions = actions.append(link("Switch to single command", "/gateworks book mode " + id));
                else actions = actions.append(Component.text("A region requires command-toggle mode.\n", NamedTextColor.GRAY));
            } else {
                actions = actions.append(Component.text("Mode: Single command\n\n", NamedTextColor.BLACK))
                        .append(link("Edit single command", "/gateworks book command " + id + " single"))
                        .append(Component.text("\n" + (singleCommand.isBlank() ? "(empty)" : singleCommand) + "\n\n", NamedTextColor.DARK_GRAY))
                        .append(link("Enable ON / OFF toggle", "/gateworks book mode " + id));
            }
            Component protection = Component.text("SPAM PROTECTION\n\n", NamedTextColor.DARK_AQUA)
                    .append(Component.text("Status: " + (plugin.getConfig().getBoolean("triggers." + id + ".protection.enabled", true) ? "Enabled" : "Disabled") + "\n", NamedTextColor.BLACK))
                    .append(Component.text("Cooldown: " + plugin.getConfig().getLong("triggers." + id + ".protection.cooldown-ms", 1000L) + " ms\n", NamedTextColor.BLACK))
                    .append(Component.text("Spam threshold: " + plugin.getConfig().getInt("triggers." + id + ".protection.spam-threshold", 5) + " attempts\n\n", NamedTextColor.BLACK))
                    .append(link("Edit cooldown", "/gateworks book protection-setting " + id + " cooldown"))
                    .append(Component.text("\n", NamedTextColor.BLACK))
                    .append(link("Edit spam threshold", "/gateworks book protection-setting " + id + " threshold"));
            Component protectionDetails = Component.text("SPAM PROTECTION — SETTINGS\n\n", NamedTextColor.DARK_AQUA)
                    .append(Component.text("Detection window: " + plugin.getConfig().getLong("triggers." + id + ".protection.spam-window-seconds", 10L) + " seconds\n", NamedTextColor.BLACK))
                    .append(Component.text("Lockout: " + plugin.getConfig().getLong("triggers." + id + ".protection.lockout-seconds", 15L) + " seconds\n\n", NamedTextColor.BLACK))
                    .append(link("Edit detection window", "/gateworks book protection-setting " + id + " window"))
                    .append(Component.text("\n", NamedTextColor.BLACK))
                    .append(link("Edit lockout duration", "/gateworks book protection-setting " + id + " lockout"))
                    .append(Component.text("\n\n", NamedTextColor.BLACK))
                    .append(link(plugin.getConfig().getBoolean("triggers." + id + ".protection.enabled", true)
                            ? "Disable spam protection" : "Enable spam protection", "/gateworks book protection " + id));
            String listenerStatus = plugin.getConfig().getString("triggers." + id + ".listener.status",
                    plugin.getConfig().getBoolean("triggers." + id + ".listener.enabled", false) ? "OFFLINE" : "DISABLED");
            boolean listenerEnabled = plugin.getConfig().getBoolean("triggers." + id + ".listener.enabled", false);
            Component listenerPage = Component.text("STATUS LISTENER\\n\\n", NamedTextColor.DARK_AQUA)
                    .append(Component.text("Health: " + listenerStatus + "\\n\\n",
                            "ONLINE".equals(listenerStatus) ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(link(listenerEnabled ? "Disable listener" : "Enable listener", "/gateworks book listener " + id))
                    .append(Component.text("\\n\\n", NamedTextColor.BLACK))
                    .append(link("Show latest error", "/gateworks book listener-error " + id))
                    .append(Component.text("\\n\\n", NamedTextColor.BLACK))
                    .append(Component.text("Settings are on the next page.\\n", NamedTextColor.GRAY))
                    .append(Component.text("The listener observes status only; it never runs Action 1 commands.", NamedTextColor.GRAY));
            boolean commandConfigured = !plugin.getConfig().getString("triggers." + id + ".listener.command", "").isBlank();
            boolean stateAConfigured = !plugin.getConfig().getString("triggers." + id + ".listener.state-a", "").isBlank();
            boolean stateBConfigured = !plugin.getConfig().getString("triggers." + id + ".listener.state-b", "").isBlank();
            Component listenerSettingsPage = Component.text("LISTENER SETTINGS\\n\\n", NamedTextColor.DARK_AQUA)
                    .append(link("Monitoring command: " + (commandConfigured ? "Edit" : "Not set — add"),
                            "/gateworks book listener-setting " + id + " command"))
                    .append(Component.text("\\n\\n", NamedTextColor.BLACK))
                    .append(link("State A (OFF): " + (stateAConfigured ? "Edit" : "Not set — add"),
                            "/gateworks book listener-setting " + id + " state-a"))
                    .append(Component.text("\\n\\n", NamedTextColor.BLACK))
                    .append(link("State B (ON): " + (stateBConfigured ? "Edit" : "Not set — add"),
                            "/gateworks book listener-setting " + id + " state-b"))
                    .append(Component.text("\\n\\n", NamedTextColor.BLACK))
                    .append(link("Polling interval: " + plugin.getConfig().getLong("triggers." + id + ".listener.interval-seconds", 5) + " sec — edit",
                            "/gateworks book listener-setting " + id + " interval"))
                    .append(Component.text("\\n\\nEnter values in chat after selecting an edit link.\\nPress T to open chat if it does not open automatically.", NamedTextColor.GRAY));
            Component danger = Component.text("TRIGGER MANAGEMENT\n\n", NamedTextColor.DARK_RED)
                    .append(Component.text("Deleting this trigger permanently removes its saved region and trigger settings.\n\n", NamedTextColor.BLACK))
                    .append(link("Delete this trigger", "/gateworks book delete " + id));
            if (blocks > 0) {
                bm.pages(overview, region, regionControls, regionAction,
                        actions, protection, protectionDetails, listenerPage, danger);
            } else {
                bm.pages(overview, region, regionControls, actions, protection, protectionDetails, listenerPage, listenerSettingsPage, danger);
            }
            bm.getPersistentDataContainer().set(bookType, PersistentDataType.STRING, "trigger");
            bm.getPersistentDataContainer().set(bookId, PersistentDataType.STRING, id);
        });
        if (newBook) player.getInventory().addItem(book).values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private String previewCommand(String command) {
        if (command == null || command.isBlank()) return "(empty)";
        return command.length() > 44 ? command.substring(0, 41) + "..." : command;
    }

    private Component selectionPreview(String id) {
        List<String> entries = plugin.getConfig().getStringList("triggers." + id + ".selection");
        Map<Integer, Set<String>> layers = new TreeMap<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int firstY = Integer.MAX_VALUE;
        for (String entry : entries) {
            String[] p = entry.split("\\|");
            if (p.length != 5) continue;
            try {
                int x = Integer.parseInt(p[2]), y = Integer.parseInt(p[3]), z = Integer.parseInt(p[4]);
                firstY = Math.min(firstY, y);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                layers.computeIfAbsent(y, ignored -> new HashSet<>()).add(x + ":" + z);
            } catch (NumberFormatException ignored) { }
        }
        if (entries.isEmpty() || firstY == Integer.MAX_VALUE) return Component.text("No region saved yet.", NamedTextColor.GRAY);
        Component preview = Component.text("Preview (Y=" + firstY + ")\n", NamedTextColor.DARK_AQUA);
        int width = maxX - minX + 1, depth = maxZ - minZ + 1;
        if (width > 12 || depth > 12) return preview.append(Component.text("Selection is too large for a book grid. Review it with the wand in the world.", NamedTextColor.GRAY));
        Set<String> firstLayer = layers.getOrDefault(firstY, Set.of());
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                preview = preview.append(Component.text(firstLayer.contains(x + ":" + z) ? "X" : ".", firstLayer.contains(x + ":" + z) ? NamedTextColor.DARK_GREEN : NamedTextColor.GRAY));
            }
            if (z < maxZ) preview = preview.append(Component.text("\n", NamedTextColor.BLACK));
        }
        return preview;
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
