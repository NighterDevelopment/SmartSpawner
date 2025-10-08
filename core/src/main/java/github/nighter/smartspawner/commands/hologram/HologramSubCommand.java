package github.nighter.smartspawner.commands.hologram;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

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

            // Set new value in config
            plugin.getConfig().set("hologram.enabled", newValue);

            // Save the config (this only saves the current config, it doesn't create a new one)
            plugin.saveConfig();

            // Update all holograms
            spawnerManager.refreshAllHolograms();

            // Send message to player using MessageService
            String messageKey = newValue ? "command_hologram_enabled" : "command_hologram_disabled";
            plugin.getMessageService().sendMessage(sender, messageKey);

            return 1;
        } catch (Exception e) {
            plugin.getLogger().severe("Error toggling holograms: " + e.getMessage());
            sendError(sender, "An error occurred while toggling holograms");
            return 0;
        }
    }
}
