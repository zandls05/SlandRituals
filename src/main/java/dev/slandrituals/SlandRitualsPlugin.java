package dev.slandrituals;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class SlandRitualsPlugin extends JavaPlugin {

    private RitualManager manager;
    private Cfg messages;
    private Cfg animations;
    private Hooks hooks;
    private ChatInput chat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String f : new String[]{"rituals.yml", "messages.yml", "animaciones.yml"}) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }
        messages = Cfg.load(this, "messages.yml");
        animations = Cfg.load(this, "animaciones.yml");
        hooks = new Hooks(this);
        chat = new ChatInput(this);

        manager = new RitualManager(this);
        manager.loadRituals();
        manager.loadAltars();
        manager.startTicker();

        getServer().getPluginManager().registerEvents(new AltarListener(this, manager), this);
        getServer().getPluginManager().registerEvents(chat, this);

        PluginCommand cmd = getCommand("rituales");
        if (cmd != null) {
            RitualCommand handler = new RitualCommand(this, manager);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("Cargados " + manager.getRituals().size() + " rituales y "
                + manager.getAltars().size() + " altares. MythicMobs: "
                + (hooks.mythicAvailable() ? "sí" : "no") + ".");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    /** Recarga config.yml, messages.yml, animaciones.yml y rituals.yml sin reiniciar. */
    public void reloadAll() {
        reloadConfig();
        messages = Cfg.load(this, "messages.yml");
        animations = Cfg.load(this, "animaciones.yml");
        manager.loadRituals();
    }

    public RitualManager manager() { return manager; }
    public Hooks hooks() { return hooks; }
    public ChatInput chat() { return chat; }
    public Cfg anim() { return animations; }

    public String prefix() {
        return color(messages.str("prefix", ""));
    }

    /** Mensaje con prefijo. Pares clave/valor para reemplazar {placeholders}. */
    public String msg(String key, String... pairs) {
        return prefix() + raw(key, pairs);
    }

    /** Mensaje sin prefijo. */
    public String raw(String key, String... pairs) {
        String text = messages.str(key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return color(text);
    }

    /** Lista de textos de messages.yml (por ejemplo la ayuda). */
    public List<String> messageList(String key) {
        Object o = messages.get(key);
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        return out;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
