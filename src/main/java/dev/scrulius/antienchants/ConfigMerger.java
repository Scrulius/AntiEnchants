/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Adds config keys that exist in the bundled default {@code config.yml} but are missing from the
 * user's file (with their comments), without touching values the user already edited. Lets old
 * installs pick up new options on update instead of silently falling back to code defaults.
 */
public final class ConfigMerger {

    private ConfigMerger() {
    }

    public static void addMissingKeys(@NotNull JavaPlugin plugin) {
        final InputStream resource = plugin.getResource("config.yml");
        if (resource == null) {
            return;
        }
        final YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(resource, StandardCharsets.UTF_8));
        final FileConfiguration current = plugin.getConfig();
        boolean changed = false;
        // getKeys(true) yields parents before children, so sections exist before their leaves.
        for (String path : defaults.getKeys(true)) {
            if (current.contains(path, true)) {
                continue;
            }
            if (defaults.isConfigurationSection(path)) {
                current.createSection(path);
            } else {
                current.set(path, defaults.get(path));
            }
            current.setComments(path, defaults.getComments(path));
            changed = true;
        }
        if (changed) {
            plugin.saveConfig();
            plugin.getLogger().info("config.yml updated with new options (existing values untouched).");
        }
    }
}
