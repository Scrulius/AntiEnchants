/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Pure helpers that remove banned enchantments (and apply level caps) on items/inventories. */
public final class EnchantStripper {

    /** Bypass predicate for contexts with no player (loot, mob drops, mob pickups). */
    public static final Predicate<Enchantment> NO_BYPASS = ench -> false;

    private EnchantStripper() {
    }

    /** What a strip pass did to an item/inventory: which enchantments were removed and how many were capped. */
    public record StripResult(@NotNull List<Removed> removed, int capped) {

        public static final StripResult NONE = new StripResult(List.of(), 0);

        /** A banned enchantment that was removed, with the level it had (for compensation). */
        public record Removed(@NotNull Enchantment enchantment, int level) {
        }

        public boolean changed() {
            return !removed.isEmpty() || capped > 0;
        }

        /** Combines two results (for aggregating inventory + cursor + container passes). */
        public @NotNull StripResult plus(@NotNull StripResult other) {
            if (!other.changed()) {
                return this;
            }
            if (!changed()) {
                return other;
            }
            final List<Removed> all = new ArrayList<>(removed);
            all.addAll(other.removed);
            return new StripResult(List.copyOf(all), capped + other.capped);
        }
    }

    /**
     * Removes every banned enchantment and reduces over-cap levels (held or book-stored) on an item.
     * Exempt item types and bypassed enchantments are left untouched. If a stripped enchanted book
     * ends up empty and {@code convert-empty-books} is on, it becomes a normal book.
     *
     * @param worldName world whose rules apply ({@code per-world} extras); {@code null} = global rules
     * @param bypass    per-enchantment bypass (player permissions); use {@link #NO_BYPASS} when there is no player
     */
    public static @NotNull StripResult strip(@Nullable ItemStack item, @NotNull AntiEnchantsConfig config,
                                             @Nullable String worldName, @NotNull Predicate<Enchantment> bypass) {
        return strip(item, config, worldName, bypass, true);
    }

    /**
     * Same as {@link #strip(ItemStack, AntiEnchantsConfig, String, Predicate)}, with a dry-run
     * switch: {@code apply = false} reports what would change without touching the item.
     */
    public static @NotNull StripResult strip(@Nullable ItemStack item, @NotNull AntiEnchantsConfig config,
                                             @Nullable String worldName, @NotNull Predicate<Enchantment> bypass,
                                             boolean apply) {
        if (item == null || item.isEmpty() || !item.hasItemMeta() || config.isExemptItem(item.getType())) {
            return StripResult.NONE;
        }
        final ItemMeta meta = item.getItemMeta();
        final List<StripResult.Removed> removed = new ArrayList<>(0);
        int capped = 0;

        for (Map.Entry<Enchantment, Integer> entry : Map.copyOf(meta.getEnchants()).entrySet()) {
            final Enchantment ench = entry.getKey();
            if (bypass.test(ench)) {
                continue;
            }
            if (config.isBanned(ench, worldName)) {
                meta.removeEnchant(ench);
                removed.add(new StripResult.Removed(ench, entry.getValue()));
            } else {
                final int cap = config.levelCap(ench, worldName);
                if (cap > 0 && entry.getValue() > cap) {
                    meta.addEnchant(ench, cap, true);
                    capped++;
                }
            }
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Map.Entry<Enchantment, Integer> entry : Map.copyOf(storage.getStoredEnchants()).entrySet()) {
                final Enchantment ench = entry.getKey();
                if (bypass.test(ench)) {
                    continue;
                }
                if (config.isBanned(ench, worldName)) {
                    storage.removeStoredEnchant(ench);
                    removed.add(new StripResult.Removed(ench, entry.getValue()));
                } else {
                    final int cap = config.levelCap(ench, worldName);
                    if (cap > 0 && entry.getValue() > cap) {
                        storage.addStoredEnchant(ench, cap, true);
                        capped++;
                    }
                }
            }
        }

        if (removed.isEmpty() && capped == 0) {
            return StripResult.NONE;
        }
        if (apply) {
            item.setItemMeta(meta);
            if (!removed.isEmpty() && config.isConvertEmptyBooks()
                    && item.getType() == Material.ENCHANTED_BOOK
                    && item.getItemMeta() instanceof EnchantmentStorageMeta leftover
                    && !leftover.hasStoredEnchants()) {
                // setType (deprecated) over withType: callers rely on in-place mutation of inventory mirrors.
                item.setType(Material.BOOK);
            }
        }
        return new StripResult(List.copyOf(removed), capped);
    }

    /**
     * Whether an item violates the rules (carries a banned or over-cap enchantment, held or
     * book-stored), without modifying it. Exempt item types never violate.
     */
    public static boolean violates(@Nullable ItemStack item, @NotNull AntiEnchantsConfig config,
                                   @Nullable String worldName) {
        if (item == null || item.isEmpty() || !item.hasItemMeta() || config.isExemptItem(item.getType())) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        if (anyViolation(meta.getEnchants(), config, worldName)) {
            return true;
        }
        return meta instanceof EnchantmentStorageMeta storage
                && anyViolation(storage.getStoredEnchants(), config, worldName);
    }

    private static boolean anyViolation(@NotNull Map<Enchantment, Integer> enchants,
                                        @NotNull AntiEnchantsConfig config, @Nullable String worldName) {
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (config.isBanned(entry.getKey(), worldName)) {
                return true;
            }
            final int cap = config.levelCap(entry.getKey(), worldName);
            if (cap > 0 && entry.getValue() > cap) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips banned/over-cap enchantments from every slot of an inventory in place.
     */
    public static @NotNull StripResult purge(@Nullable Inventory inv, @NotNull AntiEnchantsConfig config,
                                             @Nullable String worldName, @NotNull Predicate<Enchantment> bypass) {
        return purge(inv, config, worldName, bypass, true);
    }

    /**
     * Same as {@link #purge(Inventory, AntiEnchantsConfig, String, Predicate)}, with a dry-run
     * switch: {@code apply = false} reports what would change without touching any item.
     */
    public static @NotNull StripResult purge(@Nullable Inventory inv, @NotNull AntiEnchantsConfig config,
                                             @Nullable String worldName, @NotNull Predicate<Enchantment> bypass,
                                             boolean apply) {
        if (inv == null) {
            return StripResult.NONE;
        }
        StripResult total = StripResult.NONE;
        for (ItemStack item : inv.getContents()) {
            total = total.plus(strip(item, config, worldName, bypass, apply));
        }
        return total;
    }
}
