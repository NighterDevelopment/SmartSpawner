package github.nighter.smartspawner.commands.near;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class NearSubCommand extends BaseSubCommand {

    private static final int DEFAULT_RADIUS = 50;

    private final SpawnerHighlightManager highlightManager;

    public NearSubCommand(SmartSpawner plugin, SpawnerHighlightManager highlightManager) {
        super(plugin);
        this.highlightManager = highlightManager;
    }

    @Override
    public String getName() {
        return "near";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.near";
    }

    @Override
    public String getDescription() {
        return "Highlight nearby spawners through walls";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));

        // /ss near           – scan with default radius
        builder.executes(this::execute);

        // /ss near <radius>  – scan with custom radius (1..MAX_RADIUS)
        builder.then(
                Commands.argument("radius", IntegerArgumentType.integer(1, SpawnerHighlightManager.MAX_RADIUS))
                        .executes(context ->
                                executeScan(context, IntegerArgumentType.getInteger(context, "radius")))
        );

        // /ss near cancel    – cancel active scan + remove highlights
        builder.then(
                Commands.literal("cancel")
                        .executes(this::executeCancel)
        );

        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        return executeScan(context, DEFAULT_RADIUS);
    }

    private int executeScan(CommandContext<CommandSourceStack> context, int radius) {
        CommandSender sender = context.getSource().getSender();
        logCommandExecution(context);

        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "command_player_only");
            return 0;
        }

        highlightManager.startScan(player, radius);
        return 1;
    }

    private int executeCancel(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        logCommandExecution(context);

        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "command_player_only");
            return 0;
        }

        highlightManager.cancelScan(player);
        return 1;
    }
}
