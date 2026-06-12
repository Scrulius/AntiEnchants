/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Typed, cached view of {@code config.yml}. Rebuilt on {@link #reload()} so
 * {@code /antienchants reload} takes effect without a restart.
 */
public final class AntiEnchantsConfig {

    private static final String BYPASS_WILDCARD = "antienchants.bypass.*";

    private final AntiEnchantsPlugin plugin;

    // Dry-run: detect and log only, never modify anything
    private boolean dryRun;

    // Banned enchantments
    private boolean bannedEnabled;
    private boolean purgeInventories;
    private boolean blockXpRepair;
    private boolean stripOnPickup;
    private boolean stripOnFish;
    private boolean stripFromLoot;
    private boolean stripMobDrops;
    private boolean banAllCurses;
    private boolean convertEmptyBooks;
    private boolean blockAtTable;
    private boolean blockAtAnvil;
    private boolean blockAtGrindstone;
    private Set<String> bannedKeys = Set.of();

    // Per-world extra rules, already merged with the global ones (world name lowercase -> rules)
    private record WorldRules(@NotNull Set<String> banned, @NotNull Map<String, Integer> caps) {
    }

    private Map<String, WorldRules> perWorldRules = Map.of();

    // Audit log
    private boolean auditEnabled;
    private int auditMaxFileKb;

    // Exempt item types (whitelist)
    private Set<Material> exemptMaterials = Set.of();
    private List<Pattern> exemptPatterns = List.of();

    // Level caps (normalized key -> max allowed level)
    private Map<String, Integer> levelCaps = Map.of();

    // Compensation
    private boolean compensationEnabled;
    private List<ItemStack> compensationDefault = List.of();
    private Map<String, List<ItemStack>> compensationPerEnchant = Map.of();

    // Permission bypass
    private boolean permissionBypassEnabled;

    // Player feedback (MiniMessage; blank = silent)
    private String strippedMessage = "";
    private String cappedMessage = "";
    private String compensatedMessage = "";

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

        dryRun = c.getBoolean("dry-run", false);
        bannedEnabled = c.getBoolean("banned-enchantments.enabled", true);
        purgeInventories = c.getBoolean("banned-enchantments.purge-player-inventories", true);
        blockXpRepair = c.getBoolean("banned-enchantments.block-xp-repair", true);
        stripOnPickup = c.getBoolean("banned-enchantments.strip-on-pickup", true);
        stripOnFish = c.getBoolean("banned-enchantments.strip-on-fish", true);
        stripFromLoot = c.getBoolean("banned-enchantments.strip-from-loot", true);
        stripMobDrops = c.getBoolean("banned-enchantments.strip-mob-drops", true);
        banAllCurses = c.getBoolean("banned-enchantments.ban-all-curses", false);
        convertEmptyBooks = c.getBoolean("banned-enchantments.convert-empty-books", true);
        blockAtTable = c.getBoolean("banned-enchantments.block-at-table", true);
        blockAtAnvil = c.getBoolean("banned-enchantments.block-at-anvil", true);
        blockAtGrindstone = c.getBoolean("banned-enchantments.block-at-grindstone", true);

        final Set<String> keys = new HashSet<>();
        for (String raw : c.getStringList("banned-enchantments.keys")) {
            if (raw != null && !raw.isBlank()) {
                keys.add(normalizeKey(raw));
            }
        }
        bannedKeys = keys;

        loadExemptItems(c.getStringList("banned-enchantments.exempt-items"));
        loadLevelCaps(c.getConfigurationSection("level-caps"));
        loadPerWorld(c.getConfigurationSection("per-world"));
        loadCompensation(c);

        permissionBypassEnabled = c.getBoolean("permission-bypass.enabled", true);

        auditEnabled = c.getBoolean("audit-log.enabled", false);
        auditMaxFileKb = c.getInt("audit-log.max-file-kb", 2048);

        strippedMessage = c.getString("messages.stripped", "");
        cappedMessage = c.getString("messages.capped", "");
        compensatedMessage = c.getString("messages.compensated", "");

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

        registerBypassPermissions();

        if (dryRun) {
            plugin.getLogger().warning("DRY-RUN mode is ON: violations are only logged (console"
                    + " + strips.log if the audit log is enabled), no item is ever modified."
                    + " Set 'dry-run: false' to enforce the rules.");
        }
    }

    private void loadExemptItems(@NotNull List<String> raw) {
        final Set<Material> mats = EnumSet.noneOf(Material.class);
        final List<Pattern> patterns = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            final String name = entry.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (name.indexOf('*') >= 0) {
                patterns.add(Pattern.compile(name.replace("*", ".*")));
            } else {
                final Material mat = Material.matchMaterial(name);
                if (mat != null) {
                    mats.add(mat);
                } else {
                    plugin.getLogger().warning("exempt-items: unknown material '" + entry + "'");
                }
            }
        }
        exemptMaterials = mats;
        exemptPatterns = List.copyOf(patterns);
    }

    private void loadLevelCaps(@Nullable ConfigurationSection section) {
        final Map<String, Integer> caps = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                final int cap = section.getInt(key, 0);
                if (cap > 0) {
                    caps.put(normalizeKey(key), cap);
                } else {
                    plugin.getLogger().warning("level-caps: invalid cap for '" + key + "' (must be > 0)");
                }
            }
        }
        levelCaps = caps;
    }

    /** Merges each world's extra banned keys / cap overrides on top of the global rules. Must run after both. */
    private void loadPerWorld(@Nullable ConfigurationSection section) {
        final Map<String, WorldRules> rules = new HashMap<>();
        if (section != null) {
            for (String world : section.getKeys(false)) {
                final ConfigurationSection ws = section.getConfigurationSection(world);
                if (ws == null) {
                    continue;
                }
                final Set<String> banned = new HashSet<>(bannedKeys);
                for (String raw : ws.getStringList("keys")) {
                    if (raw != null && !raw.isBlank()) {
                        banned.add(normalizeKey(raw));
                    }
                }
                final Map<String, Integer> caps = new HashMap<>(levelCaps);
                final ConfigurationSection capsSection = ws.getConfigurationSection("level-caps");
                if (capsSection != null) {
                    for (String key : capsSection.getKeys(false)) {
                        final int cap = capsSection.getInt(key, 0);
                        if (cap > 0) {
                            caps.put(normalizeKey(key), cap);
                        } else {
                            plugin.getLogger().warning("per-world." + world
                                    + ".level-caps: invalid cap for '" + key + "' (must be > 0)");
                        }
                    }
                }
                rules.put(world.toLowerCase(Locale.ROOT),
                        new WorldRules(Set.copyOf(banned), Map.copyOf(caps)));
            }
        }
        perWorldRules = rules;
    }

    private void loadCompensation(@NotNull FileConfiguration c) {
        compensationEnabled = c.getBoolean("compensation.enabled", false);
        compensationDefault = parseItemList(c.getStringList("compensation.default"));
        final Map<String, List<ItemStack>> per = new HashMap<>();
        final ConfigurationSection section = c.getConfigurationSection("compensation.per-enchant");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                per.put(normalizeKey(key), parseItemList(section.getStringList(key)));
            }
        }
        compensationPerEnchant = per;
    }

    /** Parses {@code "MATERIAL:amount"} entries into item templates (amount defaults to 1). */
    private @NotNull List<ItemStack> parseItemList(@NotNull List<String> raw) {
        final List<ItemStack> out = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            final String[] parts = entry.trim().split(":", 2);
            final Material mat = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
            if (mat == null || !mat.isItem()) {
                plugin.getLogger().warning("compensation: unknown item '" + entry + "'");
                continue;
            }
            int amount = 1;
            if (parts.length == 2) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("compensation: invalid amount in '" + entry + "', using 1");
                }
            }
            out.add(new ItemStack(mat, amount));
        }
        return List.copyOf(out);
    }

    /** Normalizes an enchantment key to {@code namespace:key} (defaulting to {@code minecraft:}), lowercased. */
    public static @NotNull String normalizeKey(@NotNull String raw) {
        final String key = raw.trim().toLowerCase(Locale.ROOT);
        return key.contains(":") ? key : "minecraft:" + key;
    }

    /**
     * Whether an enchantment is banned in a world — listed explicitly (globally or in that
     * world's {@code per-world} extras) or, when {@code ban-all-curses} is on, any curse.
     * {@code null} world = global rules only.
     */
    public boolean isBanned(@NotNull Enchantment ench, @Nullable String worldName) {
        if (banAllCurses && ench.isCursed()) {
            return true;
        }
        final Set<String> keys = bannedKeysFor(worldName);
        return !keys.isEmpty()
                && keys.contains(ench.getKey().toString().toLowerCase(Locale.ROOT));
    }

    /** @return the maximum level for an enchantment in a world, or {@code 0} if uncapped */
    public int levelCap(@NotNull Enchantment ench, @Nullable String worldName) {
        return capsFor(worldName).getOrDefault(ench.getKey().toString().toLowerCase(Locale.ROOT), 0);
    }

    private @NotNull Set<String> bannedKeysFor(@Nullable String worldName) {
        if (worldName != null && !perWorldRules.isEmpty()) {
            final WorldRules rules = perWorldRules.get(worldName.toLowerCase(Locale.ROOT));
            if (rules != null) {
                return rules.banned();
            }
        }
        return bannedKeys;
    }

    private @NotNull Map<String, Integer> capsFor(@Nullable String worldName) {
        if (worldName != null && !perWorldRules.isEmpty()) {
            final WorldRules rules = perWorldRules.get(worldName.toLowerCase(Locale.ROOT));
            if (rules != null) {
                return rules.caps();
            }
        }
        return levelCaps;
    }

    /** @return whether the item type is whitelisted and must never be touched */
    public boolean isExemptItem(@NotNull Material material) {
        if (exemptMaterials.contains(material)) {
            return true;
        }
        if (!exemptPatterns.isEmpty()) {
            final String name = material.name();
            for (Pattern pattern : exemptPatterns) {
                if (pattern.matcher(name).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @return the compensation items for a stripped enchantment (per-enchant override or default list) */
    public @NotNull List<ItemStack> compensationFor(@NotNull Enchantment ench) {
        final List<ItemStack> list = compensationPerEnchant.get(ench.getKey().toString().toLowerCase(Locale.ROOT));
        return list != null ? list : compensationDefault;
    }

    /**
     * Per-enchantment bypass predicate for a player context. Returns "never bypass" when there is
     * no player or the feature is off. Nodes are registered with default FALSE on reload, so ops
     * don't silently bypass via Bukkit's undefined-permission-defaults-to-op rule.
     */
    public @NotNull Predicate<Enchantment> bypass(@Nullable Player player) {
        if (player == null || !permissionBypassEnabled) {
            return ench -> false;
        }
        return ench -> player.hasPermission(bypassNode(ench));
    }

    /** Permission node to bypass restrictions on an enchantment, e.g. {@code antienchants.bypass.mending}. */
    public static @NotNull String bypassNode(@NotNull Enchantment ench) {
        return bypassNode(ench.getKey().getNamespace(), ench.getKey().getKey());
    }

    private static @NotNull String bypassNode(@NotNull String namespace, @NotNull String key) {
        return NamespacedKey.MINECRAFT.equals(namespace)
                ? "antienchants.bypass." + key
                : "antienchants.bypass." + namespace + "." + key;
    }

    private static @NotNull String bypassNodeFromKey(@NotNull String normalizedKey) {
        final int colon = normalizedKey.indexOf(':');
        return bypassNode(normalizedKey.substring(0, colon), normalizedKey.substring(colon + 1));
    }

    /**
     * Registers every relevant {@code antienchants.bypass.<key>} node with default FALSE (plus the
     * wildcard parent). Without this, undefined permissions default to OP and every op would bypass
     * everything silently.
     */
    private void registerBypassPermissions() {
        if (!permissionBypassEnabled) {
            return;
        }
        final PluginManager pm = plugin.getServer().getPluginManager();
        Permission wildcard = pm.getPermission(BYPASS_WILDCARD);
        if (wildcard == null) {
            wildcard = new Permission(BYPASS_WILDCARD, "Bypass every AntiEnchants restriction.",
                    PermissionDefault.FALSE);
            pm.addPermission(wildcard);
        }
        final Set<String> nodes = new HashSet<>();
        for (String key : bannedKeys) {
            nodes.add(bypassNodeFromKey(key));
        }
        for (String key : levelCaps.keySet()) {
            nodes.add(bypassNodeFromKey(key));
        }
        for (WorldRules rules : perWorldRules.values()) {
            for (String key : rules.banned()) {
                nodes.add(bypassNodeFromKey(key));
            }
            for (String key : rules.caps().keySet()) {
                nodes.add(bypassNodeFromKey(key));
            }
        }
        if (banAllCurses) {
            for (Enchantment ench : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
                if (ench.isCursed()) {
                    nodes.add(bypassNode(ench));
                }
            }
        }
        for (String node : nodes) {
            if (pm.getPermission(node) == null) {
                final Permission permission = new Permission(node,
                        "Bypass AntiEnchants restrictions on this enchantment.", PermissionDefault.FALSE);
                permission.addParent(wildcard, true);
                pm.addPermission(permission);
            }
        }
    }

    /** @return whether dry-run mode is on (detect + log only; nothing is ever modified) */
    public boolean isDryRun() { return dryRun; }

    public boolean isBannedEnabled() { return bannedEnabled; }
    public boolean isPurgeInventories() { return purgeInventories; }
    public boolean isBlockXpRepair() { return blockXpRepair; }
    public boolean isStripOnPickup() { return stripOnPickup; }
    public boolean isStripOnFish() { return stripOnFish; }
    public boolean isStripFromLoot() { return stripFromLoot; }
    public boolean isStripMobDrops() { return stripMobDrops; }
    public boolean isBanAllCurses() { return banAllCurses; }
    public boolean isConvertEmptyBooks() { return convertEmptyBooks; }
    public boolean isBlockAtTable() { return blockAtTable; }
    public boolean isBlockAtAnvil() { return blockAtAnvil; }
    public boolean isBlockAtGrindstone() { return blockAtGrindstone; }
    public boolean isCompensationEnabled() { return compensationEnabled; }
    public boolean isAuditEnabled() { return auditEnabled; }
    public int getAuditMaxFileKb() { return auditMaxFileKb; }

    public @NotNull String getStrippedMessage() { return strippedMessage; }
    public @NotNull String getCappedMessage() { return cappedMessage; }
    public @NotNull String getCompensatedMessage() { return compensatedMessage; }

    public boolean isVillagerEnabled() { return villagerEnabled; }
    public boolean isBlockBookTrades() { return blockBookTrades; }
    public boolean isBlockBannedEnchantTrades() { return blockBannedEnchantTrades; }

    /** @return the normalized banned keys (read-only; for /antienchants list & tab completion) */
    public @NotNull Set<String> getBannedKeys() { return Set.copyOf(bannedKeys); }

    /** @return the normalized level caps (read-only; for /antienchants list) */
    public @NotNull Map<String, Integer> getLevelCaps() { return Map.copyOf(levelCaps); }

    /** @return how many enchantment keys are explicitly banned (for the startup log) */
    public int bannedKeyCount() { return bannedKeys.size(); }

    /** @return how many enchantments have a level cap (for the startup log) */
    public int levelCapCount() { return levelCaps.size(); }

    /** @return how many worlds define extra rules (for /antienchants list) */
    public int perWorldRuleCount() { return perWorldRules.size(); }

    /** @return whether AntiEnchants should be skipped in the given world */
    public boolean isWorldDisabled(@NotNull String worldName) {
        return disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }
}
