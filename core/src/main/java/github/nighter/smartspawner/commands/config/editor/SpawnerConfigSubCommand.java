package github.nighter.smartspawner.commands.config.editor;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ss config smartspawner} and {@code /ss config itemspawner}.
 *
 * <p>Opens the in-game editor for the matching settings file. Player only: the whole command is a
 * GUI, so there is nothing for console to do, and it says so rather than failing silently.</p>
 */
@NullMarked
public class SpawnerConfigSubCommand extends BaseSubCommand {

    private final ConfigEditorUI ui;

    public SpawnerConfigSubCommand(SmartSpawner plugin, ConfigEditorUI ui) {
        super(plugin);
        this.ui = ui;
    }

    @Override
    public String getName() {
        return "config";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.config";
    }

    @Override
    public String getDescription() {
        return "Edit spawner and item spawner settings in game";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));

        // No bare /ss config: without a target there is nothing to open.
        builder.executes(context -> {
            logCommandExecution(context);
            plugin.getMessageService().sendMessage(context.getSource().getSender(), "config_editor.usage");
            return 0;
        });

        for (ConfigEditorTarget target : ConfigEditorTarget.values()) {
            builder.then(Commands.literal(target.getCommandArgument()).executes(context -> {
                logCommandExecution(context);
                return open(context, target);
            }));
        }

        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        plugin.getMessageService().sendMessage(context.getSource().getSender(), "config_editor.usage");
        return 0;
    }

    private int open(CommandContext<CommandSourceStack> context, ConfigEditorTarget target) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "player_only");
            return 0;
        }

        ui.openEntryList(player, target, 1);
        return 1;
    }
}
