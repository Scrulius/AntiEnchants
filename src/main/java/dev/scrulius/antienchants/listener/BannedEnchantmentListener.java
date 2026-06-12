/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants.listener;

import dev.scrulius.antienchants.AntiEnchantsConfig;
import dev.scrulius.antienchants.AntiEnchantsPlugin;
import dev.scrulius.antienchants.EnchantStripper;
import dev.scrulius.antienchants.EnchantStripper.StripResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Globally purges banned enchantments (default: <b>mending</b>) and reduces over-cap levels so
 * they can never exist on the server. Every action is config-gated ({@code banned-enchantments},
 * {@code level-caps}); rules can differ per world ({@code per-world}).
 * <p>
 * Covers every realistic vector: existing inventories (join / open / click / creative), item
 * pickups, fishing, generated loot, mob drops, the enchanting table result, the anvil and
 * grindstone result previews, plus the very effect of mending via {@link PlayerItemMendEvent}.
 * <p>
 * Player-context strips honour {@code antienchants.bypass.<key>} permissions, notify the player
 * ({@code messages}), are recorded in the audit log ({@code audit-log}) and may grant compensation
 * ({@code compensation}) — never in creative mode (spawning banned books and farming the payout
 * would be free money) and never for loot/mob-drop strips (no owner; would be farmable).
 * <p>
 * The click / creative strips are deferred 1 tick — mutating an item mid-click breaks Bukkit's
 * internal item tracking and can dupe (notably in creative).
 * <p>
 * In dry-run mode ({@code dry-run: true}) every destructive path detects and logs what it
 * <i>would</i> do (console + audit log) without modifying anything; the non-destructive
 * interventions (result previews, mend cancel, event cancels) simply stand down.
 */
public final class BannedEnchantmentListener implements Listener {

    private final AntiEnchantsPlugin plugin;

    /** Last dry-run findings per player, so unchanged inventories aren't re-logged on every event. */
    private final Map<UUID, String> dryRunSeen = new HashMap<>();

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

    /**
     * Purges the player's inventory + cursor (and optionally a container), refreshing the client
     * and applying feedback/compensation/audit if anything changed. In dry-run mode nothing is
     * modified; the findings are logged instead.
     *
     * @return what was (or, in dry-run, would have been) stripped/capped
     */
    private @NotNull StripResult purgeFor(@NotNull Player player, @Nullable Inventory extra,
                                          @NotNull String context) {
        final AntiEnchantsConfig config = config();
        final String world = player.getWorld().getName();
        final Predicate<Enchantment> bypass = config.bypass(player);
        final boolean apply = !config.isDryRun();

        StripResult total = EnchantStripper.purge(player.getInventory(), config, world, bypass, apply);
        final ItemStack cursor = player.getItemOnCursor();
        final StripResult cursorResult = EnchantStripper.strip(cursor, config, world, bypass, apply);
        if (apply && cursorResult.changed()) {
            player.setItemOnCursor(cursor);
        }
        total = total.plus(cursorResult);
        if (extra != null) {
            total = total.plus(EnchantStripper.purge(extra, config, world, bypass, apply));
        }
        if (total.changed()) {
            if (apply) {
                player.updateInventory();
                afterStrip(player, total, context);
            } else {
                reportDryRun(player, total, context);
            }
        }
        return total;
    }

    /**
     * On-demand purge for {@code /antienchants purge} — same pipeline (bypass, feedback,
     * compensation, audit, dry-run) as the automatic strips.
     */
    public @NotNull StripResult purgePlayer(@NotNull Player player) {
        return purgeFor(player, null, "COMMAND");
    }

    /** Logs a dry-run finding (console + audit log), skipping repeats of the same findings. */
    private void reportDryRun(@NotNull Player player, @NotNull StripResult result, @NotNull String context) {
        final String fingerprint = fingerprint(result);
        if (fingerprint.equals(dryRunSeen.put(player.getUniqueId(), fingerprint))) {
            return;
        }
        plugin.getLogger().info(describeWouldDo("DRY-RUN | " + context + " | " + player.getName()
                + " @ " + player.getWorld().getName(), result));
        plugin.auditLog().log("DRY-RUN/" + context, player, result);
    }

    /** Order-independent summary of a result, to dedupe dry-run logs for unchanged inventories. */
    private static @NotNull String fingerprint(@NotNull StripResult result) {
        final List<String> parts = new ArrayList<>(result.removed().size());
        for (StripResult.Removed removed : result.removed()) {
            parts.add(removed.enchantment().getKey() + ":" + removed.level());
        }
        Collections.sort(parts);
        return String.join(",", parts) + "|capped=" + result.capped();
    }

