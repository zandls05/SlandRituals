package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/** Integraciones opcionales (MythicMobs y PlaceholderAPI) mediante reflexión: no son dependencias obligatorias. */
public final class Hooks {

    private final SlandRitualsPlugin plugin;
    private Method papiSet;

    public Hooks(SlandRitualsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean mythicAvailable() {
        return plugin.getConfig().getBoolean("integrations.mythicmobs", true)
                && Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    /** Invoca un mob de MythicMobs. Devuelve null si no se pudo (el llamador usa la alternativa vanilla). */
    public Entity spawnMythic(String mobId, Location loc) {
        try {
            Class<?> cls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = cls.getMethod("inst").invoke(null);
            Object helper = cls.getMethod("getAPIHelper").invoke(inst);
            Object res = helper.getClass().getMethod("spawnMythicMob", String.class, Location.class)
                    .invoke(helper, mobId, loc);
            return res instanceof Entity e ? e : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Throwable t = ex.getCause() != null ? ex.getCause() : ex;
            plugin.getLogger().warning("No se pudo invocar el mob de MythicMobs '" + mobId + "': " + t);
            return null;
        }
    }

    /** Resuelve placeholders de PlaceholderAPI si está instalado; si no, devuelve el texto igual. */
    public String parse(Player p, String text) {
        if (p == null || text == null || text.indexOf('%') < 0) return text;
        if (!plugin.getConfig().getBoolean("integrations.placeholderapi", true)) return text;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        try {
            if (papiSet == null) {
                papiSet = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                        .getMethod("setPlaceholders", Player.class, String.class);
            }
            Object r = papiSet.invoke(null, p, text);
            return r == null ? text : r.toString();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return text;
        }
    }
}
