package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerEventType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages per-event Discord embed configurations.
 *
 * <h3>Performance design</h3>
 * <ul>
 *   <li>Nothing is loaded when Discord is disabled – the manager simply isn't created.</li>
 *   <li>On startup (enabled=true) only the files for events in {@code logged_events} are
 *       extracted from the bundled defaults; other event files are left untouched.</li>
 *   <li>Individual configs are loaded <em>lazily</em> on first use and cached in an
 *       {@link EnumMap} for O(1) lookup thereafter.</li>
 * </ul>
 */
public class DiscordEmbedConfigManager {

    private static final String EVENTS_FOLDER     = "discord/events";
    private static final String DEFAULTS_RESOURCE  = "discord_logging.yml";
    private static final String DEFAULTS_SECTION   = "event_defaults";

    private final SmartSpawner plugin;
    private final DiscordWebhookConfig config;

    /** Lazy cache – populated on first access per event type. */
    private final EnumMap<SpawnerEventType, DiscordEventEmbedConfig> cache =
            new EnumMap<>(SpawnerEventType.class);

    public DiscordEmbedConfigManager(SmartSpawner plugin, DiscordWebhookConfig config) {
        this.plugin  = plugin;
        this.config  = config;

        // Extract missing default files only for the events we will actually use
        File eventsDir = new File(plugin.getDataFolder(), EVENTS_FOLDER);
        eventsDir.mkdirs();
        extractMissingDefaults(eventsDir);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the {@link DiscordEventEmbedConfig} for the given event type.
     * The config is loaded on first call and cached for all subsequent calls.
     *
     * @param eventType the event whose embed config is needed
     * @return never {@code null}; falls back to {@link DiscordEventEmbedConfig#defaults()} on error
     */
    public DiscordEventEmbedConfig getEmbedConfig(SpawnerEventType eventType) {
        DiscordEventEmbedConfig cached = cache.get(eventType);
        if (cached != null) return cached;

        // Load on demand – only for events that are actually being processed
        DiscordEventEmbedConfig loaded = loadEventConfig(eventType);
        cache.put(eventType, loaded);
        return loaded;
    }

    /** Clears the cache so configs are reloaded from disk on next access. */
    public void invalidateCache() {
        cache.clear();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private DiscordEventEmbedConfig loadEventConfig(SpawnerEventType eventType) {
        File eventsDir = new File(plugin.getDataFolder(), EVENTS_FOLDER);
        File eventFile = new File(eventsDir, eventType.name() + ".yml");

        if (!eventFile.exists()) {
            extractDefaultForEvent(eventType, eventFile, eventsDir);
        }

        return DiscordEventEmbedConfig.fromFile(eventFile);
    }

    /** Extracts only the event files that are missing AND in the active event set. */
    private void extractMissingDefaults(File eventsDir) {
        Set<SpawnerEventType> active = config.isLogAllEvents()
                ? EnumSet.allOf(SpawnerEventType.class)
                : config.getEnabledEvents();

        for (SpawnerEventType event : active) {
            File eventFile = new File(eventsDir, event.name() + ".yml");
            if (!eventFile.exists()) {
                extractDefaultForEvent(event, eventFile, eventsDir);
            }
        }
    }

    /**
     * Reads the event's section from {@code discord_logging.yml} under
     * {@code event_defaults.<EVENT_NAME>} and writes a formatted, commented
     * file to the plugin data folder.
     */
    private void extractDefaultForEvent(SpawnerEventType eventType, File dest, File eventsDir) {
        try (InputStream in = plugin.getResource(DEFAULTS_RESOURCE)) {
            if (in == null) {
                plugin.getLogger().warning(DEFAULTS_RESOURCE + " not found in JAR");
                return;
            }

            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));

            // Templates live under event_defaults.<EVENT_NAME> in discord_logging.yml
            ConfigurationSection eventDefaults = defaults.getConfigurationSection(DEFAULTS_SECTION);
            if (eventDefaults == null) {
                plugin.getLogger().warning(DEFAULTS_RESOURCE + ": missing '" + DEFAULTS_SECTION + "' section");
                return;
            }
            ConfigurationSection section = eventDefaults.getConfigurationSection(eventType.name());
            if (section == null) {
                plugin.debug("No default embed found for event: " + eventType.name());
                return;
            }

            writeEventFile(eventType, section, dest);
            plugin.debug("Extracted discord embed config: discord/events/" + dest.getName());

        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to extract discord embed config for " + eventType.name(), e);
        }
    }

    /**
     * Serialises {@code section} to a {@code .yml} file prefixed with a helpful comment header.
     */
    private void writeEventFile(SpawnerEventType eventType,
                                ConfigurationSection section,
                                File dest) throws IOException {
        // Build YAML body from the section
        FileConfiguration out = new YamlConfiguration();
        copySection(section, out, "");
        String yamlBody = out.saveToString();

        // Build comment header
        StringBuilder sb = new StringBuilder();
        sb.append("# ─────────────────────────────────────────────────────────────────────────\n");
        sb.append("#  Discord Embed Configuration – ").append(eventType.name()).append('\n');
        sb.append("#  ").append(eventType.getDescription()).append('\n');
        sb.append("# ─────────────────────────────────────────────────────────────────────────\n");
        sb.append("#\n");
        sb.append("#  embed_format:\n");
        sb.append("#    yaml  – Use the embed: block below (easy, structured)\n");
        sb.append("#    json  – Use embed_json: for a raw Discord webhook JSON payload\n");
        sb.append("#\n");
        sb.append("#  Common placeholders (both modes):\n");
        sb.append("#    {player}       – Player name  (N/A for console/explosion events)\n");
        sb.append("#    {player_uuid}  – Player UUID\n");
        sb.append("#    {description}  – Event description\n");
        sb.append("#    {event_type}   – ").append(eventType.name()).append('\n');
        sb.append("#    {time}         – HH:mm:ss\n");
        sb.append("#    {timestamp}    – ISO 8601 (Discord native date)\n");
        sb.append("#    {location}     – world (x, y, z)\n");
        sb.append("#    {world} {x} {y} {z}\n");
        sb.append("#    {entity}       – Mob / entity name\n");
        sb.append("#    {color}        – Decimal colour int for this event\n");
        appendEventMetadata(sb, eventType);
        sb.append("# ─────────────────────────────────────────────────────────────────────────\n");
        sb.append('\n');
        sb.append(yamlBody);

        dest.getParentFile().mkdirs();
        Files.write(dest.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Appends event-specific metadata placeholder hints to the comment header. */
    private void appendEventMetadata(StringBuilder sb, SpawnerEventType type) {
        String extra = switch (type) {
            case SPAWNER_PLACE         -> "#    {quantity}      – Number of spawners placed\n";
            case SPAWNER_BREAK         -> "#    {quantity}      – Number of spawners broken\n";
            case SPAWNER_EXPLODE       -> "#    {quantity}      – Number of spawners destroyed\n";
            case SPAWNER_STACK_HAND,
                 SPAWNER_STACK_GUI     -> "#    {amount_added}  – Spawners added\n" +
                                          "#    {old_stack_size} / {new_stack_size}\n";
            case SPAWNER_EXP_CLAIM     -> "#    {exp_amount}    – XP collected\n";
            case SPAWNER_SELL_ALL      -> "#    {items_sold}    – Total item count\n" +
                                          "#    {total_price}   – Revenue\n";
            case SPAWNER_ITEM_TAKE_ALL -> "#    {items_taken} / {items_left}\n";
            case SPAWNER_ITEMS_SORT    -> "#    {sort_item}     – Material name used for sorting\n" +
                                          "#    {previous_sort}\n";
            case SPAWNER_DROP_PAGE_ITEMS -> "#    {items_dropped}\n" +
                                             "#    {page_number}\n";
            case SPAWNER_EGG_CHANGE    -> "#    {old_entity}    – Previous entity type\n";
            case SPAWNER_STORAGE_OPEN  -> "#    {page} / {total_pages}\n";
            case COMMAND_EXECUTE_PLAYER,
                 COMMAND_EXECUTE_CONSOLE,
                 COMMAND_EXECUTE_RCON  -> "#    {full_command}  – Full command string\n";
            default                    -> "";
        };
        if (!extra.isEmpty()) sb.append(extra);
    }

    /**
     * Recursively copies all leaf values from {@code source} into {@code target}.
     * Lists (e.g. embed.fields) are copied as-is via the {@code set()} path.
     */
    private static void copySection(ConfigurationSection source,
                                    FileConfiguration target,
                                    String prefix) {
        for (String key : source.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object val = source.get(key);
            if (val instanceof ConfigurationSection sub) {
                copySection(sub, target, fullKey);
            } else {
                target.set(fullKey, val);
            }
        }
    }
}

