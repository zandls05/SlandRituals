package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Menú genérico que lista campos editables: clic para cambiarlos (por chat, alternando o con el objeto en mano). */
public final class FieldMenu extends Menu {

    private static final int SLOT_BACK = 4;

    private final RitualManager manager;
    private final String ritualId;
    private final List<Field> fields;
    private final Supplier<Menu> back;
    private final Map<Integer, Field> slots = new HashMap<>();
    private final int rows;

    public FieldMenu(SlandRitualsPlugin plugin, RitualManager manager, String ritualId, String title,
                     List<Field> fields, Supplier<Menu> back) {
        super(plugin);
        this.manager = manager;
        this.ritualId = ritualId;
        this.fields = fields;
        this.back = back;
        this.rows = Math.min(6, 2 + (fields.size() + 6) / 7);
        this.inv = Bukkit.createInventory(this, rows * 9, SlandRitualsPlugin.color("&5") + title);
        render();
    }

    private Object current(Field f) {
        Object v = manager.store().get(ritualId, f.path());
        return v != null ? v : f.def();
    }

    private void render() {
        inv.clear();
        slots.clear();
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        for (int i = 0; i < fields.size() && i < 28; i++) {
            Field f = fields.get(i);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            inv.setItem(slot, fieldItem(f));
            slots.put(slot, f);
        }
        inv.setItem(SLOT_BACK, backButton());
    }

    private ItemStack fieldItem(Field f) {
        boolean unset = manager.store().get(ritualId, f.path()) == null;
        List<String> lore = new ArrayList<>();
        lore.add(plugin.raw("field-value", "value", display(current(f))) + (unset ? plugin.raw("field-default-tag") : ""));
        if (!unset && f.def() != null) lore.add(plugin.raw("field-default", "value", display(f.def())));
        lore.add(plugin.raw("field-type-" + f.kind().name().toLowerCase(Locale.ROOT)));
        if (f.kind() == Field.Kind.INT) {
            lore.add(plugin.raw("field-range", "min", String.valueOf((int) f.min()), "max", String.valueOf((int) f.max())));
        } else if (f.kind() == Field.Kind.DOUBLE) {
            lore.add(plugin.raw("field-range", "min", Lore.num(f.min()), "max", Lore.num(f.max())));
        } else if (f.kind() == Field.Kind.CHOICE) {
            lore.addAll(wrap(plugin.raw("field-choices", "choices", String.join(", ", f.choices()))));
        }
        if (!f.hint().isEmpty()) {
            lore.add(" ");
            lore.add(plugin.raw("field-help"));
            lore.addAll(wrap(SlandRitualsPlugin.color("&8" + f.hint())));
        }
        lore.add(" ");
        switch (f.kind()) {
            case BOOL -> lore.add(plugin.raw("field-click-toggle"));
            case CHOICE -> {
                lore.add(plugin.raw("field-click-next"));
                lore.add(plugin.raw("field-click-prev"));
            }
            case MATERIAL -> lore.add(plugin.raw("field-click-hand"));
            default -> lore.add(plugin.raw("field-click-chat"));
        }
        lore.add(plugin.raw("field-click-reset"));
        boolean glow = f.kind() == Field.Kind.BOOL && Boolean.TRUE.equals(current(f));
        return item(f.icon(), SlandRitualsPlugin.color("&d" + f.label()), lore, glow);
    }

    private String display(Object v) {
        if (v == null) return "§8(sin definir)";
        if (v instanceof Boolean b) return b ? "§a✔ activado" : "§c✘ desactivado";
        if (v instanceof Number n) return Lore.num(n.doubleValue());
        if (v instanceof List<?> l) {
            return l.isEmpty() ? "§8(vacío)" : shorten(String.join(" | ", l.stream().map(String::valueOf).toList()), 60);
        }
        String s = String.valueOf(v);
        return s.isEmpty() ? "§8(vacío)" : shorten(s, 60);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getSlot() == SLOT_BACK) {
            openLater(back.get(), p);
            return;
        }
        Field f = slots.get(e.getSlot());
        if (f == null) return;

