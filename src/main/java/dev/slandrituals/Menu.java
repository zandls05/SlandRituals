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

import java.util.List;

/** Base de todos los menús del plugin; el holder permite reconocerlos en los eventos. */
public abstract class Menu implements InventoryHolder {

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

    protected static ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            if (glow) meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }
}
