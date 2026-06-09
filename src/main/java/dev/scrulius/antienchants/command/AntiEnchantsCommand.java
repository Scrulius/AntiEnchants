/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants.command;

import dev.scrulius.antienchants.AntiEnchantsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code /antienchants reload} - re-reads {@code config.yml} without a restart.
 * Permission: {@code antienchants.admin} (default op).
 */
public final class AntiEnchantsCommand implements TabExecutor {

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
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.config().reload();
            sender.sendMessage(Component.text("AntiEnchants reloaded. ", NamedTextColor.GREEN)
                    .append(Component.text(plugin.config().bannedKeyCount() + " enchantment(s) in the blocklist.",
                            NamedTextColor.GRAY)));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /antienchants reload", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("antienchants.admin")) {
            return List.of("reload");
        }
        return List.of();
    }
}
