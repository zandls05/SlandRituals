package dev.slandrituals;

import org.bukkit.Material;
import org.bukkit.boss.BarColor;

import java.util.ArrayList;
import java.util.List;

/** Listas de campos editables de cada categoría del editor. */
final class Fields {

    private Fields() { }

    static List<Field> info() {
        return List.of(
                Field.text("name", "Nombre visible", Material.NAME_TAG, "", "Admite colores con &. Ej: &5&lGuardián"),
                Field.list("description", "Descripción", Material.WRITABLE_BOOK, "Líneas separadas por |. Escribe - para borrar."),
                Field.of(Field.Kind.MATERIAL, "icon", "Icono", Material.ITEM_FRAME, null, "Sostén un objeto en la mano y haz clic."));
    }

    static List<Field> boss() {
        List<String> colors = new ArrayList<>();
        for (BarColor c : BarColor.values()) colors.add(c.name());
        return List.of(
                Field.of(Field.Kind.ENTITY, "boss.type", "Tipo de jefe (vanilla)", Material.ZOMBIE_HEAD, "ZOMBIE",
                        "Mob que aparece. También es la alternativa si falta MythicMobs."),
                Field.text("boss.mythic-id", "Id de MythicMobs", Material.NETHER_STAR, "",
                        "Opcional. Escribe - para quitarlo."),
                Field.text("boss.name", "Nombre del jefe", Material.NAME_TAG, "", "Por defecto, el nombre del ritual."),
                Field.decimal("boss.health", "Vida (solo vanilla)", Material.GOLDEN_APPLE, 100, 1, 100000, "Puntos de vida."),
                Field.decimal("boss.damage", "Daño (solo vanilla)", Material.IRON_SWORD, 6, 0, 1000, "Daño de ataque."),
                Field.choice("boss.bar-color", "Color de la barra", Material.PURPLE_DYE, "RED", colors, "Color de la bossbar."),
                Field.decimal("boss.spawn.x", "Aparición: desplazamiento X", Material.RED_CONCRETE, 0, -20, 20,
                        "Bloques respecto al centro del altar."),
                Field.decimal("boss.spawn.y", "Aparición: desplazamiento Y", Material.LIME_CONCRETE, 1, -20, 20,
                        "Bloques hacia arriba respecto al centro."),
                Field.decimal("boss.spawn.z", "Aparición: desplazamiento Z", Material.BLUE_CONCRETE, 0, -20, 20,
                        "Bloques respecto al centro del altar."));
    }

    static List<Field> timing() {
        return List.of(
                Field.integer("cooldown", "Enfriamiento (s)", Material.CLOCK, 300, 0, 86400,
                        "Segundos que el altar queda inactivo tras vencer al jefe."),
                Field.integer("radius", "Radio de visibilidad", Material.SPYGLASS, 60, 8, 512,
                        "Bloques a la redonda que ven y oyen el ritual."),
                Field.integer("cinematic.activation.duration", "Activación (s)", Material.REDSTONE_TORCH, 2, 1, 600, "Duración de la fase."),
                Field.integer("cinematic.charge.duration", "Carga / cuenta atrás (s)", Material.RESPAWN_ANCHOR, 10, 1, 600, "Duración de la fase."),
                Field.integer("cinematic.portal.duration", "Apertura del portal (s)", Material.END_PORTAL_FRAME, 3, 1, 600, "Duración de la fase."),
                Field.integer("cinematic.spawn.duration", "Aparición del jefe (s)", Material.WITHER_SKELETON_SKULL, 2, 1, 600, "Duración de los efectos finales."));
    }

    static List<Field> conditions() {
        return List.of(
                Field.integer("conditions.min-players", "Jugadores mínimos cerca", Material.PLAYER_HEAD, 0, 0, 50,
                        "0 = sin mínimo. Se cuentan dentro del radio."),
                Field.choice("conditions.time", "Momento del día", Material.DAYLIGHT_DETECTOR, "ANY",
                        List.of("ANY", "DAY", "NIGHT"), "ANY = siempre, DAY = día, NIGHT = noche."),
                Field.text("conditions.permission", "Permiso necesario", Material.PAPER, "",
                        "Vacío = sin permiso extra. Escribe - para quitarlo."),
                Field.choice("visibility", "Quién ve la animación", Material.ENDER_EYE, "ALL",
                        List.of("ALL", "STARTER"), "ALL = jugadores cercanos, STARTER = solo quien activa."));
    }

