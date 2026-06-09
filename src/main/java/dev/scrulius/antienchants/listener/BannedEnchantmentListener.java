/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants.listener;

import dev.scrulius.antienchants.AntiEnchantsConfig;
import dev.scrulius.antienchants.AntiEnchantsPlugin;
import dev.scrulius.antienchants.EnchantStripper;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Globally purges banned enchantments (default: <b>mending</b>) so they can never
 * exist on the server. Every action is config-gated ({@code banned-enchantments}).
 * <p>
 * Covers every realistic vector: existing inventories (join / open / click /
 * creative), item pickups, fishing, generated loot and mob drops, plus the very
 * effect of mending via {@link PlayerItemMendEvent}.
 * <p>
 * The click / creative strips are deferred 1 tick — mutating an item mid-click
 * breaks Bukkit's internal item tracking and can dupe (notably in creative).
 */
public final class BannedEnchantmentListener implements Listener {

    private final AntiEnchantsPlugin plugin;

    public BannedEnchantmentListener(@NotNull AntiEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    private AntiEnchantsConfig config() {
        return plugin.config();
    }

    private boolean active() {
        return config().isBannedEnabled();
    }

    private boolean worldDisabled(@NotNull Player player) {
        return config().isWorldDisabled(player.getWorld().getName());
    }

    /** Purges a player's inventory and cursor, refreshing the client if anything changed. */
    private void purgePlayer(@NotNull Player player) {
        final AntiEnchantsConfig config = config();
        final PlayerInventory inv = player.getInventory();
        boolean changed = EnchantStripper.purge(inv, config);

        final ItemStack cursor = player.getItemOnCursor();
        if (EnchantStripper.strip(cursor, config)) {
            player.setItemOnCursor(cursor);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        if (active() && config().isPurgeInventories() && !worldDisabled(event.getPlayer())) {
            purgePlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        if (!active() || !config().isPurgeInventories()) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            if (worldDisabled(player)) {
                return;
            }
            purgePlayer(player);
        }
        EnchantStripper.purge(event.getInventory(), config());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!active() || !config().isPurgeInventories()
                || !(event.getWhoClicked() instanceof Player player) || worldDisabled(player)) {
            return;
        }
        final Inventory top = event.getInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            purgePlayer(player);
            EnchantStripper.purge(top, config());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(@NotNull InventoryCreativeEvent event) {
        if (!active() || !config().isPurgeInventories()
                || !(event.getWhoClicked() instanceof Player player) || worldDisabled(player)) {
            return;
        }
        final Inventory top = event.getInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            purgePlayer(player);
            EnchantStripper.purge(top, config());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (!active() || !config().isStripOnPickup()) {
            return;
        }
        final ItemStack item = event.getItem().getItemStack();
        if (EnchantStripper.strip(item, config())) {
            event.getItem().setItemStack(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(@NotNull PlayerFishEvent event) {
        if (active() && config().isStripOnFish() && event.getCaught() instanceof Item itemEntity) {
            final ItemStack item = itemEntity.getItemStack();
            if (EnchantStripper.strip(item, config())) {
                itemEntity.setItemStack(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootGenerate(@NotNull LootGenerateEvent event) {
        if (!active() || !config().isStripFromLoot()) {
            return;
        }
        final var world = event.getLootContext().getLocation().getWorld();
        if (world != null && config().isWorldDisabled(world.getName())) {
            return;
        }
        final List<ItemStack> loot = new ArrayList<>(event.getLoot());
        boolean changed = false;
        for (ItemStack item : loot) {
            changed |= EnchantStripper.strip(item, config());
        }
        if (changed) {
            event.setLoot(loot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        if (!active() || !config().isStripMobDrops()
                || config().isWorldDisabled(event.getEntity().getWorld().getName())) {
            return;
        }
        for (ItemStack item : event.getDrops()) {
            EnchantStripper.strip(item, config());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMend(@NotNull PlayerItemMendEvent event) {
        // Block the very effect of mending: XP never repairs durability.
        if (active() && config().isBlockXpRepair() && !worldDisabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
