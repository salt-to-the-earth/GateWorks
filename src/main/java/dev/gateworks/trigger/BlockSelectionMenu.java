package dev.gateworks.trigger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Temporary, read-only Creative-style material browser. The Trigger Book remains
 * the configuration interface; this menu is only used to choose a region block.
 */
public final class BlockSelectionMenu implements Listener {
    private static final int PAGE_SIZE = 36;
    private static final Set<Material> SPECIAL_MATERIALS = EnumSet.of(
            Material.AIR, Material.BARRIER, Material.LIGHT,
            Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY,
            Material.END_PORTAL_FRAME, Material.STRUCTURE_VOID, Material.STRUCTURE_BLOCK,
            Material.JIGSAW, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.MOVING_PISTON, Material.PISTON_HEAD,
            Material.SPAWNER
    );

    private final JavaPlugin plugin;
    private final TriggerWorkflowService workflow;

    private enum Category {
        BUILDING("Building", Material.BRICKS),
        NATURAL("Natural", Material.GRASS_BLOCK),
        WOOD("Wood", Material.OAK_LOG),
        REDSTONE("Redstone", Material.REDSTONE),
        FUNCTIONAL("Functional", Material.CRAFTING_TABLE),
        DECORATIVE("Decorative", Material.FLOWER_POT),
        SPECIAL("Special", Material.BARRIER);

        private final String label;
        private final Material icon;

        Category(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        private final String triggerId;
        private final String slot;
        private Category category = Category.BUILDING;
        private int page;
        private Inventory inventory;

        private MenuHolder(String triggerId, String slot) {
            this.triggerId = triggerId;
            this.slot = slot;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public BlockSelectionMenu(JavaPlugin plugin, TriggerWorkflowService workflow) {
        this.plugin = plugin;
        this.workflow = workflow;
    }

    public static boolean isSelectableMaterial(Material material) {
        return material != null && (material == Material.AIR
                || (material.isBlock() && material != Material.CAVE_AIR && material != Material.VOID_AIR));
    }

    public void open(Player player, String triggerId, String slot) {
        if (!slot.equalsIgnoreCase("primary") && !slot.equalsIgnoreCase("secondary")) {
            player.sendMessage(Component.text("Unknown block selection slot.", NamedTextColor.RED));
            return;
        }
        MenuHolder holder = new MenuHolder(triggerId, slot.toLowerCase(Locale.ROOT));
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Choose " + titleCase(slot) + " Material"));
        holder.inventory = inventory;
        render(holder);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= 54) return;

        if (rawSlot >= 0 && rawSlot < Category.values().length) {
            holder.category = Category.values()[rawSlot];
            holder.page = 0;
            render(holder);
            player.updateInventory();
            return;
        }

        if (rawSlot == 8 || rawSlot == 45) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                workflow.refreshTriggerBook(player, holder.triggerId);
            });
            return;
        }
        if (rawSlot == 48) {
            holder.page = Math.max(0, holder.page - 1);
            render(holder);
            player.updateInventory();
            return;
        }
        if (rawSlot == 50) {
            int pages = pageCount(holder.category);
            holder.page = Math.min(pages - 1, holder.page + 1);
            render(holder);
            player.updateInventory();
            return;
        }
        if (rawSlot < 9 || rawSlot > 44) return;

        List<Material> materials = materialsFor(holder.category);
        int index = holder.page * PAGE_SIZE + rawSlot - 9;
        if (index < 0 || index >= materials.size()) return;
        Material selected = materials.get(index);
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            workflow.selectRegionMaterial(player, holder.triggerId, holder.slot, selected.name());
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void render(MenuHolder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();

        for (Category category : Category.values()) {
            ItemStack tab = item(category.icon, category.label,
                    category == holder.category ? "Currently viewing" : "Click to browse this category",
                    category == holder.category);
            inventory.setItem(category.ordinal(), tab);
        }
        inventory.setItem(8, item(Material.BARRIER, "Close", "Return to the Trigger Book", false));

        List<Material> materials = materialsFor(holder.category);
        int start = holder.page * PAGE_SIZE;
        int end = Math.min(materials.size(), start + PAGE_SIZE);
        String selectedName = plugin.getConfig().getString("triggers." + holder.triggerId
                + ".region." + holder.slot + "-material", "");

        for (int index = start; index < end; index++) {
            Material material = materials.get(index);
            boolean selected = material.name().equalsIgnoreCase(selectedName);
            ItemStack icon = item(iconFor(material), (selected ? "✓ " : "") + prettyName(material),
                    selected ? "Currently selected" : "Click to select",
                    selected);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(material.name(), NamedTextColor.DARK_GRAY));
            if (selected) lore.add(Component.text("Current selection", NamedTextColor.GREEN));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(9 + index - start, icon);
        }

