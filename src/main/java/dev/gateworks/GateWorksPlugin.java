package dev.gateworks;

import dev.gateworks.command.GateWorksCommand;
import dev.gateworks.selection.SelectionService;
import dev.gateworks.visual.SelectionVisualizer;
import dev.gateworks.wand.WandItemService;
import dev.gateworks.wand.WandListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** GateWorks plugin entry point. */
public final class GateWorksPlugin extends JavaPlugin {
    private SelectionService selectionService;
    private WandItemService wandItemService;
    private SelectionVisualizer selectionVisualizer;

    @Override
    public void onEnable() {
        selectionService = new SelectionService();
        wandItemService = new WandItemService(this);
        selectionVisualizer = new SelectionVisualizer(this, selectionService);
        selectionVisualizer.start();

        PluginCommand command = getCommand("gateworks");
        if (command == null) {
            getLogger().severe("The /gateworks command is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GateWorksCommand executor = new GateWorksCommand(this, selectionService, wandItemService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(
                new WandListener(this, selectionService, wandItemService), this);

        getLogger().info("GateWorks 0.1.1 enabled. Selection highlighting is active by default.");
    }

    @Override
    public void onDisable() {
        if (selectionVisualizer != null) selectionVisualizer.stop();
        getLogger().info("GateWorks disabled.");
    }
}
