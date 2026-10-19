package dev.slandrituals;

import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.concurrent.ThreadLocalRandom;

/** Dibuja las animaciones de partículas. Cada llamada dibuja un "fotograma" (cada 2 ticks de juego). */
final class Animator {

    private static final Particle BIG_EXPLOSION = find("EXPLOSION_EMITTER", "EXPLOSION");

    /** Glifos de las runas: polilíneas en coordenadas -1..1 (x, z). */
    private static final double[][][] GLYPHS = {
            {{0, 1}, {0.87, -0.5}, {-0.87, -0.5}, {0, 1}},                    // triángulo
            {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}},                   // cuadrado
            {{-1, 0}, {1, 0}, {0, 0}, {0, -1}, {0, 1}},                       // cruz
            {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {0, 1}},                       // rombo
            {{-1, -1}, {1, 1}, {0, 0}, {-1, 1}, {1, -1}},                     // aspa
            {{-1, -0.5}, {0, 0.8}, {1, -0.5}},                                // chevron
    };

    private Animator() { }

    private static Particle find(String first, String second) {
        try {
            return Particle.valueOf(first);
        } catch (IllegalArgumentException e) {
            try {
                return Particle.valueOf(second);
            } catch (IllegalArgumentException e2) {
                return Particle.END_ROD;
            }
        }
    }

    /**
     * @param tick     número de fotograma dentro de la fase
     * @param progress avance de la fase, de 0 a 1
     */
    static void play(Ritual.AnimSpec s, Location c, int tick, double progress, ParticleSink sink) {
        if (s.type() != AnimType.FINAL_EXPLOSION && tick % s.frequency() != 0) return;
        double cx = c.getX();
        double cz = c.getZ();
        double base = c.getY() + 0.55; // justo por encima del altar

        switch (s.type()) {
            case ARCANE_SPIRAL -> spiral(s, cx, base, cz, tick, sink);
            case MAGIC_CIRCLE -> circle(s, cx, base, cz, tick, progress, sink);
            case ENERGY_COLUMNS -> columns(s, cx, base, cz, tick, progress, sink);
            case ENERGY_PULSE -> pulse(s, cx, base, cz, tick, sink);
            case MAGIC_RUNES -> runes(s, cx, base, cz, tick, progress, sink);
            case SUMMONING_VORTEX -> vortex(s, cx, base, cz, tick, progress, sink);
            case ARCANE_OVERLOAD -> overload(s, cx, base, cz, progress, sink);
            case FINAL_EXPLOSION -> explosion(s, cx, base, cz, tick, sink);
        }
    }

    private static void ring(Ritual.AnimSpec s, double cx, double y, double cz, double r, int points,
                             double spin, int upTo, ParticleSink sink) {
        for (int j = 0; j < upTo && j < points; j++) {
            double ang = spin + j * (Math.PI * 2 / points);
            sink.emit(s.particle(), cx + Math.cos(ang) * r, y, cz + Math.sin(ang) * r,
                    1, 0, 0, 0, 0, s.data());
        }
    }

    private static void spiral(Ritual.AnimSpec s, double cx, double base, double cz, int tick, ParticleSink sink) {
        double rot = tick * 0.45 * s.speed();
        double rise = ((tick * s.speed()) % 24) / 24.0;
        for (int i = 0; i < s.count(); i++) {
            double ang = rot + i * (Math.PI * 2 / s.count());
            for (int k = 0; k < 3; k++) { // pequeña estela detrás de cada punto
                double a2 = ang - k * 0.18;
                double y = base + Math.max(0, rise - k * 0.04) * s.height();
                sink.emit(s.particle(), cx + Math.cos(a2) * s.radius(), y, cz + Math.sin(a2) * s.radius(),
                        1, 0, 0, 0, 0, s.data());
            }
        }
    }

    private static void circle(Ritual.AnimSpec s, double cx, double base, double cz, int tick,
                               double progress, ParticleSink sink) {
        int points = 16 + s.count() * 8;
        double frac = Math.min(1.0, 0.15 + progress * 1.6); // el círculo se va formando
        int n = (int) Math.round(points * frac);
        double spin = tick * 0.03 * s.speed();
        ring(s, cx, base, cz, s.radius(), points, spin, n, sink);
        ring(s, cx, base, cz, s.radius() * 0.6, points / 2, -spin, n / 2, sink);
    }

    private static void columns(Ritual.AnimSpec s, double cx, double base, double cz, int tick,
                                double progress, ParticleSink sink) {
        int per = 6 + s.count() * 2;
        double floor = base - 0.5;
        for (int k = 0; k < 4; k++) {
            double ang = k * Math.PI / 2 + Math.PI / 4 + tick * 0.01 * s.speed();
            double x0 = cx + Math.cos(ang) * s.radius();
            double z0 = cz + Math.sin(ang) * s.radius();
            for (int j = 0; j < per; j++) {
                double y = floor + ((double) j / per) * s.height();
                sink.emit(s.particle(), x0, y, z0, 1, 0.02, 0, 0.02, 0, s.data());
            }
            double pf = ((tick * s.speed()) % 12) / 12.0; // destello que sube por la columna
            sink.emit(s.particle(), x0, floor + pf * s.height(), z0, 2, 0.05, 0.05, 0.05, 0, s.data());

            if (progress > 0.5) { // haz desde lo alto de la columna hacia el centro
                double topY = floor + s.height();
                double midY = base + s.height() * 0.5;
                for (int j = 1; j <= 5; j++) {
                    double f = j / 6.0;
                    sink.emit(s.particle(), x0 + (cx - x0) * f, topY + (midY - topY) * f, z0 + (cz - z0) * f,
                            1, 0, 0, 0, 0, s.data());
                }
            }
        }
    }

    private static void pulse(Ritual.AnimSpec s, double cx, double base, double cz, int tick, ParticleSink sink) {
        double f = ((tick * s.speed()) % 14) / 14.0;
        double r = Math.max(0.3, f * s.radius());
        int points = 20 + (int) (r * 8);
        ring(s, cx, base - 0.3, cz, r, points, 0, points, sink);
    }

    private static void runes(Ritual.AnimSpec s, double cx, double base, double cz, int tick,
                              double progress, ParticleSink sink) {
        int total = 6;
        int visible = (int) Math.ceil(progress * total);
        double size = 0.35;
        for (int k = 0; k < total && k < visible; k++) {
            double ang = k * (Math.PI * 2 / total) + tick * 0.01 * s.speed();
            double rx = cx + Math.cos(ang) * s.radius();
            double rz = cz + Math.sin(ang) * s.radius();
            double[][] path = GLYPHS[k % GLYPHS.length];
            for (int i = 0; i + 1 < path.length; i++) {
                for (int step = 0; step < 4; step++) {
                    double f = step / 4.0;
                    double px = path[i][0] + (path[i + 1][0] - path[i][0]) * f;
                    double pz = path[i][1] + (path[i + 1][1] - path[i][1]) * f;
                    sink.emit(s.particle(), rx + px * size, base - 0.2, rz + pz * size, 1, 0, 0, 0, 0, s.data());
                }
            }
        }
    }

    private static void vortex(Ritual.AnimSpec s, double cx, double base, double cz, int tick,
                               double progress, ParticleSink sink) {
        int n = s.count() * 5;
        double r = s.radius() * (1.0 - 0.85 * progress);
        for (int i = 0; i < n; i++) {
            double ang = tick * 0.55 * s.speed() + i * (Math.PI * 2 / n);
            double rr = r * (0.55 + 0.45 * ((i % 4) / 3.0));
            double y = base + 0.2 + (1 - progress) * s.height() * ((i % 5) / 4.0);
            sink.emit(s.particle(), cx + Math.cos(ang) * rr, y, cz + Math.sin(ang) * rr, 1, 0, 0, 0, 0, s.data());
        }
        if (progress >= 0.8) { // al final las partículas se dispersan desde el centro
            sink.emit(s.particle(), cx, base + 0.8, cz, s.count() * 4, 0.2, 0.2, 0.2, 0.35 * s.speed(), s.data());
        }
    }

    private static void overload(Ritual.AnimSpec s, double cx, double base, double cz,
                                 double progress, ParticleSink sink) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double intensity = 0.25 + 1.75 * progress;
        int n = (int) Math.round(s.count() * 3 * intensity);
        double r = s.radius() * (1.3 - 0.6 * progress);
        for (int i = 0; i < n; i++) {
            double u = rnd.nextDouble() * 2 - 1;
            double th = rnd.nextDouble() * Math.PI * 2;
            double sq = Math.sqrt(1 - u * u);
            sink.emit(s.particle(), cx + sq * Math.cos(th) * r, base + 0.8 + u * r * 0.7, cz + sq * Math.sin(th) * r,
                    1, 0, 0, 0, 0, s.data());
        }
    }

    private static void explosion(Ritual.AnimSpec s, double cx, double base, double cz, int tick, ParticleSink sink) {
        if (tick == 0) {
            sink.emit(BIG_EXPLOSION, cx, base + 0.8, cz, 1, 0, 0, 0, 0, null);
            sink.emit(s.particle(), cx, base + 0.8, cz, s.count() * 30, 0.1, 0.1, 0.1, 0.7 * s.speed(), s.data());
        }
        if (tick < 8) { // onda de choque que se expande rápido
            double r = s.radius() * (tick + 1) / 8.0;
            int points = 24 + (int) (r * 10);
            ring(s, cx, base - 0.3, cz, r, points, 0, points, sink);
        }
    }
}
