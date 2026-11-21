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
import dev.gateworks.trigger.StatusListenerService;

import java.util.List;

public final class GateWorksCommand implements CommandExecutor, TabCompleter {
    private final SelectionService selections;
    private final WandItemService wandItems;
    private final TriggerWorkflowService triggerWorkflow;
    private final StatusListenerService statusListener;

    public GateWorksCommand(dev.gateworks.GateWorksPlugin plugin, SelectionService selections,
                            WandItemService wandItems, TriggerWorkflowService triggerWorkflow, StatusListenerService statusListener) {
        this.selections = selections;
        this.wandItems = wandItems;
        this.triggerWorkflow = triggerWorkflow;
        this.statusListener = statusListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gateworks.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use GateWorks.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (sender instanceof Player player) triggerWorkflow.giveHelpBook(player);
            else sendHelp(sender);
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
            case "edit-input" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("The GateWorks edit link was incomplete. Reopen the Trigger Book and try again.", NamedTextColor.RED));
                } else {
                    String value = args.length > 3
                            ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                            : "";
                    triggerWorkflow.acceptSuggestedEdit(player, args[1], args[2], value);
                }
            }
            case "listener-test" -> runListenerTest(player, args);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void runListenerTest(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /gateworks listener-test <trigger name>", NamedTextColor.YELLOW));
            return;
        }
        String requestedName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                .replaceAll("^[\\\"]|[\\\"]$", "").trim();
        String triggerId = null;
        var triggers = player.getServer().getPluginManager().getPlugin("GateWorks");
        if (triggers instanceof dev.gateworks.GateWorksPlugin plugin) {
            var section = plugin.getConfig().getConfigurationSection("triggers");
            if (section != null) for (String id : section.getKeys(false)) {
                if (requestedName.equalsIgnoreCase(plugin.getConfig().getString("triggers." + id + ".name", ""))) {
                    triggerId = id;
                    break;
                }
            }
        }
        if (triggerId == null) {
            player.sendMessage(Component.text("No trigger book was found with the name: " + requestedName, NamedTextColor.RED));
            return;
        }
        var result = statusListener.testOnce(triggerId);
        player.sendMessage(Component.text("=== GateWorks Listener Diagnostic ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Trigger: " + requestedName, NamedTextColor.GRAY));
        player.sendMessage(Component.text("Command attempted: " + result.command(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Captured response:", NamedTextColor.GOLD));
        player.sendMessage(Component.text(result.response().isBlank() ? "<no response captured>" : result.response(), NamedTextColor.WHITE));
        player.sendMessage(Component.text("Expected State A: " + (result.stateA().isBlank() ? "<not set>" : result.stateA()), NamedTextColor.AQUA));
        player.sendMessage(Component.text("Expected State B: " + (result.stateB().isBlank() ? "<not set>" : result.stateB()), NamedTextColor.AQUA));
        NamedTextColor resultColor = result.result().startsWith("MATCHED STATE") ? NamedTextColor.GREEN : NamedTextColor.RED;
        player.sendMessage(Component.text("Result: " + result.result(), resultColor));
        player.sendMessage(Component.text("Diagnostic only: no toggle, region, or listener health settings were changed.", NamedTextColor.GRAY));
    }

    private void handleBook(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Use /gateworks book refresh|rename <trigger-id>|edit-selection <trigger-id>|attach <trigger-id>|button <trigger-id>", NamedTextColor.YELLOW));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "open-trigger" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.openTriggerBook(player, args[2]);
            }
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
            case "material-menu" -> {
                if (args.length < 4) player.sendMessage(Component.text("Use /gateworks book material-menu <trigger-id> <primary|secondary>.", NamedTextColor.RED));
                else triggerWorkflow.openMaterialSelector(player, args[2], args[3]);
            }
            case "material" -> {
                if (args.length < 5) player.sendMessage(Component.text("Use /gateworks book material <trigger-id> <primary|secondary> <MATERIAL_ID>.", NamedTextColor.RED));
                else triggerWorkflow.selectRegionMaterial(player, args[2], args[3], args[4]);
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
            case "listener" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.toggleListener(player, args[2]);
            }
            case "listener-setting" -> {
                if (args.length < 4) player.sendMessage(Component.text("Use /gateworks book listener-setting <trigger-id> <command|state-a|state-b|interval>.", NamedTextColor.RED));
                else triggerWorkflow.promptForListenerSetting(player, args[2], args[3]);
            }
            case "listener-error" -> {
                if (args.length < 3) player.sendMessage(Component.text("Missing trigger ID.", NamedTextColor.RED));
                else triggerWorkflow.showListenerError(player, args[2]);
            }
            case "show-setting" -> {
                if (args.length < 4) player.sendMessage(Component.text("Use /gateworks book show-setting <trigger-id> <setting>.", NamedTextColor.RED));
                else triggerWorkflow.showCurrentSetting(player, args[2], args[3]);
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
            return List.of("wand", "selection", "clear", "book", "listener-test", "help").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
