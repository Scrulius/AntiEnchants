/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants.command;

import dev.scrulius.antienchants.AntiEnchantsConfig;
import dev.scrulius.antienchants.AntiEnchantsPlugin;
import dev.scrulius.antienchants.EnchantStripper.StripResult;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * {@code /antienchants} admin command (permission {@code antienchants.admin}, default op):
 * <ul>
 *   <li>{@code reload} — re-reads {@code config.yml} without a restart.</li>
 *   <li>{@code list} — shows the banned keys and level caps in effect.</li>
 *   <li>{@code check [player]} — inspects the held item (or another player's whole
 *       inventory) for banned / over-cap enchantments.</li>
 *   <li>{@code add <key>} / {@code remove <key>} — edits the blocklist, saves and reloads.</li>
 *   <li>{@code cap <key> <level|off>} — sets/clears a level cap, saves and reloads.</li>
 *   <li>{@code purge [player|all]} — cleans online inventories on demand (honours dry-run).</li>
 * </ul>
 */
public final class AntiEnchantsCommand implements TabExecutor {

    private static final String KEYS_PATH = "banned-enchantments.keys";
    private static final String CAPS_PATH = "level-caps";
    private static final List<String> SUBCOMMANDS =
            List.of("reload", "list", "check", "add", "remove", "cap", "purge");

    private final AntiEnchantsPlugin plugin;

