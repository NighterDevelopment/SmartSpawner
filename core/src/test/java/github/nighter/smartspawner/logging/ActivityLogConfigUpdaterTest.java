package github.nighter.smartspawner.logging;

import github.nighter.smartspawner.updates.ConfigMigrations;
import github.nighter.smartspawner.updates.YamlMigrator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static github.nighter.smartspawner.ConfigFixtures.read;
import static github.nighter.smartspawner.ConfigFixtures.resource;
import static github.nighter.smartspawner.ConfigFixtures.silentLogger;
import static github.nighter.smartspawner.ConfigFixtures.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.8.0 move of {@code discord_logging.yml} to {@code activity_log.yml}, and of the
 * {@code logging} section of {@code config.yml} into that file's {@code file} section.
 */
class ActivityLogConfigUpdaterTest {

    @TempDir
    Path folder;

    private static final String LEGACY_LOGGING_SECTION = """
            logging:
              enabled: true
              json_format: false
              console_output: false
              max_log_files: 10
              max_log_size_mb: 10
              log_all_events: false
              logged_events:
                - SPAWNER_PLACE
                - SPAWNER_BREAK
            """;

    private static final String LEGACY_DISCORD_FILE = """
            enabled: true
            webhook_url: 'https://discord.com/api/webhooks/mine'
            show_player_head: true
            log_all_events: false
            logged_events:
              - SPAWNER_BREAK
            SPAWNER_PLACE:
              embed:
                title: 'Spawner placed'
                color: '#00FF00'
            """;

    @Test
    @DisplayName("discord_logging.yml is renamed to activity_log.yml")
    void legacyFileIsRenamed() {
        write(folder, "config.yml", "language: en_US\n");
        write(folder, "discord_logging.yml", LEGACY_DISCORD_FILE);

        ActivityLogConfigUpdater.importLegacySources(folder.toFile(), silentLogger());

        assertFalse(Files.exists(folder.resolve("discord_logging.yml")));
        assertTrue(Files.exists(folder.resolve("activity_log.yml")));
        assertEquals("https://discord.com/api/webhooks/mine",
                read(folder, "activity_log.yml").getString("webhook_url"),
                "the keys are moved under 'discord' by the migrator afterwards, not by the rename");
    }

    @Test
    @DisplayName("the logging section of config.yml becomes the file section, and is dropped from config.yml")
    void loggingSectionMovesIntoTheFileSection() {
        write(folder, "config.yml", "language: en_US\n" + LEGACY_LOGGING_SECTION + "debug: false\n");

        assertTrue(ActivityLogConfigUpdater.importLegacySources(folder.toFile(), silentLogger()));

        YamlConfiguration activityLog = read(folder, "activity_log.yml");
        assertTrue(activityLog.getBoolean("file.enabled"));
        assertEquals(10, activityLog.getInt("file.max_log_files"));
        assertEquals(java.util.List.of("SPAWNER_PLACE", "SPAWNER_BREAK"),
                activityLog.getStringList("file.logged_events"));

        YamlConfiguration config = read(folder, "config.yml");
        assertFalse(config.contains("logging"));
        assertEquals("en_US", config.getString("language"));
    }

    @Test
    @DisplayName("the Discord keys move under 'discord' and the per-event blocks under 'embeds'")
    void discordKeysAndEmbedsAreRelocated() {
        write(folder, "activity_log.yml", LEGACY_DISCORD_FILE);

        YamlMigrator.migrate(folder.resolve("activity_log.yml").toFile(), null,
                ConfigMigrations.ACTIVITY_LOG, ConfigMigrations.ACTIVITY_LOG_LAYOUT, silentLogger());

        YamlConfiguration result = read(folder, "activity_log.yml");
        assertTrue(result.getBoolean("discord.enabled"));
        assertEquals("https://discord.com/api/webhooks/mine", result.getString("discord.webhook_url"));
        assertEquals(java.util.List.of("SPAWNER_BREAK"), result.getStringList("discord.logged_events"));
        assertEquals("Spawner placed", result.getString("embeds.SPAWNER_PLACE.title"),
                "the redundant 'embed' level is dropped in the move");
        assertEquals("#00FF00", result.getString("embeds.SPAWNER_PLACE.color"));
        assertFalse(result.contains("SPAWNER_PLACE"));
        assertFalse(result.contains("webhook_url"));
    }

    @Test
    @DisplayName("a pre-1.8.0 file is detected by shape, a converted one is not")
    void legacyLayoutIsDetectedByShape() {
        write(folder, "legacy.yml", LEGACY_DISCORD_FILE);
        write(folder, "converted.yml", "discord:\n  enabled: true\n");

        assertTrue(ActivityLogConfigUpdater.isLegacyLayout(folder.resolve("legacy.yml").toFile()));
        assertFalse(ActivityLogConfigUpdater.isLegacyLayout(folder.resolve("converted.yml").toFile()),
                "matching a converted file would rewrite it on every single start");
    }

    @Test
    @DisplayName("the one-off rebuild keeps the user's values and the shipped comments")
    void rebuildKeepsValuesAndTakesShippedComments() {
        write(folder, "activity_log.yml", "discord:\n  enabled: true\n  webhook_url: 'mine'\n");
        String bundled = """
                discord:
                  # Turn Discord messages on.
                  enabled: false
                  webhook_url: ''
                file:
                  enabled: true
                """;

        ActivityLogConfigUpdater.rebuildFromDefaults(
                folder.resolve("activity_log.yml").toFile(), resource(bundled), silentLogger());

        YamlConfiguration result = read(folder, "activity_log.yml");
        assertTrue(result.getBoolean("discord.enabled"), "the user's value wins over the default");
        assertEquals("mine", result.getString("discord.webhook_url"));
        assertTrue(result.getBoolean("file.enabled"), "keys only in the defaults are filled in");
        assertEquals(java.util.List.of("Turn Discord messages on."), result.getComments("discord.enabled"));
    }

    @Test
    @DisplayName("running it again changes nothing")
    void isIdempotent() {
        write(folder, "config.yml", "language: en_US\n" + LEGACY_LOGGING_SECTION);
        write(folder, "discord_logging.yml", LEGACY_DISCORD_FILE);

        ActivityLogConfigUpdater.importLegacySources(folder.toFile(), silentLogger());
        String afterFirst = readActivityLog();

        assertFalse(ActivityLogConfigUpdater.importLegacySources(folder.toFile(), silentLogger()));
        assertEquals(afterFirst, readActivityLog());
    }

    private String readActivityLog() {
        try {
            return Files.readString(folder.resolve("activity_log.yml"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
