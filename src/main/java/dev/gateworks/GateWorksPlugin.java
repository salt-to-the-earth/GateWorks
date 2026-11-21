package dev.gateworks;

import dev.gateworks.command.GateWorksCommand;
import dev.gateworks.selection.SelectionService;
import dev.gateworks.visual.SelectionVisualizer;
import dev.gateworks.wand.WandItemService;
import dev.gateworks.wand.WandListener;
import dev.gateworks.trigger.TriggerWorkflowService;
import dev.gateworks.trigger.TriggerNamingListener;
import dev.gateworks.trigger.TriggerActivationListener;
import dev.gateworks.trigger.BlockSelectionMenu;
import dev.gateworks.trigger.StatusListenerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** GateWorks plugin entry point. */
public final class GateWorksPlugin extends JavaPlugin {
    private SelectionService selectionService;
    private WandItemService wandItemService;
    private SelectionVisualizer selectionVisualizer;
    private TriggerWorkflowService triggerWorkflow;

    @Override
    public void onEnable() {
        selectionService = new SelectionService();
        wandItemService = new WandItemService(this);
        selectionVisualizer = new SelectionVisualizer(this, selectionService);
        triggerWorkflow = new TriggerWorkflowService(this, selectionService);
        BlockSelectionMenu blockSelectionMenu = new BlockSelectionMenu(this, triggerWorkflow);
        triggerWorkflow.setBlockSelectionMenu(blockSelectionMenu);
        selectionVisualizer.start();

        PluginCommand command = getCommand("gateworks");
        if (command == null) {
            getLogger().severe("The /gateworks command is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        StatusListenerService statusListener = new StatusListenerService(this);
        GateWorksCommand executor = new GateWorksCommand(this, selectionService, wandItemService, triggerWorkflow, statusListener);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(
                new WandListener(this, selectionService, wandItemService, triggerWorkflow), this);
        getServer().getPluginManager().registerEvents(new TriggerNamingListener(triggerWorkflow), this);
        getServer().getPluginManager().registerEvents(blockSelectionMenu, this);
        TriggerActivationListener activationListener = new TriggerActivationListener(this);
        getServer().getPluginManager().registerEvents(activationListener, this);
        activationListener.initializeExistingTriggers();
        statusListener.start();

        getLogger().info("GateWorks enabled. Selection tools, trigger books, and initial command actions are active.");
    }

    @Override
    public void onDisable() {
        if (selectionVisualizer != null) selectionVisualizer.stop();
        getLogger().info("GateWorks disabled.");
    }
}
