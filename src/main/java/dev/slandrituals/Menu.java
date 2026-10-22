package dev.slandrituals;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Base de todos los menús del plugin; el holder permite reconocerlos en los eventos. */
public abstract class Menu implements InventoryHolder {

    /** Ancho (caracteres visibles) al que se parten los textos largos de los lores. */
    protected static final int WRAP = 42;

    protected final SlandRitualsPlugin plugin;
    protected Inventory inv;

    protected Menu(SlandRitualsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    public abstract void onClick(InventoryClickEvent e);

    /** Cerrar un inventario dentro de su propio evento da problemas; se hace en el siguiente tick. */
    protected void closeLater(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
    }

    /** Abre otro menú en el siguiente tick (abrir un inventario dentro de su propio evento da problemas). */
    protected void openLater(Menu next, Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> next.open(p));
    }

    /** Botón estándar para volver al menú anterior. */
    protected ItemStack backButton() {
        return item(Material.ARROW, plugin.raw("menu-back"), wrap(plugin.raw("menu-back-lore")), false);
    }

    /**
     * Parte un texto largo en varias líneas de lore sin cortar palabras.
     * Las líneas nuevas conservan el último color usado (los códigos ya vienen traducidos a §).
     */
    protected static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        int visible = 0;
        String color = "";
        for (String word : text.split(" ")) {
            int len = 0;
            String wordColor = color;
            for (int i = 0; i < word.length(); i++) {
                if (word.charAt(i) == '§' && i + 1 < word.length()) {
                    wordColor = "§" + word.charAt(i + 1);
                    i++;
                } else {
                    len++;
                }
            }
            if (visible > 0 && visible + 1 + len > WRAP) {
                out.add(line.toString());
                line = new StringBuilder(color);
                visible = 0;
            }
            if (visible > 0) {
                line.append(' ');
                visible++;
            }
            line.append(word);
            visible += len;
            color = wordColor;
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** Recorta un texto largo para que quepa en una línea de lore. */
    protected static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    protected static ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name == null || name.isEmpty() ? " " : name);
            List<String> clean = new ArrayList<>(); // se descartan las líneas vacías (claves que faltan)
            if (lore != null) {
                for (String line : lore) {
                    if (line != null && !line.isEmpty()) clean.add(line);
                }
            }
            meta.setLore(clean);
            meta.addItemFlags(ItemFlag.values());
            if (glow) meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }
}
