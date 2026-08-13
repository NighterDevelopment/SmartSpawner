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

/** Opens one of the two independent spawner configuration editors. */
@NullMarked
public class EditSpawnerSubCommand extends BaseSubCommand {
    private final ConfigEditorUI ui;

    public EditSpawnerSubCommand(SmartSpawner plugin, ConfigEditorUI ui) {
        super(plugin);
        this.ui = ui;
    }

    @Override
    public String getName() {
        return "edit";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.edit";
    }

    @Override
    public String getDescription() {
        return "Edit SmartSpawner or ItemSpawner settings in game";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));
        builder.executes(context -> {
            logCommandExecution(context);
            return usage(context.getSource().getSender());
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
        return usage(context.getSource().getSender());
    }

    private int usage(CommandSender sender) {
        plugin.getMessageService().sendMessage(sender, "config_editor.edit_usage");
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
