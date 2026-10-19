package dev.slandrituals;

import org.bukkit.Material;

/** Animaciones de partículas disponibles. La clave es la que se usa en rituals.yml y animaciones.yml. */
public enum AnimType {
    ARCANE_SPIRAL("arcane_spiral", "Espiral arcana", Material.ENCHANTED_BOOK,
            "Partículas que giran alrededor del altar y ascienden."),
    MAGIC_CIRCLE("magic_circle", "Círculo mágico", Material.ENDER_EYE,
            "Un anillo que se forma poco a poco alrededor del altar."),
    ENERGY_COLUMNS("energy_columns", "Columnas de energía", Material.BEACON,
            "Haces verticales que conectan el suelo con el centro."),
    ENERGY_PULSE("energy_pulse", "Pulso de energía", Material.AMETHYST_SHARD,
            "Ondas circulares que se expanden desde el altar."),
    MAGIC_RUNES("magic_runes", "Runas mágicas", Material.LAPIS_LAZULI,
            "Símbolos que aparecen uno tras otro."),
    SUMMONING_VORTEX("summoning_vortex", "Vórtice de invocación", Material.ENDER_PEARL,
            "Partículas que convergen al centro y luego se dispersan."),
    ARCANE_OVERLOAD("arcane_overload", "Sobrecarga arcana", Material.REDSTONE_BLOCK,
            "Intensidad, velocidad y cantidad crecientes."),
    FINAL_EXPLOSION("final_explosion", "Explosión final", Material.NETHER_STAR,
            "Destello y onda de choque al aparecer el jefe.");

    public final String key;
    public final String label;
    public final Material icon;
    public final String description;

    AnimType(String key, String label, Material icon, String description) {
        this.key = key;
        this.label = label;
        this.icon = icon;
        this.description = description;
    }

    public static AnimType fromKey(String key) {
        for (AnimType t : values()) {
            if (t.key.equalsIgnoreCase(key)) return t;
        }
        return null;
    }
}
