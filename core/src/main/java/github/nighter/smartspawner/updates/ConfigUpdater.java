package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;

import java.io.File;
import java.util.Map;

public class ConfigUpdater {
    private static final String VERSION_KEY = "config_version";
    private final SmartSpawner plugin;

    public ConfigUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void checkAndUpdateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        ConfigVersionService.updateFile(plugin, configFile, "config.yml", VERSION_KEY,
                this::migrateRenamedPaths);

        // Reload Bukkit's cached config after any write
        plugin.reloadConfig();
    }

    // ── Path migrations ──────────────────────────────────────────────────────

    private void migrateRenamedPaths(Map<String, Object> userValues) {
        Map<String, String> renames = Map.ofEntries(
                Map.entry("database.standalone.host",     "database.sql.host"),
                Map.entry("database.standalone.port",     "database.sql.port"),
                Map.entry("database.standalone.username", "database.sql.username"),
                Map.entry("database.standalone.password", "database.sql.password"),
                Map.entry("database.standalone.pool.maximum-size",           "database.sql.pool.maximum-size"),
                Map.entry("database.standalone.pool.minimum-idle",           "database.sql.pool.minimum-idle"),
                Map.entry("database.standalone.pool.connection-timeout",     "database.sql.pool.connection-timeout"),
                Map.entry("database.standalone.pool.max-lifetime",           "database.sql.pool.max-lifetime"),
                Map.entry("database.standalone.pool.idle-timeout",           "database.sql.pool.idle-timeout"),
                Map.entry("database.standalone.pool.keepalive-time",         "database.sql.pool.keepalive-time"),
                Map.entry("database.standalone.pool.leak-detection-threshold", "database.sql.pool.leak-detection-threshold")
        );

        for (Map.Entry<String, String> rename : renames.entrySet()) {
            String old = rename.getKey();
            String next = rename.getValue();
            if (userValues.containsKey(old) && !userValues.containsKey(next)) {
                userValues.put(next, userValues.remove(old));
                plugin.debug("Migrated config path: " + old + " → " + next);
            }
        }

        // DATABASE → MYSQL mode rename
        if ("DATABASE".equals(userValues.get("database.mode"))) {
            userValues.put("database.mode", "MYSQL");
            plugin.getLogger().info("Migrated database.mode: DATABASE → MYSQL");
        }
    }
}