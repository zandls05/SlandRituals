package dev.slandrituals;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Construye las líneas de lore que se repiten en varios menús. */
final class Lore {

    private Lore() { }

    /** 3 -> "3", 2.5 -> "2.5". */
    static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    static String visibility(SlandRitualsPlugin plugin, Ritual.Visibility v) {
        return plugin.raw(v == Ritual.Visibility.STARTER ? "visibility-starter" : "visibility-all");
    }

    static String time(SlandRitualsPlugin plugin, Ritual.TimeMode t) {
        return plugin.raw("time-" + t.name().toLowerCase(Locale.ROOT));
    }

    /** Resumen de condiciones: "solo de noche, mín. 2 jugadores cerca, requiere permiso". */
    static String conditions(SlandRitualsPlugin plugin, Ritual r) {
        List<String> parts = new ArrayList<>();
        parts.add(time(plugin, r.conditions().time()));
        if (r.conditions().minPlayers() > 0) {
            parts.add(plugin.raw("cond-players", "count", String.valueOf(r.conditions().minPlayers())));
        }
        if (!r.conditions().permission().isEmpty()) parts.add(plugin.raw("cond-permission"));
        return String.join(", ", parts);
    }

    static int totalItems(Ritual r) {
        int total = 0;
        for (int n : r.offerings().values()) total += n;
        return total;
    }

    /** Una línea por tipo de ofrenda; si hay más de {@code max}, añade «…y N más». */
    static List<String> offerings(SlandRitualsPlugin plugin, Ritual r, int max) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int total = r.offerings().size();
        for (Map.Entry<Material, Integer> e : r.offerings().entrySet()) {
            if (i >= max) {
                out.add(plugin.raw("menu-archive-offering-more", "count", String.valueOf(total - max)));
                break;
            }
            out.add(plugin.raw("menu-archive-offering-line", "amount", String.valueOf(e.getValue()),
                    "item", RitualManager.pretty(e.getKey())));
            i++;
        }
        return out;
    }

    /** Ficha completa de un ritual (la que se ve en el Archivo de Rituales). */
    static List<String> details(SlandRitualsPlugin plugin, RitualManager manager, Ritual r) {
        List<String> out = new ArrayList<>();
        out.add(plugin.raw("menu-archive-id", "id", r.id()));
        out.add(plugin.raw("menu-archive-boss", "boss", r.bossLabel()));
        if (r.boss().mythicId().isEmpty()) {
            out.add(plugin.raw("menu-archive-stats", "health", num(r.boss().health()), "damage", num(r.boss().damage())));
        } else {
            out.add(plugin.raw("menu-archive-mythic"));
        }
        out.add(" ");
        out.add(plugin.raw("menu-archive-offerings", "count", String.valueOf(r.offerings().size()),
                "total", String.valueOf(totalItems(r))));
        out.addAll(offerings(plugin, r, 6));
        out.add(" ");
        out.add(plugin.raw("menu-archive-timing", "summon", String.valueOf(r.summonSeconds()),
                "cooldown", RitualManager.formatTime(r.cooldown())));
        out.add(plugin.raw("menu-archive-radius", "radius", String.valueOf(r.radius())));
        out.add(plugin.raw("menu-archive-visibility", "visibility", visibility(plugin, r.visibility())));
        out.add(plugin.raw("menu-archive-conditions", "conditions", conditions(plugin, r)));
        out.add(plugin.raw("menu-archive-rewards", "xp", String.valueOf(r.rewards().xp()),
                "drops", String.valueOf(r.rewards().drops().size()),
                "commands", String.valueOf(r.rewards().commands().size())));
        out.add(" ");
        out.add(plugin.raw("menu-archive-altars", "count", String.valueOf(manager.altarsOf(r.id()).size())));
        out.add(plugin.raw("menu-archive-status", "status", manager.statusLabel(r)));
        return out;
    }
}
