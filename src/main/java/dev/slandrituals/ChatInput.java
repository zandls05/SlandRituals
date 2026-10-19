package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Entrada de datos por chat: el siguiente mensaje del jugador no se publica, se valida y se aplica.
 * El manejador devuelve null si el valor fue aceptado, o un texto de error para volver a preguntar.
 */
public final class ChatInput implements Listener {

    private record Prompt(Function<String, String> handler, Runnable onCancel, BukkitTask timeout) { }

    private final SlandRitualsPlugin plugin;
    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();

    public ChatInput(SlandRitualsPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String message, Function<String, String> handler, Runnable onCancel) {
        clear(p);
        UUID id = p.getUniqueId();
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(id) != null) {
                p.sendMessage(plugin.msg("input-timeout"));
                onCancel.run();
            }
        }, 20L * 60);
        pending.put(id, new Prompt(handler, onCancel, timeout));
        p.sendMessage(SlandRitualsPlugin.color(message));
        p.sendMessage(plugin.msg("input-hint"));
    }

    public void clear(Player p) {
        Prompt old = pending.remove(p.getUniqueId());
        if (old != null) old.timeout().cancel();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!pending.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        String text = e.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> handle(p, text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clear(e.getPlayer());
    }

    private void handle(Player p, String text) {
        UUID id = p.getUniqueId();
        Prompt pr = pending.get(id);
        if (pr == null) return;
        String in = text.trim();
        if (in.equalsIgnoreCase("cancelar")) {
            clear(p);
            p.sendMessage(plugin.msg("input-cancelled"));
            pr.onCancel().run();
            return;
        }
        String error = pr.handler().apply(in);
        if (error == null) {
            if (pending.get(id) == pr) {
                pending.remove(id);
                pr.timeout().cancel();
            }
        } else {
            p.sendMessage(plugin.msg("input-error", "error", error));
        }
    }
}