    public AntiEnchantsCommand(@NotNull AntiEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("antienchants.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.config().reload();
                sender.sendMessage(Component.text("AntiEnchants reloaded. ", NamedTextColor.GREEN)
                        .append(summary()));
                if (plugin.config().isDryRun()) {
                    sender.sendMessage(dryRunBanner());
                }
            }
            case "list" -> list(sender);
            case "check" -> check(sender, args.length >= 2 ? args[1] : null);
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /antienchants add <enchantment>", NamedTextColor.YELLOW));
                } else {
                    add(sender, args[1]);
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /antienchants remove <enchantment>", NamedTextColor.YELLOW));
                } else {
                    remove(sender, args[1]);
                }
            }
            case "cap" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /antienchants cap <enchantment> <level|off>",
                            NamedTextColor.YELLOW));
                } else {
                    cap(sender, args[1], args[2]);
                }
            }
            case "purge" -> purge(sender, args.length >= 2 ? args[1] : null);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /antienchants <reload|list|check|add|remove|cap|purge>",
                NamedTextColor.YELLOW));
    }

    private static @NotNull Component dryRunBanner() {
        return Component.text("DRY-RUN mode is ON — violations are only logged, nothing is modified.",
                NamedTextColor.YELLOW);
    }

    private @NotNull Component summary() {
        final AntiEnchantsConfig config = plugin.config();
        return Component.text(config.bannedKeyCount() + " banned, " + config.levelCapCount() + " level-capped.",
                NamedTextColor.GRAY);
    }

    private void list(@NotNull CommandSender sender) {
        final AntiEnchantsConfig config = plugin.config();
        if (config.isDryRun()) {
            sender.sendMessage(dryRunBanner());
        }
        sender.sendMessage(Component.text("Banned enchantments:", NamedTextColor.GOLD));
        if (config.getBannedKeys().isEmpty()) {
            sender.sendMessage(Component.text("  (none)", NamedTextColor.GRAY));
        } else {
            for (String key : new TreeSet<>(config.getBannedKeys())) {
                sender.sendMessage(Component.text("  - " + key, NamedTextColor.RED));
            }
        }
        if (config.isBanAllCurses()) {
            sender.sendMessage(Component.text("  + every curse (ban-all-curses)", NamedTextColor.RED));
        }
        final Map<String, Integer> caps = new TreeMap<>(config.getLevelCaps());
        if (!caps.isEmpty()) {
            sender.sendMessage(Component.text("Level caps:", NamedTextColor.GOLD));
            caps.forEach((key, cap) -> sender.sendMessage(
                    Component.text("  - " + key + " <= " + cap, NamedTextColor.YELLOW)));
        }
        if (config.perWorldRuleCount() > 0) {
            sender.sendMessage(Component.text("Per-world extra rules: " + config.perWorldRuleCount()
                    + " world(s) (see config.yml).", NamedTextColor.GRAY));
        }
    }

    private void check(@NotNull CommandSender sender, @Nullable String targetName) {
        if (targetName != null) {
            final Player target = plugin.getServer().getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + targetName + "' is not online.",
                        NamedTextColor.RED));
                return;
            }
            checkInventory(sender, target);
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("From console, use /antienchants check <player>.",
                    NamedTextColor.RED));
            return;
        }
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) {
            sender.sendMessage(Component.text("Hold an item to check.", NamedTextColor.YELLOW));
            return;
        }
        final AntiEnchantsConfig config = plugin.config();
        if (config.isExemptItem(item.getType())) {
            sender.sendMessage(Component.text("This item type is exempt (exempt-items) — never touched.",
                    NamedTextColor.GREEN));
            return;
        }
        final String world = player.getWorld().getName();
        final List<Component> findings = new ArrayList<>();
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            collectFindings(meta.getEnchants(), config, world, findings);
            if (meta instanceof EnchantmentStorageMeta storage) {
                collectFindings(storage.getStoredEnchants(), config, world, findings);
            }
        }
        if (findings.isEmpty()) {
            sender.sendMessage(Component.text("Item is clean — nothing banned or over the cap.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Violations on this item:", NamedTextColor.GOLD));
            findings.forEach(sender::sendMessage);
        }
    }

    /** Scans every slot of a player's inventory (armour + offhand included) and reports violations. */
    private void checkInventory(@NotNull CommandSender sender, @NotNull Player target) {
        final AntiEnchantsConfig config = plugin.config();
        final String world = target.getWorld().getName();
        int flagged = 0;
        for (ItemStack item : target.getInventory().getContents()) {
            if (item == null || item.isEmpty() || !item.hasItemMeta() || config.isExemptItem(item.getType())) {
                continue;
            }
            final List<Component> findings = new ArrayList<>();
            final ItemMeta meta = item.getItemMeta();
            collectFindings(meta.getEnchants(), config, world, findings);
            if (meta instanceof EnchantmentStorageMeta storage) {
                collectFindings(storage.getStoredEnchants(), config, world, findings);
            }
            if (findings.isEmpty()) {
                continue;
            }
            if (flagged == 0) {
                sender.sendMessage(Component.text("Violations in " + target.getName() + "'s inventory:",
                        NamedTextColor.GOLD));
            }
            flagged++;
            sender.sendMessage(Component.text("  " + item.getType().name()
                    + (item.getAmount() > 1 ? " x" + item.getAmount() : ""), NamedTextColor.AQUA));
            findings.forEach(line -> sender.sendMessage(Component.text("  ").append(line)));
        }
        if (flagged == 0) {
            sender.sendMessage(Component.text(target.getName()
                    + "'s inventory is clean — nothing banned or over the cap.", NamedTextColor.GREEN));
        }
    }

    private void collectFindings(@NotNull Map<Enchantment, Integer> enchants,
                                 @NotNull AntiEnchantsConfig config, @NotNull String world,
                                 @NotNull List<Component> findings) {
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            final String key = entry.getKey().getKey().toString();
            if (config.isBanned(entry.getKey(), world)) {
                findings.add(Component.text("  - " + key + " (BANNED)", NamedTextColor.RED));
            } else {
                final int cap = config.levelCap(entry.getKey(), world);
                if (cap > 0 && entry.getValue() > cap) {
                    findings.add(Component.text("  - " + key + " " + entry.getValue()
                            + " (over cap, max " + cap + ")", NamedTextColor.YELLOW));
                }
            }
        }
    }

    /** Sets or clears ({@code off} / {@code 0}) a global level cap, persisting it to config.yml. */
    private void cap(@NotNull CommandSender sender, @NotNull String rawKey, @NotNull String rawLevel) {
        final String normalized = AntiEnchantsConfig.normalizeKey(rawKey);
        // Stored short for minecraft keys ("sharpness"), full otherwise; clear both variants on write.
        final String shortKey = normalized.startsWith("minecraft:")
                ? normalized.substring("minecraft:".length()) : normalized;
        if (rawLevel.equalsIgnoreCase("off") || rawLevel.equals("0")) {
            if (!plugin.config().getLevelCaps().containsKey(normalized)) {
                sender.sendMessage(Component.text(normalized + " has no level cap.", NamedTextColor.YELLOW));
                return;
            }
            plugin.getConfig().set(CAPS_PATH + "." + shortKey, null);
            plugin.getConfig().set(CAPS_PATH + "." + normalized, null);
            persist();
            sender.sendMessage(Component.text("Removed the level cap on " + normalized + ". ",
                    NamedTextColor.GREEN).append(summary()));
            return;
        }
        final int level;
        try {
            level = Integer.parseInt(rawLevel);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("'" + rawLevel + "' is not a level (number or 'off').",
                    NamedTextColor.RED));
            return;
        }
        if (level < 1) {
            sender.sendMessage(Component.text("The cap must be 1 or higher (use 'off' to remove it).",
                    NamedTextColor.RED));
            return;
        }
        plugin.getConfig().set(CAPS_PATH + "." + normalized, null);
        plugin.getConfig().set(CAPS_PATH + "." + shortKey, level);
        persist();
        sender.sendMessage(Component.text("Capped " + normalized + " at level " + level + ". ",
                NamedTextColor.GREEN).append(summary()));
    }

    private void persist() {
        plugin.saveConfig();
        plugin.config().reload();
    }

    private void add(@NotNull CommandSender sender, @NotNull String raw) {
        final String normalized = AntiEnchantsConfig.normalizeKey(raw);
        final List<String> keys = new ArrayList<>(plugin.getConfig().getStringList(KEYS_PATH));
        if (keys.stream().anyMatch(k -> AntiEnchantsConfig.normalizeKey(k).equals(normalized))) {
            sender.sendMessage(Component.text(normalized + " is already banned.", NamedTextColor.YELLOW));
            return;
        }
        keys.add(raw.trim().toLowerCase(Locale.ROOT));
        saveKeys(keys);
        sender.sendMessage(Component.text("Banned " + normalized + ". ", NamedTextColor.GREEN).append(summary()));
    }

    private void remove(@NotNull CommandSender sender, @NotNull String raw) {
        final String normalized = AntiEnchantsConfig.normalizeKey(raw);
        final List<String> keys = new ArrayList<>(plugin.getConfig().getStringList(KEYS_PATH));
        if (!keys.removeIf(k -> AntiEnchantsConfig.normalizeKey(k).equals(normalized))) {
            sender.sendMessage(Component.text(normalized + " is not in the blocklist.", NamedTextColor.YELLOW));
            return;
        }
        saveKeys(keys);
        sender.sendMessage(Component.text("Unbanned " + normalized + ". ", NamedTextColor.GREEN).append(summary()));
    }

    /**
     * Cleans online inventories on demand — right after tightening the rules, instead of waiting
     * for each player's next join/click. Runs the same pipeline as the automatic strips (bypass
     * permissions, messages, compensation, audit), so in dry-run mode it only reports.
     */
    private void purge(@NotNull CommandSender sender, @Nullable String target) {
        final List<Player> targets = new ArrayList<>();
        if (target == null) {
            if (sender instanceof Player player) {
                targets.add(player);
            } else {
                sender.sendMessage(Component.text("Usage: /antienchants purge <player|all>",
                        NamedTextColor.YELLOW));
                return;
            }
        } else if (target.equalsIgnoreCase("all")) {
            targets.addAll(plugin.getServer().getOnlinePlayers());
        } else {
            final Player player = plugin.getServer().getPlayerExact(target);
            if (player == null) {
                sender.sendMessage(Component.text("Player '" + target + "' is not online.",
                        NamedTextColor.RED));
                return;
            }
            targets.add(player);
        }

        final AntiEnchantsConfig config = plugin.config();
        int purged = 0;
        int removed = 0;
        int capped = 0;
        int skipped = 0;
        for (Player player : targets) {
            if (config.isWorldDisabled(player.getWorld().getName())) {
                skipped++;
                continue;
            }
            final StripResult result = plugin.stripListener().purgePlayer(player);
            purged++;
            removed += result.removed().size();
            capped += result.capped();
        }
        if (config.isDryRun()) {
            sender.sendMessage(Component.text("DRY-RUN: checked " + purged + " player(s) — would remove "
                    + removed + " enchantment(s) and cap " + capped + ". Nothing was modified.",
                    NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Purged " + purged + " player(s): " + removed
                    + " enchantment(s) removed, " + capped + " capped.", NamedTextColor.GREEN));
        }
        if (skipped > 0) {
            sender.sendMessage(Component.text(skipped + " player(s) skipped (disabled world).",
                    NamedTextColor.GRAY));
        }
    }

    private void saveKeys(@NotNull List<String> keys) {
        plugin.getConfig().set(KEYS_PATH, keys);
        plugin.saveConfig();
        plugin.config().reload();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("antienchants.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("cap"))) {
            final List<String> keys = new ArrayList<>();
            for (Enchantment ench : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
                final NamespacedKey key = ench.getKey();
                keys.add(NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : key.toString());
            }
            return filterPrefix(keys, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return filterPrefix(new ArrayList<>(plugin.config().getBannedKeys()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("purge")) {
            final List<String> options = new ArrayList<>();
            options.add("all");
            plugin.getServer().getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return filterPrefix(options, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            final List<String> options = new ArrayList<>();
            plugin.getServer().getOnlinePlayers().forEach(p -> options.add(p.getName()));
            return filterPrefix(options, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cap")) {
            return filterPrefix(List.of("1", "2", "3", "4", "off"), args[2]);
        }
        return List.of();
    }

    private static @NotNull List<String> filterPrefix(@NotNull List<String> options, @NotNull String prefix) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
