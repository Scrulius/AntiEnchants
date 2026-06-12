/* AntiEnchants - Author: Scrulius (GitHub) */
package dev.scrulius.antienchants;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the published-version upgrade path: a user's <b>1.0.0</b> config (the last version that
 * shipped publicly), including their own edits, fed through {@link ConfigMerger#merge} against the
 * current bundled defaults. No regeneration should ever be needed: edited values stay, every new
 * key appears with its default and comments, and the result survives a save/load round-trip.
 */
class ConfigMergerTest {

    /**
     * The exact config.yml that v1.0.0 generated (from git, commit 9395b6b), with user edits a
     * real server might have: an extra banned key, strip-on-fish off, a disabled world, and a
     * custom comment that must survive.
     */
    private static final String USER_CONFIG_1_0_0 = """
            # ─────────────────────────────────────────────
            #  AntiEnchants - configuration
            #  Successor to AntiMending: blocks unwanted enchantments server-wide.
            #  After editing, run /antienchants reload (no restart needed).
            # ─────────────────────────────────────────────

            # Banned enchantments: removed from ANY item (inventories, picked-up items,
            # fishing, loot, mob drops) so they can never exist on the server.
            banned-enchantments:
              enabled: true

              # my custom note: we also ban telekinesis here
              keys:
                - mending
                - ecoenchants:telekinesis

              # Block ALL curses (vanishing_curse, binding_curse and any plugin curse)
              # without listing them one by one.
              ban-all-curses: true

              # Purge the player's inventory on join / inventory open / click. The click and
              # creative strips are deferred 1 tick to avoid breaking Bukkit's item tracking
              # (and duping).
              purge-player-inventories: true

              # Block PlayerItemMendEvent: XP never repairs durability (mending's effect),
              # even if an item slips through with the enchantment.
              block-xp-repair: true

              # Clean items when picked up off the ground.
              strip-on-pickup: true

              # Clean fished items.
              strip-on-fish: false

              # Clean generated loot (structure chests, loot-table fishing, etc.).
              strip-from-loot: true

              # Clean enchanted gear dropped by mobs on death.
              strip-mob-drops: true

            # Villager trade control. Cancelling on acquire prevents the recipe from ever
            # being added to the villager.
            villager-trades:
              enabled: true

              # Villagers won't acquire ANY trade whose result is a book (book, enchanted
              # book, writable/written book, knowledge book).
              block-book-trades: false

              # Villagers won't acquire trades whose result carries a banned enchantment
              # (covers non-book results too).
              block-banned-enchant-trades: true

            # Worlds where nothing is applied (list of names). Empty = applied everywhere.
            disabled-worlds:
              - creative_hub
            """;

    private YamlConfiguration defaults;
    private YamlConfiguration user;

    @BeforeEach
    void load() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in, "bundled default config.yml must be on the test classpath");
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        user = YamlConfiguration.loadConfiguration(new StringReader(USER_CONFIG_1_0_0));
    }

    @Test
    @DisplayName("1.0.0 -> current: merge adds keys and reports change")
    void mergeReportsChange() {
        assertTrue(ConfigMerger.merge(defaults, user));
    }

    @Test
    @DisplayName("user-edited 1.0.0 values are never touched")
    void preservesUserValues() {
        ConfigMerger.merge(defaults, user);

        assertEquals(java.util.List.of("mending", "ecoenchants:telekinesis"),
                user.getStringList("banned-enchantments.keys"));
        assertTrue(user.getBoolean("banned-enchantments.ban-all-curses"));
        assertFalse(user.getBoolean("banned-enchantments.strip-on-fish"));
        assertFalse(user.getBoolean("villager-trades.block-book-trades"));
        assertEquals(java.util.List.of("creative_hub"), user.getStringList("disabled-worlds"));
    }

    @Test
    @DisplayName("every new key since 1.0.0 appears with its bundled default")
    void addsAllNewKeys() {
        ConfigMerger.merge(defaults, user);

        // Additions since 1.0.0
        assertTrue(user.isList("banned-enchantments.exempt-items"));
        assertTrue(user.getBoolean("banned-enchantments.convert-empty-books"));
        assertTrue(user.getBoolean("banned-enchantments.block-at-table"));
        assertTrue(user.getBoolean("banned-enchantments.block-at-anvil"));
        assertTrue(user.isConfigurationSection("level-caps"));
        assertFalse(user.getBoolean("compensation.enabled"));
        assertEquals(java.util.List.of("EXPERIENCE_BOTTLE:4"), user.getStringList("compensation.default"));
        assertTrue(user.isConfigurationSection("compensation.per-enchant"));
        assertTrue(user.getBoolean("permission-bypass.enabled"));
        assertFalse(user.getString("messages.stripped", "").isBlank());
        assertFalse(user.getString("messages.capped", "").isBlank());
        assertFalse(user.getString("messages.compensated", "").isBlank());

        assertTrue(user.getBoolean("banned-enchantments.block-at-grindstone"));
        assertTrue(user.isConfigurationSection("per-world"));
        assertFalse(user.getBoolean("audit-log.enabled"));
        assertEquals(2048, user.getInt("audit-log.max-file-kb"));

        // 1.2.0: dry-run mode appears, off (the merge must never flip a live server to observe-only
        // OR silently leave it enforcing when the admin expected a preview — off is the only safe default).
        assertTrue(user.contains("dry-run", true));
        assertFalse(user.getBoolean("dry-run", true));
        assertTrue(user.getBoolean("banned-enchantments.block-at-smithing"));

        // Nothing in the bundled defaults is missing from the merged config.
        for (String path : defaults.getKeys(true)) {
            assertTrue(user.contains(path, true), "missing after merge: " + path);
        }
    }

    @Test
    @DisplayName("merge is idempotent (second run adds nothing)")
    void idempotent() {
        ConfigMerger.merge(defaults, user);
        assertFalse(ConfigMerger.merge(defaults, user));
    }

    @Test
    @DisplayName("save/load round-trip keeps values, new keys, and comments")
    void survivesRoundTrip() throws Exception {
        ConfigMerger.merge(defaults, user);
        final String saved = user.saveToString();

        // User's own comment survives the save (saveConfig rewrites the file).
        assertTrue(saved.contains("my custom note: we also ban telekinesis here"),
                "user comment lost on save");
        // New keys carry their comments from the bundled defaults.
        assertTrue(saved.contains("Level caps"), "comments of new keys missing");

        final YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(new StringReader(saved));
        assertEquals(java.util.List.of("mending", "ecoenchants:telekinesis"),
                reloaded.getStringList("banned-enchantments.keys"));
        assertFalse(reloaded.getBoolean("banned-enchantments.strip-on-fish"));
        assertTrue(reloaded.getBoolean("banned-enchantments.block-at-grindstone"));
        assertFalse(reloaded.getBoolean("compensation.enabled"));
        for (String path : defaults.getKeys(true)) {
            assertTrue(reloaded.contains(path, true), "missing after round-trip: " + path);
        }
        // Still idempotent against the reloaded file.
        assertFalse(ConfigMerger.merge(defaults, reloaded));
    }
}
