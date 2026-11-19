package dev.gateworks.command;

import dev.gateworks.selection.SelectionService;
import dev.gateworks.selection.SelectionSession;
import dev.gateworks.wand.WandItemService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class GateWorksCommand implements CommandExecutor, TabCompleter {
    private final SelectionService selections;
    private final WandItemService wandItems;

    public GateWorksCommand(SelectionService selections, WandItemService wandItems) {
        this.selections = selections;
        this.wandItems = wandItems;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gateworks.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use GateWorks.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This GateWorks command must be run by a player.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> giveWand(player);
            case "selection" -> showSelection(player);
            case "clear" -> clearSelection(player);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void giveWand(Player player) {
        ItemStack wand = wandItems.createWand();
        player.getInventory().addItem(wand).values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(Component.text("GateWorks selection wand added to your inventory.", NamedTextColor.GREEN));
    }

    private void showSelection(Player player) {
        SelectionSession session = selections.get(player.getUniqueId());
        player.sendMessage(Component.text("GateWorks selection: ", NamedTextColor.GOLD)
                .append(Component.text(session.getBlockCount() + " blocks", NamedTextColor.GREEN))
                .append(Component.text(" in " + session.getGroupCount() + " groups.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(
                "Selection data is temporary and will be lost when the server restarts in this prototype.",
                NamedTextColor.YELLOW));
    }

    private void clearSelection(Player player) {
        selections.clear(player.getUniqueId());
        player.sendMessage(Component.text("Your temporary GateWorks selection has been cleared.", NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("GateWorks 0.1.0 — selection prototype", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/gateworks wand", NamedTextColor.YELLOW)
                .append(Component.text(" — receive a selection wand", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gateworks selection", NamedTextColor.YELLOW)
                .append(Component.text(" — show current selection size", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gateworks clear", NamedTextColor.YELLOW)
                .append(Component.text(" — clear your temporary selection", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("wand", "selection", "clear", "help").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
