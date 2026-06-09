/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants;

import dev.scrulius.antienchants.command.AntiEnchantsCommand;
import dev.scrulius.antienchants.listener.BannedEnchantmentListener;
import dev.scrulius.antienchants.listener.VillagerTradeListener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * AntiEnchants — blocks unwanted enchantments server-wide.
 * <p>
 * Standalone successor to the original AntiMending: generalised from a single
 * hardcoded enchantment to a configurable blacklist (plus "ban all curses"),
 * with villager book-trade control, world exclusions and a live-reload command.
 */
public final class AntiEnchantsPlugin extends JavaPlugin {

    private AntiEnchantsConfig config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new AntiEnchantsConfig(this);

        final PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BannedEnchantmentListener(this), this);
        pm.registerEvents(new VillagerTradeListener(this), this);

        final var cmd = Objects.requireNonNull(getCommand("antienchants"),
                "command 'antienchants' missing from plugin.yml");
        cmd.setExecutor(new AntiEnchantsCommand(this));

        getLogger().info("AntiEnchants habilitado. " + config.bannedKeyCount()
                + " encantamiento(s) en la lista de bloqueo.");
    }

    @Override
    public void onDisable() {
        getLogger().info("AntiEnchants deshabilitado.");
    }

    /** @return the cached, reloadable config view */
    public @NotNull AntiEnchantsConfig config() {
        return config;
    }
}
