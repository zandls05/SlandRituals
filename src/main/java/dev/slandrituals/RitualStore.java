package dev.slandrituals;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Acceso de lectura/escritura a rituals.yml (la edición desde los menús se guarda aquí). */
public final class RitualStore {

    private final SlandRitualsPlugin plugin;
    private final File file;
    private YamlConfiguration yml = new YamlConfiguration();

    public RitualStore(SlandRitualsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rituals.yml");
    }

    public void reload() {
        yml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean save() {
        try {
            yml.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar rituals.yml: " + e.getMessage());
            return false;
        }
    }

    public List<String> ids() {
        ConfigurationSection s = yml.getConfigurationSection("rituals");
        return s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
    }

    public ConfigurationSection section(String id) {
        return yml.getConfigurationSection("rituals." + id);
    }

    public boolean exists(String id) {
        return section(id) != null;
    }

    public Object get(String id, String path) {
        return yml.get("rituals." + id + "." + path);
    }

    public void set(String id, String path, Object value) {
        yml.set("rituals." + id + "." + path, value);
    }

    /** Crea un ritual mínimo y válido para que el administrador lo termine de configurar. */
    public void create(String id) {
        set(id, "name", "&d" + id);
        set(id, "description", List.of("&7Ritual nuevo. Edítalo desde el menú."));
        set(id, "cooldown", 300);
        set(id, "offerings.ROTTEN_FLESH", 16);
        set(id, "boss.type", "ZOMBIE");
        set(id, "boss.health", 100);
        set(id, "boss.damage", 6);
        set(id, "boss.bar-color", "RED");
        set(id, "rewards.xp", 100);
        set(id, "rewards.vanilla-drops", true);
    }

    public void delete(String id) {
        yml.set("rituals." + id, null);
    }

    public void copy(String from, String to) {
        ConfigurationSection src = section(from);
        if (src == null) return;
        for (Map.Entry<String, Object> e : src.getValues(true).entrySet()) {
            if (e.getValue() instanceof ConfigurationSection) continue;
            set(to, e.getKey(), e.getValue());
        }
    }

    public Map<String, Object> snapshot(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        ConfigurationSection src = section(id);
        if (src == null) return out;
        for (Map.Entry<String, Object> e : src.getValues(true).entrySet()) {
            if (e.getValue() instanceof ConfigurationSection) continue;
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    public void restore(String id, Map<String, Object> snap) {
        yml.set("rituals." + id, null);
        for (Map.Entry<String, Object> e : snap.entrySet()) set(id, e.getKey(), e.getValue());
    }
}
