package dev.slandrituals;

import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Emite partículas respetando un límite por tick y la visibilidad (todos los cercanos o solo el activador). */
final class ParticleSink {

    private final World world;
    private final Player only;
    private final int max;
    private int left;

    /** @param only si no es null, solo ese jugador ve las partículas. */
    ParticleSink(World world, Player only, int max) {
        this.world = world;
        this.only = only;
        this.max = Math.max(10, max);
        this.left = this.max;
    }

    void newTick() {
        left = max;
    }

    void emit(Particle p, double x, double y, double z, int count,
              double dx, double dy, double dz, double speed, Object data) {
        if (count <= 0 || left <= 0) return;
        int n = Math.min(count, left);
        left -= n;
        if (only != null) {
            if (only.isOnline() && only.getWorld().equals(world)) {
                only.spawnParticle(p, x, y, z, n, dx, dy, dz, speed, data);
            }
        } else {
            world.spawnParticle(p, x, y, z, n, dx, dy, dz, speed, data);
        }
    }
}
