package me.nighter.smartSpawner.commands;

import me.nighter.smartSpawner.SmartSpawner;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SmartSpawnerCommand implements CommandExecutor, TabCompleter {
    private final ReloadCommand reloadCommand;
    private final GiveSpawnerCommand giveCommand;
    private final SpawnerListCommand listCommand;
    private final SmartSpawner plugin;

    public SmartSpawnerCommand(SmartSpawner plugin) {
        this.plugin = plugin;
        this.reloadCommand = new ReloadCommand(plugin);
        this.giveCommand = new GiveSpawnerCommand(plugin);
        this.listCommand = new SpawnerListCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getLanguageManager().getMessageWithPrefix("command.usage"));
            return true;
        }

        if(sender instanceof ConsoleCommandSender && args[0].equalsIgnoreCase("give")) {
            return giveCommand.executeCommand(args);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return reloadCommand.onCommand(sender, command, label, args);
            case "give":
                return giveCommand.executeCommand(sender, args);
            case "list":
                listCommand.openWorldSelectionGUI(player);
                return true;
            default:
                sender.sendMessage(plugin.getLanguageManager().getMessageWithPrefix("command.usage"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("smartspawner.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("smartspawner.give")) {
                completions.add("give");
            }
            if (sender.hasPermission("smartspawner.list")) {
                completions.add("list");
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Handle subcommand tab completions
        if (args[0].equalsIgnoreCase("reload")) {
            return reloadCommand.onTabComplete(sender, command, alias, args);
        } else if (args[0].equalsIgnoreCase("give")) {
            return giveCommand.tabComplete(sender, args);
        }

        return Collections.emptyList();
    }
}
