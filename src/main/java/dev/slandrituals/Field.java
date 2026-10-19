package dev.slandrituals;

import org.bukkit.Material;

import java.util.List;

/** Un valor editable de un ritual desde los menús (ruta en rituals.yml, tipo y límites). */
public record Field(String path, String label, Material icon, Kind kind, Object def,
                    double min, double max, List<String> choices, String hint) {

    public enum Kind { TEXT, LIST, INT, DOUBLE, BOOL, CHOICE, MATERIAL, PARTICLE, SOUND, COLOR, ENTITY }

    public static Field text(String path, String label, Material icon, Object def, String hint) {
        return new Field(path, label, icon, Kind.TEXT, def, 0, 0, List.of(), hint);
    }

    public static Field list(String path, String label, Material icon, String hint) {
        return new Field(path, label, icon, Kind.LIST, null, 0, 0, List.of(), hint);
    }

    public static Field integer(String path, String label, Material icon, int def, int min, int max, String hint) {
        return new Field(path, label, icon, Kind.INT, def, min, max, List.of(), hint);
    }

    public static Field decimal(String path, String label, Material icon, double def, double min, double max, String hint) {
        return new Field(path, label, icon, Kind.DOUBLE, def, min, max, List.of(), hint);
    }

    public static Field bool(String path, String label, Material icon, boolean def, String hint) {
        return new Field(path, label, icon, Kind.BOOL, def, 0, 0, List.of(), hint);
    }

    public static Field choice(String path, String label, Material icon, String def, List<String> choices, String hint) {
        return new Field(path, label, icon, Kind.CHOICE, def, 0, 0, choices, hint);
    }

    public static Field of(Kind kind, String path, String label, Material icon, Object def, String hint) {
        return new Field(path, label, icon, kind, def, 0, 0, List.of(), hint);
    }
}
