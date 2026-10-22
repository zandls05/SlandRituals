package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menú que se abre al hacer clic en un altar: muestra las ofrendas y deja entregarlas. */
public final class AltarMenu extends Menu {

    private final RitualManager manager;
    private final Altar altar;
    private final Ritual ritual;
    private final int rows;
    private final Map<Integer, Material> slots = new HashMap<>();
    private Player viewer;

    public AltarMenu(SlandRitualsPlugin plugin, RitualManager manager, Altar altar, Ritual ritual) {
        super(plugin);
        this.manager = manager;
        this.altar = altar;
        this.ritual = ritual;
        this.rows = ritual.offerings().size() <= 7 ? 3 : 5;
        this.inv = Bukkit.createInventory(this, rows * 9, plugin.raw("menu-altar-title", "ritual", ritual.name()));
        render();
    }

    public Altar altar() {
        return altar;
    }

    @Override
    public void open(Player p) {
        this.viewer = p;
        render(); // para mostrar cuántos objetos lleva encima este jugador
        super.open(p);
    }

    /** Objetos de este tipo que lleva el jugador, o -1 si aún no se sabe quién mira el menú. */
    private int owned(Material m) {
        if (viewer == null) return -1;
        int n = 0;
        for (ItemStack it : viewer.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m) n += it.getAmount();
        }
        return n;
    }

    /** Redibuja todo el contenido con el progreso actual. */
    public void render() {
        inv.clear();
        slots.clear();

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        List<Material> mats = new ArrayList<>(ritual.offerings().keySet());
        List<Integer> positions = new ArrayList<>();
        if (rows == 3) {
            int start = 9 + (9 - mats.size()) / 2;
            for (int i = 0; i < mats.size(); i++) positions.add(start + i);
        } else {
            for (int r = 1; r <= rows - 2; r++) {
                for (int c = 1; c <= 7; c++) positions.add(r * 9 + c);
            }
        }

        int totalNeed = 0, totalHave = 0;
        for (Material m : mats) {
            int need = ritual.offerings().get(m);
            totalNeed += need;
            totalHave += Math.min(need, altar.delivered.getOrDefault(m, 0));
        }

        for (int i = 0; i < mats.size() && i < positions.size(); i++) {
            Material m = mats.get(i);
            int need = ritual.offerings().get(m);
            int have = Math.min(need, altar.delivered.getOrDefault(m, 0));
            boolean done = have >= need;
            String pretty = RitualManager.pretty(m);

            List<String> lore = new ArrayList<>();
            lore.add(plugin.raw("menu-offer-progress", "have", String.valueOf(have), "need", String.valueOf(need)));
            lore.add(" ");
            if (done) {
                lore.addAll(wrap(plugin.raw("menu-offer-done")));
            } else {
                lore.add(plugin.raw("menu-offer-missing", "missing", String.valueOf(need - have)));
                int owned = owned(m);
                if (owned > 0) lore.add(plugin.raw("menu-offer-inventory", "owned", String.valueOf(owned)));
                else if (owned == 0) lore.addAll(wrap(plugin.raw("menu-offer-inventory-none")));
                lore.add(" ");
                lore.add(plugin.raw("menu-offer-click"));
                lore.add(plugin.raw("menu-offer-shift"));
            }

            ItemStack it = item(m, plugin.raw(done ? "menu-offer-name-done" : "menu-offer-name", "item", pretty), lore, done);
            it.setAmount(Math.max(1, Math.min(64, need - have)));
            inv.setItem(positions.get(i), it);
            slots.put(positions.get(i), m);
        }

        List<String> info = new ArrayList<>(ritual.description());
        if (!info.isEmpty()) info.add(" ");
        info.add(plugin.raw("menu-info-progress", "percent", String.valueOf(totalNeed == 0 ? 0 : totalHave * 100 / totalNeed),
                "have", String.valueOf(totalHave), "need", String.valueOf(totalNeed)));
        info.add(" ");
        info.add(plugin.raw("menu-info-boss", "boss", ritual.boss().mythicId().isEmpty() ? ritual.boss().name() : ritual.bossLabel()));
        if (ritual.boss().mythicId().isEmpty()) {
            info.add(plugin.raw("menu-info-health", "health", Lore.num(ritual.boss().health())));
            info.add(plugin.raw("menu-info-damage", "damage", Lore.num(ritual.boss().damage())));
        }
        info.add(plugin.raw("menu-info-countdown", "time", String.valueOf(ritual.summonSeconds())));
        info.add(plugin.raw("menu-info-cooldown", "time", RitualManager.formatTime(ritual.cooldown())));
        info.add(plugin.raw("menu-info-conditions", "conditions", Lore.conditions(plugin, ritual)));
        info.add(" ");
        info.addAll(wrap(plugin.raw("menu-info-hint")));
        inv.setItem((rows - 1) * 9 + 4, item(ritual.icon(), plugin.raw("menu-info-name", "ritual", ritual.name()), info, true));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() != inv) return;
        Material m = slots.get(e.getSlot());
        if (m == null) return;
        manager.deposit((Player) e.getWhoClicked(), altar, m, e.isShiftClick());
    }
}
