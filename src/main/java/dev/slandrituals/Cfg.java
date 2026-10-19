package dev.slandrituals;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** Lee un YAML del servidor y, si falta una clave, usa el valor por defecto que viene dentro del .jar. */
public final class Cfg {

    private final YamlConfiguration user;
    private final YamlConfiguration def;

    private Cfg(YamlConfiguration user, YamlConfiguration def) {
        this.user = user;
        this.def = def;
    }

    public static Cfg load(JavaPlugin plugin, String name) {
        YamlConfiguration u = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
        YamlConfiguration d = new YamlConfiguration();
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                d = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // sin valores por defecto
        }
        return new Cfg(u, d);
    }

    public Object get(String path) {
        Object o = user.get(path);
        return o != null ? o : def.get(path);
    }

    public String str(String path, String fallback) {
        Object o = get(path);
        return o == null || o instanceof ConfigurationSection ? fallback : String.valueOf(o);
    }

    public int integer(String path, int fallback) {
        Object o = get(path);
        return o instanceof Number n ? n.intValue() : fallback;
    }

    public double dbl(String path, double fallback) {
        Object o = get(path);
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    public boolean bool(String path, boolean fallback) {
        Object o = get(path);
        return o instanceof Boolean b ? b : fallback;
    }

    /** Claves hijas de una sección (la del usuario si existe; si no, la del .jar). */
    public Set<String> keys(String path) {
        ConfigurationSection s = user.getConfigurationSection(path);
        if (s == null) s = def.getConfigurationSection(path);
        return s == null ? new LinkedHashSet<>() : s.getKeys(false);
    }
}
