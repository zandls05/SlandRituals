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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RitualManager {

    /** Copia del ritual al empezar a editarlo, para poder descartar los cambios. */
    private record Snapshot(String ritualId, Map<String, Object> data) { }

    private final SlandRitualsPlugin plugin;
    private final RitualStore store;
    private final Map<String, Ritual> rituals = new LinkedHashMap<>();
    private final Map<String, Altar> altars = new HashMap<>();
    private final Map<String, Altar> tests = new HashMap<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final NamespacedKey bossKey;
    private final File altarFile;
    private BukkitTask ticker;
    private boolean dirty;
    private int tickCount;

    public RitualManager(SlandRitualsPlugin plugin) {
        this.plugin = plugin;
        this.store = new RitualStore(plugin);
        this.bossKey = new NamespacedKey(plugin, "ritual_boss");
        this.altarFile = new File(plugin.getDataFolder(), "altars.yml");
    }

    public RitualStore store() { return store; }

    // ------------------------------------------------------------------ carga / guardado

    public void loadRituals() {
        store.reload();
        boolean renamed = false;
        for (String key : store.ids()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (!key.equals(lower) && !store.exists(lower)) { // los ids se guardan siempre en minúsculas
                store.copy(key, lower);
                store.delete(key);
                renamed = true;
            }
        }
        if (renamed) store.save();

        rituals.clear();
        for (String id : store.ids()) {
            ConfigurationSection s = store.section(id);
            if (s == null) continue;
            Ritual r = Ritual.parse(id, s, plugin);
            if (r != null) rituals.put(r.id(), r);
        }
    }

    public void loadAltars() {
        altars.clear();
        if (!altarFile.exists()) return;
        ConfigurationSection root = YamlConfiguration.loadConfiguration(altarFile).getConfigurationSection("altars");
        if (root == null) return;
        long now = System.currentTimeMillis();
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null) continue;
            Altar a = new Altar(s.getString("world", "world"), s.getInt("x"), s.getInt("y"),
                    s.getInt("z"), s.getString("ritual", ""));
            ConfigurationSection del = s.getConfigurationSection("delivered");
            if (del != null) {
                for (String mk : del.getKeys(false)) {
                    Material m = Material.matchMaterial(mk);
                    if (m != null && del.getInt(mk) > 0) a.delivered.put(m, del.getInt(mk));
                }
            }
            long until = s.getLong("cooldown-until", 0);
            if (until > now) {
                a.state = Altar.State.COOLDOWN;
                a.cooldownUntil = until;
            }
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
            for (Map.Entry<Material, Integer> e : a.delivered.entrySet()) {
                yml.set(p + ".delivered." + e.getKey().name(), e.getValue());
            }
            if (a.state == Altar.State.COOLDOWN) yml.set(p + ".cooldown-until", a.cooldownUntil);
        }
        try {
            yml.save(altarFile);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar altars.yml: " + e.getMessage());
        }
    }

    /** Guarda rituals.yml y altars.yml. */
    public boolean saveAll() {
        saveAltars();
        return store.save();
    }

    public void startTicker() {
        ticker = new BukkitRunnable() {
            @Override
            public void run() {
                tickAltars();
            }
        }.runTaskTimer(plugin, 40L, 20L);
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        for (Altar a : new ArrayList<>(altars.values())) {
            if (a.state == Altar.State.CHARGING || a.state == Altar.State.ACTIVE) abort(a);
        }
        for (Altar a : new ArrayList<>(tests.values())) abort(a);
        tests.clear();
        saveAltars();
    }

    /** Cada segundo: termina enfriamientos, guarda cambios pendientes y dibuja un aura suave en altares inactivos. */
    private void tickAltars() {
        long now = System.currentTimeMillis();
        boolean ambient = plugin.getConfig().getBoolean("performance.ambient-particles", true);
        for (Altar a : altars.values()) {
            if (a.state == Altar.State.COOLDOWN && now >= a.cooldownUntil) {
                a.state = Altar.State.IDLE;
                a.cooldownUntil = 0;
                dirty = true;
            }
            if (!ambient || a.state != Altar.State.IDLE) continue;
            World w = Bukkit.getWorld(a.world);
            if (w == null || !w.isChunkLoaded(a.x >> 4, a.z >> 4)) continue;
            Location c = a.center();
            if (c == null || w.getNearbyPlayers(c, 20).isEmpty()) continue;
            w.spawnParticle(Particle.ENCHANT, c.getX(), c.getY() + 1.0, c.getZ(),
                    a.delivered.isEmpty() ? 4 : 12, 0.35, 0.35, 0.35, 0.4);
        }
        if (dirty && ++tickCount % 10 == 0) saveAltars();
    }

    // ------------------------------------------------------------------ acceso

    public Collection<Ritual> getRituals() { return rituals.values(); }
    public Collection<Altar> getAltars() { return altars.values(); }
    public Ritual getRitual(String id) { return id == null ? null : rituals.get(id.toLowerCase(Locale.ROOT)); }

    public Altar getAltar(Block b) {
        return altars.get(Altar.key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
    }

    public List<Altar> altarsOf(String ritualId) {
        List<Altar> out = new ArrayList<>();
        for (Altar a : altars.values()) if (a.ritualId.equals(ritualId)) out.add(a);
        return out;
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
        return a != null && removeAltar(a);
    }

    public boolean removeAltar(Altar a) {
        abort(a);
        altars.remove(a.key());
        saveAltars();
        return true;
    }

    /** Cancela lo que esté en marcha, devuelve las ofrendas y quita el enfriamiento. */
    public void resetAltar(Altar a) {
        abort(a);
        a.cooldownUntil = 0;
        dirty = true;
    }

    // ------------------------------------------------------------------ edición de rituales (menús)

    public boolean validId(String id) {
        return id != null && id.matches("[a-z0-9_]{1,32}");
    }

    public void createRitual(String id) {
        store.create(id);
        store.save();
        loadRituals();
    }

    public void duplicateRitual(String from, String to) {
        store.copy(from, to);
        store.set(to, "name", store.get(from, "name") + " &7(copia)");
        store.save();
        loadRituals();
    }

    /** Elimina el ritual y todos sus altares (las ofrendas guardadas se devuelven). */
    public void deleteRitual(String id) {
        for (Altar a : altarsOf(id)) removeAltar(a);
        store.delete(id);
        store.save();
        loadRituals();
    }

    /** Cambia un valor y lo guarda en rituals.yml al instante. value=null restablece el valor por defecto. */
    public void editSet(String id, String path, Object value) {
        store.set(id, path, value);
        store.save();
        loadRituals();
    }

    /** Toma la copia de seguridad del ritual al entrar al editor (para poder descartar los cambios). */
    public void beginEdit(Player p, String id) {
        snapshots.put(p.getUniqueId(), new Snapshot(id, store.snapshot(id)));
    }

    public void commitEdit(Player p) {
        snapshots.remove(p.getUniqueId());
        store.save();
    }

    /** Descarta los cambios hechos desde que se abrió el editor. */
    public boolean cancelEdit(Player p) {
        Snapshot s = snapshots.remove(p.getUniqueId());
        if (s == null) return false;
        store.restore(s.ritualId(), s.data());
        store.save();
        loadRituals();
        return true;
    }

    /**
     * Si el ritual aún hereda las animaciones por defecto de una fase, las copia a rituals.yml
     * para poder activarlas o desactivarlas una a una sin perder el resto.
     */
    public void materializeAnimations(String id, Ritual.PhaseId pid) {
        String base = "cinematic." + pid.key + ".animations";
        ConfigurationSection s = store.section(id);
        if (s == null || s.getConfigurationSection(base) != null) return;
        String defBase = "phases." + pid.key + ".animations";
        for (String k : plugin.anim().keys(defBase)) {
            store.set(id, base + "." + k + ".enabled", plugin.anim().bool(defBase + "." + k + ".enabled", true));
        }
        store.save();
        loadRituals();
    }

    // ------------------------------------------------------------------ estados

    public String statusLabel(Altar a) {
        return switch (a.state) {
            case CHARGING, ACTIVE -> plugin.raw("status-running");
            case COOLDOWN -> plugin.raw("status-cooldown", "time", formatTime(cooldownLeft(a)));
            case IDLE -> plugin.raw(a.delivered.isEmpty() ? "status-idle" : "status-ready");
        };
    }

    /** Estado resumido de un ritual según sus altares. */
    public String statusLabel(Ritual r) {
        List<Altar> list = altarsOf(r.id());
        boolean running = false, ready = false;
        long cooldown = 0;
        for (Altar a : list) {
            if (a.state == Altar.State.CHARGING || a.state == Altar.State.ACTIVE) running = true;
            else if (a.state == Altar.State.COOLDOWN) cooldown = Math.max(cooldown, cooldownLeft(a));
            else if (!a.delivered.isEmpty()) ready = true;
        }
        if (running) return plugin.raw("status-running");
        if (ready) return plugin.raw("status-ready");
        if (cooldown > 0) return plugin.raw("status-cooldown", "time", formatTime(cooldown));
        return plugin.raw("status-idle");
    }

    public long cooldownLeft(Altar a) {
        return Math.max(0, (a.cooldownUntil - System.currentTimeMillis()) / 1000);
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
            long left = cooldownLeft(a);
            if (left > 0) {
                p.sendMessage(plugin.msg("cooldown", "time", formatTime(left)));
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

    /** Comprueba permiso, hora y jugadores cercanos ANTES de consumir nada. Devuelve el mensaje de error o null. */
    private String checkConditions(Player p, Altar a, Ritual r) {
        Ritual.Conditions c = r.conditions();
        if (!c.permission().isEmpty() && !p.hasPermission(c.permission())) {
            return plugin.msg("condition-permission");
        }
        Location loc = a.center();
        if (loc == null) return plugin.msg("world-missing");
        if (c.time() != Ritual.TimeMode.ANY) {
            long t = loc.getWorld().getTime() % 24000;
            boolean day = t < 12300 || t > 23000;
            if (c.time() == Ritual.TimeMode.DAY && !day) return plugin.msg("condition-day");
            if (c.time() == Ritual.TimeMode.NIGHT && day) return plugin.msg("condition-night");
        }
        if (c.minPlayers() > 0) {
            int near = loc.getWorld().getNearbyPlayers(loc, r.radius()).size();
            if (near < c.minPlayers()) {
                return plugin.msg("condition-players", "need", String.valueOf(c.minPlayers()),
                        "have", String.valueOf(near));
            }
        }
        return null;
    }

    /** Un jugador hace clic derecho sobre el altar: abre el menú (o entrega desde la mano si use-menu=false). */
    public void interact(Player p, Altar a, boolean sneaking) {
        Ritual r = checkAvailable(p, a);
        if (r == null) return;
        String problem = checkConditions(p, a, r);
        if (problem != null) {
            p.sendMessage(problem);
            return;
        }
        if (isComplete(a, r)) { // ofrendas completas pero el ritual no arrancó (por ejemplo, tras editarlo)
            startCeremony(a, r, p);
            return;
        }

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
        String problem = checkConditions(p, a, r);
        if (problem != null) {
            p.sendMessage(problem);
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
        dirty = true;

        Location c = a.center();
        if (c != null) {
            c.getWorld().playSound(c, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.7f);
            c.getWorld().spawnParticle(Particle.ENCHANT, c.clone().add(0, 0.8, 0), 25, 0.4, 0.4, 0.4, 0.6);
        }
        p.sendMessage(plugin.msg("offered",
                "item", pretty(m),
                "have", String.valueOf(have + give),
                "need", String.valueOf(required)));

        if (isComplete(a, r)) startCeremony(a, r, p);
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

    // ------------------------------------------------------------------ invocación

    private void startCeremony(Altar a, Ritual r, Player starter) {
        if (a.center() == null) return;
        a.state = Altar.State.CHARGING;
        a.starter = starter.getUniqueId();
        a.bar = Bukkit.createBossBar(plugin.raw("bar-summoning", "ritual", r.name()), r.boss().barColor(), BarStyle.SOLID);
        a.bar.setProgress(0);
        a.task = new Ceremony(plugin, this, a, r, starter).runTaskTimer(plugin, 0L, 2L);
    }

    /** Prueba la cinemática en un bloque sin gastar ofrendas ni dar recompensas. */
    public void startTest(Player p, Ritual r, Block b) {
        Altar a = new Altar(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), r.id());
        a.test = true;
        tests.put(a.key(), a);
        startCeremony(a, r, p);
    }

    public void syncBar(BossBar bar, Collection<Player> viewers) {
        for (Player p : new ArrayList<>(bar.getPlayers())) {
            if (!viewers.contains(p)) bar.removePlayer(p);
        }
        for (Player p : viewers) bar.addPlayer(p);
    }

    /** Invoca al jefe. Si no puede, devuelve las ofrendas, libera el altar y devuelve false. */
    boolean spawnBoss(Altar a, Ritual r) {
        Location c = a.center();
        Player starter = a.starter == null ? null : Bukkit.getPlayer(a.starter);
        if (c == null) {
            abort(a);
            return false;
        }
        Ritual.BossSpec b = r.boss();
        Location spawn = resolveSpawn(c, b);
        if (spawn == null) {
            if (starter != null) starter.sendMessage(plugin.msg("boss-spawn-blocked"));
            abort(a);
            return false;
        }

        Entity e = null;
        if (!b.mythicId().isEmpty()) {
            if (plugin.hooks().mythicAvailable()) e = plugin.hooks().spawnMythic(b.mythicId(), spawn);
            if (e == null) {
                plugin.getLogger().warning("Ritual '" + r.id() + "': MythicMobs no pudo invocar '" + b.mythicId()
                        + "'; se usa el jefe vanilla " + b.type() + ".");
                if (starter != null && starter.hasPermission("slandrituals.admin")) {
                    starter.sendMessage(plugin.msg("mythic-fallback", "mob", b.mythicId()));
                }
            }
        }
        boolean mythic = e != null;
        if (e == null) {
            try {
                e = spawn.getWorld().spawnEntity(spawn, b.type());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Ritual '" + r.id() + "': no se pudo crear " + b.type() + ": " + ex.getMessage());
            }
        }
        if (!(e instanceof LivingEntity boss)) {
            if (e != null) e.remove();
            if (starter != null) starter.sendMessage(plugin.msg("boss-spawn-failed"));
            abort(a);
            return false;
        }

        if (!mythic) {
            AttributeInstance hp = boss.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(b.health());
                boss.setHealth(Math.min(b.health(), hp.getValue()));
            }
            AttributeInstance dmg = boss.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(b.damage());

            boss.setCustomName(b.name());
            boss.setCustomNameVisible(true);

            EntityEquipment eq = boss.getEquipment();
            if (eq != null) {
                for (Map.Entry<EquipmentSlot, Material> g : b.gear().entrySet()) {
                    eq.setItem(g.getKey(), new ItemStack(g.getValue()));
                    eq.setDropChance(g.getKey(), 0f);
                }
            }
        }
        boss.setRemoveWhenFarAway(false);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, a.key());

        if (boss instanceof Mob mob && starter != null) mob.setTarget(starter);

        a.boss = boss;
        a.state = Altar.State.ACTIVE;
        if (a.bar != null) {
            a.bar.setTitle(b.name());
            a.bar.setStyle(BarStyle.SEGMENTED_10);
            a.bar.setProgress(1.0);
        }

        for (Player p : c.getWorld().getNearbyPlayers(spawn, r.radius())) {
            p.sendMessage(plugin.msg("boss-spawned", "boss", b.name()));
        }
        startMonitor(a, r, boss);

        if (a.test) {
            int secs = Math.max(3, plugin.getConfig().getInt("settings.test-boss-seconds", 10));
            if (starter != null) starter.sendMessage(plugin.msg("test-boss", "time", String.valueOf(secs)));
            Bukkit.getScheduler().runTaskLater(plugin, () -> finishTest(a), secs * 20L);
        }
        return true;
    }

    /** Punto de aparición: centro del altar + desplazamiento; sube hasta encontrar hueco libre. */
    private Location resolveSpawn(Location c, Ritual.BossSpec b) {
        Location l = c.clone().add(b.dx(), b.dy(), b.dz());
        l.getChunk(); // fuerza la carga del chunk
        for (int i = 0; i < 6; i++) {
            if (l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable()) return l;
            l.add(0, 1, 0);
        }
        return null;
    }

    private void startMonitor(Altar a, Ritual r, LivingEntity boss) {
        double radius = r.radius();
        a.monitor = new BukkitRunnable() {
            @Override
            public void run() {
                if (a.state != Altar.State.ACTIVE) { cancel(); return; }
                if (!boss.isValid()) { // desapareció sin morir (vacío, comando, descarga...)
                    cancel();
                    Player st = a.starter == null ? null : Bukkit.getPlayer(a.starter);
                    if (st != null) st.sendMessage(plugin.msg("ritual-failed"));
                    abort(a);
                    return;
                }
                AttributeInstance maxAttr = boss.getAttribute(Attribute.MAX_HEALTH);
                double max = maxAttr == null ? r.boss().health() : maxAttr.getValue();
                if (a.bar != null) {
                    a.bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / max)));
                    Location bl = boss.getLocation();
                    for (Player p : boss.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(bl) <= radius * radius) a.bar.addPlayer(p);
                        else a.bar.removePlayer(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /** Llamado desde el listener cuando muere cualquier entidad. */
    public void handleDeath(EntityDeathEvent e) {
        String key = e.getEntity().getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        if (key == null) return;
        Altar a = altars.get(key);
        if (a == null) a = tests.get(key);
        if (a == null) return;

        if (a.test) { // jefe de prueba: sin drops ni recompensas
            e.getDrops().clear();
            e.setDroppedExp(0);
            finishTest(a);
            return;
        }
        Ritual r = rituals.get(a.ritualId);
        if (r == null) { abort(a); return; }

        Ritual.Rewards rw = r.rewards();
        if (!rw.vanillaDrops()) e.getDrops().clear();
        for (Map.Entry<Material, Integer> d : rw.drops().entrySet()) {
            e.getDrops().add(new ItemStack(d.getKey(), d.getValue()));
        }
        e.setDroppedExp(rw.xp());

        Player killer = e.getEntity().getKiller();
        String name = killer != null ? killer.getName()
                : (a.starter != null && Bukkit.getOfflinePlayer(a.starter).getName() != null
                        ? Bukkit.getOfflinePlayer(a.starter).getName() : "alguien");
        for (String cmd : rw.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", name).replace("{player}", name));
        }

        for (Player p : e.getEntity().getWorld().getNearbyPlayers(e.getEntity().getLocation(), r.radius())) {
            p.sendMessage(plugin.msg("boss-defeated", "player", name, "boss", r.boss().name()));
        }

        cleanup(a);
        if (r.cooldown() > 0) {
            a.state = Altar.State.COOLDOWN;
            a.cooldownUntil = System.currentTimeMillis() + r.cooldown() * 1000L;
        } else {
            a.state = Altar.State.IDLE;
        }
        dirty = true;
    }

    // ------------------------------------------------------------------ utilidades

    private void finishTest(Altar a) {
        abort(a);
        tests.remove(a.key());
    }

    /** Cancela todo y deja el altar libre otra vez. Las ofrendas se devuelven si settings.refund-on-abort. */
    void abort(Altar a) {
        boolean refund = !a.test && plugin.getConfig().getBoolean("settings.refund-on-abort", true);
        if (a.boss != null && a.boss.isValid()) a.boss.remove();
        Map<Material, Integer> items = new HashMap<>(a.delivered);
        cleanup(a);
        a.state = Altar.State.IDLE;
        dirty = true;
        Location c = a.center();
        if (refund && c != null) {
            for (Map.Entry<Material, Integer> e : items.entrySet()) {
                int left = e.getValue();
                while (left > 0) {
                    int n = Math.min(left, e.getKey().getMaxStackSize());
                    c.getWorld().dropItemNaturally(c.clone().add(0, 1, 0), new ItemStack(e.getKey(), n));
                    left -= n;
                }
            }
        }
    }

    private void cleanup(Altar a) {
        if (a.task != null) { a.task.cancel(); a.task = null; }
        if (a.monitor != null) { a.monitor.cancel(); a.monitor = null; }
        if (a.bar != null) { a.bar.removeAll(); a.bar = null; }
        a.boss = null;
        a.starter = null;
        a.delivered.clear();
    }

    public static String pretty(Material m) {
        return pretty(m.name());
    }

    public static String pretty(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static String formatTime(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
