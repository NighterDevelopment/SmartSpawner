package github.nighter.smartspawner.commands.benchmark;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.benchmark.StoragePerformanceBenchmark;
import github.nighter.smartspawner.commands.BaseSubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

public class BenchmarkSubCommand extends BaseSubCommand {

    public BenchmarkSubCommand(SmartSpawner plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "benchmark";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.benchmark";
    }

    @Override
    public String getDescription() {
        return "Runs comprehensive storage performance benchmarks comparing display and take operations.";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        StoragePerformanceBenchmark.runBenchmark(plugin, sender);
        return 1;
    }
}