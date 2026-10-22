package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Menú principal «Archivo de Rituales»: lista paginada con crear, editar, duplicar y eliminar. */
public final class ArchiveMenu extends Menu {

    private static final int ROWS = 6;
    private static final int PER_PAGE = 28;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_RELOAD = 47;
    private static final int SLOT_CREATE = 49;
    private static final int SLOT_CLOSE = 51;
    private static final int SLOT_NEXT = 53;

    private final RitualManager manager;
    private final Map<Integer, Ritual> slots = new HashMap<>();
    private int page;

    public ArchiveMenu(SlandRitualsPlugin plugin, RitualManager manager, int page) {
        super(plugin);
        this.manager = manager;
        this.page = Math.max(0, page);
        this.inv = Bukkit.createInventory(this, ROWS * 9, plugin.raw("menu-archive-title"));
        render();
    }

    private void render() {
        inv.clear();
        slots.clear();

        ItemStack frame = item(Material.PURPLE_STAINED_GLASS_PANE, " ", List.of(), false);
        ItemStack inner = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < inv.getSize(); i++) {
            boolean edge = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            inv.setItem(i, edge ? frame : inner);
        }

        List<Ritual> all = new ArrayList<>(manager.getRituals());
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) page = pages - 1;

        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            Ritual r = all.get(start + i);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            inv.setItem(slot, ritualItem(r));
            slots.put(slot, r);
        }

        List<String> header = new ArrayList<>();
        header.add(plugin.raw("menu-archive-count", "count", String.valueOf(all.size())));
        header.add(plugin.raw("menu-archive-page", "page", String.valueOf(page + 1), "pages", String.valueOf(pages)));
        header.add(" ");
        header.addAll(wrap(plugin.raw("menu-archive-header-lore")));
        inv.setItem(4, item(Material.ENCHANTED_BOOK, plugin.raw("menu-archive-header"), header, true));
        if (page > 0) {
            inv.setItem(SLOT_PREV, item(Material.ARROW, plugin.raw("menu-prev"),
                    List.of(plugin.raw("menu-page-go", "page", String.valueOf(page))), false));
        }
        if (page < pages - 1) {
            inv.setItem(SLOT_NEXT, item(Material.ARROW, plugin.raw("menu-next"),
                    List.of(plugin.raw("menu-page-go", "page", String.valueOf(page + 2))), false));
        }
        inv.setItem(SLOT_RELOAD, item(Material.REDSTONE, plugin.raw("menu-archive-reload"),
                wrap(plugin.raw("menu-archive-reload-lore")), false));
        inv.setItem(SLOT_CREATE, item(Material.EMERALD, plugin.raw("menu-archive-create"),
                wrap(plugin.raw("menu-archive-create-lore")), true));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, plugin.raw("menu-close"),
                wrap(plugin.raw("menu-close-lore")), false));
    }

    private ItemStack ritualItem(Ritual r) {
        List<String> lore = new ArrayList<>(r.description());
        if (!lore.isEmpty()) lore.add(" ");
        lore.addAll(Lore.details(plugin, manager, r));
        lore.add(" ");
        lore.add(plugin.raw("menu-archive-click-edit"));
        lore.add(plugin.raw("menu-archive-click-dup"));
        lore.add(plugin.raw("menu-archive-click-del"));
        return item(r.icon(), r.name(), lore, false);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();

        if (slot == SLOT_CLOSE) {
            closeLater(p);
        } else if (slot == SLOT_PREV && page > 0) {
            page--;
            render();
        } else if (slot == SLOT_NEXT) {
            page++;
            render();
        } else if (slot == SLOT_RELOAD) {
            plugin.reloadAll();
            p.sendMessage(plugin.msg("reload"));
            render();
        } else if (slot == SLOT_CREATE) {
            askNewRitual(p);
        } else {
            Ritual r = slots.get(slot);
            if (r == null) return;
            if (e.isShiftClick() && e.isRightClick()) confirmDelete(p, r);
            else if (e.isRightClick()) askDuplicate(p, r);
            else {
                manager.beginEdit(p, r.id());
                openLater(new EditorMenu(plugin, manager, r.id()), p);
            }
        }
    }

    private void reopen(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> new ArchiveMenu(plugin, manager, page).open(p));
    }

    private void askNewRitual(Player p) {
        closeLater(p);
        plugin.chat().ask(p, plugin.raw("prompt-new-id"), input -> {
            String id = input.toLowerCase(Locale.ROOT);
            if (!manager.validId(id)) return plugin.raw("invalid-id");
            if (manager.store().exists(id)) return plugin.raw("ritual-exists", "ritual", id);
            manager.createRitual(id);
            p.sendMessage(plugin.msg("ritual-created", "ritual", id));
            manager.beginEdit(p, id);
            Bukkit.getScheduler().runTask(plugin, () -> new EditorMenu(plugin, manager, id).open(p));
            return null;
        }, () -> reopen(p));
    }

    private void askDuplicate(Player p, Ritual src) {
        closeLater(p);
        plugin.chat().ask(p, plugin.raw("prompt-duplicate-id", "ritual", src.id()), input -> {
            String id = input.toLowerCase(Locale.ROOT);
            if (!manager.validId(id)) return plugin.raw("invalid-id");
            if (manager.store().exists(id)) return plugin.raw("ritual-exists", "ritual", id);
            manager.duplicateRitual(src.id(), id);
            p.sendMessage(plugin.msg("ritual-duplicated", "ritual", src.id(), "new", id));
            reopen(p);
            return null;
        }, () -> reopen(p));
    }

    private void confirmDelete(Player p, Ritual r) {
        String question = plugin.raw("menu-confirm-delete", "ritual", r.name());
        List<String> details = new ArrayList<>();
        details.addAll(wrap(plugin.raw("menu-confirm-delete-lore1", "altars", String.valueOf(manager.altarsOf(r.id()).size()))));
        details.addAll(wrap(plugin.raw("menu-confirm-delete-lore2")));
        details.add(" ");
        details.add(plugin.raw("menu-confirm-delete-lore3"));
        Bukkit.getScheduler().runTask(plugin, () -> new ConfirmMenu(plugin, question, details, () -> {
            manager.deleteRitual(r.id());
            p.sendMessage(plugin.msg("ritual-deleted", "ritual", r.id()));
            reopen(p);
        }, () -> reopen(p)).open(p));
    }
}
