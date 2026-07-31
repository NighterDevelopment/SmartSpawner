package github.nighter.smartspawner.updates;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central registry for all YAML key renames and value migrations across plugin versions.
 *
 * <p>When a key is renamed, add a {@link YamlMigrator.Rename} entry to the matching list below. On
 * startup the migrator reads the user's old key value, writes it under the new key name and removes
 * the old key – all automatically. Migrations that a static rename can't express (conditional value
 * rewrites, pattern-based moves) live in the {@link YamlMigrator.CustomMigration} constants.</p>
 */
public final class ConfigMigrations {

    private ConfigMigrations() {}

    // ── config.yml ───────────────────────────────────────────────────────────

    /** Renames applied to {@code config.yml}. */
    public static final List<YamlMigrator.Rename> CONFIG = List.of(
            new YamlMigrator.Rename("database.standalone.host",     "database.sql.host"),
            new YamlMigrator.Rename("database.standalone.port",     "database.sql.port"),
            new YamlMigrator.Rename("database.standalone.username", "database.sql.username"),
            new YamlMigrator.Rename("database.standalone.password", "database.sql.password"),
            new YamlMigrator.Rename("database.standalone.pool.maximum-size",             "database.sql.pool.maximum-size"),
            new YamlMigrator.Rename("database.standalone.pool.minimum-idle",             "database.sql.pool.minimum-idle"),
            new YamlMigrator.Rename("database.standalone.pool.connection-timeout",       "database.sql.pool.connection-timeout"),
            new YamlMigrator.Rename("database.standalone.pool.max-lifetime",             "database.sql.pool.max-lifetime"),
            new YamlMigrator.Rename("database.standalone.pool.idle-timeout",             "database.sql.pool.idle-timeout"),
            new YamlMigrator.Rename("database.standalone.pool.keepalive-time",           "database.sql.pool.keepalive-time"),
            new YamlMigrator.Rename("database.standalone.pool.leak-detection-threshold", "database.sql.pool.leak-detection-threshold"),
            new YamlMigrator.Rename("custom_economy.enabled",                        "sell_integration.enabled"),
            new YamlMigrator.Rename("custom_economy.currency",                       "sell_integration.currency"),
            new YamlMigrator.Rename("custom_economy.coinsengine_currency",           "sell_integration.excellenteconomy_currency"),
            new YamlMigrator.Rename("custom_economy.price_source_mode",              "sell_integration.price_source_mode"),
            new YamlMigrator.Rename("custom_economy.shop_integration.enabled",           "sell_integration.shop_integration.enabled"),
            new YamlMigrator.Rename("custom_economy.shop_integration.preferred_plugin",  "sell_integration.shop_integration.preferred_plugin"),
            new YamlMigrator.Rename("custom_economy.custom_prices.enabled",              "sell_integration.custom_prices.enabled"),
            new YamlMigrator.Rename("custom_economy.custom_prices.default_price",        "sell_integration.custom_prices.default_price"),
            new YamlMigrator.Rename("sell_integration.coinsengine_currency",             "sell_integration.excellenteconomy_currency"),
            new YamlMigrator.Rename("spawner_break.auto_sell_and_claim_exp_on_break",    "spawner_break.sell_and_xp_break")
    );

    // ── activity_log.yml ─────────────────────────────────────────────────────

    /**
     * Renames applied to {@code activity_log.yml}. The Discord settings used to sit at the top level
     * of {@code discord_logging.yml}, which is now this file, so they move under {@code discord}.
     * The other half of that move, {@code config.yml}'s {@code logging} section becoming the
     * {@code file} section here, spans two files and lives in {@code ActivityLogConfigUpdater}.
     */
    public static final List<YamlMigrator.Rename> ACTIVITY_LOG = List.of(
            new YamlMigrator.Rename("enabled",          "discord.enabled"),
            new YamlMigrator.Rename("webhook_url",      "discord.webhook_url"),
            new YamlMigrator.Rename("show_player_head", "discord.show_player_head"),
            new YamlMigrator.Rename("log_all_events",   "discord.log_all_events"),
            new YamlMigrator.Rename("logged_events",    "discord.logged_events")
    );

