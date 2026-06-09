/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/** Pure helpers that remove banned enchantments from items/inventories. */
public final class EnchantStripper {

    private EnchantStripper() {
    }

    /**
     * Removes every banned enchantment (held or book-stored) from an item.
     *
     * @return {@code true} if the item was modified
     */
    public static boolean strip(@Nullable ItemStack item, @NotNull AntiEnchantsConfig config) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        boolean stripped = false;

        for (Enchantment ench : new ArrayList<>(meta.getEnchants().keySet())) {
            if (config.isBanned(ench)) {
                meta.removeEnchant(ench);
                stripped = true;
            }
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Enchantment ench : new ArrayList<>(storage.getStoredEnchants().keySet())) {
                if (config.isBanned(ench)) {
                    storage.removeStoredEnchant(ench);
                    stripped = true;
                }
            }
        }
        if (stripped) {
            item.setItemMeta(meta);
        }
        return stripped;
    }

    /**
     * Whether an item carries any banned enchantment (held or book-stored), without modifying it.
     */
    public static boolean hasBanned(@Nullable ItemStack item, @NotNull AntiEnchantsConfig config) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        for (Enchantment ench : meta.getEnchants().keySet()) {
            if (config.isBanned(ench)) {
                return true;
            }
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Enchantment ench : storage.getStoredEnchants().keySet()) {
                if (config.isBanned(ench)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Strips banned enchantments from every slot of an inventory in place.
     *
     * @return {@code true} if anything changed
     */
    public static boolean purge(@Nullable Inventory inv, @NotNull AntiEnchantsConfig config) {
        if (inv == null) {
            return false;
        }
        final ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (ItemStack item : contents) {
            changed |= strip(item, config);
        }
        return changed;
    }
}
