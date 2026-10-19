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
        Location
