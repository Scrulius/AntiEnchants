/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants;

import dev.scrulius.antienchants.EnchantStripper.StripResult;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Plain-text audit trail of player-context strips ({@code plugins/AntiEnchants/strips.log}) so
 * staff can answer "the plugin stole my X" claims. One line per operation:
 * <pre>2026-06-10 22:10:01 | PICKUP | Steve @ world | removed: minecraft:mending(1) | capped: 2</pre>
 * Gated by {@code audit-log.enabled}; the line is built on the main thread (it reads the player)
 * and written async; rotates to {@code strips.log.1} past {@code audit-log.max-file-kb}.
 */
public final class AuditLog {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AntiEnchantsPlugin plugin;
    private final Object ioLock = new Object();

    public AuditLog(@NotNull AntiEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Records what a strip pass did to a player's items. No-op when the audit log is disabled. */
    public void log(@NotNull String context, @NotNull Player player, @NotNull StripResult result) {
        if (!plugin.config().isAuditEnabled() || !result.changed()) {
            return;
        }
        final StringBuilder sb = new StringBuilder(96);
        sb.append(LocalDateTime.now().format(TIMESTAMP))
                .append(" | ").append(context)
                .append(" | ").append(player.getName())
                .append(" @ ").append(player.getWorld().getName());
        if (!result.removed().isEmpty()) {
            sb.append(" | removed:");
            for (StripResult.Removed removed : result.removed()) {
                sb.append(' ').append(removed.enchantment().getKey())
                        .append('(').append(removed.level()).append(')');
            }
        }
        if (result.capped() > 0) {
            sb.append(" | capped: ").append(result.capped());
        }
        final String line = sb.toString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> append(line));
    }

    private void append(@NotNull String line) {
        synchronized (ioLock) {
            try {
                final File file = new File(plugin.getDataFolder(), "strips.log");
                rotateIfNeeded(file);
                Files.writeString(file.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().warning("audit-log: could not write strips.log: " + e.getMessage());
            }
        }
    }

    private void rotateIfNeeded(@NotNull File file) throws IOException {
        final int maxKb = plugin.config().getAuditMaxFileKb();
        if (maxKb <= 0 || !file.exists() || file.length() <= maxKb * 1024L) {
            return;
        }
        Files.move(file.toPath(), file.toPath().resolveSibling("strips.log.1"),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
