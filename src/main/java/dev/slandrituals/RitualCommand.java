package dev.slandrituals;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /rituales (alias /ritual): administración de rituales y altares. */
public final class RitualCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("ayuda", "menu", "listar", "crear", "editar", "duplicar",
            "eliminar", "altar", "quitaraltar", "reiniciar", "probar", "guardar", "recargar");

    private final SlandRitualsPlugin plugin;
    private final RitualManager manager;

    public RitualCommand(SlandRitualsPlugin plugin, RitualManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("slandrituals.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ayuda", "help" -> help(sender);
            case "menu" -> {
                Player p = player(sender);
                if (p != null) new ArchiveMenu(plugin, manager, 0).open(p);
            }
            case "listar", "list" -> list(sender);
            case "crear", "create" -> create(sender, args);
            case "editar", "edit" -> edit(sender, args);
            case "duplicar" -> duplicate(sender, args);
            case "eliminar" -> delete(sender, args);
            case "altar" -> altar(sender, args);
            case "quitaraltar" -> removeAltar(sender);
            case "reiniciar" -> resetAltar(sender);
            case "probar" -> test(sender, args);
            case "guardar" -> {
                sender.sendMessage(plugin.msg(manager.saveAll() ? "saved-all" : "save-failed"));
            }
            case "recargar", "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(plugin.msg("reload"));
            }
            default -> sender.sendMessage(plugin.msg("usage"));
        }
        return true;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(plugin.msg("only-players"));
        return null;
    }

    private Ritual ritual(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(plugin.msg("usage"));
            return null;
        }
        Ritual r = manager.getRitual(args[index]);
        if (r == null) sender.sendMessage(plugin.msg("unknown-ritual", "ritual", args[index]));
        return r;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(plugin.raw("help-header"));
        for (String line : plugin.messageList("help-lines")) sender.sendMessage(SlandRitualsPlugin.color(line));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(plugin.raw("list-header", "count", String.valueOf(manager.getRituals().size())));
        for (Ritual r : manager.getRituals()) {
            sender.sendMessage(plugin.raw("list-line", "id", r.id(), "name", r.name(),
                    "altars", String.valueOf(manager.altarsOf(r.id()).size()), "status", manager.statusLabel(r)));
        }
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(plugin.msg("usage")); return; }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!manager.validId(id)) { sender.sendMessage(plugin.msg("invalid-id")); return; }
        if (manager.store().exists(id)) { sender.sendMessage(plugin.msg("ritual-exists", "ritual", id)); return; }
        manager.createRitual(id);
        sender.sendMessage(plugin.msg("ritual-created", "ritual", id));
        if (sender instanceof Player p) {
            manager.beginEdit(p, id);
            new EditorMenu(plugin, manager, id).open(p);
        }
    }

    private void edit(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        Ritual r = ritual(sender, args, 1);
        if (r != null) {
            manager.beginEdit(p, r.id());
            new EditorMenu(plugin, manager, r.id()).open(p);
        }
    }

    private void duplicate(CommandSender sender, String[] args) {
        Ritual r = ritual(sender, args, 1);
        if (r == null) return;
        if (args.length < 3) { sender.sendMessage(plugin.msg("usage")); return; }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!manager.validId(id)) { sender.sendMessage(plugin.msg("invalid-id")); return; }
        if (manager.store().exists(id)) { sender.sendMessage(plugin.msg("ritual-exists", "ritual", id)); return; }
        manager.duplicateRitual(r.id(), id);
        sender.sendMessage(plugin.msg("ritual-duplicated", "ritual", r.id(), "new", id));
    }

    private void delete(CommandSender sender, String[] args) {
        Ritual r = ritual(sender, args, 1);
        if (r == null) return;
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirmar")) {
            sender.sendMessage(plugin.msg("confirm-delete", "ritual", r.id()));
            return;
        }
        manager.deleteRitual(r.id());
        sender.sendMessage(plugin.msg("ritual-deleted", "ritual", r.id()));
    }

    private void altar(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        Ritual r = ritual(sender, args, 1);
        if (r == null) return;
        Block b = p.getTargetBlockExact(6);
        if (b == null || b.getType().isAir()) p.sendMessage(plugin.msg("not-looking"));
        else if (manager.createAltar(b, r)) p.sendMessage(plugin.msg("altar-created", "ritual", r.name()));
        else p.sendMessage(plugin.msg("altar-exists"));
    }

    private void removeAltar(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        Block b = p.getTargetBlockExact(6);
        if (b == null) p.sendMessage(plugin.msg("not-looking"));
        else if (manager.removeAltar(b)) p.sendMessage(plugin.msg("altar-removed"));
        else p.sendMessage(plugin.msg("not-altar"));
    }

    private void resetAltar(CommandSender sender) {
        Player p = player(sender);
        if (p == null) return;
        Block b = p.getTargetBlockExact(6);
        Altar a = b == null ? null : manager.getAltar(b);
        if (a == null) {
            p.sendMessage(plugin.msg("not-altar"));
            return;
        }
        manager.resetAltar(a);
        p.sendMessage(plugin.msg("altar-reset"));
    }

    private void test(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) return;
        Ritual r = ritual(sender, args, 1);
        if (r == null) return;
        Block b = p.getTargetBlockExact(6);
        if (b == null || b.getType().isAir()) b = p.getLocation().getBlock();
        manager.startTest(p, r, b);
        p.sendMessage(plugin.msg("test-started", "ritual", r.name()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("slandrituals.admin")) return out;
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && List.of("editar", "duplicar", "eliminar", "altar", "probar").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Ritual r : manager.getRituals()) if (r.id().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(r.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("eliminar")) {
            out.add("confirmar");
        }
        return out;
    }
}
