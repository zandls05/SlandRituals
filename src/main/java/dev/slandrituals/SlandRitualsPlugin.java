package dev.slandrituals;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SlandRitualsPlugin extends JavaPlugin {

    private RitualManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "rituals.yml").exists()) {
            saveResource("rituals.yml", false);
        }
        manager = new RitualManager(this);
        manager.loadRituals();
        manager.loadAltars();

        getServer().getPluginManager().registerEvents(new AltarListener(this, manager), this);

        PluginCommand cmd = getCommand("ritual");
        if (cmd != null) {
            RitualCommand handler = new RitualCommand(this, manager);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("Cargados " + manager.getRituals().size() + " rituales y "
                + manager.getAltars().size() + " altares.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    /** Mensaje de config con prefijo. Pares clave/valor para reemplazar {placeholders}. */
    public String msg(String key, String... pairs) {
        return color(getConfig().getString("messages.prefix", "")) + raw(key, pairs);
    }

    /** Mensaje de config sin prefijo. */
    public String raw(String key, String... pairs) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return color(text);
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
