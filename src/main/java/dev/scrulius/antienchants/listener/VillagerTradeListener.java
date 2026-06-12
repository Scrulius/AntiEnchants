/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants.listener;

import dev.scrulius.antienchants.AntiEnchantsConfig;
import dev.scrulius.antienchants.AntiEnchantsPlugin;
import dev.scrulius.antienchants.EnchantStripper;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Stops villagers from acquiring unwanted trades. Config-gated ({@code villager-trades});
 * cancelling {@link VillagerAcquireTradeEvent} prevents the recipe from ever being added.
 * <ul>
 *   <li>{@code block-book-trades} — any trade whose result is a book of any kind.</li>
 *   <li>{@code block-banned-enchant-trades} — any trade whose result carries a banned or
 *       over-cap enchantment (covers non-book results too).</li>
 * </ul>
 */
public final class VillagerTradeListener implements Listener {

    /** Every book-family material. */
    private static final Set<Material> BOOK_MATERIALS = Set.of(
            Material.ENCHANTED_BOOK,
            Material.BOOK,
            Material.WRITABLE_BOOK,
            Material.WRITTEN_BOOK,
            Material.KNOWLEDGE_BOOK
    );

    private final AntiEnchantsPlugin plugin;

    public VillagerTradeListener(@NotNull AntiEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAcquireTrade(@NotNull VillagerAcquireTradeEvent event) {
        final AntiEnchantsConfig config = plugin.config();
        if (!config.isVillagerEnabled()) {
            return;
        }
        final ItemStack result = event.getRecipe().getResult();
        if (result == null) {
            return;
        }
        final String world = event.getEntity().getWorld().getName();
        final boolean blocked = (config.isBlockBookTrades() && BOOK_MATERIALS.contains(result.getType()))
                || (config.isBlockBannedEnchantTrades() && EnchantStripper.violates(result, config, world));
        if (!blocked) {
            return;
        }
        if (config.isDryRun()) {
            plugin.getLogger().info("DRY-RUN | VILLAGER-TRADE @ " + world
                    + " | would block a trade with result " + result.getType());
            return;
        }
        event.setCancelled(true);
    }
}
