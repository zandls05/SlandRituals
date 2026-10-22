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
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Menú de edición de un ritual, organizado por categorías. Los cambios se guardan al instante. */
public final class EditorMenu extends Menu {

    private enum Cat {
        INFO(11, Material.NAME_TAG), ALTARS(13, Material.LODESTONE), OFFERINGS(15, Material.HOPPER),
        BOSS(20, Material.WITHER_SKELETON_SKULL), ANIMATIONS(22, Material.FIREWORK_STAR),
        CINEMATICS(24, Material.NOTE_BLOCK), TIMING(29, Material.CLOCK), CONDITIONS(31, Material.COMPARATOR),
        REWARDS(33, Material.EXPERIENCE_BOTTLE);

        final int slot;
        final Material icon;

        Cat(int slot, Material icon) {
            this.slot = slot;
            this.icon = icon;
        }
    }

    private static final int SLOT_SAVE = 47;
    private static final int SLOT_TEST = 49;
    private static final int SLOT_CANCEL = 51;
    private static final int SLOT_BACK = 53;

    private final RitualManager manager;
    private final String id;
    private final Map<Integer, Cat> cats = new HashMap<>();

    public EditorMenu(SlandRitualsPlugin plugin, RitualManager manager, String id) {
        super(plugin);
        this.manager = manager;
        this.id = id;
        Ritual r = manager.getRitual(id);
        this.inv = Bukkit.createInventory(this, 54, plugin.raw("menu-editor-title", "ritual", r == null ? id : r.name()));
        render();
    }

    private void render() {
        inv.clear();
        cats.clear();
        ItemStack frame = item(Material.PURPLE_STAINED_GLASS_PANE, " ", List.of(), false);
        ItemStack inner = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 54; i++) {
            boolean edge = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            inv.setItem(i, edge ? frame : inner);
        }

