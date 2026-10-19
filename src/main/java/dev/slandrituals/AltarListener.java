package dev.slandrituals;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class AltarListener implements Listener {

    private final SlandRitualsPlugin plugin;
    private final RitualManager manager;

    public AltarListener(SlandRitualsPlugin plugin, RitualManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getClickedBlock() == null) return;
        Altar altar = manager.getAltar(e.getClickedBlock());
        if (altar == null) return;

        e.setCancelled(true);
        if (!e.getPlayer().hasPermission("slandrituals.use")) {
            e.getPlayer().sendMessage(plugin.msg("no-use-permission"));
            return;
        }
        manager.interact(e.getPlayer(), altar, e.getPlayer().isSneaking());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (manager.getAltar(e.getBlock()) != null) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.msg("altar-protected"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> manager.getAltar(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> manager.getAltar(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (org.bukkit.block.Block b : e.getBlocks()) {
            if (manager.getAltar(b) != null) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (org.bukkit.block.Block b : e.getBlocks()) {
            if (manager.getAltar(b) != null) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Menu menu) {
            e.setCancelled(true);
            menu.onClick(e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        manager.handleDeath(e);
    }
}
