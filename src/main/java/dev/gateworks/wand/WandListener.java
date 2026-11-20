package dev.gateworks.wand;

import dev.gateworks.selection.BlockPosition;
import dev.gateworks.selection.SelectionService;
import dev.gateworks.selection.SelectionSession;
import dev.gateworks.trigger.TriggerWorkflowService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Handles the initial selection-wand gestures. */
public final class WandListener implements Listener {
    private static final int MAX_CUBOID_BLOCKS = 4096;
    private final SelectionService selections;
    private final WandItemService wandItems;
    private final TriggerWorkflowService triggerWorkflow;

    public WandListener(dev.gateworks.GateWorksPlugin plugin, SelectionService selections,
                        WandItemService wandItems, TriggerWorkflowService triggerWorkflow) {
        this.selections = selections;
        this.wandItems = wandItems;
        this.triggerWorkflow = triggerWorkflow;
    }

    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !wandItems.isWand(event.getItem())) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        Player player = event.getPlayer();
        if (!player.hasPermission("gateworks.admin")) {
            send(player, "You do not have permission to use the GateWorks wand.", NamedTextColor.RED);
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (triggerWorkflow.isAwaitingButton(player)) {
            triggerWorkflow.attachButton(player, clicked);
            return;
        }
        if (triggerWorkflow.isTriggerDeviceBlock(clicked)) {
            if (action == Action.LEFT_CLICK_BLOCK) triggerWorkflow.selectTriggerDevice(player, clicked);
            else triggerWorkflow.clearPendingTriggerDevice(player);
            return;
        }
        BlockPosition position = new BlockPosition(clicked.getWorld().getUID(),
                clicked.getX(), clicked.getY(), clicked.getZ());
        SelectionSession session = selections.get(player.getUniqueId());

        if (action == Action.LEFT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                BlockPosition anchor = session.getAnchor();
                if (anchor == null || !anchor.worldId().equals(position.worldId())) {
                    session.addSingle(position);
                    session.setAnchor(position);
                    send(player, "First corner selected. Shift-left-click a second block to create a cuboid.", NamedTextColor.YELLOW);
                } else if (session.addCuboid(anchor, position, MAX_CUBOID_BLOCKS)) {
                    session.setAnchor(position);
                    send(player, "Cuboid added. " + session.getBlockCount() + " blocks in " + session.getGroupCount() + " groups.", NamedTextColor.GREEN);
                } else {
                    send(player, "That cuboid is too large. The current limit is " + MAX_CUBOID_BLOCKS + " blocks.", NamedTextColor.RED);
                }
            } else {
                session.addSingle(position);
                session.setAnchor(position);
                send(player, "Block added. " + session.getBlockCount() + " blocks in " + session.getGroupCount() + " groups.", NamedTextColor.GREEN);
            }
            feedback(clicked);
            if (triggerWorkflow.isEditingRegion(player)) triggerWorkflow.refreshTriggerBook(player);
            else triggerWorkflow.refreshSelectionBook(player);
            return;
        }

        if (player.isSneaking()) {
            int removed = session.removeGroupAt(position);
            send(player, removed == 0 ? "That block is not part of the current selection."
                    : "Removed selection group (" + removed + " blocks).",
                    removed == 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
        } else {
            boolean removed = session.removeBlock(position);
            send(player, removed ? "Block removed. " + session.getBlockCount() + " blocks remain."
                    : "That block is not part of the current selection.",
                    removed ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
        }
        feedback(clicked);
        if (triggerWorkflow.isEditingRegion(player)) triggerWorkflow.refreshTriggerBook(player);
        else triggerWorkflow.refreshSelectionBook(player);
    }

    private void feedback(Block block) {
        block.getWorld().spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 0.65, 0.5),
                6, 0.18, 0.18, 0.18, 0.01);
    }

    private void send(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text("[GateWorks] ", NamedTextColor.GOLD)
                .append(Component.text(message, color)));
    }
}
