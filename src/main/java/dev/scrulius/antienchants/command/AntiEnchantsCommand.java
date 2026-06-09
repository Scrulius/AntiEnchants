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
 * {@code /antienchants reload} — re-reads {@code config.yml} without a restart.
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
            sender.sendMessage(Component.text("No tienes permiso para usar este comando.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.config().reload();
            sender.sendMessage(Component.text("AntiEnchants recargado. ", NamedTextColor.GREEN)
                    .append(Component.text(plugin.config().bannedKeyCount() + " encantamiento(s) en la lista.",
                            NamedTextColor.GRAY)));
            return true;
        }
        sender.sendMessage(Component.text("Uso: /antienchants reload", NamedTextColor.YELLOW));
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
