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
import dev.gateworks.trigger.TriggerWorkflowService;

import java.util.List;

public final class GateWorksCommand implements CommandExecutor, TabCompleter {
    private final SelectionService selections;
    private final WandItemService wandItems;
    private final TriggerWorkflowService triggerWorkflow;

    public GateWorksCommand(dev.gateworks.GateWorksPlugin plugin, SelectionService selections,
                            WandItemService wandItems, TriggerWorkflowService triggerWorkflow) {
        this.selections = selections;
        this.wandItems = wandItems;
        this.triggerWorkflow = triggerWorkflow;
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
        switch (args[0].toLowerCase()) {
            case "wand" -> giveWand(player);
            case "selection" -> showSelection(player);
            case "clear" -> clearSelection(player);
            case "book" -> handleBook(player, args);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void handleBook(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Use /gateworks book refresh|rename <trigger-id>|edit-selection <trigger-id>|attach <trigger-id>|button <trigger-id>", NamedTextColor.YELLOW));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "refresh" -> {
                triggerWorkflow.refreshSelectionBook(player);
                player.sendMessage(Component.text("Selection book refreshed.", NamedTextColor.GREEN));
            }
            case "rename" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.promptForName(player, args[2]);
            }
            case "edit-selection" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.beginRegionEdit(player, args[2]);
            }
            case "attach" -> {
                if (args.length < 3 || !triggerWorkflow.attachSelection(player, args[2])) {
                    player.sendMessage(Component.text("Trigger not found. Refresh the Selection Book and choose an existing trigger.", NamedTextColor.RED));
                }
            }
            case "save-region" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.saveRegionEdit(player, args[2]);
            }
            case "cancel-region" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.cancelRegionEdit(player, args[2]);
            }
            case "delete" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.deleteTrigger(player, args[2]);
            }
            case "button" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.beginButtonSelection(player, args[2]);
            }
            case "command" -> {
                if (args.length < 4) player.sendMessage(Component.text("Use /gateworks book command <trigger-id> <single|on|off>.", NamedTextColor.RED));
                else triggerWorkflow.promptForCommand(player, args[2], args[3]);
            }
            case "mode" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.toggleActionMode(player, args[2]);
            }
            case "protection" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.toggleProtection(player, args[2]);
            }
            case "protection-setting" -> {
                if (args.length < 4) player.sendMessage(Component.text("Use /gateworks book protection-setting <trigger-id> <cooldown|threshold|window|lockout>.", NamedTextColor.RED));
                else triggerWorkflow.promptForProtectionSetting(player, args[2], args[3]);
            }
            default -> player.sendMessage(Component.text("Unknown book action.", NamedTextColor.RED));
        }
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
        triggerWorkflow.refreshSelectionBook(player);
        player.sendMessage(Component.text("Your temporary GateWorks selection has been cleared.", NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("GateWorks — selection and trigger-book framework", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/gateworks wand", NamedTextColor.YELLOW)
                .append(Component.text(" — receive a selection wand", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gateworks selection", NamedTextColor.YELLOW)
                .append(Component.text(" — show current selection size", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gateworks clear", NamedTextColor.YELLOW)
                .append(Component.text(" — clear your temporary selection", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gateworks book", NamedTextColor.YELLOW)
                .append(Component.text(" — manage selection and trigger books", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("wand", "selection", "clear", "book", "help").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