    static List<Field> rewards() {
        return List.of(
                Field.integer("rewards.xp", "Experiencia", Material.EXPERIENCE_BOTTLE, 0, 0, 100000, "Puntos de experiencia al morir el jefe."),
                Field.bool("rewards.vanilla-drops", "Conservar drops normales", Material.CHEST, false,
                        "Con MythicMobs, actívalo para conservar sus drops."));
    }

    /** Cinemática y sonidos de una fase. Los valores por defecto salen de animaciones.yml. */
    static List<Field> phase(SlandRitualsPlugin plugin, Ritual.PhaseId pid) {
        Cfg d = plugin.anim();
        String dp = "phases." + pid.key + ".";
        String rp = "cinematic." + pid.key + ".";
        List<Field> out = new ArrayList<>();
        out.add(Field.bool(rp + "enabled", "Fase activada", Material.LEVER, d.bool(dp + "enabled", true),
                "Si la desactivas, la fase se salta por completo."));
        out.add(Field.of(Field.Kind.SOUND, rp + "sound", "Sonido de inicio", Material.NOTE_BLOCK, d.str(dp + "sound", ""),
                "Ej: block.beacon.activate  (- para silenciar)"));
        if (pid == Ritual.PhaseId.CHARGE) {
            out.add(Field.of(Field.Kind.SOUND, rp + "tick-sound", "Sonido de cada segundo", Material.JUKEBOX,
                    d.str(dp + "tick-sound", ""), "Sube de tono según avanza la carga."));
            out.add(Field.bool(rp + "countdown-title", "Título de cuenta atrás", Material.OAK_SIGN,
                    d.bool(dp + "countdown-title", true), "Muestra el título cada segundo con {time}."));
        }
        out.add(Field.text(rp + "title", "Título", Material.OAK_SIGN, d.str(dp + "title", ""),
                "Variables: {ritual} {boss} {player} {time}. - para vacío."));
        out.add(Field.text(rp + "subtitle", "Subtítulo", Material.BIRCH_SIGN, d.str(dp + "subtitle", ""),
                "Mismas variables que el título."));
        out.add(Field.text(rp + "message", "Mensaje en el chat", Material.BOOK, d.str(dp + "message", ""),
                "Se envía a los jugadores cercanos al empezar la fase."));
        return out;
    }

    /** Ajustes de una animación concreta dentro de una fase. */
    static List<Field> animation(SlandRitualsPlugin plugin, Ritual.PhaseId pid, AnimType t) {
        Cfg d = plugin.anim();
        String rp = "cinematic." + pid.key + ".animations." + t.key + ".";
        String ap = "animations." + t.key + ".";
        return List.of(
                Field.of(Field.Kind.PARTICLE, rp + "particle", "Tipo de partícula", Material.FIREWORK_STAR,
                        d.str(ap + "particle", "END_ROD"), "Ej: END_ROD, FLAME, SOUL_FIRE_FLAME, ENCHANT, DUST"),
                Field.of(Field.Kind.COLOR, rp + "color", "Color (solo DUST)", Material.PURPLE_DYE,
                        d.str(ap + "color", "#8A2BE2"), "Formato #RRGGBB."),
                Field.decimal(rp + "size", "Tamaño (solo DUST)", Material.SLIME_BALL, d.dbl(ap + "size", 1.2), 0.3, 4, "Entre 0.3 y 4."),
                Field.integer(rp + "count", "Cantidad", Material.HOPPER, d.integer(ap + "count", 3), 1, 60, "Densidad de partículas."),
                Field.decimal(rp + "radius", "Radio", Material.COMPASS, d.dbl(ap + "radius", 1.5), 0.3, 16, "Bloques."),
                Field.decimal(rp + "speed", "Velocidad", Material.SUGAR, d.dbl(ap + "speed", 1), 0.1, 5, "Multiplicador de velocidad."),
                Field.decimal(rp + "height", "Altura", Material.LADDER, d.dbl(ap + "height", 2), 0.3, 12, "Bloques."),
                Field.integer(rp + "frequency", "Frecuencia", Material.REPEATER, d.integer(ap + "frequency", 1), 1, 20,
                        "1 = cada 2 ticks. Más alto = menos fotogramas."));
    }
}
