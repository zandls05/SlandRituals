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
                lore.add(plugin.raw("menu-offer-done"));
            } else {
                lore.add(plugin.raw("menu-offer-click"));
                lore.add(plugin.raw("menu-offer-shift"));
            }

            ItemStack it = item(m, plugin.raw(done ? "menu-offer-name-done" : "menu-offer-name", "item", pretty), lore, done);
            it.setAmount(Math.max(1, Math.min(64, need - have)));
            inv.setItem(positions.get(i), it);
            slots.put(positions.get(i), m);
        }

        List<String> info = new ArrayList<>();
        info.add(plugin.raw("menu-info-progress", "percent", String.valueOf(totalNeed == 0 ? 0 : totalHave * 100 / totalNeed)));
        info.add(plugin.raw("menu-info-health", "health", String.valueOf((int) ritual.health())));
        info.add(plugin.raw("menu-info-countdown", "time", String.valueOf(ritual.countdown())));
        info.add(" ");
        info.add(plugin.raw("menu-info-hint"));
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
