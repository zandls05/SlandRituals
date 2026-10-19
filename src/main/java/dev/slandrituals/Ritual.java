package dev.slandrituals;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Definición inmutable de un ritual, leída de rituals.yml. */
public record Ritual(
        String id,
        String name,
        List<String> description,
        Material icon,
        int cooldown,
        int radius,
        Visibility visibility,
        Map<Material, Integer> offerings,
        Conditions conditions,
        BossSpec boss,
        Map<PhaseId, Phase> phases,
        Rewards rewards) {

    public enum Visibility { ALL, STARTER }

    public enum TimeMode { ANY, DAY, NIGHT }

    /** Fases de la cinemática de invocación. */
    public enum PhaseId {
        ACTIVATION("activation", "Activación", Material.REDSTONE_TORCH),
        CHARGE("charge", "Carga", Material.RESPAWN_ANCHOR),
        PORTAL("portal", "Apertura del portal", Material.END_PORTAL_FRAME),
        SPAWN("spawn", "Aparición del jefe", Material.WITHER_SKELETON_SKULL);

        public final String key;
        public final String label;
        public final Material icon;

        PhaseId(String key, String label, Material icon) {
            this.key = key;
            this.label = label;
            this.icon = icon;
        }
    }

    public record Conditions(int minPlayers, TimeMode time, String permission) { }

    public record BossSpec(EntityType type, String mythicId, String name, double health, double damage,
                           BarColor barColor, Map<EquipmentSlot, Material> gear,
                           double dx, double dy, double dz) { }

    public record Rewards(int xp, boolean vanillaDrops, Map<Material, Integer> drops, List<String> commands) { }

    public record Phase(boolean enabled, int duration, String sound, String tickSound, boolean countdownTitle,
                        String title, String subtitle, String message, List<AnimSpec> anims) { }

    public record AnimSpec(AnimType type, Particle particle, Color color, float size, int count,
                           double radius, double speed, double height, int frequency) {

        /** Datos extra que necesita la partícula (color para DUST, etc.), o null. */
        public Object data() {
            Class<?> dt = particle.getDataType();
            if (dt == Particle.DustOptions.class) return new Particle.DustOptions(color, size);
            if (dt == Color.class) return color;
            return null;
        }
    }

    /** Segundos totales desde que empieza la activación hasta que aparece el jefe. */
    public int summonSeconds() {
        int total = 0;
        for (PhaseId id : new PhaseId[]{PhaseId.ACTIVATION, PhaseId.CHARGE, PhaseId.PORTAL}) {
            Phase p = phases.get(id);
            if (p != null && p.enabled()) total += p.duration();
        }
        return total;
    }

    /** Nombre del jefe para mostrar en menús (sin colores de formato propios del ritual). */
    public String bossLabel() {
        if (!boss.mythicId().isEmpty()) return "MythicMobs: " + boss.mythicId();
        return RitualManager.pretty(boss.type().name());
    }

    // ------------------------------------------------------------------ lectura

    public static Ritual parse(String id, ConfigurationSection s, SlandRitualsPlugin plugin) {
        Logger log = plugin.getLogger();

        Map<Material, Integer> offers = readItems(s.getConfigurationSection("offerings"), log, id);
        if (offers.isEmpty()) {
            log.warning("Ritual '" + id + "' ignorado: no tiene ofrendas válidas.");
            return null;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(s.getString("boss.type", "ZOMBIE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("Ritual '" + id + "' ignorado: boss.type inválido.");
            return null;
        }
        Class<?> cls = type.getEntityClass();
        if (cls == null || !Mob.class.isAssignableFrom(cls)) {
            log.warning("Ritual '" + id + "' ignorado: " + type + " no es un mob.");
            return null;
        }

        Material icon = Material.matchMaterial(s.getString("icon", type.name() + "_SPAWN_EGG"));
        if (icon == null || icon.isAir()) icon = Material.NETHER_STAR;

        String rawName = s.getString("name", id);
        String name = SlandRitualsPlugin.color(rawName);

        Map<EquipmentSlot, Material> gear = new EnumMap<>(EquipmentSlot.class);
        ConfigurationSection g = s.getConfigurationSection("boss.gear");
        if (g != null) {
            put(gear, EquipmentSlot.HEAD, g.getString("helmet"));
            put(gear, EquipmentSlot.CHEST, g.getString("chestplate"));
            put(gear, EquipmentSlot.LEGS, g.getString("leggings"));
            put(gear, EquipmentSlot.FEET, g.getString("boots"));
            put(gear, EquipmentSlot.HAND, g.getString("main-hand"));
            put(gear, EquipmentSlot.OFF_HAND, g.getString("off-hand"));
        }

        BossSpec boss = new BossSpec(
                type,
                s.getString("boss.mythic-id", "").trim(),
                SlandRitualsPlugin.color(s.getString("boss.name", "").isBlank() ? rawName : s.getString("boss.name", "")),
                Math.max(1, s.getDouble("boss.health", 100)),
                Math.max(0, s.getDouble("boss.damage", 6)),
                enumOf(BarColor.class, s.getString("boss.bar-color"), BarColor.RED),
                gear,
                s.getDouble("boss.spawn.x", 0),
                s.getDouble("boss.spawn.y", 1),
                s.getDouble("boss.spawn.z", 0));

        Conditions cond = new Conditions(
                Math.max(0, s.getInt("conditions.min-players", 0)),
                enumOf(TimeMode.class, s.getString("conditions.time"), TimeMode.ANY),
                s.getString("conditions.permission", ""));

        Rewards rewards = new Rewards(
                Math.max(0, s.getInt("rewards.xp", 0)),
                s.getBoolean("rewards.vanilla-drops", false),
                readItems(s.getConfigurationSection("rewards.drops"), log, id),
                s.getStringList("rewards.commands"));

        Map<PhaseId, Phase> phases = new EnumMap<>(PhaseId.class);
        for (PhaseId pid : PhaseId.values()) phases.put(pid, parsePhase(plugin, s, pid, id));

        List<String> desc = new ArrayList<>();
        for (String line : s.getStringList("description")) desc.add(SlandRitualsPlugin.color(line));

        return new Ritual(
                id,
                name,
                desc,
                icon,
                Math.max(0, s.getInt("cooldown", 300)),
                Math.max(8, Math.min(512, s.getInt("radius", plugin.getConfig().getInt("settings.broadcast-radius", 60)))),
                enumOf(Visibility.class, s.getString("visibility"), Visibility.ALL),
                offers,
                cond,
                boss,
                phases,
                rewards);
    }

    private static Phase parsePhase(SlandRitualsPlugin plugin, ConfigurationSection s, PhaseId pid, String id) {
        Cfg d = plugin.anim();
        String dp = "phases." + pid.key + ".";
        String rp = "cinematic." + pid.key + ".";

        boolean enabled = s.contains(rp + "enabled") ? s.getBoolean(rp + "enabled", true) : d.bool(dp + "enabled", true);
        int duration = s.contains(rp + "duration") ? s.getInt(rp + "duration") : d.integer(dp + "duration", 2);
        if (pid == PhaseId.CHARGE && !s.contains(rp + "duration") && s.contains("countdown")) {
            duration = s.getInt("countdown", duration); // compatibilidad con rituals.yml antiguos
        }
        duration = Math.max(1, Math.min(600, duration));

        boolean countdownTitle = s.contains(rp + "countdown-title")
                ? s.getBoolean(rp + "countdown-title") : d.bool(dp + "countdown-title", pid == PhaseId.CHARGE);

        return new Phase(
                enabled,
                duration,
                str(s, rp + "sound", d, dp + "sound"),
                str(s, rp + "tick-sound", d, dp + "tick-sound"),
                countdownTitle,
                str(s, rp + "title", d, dp + "title"),
                str(s, rp + "subtitle", d, dp + "subtitle"),
                str(s, rp + "message", d, dp + "message"),
                parseAnims(plugin, s, pid, id));
    }

    private static String str(ConfigurationSection s, String ritualPath, Cfg d, String defPath) {
        if (s.contains(ritualPath)) {
            String v = s.getString(ritualPath);
            return v == null ? "" : v;
        }
        return d.str(defPath, "");
    }

    private static List<AnimSpec> parseAnims(SlandRitualsPlugin plugin, ConfigurationSection s, PhaseId pid, String id) {
        Cfg d = plugin.anim();
        Logger log = plugin.getLogger();
        ConfigurationSection own = s.getConfigurationSection("cinematic." + pid.key + ".animations");
        Set<String> keys = own != null ? own.getKeys(false) : d.keys("phases." + pid.key + ".animations");

        List<AnimSpec> out = new ArrayList<>();
        for (String key : keys) {
            AnimType type = AnimType.fromKey(key);
            if (type == null) {
                log.warning("Ritual '" + id + "': animación desconocida '" + key + "' (se ignora).");
                continue;
            }
            boolean on = own != null
                    ? own.getBoolean(key + ".enabled", true)
                    : d.bool("phases." + pid.key + ".animations." + key + ".enabled", true);
            if (!on) continue;
            out.add(buildSpec(plugin, s, pid, type, id));
        }
        return out;
    }

    private static AnimSpec buildSpec(SlandRitualsPlugin plugin, ConfigurationSection s, PhaseId pid, AnimType t, String id) {
        Cfg d = plugin.anim();
        String ritualBase = "cinematic." + pid.key + ".animations." + t.key + ".";
        String phaseBase = "phases." + pid.key + ".animations." + t.key + ".";
        String animBase = "animations." + t.key + ".";

        Particle particle = parseParticle(String.valueOf(pick(s, d, "particle", ritualBase, phaseBase, animBase)),
                plugin.getLogger(), id);
        Color color = parseColor(String.valueOf(pick(s, d, "color", ritualBase, phaseBase, animBase)));

        return new AnimSpec(
                t,
                particle,
                color,
                (float) clamp(num(pick(s, d, "size", ritualBase, phaseBase, animBase), 1.2), 0.3, 4),
                (int) clamp(num(pick(s, d, "count", ritualBase, phaseBase, animBase), 3), 1, 60),
                clamp(num(pick(s, d, "radius", ritualBase, phaseBase, animBase), 1.5), 0.3, 16),
                clamp(num(pick(s, d, "speed", ritualBase, phaseBase, animBase), 1), 0.1, 5),
                clamp(num(pick(s, d, "height", ritualBase, phaseBase, animBase), 2), 0.3, 12),
                (int) clamp(num(pick(s, d, "frequency", ritualBase, phaseBase, animBase), 1), 1, 20));
    }

    /** Valor del ritual, si no el de la fase en animaciones.yml, si no el de la animación. */
    private static Object pick(ConfigurationSection s, Cfg d, String field, String ritualBase, String phaseBase, String animBase) {
        if (s.contains(ritualBase + field)) return s.get(ritualBase + field);
        Object o = d.get(phaseBase + field);
        if (o != null) return o;
        return d.get(animBase + field);
    }

    private static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Devuelve la partícula si existe y es soportada; si no, END_ROD. */
    public static Particle parseParticle(String name, Logger log, String ctx) {
        try {
            Particle p = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
            Class<?> dt = p.getDataType();
            if (dt == Void.class || dt == Particle.DustOptions.class || dt == Color.class) return p;
            log.warning("Ritual '" + ctx + "': la partícula " + p + " necesita datos extra no soportados; se usa END_ROD.");
        } catch (IllegalArgumentException ex) {
            log.warning("Ritual '" + ctx + "': partícula desconocida '" + name + "'; se usa END_ROD.");
        }
        return Particle.END_ROD;
    }

    public static Color parseColor(String hex) {
        try {
            String h = hex.trim();
            if (h.startsWith("#")) h = h.substring(1);
            return Color.fromRGB(Integer.parseInt(h, 16) & 0xFFFFFF);
        } catch (RuntimeException ex) {
            return Color.fromRGB(138, 43, 226);
        }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> cls, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(cls, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static void put(Map<EquipmentSlot, Material> map, EquipmentSlot slot, String name) {
        if (name == null) return;
        Material m = Material.matchMaterial(name);
        if (m != null) map.put(slot, m);
    }

    private static Map<Material, Integer> readItems(ConfigurationSection sec, Logger log, String id) {
        Map<Material, Integer> out = new EnumMap<>(Material.class);
        if (sec == null) return out;
        for (String key : sec.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m == null || m.isAir()) {
                log.warning("Ritual '" + id + "': material desconocido '" + key + "'.");
                continue;
            }
            int amount = sec.getInt(key, 1);
            if (amount > 0) out.put(m, amount);
        }
        return out;
    }
}
