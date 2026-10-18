package dev.slandrituals;

import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Definición inmutable de un ritual, leída de rituals.yml. */
public record Ritual(
        String id,
        String name,
        Material icon,
        int countdown,
        int cooldown,
        Map<Material, Integer> offerings,
        EntityType bossType,
        double health,
        double damage,
        BarColor barColor,
        Map<EquipmentSlot, Material> gear,
        int xp,
        boolean vanillaDrops,
        Map<Material, Integer> drops,
        List<String> commands) {

    public static Ritual parse(String id, ConfigurationSection s, Logger log) {
        Map<Material, Integer> offers = readItems(s.getConfigurationSection("offerings"), log, id);
        if (offers.isEmpty()) {
            log.warning("Ritual '" + id + "' ignorado: no tiene ofrendas válidas.");
            return null;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(s.getString("boss.type", "ZOMBIE").toUpperCase());
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

        BarColor bar;
        try {
            bar = BarColor.valueOf(s.getString("boss.bar-color", "RED").toUpperCase());
        } catch (IllegalArgumentException ex) {
            bar = BarColor.RED;
        }

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

        return new Ritual(
                id.toLowerCase(),
                SlandRitualsPlugin.color(s.getString("name", id)),
                icon,
                Math.max(1, s.getInt("countdown", 10)),
                Math.max(0, s.getInt("cooldown", 300)),
                offers,
                type,
                Math.max(1, s.getDouble("boss.health", 100)),
                Math.max(0, s.getDouble("boss.damage", 6)),
                bar,
                gear,
                Math.max(0, s.getInt("rewards.xp", 0)),
                s.getBoolean("rewards.vanilla-drops", false),
                readItems(s.getConfigurationSection("rewards.drops"), log, id),
                s.getStringList("rewards.commands"));
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
