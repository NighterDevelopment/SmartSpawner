package github.nighter.smartspawner.commands.reload;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.synchronization.ItemUpdater;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ReloadCommand implements CommandExecutor, TabCompleter {
    private final SmartSpawner plugin;

    public ReloadCommand(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smartspawner.reload")) {
            plugin.getMessageService().sendMessage(sender,"no_permission");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (args.length == 1) {
                reloadAll(sender);
            } else {
                plugin.getMessageService().sendMessage(sender, "reload_command_usage");
            }
            return true;
        }

        return false;
    }

    private void reloadAll(CommandSender sender) {
        try {
            plugin.getMessageService().sendMessage(sender, "reload_command_start");

            // Log current cache stats for debugging
            if (plugin.getConfig().getBoolean("debug", false)) {
                logCacheStats();
            }

            // Clear all caches first to avoid using stale data during reload
            ItemUpdater.clearCache();
            plugin.getSpawnerItemFactory().clearAllCaches();
            plugin.getMessageService().clearKeyExistsCache();

            // Reload all configurations
            plugin.reloadConfig();
            plugin.refreshTimeCache();

            // Reload components in dependency order
            plugin.setUpHopperHandler();
            plugin.getEntityLootRegistry().reload();
            reloadSpawnerLootConfigs();
            plugin.getLanguageManager().reloadLanguages();

            // Reload factory AFTER its dependencies (loot registry, language manager)
            plugin.getSpawnerItemFactory().reload();
            plugin.getSpawnerManager().reloadAllHolograms();
            plugin.getSpawnerMenuAction().updateCooldownSettings();
            plugin.getRangeChecker().reload();
            plugin.reload();
            plugin.reloadStaticUI();

            // Log new cache stats after reload if in debug mode
            if (plugin.getConfig().getBoolean("debug", false)) {
                logCacheStats();
            }

            plugin.getMessageService().sendMessage(sender, "reload_command_success");
            plugin.getLogger().info("Plugin reloaded successfully by " + sender.getName());
        } catch (Exception e) {
            plugin.getMessageService().sendMessage(sender, "reload_command_error");
            plugin.getLogger().severe("Error reloading plugin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logCacheStats() {
        Map<String, Object> stats = plugin.getLanguageManager().getCacheStats();
        plugin.getLogger().info("Language cache statistics:");
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            plugin.getLogger().info("  " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private void reloadSpawnerLootConfigs() {
        List<SpawnerData> allSpawners = plugin.getSpawnerManager().getAllSpawners();
        for (SpawnerData spawner : allSpawners) {
            try {
                spawner.reloadLootConfig();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reload loot config for spawner " +
                        spawner.getSpawnerId() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("smartspawner.reload")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Collections.singletonList("reload");
        }

        return Collections.emptyList();
    }
}