        Ritual r = manager.getRitual(id);
        if (r != null) {
            List<String> head = new ArrayList<>(r.description());
            if (!head.isEmpty()) head.add(" ");
            head.add(plugin.raw("menu-editor-id", "id", id));
            head.add(plugin.raw("menu-archive-boss", "boss", r.bossLabel()));
            head.add(plugin.raw("menu-archive-status", "status", manager.statusLabel(r)));
            head.add(" ");
            head.addAll(wrap(plugin.raw("menu-editor-hint")));
            inv.setItem(4, item(r.icon(), r.name(), head, true));
        }
        for (Cat c : Cat.values()) {
            String k = c.name().toLowerCase(Locale.ROOT);
            inv.setItem(c.slot, item(c.icon, plugin.raw("cat-" + k), categoryLore(c, r), false));
            cats.put(c.slot, c);
        }
        inv.setItem(SLOT_SAVE, item(Material.LIME_CONCRETE, plugin.raw("menu-editor-save"),
                wrap(plugin.raw("menu-editor-save-lore")), false));
        inv.setItem(SLOT_TEST, item(Material.BLAZE_POWDER, plugin.raw("menu-editor-test"),
                wrap(plugin.raw("menu-editor-test-lore")), true));
        inv.setItem(SLOT_CANCEL, item(Material.RED_CONCRETE, plugin.raw("menu-editor-cancel"),
                wrap(plugin.raw("menu-editor-cancel-lore")), false));
        inv.setItem(SLOT_BACK, backButton());
    }

    /** Descripción de la categoría + resumen de los valores actuales del ritual. */
    private List<String> categoryLore(Cat c, Ritual r) {
        String k = c.name().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(wrap(plugin.raw("cat-" + k + "-lore")));
        if (r != null) {
            out.add(" ");
            switch (c) {
                case INFO -> {
                    out.add(plugin.raw("sum-name", "name", r.name()));
                    out.add(plugin.raw("sum-desc", "lines", String.valueOf(r.description().size())));
                    out.add(plugin.raw("sum-icon", "icon", RitualManager.pretty(r.icon())));
                }
                case ALTARS -> {
                    List<Altar> list = manager.altarsOf(id);
                    int running = 0;
                    for (Altar a : list) {
                        if (a.state == Altar.State.CHARGING || a.state == Altar.State.ACTIVE) running++;
                    }
                    out.add(plugin.raw("sum-altars", "count", String.valueOf(list.size())));
                    out.add(plugin.raw("sum-altars-running", "count", String.valueOf(running)));
                }
                case OFFERINGS -> {
                    out.add(plugin.raw("sum-offer-types", "types", String.valueOf(r.offerings().size()),
                            "total", String.valueOf(Lore.totalItems(r))));
                    out.addAll(Lore.offerings(plugin, r, 5));
                }
                case BOSS -> {
                    Ritual.BossSpec b = r.boss();
                    if (b.mythicId().isEmpty()) {
                        out.add(plugin.raw("sum-boss-type", "type", RitualManager.pretty(b.type().name())));
                        out.add(plugin.raw("menu-archive-stats", "health", Lore.num(b.health()), "damage", Lore.num(b.damage())));
                    } else {
                        out.add(plugin.raw("sum-boss-mythic", "id", b.mythicId(), "type", RitualManager.pretty(b.type().name())));
                    }
                    out.add(plugin.raw("sum-boss-bar", "color", b.barColor().name()));
                    out.add(plugin.raw("sum-boss-spawn", "x", Lore.num(b.dx()), "y", Lore.num(b.dy()), "z", Lore.num(b.dz())));
                }
                case ANIMATIONS -> {
                    for (Ritual.PhaseId pid : Ritual.PhaseId.values()) {
                        out.add(plugin.raw("sum-anim-phase", "phase", pid.label,
                                "count", String.valueOf(r.phases().get(pid).anims().size())));
                    }
                }
                case CINEMATICS -> {
                    for (Ritual.PhaseId pid : Ritual.PhaseId.values()) {
                        Ritual.Phase ph = r.phases().get(pid);
                        out.add(plugin.raw("sum-cine-phase", "phase", pid.label,
                                "state", plugin.raw(ph.enabled() ? "state-on" : "state-off"),
                                "time", String.valueOf(ph.duration())));
                    }
                }
                case TIMING -> {
                    out.add(plugin.raw("sum-timing-total", "time", String.valueOf(r.summonSeconds())));
                    out.add(plugin.raw("sum-timing-cooldown", "time", RitualManager.formatTime(r.cooldown())));
                    out.add(plugin.raw("menu-archive-radius", "radius", String.valueOf(r.radius())));
                }
                case CONDITIONS -> {
                    out.add(plugin.raw("sum-cond-time", "value", Lore.time(plugin, r.conditions().time())));
                    out.add(plugin.raw("sum-cond-players", "count", String.valueOf(r.conditions().minPlayers())));
                    out.add(plugin.raw("sum-cond-permission", "value", r.conditions().permission().isEmpty()
                            ? plugin.raw("sum-none") : r.conditions().permission()));
                    out.add(plugin.raw("menu-archive-visibility", "visibility", Lore.visibility(plugin, r.visibility())));
                }
                case REWARDS -> {
                    out.add(plugin.raw("sum-rew-xp", "xp", String.valueOf(r.rewards().xp())));
                    out.add(plugin.raw("sum-rew-drops", "count", String.valueOf(r.rewards().drops().size())));
                    out.add(plugin.raw("sum-rew-vanilla", "state",
                            plugin.raw(r.rewards().vanillaDrops() ? "state-yes" : "state-no")));
                    out.add(plugin.raw("sum-rew-commands", "count", String.valueOf(r.rewards().commands().size())));
                }
            }
        }
        out.add(" ");
        out.add(plugin.raw("cat-click"));
        return out;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        Ritual r = manager.getRitual(id);
        if (r == null) {
            p.sendMessage(plugin.msg("unknown-ritual", "ritual", id));
            closeLater(p);
            return;
        }
        int slot = e.getSlot();

        if (slot == SLOT_SAVE) {
            manager.commitEdit(p);
            p.sendMessage(plugin.msg("ritual-saved", "ritual", r.name()));
            openLater(new ArchiveMenu(plugin, manager, 0), p);
        } else if (slot == SLOT_CANCEL) {
            boolean undone = manager.cancelEdit(p);
            p.sendMessage(plugin.msg(undone ? "edit-cancelled" : "edit-nothing"));
            openLater(new ArchiveMenu(plugin, manager, 0), p);
        } else if (slot == SLOT_BACK) {
            openLater(new ArchiveMenu(plugin, manager, 0), p);
        } else if (slot == SLOT_TEST) {
            Block b = p.getTargetBlockExact(6);
            if (b == null || b.getType().isAir()) b = p.getLocation().getBlock();
            closeLater(p);
            manager.startTest(p, r, b);
            p.sendMessage(plugin.msg("test-started", "ritual", r.name()));
        } else {
            Cat c = cats.get(slot);
            if (c != null) openCategory(p, c);
        }
    }

    private void openCategory(Player p, Cat c) {
        Supplier<Menu> back = () -> new EditorMenu(plugin, manager, id);
        Menu next = switch (c) {
            case INFO -> new FieldMenu(plugin, manager, id, plugin.raw("cat-info"), Fields.info(), back);
            case ALTARS -> new AltarsMenu(plugin, manager, id, back);
            case OFFERINGS -> new OfferingsMenu(plugin, manager, id, back);
            case BOSS -> new FieldMenu(plugin, manager, id, plugin.raw("cat-boss"), Fields.boss(), back);
            case ANIMATIONS -> new PhaseSelectMenu(plugin, manager, id, true, back);
            case CINEMATICS -> new PhaseSelectMenu(plugin, manager, id, false, back);
            case TIMING -> new FieldMenu(plugin, manager, id, plugin.raw("cat-timing"), Fields.timing(), back);
            case CONDITIONS -> new FieldMenu(plugin, manager, id, plugin.raw("cat-conditions"), Fields.conditions(), back);
            case REWARDS -> new FieldMenu(plugin, manager, id, plugin.raw("cat-rewards"), Fields.rewards(), back);
        };
        openLater(next, p);
    }
}
