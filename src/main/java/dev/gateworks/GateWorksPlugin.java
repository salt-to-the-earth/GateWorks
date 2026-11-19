package dev.gateworks;

import dev.gateworks.command.GateWorksCommand;
import dev.gateworks.selection.SelectionService;
import dev.gateworks.wand.WandItemService;
import dev.gateworks.wand.WandListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** GateWorks plugin entry point. */
public final class GateWorksPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        SelectionService selectionService = new SelectionService();
        WandItemService wandItemService = new WandItemService(this);

        PluginCommand command = getCommand("gateworks");
        if (command == null) {
            getLogger().severe("The /gateworks command is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GateWorksCommand executor = new GateWorksCommand(selectionService, wandItemService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getServer().getPluginManager().registerEvents(
                new WandListener(selectionService, wandItemService), this);

        getLogger().info("GateWorks 0.1.0 enabled. Selection-wand prototype is ready.");
    }

    @Override
    public void onDisable() {
        getLogger().info("GateWorks disabled.");
    }
}
