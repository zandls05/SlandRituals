package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menú de administración: lista los rituales y permite crear/quitar altares sin escribir comandos. */
public final class AdminMenu extends Menu {

    private static final int ROWS = 5;
    private static final int SLOT_RELOAD = 38;
    private static final int SLOT_REMOVE = 40;
    private static final int SLOT_CLOSE = 42;

    private final RitualManager manager;
    private final Map<Integer, Ritual> slots = new HashMap<>();

    public AdminMenu(SlandRitualsPlugin plugin, RitualManager manager) {
        super(plugin);
        this.manager = manager;
        this.inv = Bukkit.createInventory(this, ROWS * 9, plugin.raw("menu-admin-title"));
        render();
    }

    private void render() {
        inv.clear();
        slots.clear();

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        List<Integer> positions = new ArrayList<>();
        for (int r = 1; r <= 3; r++) for (int c = 1; c <= 7; c++) positions.add(r * 9 + c);

        int idx = 0;
        for (Ritual r : manager.getRituals()) {
            if (idx >= positions.size()) break;
            long altars = manager.getAltars().stream().filter(a -> a.ritualId.equals(r.id())).count();

            List<String> lore = new ArrayList<>();
            lore.add(plugin.raw("menu-admin-item-stats", "health", String.valueOf((int) r.health()),
                    "damage", String.valueOf((int) r.damage())));
            lore.add(plugin.raw("menu-admin-item-altars", "count", String.valueOf(altars)));
            lore.add(" ");
            lore.add(plugin.raw("menu-admin-item-offers"));
            r.offerings().forEach((m, n) -> lore.add(plugin.raw("menu-admin-item-offer-line",
                    "item", RitualManager.pretty(m), "amount", String.valueOf(n))));
            lore.add(" ");
            lore.add(plugin.raw("menu-admin-item-click"));

            inv.setItem(positions.get(idx), item(r.icon(), r.name(), lore, false));
            slots.put(positions.get(idx), r);
            idx++;
        }

        inv.setItem(SLOT_RELOAD, item(Material.REDSTONE, plugin.raw("menu-admin-reload"), List.of(), false));
        inv.setItem(SLOT_REMOVE, item(Material.BARRIER, plugin.raw("menu-admin-remove"),
                List.of(plugin.raw("menu-admin-remove-lore")), false));
        inv.setItem(SLOT_CLOSE, item(Material.ARROW, plugin.raw("menu-admin-close"), List.of(), false));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();

        if (slot == SLOT_CLOSE) {
            closeLater(p);
        } else if (slot == SLOT_RELOAD) {
            plugin.reloadConfig();
            manager.loadRituals();
            p.sendMessage(plugin.msg("reload"));
            closeLater(p);
        } else if (slot == SLOT_REMOVE) {
            Block b = p.getTargetBlockExact(6);
            closeLater(p);
            if (b == null) p.sendMessage(plugin.msg("not-looking"));
            else if (manager.removeAltar(b)) p.sendMessage(plugin.msg("altar-removed"));
            else p.sendMessage(plugin.msg("not-altar"));
        } else {
            Ritual r = slots.get(slot);
            if (r == null) return;
            Block b = p.getTargetBlockExact(6);
            closeLater(p);
            if (b == null || b.getType().isAir()) p.sendMessage(plugin.msg("not-looking"));
            else if (manager.createAltar(b, r)) p.sendMessage(plugin.msg("altar-created", "ritual", r.name()));
            else p.sendMessage(plugin.msg("altar-exists"));
        }
    }
}