    /**
     * Moves the per-event embed blocks of {@code activity_log.yml} into the {@code embeds} section
     * and drops their redundant {@code embed} level, so {@code SPAWNER_PLACE.embed.title} becomes
     * {@code embeds.SPAWNER_PLACE.title}.
     *
     * <p>Matched by shape rather than by event name: any top-level section holding an {@code embed}
     * child is a legacy block, which also catches events that no longer exist instead of stranding
     * them at the top level.</p>
     */
    public static final YamlMigrator.CustomMigration ACTIVITY_LOG_LAYOUT = (user, defaults) -> {
        boolean changed = false;
        for (String key : new ArrayList<>(user.getKeys(false))) {
            ConfigurationSection legacy = user.getConfigurationSection(key + ".embed");
            if (legacy == null) continue;

            String target = "embeds." + key;
            // A value already at the new path is the user's; only fill in an empty one.
            if (!user.contains(target)) {
                for (String path : legacy.getKeys(true)) {
                    if (legacy.isConfigurationSection(path)) continue;
                    user.set(target + "." + path, legacy.get(path));
                }
            }
            user.set(key, null);
            changed = true;
        }
        return changed;
    };

    // ── language files ───────────────────────────────────────────────────────

    /*
     * Every language file gets a list, empty until it needs one, so the first rename has an obvious
     * home. These matter more than the config ones: the plugin looks message keys up by name, so a
     * key renamed without an entry here leaves the owner's wording stranded under the old name while
     * the new name arrives carrying the shipped default, and their customisation quietly stops being
     * used. Renaming a whole message entry is enough, its components move with it.
     */

    /** Renames applied to {@code messages.yml}, whose keys sit at the top level. */
    public static final List<YamlMigrator.Rename> MESSAGES = List.of();

    /** Renames applied to {@code command_messages.yml}, whose keys nest under a command, for example {@code list.no_spawners_found}. */
    public static final List<YamlMigrator.Rename> COMMAND_MESSAGES = List.of();

    /** Renames applied to {@code gui.yml}. */
    public static final List<YamlMigrator.Rename> GUI = List.of();

    /** Renames applied to {@code command_gui.yml}. */
    public static final List<YamlMigrator.Rename> COMMAND_GUI = List.of();

    /** Renames applied to {@code formatting.yml}. */
    public static final List<YamlMigrator.Rename> FORMATTING = List.of();

    /** Renames applied to {@code items.yml}. */
    public static final List<YamlMigrator.Rename> ITEMS = List.of();

    /**
     * The rename list for a language file. The switch is exhaustive on purpose: a new
     * {@link LanguageUpdater.LanguageFileType} will not compile until its list is declared here.
     */
    public static List<YamlMigrator.Rename> forLanguageFile(LanguageUpdater.LanguageFileType type) {
        return switch (type) {
            case MESSAGES         -> MESSAGES;
            case COMMAND_MESSAGES -> COMMAND_MESSAGES;
            case GUI              -> GUI;
            case COMMAND_GUI      -> COMMAND_GUI;
            case FORMATTING       -> FORMATTING;
            case ITEMS            -> ITEMS;
        };
    }

    /** Value rewrites for {@code config.yml} that a plain rename can't express. */
    public static final YamlMigrator.CustomMigration CONFIG_VALUES = (user, defaults) -> {
        boolean changed = false;
        if ("COINSENGINE".equals(user.getString("sell_integration.currency"))) {
            user.set("sell_integration.currency", "EXCELLENTECONOMY");
            changed = true;
        }
        if ("DATABASE".equals(user.getString("database.mode"))) {
            user.set("database.mode", "MYSQL");
            changed = true;
        }
        // YAML storage was removed in 1.8. Existing spawners_data.yml files are imported into
        // SQLite on the next startup by YamlToDatabaseMigration.
        if ("YAML".equals(user.getString("database.mode"))) {
            user.set("database.mode", "SQLITE");
            changed = true;
        }
        return changed;
    };

