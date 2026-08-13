package github.nighter.smartspawner.commands.config.editor;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.spawner.config.SpawnerConfigName;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Adds SmartSpawner entries by summon-style SNBT or ItemSpawner entries through item capture. */
@NullMarked
public class AddSpawnerSubCommand extends BaseSubCommand {
    private final ConfigEditorService service;
    private final ConfigEditorUI ui;
    private final ConfigEditorDialogs dialogs;
    private final List<String> mobSuggestions;

    public AddSpawnerSubCommand(SmartSpawner plugin, ConfigEditorService service,
                                ConfigEditorUI ui, ConfigEditorDialogs dialogs) {
        super(plugin);
        this.service = service;
        this.ui = ui;
        this.dialogs = dialogs;
        this.mobSuggestions = Arrays.stream(EntityType.values())
                .filter(EntityType::isAlive)
                .filter(EntityType::isSpawnable)
                .flatMap(type -> Stream.of(type.getKey().getKey(), type.getKey().asString()))
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public String getName() {
        return "add";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.add";
    }

    @Override
    public String getDescription() {
        return "Add a SmartSpawner or ItemSpawner entry";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));
        builder.executes(context -> {
            logCommandExecution(context);
            return usage(context.getSource().getSender());
        });

        builder.then(Commands.literal(ConfigEditorTarget.SMART_SPAWNER.getCommandArgument())
                .executes(context -> {
                    logCommandExecution(context);
                    return smartUsage(context.getSource().getSender());
                })
                .then(Commands.argument("mob", ArgumentTypes.resource(RegistryKey.ENTITY_TYPE))
                        .suggests(mobSuggestions())
                        .executes(context -> addSmartSpawner(context, null, "{}"))
                        .then(Commands.argument("options", StringArgumentType.greedyString())
                                .executes(this::addSmartSpawnerWithOptions))));

        builder.then(Commands.literal(ConfigEditorTarget.ITEM_SPAWNER.getCommandArgument())
                .executes(context -> openItemCapture(context, null))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> openItemCapture(context,
                                StringArgumentType.getString(context, "name")))));
        return builder;
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        return usage(context.getSource().getSender());
    }

    private SuggestionProvider<CommandSourceStack> mobSuggestions() {
        return (context, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            mobSuggestions.stream().filter(value -> value.startsWith(remaining)).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private int addSmartSpawnerWithOptions(CommandContext<CommandSourceStack> context) {
        String options = StringArgumentType.getString(context, "options").trim();
        int nbtStart = options.indexOf('{');
        if (nbtStart == 0) return addSmartSpawner(context, null, options);
        String name = nbtStart < 0 ? options : options.substring(0, nbtStart).trim();
        String nbt = nbtStart < 0 ? "{}" : options.substring(nbtStart).trim();
        return addSmartSpawner(context, name, nbt);
    }

    private int addSmartSpawner(CommandContext<CommandSourceStack> context, String requestedName, String nbt) {
        logCommandExecution(context);
        CommandSender sender = context.getSource().getSender();
        EntityType type = context.getArgument("mob", EntityType.class);
        if (!type.isAlive() || !type.isSpawnable()) {
            plugin.getMessageService().sendMessage(sender, "config_editor.invalid_mob",
                    Map.of("mob", type.getKey().asString()));
            return 0;
        }

        if (!validEntityNbt(type, nbt)) {
            plugin.getMessageService().sendMessage(sender, "config_editor.invalid_nbt");
            return 0;
        }

        String key = requestedName == null || requestedName.isBlank()
                ? SpawnerConfigName.defaultName(type.name()) : SpawnerConfigName.normalize(requestedName);
        if (!service.createMobEntry(key, type, nbt)) {
            plugin.getMessageService().sendMessage(sender, "config_editor.new_entry_exists",
                    Map.of("entry", key));
            return 0;
        }

        plugin.getMessageService().sendMessage(sender, "config_editor.new_entry_created",
                Map.of("entry", key));
        if (sender instanceof Player player) {
            dialogs.openEntryOptions(player, ConfigEditorTarget.SMART_SPAWNER, key, 1);
        }
        return 1;
    }

    private int openItemCapture(CommandContext<CommandSourceStack> context, String requestedName) {
        logCommandExecution(context);
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            plugin.getMessageService().sendMessage(sender, "player_only");
            return 0;
        }
        ui.openItemCapture(player, new ItemCaptureHolder(ConfigEditorTarget.ITEM_SPAWNER,
                ItemCaptureHolder.Purpose.NEW_ENTRY, null, null, 1, requestedName));
        return 1;
    }

    /** Paper parses the same SNBT representation used by vanilla entity commands, without spawning it. */
    private boolean validEntityNbt(EntityType type, String nbt) {
        if (nbt.length() < 2 || nbt.charAt(0) != '{' || nbt.charAt(nbt.length() - 1) != '}') {
            return false;
        }
        String body = nbt.substring(1, nbt.length() - 1).trim();
        String fullNbt = "{id:\"" + type.getKey().asString() + "\""
                + (body.isEmpty() ? "" : "," + body) + "}";
        try {
            EntitySnapshot snapshot = Bukkit.getServer().getEntityFactory().createEntitySnapshot(fullNbt);
            return snapshot.getEntityType() == type;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private int usage(CommandSender sender) {
        plugin.getMessageService().sendMessage(sender, "config_editor.add_usage");
        return 0;
    }

    private int smartUsage(CommandSender sender) {
        plugin.getMessageService().sendMessage(sender, "config_editor.add_smartspawner_usage");
        return 0;
    }
}