        inventory.setItem(45, item(Material.WRITTEN_BOOK, "Back to Trigger Book",
                "Close the selector without changing anything", false));
        inventory.setItem(48, item(Material.ARROW, "Previous Page", "Browse earlier blocks", false));
        int pages = pageCount(holder.category);
        inventory.setItem(49, item(Material.PAPER, "Page " + (holder.page + 1) + " / " + pages,
                materials.size() + " available block types in " + holder.category.label, false));
        inventory.setItem(50, item(Material.ARROW, "Next Page", "Browse more blocks", false));
        Material selected = Material.matchMaterial(selectedName == null ? "" : selectedName);
        inventory.setItem(53, item(selected == null ? Material.BARRIER : iconFor(selected),
                selected == null ? "No selection" : "Selected: " + prettyName(selected),
                selected == null ? "Choose a block above" : selected.name(), true));
    }

    private int pageCount(Category category) {
        return Math.max(1, (int) Math.ceil(materialsFor(category).size() / (double) PAGE_SIZE));
    }

    private List<Material> materialsFor(Category category) {
        return Arrays.stream(Material.values())
                .filter(BlockSelectionMenu::isSelectableMaterial)
                .filter(material -> belongsTo(material, category))
                // Keep related blocks together, closer to the way the vanilla
                // Creative inventory presents building materials, rather than
                // making players hunt through a purely alphabetical list.
                .sorted(Comparator.comparingInt((Material material) -> displayGroup(material, category))
                        .thenComparingInt(material -> variantOrder(material, category))
                        .thenComparing(BlockSelectionMenu::prettyName))
                .toList();
    }

    private int displayGroup(Material material, Category category) {
        String n = material.name();
        if (category == Category.SPECIAL) {
            return switch (material) {
                case AIR -> 0;
                case BARRIER, STRUCTURE_VOID -> 1;
                case LIGHT -> 2;
                case NETHER_PORTAL, END_PORTAL, END_GATEWAY, END_PORTAL_FRAME -> 3;
                case STRUCTURE_BLOCK, JIGSAW -> 4;
                case COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK -> 5;
                case MOVING_PISTON, PISTON_HEAD, SPAWNER -> 6;
                default -> 7;
            };
        }
        if (category == Category.WOOD) {
            if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) return 0;
            if (n.endsWith("_PLANKS")) return 1;
            if (n.endsWith("_LEAVES") || n.endsWith("_SAPLING") || n.endsWith("_PROPAGULE")) return 2;
            if (n.endsWith("_STAIRS") || n.endsWith("_SLAB")) return 3;
            if (n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE") || n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR")) return 4;
            if (n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE") || n.endsWith("_SIGN") || n.endsWith("_HANGING_SIGN")) return 5;
            return 6;
        }
        if (category == Category.NATURAL) {
            if (containsAny(n, "STONE", "DEEPSLATE", "TUFF", "ANDESITE", "DIORITE", "GRANITE", "BASALT")) return 0;
            if (containsAny(n, "DIRT", "GRASS", "MUD", "ROOTED_DIRT", "PODZOL", "MYCELIUM")) return 1;
            if (containsAny(n, "SAND", "GRAVEL", "CLAY", "TERRACOTTA")) return 2;
            if (containsAny(n, "ORE", "RAW_")) return 3;
            if (containsAny(n, "ICE", "SNOW")) return 4;
            if (containsAny(n, "LOG", "LEAVES", "SAPLING", "FLOWER", "MUSHROOM", "VINE", "MOSS", "CORAL", "KELP")) return 5;
            return 6;
        }
        if (category == Category.BUILDING) {
            if (containsAny(n, "BRICK", "STONE", "COBBLE", "DEEPSLATE", "TUFF", "BLACKSTONE", "BASALT")) return 0;
            if (containsAny(n, "SANDSTONE", "QUARTZ", "PRISMARINE", "PURPUR", "END_STONE")) return 1;
            if (containsAny(n, "PLANKS", "LOG", "WOOD", "STEM", "HYPHAE")) return 2;
            if (containsAny(n, "CONCRETE", "WOOL", "TERRACOTTA", "GLASS")) return 3;
            return 4;
        }
        if (category == Category.REDSTONE) {
            if (containsAny(n, "REDSTONE", "REPEATER", "COMPARATOR", "TARGET")) return 0;
            if (containsAny(n, "PISTON", "OBSERVER", "SLIME", "HONEY")) return 1;
            if (containsAny(n, "DISPENSER", "DROPPER", "HOPPER", "CHEST")) return 2;
            if (containsAny(n, "RAIL", "LEVER", "BUTTON", "PRESSURE_PLATE", "TRIPWIRE")) return 3;
            return 4;
        }
        if (category == Category.FUNCTIONAL) {
            if (containsAny(n, "CRAFTING", "FURNACE", "SMOKER", "BLAST_FURNACE", "ANVIL", "SMITHING")) return 0;
            if (containsAny(n, "CHEST", "BARREL", "SHULKER", "ENDER_CHEST")) return 1;
            if (containsAny(n, "TORCH", "LANTERN", "CAMPFIRE", "BEACON", "LAMP")) return 2;
            if (containsAny(n, "BED", "DOOR", "TRAPDOOR", "LADDER", "SCAFFOLDING")) return 3;
            return 4;
        }
        if (category == Category.DECORATIVE) {
            if (containsAny(n, "WOOL", "CARPET", "CONCRETE", "TERRACOTTA", "GLAZED")) return 0;
            if (containsAny(n, "GLASS", "PANE", "ICE")) return 1;
            if (containsAny(n, "FLOWER", "POT", "LEAVES", "SAPLING", "MUSHROOM")) return 2;
            if (containsAny(n, "BANNER", "CANDLE", "HEAD", "SKULL", "SIGN", "CORAL")) return 3;
            return 4;
        }
        return 0;
    }

    private int variantOrder(Material material, Category category) {
        String n = material.name();
        // Keep each wood family and common block variants adjacent.
        if (category == Category.WOOD || category == Category.BUILDING) {
            String[] woods = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
                    "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED"};
            for (int i = 0; i < woods.length; i++) if (n.startsWith(woods[i] + "_")) return i;
        }
        return 0;
    }

    private boolean belongsTo(Material material, Category category) {
        String name = material.name();
        if (category == Category.SPECIAL) return SPECIAL_MATERIALS.contains(material);
        if (SPECIAL_MATERIALS.contains(material)) return false;

        if (category == Category.WOOD) {
            return containsAny(name, "_LOG", "_WOOD", "_PLANKS", "_LEAVES", "_SAPLING",
                    "_STAIRS", "_SLAB", "_FENCE", "_FENCE_GATE", "_DOOR", "_TRAPDOOR",
                    "_BUTTON", "_SIGN", "_HANGING_SIGN", "_PRESSURE_PLATE", "_BOAT");
        }
        if (category == Category.REDSTONE) {
            return containsAny(name, "REDSTONE", "PISTON", "OBSERVER", "HOPPER", "DISPENSER",
                    "DROPPER", "REPEATER", "COMPARATOR", "DAYLIGHT_DETECTOR", "TRIPWIRE",
                    "RAIL", "TARGET", "TNT", "NOTE_BLOCK", "LEVER", "COPPER_BULB");
        }
        if (category == Category.NATURAL) {
            return containsAny(name, "STONE", "DIRT", "GRASS", "SAND", "GRAVEL", "CLAY",
                    "ICE", "SNOW", "WATER", "LAVA", "CORAL", "KELP", "SEAGRASS",
                    "FLOWER", "MUSHROOM", "VINE", "ROOTS", "MOSS", "ORE", "TUFF",
                    "DEEPSLATE", "DRIPSTONE", "AMETHYST", "BASALT", "NETHERRACK",
                    "END_STONE", "SOUL_SAND", "SOUL_SOIL");
        }
        if (category == Category.FUNCTIONAL) {
            return containsAny(name, "CHEST", "FURNACE", "CRAFTING", "ANVIL", "BEACON",
                    "BED", "LADDER", "TORCH", "LANTERN", "ENCHANTING", "BREWING",
                    "SMITHING", "LECTERN", "BELL", "BARREL", "SHULKER", "CAMPFIRE",
                    "CAULDRON", "DOOR", "TRAPDOOR", "BUTTON", "PRESSURE_PLATE");
        }
        if (category == Category.DECORATIVE) {
            return containsAny(name, "WOOL", "CARPET", "CONCRETE", "TERRACOTTA", "GLAZED",
                    "GLASS", "PANE", "BANNER", "CANDLE", "HEAD", "SKULL", "POT",
                    "DECORATED", "HANGING_SIGN", "SIGN", "CORAL_FAN");
        }
        return true;
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) if (value.contains(fragment)) return true;
        return false;
    }

    private static Material iconFor(Material material) {
        // AIR is a valid region target, but ItemStack(AIR) is an empty stack
        // and therefore has no ItemMeta. Show it with a barrier icon instead.
        if (material == Material.AIR) return Material.BARRIER;
        if (material.isItem()) return material;
        return switch (material) {
            case AIR -> Material.BARRIER;
            case NETHER_PORTAL -> Material.OBSIDIAN;
            case END_PORTAL -> Material.END_PORTAL_FRAME;
            case END_GATEWAY -> Material.END_STONE;
            case LIGHT -> Material.GLOWSTONE;
            case WATER, BUBBLE_COLUMN -> Material.WATER_BUCKET;
            case LAVA -> Material.LAVA_BUCKET;
            case FIRE, SOUL_FIRE -> Material.FLINT_AND_STEEL;
            case MOVING_PISTON, PISTON_HEAD -> Material.PISTON;
            default -> Material.BARRIER;
        };
    }

    private ItemStack item(Material material, String name, String description, boolean highlight) {
        // Keep menu controls valid even if a future icon has no item representation.
        Material iconMaterial = material == null || material == Material.AIR || !material.isItem()
                ? Material.BARRIER : material;
        ItemStack stack = new ItemStack(iconMaterial);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            // Defensive fallback: never let one invalid icon crash the entire menu.
            stack = new ItemStack(Material.BARRIER);
            meta = stack.getItemMeta();
        }
        if (meta == null) {
            plugin.getLogger().warning("Could not create menu icon metadata for " + name);
            return stack;
        }
        meta.displayName(Component.text(name, highlight ? NamedTextColor.GREEN : NamedTextColor.AQUA));
        meta.lore(List.of(Component.text(description, NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private static String prettyName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String titleCase(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
