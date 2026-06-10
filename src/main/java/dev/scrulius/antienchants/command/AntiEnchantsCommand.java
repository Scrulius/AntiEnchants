/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants.command;

import dev.scrulius.antienchants.AntiEnchantsConfig;
import dev.scrulius.antienchants.AntiEnchantsPlugin;
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
 *   <li>{@code check} — inspects the held item for banned / over-cap enchantments.</li>
 *   <li>{@code add <key>} / {@code remove <key>} — edits the blocklist, saves and reloads.</li>
 * </ul>
 */
public final class AntiEnchantsCommand implements TabExecutor {

    private static final String KEYS_PATH = "banned-enchantments.keys";
    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "check", "add", "remove");

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
            }
            case "list" -> list(sender);
            case "check" -> check(sender);
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
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /antienchants <reload|list|check|add|remove>", NamedTextColor.YELLOW));
    }

    private @NotNull Component summary() {
        final AntiEnchantsConfig config = plugin.config();
        return Component.text(config.bannedKeyCount() + " banned, " + config.levelCapCount() + " level-capped.",
                NamedTextColor.GRAY);
    }

    private void list(@NotNull CommandSender sender) {
        final AntiEnchantsConfig config = plugin.config();
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
    }

    private void check(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can check their held item.", NamedTextColor.RED));
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
        final List<Component> findings = new ArrayList<>();
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            collectFindings(meta.getEnchants(), config, findings);
            if (meta instanceof EnchantmentStorageMeta storage) {
                collectFindings(storage.getStoredEnchants(), config, findings);
            }
        }
        if (findings.isEmpty()) {
            sender.sendMessage(Component.text("Item is clean — nothing banned or over the cap.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Violations on this item:", NamedTextColor.GOLD));
            findings.forEach(sender::sendMessage);
        }
    }

    private void collectFindings(@NotNull Map<Enchantment, Integer> enchants,
                                 @NotNull AntiEnchantsConfig config, @NotNull List<Component> findings) {
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            final String key = entry.getKey().getKey().toString();
            if (config.isBanned(entry.getKey())) {
                findings.add(Component.text("  - " + key + " (BANNED)", NamedTextColor.RED));
            } else {
                final int cap = config.levelCap(entry.getKey());
                if (cap > 0 && entry.getValue() > cap) {
                    findings.add(Component.text("  - " + key + " " + entry.getValue()
                            + " (over cap, max " + cap + ")", NamedTextColor.YELLOW));
                }
            }
        }
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
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
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
        return List.of();
    }

    private static @NotNull List<String> filterPrefix(@NotNull List<String> options, @NotNull String prefix) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
