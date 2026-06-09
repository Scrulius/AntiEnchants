/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Typed, cached view of {@code config.yml}. Rebuilt on {@link #reload()} so
 * {@code /antienchants reload} takes effect without a restart.
 */
public final class AntiEnchantsConfig {

    private final AntiEnchantsPlugin plugin;

    // Banned enchantments
    private boolean bannedEnabled;
    private boolean purgeInventories;
    private boolean blockXpRepair;
    private boolean stripOnPickup;
    private boolean stripOnFish;
    private boolean stripFromLoot;
    private boolean stripMobDrops;
    private boolean banAllCurses;
    private Set<String> bannedKeys = Set.of();

    // Villager trades
    private boolean villagerEnabled;
    private boolean blockBookTrades;
    private boolean blockBannedEnchantTrades;

    // World exclusions
    private Set<String> disabledWorlds = Set.of();

    public AntiEnchantsConfig(@NotNull AntiEnchantsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Re-reads {@code config.yml} from disk into the cached fields. */
    public void reload() {
        plugin.reloadConfig();
        final FileConfiguration c = plugin.getConfig();

        bannedEnabled = c.getBoolean("banned-enchantments.enabled", true);
        purgeInventories = c.getBoolean("banned-enchantments.purge-player-inventories", true);
        blockXpRepair = c.getBoolean("banned-enchantments.block-xp-repair", true);
        stripOnPickup = c.getBoolean("banned-enchantments.strip-on-pickup", true);
        stripOnFish = c.getBoolean("banned-enchantments.strip-on-fish", true);
        stripFromLoot = c.getBoolean("banned-enchantments.strip-from-loot", true);
        stripMobDrops = c.getBoolean("banned-enchantments.strip-mob-drops", true);
        banAllCurses = c.getBoolean("banned-enchantments.ban-all-curses", false);

        final Set<String> keys = new HashSet<>();
        for (String raw : c.getStringList("banned-enchantments.keys")) {
            if (raw != null && !raw.isBlank()) {
                keys.add(normalizeKey(raw));
            }
        }
        bannedKeys = keys;

        villagerEnabled = c.getBoolean("villager-trades.enabled", true);
        blockBookTrades = c.getBoolean("villager-trades.block-book-trades", true);
        blockBannedEnchantTrades = c.getBoolean("villager-trades.block-banned-enchant-trades", true);

        final Set<String> worlds = new HashSet<>();
        for (String w : c.getStringList("disabled-worlds")) {
            if (w != null && !w.isBlank()) {
                worlds.add(w.toLowerCase(Locale.ROOT));
            }
        }
        disabledWorlds = worlds;
    }

    /** Normalizes an enchantment key to {@code namespace:key} (defaulting to {@code minecraft:}), lowercased. */
    private static @NotNull String normalizeKey(@NotNull String raw) {
        final String key = raw.trim().toLowerCase(Locale.ROOT);
        return key.contains(":") ? key : "minecraft:" + key;
    }

    /**
     * Whether an enchantment is banned — either listed explicitly or (when
     * {@code ban-all-curses} is on) any curse.
     */
    public boolean isBanned(@NotNull Enchantment ench) {
        if (banAllCurses && ench.isCursed()) {
            return true;
        }
        return !bannedKeys.isEmpty()
                && bannedKeys.contains(ench.getKey().toString().toLowerCase(Locale.ROOT));
    }

    public boolean isBannedEnabled() { return bannedEnabled; }
    public boolean isPurgeInventories() { return purgeInventories; }
    public boolean isBlockXpRepair() { return blockXpRepair; }
    public boolean isStripOnPickup() { return stripOnPickup; }
    public boolean isStripOnFish() { return stripOnFish; }
    public boolean isStripFromLoot() { return stripFromLoot; }
    public boolean isStripMobDrops() { return stripMobDrops; }

    public boolean isVillagerEnabled() { return villagerEnabled; }
    public boolean isBlockBookTrades() { return blockBookTrades; }
    public boolean isBlockBannedEnchantTrades() { return blockBannedEnchantTrades; }

    /** @return how many enchantment keys are explicitly banned (for the startup log) */
    public int bannedKeyCount() { return bannedKeys.size(); }

    /** @return whether AntiEnchants should be skipped in the given world */
    public boolean isWorldDisabled(@NotNull String worldName) {
        return disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }
}
