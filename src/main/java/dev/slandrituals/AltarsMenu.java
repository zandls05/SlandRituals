package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Altares de un ritual: estado, teletransporte, reinicio, eliminación y creación en el bloque que miras. */
public final class AltarsMenu extends Menu {

    private static final int SLOT_BACK = 4;
    private static final int SLOT_CREATE = 49;

    private final RitualManager manager;
    private final String ritualId;
    private final Supplier<Menu> back;
    private final Map<Integer, Altar> slots = new HashMap<>();

    public AltarsMenu(SlandRitualsPlugin plugin, RitualManager manager, String ritualId, Supplier<Menu> back) {
        super(plugin);
        this.manager = manager;
        this.ritualId = ritualId;
        this.back = back;
        this.inv = Bukkit.createInventory(this, 54, plugin.raw("menu-altars-title"));
        render();
    }

    private void render() {
        inv.clear();
        slots.clear();
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        Ritual r = manager.getRitual(ritualId);
        List<Altar> list = manager.altarsOf(ritualId);
        for (int i = 0; i < list.size() && i < 28; i++) {
            Altar a = list.get(i);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            List<String> lore = new ArrayList<>();
            lore.add(plugin.raw("menu-altars-loc", "world", a.world, "x", String.valueOf(a.x),
                    "y", String.valueOf(a.y), "z", String.valueOf(a.z)));
            if (r != null) lore.add(plugin.raw("menu-altars-ritual", "ritual", r.name()));
            lore.add(plugin.raw("menu-archive-status", "status", manager.statusLabel(a)));
            if (r != null) {
                int need = Lore.totalItems(r);
                int have = 0;
                for (Map.Entry<Material, Integer> e : r.offerings().entrySet()) {
                    have += Math.min(e.getValue(), a.delivered.getOrDefault(e.getKey(), 0));
                }
                lore.add(plugin.raw("menu-altars-progress", "have", String.valueOf(have), "need", String.valueOf(need),
                        "percent", String.valueOf(need == 0 ? 0 : have * 100 / need)));
            }
            if (a.state == Altar.State.CHARGING || a.state == Altar.State.ACTIVE) {
                String who = a.starter == null ? null : Bukkit.getOfflinePlayer(a.starter).getName();
                lore.add(plugin.raw("menu-altars-starter", "player", who == null ? "?" : who));
                if (a.boss != null && a.boss.isValid()) lore.add(plugin.raw("menu-altars-boss"));
            }
            lore.add(" ");
            lore.add(plugin.raw("menu-altars-click-tp"));
            lore.add(plugin.raw("menu-altars-click-reset"));
            lore.add(plugin.raw("menu-altars-click-del"));
            inv.setItem(slot, item(Material.LODESTONE, plugin.raw("menu-altars-name", "n", String.valueOf(i + 1)), lore, false));
            slots.put(slot, a);
        }
        List<String> create = new ArrayList<>(wrap(plugin.raw("menu-altars-create-lore")));
        create.add(" ");
        create.add(plugin.raw("menu-altars-count", "count", String.valueOf(list.size())));
        inv.setItem(SLOT_CREATE, item(Material.EMERALD, plugin.raw("menu-altars-create"), create, true));
        inv.setItem(SLOT_BACK, backButton());
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();

        if (slot == SLOT_BACK) {
            openLater(back.get(), p);
        } else if (slot == SLOT_CREATE) {
            Ritual r = manager.getRitual(ritualId);
            Block b = p.getTargetBlockExact(6);
            if (r == null) return;
            if (b == null || b.getType().isAir()) p.sendMessage(plugin.msg("not-looking"));
            else if (manager.createAltar(b, r)) {
                p.sendMessage(plugin.msg("altar-created", "ritual", r.name()));
                render();
            } else p.sendMessage(plugin.msg("altar-exists"));
        } else {
            Altar a = slots.get(slot);
            if (a == null) return;
            if (e.isShiftClick() && e.isRightClick()) {
                manager.removeAltar(a);
                p.sendMessage(plugin.msg("altar-removed"));
                render();
            } else if (e.isRightClick()) {
                manager.resetAltar(a);
                p.sendMessage(plugin.msg("altar-reset"));
                render();
            } else {
                Location c = a.center();
                if (c == null) {
                    p.sendMessage(plugin.msg("world-missing"));
                } else {
                    closeLater(p);
                    Bukkit.getScheduler().runTask(plugin, () -> p.teleport(c.clone().add(0, 1, 0)));
                }
            }
        }
    }
}
