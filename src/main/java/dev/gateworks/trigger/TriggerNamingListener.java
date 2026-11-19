package dev.gateworks.trigger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

/** Captures the one chat response used to name a new trigger. */
public final class TriggerNamingListener implements Listener {
    private final TriggerWorkflowService workflow;
    public TriggerNamingListener(TriggerWorkflowService workflow) { this.workflow = workflow; }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!workflow.isNaming(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        String message = event.getMessage();
        Player player = event.getPlayer();
        player.getScheduler().run(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), task ->
                workflow.acceptName(player, message), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Pending naming is intentionally retained only until disconnect; no partial trigger is created.
    }
}
