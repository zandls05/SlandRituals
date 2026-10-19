package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Elige la fase de la cinemática a editar: animaciones de partículas o textos y sonidos. */
public final class PhaseSelectMenu extends Menu {

    private static final int SLOT_BACK = 4;

    private final RitualManager manager;
    private final String ritualId;
    private final boolean animations;
    private final Supplier<Menu> back;
    private final Map<Integer, Ritual.PhaseId> slots = new HashMap<>();

    public PhaseSelectMenu(SlandRitualsPlugin plugin, RitualManager manager, String ritualId,
                           boolean animations, Supplier<Menu> back) {
        super(plugin);
        this.manager = manager;
        this.ritualId = ritualId;
        this.animations = animations;
        this.back = back;
        this.inv = Bukkit.createInventory(this, 27, plugin.raw(animations ? "menu-phases-anim-title" : "menu-phases-cine-title"));
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        int[] positions = {10, 12, 14, 16};
        Ritual.PhaseId[] ids = Ritual.PhaseId.values();
        for (int i = 0; i < ids.length; i++) {
            inv.setItem(positions[i], item(ids[i].icon, SlandRitualsPlugin.color("&d" + ids[i].label),
                    List.of(plugin.raw("menu-phases-click")), false));
            slots.put(positions[i], ids[i]);
        }
        inv.setItem(SLOT_BACK, item(Material.ARROW, plugin.raw("menu-back"), List.of(), false));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getSlot() == SLOT_BACK) {
            openLater(back.get(), p);
            return;
        }
        Ritual.PhaseId pid = slots.get(e.getSlot());
        if (pid == null) return;
        Supplier<Menu> self = () -> new PhaseSelectMenu(plugin, manager, ritualId, animations, back);
        if (animations) {
            manager.materializeAnimations(ritualId, pid);
            openLater(new AnimMenu(plugin, manager, ritualId, pid, self), p);
        } else {
            openLater(new FieldMenu(plugin, manager, ritualId, pid.label, Fields.phase(plugin, pid), self), p);
        }
    }
}