    // ── gui_layouts/**/*.yml ───────────────────────────────────────────────────

    private static final Pattern LEGACY_CLICK_PATH = Pattern.compile(
            "^(slot_[^.]+(?:\\.if\\.[^.]+)?)\\."
                    + "(click|left_click|right_click|shift_left_click|shift_right_click)$");
    private static final Pattern LEGACY_SOUND_PATH = Pattern.compile(
            "^(slot_[^.]+(?:\\.if\\.[^.]+)?)\\.sound(?:\\.(success|fail))?(.*)$");

    /** Pattern-based key moves for the GUI layout files (legacy click / sound paths). */
    public static final YamlMigrator.CustomMigration GUI_LAYOUT = (user, defaults) -> {
        // A leaf 'slot_x.click' becomes the section 'slot_x.click.action', so all old paths must be
        // removed BEFORE the new (nested) paths are written, or the fresh section would be clobbered.
        record Move(String oldPath, String newPath, Object value, boolean newExists) {}

        List<Move> moves = new ArrayList<>();
        for (String path : user.getKeys(true)) {
            if (user.isConfigurationSection(path)) continue;

            String newPath = null;
            Matcher clickMatcher = LEGACY_CLICK_PATH.matcher(path);
            if (clickMatcher.matches()) {
                newPath = clickMatcher.group(1) + "." + clickMatcher.group(2) + ".action";
            } else {
                Matcher soundMatcher = LEGACY_SOUND_PATH.matcher(path);
                if (soundMatcher.matches()) {
                    String soundKey = switch (soundMatcher.group(2)) {
                        case "success" -> "sound_success";
                        case "fail" -> "sound_fail";
                        case null, default -> "sound";
                    };
                    newPath = soundMatcher.group(1) + ".click." + soundKey + soundMatcher.group(3);
                }
            }

            if (newPath == null || newPath.equals(path)) continue;
            moves.add(new Move(path, newPath, user.get(path), user.contains(newPath)));
        }

        if (moves.isEmpty()) return false;

        for (Move m : moves) user.set(m.oldPath(), null);
        for (Move m : moves) {
            // Keep a value the user already had at the new key; only fill it in when it was empty.
            if (!m.newExists() && !user.contains(m.newPath())) {
                user.set(m.newPath(), m.value());
            }
        }
        return true;
    };

    // ── language/**/items.yml ──────────────────────────────────────────────────

    private static final String[] ITEM_DEFAULT_SECTIONS = {"smart_spawner", "item_spawner", "vanilla_spawner"};

    /**
     * Tops up only the {@code <section>.default} keys of items.yml. Entity-specific overrides are left
     * untouched: a mob section deliberately omits keys so they fall back to {@code default}, so blindly
     * adding every default key there (or adding whole new mob sections) would break that inheritance.
     */
    public static final YamlMigrator.CustomMigration ITEM_DEFAULTS = (user, defaults) -> {
        if (defaults == null) return false;
        boolean changed = false;
        for (String section : ITEM_DEFAULT_SECTIONS) {
            String base = section + ".default";
            ConfigurationSection defSection = defaults.getConfigurationSection(base);
            if (defSection == null) continue;

            for (String key : defSection.getKeys(true)) {
                if (defSection.isConfigurationSection(key)) continue;
                String path = base + "." + key;
                if (!user.contains(path)) {
                    user.set(path, defaults.get(path));
                    user.setComments(path, defaults.getComments(path));
                    changed = true;
                }
            }
        }
        return changed;
    };
}
