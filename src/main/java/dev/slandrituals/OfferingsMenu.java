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
import java.util.function.Supplier;

/** Edita los objetos que hay que ofrendar: clic para sumar/restar cantidad, o añadir el objeto de la mano. */
public final class OfferingsMenu extends Menu {

    private static final int SLOT_BACK = 4;
    private static final int SLOT_ADD = 49;

    private final RitualManager manager;
    private final String ritualId;
    private final Supplier<Menu> back;
    private final Map<Integer, Material> slots = new HashMap<>();

    public OfferingsMenu(SlandRitualsPlugin plugin, RitualManager manager, String ritualId, Supplier<Menu> back) {
        super(plugin);
        this.manager = manager;
        this.ritualId = ritualId;
        this.back = back;
        this.inv = Bukkit.createInventory(this, 54, plugin.raw("menu-offerings-title"));
        render();
    }

    private void render() {
        inv.clear();
        slots.clear();
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        Ritual r = manager.getRitual(ritualId);
        if (r != null) {
            int i = 0;
            for (Map.Entry<Material, Integer> e : r.offerings().entrySet()) {
                if (i >= 28) break;
                int slot = (1 + i / 7) * 9 + 1 + i % 7;
                int amount = e.getValue();
                int stack = Math.max(1, e.getKey().getMaxStackSize());
                int total = Math.max(1, Lore.totalItems(r));
                List<String> lore = new ArrayList<>();
                lore.add(plugin.raw("menu-offerings-amount", "amount", String.valueOf(amount)));
                lore.add(plugin.raw("menu-offerings-stacks", "stacks", String.valueOf(amount / stack),
                        "rest", String.valueOf(amount % stack)));
                lore.add(plugin.raw("menu-offerings-share", "percent", String.valueOf(amount * 100 / total)));
                lore.add(plugin.raw("menu-offerings-id", "id", e.getKey().name()));
                lore.add(" ");
                lore.add(plugin.raw("menu-offerings-click-add"));
                lore.add(plugin.raw("menu-offerings-click-sub"));
                ItemStack it = item(e.getKey(), SlandRitualsPlugin.color("&e" + RitualManager.pretty(e.getKey())), lore, false);
                it.setAmount(Math.max(1, Math.min(64, e.getValue())));
                inv.setItem(slot, it);
                slots.put(slot, e.getKey());
                i++;
            }
        }
        List<String> add = new ArrayList<>(wrap(plugin.raw("menu-offerings-add-lore")));
        if (r != null) {
            add.add(" ");
            add.add(plugin.raw("menu-offerings-add-limit", "count", String.valueOf(r.offerings().size())));
        }
        inv.setItem(SLOT_ADD, item(Material.HOPPER, plugin.raw("menu-offerings-add"), add, true));
        inv.setItem(SLOT_BACK, backButton());
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        Ritual r = manager.getRitual(ritualId);
        if (r == null) {
            closeLater(p);
            return;
        }

        if (slot == SLOT_BACK) {
            openLater(back.get(), p);
        } else if (slot == SLOT_ADD) {
            Material hand = p.getInventory().getItemInMainHand().getType();
            if (hand.isAir()) p.sendMessage(plugin.msg("hold-item"));
            else if (r.offerings().containsKey(hand)) p.sendMessage(plugin.msg("offering-exists"));
            else if (r.offerings().size() >= 28) p.sendMessage(plugin.msg("offering-limit"));
            else {
                manager.editSet(ritualId, "offerings." + hand.name(), 1);
                render();
            }
        } else {
            Material m = slots.get(slot);
            if (m == null) return;
            int step = e.isShiftClick() ? 16 : 1;
            int now = r.offerings().get(m) + (e.isRightClick() ? -step : step);
            if (now > 2304) now = 2304;
            if (now <= 0) {
                if (r.offerings().size() <= 1) {
                    p.sendMessage(plugin.msg("offering-last"));
                    return;
                }
                manager.editSet(ritualId, "offerings." + m.name(), null);
            } else {
                manager.editSet(ritualId, "offerings." + m.name(), now);
            }
            render();
        }
    }
}
