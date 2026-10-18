package dev.slandrituals;

import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RitualCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("menu", "create", "remove", "list", "reload");

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
        if (args.length == 0) {
            if (sender instanceof Player player) new AdminMenu(plugin, manager).open(player);
            else sender.sendMessage(plugin.msg("usage"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("only-players")); return true; }
                new AdminMenu(plugin, manager).open(p);
            }
            case "create" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("only-players")); return true; }
                if (args.length < 2) { sender.sendMessage(plugin.msg("usage")); return true; }
                Ritual r = manager.getRitual(args[1]);
                if (r == null) { sender.sendMessage(plugin.msg("unknown-ritual", "ritual", args[1])); return true; }
                Block b = p.getTargetBlockExact(6);
                if (b == null || b.getType().isAir()) { sender.sendMessage(plugin.msg("not-looking")); return true; }
                if (manager.createAltar(b, r)) sender.sendMessage(plugin.msg("altar-created", "ritual", r.name()));
                else sender.sendMessage(plugin.msg("altar-exists"));
            }
            case "remove" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("only-players")); return true; }
                Block b = p.getTargetBlockExact(6);
                if (b == null) { sender.sendMessage(plugin.msg("not-looking")); return true; }
                if (manager.removeAltar(b)) sender.sendMessage(plugin.msg("altar-removed"));
                else sender.sendMessage(plugin.msg("not-altar"));
            }
            case "list" -> {
                sender.sendMessage("§5Rituales (" + manager.getRituals().size() + "):");
                for (Ritual r : manager.getRituals()) {
                    long count = manager.getAltars().stream().filter(a -> a.ritualId.equals(r.id())).count();
                    sender.sendMessage(" §8- §f" + r.id() + " §7→ " + r.name() + " §7(" + count + " altares)");
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                manager.loadRituals();
                sender.sendMessage(plugin.msg("reload"));
            }
            default -> sender.sendMessage(plugin.msg("usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("slandrituals.admin")) return out;
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            for (Ritual r : manager.getRituals()) if (r.id().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(r.id());
        }
        return out;
    }
}
