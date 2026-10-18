package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RitualManager {

    private final SlandRitualsPlugin plugin;
    private final Map<String, Ritual> rituals = new LinkedHashMap<>();
    private final Map<String, Altar> altars = new HashMap<>();
    private final NamespacedKey bossKey;
    private final File altarFile;

    public RitualManager(SlandRitualsPlugin plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "ritual_boss");
        this.altarFile = new File(plugin.getDataFolder(), "altars.yml");
    }

    // ------------------------------------------------------------------ carga / guardado

    public void loadRituals() {
        rituals.clear();
        File f = new File(plugin.getDataFolder(), "rituals.yml");
        ConfigurationSection root = YamlConfiguration.loadConfiguration(f).getConfigurationSection("rituals");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Ritual r = Ritual.parse(id, s, plugin.getLogger());
            if (r != null) rituals.put(r.id(), r);
        }
    }

    public void loadAltars() {
        altars.clear();
        if (!altarFile.exists()) return;
        ConfigurationSection root = YamlConfiguration.loadConfiguration(altarFile).getConfigurationSection("altars");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null) continue;
            Altar a = new Altar(s.getString("world", "world"), s.getInt("x"), s.getInt("y"),
                    s.getInt("z"), s.getString("ritual", ""));
            altars.put(a.key(), a);
        }
    }

    public void saveAltars() {
        YamlConfiguration yml = new YamlConfiguration();
        int i = 0;
        for (Altar a : altars.values()) {
            String p = "altars." + (i++);
            yml.set(p + ".world", a.world);
            yml.set(p + ".x", a.x);
            yml.set(p + ".y", a.y);
            yml.set(p + ".z", a.z);
            yml.set(p + ".ritual", a.ritualId);
        }
        try {
            yml.save(altarFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar altars.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        for (Altar a : altars.values()) {
            if (a.task != null) a.task.cancel();
            if (a.bar != null) a.bar.removeAll();
            if (a.boss != null && a.boss.isValid()) a.boss.remove();
        }
        saveAltars();
    }

    // ------------------------------------------------------------------ acceso

    public Collection<Ritual> getRituals() { return rituals.values(); }
    public Collection<Altar> getAltars() { return altars.values(); }
    public Ritual getRitual(String id) { return id == null ? null : rituals.get(id.toLowerCase(Locale.ROOT)); }

    public Altar getAltar(Block b) {
        return altars.get(Altar.key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
    }

    public boolean createAltar(Block b, Ritual r) {
        if (getAltar(b) != null) return false;
        Altar a = new Altar(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), r.id());
        altars.put(a.key(), a);
        saveAltars();
        return true;
    }

    public boolean removeAltar(Block b) {
        Altar a = getAltar(b);
        if (a == null) return false;
        abort(a);
        altars.remove(a.key());
        saveAltars();
        return true;
    }

    // ------------------------------------------------------------------ ofrendas

    public void showProgress(Player p, Altar a, Ritual r) {
        p.sendMessage(plugin.raw("progress-header", "ritual", r.name()));
        for (Map.Entry<Material, Integer> e : r.offerings().entrySet()) {
            p.sendMessage(plugin.raw("progress-line",
                    "item", pretty(e.getKey()),
                    "have", String.valueOf(a.delivered.getOrDefault(e.getKey(), 0)),
                    "need", String.valueOf(e.getValue())));
        }
    }

    /** Devuelve el ritual si el altar admite ofrendas ahora; si no, avisa al jugador y devuelve null. */
    private Ritual checkAvailable(Player p, Altar a) {
        Ritual r = rituals.get(a.ritualId);
        if (r == null) {
            p.sendMessage(plugin.msg("unknown-ritual", "ritual", a.ritualId));
            return null;
        }
        if (a.state == Altar.State.COOLDOWN) {
            long left = (a.cooldownUntil - System.currentTimeMillis()) / 1000;
            if (left > 0) {
                p.sendMessage(plugin.msg("cooldown", "time", String.valueOf(left)));
                return null;
            }
            a.state = Altar.State.IDLE;
        }
        if (a.state != Altar.State.IDLE) {
            p.sendMessage(plugin.msg("busy"));
            return null;
        }
        return r;
    }

    /** Un jugador hace clic derecho sobre el altar: abre el menú (o entrega desde la mano si use-menu=false). */
    public void interact(Player p, Altar a, boolean sneaking) {
        Ritual r = checkAvailable(p, a);
        if (r == null) return;

        if (plugin.getConfig().getBoolean("settings.use-menu", true)) {
            new AltarMenu(plugin, this, a, r).open(p);
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            showProgress(p, a, r);
            return;
        }
        deposit(p, a, hand.getType(), sneaking);
    }

    /** Toma objetos del inventario del jugador y los suma a las ofrendas del altar. */
    public void deposit(Player p, Altar a, Material m, boolean all) {
        Ritual r = checkAvailable(p, a);
        if (r == null) {
            refreshMenus(a);
            return;
        }
        Integer required = r.offerings().get(m);
        if (required == null) {
            p.sendMessage(plugin.msg("wrong-item"));
            return;
        }
        int have = a.delivered.getOrDefault(m, 0);
        int missing = required - have;
        if (missing <= 0) {
            p.sendMessage(plugin.msg("item-complete", "item", pretty(m)));
            return;
        }
        int owned = countItems(p, m);
        if (owned <= 0) {
            p.sendMessage(plugin.msg("no-item", "item", pretty(m)));
            return;
        }

        int give = all ? Math.min(missing, owned) : 1;
        removeItems(p, m, give);
        a.delivered.merge(m, give, Integer::sum);

        Location c = a.center();
        if (c != null) {
            c.getWorld().playSound(c, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.7f);
            c.getWorld().spawnParticle(Particle.ENCHANT, c.clone().add(0, 0.8, 0), 25, 0.4, 0.4, 0.4, 0.6);
        }
        p.sendMessage(plugin.msg("offered",
                "item", pretty(m),
                "have", String.valueOf(have + give),
                "need", String.valueOf(required)));

        if (isComplete(a, r)) startCountdown(a, r, p);
        refreshMenus(a);
    }

    /** Redibuja los menús abiertos de este altar; si ya no admite ofrendas, los cierra. */
    private void refreshMenus(Altar a) {
        for (Player pl : Bukkit.getOnlinePlayers()) {
            InventoryHolder h = pl.getOpenInventory().getTopInventory().getHolder();
            if (h instanceof AltarMenu menu && menu.altar() == a) {
                if (a.state == Altar.State.IDLE) menu.render();
                else Bukkit.getScheduler().runTask(plugin, () -> pl.closeInventory());
            }
        }
    }

    private int countItems(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m) n += it.getAmount();
        }
        return n;
    }

    private void removeItems(Player p, Material m, int amount) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != m) continue;
            int take = Math.min(amount, it.getAmount());
            amount -= take;
            if (it.getAmount() - take <= 0) contents[i] = null;
            else it.setAmount(it.getAmount() - take);
        }
        p.getInventory().setStorageContents(contents);
    }

    private boolean isComplete(Altar a, Ritual r) {
        for (Map.Entry<Material, Integer> e : r.offerings().entrySet()) {
            if (a.delivered.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ cuenta atrás

    private void startCountdown(Altar a, Ritual r, Player starter) {
        Location c = a.center();
        if (c == null) return;
        a.state = Altar.State.CHARGING;
        a.starter = starter.getUniqueId();
        double radius = plugin.getConfig().getDouble("settings.broadcast-radius", 60);

        for (Player near : nearby(c, radius)) {
            near.sendMessage(plugin.msg("ritual-start", "player", starter.getName(), "ritual", r.name()));
        }

        a.task = new BukkitRunnable() {
            int ticks = 0;
            int seconds = r.countdown();

            @Override
            public void run() {
                Location center = a.center();
                if (center == null) { cancel(); abort(a); return; }
                World w = center.getWorld();

                // anillo de partículas que gira y sube
                double angle = ticks * 0.35;
                double rise = (ticks % 40) / 40.0 * 2.0;
                for (int i = 0; i < 3; i++) {
                    double a2 = angle + i * (Math.PI * 2 / 3);
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME,
                            center.getX() + Math.cos(a2) * 1.4,
                            center.getY() + 0.3 + rise,
                            center.getZ() + Math.sin(a2) * 1.4, 1, 0, 0, 0, 0);
                }

                if (ticks % 5 == 0) { // cada 5 ticks (=20 ticks reales) baja un segundo
                    if (seconds <= 0) {
                        cancel();
                        spawnBoss(a, r);
                        return;
                    }
                    w.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 1f, 0.5f + (r.countdown() - seconds) * 0.05f);
                    for (Player p : nearby(center, radius)) {
                        p.sendTitle(r.name(), "§7" + seconds, 0, 25, 5);
                    }
                    seconds--;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    // ------------------------------------------------------------------ jefe

    private void spawnBoss(Altar a, Ritual r) {
        Location c = a.center();
        if (c == null) { abort(a); return; }
        World w = c.getWorld();
        Location spawn = c.clone().add(0, 1, 0);

        w.strikeLightningEffect(spawn);
        w.playSound(spawn, Sound.ENTITY_WITHER_SPAWN, 1.2f, 1f);
        w.spawnParticle(Particle.EXPLOSION, spawn, 3, 0.5, 0.5, 0.5, 0);

        Entity e = w.spawnEntity(spawn, r.bossType());
        if (!(e instanceof LivingEntity boss)) {
            e.remove();
            abort(a);
            return;
        }

        AttributeInstance hp = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(r.health());
            boss.setHealth(Math.min(r.health(), hp.getValue()));
        }
        AttributeInstance dmg = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(r.damage());

        boss.setCustomName(r.name());
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, a.key());

        EntityEquipment eq = boss.getEquipment();
        if (eq != null) {
            for (Map.Entry<EquipmentSlot, Material> g : r.gear().entrySet()) {
                eq.setItem(g.getKey(), new ItemStack(g.getValue()));
                eq.setDropChance(g.getKey(), 0f);
            }
        }

        if (boss instanceof Mob mob && a.starter != null) {
            Player starter = Bukkit.getPlayer(a.starter);
            if (starter != null) mob.setTarget(starter);
        }

        a.boss = boss;
        a.state = Altar.State.ACTIVE;
        a.bar = Bukkit.createBossBar(r.name(), r.barColor(), BarStyle.SEGMENTED_10);

        double radius = plugin.getConfig().getDouble("settings.broadcast-radius", 60);
        for (Player p : nearby(spawn, radius)) {
            p.sendMessage(plugin.msg("boss-spawned", "boss", r.name()));
        }

        a.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (a.state != Altar.State.ACTIVE) { cancel(); return; }
                if (!boss.isValid()) { // desapareció sin morir (void, comando, despawn...)
                    cancel();
                    Player st = a.starter == null ? null : Bukkit.getPlayer(a.starter);
                    if (st != null) st.sendMessage(plugin.msg("ritual-failed"));
                    abort(a);
                    return;
                }
                double max = boss.getAttribute(Attribute.MAX_HEALTH) == null
                        ? r.health() : boss.getAttribute(Attribute.MAX_HEALTH).getValue();
                a.bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / max)));

                Location bl = boss.getLocation();
                for (Player p : boss.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(bl) <= radius * radius) a.bar.addPlayer(p);
                    else a.bar.removePlayer(p);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /** Llamado desde el listener cuando muere cualquier entidad. */
    public void handleDeath(EntityDeathEvent e) {
        String key = e.getEntity().getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        if (key == null) return;
        Altar a = altars.get(key);
        if (a == null) return;
        Ritual r = rituals.get(a.ritualId);
        if (r == null) { abort(a); return; }

        if (!r.vanillaDrops()) e.getDrops().clear();
        for (Map.Entry<Material, Integer> d : r.drops().entrySet()) {
            e.getDrops().add(new ItemStack(d.getKey(), d.getValue()));
        }
        e.setDroppedExp(r.xp());

        Player killer = e.getEntity().getKiller();
        String name = killer != null ? killer.getName()
                : (a.starter != null && Bukkit.getOfflinePlayer(a.starter).getName() != null
                        ? Bukkit.getOfflinePlayer(a.starter).getName() : "alguien");
        for (String cmd : r.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", name));
        }

        double radius = plugin.getConfig().getDouble("settings.broadcast-radius", 60);
        for (Player p : nearby(e.getEntity().getLocation(), radius)) {
            p.sendMessage(plugin.msg("boss-defeated", "player", name, "boss", r.name()));
        }

        cleanup(a);
        a.state = Altar.State.COOLDOWN;
        a.cooldownUntil = System.currentTimeMillis() + r.cooldown() * 1000L;
    }

    // ------------------------------------------------------------------ utilidades

    /** Cancela todo y deja el altar libre otra vez (las ofrendas se pierden). */
    private void abort(Altar a) {
        if (a.boss != null && a.boss.isValid()) a.boss.remove();
        cleanup(a);
        a.state = Altar.State.IDLE;
    }

    private void cleanup(Altar a) {
        if (a.task != null) { a.task.cancel(); a.task = null; }
        if (a.bar != null) { a.bar.removeAll(); a.bar = null; }
        a.boss = null;
        a.starter = null;
        a.delivered.clear();
    }

    private Collection<Player> nearby(Location loc, double radius) {
        return loc.getWorld().getNearbyPlayers(loc, radius);
    }

    public static String pretty(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