        if (e.isShiftClick() && e.isRightClick()) { // restablecer al valor por defecto
            manager.editSet(ritualId, f.path(), null);
            render();
            return;
        }
        switch (f.kind()) {
            case BOOL -> {
                manager.editSet(ritualId, f.path(), !Boolean.TRUE.equals(current(f)));
                render();
            }
            case CHOICE -> {
                List<String> ch = f.choices();
                int idx = ch.indexOf(String.valueOf(current(f)).toUpperCase(Locale.ROOT));
                idx = e.isRightClick() ? (idx - 1 + ch.size()) % ch.size() : (idx + 1) % ch.size();
                manager.editSet(ritualId, f.path(), ch.get(Math.max(0, idx)));
                render();
            }
            case MATERIAL -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    p.sendMessage(plugin.msg("hold-item"));
                } else {
                    manager.editSet(ritualId, f.path(), hand.getType().name());
                    render();
                }
            }
            default -> askChat(p, f);
        }
    }

    private void askChat(Player p, Field f) {
        closeLater(p);
        plugin.chat().ask(p, plugin.raw("prompt-field", "field", f.label(), "hint", f.hint()), input -> {
            Object value;
            try {
                value = parse(f, input);
            } catch (IllegalArgumentException ex) {
                return ex.getMessage();
            }
            manager.editSet(ritualId, f.path(), value);
            p.sendMessage(plugin.msg("field-updated", "field", f.label()));
            reopen(p);
            return null;
        }, () -> reopen(p));
    }

    private void reopen(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            render();
            open(p);
        });
    }

    /** Valida lo escrito en el chat. Lanza IllegalArgumentException con el motivo si no es válido. */
    private Object parse(Field f, String in) {
        switch (f.kind()) {
            case TEXT:
                return in.equals("-") ? "" : in;
            case LIST: {
                if (in.equals("-")) return new ArrayList<String>();
                List<String> out = new ArrayList<>();
                for (String part : in.split("\\|")) if (!part.trim().isEmpty()) out.add(part.trim());
                return out;
            }
            case INT: {
                try {
                    int v = Integer.parseInt(in);
                    if (v < f.min() || v > f.max()) throw new IllegalArgumentException(range(f));
                    return v;
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(plugin.raw("invalid-number"));
                }
            }
            case DOUBLE: {
                try {
                    double v = Double.parseDouble(in.replace(',', '.'));
                    if (v < f.min() || v > f.max()) throw new IllegalArgumentException(range(f));
                    return v;
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(plugin.raw("invalid-number"));
                }
            }
            case PARTICLE: {
                Particle part = null;
                try {
                    part = Particle.valueOf(in.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    // se avisa abajo
                }
                if (part == null) throw new IllegalArgumentException(plugin.raw("particle-unknown"));
                Class<?> dt = part.getDataType();
                if (dt != Void.class && dt != Particle.DustOptions.class && dt != org.bukkit.Color.class) {
                    throw new IllegalArgumentException(plugin.raw("particle-unsupported"));
                }
                return part.name();
            }
            case SOUND: {
                if (in.equals("-")) return "";
                String s = in.toLowerCase(Locale.ROOT);
                if (!s.matches("[a-z0-9_.:/-]+")) throw new IllegalArgumentException(plugin.raw("sound-invalid"));
                return s;
            }
            case COLOR: {
                if (!in.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException(plugin.raw("color-invalid"));
                return in.toUpperCase(Locale.ROOT);
            }
            case ENTITY: {
                EntityType t = null;
                try {
                    t = EntityType.valueOf(in.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    // se avisa abajo
                }
                if (t == null) throw new IllegalArgumentException(plugin.raw("entity-unknown"));
                Class<?> cls = t.getEntityClass();
                if (cls == null || !Mob.class.isAssignableFrom(cls)) {
                    throw new IllegalArgumentException(plugin.raw("entity-not-mob"));
                }
                return t.name();
            }
            default:
                throw new IllegalArgumentException(plugin.raw("invalid-value"));
        }
    }

    private String range(Field f) {
        boolean whole = f.kind() == Field.Kind.INT;
        return plugin.raw("out-of-range",
                "min", whole ? String.valueOf((int) f.min()) : String.valueOf(f.min()),
                "max", whole ? String.valueOf((int) f.max()) : String.valueOf(f.max()));
    }
}
