package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Cinemática de invocación: recorre las fases activadas (activación, carga, portal, aparición).
 * Se ejecuta cada 2 ticks; 10 fotogramas = 1 segundo.
 */
final class Ceremony extends BukkitRunnable {

    private static final int FRAMES_PER_SECOND = 10;

    private final SlandRitualsPlugin plugin;
    private final RitualManager manager;
    private final Altar altar;
    private final Ritual ritual;
    private final String starterName;
    private final List<Ritual.PhaseId> order = new ArrayList<>();
    private final ParticleSink sink;
    private final int preSpawnFrames;
    private final boolean skipWithoutViewers;

    private Collection<Player> viewers = List.of();
    private int idx;
    private int phaseTick;
    private int elapsedPre;
    private boolean started;
    private boolean spawned;

    Ceremony(SlandRitualsPlugin plugin, RitualManager manager, Altar altar, Ritual ritual, Player starter) {
        this.plugin = plugin;
        this.manager = manager;
        this.altar = altar;
        this.ritual = ritual;
        this.starterName = starter.getName();
        for (Ritual.PhaseId id : Ritual.PhaseId.values()) {
            if (ritual.phases().get(id).enabled()) order.add(id);
        }
        this.preSpawnFrames = Math.max(1, ritual.summonSeconds() * FRAMES_PER_SECOND);
        this.skipWithoutViewers = plugin.getConfig().getBoolean("performance.skip-when-no-players", true);
        Location c = altar.center();
        this.sink = new ParticleSink(c == null ? Bukkit.getWorlds().get(0) : c.getWorld(),
                ritual.visibility() == Ritual.Visibility.STARTER ? starter : null,
                plugin.getConfig().getInt("performance.max-particles-per-tick", 150));
    }

    @Override
    public void run() {
        Location c = altar.center();
        if (c == null) {
            cancel();
            manager.abort(altar);
            return;
        }
        if (altar.state != Altar.State.CHARGING && altar.state != Altar.State.ACTIVE) {
            cancel();
            return;
        }
        if (idx >= order.size()) {
            finish();
            return;
        }

        Ritual.PhaseId id = order.get(idx);
        Ritual.Phase ph = ritual.phases().get(id);

        if (id == Ritual.PhaseId.SPAWN && !spawned && !spawnNow()) return;

        int total = Math.max(1, ph.duration() * FRAMES_PER_SECOND);
        if (phaseTick % FRAMES_PER_SECOND == 0) refreshViewers(c);
        if (!started) {
            started = true;
            startPhase(c, id, ph);
        }
        double progress = Math.min(1.0, phaseTick / (double) total);

        if (!(skipWithoutViewers && viewers.isEmpty())) {
            sink.newTick();
            for (Ritual.AnimSpec spec : ph.anims()) Animator.play(spec, c, phaseTick, progress, sink);
        }

        if (phaseTick % FRAMES_PER_SECOND == 0) secondTick(c, id, ph, total, progress);

        if (!spawned && altar.bar != null) {
            elapsedPre++;
            altar.bar.setProgress(Math.min(1.0, elapsedPre / (double) preSpawnFrames));
        }

        phaseTick++;
        if (phaseTick >= total) {
            idx++;
            phaseTick = 0;
            started = false;
        }
    }

    // ------------------------------------------------------------------ fases

    private void startPhase(Location c, Ritual.PhaseId id, Ritual.Phase ph) {
        play(c, ph.sound(), 1.0f, 1.0f);
        boolean titleEachSecond = id == Ritual.PhaseId.CHARGE && ph.countdownTitle();
        for (Player p : viewers) {
            if (!titleEachSecond && (!ph.title().isEmpty() || !ph.subtitle().isEmpty())) {
                p.sendTitle(fmt(p, ph.title(), ""), fmt(p, ph.subtitle(), ""), 5, 40, 10);
            }
            if (!ph.message().isEmpty()) {
                p.sendMessage(plugin.prefix() + fmt(p, ph.message(), ""));
            }
        }
    }

    private void secondTick(Location c, Ritual.PhaseId id, Ritual.Phase ph, int total, double progress) {
        if (altar.bar != null && !spawned) manager.syncBar(altar.bar, viewers);
        if (id != Ritual.PhaseId.CHARGE) return;
        int left = (int) Math.ceil((total - phaseTick) / (double) FRAMES_PER_SECOND);
        if (!ph.tickSound().isEmpty()) play(c, ph.tickSound(), 1.0f, (float) (0.5 + 1.5 * progress));
        if (ph.countdownTitle()) {
            for (Player p : viewers) {
                p.sendTitle(fmt(p, ph.title(), String.valueOf(left)), fmt(p, ph.subtitle(), String.valueOf(left)), 0, 25, 5);
            }
        }
    }

    private boolean spawnNow() {
        spawned = true;
        boolean ok = manager.spawnBoss(altar, ritual);
        if (!ok) cancel();
        return ok;
    }

    private void finish() {
        if (!spawned && !spawnNow()) return;
        cancel();
        altar.task = null;
    }

    // ------------------------------------------------------------------ utilidades

    private void refreshViewers(Location c) {
        if (ritual.visibility() == Ritual.Visibility.STARTER) {
            Player p = altar.starter == null ? null : Bukkit.getPlayer(altar.starter);
            viewers = p == null ? List.of() : List.of(p);
        } else {
            viewers = c.getWorld().getNearbyPlayers(c, ritual.radius());
        }
    }

    private void play(Location c, String sound, float volume, float pitch) {
        if (sound == null || sound.isEmpty()) return;
        for (Player p : viewers) p.playSound(c, sound, volume, pitch);
    }

    private String fmt(Player viewer, String text, String time) {
        String s = text
                .replace("{ritual}", ritual.name())
                .replace("{boss}", ritual.boss().name())
                .replace("{player}", starterName)
                .replace("{time}", time);
        return plugin.hooks().parse(viewer, SlandRitualsPlugin.color(s));
    }
}
