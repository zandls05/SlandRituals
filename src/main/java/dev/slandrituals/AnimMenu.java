package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Animaciones de una fase: clic izquierdo las activa/desactiva, clic derecho ajusta partícula, radio, etc. */
public final class AnimMenu extends Menu {

    private static final int SLOT_BACK = 4;

    private final RitualManager manager;
    private final String ritualId;
    private final Ritual.PhaseId phase;
    private final Supplier<Menu> back;
    private final Map<Integer, AnimType> slots = new HashMap<>();

    public AnimMenu(SlandRitualsPlugin plugin, RitualManager manager, String ritualId, Ritual.PhaseId phase,
                    Supplier<Menu> back) {
        super(plugin);
        this.manager = manager;
        this.ritualId = ritualId;
        this.phase = phase;
        this.back = back;
        this.inv = Bukkit.createInventory(this, 36, plugin.raw("menu-anim-title", "phase", phase.label));
        render();
    }

    private boolean enabled(AnimType t) {
        ConfigurationSection s = manager.store().section(ritualId);
        if (s == null) return false;
        String base = "cinematic." + phase.key + ".animations." + t.key;
        if (s.getConfigurationSection(base) == null) return false;
        return s.getBoolean(base + ".enabled", true);
    }

    private void render() {
        inv.clear();
        slots.clear();
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        AnimType[] all = AnimType.values();
        for (int i = 0; i < all.length; i++) {
            AnimType t = all[i];
            int slot = 10 + (i / 4) * 9 + (i % 4) * 2;
            boolean on = enabled(t);
            List<String> lore = new ArrayList<>();
            lore.add(SlandRitualsPlugin.color("&7" + t.description));
            lore.add(" ");
            lore.add(plugin.raw(on ? "menu-anim-on" : "menu-anim-off"));
            lore.add(plugin.raw("menu-anim-click-toggle"));
            lore.add(plugin.raw("menu-anim-click-edit"));
            inv.setItem(slot, item(t.icon, SlandRitualsPlugin.color((on ? "&a" : "&7") + t.label), lore, on));
            slots.put(slot, t);
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
        AnimType t = slots.get(e.getSlot());
        if (t == null) return;
        String enabledPath = "cinematic." + phase.key + ".animations." + t.key + ".enabled";

        if (e.isRightClick()) {
            if (!enabled(t)) manager.editSet(ritualId, enabledPath, true);
            Supplier<Menu> self = () -> new AnimMenu(plugin, manager, ritualId, phase, back);
            openLater(new FieldMenu(plugin, manager, ritualId, t.label, Fields.animation(plugin, phase, t), self), p);
        } else {
            manager.editSet(ritualId, enabledPath, !enabled(t));
            render();
        }
    }
}
