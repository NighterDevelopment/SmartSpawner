package github.nighter.smartspawner.commands.hologram;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.IOException;

@NullMarked
public class HologramSubCommand extends BaseSubCommand {
    private final SpawnerManager spawnerManager;

    public HologramSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.spawnerManager = plugin.getSpawnerManager();
    }

    @Override
    public String getName() {
        return "hologram";
    }

    @Override
    public String getPermission() {
        return "smartspawner.hologram";
    }

    @Override
    public String getDescription() {
        return "Toggle hologram display for spawners";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        try {
            // Toggle hologram state
            boolean newValue = !plugin.getConfig().getBoolean("hologram.enabled");

            // Load config file directly to avoid data loss
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            
            // Set new value
            config.set("hologram.enabled", newValue);
            
            // Save the config file
            config.save(configFile);
            
            // Reload plugin config to reflect changes
            plugin.reloadConfig();

            // Update all holograms
            spawnerManager.refreshAllHolograms();

            // Send message to player using MessageService
            String messageKey = newValue ? "command_hologram_enabled" : "command_hologram_disabled";
            plugin.getMessageService().sendMessage(sender, messageKey);

            return 1;
        } catch (IOException e) {
            plugin.getLogger().severe("Error saving config: " + e.getMessage());
            sendError(sender, "An error occurred while saving the configuration");
            return 0;
        } catch (Exception e) {
            plugin.getLogger().severe("Error toggling holograms: " + e.getMessage());
            sendError(sender, "An error occurred while toggling holograms");
            return 0;
        }
    }
}
