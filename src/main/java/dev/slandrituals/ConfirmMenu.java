package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/** Pregunta de confirmación (sí / no) con un texto de aviso opcional. */
public final class ConfirmMenu extends Menu {

    private static final int SLOT_YES = 11;
    private static final int SLOT_NO = 15;

    private final Runnable onYes;
    private final Runnable onNo;

    public ConfirmMenu(SlandRitualsPlugin plugin, String question, Runnable onYes, Runnable onNo) {
        this(plugin, question, List.of(), onYes, onNo);
    }

    public ConfirmMenu(SlandRitualsPlugin plugin, String question, List<String> details, Runnable onYes, Runnable onNo) {
        super(plugin);
        this.onYes = onYes;
        this.onNo = onNo;
        this.inv = Bukkit.createInventory(this, 27, plugin.raw("menu-confirm-title"));
        for (int i = 0; i < 27; i++) inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false));
        inv.setItem(4, item(Material.PAPER, question, details, false));
        inv.setItem(SLOT_YES, item(Material.LIME_CONCRETE, plugin.raw("menu-confirm-yes"),
                wrap(plugin.raw("menu-confirm-yes-lore")), false));
        inv.setItem(SLOT_NO, item(Material.RED_CONCRETE, plugin.raw("menu-confirm-no"),
                wrap(plugin.raw("menu-confirm-no-lore")), false));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getSlot() == SLOT_YES) {
            closeLater(p);
            Bukkit.getScheduler().runTask(plugin, onYes);
        } else if (e.getSlot() == SLOT_NO) {
            closeLater(p);
            Bukkit.getScheduler().runTask(plugin, onNo);
        }
    }
}
