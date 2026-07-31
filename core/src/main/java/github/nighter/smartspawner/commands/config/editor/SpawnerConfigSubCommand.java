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
 * {@code /ss config spawnerloot}.
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
        return "Edit mob and item spawner loot in game";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));

        // No bare /ss config: keep the intended command visible in the usage message.
        builder.executes(context -> {
            logCommandExecution(context);
            plugin.getMessageService().sendMessage(context.getSource().getSender(), "config_editor.usage");
            return 0;
        });

        builder.then(Commands.literal("spawnerloot").executes(context -> {
            logCommandExecution(context);
            return open(context);
        }));

        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        plugin.getMessageService().sendMessage(context.getSource().getSender(), "config_editor.usage");
        return 0;
    }

    private int open(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "player_only");
            return 0;
        }

        ui.openEntryList(player, ConfigEditorTarget.SMART_SPAWNER, 1);
        return 1;
    }
}