    /** Human-readable "would remove/cap" line for dry-run console output. */
    private static @NotNull String describeWouldDo(@NotNull String prefix, @NotNull StripResult result) {
        final StringBuilder sb = new StringBuilder(prefix);
        if (!result.removed().isEmpty()) {
            sb.append(" | would remove:");
            for (StripResult.Removed removed : result.removed()) {
                sb.append(' ').append(removed.enchantment().getKey())
                        .append('(').append(removed.level()).append(')');
            }
        }
        if (result.capped() > 0) {
            sb.append(" | would cap: ").append(result.capped());
        }
        return sb.toString();
    }

    /** Player feedback + audit + compensation after a player-context strip. */
    private void afterStrip(@NotNull Player player, @NotNull StripResult result, @NotNull String context) {
        final AntiEnchantsConfig config = config();
        if (!result.removed().isEmpty()) {
            send(player, config.getStrippedMessage(), result.removed().size());
        }
        if (result.capped() > 0) {
            send(player, config.getCappedMessage(), result.capped());
        }
        plugin.auditLog().log(context, player, result);
        compensate(player, result.removed());
    }

    private void compensate(@NotNull Player player, @NotNull List<StripResult.Removed> removed) {
        final AntiEnchantsConfig config = config();
        if (!config.isCompensationEnabled() || removed.isEmpty() || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        boolean gave = false;
        for (StripResult.Removed entry : removed) {
            for (ItemStack template : config.compensationFor(entry.enchantment())) {
                final var leftover = player.getInventory().addItem(template.clone());
                leftover.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
                gave = true;
            }
        }
        if (gave) {
            send(player, config.getCompensatedMessage(), removed.size());
        }
    }

    private void send(@NotNull Player player, @NotNull String message, int count) {
        if (!message.isBlank()) {
            player.sendMessage(MiniMessage.miniMessage()
                    .deserialize(message.replace("{count}", String.valueOf(count))));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        if (active() && config().isPurgeInventories() && !worldDisabled(event.getPlayer())) {
            purgeFor(event.getPlayer(), null, "JOIN");
        }
        // Remind joining admins that dry-run is on, so it's never left enabled by accident.
        if (config().isDryRun() && event.getPlayer().hasPermission("antienchants.admin")) {
            event.getPlayer().sendMessage(Component.text(
                    "AntiEnchants is in DRY-RUN mode — violations are only logged, nothing is enforced.",
                    NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        if (!active() || !config().isPurgeInventories()
                || !(event.getPlayer() instanceof Player player) || worldDisabled(player)) {
            return;
        }
        purgeFor(player, event.getInventory(), "OPEN");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!active() || !config().isPurgeInventories()
                || !(event.getWhoClicked() instanceof Player player) || worldDisabled(player)) {
            return;
        }
        final Inventory top = event.getInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> purgeFor(player, top, "CLICK"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(@NotNull InventoryCreativeEvent event) {
        if (!active() || !config().isPurgeInventories()
                || !(event.getWhoClicked() instanceof Player player) || worldDisabled(player)) {
            return;
        }
        final Inventory top = event.getInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> purgeFor(player, top, "CLICK"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (!active() || !config().isStripOnPickup()) {
            return;
        }
        final AntiEnchantsConfig config = config();
        final Player player = event.getEntity() instanceof Player p ? p : null;
        if (player != null && worldDisabled(player)) {
            return;
        }
        final String world = event.getEntity().getWorld().getName();
        final boolean apply = !config.isDryRun();
        final ItemStack item = event.getItem().getItemStack();
        final StripResult result = EnchantStripper.strip(item, config, world, config.bypass(player), apply);
        if (!result.changed()) {
            return;
        }
        if (apply) {
            event.getItem().setItemStack(item);
            if (player != null) {
                afterStrip(player, result, "PICKUP");
            }
        } else if (player != null) {
            reportDryRun(player, result, "PICKUP");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(@NotNull PlayerFishEvent event) {
        if (!active() || !config().isStripOnFish() || worldDisabled(event.getPlayer())
                || !(event.getCaught() instanceof Item itemEntity)) {
            return;
        }
        final AntiEnchantsConfig config = config();
        final boolean apply = !config.isDryRun();
        final ItemStack item = itemEntity.getItemStack();
        final StripResult result = EnchantStripper.strip(item, config,
                event.getPlayer().getWorld().getName(), config.bypass(event.getPlayer()), apply);
        if (!result.changed()) {
            return;
        }
        if (apply) {
            itemEntity.setItemStack(item);
            afterStrip(event.getPlayer(), result, "FISHING");
        } else {
            reportDryRun(event.getPlayer(), result, "FISHING");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootGenerate(@NotNull LootGenerateEvent event) {
        if (!active() || !config().isStripFromLoot()) {
            return;
        }
        final var world = event.getLootContext().getLocation().getWorld();
        final String worldName = world != null ? world.getName() : null;
        if (worldName != null && config().isWorldDisabled(worldName)) {
            return;
        }
        final boolean apply = !config().isDryRun();
        final List<ItemStack> loot = new ArrayList<>(event.getLoot());
        StripResult total = StripResult.NONE;
        for (ItemStack item : loot) {
            total = total.plus(EnchantStripper.strip(item, config(), worldName, EnchantStripper.NO_BYPASS, apply));
        }
        if (!total.changed()) {
            return;
        }
        if (apply) {
            event.setLoot(loot);
        } else {
            plugin.getLogger().info(describeWouldDo(
                    "DRY-RUN | LOOT @ " + (worldName != null ? worldName : "?"), total));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        final String world = event.getEntity().getWorld().getName();
        if (!active() || !config().isStripMobDrops() || config().isWorldDisabled(world)) {
            return;
        }
        final boolean apply = !config().isDryRun();
        StripResult total = StripResult.NONE;
        for (ItemStack item : event.getDrops()) {
            total = total.plus(EnchantStripper.strip(item, config(), world, EnchantStripper.NO_BYPASS, apply));
        }
        if (!apply && total.changed()) {
            plugin.getLogger().info(describeWouldDo("DRY-RUN | MOB-DROP @ " + world, total));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMend(@NotNull PlayerItemMendEvent event) {
        // Block the very effect of mending: XP never repairs durability.
        if (!active() || !config().isBlockXpRepair() || worldDisabled(event.getPlayer())) {
            return;
        }
        if (config().bypass(event.getPlayer()).test(Enchantment.MENDING)) {
            return;
        }
        // Dry-run: stand down silently (fires on every XP orb — logging it would flood the console).
        if (config().isDryRun()) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Blocks banned enchantments (and applies level caps) at the source: the enchanting table.
     * Better UX than letting the player pay levels and purging the result a tick later.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(@NotNull EnchantItemEvent event) {
        if (!active() || !config().isBlockAtTable() || worldDisabled(event.getEnchanter())) {
            return;
        }
        final AntiEnchantsConfig config = config();
        if (config.isExemptItem(event.getItem().getType())) {
            return;
        }
        final String world = event.getEnchanter().getWorld().getName();
        final Predicate<Enchantment> bypass = config.bypass(event.getEnchanter());
        final Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();
        if (config.isDryRun()) {
            final List<StripResult.Removed> wouldRemove = new ArrayList<>(0);
            int wouldCap = 0;
            for (Map.Entry<Enchantment, Integer> entry : toAdd.entrySet()) {
                if (bypass.test(entry.getKey())) {
                    continue;
                }
                if (config.isBanned(entry.getKey(), world)) {
                    wouldRemove.add(new StripResult.Removed(entry.getKey(), entry.getValue()));
                } else {
                    final int cap = config.levelCap(entry.getKey(), world);
                    if (cap > 0 && entry.getValue() > cap) {
                        wouldCap++;
                    }
                }
            }
            final StripResult result = new StripResult(List.copyOf(wouldRemove), wouldCap);
            if (result.changed()) {
                reportDryRun(event.getEnchanter(), result, "TABLE");
            }
            return;
        }
        toAdd.entrySet().removeIf(entry ->
                !bypass.test(entry.getKey()) && config.isBanned(entry.getKey(), world));
        for (Map.Entry<Enchantment, Integer> entry : toAdd.entrySet()) {
            if (bypass.test(entry.getKey())) {
                continue;
            }
            final int cap = config.levelCap(entry.getKey(), world);
            if (cap > 0 && entry.getValue() > cap) {
                entry.setValue(cap);
            }
        }
    }

    /** Strips banned/over-cap enchantments from the anvil result preview (honest preview, no charge-then-purge). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(@NotNull PrepareAnvilEvent event) {
        if (active() && config().isBlockAtAnvil()) {
            stripResultPreview(event.getView().getPlayer() instanceof Player player ? player : null, event);
        }
    }

    /** Same for the grindstone result (mainly curses, which the grindstone keeps on the output). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(@NotNull PrepareGrindstoneEvent event) {
        if (active() && config().isBlockAtGrindstone()) {
            stripResultPreview(event.getView().getPlayer() instanceof Player player ? player : null, event);
        }
    }

    /** And the smithing table (netherite upgrades carry every enchantment onto the result). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(@NotNull PrepareSmithingEvent event) {
        if (active() && config().isBlockAtSmithing()) {
            stripResultPreview(event.getView().getPlayer() instanceof Player player ? player : null, event);
        }
    }

    private void stripResultPreview(@Nullable Player player,
                                    @NotNull com.destroystokyo.paper.event.inventory.PrepareResultEvent event) {
        // Dry-run: leave the vanilla preview alone, silently (prepare events fire on every slot
        // change — logging would flood the console, and a preview is not destructive anyway).
        if (player == null || worldDisabled(player) || config().isDryRun()) {
            return;
        }
        final ItemStack result = event.getResult();
        if (result == null || result.isEmpty()) {
            return;
        }
        final ItemStack clean = result.clone();
        if (EnchantStripper.strip(clean, config(), player.getWorld().getName(), config().bypass(player)).changed()) {
            event.setResult(clean);
        }
    }
}
