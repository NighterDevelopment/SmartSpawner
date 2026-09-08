package github.nighter.smartspawner.benchmark;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.storage.session.StorageSession;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.*;

public class StoragePerformanceBenchmark {
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INT_F = new DecimalFormat("#,##0");

    public static void runBenchmark(SmartSpawner plugin, CommandSender sender) {
        sender.sendMessage(Component.text("§e[SmartSpawner Benchmark] §aStarting comprehensive storage performance test..."));

        List<String> reportLines = new ArrayList<>();
        log(sender, reportLines, "===============================================================================");
        log(sender, reportLines, "                SMARTSPAWNER STORAGE PERFORMANCE BENCHMARK");
        log(sender, reportLines, "===============================================================================");
        log(sender, reportLines, "Server: Paper " + Bukkit.getMinecraftVersion() + " | Java: " + System.getProperty("java.version"));
        log(sender, reportLines, "Date: " + new Date());
        log(sender, reportLines, "OS: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        log(sender, reportLines, "-------------------------------------------------------------------------------");

        Location loc = Bukkit.getWorlds().isEmpty() ? null : new Location(Bukkit.getWorlds().get(0), 0, 100, 0);

        // Run Test Suite
        benchmarkDisplayPageGeneration(plugin, sender, reportLines, loc);
        benchmarkTakeOperations(plugin, sender, reportLines, loc);
        benchmarkConcurrentOperations(plugin, sender, reportLines, loc);

        log(sender, reportLines, "===============================================================================");
        log(sender, reportLines, "                         BENCHMARK COMPLETED");
        log(sender, reportLines, "===============================================================================");

        // Save report to file
        saveReportToFile(reportLines);
        sender.sendMessage(Component.text("§e[SmartSpawner Benchmark] §aBenchmark finished! Report saved to benchmark_results.txt"));
    }

    private static void log(CommandSender sender, List<String> reportLines, String line) {
        reportLines.add(line);
        sender.sendMessage(Component.text(line));
    }

    private static List<Material> getValidItemMaterials() {
        List<Material> list = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isItem() && !m.isAir() && !m.name().startsWith("LEGACY_")) {
                list.add(m);
            }
        }
        return list;
    }

    // ==============================================================================================
    // BENCHMARK 1: Display Page Generation across multiple inventory scales
    // ==============================================================================================
    private static void benchmarkDisplayPageGeneration(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 1: Display Page Materialization vs Cached Session");
        log(sender, reportLines, "Measures display retrieval throughput and latency across different inventory sizes.");
        log(sender, reportLines, String.format("%-32s | %-12s | %-12s | %-12s | %-14s | %-10s", 
                "Inventory Scale", "Mode", "Avg (μs)", "P95 (μs)", "Throughput (op/s)", "Speedup"));
        log(sender, reportLines, "---------------------------------------------------------------------------------------------------------");

        List<Material> allMaterials = getValidItemMaterials();
        int[] itemCounts = {5, 45, 200, 1000};
        String[] scaleLabels = {
                "Small (5 types / 320 items)",
                "Medium (45 types / 2,880 items)",
                "Large (200 types / 12.8k items)",
                "Massive (1k types / 64k items)"
        };

        for (int i = 0; i < itemCounts.length; i++) {
            int count = itemCounts[i];
            String label = scaleLabels[i];

            SpawnerData spawner = new SpawnerData("bench_disp_" + count, loc, EntityType.ZOMBIE, plugin);
            spawner.setMaxSpawnerLootSlots(count * 64);
            VirtualInventory vi = spawner.getVirtualInventory();

            // Populate items
            for (int k = 0; k < count; k++) {
                Material m = allMaterials.get(k % allMaterials.size());
                vi.addItem(new ItemStack(m, 64), 64);
            }

            int iterations = count <= 200 ? 10000 : 3000;

            // Warmup
            for (int w = 0; w < 500; w++) {
                vi.getDisplayPage(1, 45);
            }

            // Benchmark Uncached getDisplayPage (Old Path)
            long[] oldTimes = new long[iterations];
            long startOld = System.nanoTime();
            for (int it = 0; it < iterations; it++) {
                long t0 = System.nanoTime();
                vi.getDisplayPage(1, 45);
                oldTimes[it] = System.nanoTime() - t0;
            }
            long totalOldNs = System.nanoTime() - startOld;
            double avgOldUs = (totalOldNs / 1000.0) / iterations;
            Arrays.sort(oldTimes);
            double p95OldUs = oldTimes[(int) (iterations * 0.95)] / 1000.0;
            double oldOpsPerSec = iterations / (totalOldNs / 1_000_000_000.0);

            // Benchmark Cached StorageSession (New Path)
            StorageSession session = new StorageSession(plugin, spawner);
            var page1 = vi.getDisplayPage(1, 45);
            ItemStack[] initialSlots = new ItemStack[45];
            for (var entry : page1.int2ObjectEntrySet()) {
                if (entry.getIntKey() < 45) {
                    initialSlots[entry.getIntKey()] = entry.getValue();
                }
            }
            session.setPageSlots(1, initialSlots);

            // Warmup session
            for (int w = 0; w < 500; w++) {
                session.getPageSlots(1);
            }

            long[] newTimes = new long[iterations];
            long startNew = System.nanoTime();
            for (int it = 0; it < iterations; it++) {
                long t0 = System.nanoTime();
                session.getPageSlots(1);
                newTimes[it] = System.nanoTime() - t0;
            }
            long totalNewNs = System.nanoTime() - startNew;
            double avgNewUs = (totalNewNs / 1000.0) / iterations;
            Arrays.sort(newTimes);
            double p95NewUs = newTimes[(int) (iterations * 0.95)] / 1000.0;
            double newOpsPerSec = iterations / (totalNewNs / 1_000_000_000.0);

            double speedup = avgOldUs / Math.max(0.001, avgNewUs);

            log(sender, reportLines, String.format("%-32s | %-12s | %-12s | %-12s | %-14s | %-10s",
                    label, "Old (Uncached)", DF.format(avgOldUs), DF.format(p95OldUs), INT_F.format(oldOpsPerSec), "1.0x"));
            log(sender, reportLines, String.format("%-32s | %-12s | %-12s | %-12s | %-14s | %-10s",
                    "", "New (Session)", DF.format(avgNewUs), DF.format(p95NewUs), INT_F.format(newOpsPerSec), DF.format(speedup) + "x"));
        }
    }

    // ==============================================================================================
    // BENCHMARK 2: Take Operation Cycle (1,000 player takes)
    // ==============================================================================================
    private static void benchmarkTakeOperations(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 2: Item Take Cycle (1,000 Consecutive Takes)");
        log(sender, reportLines, "Compares old full-page repaint + auto-compact vs new native take + diff reconciliation.");

        List<Material> materials = getValidItemMaterials();
        final int TAKE_OPS = 1000;
        ItemStack dummyButton = new ItemStack(Material.GOLD_BLOCK);

        // -------------------------------------------------------------
        // Test A: Old Path (Simulates handleItemSlotClick + updateDisplay)
        // -------------------------------------------------------------
        SpawnerData spawnerOld = new SpawnerData("bench_take_old", loc, EntityType.ZOMBIE, plugin);
        spawnerOld.setMaxSpawnerLootSlots(45 * 64 * 10);
        VirtualInventory viOld = spawnerOld.getVirtualInventory();
        for (int s = 0; s < 45; s++) {
            viOld.addItem(new ItemStack(materials.get(s % materials.size()), 64), 64);
        }
        Inventory invOld = Bukkit.createInventory(null, 54, Component.text("Old Storage"));
        // Initial populate
        for (var entry : viOld.getDisplayPage(1, 45).int2ObjectEntrySet()) {
            invOld.setItem(entry.getIntKey(), entry.getValue());
        }

        long[] latenciesOld = new long[TAKE_OPS];
        long slotWritesOld = 0;
        long startOld = System.nanoTime();

        for (int op = 0; op < TAKE_OPS; op++) {
            long t0 = System.nanoTime();
            int slot = op % 45;
            ItemStack current = invOld.getItem(slot);
            if (current != null && current.getAmount() > 0) {
                // 1. Debit item from VirtualInventory
                ItemStack removedItem = current.clone();
                removedItem.setAmount(1);
                Map<ItemSignature, Long> debited = Map.of(VirtualInventory.getSignature(removedItem), 1L);
                spawnerOld.removeItemsAndUpdateSellValue(debited);

                // 2. Full Repaint (Old updateDisplay): clear all 45 slots
                for (int s = 0; s < 45; s++) {
                    invOld.setItem(s, null);
                    slotWritesOld++;
                }

                // 3. Materialize page and write items (items auto-compact forward!)
                var pageItems = viOld.getDisplayPage(1, 45);
                for (var entry : pageItems.int2ObjectEntrySet()) {
                    if (entry.getIntKey() < 45) {
                        invOld.setItem(entry.getIntKey(), entry.getValue());
                        slotWritesOld++;
                    }
                }

                // 4. Update dynamic buttons
                invOld.setItem(49, dummyButton);
                slotWritesOld++;
            }
            latenciesOld[op] = System.nanoTime() - t0;
        }
        long totalOldNs = System.nanoTime() - startOld;

        // -------------------------------------------------------------
        // Test B: New Path (Native Take + reconcileStoragePage + deferred compact)
        // -------------------------------------------------------------
        SpawnerData spawnerNew = new SpawnerData("bench_take_new", loc, EntityType.ZOMBIE, plugin);
        spawnerNew.setMaxSpawnerLootSlots(45 * 64 * 10);
        VirtualInventory viNew = spawnerNew.getVirtualInventory();
        for (int s = 0; s < 45; s++) {
            viNew.addItem(new ItemStack(materials.get(s % materials.size()), 64), 64);
        }
        Inventory invNew = Bukkit.createInventory(null, 54, Component.text("New Storage"));
        StorageSession session = new StorageSession(plugin, spawnerNew);
        ItemStack[] initialSlots = new ItemStack[45];
        for (var entry : viNew.getDisplayPage(1, 45).int2ObjectEntrySet()) {
            if (entry.getIntKey() < 45) {
                initialSlots[entry.getIntKey()] = entry.getValue();
                invNew.setItem(entry.getIntKey(), entry.getValue());
            }
        }
        session.setPageSlots(1, initialSlots);

        long[] latenciesNew = new long[TAKE_OPS];
        long slotWritesNew = 0;
        long startNew = System.nanoTime();

        for (int op = 0; op < TAKE_OPS; op++) {
            long t0 = System.nanoTime();
            int slot = op % 45;
            ItemStack current = invNew.getItem(slot);
            if (current != null && current.getAmount() > 0) {
                // 1. Native take simulation: client takes 1 item directly
                if (current.getAmount() == 1) {
                    invNew.setItem(slot, null);
                } else {
                    current.setAmount(current.getAmount() - 1);
                }
                slotWritesNew++;

                // 2. reconcileStoragePage: diff current 45 slots against session
                ItemStack[] currentSlots = new ItemStack[45];
                List<ItemStack> currList = new ArrayList<>(45);
                for (int s = 0; s < 45; s++) {
                    ItemStack it = invNew.getItem(s);
                    if (it != null && it.getType() != Material.AIR && it.getAmount() > 0) {
                        currentSlots[s] = it.clone();
                        currList.add(it.clone());
                    }
                }

                ItemStack[] prevSlots = session.getPageSlots(1);
                List<ItemStack> prevList = new ArrayList<>(45);
                if (prevSlots != null) {
                    for (ItemStack prev : prevSlots) {
                        if (prev != null && prev.getType() != Material.AIR && prev.getAmount() > 0) {
                            prevList.add(prev.clone());
                        }
                    }
                }

                session.setPageSlots(1, currentSlots);

                for (ItemStack curr : currList) {
                    for (ItemStack prev : prevList) {
                        if (curr.getAmount() > 0 && prev.getAmount() > 0 && curr.isSimilar(prev)) {
                            int matched = Math.min(curr.getAmount(), prev.getAmount());
                            curr.setAmount(curr.getAmount() - matched);
                            prev.setAmount(prev.getAmount() - matched);
                        }
                    }
                }

                Map<ItemSignature, Long> removed = new HashMap<>();
                for (ItemStack prev : prevList) {
                    if (prev.getAmount() > 0) {
                        removed.merge(VirtualInventory.getSignature(prev), (long) prev.getAmount(), Long::sum);
                    }
                }

                if (!removed.isEmpty()) {
                    spawnerNew.removeItemsAndUpdateSellValue(removed);
                }

                // 3. Update sell button only (item slots are NEVER repainted!)
                invNew.setItem(49, dummyButton);
                slotWritesNew++;
            }
            latenciesNew[op] = System.nanoTime() - t0;
        }
        long totalNewNs = System.nanoTime() - startNew;

        // End session (deferred compacting)
        session.endSession();

        // Calculate statistics
        Arrays.sort(latenciesOld);
        Arrays.sort(latenciesNew);

        double avgOldUs = (totalOldNs / 1000.0) / TAKE_OPS;
        double p50OldUs = latenciesOld[(int) (TAKE_OPS * 0.50)] / 1000.0;
        double p95OldUs = latenciesOld[(int) (TAKE_OPS * 0.95)] / 1000.0;
        double p99OldUs = latenciesOld[(int) (TAKE_OPS * 0.99)] / 1000.0;
        double maxOldUs = latenciesOld[TAKE_OPS - 1] / 1000.0;
        double throughputOld = TAKE_OPS / (totalOldNs / 1_000_000_000.0);

        double avgNewUs = (totalNewNs / 1000.0) / TAKE_OPS;
        double p50NewUs = latenciesNew[(int) (TAKE_OPS * 0.50)] / 1000.0;
        double p95NewUs = latenciesNew[(int) (TAKE_OPS * 0.95)] / 1000.0;
        double p99NewUs = latenciesNew[(int) (TAKE_OPS * 0.99)] / 1000.0;
        double maxNewUs = latenciesNew[TAKE_OPS - 1] / 1000.0;
        double throughputNew = TAKE_OPS / (totalNewNs / 1_000_000_000.0);

        double takeSpeedup = avgOldUs / Math.max(0.001, avgNewUs);
        double writeReduction = ((double) (slotWritesOld - slotWritesNew) / slotWritesOld) * 100.0;

        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Metric", "Old (Full Repaint)", "New (Native Take)", "Improvement"));
        log(sender, reportLines, "---------------------------------------------------------------------------------------------");
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Total Time (1000 ops)", DF.format(totalOldNs / 1_000_000.0) + " ms", DF.format(totalNewNs / 1_000_000.0) + " ms", DF.format(takeSpeedup) + "x faster"));
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Average Latency", DF.format(avgOldUs) + " μs", DF.format(avgNewUs) + " μs", DF.format(takeSpeedup) + "x faster"));
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "P50 Latency", DF.format(p50OldUs) + " μs", DF.format(p50NewUs) + " μs", DF.format(p50OldUs / Math.max(0.001, p50NewUs)) + "x"));
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "P95 Latency", DF.format(p95OldUs) + " μs", DF.format(p95NewUs) + " μs", DF.format(p95OldUs / Math.max(0.001, p95NewUs)) + "x"));
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "P99 Latency", DF.format(p99OldUs) + " μs", DF.format(p99NewUs) + " μs", DF.format(p99OldUs / Math.max(0.001, p99NewUs)) + "x"));
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Max Latency", DF.format(maxOldUs) + " μs", DF.format(maxNewUs) + " μs", "-"));
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Throughput", INT_F.format(throughputOld) + " takes/s", INT_F.format(throughputNew) + " takes/s", DF.format(throughputNew / Math.max(1, throughputOld)) + "x"));
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Inventory Slot Writes", INT_F.format(slotWritesOld), INT_F.format(slotWritesNew), "-" + DF.format(writeReduction) + "% fewer"));
    }

    // ==============================================================================================
    // BENCHMARK 3: Concurrent Operations (Loot Gen + Hopper Transfer while GUI open)
    // ==============================================================================================
    private static void benchmarkConcurrentOperations(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 3: Concurrent Operations (500 Loot Adds + 500 Hopper Takes)");
        log(sender, reportLines, "Simulates background loot generation and hopper extraction while GUI is viewed.");

        List<Material> materials = getValidItemMaterials();
        final int CYCLES = 500;

        // Old Path
        SpawnerData spawnerOld = new SpawnerData("bench_conc_old", loc, EntityType.ZOMBIE, plugin);
        Inventory invOld = Bukkit.createInventory(null, 54, Component.text("Storage"));
        long startOld = System.nanoTime();
        for (int i = 0; i < CYCLES; i++) {
            // 1. Spawner generates loot:
            Material m = materials.get(i % 10);
            spawnerOld.getVirtualInventory().addItem(new ItemStack(m, 4), 4);
            // Viewer push update (old full repaint)
            invOld.clear();
            for (var entry : spawnerOld.getVirtualInventory().getDisplayPage(1, 45).int2ObjectEntrySet()) {
                invOld.setItem(entry.getIntKey(), entry.getValue());
            }

            // 2. Hopper extracts loot:
            spawnerOld.getVirtualInventory().removeItems(Map.of(VirtualInventory.getSignature(new ItemStack(m, 1)), 1L));
            invOld.clear();
            for (var entry : spawnerOld.getVirtualInventory().getDisplayPage(1, 45).int2ObjectEntrySet()) {
                invOld.setItem(entry.getIntKey(), entry.getValue());
            }
        }
        long totalOldNs = System.nanoTime() - startOld;

        // New Path
        SpawnerData spawnerNew = new SpawnerData("bench_conc_new", loc, EntityType.ZOMBIE, plugin);
        StorageSession session = new StorageSession(plugin, spawnerNew);
        Inventory invNew = Bukkit.createInventory(null, 54, Component.text("Storage"));
        long startNew = System.nanoTime();
        for (int i = 0; i < CYCLES; i++) {
            // 1. Spawner generates loot:
            Material m = materials.get(i % 10);
            ItemStack lootStack = new ItemStack(m, 4);
            spawnerNew.getVirtualInventory().addItem(lootStack, 4);
            session.addLoot(Map.of(VirtualInventory.getSignature(lootStack), 4L));

            // 2. Hopper extracts loot:
            ItemStack hopperStack = new ItemStack(m, 1);
            spawnerNew.getVirtualInventory().removeItems(Map.of(VirtualInventory.getSignature(hopperStack), 1L));
            session.removeLoot(List.of(hopperStack));
        }
        long totalNewNs = System.nanoTime() - startNew;
        session.endSession();

        double avgOldMs = (totalOldNs / 1_000_000.0) / (CYCLES * 2);
        double avgNewMs = (totalNewNs / 1_000_000.0) / (CYCLES * 2);
        double speedup = (double) totalOldNs / Math.max(1, totalNewNs);

        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Metric", "Old (Full Repaint)", "New (Session Sync)", "Improvement"));
        log(sender, reportLines, "---------------------------------------------------------------------------------------------");
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Total Time (1000 ops)", DF.format(totalOldNs / 1_000_000.0) + " ms", DF.format(totalNewNs / 1_000_000.0) + " ms", DF.format(speedup) + "x faster"));
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Average per Operation", DF.format(avgOldMs * 1000) + " μs", DF.format(avgNewMs * 1000) + " μs", DF.format(speedup) + "x faster"));
        log(sender, reportLines, String.format("%-25s | %-16s | %-16s | %-12s", "Throughput", INT_F.format((CYCLES * 2) / (totalOldNs / 1_000_000_000.0)) + " ops/s", INT_F.format((CYCLES * 2) / (totalNewNs / 1_000_000_000.0)) + " ops/s", DF.format(speedup) + "x"));
    }

    private static void saveReportToFile(List<String> reportLines) {
        try {
            File file = new File("storage_benchmark_report.txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(file, false))) {
                for (String line : reportLines) {
                    pw.println(line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}