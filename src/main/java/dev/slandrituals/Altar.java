package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Un altar colocado en el mundo y su estado en ejecución. */
public final class Altar {

    public enum State { IDLE, CHARGING, ACTIVE, COOLDOWN }

    final String world;
    final int x, y, z;
    final String ritualId;
    /** Altar temporal creado por /rituales probar: no se guarda ni da recompensas. */
    boolean test;
    private final String testId = UUID.randomUUID().toString();

    final Map<Material, Integer> delivered = new EnumMap<>(Material.class);
    State state = State.IDLE;
    long cooldownUntil;
    UUID starter;
    LivingEntity boss;
    BossBar bar;
    /** Cinemática de invocación en curso. */
    BukkitTask task;
    /** Vigilancia del jefe (barra de vida, desaparición). */
    BukkitTask monitor;

    public Altar(String world, int x, int y, int z, String ritualId) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ritualId = ritualId;
    }

    public static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public String key() {
        return test ? "test;" + testId : key(world, x, y, z);
    }

    /** Centro del bloque, o null si el mundo no está cargado. */
    public Location center() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }
}
