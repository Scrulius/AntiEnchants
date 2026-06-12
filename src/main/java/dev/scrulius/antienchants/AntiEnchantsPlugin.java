/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants;

import dev.scrulius.antienchants.command.AntiEnchantsCommand;
import dev.scrulius.antienchants.listener.BannedEnchantmentListener;
import dev.scrulius.antienchants.listener.VillagerTradeListener;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * AntiEnchants — blocks unwanted enchantments server-wide.
 * <p>
 * Standalone successor to the original AntiMending: generalised from a single
 * hardcoded enchantment to a configurable blacklist (plus "ban all curses"),
 * with level caps, per-world rules, compensation, permission bypasses, villager
 * book-trade control, an audit log, world exclusions and a live-reload command.
 */
public final class AntiEnchantsPlugin extends JavaPlugin {

    /** bStats service id (https://bstats.org/plugin/bukkit/AntiEnchants/31912). */
    private static final int BSTATS_PLUGIN_ID = 31912;

    private AntiEnchantsConfig config;
    private AuditLog auditLog;
    private BannedEnchantmentListener stripListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigMerger.addMissingKeys(this);
        this.config = new AntiEnchantsConfig(this);
        this.auditLog = new AuditLog(this);

        final PluginManager pm = getServer().getPluginManager();
        this.stripListener = new BannedEnchantmentListener(this);
        pm.registerEvents(stripListener, this);
        pm.registerEvents(new VillagerTradeListener(this), this);

        final var cmd = Objects.requireNonNull(getCommand("antienchants"),
                "command 'antienchants' missing from plugin.yml");
        cmd.setExecutor(new AntiEnchantsCommand(this));

        initMetrics();

        getLogger().info("AntiEnchants enabled. " + config.bannedKeyCount()
                + " banned enchantment(s), " + config.levelCapCount() + " level cap(s).");
    }

    /** Anonymous usage stats (bStats, opt-out via plugins/bStats/config.yml). */
    private void initMetrics() {
        if (BSTATS_PLUGIN_ID <= 0) {
            getLogger().info("bStats metrics disabled (no service id set).");
            return;
        }
        final Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("banned_keys",
                () -> String.valueOf(config.bannedKeyCount())));
        metrics.addCustomChart(new SimplePie("level_caps",
                () -> String.valueOf(config.levelCapCount())));
        metrics.addCustomChart(new SimplePie("compensation_enabled",
                () -> String.valueOf(config.isCompensationEnabled())));
        metrics.addCustomChart(new SimplePie("ban_all_curses",
                () -> String.valueOf(config.isBanAllCurses())));
        metrics.addCustomChart(new SimplePie("dry_run",
                () -> String.valueOf(config.isDryRun())));
    }

    @Override
    public void onDisable() {
        getLogger().info("AntiEnchants disabled.");
    }

    /** @return the cached, reloadable config view */
    public @NotNull AntiEnchantsConfig config() {
        return config;
    }

    /** @return the strip audit trail (no-op when audit-log.enabled is off) */
    public @NotNull AuditLog auditLog() {
        return auditLog;
    }

    /** @return the strip listener (the on-demand purge for /antienchants purge lives there) */
    public @NotNull BannedEnchantmentListener stripListener() {
        return stripListener;
    }
}
