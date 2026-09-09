package github.nighter.smartspawner.benchmark;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.gui.storage.session.StorageSession;
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
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Storage benchmark: latency, throughput and memory for the paged spawner storage.
 *
 * <p>Two memory numbers are reported, and they answer different questions:
 * <ul>
 *   <li><b>Alloc/op</b> — bytes allocated on the calling thread per operation, read from
 *       {@code ThreadMXBean}. This is GC pressure: how much garbage one player click produces.</li>
 *   <li><b>Retained</b> — live heap held after the operation, measured as a GC-settled
 *       {@code Runtime} delta. This is what a leak shows up in.</li>
 * </ul>
 */
public class StoragePerformanceBenchmark {
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INT_F = new DecimalFormat("#,##0");
    private static final int PAGE_SIZE = StoragePageHolder.MAX_ITEMS_PER_PAGE;

    private static final ThreadMXBean THREAD_MX = resolveThreadMxBean();

    private static ThreadMXBean resolveThreadMxBean() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean sunBean
                && sunBean.isThreadAllocatedMemorySupported()) {
            sunBean.setThreadAllocatedMemoryEnabled(true);
            return bean;
        }
        return null;
    }

    /** Bytes allocated by the current thread so far, or -1 when the JVM does not expose it. */
    private static long allocatedBytes() {
        if (THREAD_MX instanceof com.sun.management.ThreadMXBean sunBean) {
            return sunBean.getCurrentThreadAllocatedBytes();
        }
        return -1L;
    }

    /** Live heap after coaxing the collector; used for retained-footprint deltas. */
    private static long settledHeap() {
        Runtime rt = Runtime.getRuntime();
        long used = Long.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            used = Math.min(used, rt.totalMemory() - rt.freeMemory());
        }
        return used;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "n/a";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return DF.format(bytes / 1024.0) + " KB";
        return DF.format(bytes / (1024.0 * 1024.0)) + " MB";
    }

    /** Latency percentiles plus allocation, derived from one timed run. */
    private record Stats(long totalNs, long[] sortedLatenciesNs, long allocBytes, int ops) {
        static Stats of(long totalNs, long[] latenciesNs, long allocBytes) {
            long[] sorted = latenciesNs.clone();
            Arrays.sort(sorted);
            return new Stats(totalNs, sorted, allocBytes, latenciesNs.length);
        }

        double avgUs() {
            return (totalNs / 1000.0) / ops;
        }

        double percentileUs(double q) {
            int idx = Math.min(sortedLatenciesNs.length - 1, (int) (sortedLatenciesNs.length * q));
            return sortedLatenciesNs[idx] / 1000.0;
        }

        double throughput() {
            return ops / (totalNs / 1_000_000_000.0);
        }

        long allocPerOp() {
            return allocBytes < 0 ? -1 : allocBytes / ops;
        }
    }

    public static void runBenchmark(SmartSpawner plugin, CommandSender sender) {
        sender.sendMessage(Component.text("§e[SmartSpawner Benchmark] §aStarting storage performance + memory test..."));

        Runtime rt = Runtime.getRuntime();
        List<String> reportLines = new ArrayList<>();
        log(sender, reportLines, "===============================================================================");
        log(sender, reportLines, "           SMARTSPAWNER STORAGE PERFORMANCE & MEMORY BENCHMARK");
        log(sender, reportLines, "===============================================================================");
        log(sender, reportLines, "Server: Paper " + Bukkit.getMinecraftVersion() + " | Java: " + System.getProperty("java.version"));
        log(sender, reportLines, "Date: " + new Date());
        log(sender, reportLines, "OS: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        log(sender, reportLines, "Heap: max " + formatBytes(rt.maxMemory()) + " | allocation tracking: "
                + (THREAD_MX != null ? "enabled" : "unavailable"));
        log(sender, reportLines, "-------------------------------------------------------------------------------");

        Location loc = Bukkit.getWorlds().isEmpty() ? null : new Location(Bukkit.getWorlds().get(0), 0, 100, 0);

        benchmarkDisplayPageGeneration(plugin, sender, reportLines, loc);
        benchmarkTakeOperations(plugin, sender, reportLines, loc);
        benchmarkPagination(plugin, sender, reportLines, loc);
        benchmarkPageCacheFootprint(plugin, sender, reportLines, loc);
        benchmarkSortItems(plugin, sender, reportLines, loc);
        benchmarkTakeAll(plugin, sender, reportLines, loc);
        benchmarkDropPage(plugin, sender, reportLines, loc);
        benchmarkConcurrentOperations(plugin, sender, reportLines, loc);

        log(sender, reportLines, "");
        log(sender, reportLines, "===============================================================================");
        log(sender, reportLines, "                         BENCHMARK COMPLETED");
        log(sender, reportLines, "===============================================================================");

        saveReportToFile(reportLines);
        sender.sendMessage(Component.text("§e[SmartSpawner Benchmark] §aBenchmark finished! Report saved to storage_benchmark_report.txt"));
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

    private static SpawnerData newSpawner(SmartSpawner plugin, Location loc, String id,
                                          List<Material> materials, long itemsPerType) {
        SpawnerData spawner = new SpawnerData(id, loc, EntityType.ZOMBIE, plugin);
        spawner.setMaxSpawnerLootSlots(Integer.MAX_VALUE / 2);
        VirtualInventory vi = spawner.getVirtualInventory();
        for (Material m : materials) {
            vi.addItem(new ItemStack(m, 64), itemsPerType);
        }
        return spawner;
    }

    private static Inventory paintPage(VirtualInventory vi, Inventory inv, int page) {
        for (var entry : vi.getDisplayPage(page, PAGE_SIZE).int2ObjectEntrySet()) {
            if (entry.getIntKey() < PAGE_SIZE) {
                inv.setItem(entry.getIntKey(), entry.getValue());
            }
        }
        return inv;
    }

    /** Two-column comparison table with a shared metric list. */
    private static void reportComparison(CommandSender sender, List<String> reportLines,
                                         String oldLabel, String newLabel,
                                         Stats oldStats, Stats newStats,
                                         long slotWritesOld, long slotWritesNew,
                                         String opUnit) {
        double speedup = oldStats.avgUs() / Math.max(0.001, newStats.avgUs());

        log(sender, reportLines, String.format("%-24s | %-20s | %-20s | %-14s", "Metric", oldLabel, newLabel, "Improvement"));
        log(sender, reportLines, "--------------------------------------------------------------------------------------------");
        row(sender, reportLines, "Total Time",
                DF.format(oldStats.totalNs() / 1_000_000.0) + " ms",
                DF.format(newStats.totalNs() / 1_000_000.0) + " ms",
                DF.format(speedup) + "x faster");
        row(sender, reportLines, "Average Latency",
                DF.format(oldStats.avgUs()) + " us", DF.format(newStats.avgUs()) + " us",
                DF.format(speedup) + "x faster");
        row(sender, reportLines, "P50 Latency",
                DF.format(oldStats.percentileUs(0.50)) + " us", DF.format(newStats.percentileUs(0.50)) + " us",
                DF.format(oldStats.percentileUs(0.50) / Math.max(0.001, newStats.percentileUs(0.50))) + "x");
        row(sender, reportLines, "P95 Latency",
                DF.format(oldStats.percentileUs(0.95)) + " us", DF.format(newStats.percentileUs(0.95)) + " us",
                DF.format(oldStats.percentileUs(0.95) / Math.max(0.001, newStats.percentileUs(0.95))) + "x");
        row(sender, reportLines, "P99 Latency",
                DF.format(oldStats.percentileUs(0.99)) + " us", DF.format(newStats.percentileUs(0.99)) + " us",
                DF.format(oldStats.percentileUs(0.99) / Math.max(0.001, newStats.percentileUs(0.99))) + "x");
        row(sender, reportLines, "Throughput",
                INT_F.format(oldStats.throughput()) + " " + opUnit,
                INT_F.format(newStats.throughput()) + " " + opUnit,
                DF.format(newStats.throughput() / Math.max(1, oldStats.throughput())) + "x");
        row(sender, reportLines, "Alloc / op (garbage)",
                formatBytes(oldStats.allocPerOp()), formatBytes(newStats.allocPerOp()),
                allocDelta(oldStats.allocPerOp(), newStats.allocPerOp()));
        if (slotWritesOld >= 0) {
            double writeReduction = slotWritesOld == 0 ? 0
                    : ((double) (slotWritesOld - slotWritesNew) / slotWritesOld) * 100.0;
            row(sender, reportLines, "Inventory Slot Writes",
                    INT_F.format(slotWritesOld), INT_F.format(slotWritesNew),
                    "-" + DF.format(writeReduction) + "%");
        }
    }

    private static String allocDelta(long oldPerOp, long newPerOp) {
        if (oldPerOp < 0 || newPerOp < 0) return "n/a";
        if (oldPerOp == 0) return "-";
        double reduction = ((double) (oldPerOp - newPerOp) / oldPerOp) * 100.0;
        return (reduction >= 0 ? "-" : "+") + DF.format(Math.abs(reduction)) + "%";
    }

    private static void row(CommandSender sender, List<String> reportLines, String metric, String a, String b, String c) {
        log(sender, reportLines, String.format("%-24s | %-20s | %-20s | %-14s", metric, a, b, c));
    }

    // ==============================================================================================
    // BENCHMARK 1: Display Page Generation across multiple inventory scales (up to 10M items)
    // ==============================================================================================
    private static void benchmarkDisplayPageGeneration(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 1: Display Page Materialization vs Session Buffer (up to 10M items)");
        log(sender, reportLines, "Display retrieval throughput, latency and allocation across multi-million item quantities.");
        log(sender, reportLines, String.format("%-36s | %-14s | %-10s | %-10s | %-16s | %-11s | %-8s",
                "Scale (Types / Total Items)", "Mode", "Avg (us)", "P95 (us)", "Throughput (op/s)", "Alloc/op", "Speedup"));
        log(sender, reportLines, "-------------------------------------------------------------------------------------------------------------------------");

        List<Material> allMaterials = getValidItemMaterials();
        int[] typeCounts = {5, 20, 50, 50, 50};
        long[] totalItemCounts = {1_000L, 100_000L, 1_000_000L, 5_000_000L, 10_000_000L};
        String[] scaleLabels = {
                "Small (5 types / 1,000 items)",
                "Medium (20 types / 100,000 items)",
                "Large (50 types / 1,000,000 items)",
                "Massive (50 types / 5,000,000 items)",
                "Maximum (50 types / 10,000,000 items)"
        };

        for (int i = 0; i < typeCounts.length; i++) {
            int types = typeCounts[i];
            long totalItems = totalItemCounts[i];
            List<Material> materials = allMaterials.subList(0, types);
            SpawnerData spawner = newSpawner(plugin, loc, "bench_disp_" + i, materials, totalItems / types);
            VirtualInventory vi = spawner.getVirtualInventory();

            int iterations = totalItems <= 100_000L ? 5000 : 2000;

            for (int w = 0; w < 300; w++) {
                vi.getDisplayPage(1, PAGE_SIZE);
            }

            long[] oldTimes = new long[iterations];
            long allocOld0 = allocatedBytes();
            long startOld = System.nanoTime();
            for (int it = 0; it < iterations; it++) {
                long t0 = System.nanoTime();
                vi.getDisplayPage(1, PAGE_SIZE);
                oldTimes[it] = System.nanoTime() - t0;
            }
            Stats oldStats = Stats.of(System.nanoTime() - startOld, oldTimes, allocatedBytes() - allocOld0);

            StorageSession session = new StorageSession(plugin, spawner);
            session.setPageSlots(1, sliceToArray(vi, 1));
            for (int w = 0; w < 300; w++) {
                session.getPageSlots(1);
            }

            long[] newTimes = new long[iterations];
            long allocNew0 = allocatedBytes();
            long startNew = System.nanoTime();
            for (int it = 0; it < iterations; it++) {
                long t0 = System.nanoTime();
                session.getPageSlots(1);
                newTimes[it] = System.nanoTime() - t0;
            }
            Stats newStats = Stats.of(System.nanoTime() - startNew, newTimes, allocatedBytes() - allocNew0);

            double speedup = oldStats.avgUs() / Math.max(0.001, newStats.avgUs());
            log(sender, reportLines, String.format("%-36s | %-14s | %-10s | %-10s | %-16s | %-11s | %-8s",
                    scaleLabels[i], "Old (Uncached)", DF.format(oldStats.avgUs()), DF.format(oldStats.percentileUs(0.95)),
                    INT_F.format(oldStats.throughput()), formatBytes(oldStats.allocPerOp()), "1.0x"));
            log(sender, reportLines, String.format("%-36s | %-14s | %-10s | %-10s | %-16s | %-11s | %-8s",
                    "", "New (Session)", DF.format(newStats.avgUs()), DF.format(newStats.percentileUs(0.95)),
                    INT_F.format(newStats.throughput()), formatBytes(newStats.allocPerOp()), DF.format(speedup) + "x"));

            session.endSession();
        }
    }

    private static ItemStack[] sliceToArray(VirtualInventory vi, int page) {
        ItemStack[] slots = new ItemStack[PAGE_SIZE];
        for (var entry : vi.getDisplayPage(page, PAGE_SIZE).int2ObjectEntrySet()) {
            if (entry.getIntKey() < PAGE_SIZE) {
                slots[entry.getIntKey()] = entry.getValue();
            }
        }
        return slots;
    }

    // ==============================================================================================
    // BENCHMARK 2: Item Take Operation Cycle (1,000 player takes on 10,000,000 item inventory)
    // ==============================================================================================
    private static void benchmarkTakeOperations(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 2: Item Take Cycle (1,000 Consecutive Takes at 10M Item Scale)");
        log(sender, reportLines, "Old full-page repaint + auto-compact vs live StorageSession.reconcilePage() diffing.");

        List<Material> materials = getValidItemMaterials().subList(0, 50);
        final int TAKE_OPS = 1000;
        final long ITEMS_PER_TYPE = 10_000_000L / materials.size();
        ItemStack dummyButton = new ItemStack(Material.GOLD_BLOCK);

        // Old path: debit one item, wipe all 45 slots, re-materialize the page, repaint buttons.
        SpawnerData spawnerOld = newSpawner(plugin, loc, "bench_take_old", materials, ITEMS_PER_TYPE);
        VirtualInventory viOld = spawnerOld.getVirtualInventory();
        Inventory invOld = paintPage(viOld, Bukkit.createInventory(null, 54, Component.text("Old Storage")), 1);

        long[] latenciesOld = new long[TAKE_OPS];
        long slotWritesOld = 0;
        long allocOld0 = allocatedBytes();
        long startOld = System.nanoTime();
        for (int op = 0; op < TAKE_OPS; op++) {
            long t0 = System.nanoTime();
            int slot = op % PAGE_SIZE;
            ItemStack current = invOld.getItem(slot);
            if (current != null && current.getAmount() > 0) {
                ItemStack removedItem = current.clone();
                removedItem.setAmount(1);
                spawnerOld.removeItemsAndUpdateSellValue(Map.of(VirtualInventory.getSignature(removedItem), 1L));

                for (int s = 0; s < PAGE_SIZE; s++) {
                    invOld.setItem(s, null);
                    slotWritesOld++;
                }
                for (var entry : viOld.getDisplayPage(1, PAGE_SIZE).int2ObjectEntrySet()) {
                    if (entry.getIntKey() < PAGE_SIZE) {
                        invOld.setItem(entry.getIntKey(), entry.getValue());
                        slotWritesOld++;
                    }
                }
                invOld.setItem(49, dummyButton);
                slotWritesOld++;
            }
            latenciesOld[op] = System.nanoTime() - t0;
        }
        Stats oldStats = Stats.of(System.nanoTime() - startOld, latenciesOld, allocatedBytes() - allocOld0);

        // New path: native take, then the real reconcilePage() diff. Item slots are never repainted.
        SpawnerData spawnerNew = newSpawner(plugin, loc, "bench_take_new", materials, ITEMS_PER_TYPE);
        VirtualInventory viNew = spawnerNew.getVirtualInventory();
        Inventory invNew = paintPage(viNew, Bukkit.createInventory(null, 54, Component.text("New Storage")), 1);
        StorageSession session = new StorageSession(plugin, spawnerNew);
        session.setPageSlots(1, sliceToArray(viNew, 1));

        ItemStack[] liveSlots = new ItemStack[PAGE_SIZE];
        long[] latenciesNew = new long[TAKE_OPS];
        long slotWritesNew = 0;
        long allocNew0 = allocatedBytes();
        long startNew = System.nanoTime();
        for (int op = 0; op < TAKE_OPS; op++) {
            long t0 = System.nanoTime();
            int slot = op % PAGE_SIZE;
            ItemStack current = invNew.getItem(slot);
            if (current != null && current.getAmount() > 0) {
                if (current.getAmount() == 1) {
                    invNew.setItem(slot, null);
                } else {
                    current.setAmount(current.getAmount() - 1);
                    invNew.setItem(slot, current);
                }
                slotWritesNew++;

                for (int s = 0; s < PAGE_SIZE; s++) {
                    liveSlots[s] = invNew.getItem(s);
                }
                StorageSession.PageDiff diff = session.reconcilePage(1, liveSlots);
                if (!diff.removed().isEmpty()) {
                    spawnerNew.removeItemsAndUpdateSellValue(diff.removed());
                }
                if (!diff.added().isEmpty()) {
                    spawnerNew.addItemsAndUpdateSellValue(diff.added());
                }

                invNew.setItem(49, dummyButton);
                slotWritesNew++;
            }
            latenciesNew[op] = System.nanoTime() - t0;
        }
        Stats newStats = Stats.of(System.nanoTime() - startNew, latenciesNew, allocatedBytes() - allocNew0);
        session.endSession();

        reportComparison(sender, reportLines, "Old (Full Repaint)", "New (reconcilePage)",
                oldStats, newStats, slotWritesOld, slotWritesNew, "takes/s");
    }

    // ==============================================================================================
    // BENCHMARK 3: Pagination Navigation (500 Page Switches across 10M item inventory)
    // ==============================================================================================
    private static void benchmarkPagination(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 3: Pagination Navigation (500 Page Switches at 10M Item Scale)");
        log(sender, reportLines, "Player flipping between pages 1..10: uncached slice building vs session buffers.");

        List<Material> materials = getValidItemMaterials().subList(0, 50);
        final int SWITCHES = 500;
        final long ITEMS_PER_TYPE = 10_000_000L / materials.size();

        SpawnerData spawnerOld = newSpawner(plugin, loc, "bench_page_old", materials, ITEMS_PER_TYPE);
        VirtualInventory viOld = spawnerOld.getVirtualInventory();
        Inventory invOld = Bukkit.createInventory(null, 54, Component.text("Old Storage"));

        long[] latenciesOld = new long[SWITCHES];
        long allocOld0 = allocatedBytes();
        long startOld = System.nanoTime();
        for (int i = 0; i < SWITCHES; i++) {
            long t0 = System.nanoTime();
            var pageItems = viOld.getDisplayPage((i % 10) + 1, PAGE_SIZE);
            for (int s = 0; s < PAGE_SIZE; s++) {
                invOld.setItem(s, pageItems.get(s));
            }
            latenciesOld[i] = System.nanoTime() - t0;
        }
        Stats oldStats = Stats.of(System.nanoTime() - startOld, latenciesOld, allocatedBytes() - allocOld0);

        SpawnerData spawnerNew = newSpawner(plugin, loc, "bench_page_new", materials, ITEMS_PER_TYPE);
        Inventory invNew = Bukkit.createInventory(null, 54, Component.text("New Storage"));
        StorageSession session = new StorageSession(plugin, spawnerNew);

        long[] latenciesNew = new long[SWITCHES];
        long allocNew0 = allocatedBytes();
        long startNew = System.nanoTime();
        for (int i = 0; i < SWITCHES; i++) {
            long t0 = System.nanoTime();
            ItemStack[] slots = session.getPageSlots((i % 10) + 1);
            for (int s = 0; s < PAGE_SIZE; s++) {
                invNew.setItem(s, slots[s]);
            }
            latenciesNew[i] = System.nanoTime() - t0;
        }
        Stats newStats = Stats.of(System.nanoTime() - startNew, latenciesNew, allocatedBytes() - allocNew0);

        log(sender, reportLines, "Session page buffers held after 500 flips over 10 pages: "
                + session.getBufferedPageCount() + " (bounded by viewer count, not page count)");
        session.endSession();

        reportComparison(sender, reportLines, "Old (Uncached Slices)", "New (Session Buffers)",
                oldStats, newStats, -1, -1, "flips/s");
    }

    // ==============================================================================================
    // BENCHMARK 4: Page cache footprint — the memory-leak regression test
    // ==============================================================================================
    private static void benchmarkPageCacheFootprint(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 4: Page Cache Retained Memory (walking a huge storage page by page)");
        log(sender, reportLines, "A player paging through storage. 'Unbounded' is the per-page cache this branch started with");
        log(sender, reportLines, "(one ItemStack[45] retained per visited page); 'Bounded' is the current StorageSession.");
        log(sender, reportLines, String.format("%-16s | %-22s | %-22s | %-16s | %-14s",
                "Pages Walked", "Unbounded (retained)", "Bounded (retained)", "Buffers Held", "Reduction"));
        log(sender, reportLines, "-------------------------------------------------------------------------------------------------------");

        List<Material> materials = getValidItemMaterials().subList(0, 50);
        int[] pageCounts = {1_000, 10_000, 100_000};
        Runtime rt = Runtime.getRuntime();

        for (int pages : pageCounts) {
            // Enough items that every walked page is fully populated.
            long totalItems = (long) pages * PAGE_SIZE * 64L;
            SpawnerData spawner = newSpawner(plugin, loc, "bench_mem_" + pages, materials, totalItems / materials.size());
            VirtualInventory vi = spawner.getVirtualInventory();

            // Unbounded: retain a slot array per visited page, exactly like a Map<Integer, ItemStack[]> cache.
            long projectedBytes = (long) pages * PAGE_SIZE * 48L; // ~1 ItemStack + array slot
            long headroom = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
            String unboundedLabel;
            long unboundedRetained = -1;

            if (projectedBytes * 3 > headroom) {
                unboundedLabel = "skipped (needs ~" + formatBytes(projectedBytes) + ")";
            } else {
                long before = settledHeap();
                Map<Integer, ItemStack[]> unbounded = new HashMap<>(pages * 2);
                for (int p = 1; p <= pages; p++) {
                    unbounded.put(p, sliceToArray(vi, p));
                }
                unboundedRetained = settledHeap() - before;
                unboundedLabel = formatBytes(unboundedRetained) + " (" + INT_F.format(unbounded.size()) + " pages)";
                unbounded.clear();
            }

            // Bounded: the real session, walked over the same pages.
            long before = settledHeap();
            StorageSession session = new StorageSession(plugin, spawner);
            session.addViewer(UUID.randomUUID());
            for (int p = 1; p <= pages; p++) {
                session.getPageSlots(p);
            }
            long boundedRetained = settledHeap() - before;
            int buffers = session.getBufferedPageCount();

            String reduction = (unboundedRetained > 0 && boundedRetained >= 0)
                    ? DF.format(100.0 * (unboundedRetained - boundedRetained) / unboundedRetained) + "%"
                    : "n/a";

            log(sender, reportLines, String.format("%-16s | %-22s | %-22s | %-16s | %-14s",
                    INT_F.format(pages), unboundedLabel, formatBytes(Math.max(0, boundedRetained)),
                    buffers + " page(s)", reduction));

            session.endSession();
        }
    }

    // ==============================================================================================
    // BENCHMARK 5: Sort Items Operation (sort_items across 1M, 5M, and 10M scales)
    // ==============================================================================================
    private static void benchmarkSortItems(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 5: Sort Items Operation (Cycling Preferred Sort Item)");
        log(sender, reportLines, "Stream comparator re-sorting and display projection across multi-million item inventories.");
        log(sender, reportLines, String.format("%-28s | %-14s | %-10s | %-10s | %-16s | %-11s | %-8s",
                "Scale (50 item types)", "Mode", "Avg (us)", "P95 (us)", "Throughput (/s)", "Alloc/op", "Speedup"));
        log(sender, reportLines, "-------------------------------------------------------------------------------------------------------------------");

        List<Material> materials = getValidItemMaterials().subList(0, 50);
        long[] scales = {1_000_000L, 5_000_000L, 10_000_000L};
        String[] labels = {"1,000,000 items (1M)", "5,000,000 items (5M)", "10,000,000 items (10M)"};
        final int SORTS = 200;

        for (int idx = 0; idx < scales.length; idx++) {
            long itemsPerType = scales[idx] / materials.size();

            SpawnerData spawnerOld = newSpawner(plugin, loc, "bench_sort_old_" + idx, materials, itemsPerType);
            VirtualInventory viOld = spawnerOld.getVirtualInventory();
            Inventory invOld = Bukkit.createInventory(null, 54, Component.text("Old Storage"));

            long[] latenciesOld = new long[SORTS];
            long allocOld0 = allocatedBytes();
            long startOld = System.nanoTime();
            for (int it = 0; it < SORTS; it++) {
                long t0 = System.nanoTime();
                viOld.sortItems(materials.get(it % materials.size()));
                var page = viOld.getDisplayPage(1, PAGE_SIZE);
                for (int s = 0; s < PAGE_SIZE; s++) {
                    invOld.setItem(s, page.get(s));
                }
                latenciesOld[it] = System.nanoTime() - t0;
            }
            Stats oldStats = Stats.of(System.nanoTime() - startOld, latenciesOld, allocatedBytes() - allocOld0);

            SpawnerData spawnerNew = newSpawner(plugin, loc, "bench_sort_new_" + idx, materials, itemsPerType);
            VirtualInventory viNew = spawnerNew.getVirtualInventory();
            Inventory invNew = Bukkit.createInventory(null, 54, Component.text("New Storage"));
            StorageSession session = new StorageSession(plugin, spawnerNew);

            long[] latenciesNew = new long[SORTS];
            long allocNew0 = allocatedBytes();
            long startNew = System.nanoTime();
            for (int it = 0; it < SORTS; it++) {
                long t0 = System.nanoTime();
                viNew.sortItems(materials.get(it % materials.size()));
                session.resetPages(); // what sort_items does now: drop buffers, keep the session
                ItemStack[] slots = session.getPageSlots(1);
                for (int s = 0; s < PAGE_SIZE; s++) {
                    invNew.setItem(s, slots[s]);
                }
                latenciesNew[it] = System.nanoTime() - t0;
            }
            Stats newStats = Stats.of(System.nanoTime() - startNew, latenciesNew, allocatedBytes() - allocNew0);
            session.endSession();

            double speedup = oldStats.avgUs() / Math.max(0.001, newStats.avgUs());
            log(sender, reportLines, String.format("%-28s | %-14s | %-10s | %-10s | %-16s | %-11s | %-8s",
                    labels[idx], "Old (Uncached)", DF.format(oldStats.avgUs()), DF.format(oldStats.percentileUs(0.95)),
                    INT_F.format(oldStats.throughput()), formatBytes(oldStats.allocPerOp()), "1.0x"));
            log(sender, reportLines, String.format("%-28s | %-14s | %-10s | %-10s | %-16s | %-11s | %-8s",
                    "", "New (Session)", DF.format(newStats.avgUs()), DF.format(newStats.percentileUs(0.95)),
                    INT_F.format(newStats.throughput()), formatBytes(newStats.allocPerOp()), DF.format(speedup) + "x"));
        }
    }

    // ==============================================================================================
    // BENCHMARK 6: "Take All" Page Operation (take_all at 10M scale)
    // ==============================================================================================
    private static void benchmarkTakeAll(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 6: \"Take All\" Page Operation (200 ops at 10M Item Scale)");
        log(sender, reportLines, "Extracting all displayed items from page 1 into a player inventory.");

        List<Material> materials = getValidItemMaterials().subList(0, 50);
        final int OPS = 200;
        final long ITEMS_PER_TYPE = 10_000_000L / materials.size();

        SpawnerData spawnerOld = newSpawner(plugin, loc, "bench_takeall_old", materials, ITEMS_PER_TYPE);
        VirtualInventory viOld = spawnerOld.getVirtualInventory();
        Inventory invOld = paintPage(viOld, Bukkit.createInventory(null, 54, Component.text("Old Storage")), 1);

        long[] latenciesOld = new long[OPS];
        long slotWritesOld = 0;
        long allocOld0 = allocatedBytes();
        long startOld = System.nanoTime();
        for (int it = 0; it < OPS; it++) {
            long t0 = System.nanoTime();
            List<ItemStack> taken = new ArrayList<>(36);
            for (int s = 0; s < 36; s++) {
                ItemStack item = invOld.getItem(s);
                if (item != null) taken.add(item.clone());
            }
            spawnerOld.removeItemsAndUpdateSellValue(taken);

            for (int s = 0; s < PAGE_SIZE; s++) {
                invOld.setItem(s, null);
                slotWritesOld++;
            }
            for (var entry : viOld.getDisplayPage(1, PAGE_SIZE).int2ObjectEntrySet()) {
                if (entry.getIntKey() < PAGE_SIZE) {
                    invOld.setItem(entry.getIntKey(), entry.getValue());
                    slotWritesOld++;
                }
            }
            latenciesOld[it] = System.nanoTime() - t0;
        }
        Stats oldStats = Stats.of(System.nanoTime() - startOld, latenciesOld, allocatedBytes() - allocOld0);

        SpawnerData spawnerNew = newSpawner(plugin, loc, "bench_takeall_new", materials, ITEMS_PER_TYPE);
        VirtualInventory viNew = spawnerNew.getVirtualInventory();
        Inventory invNew = paintPage(viNew, Bukkit.createInventory(null, 54, Component.text("New Storage")), 1);
        StorageSession session = new StorageSession(plugin, spawnerNew);
        session.setPageSlots(1, sliceToArray(viNew, 1));

        long[] latenciesNew = new long[OPS];
        long slotWritesNew = 0;
        long allocNew0 = allocatedBytes();
        long startNew = System.nanoTime();
        for (int it = 0; it < OPS; it++) {
            long t0 = System.nanoTime();
            List<ItemStack> taken = new ArrayList<>(36);
            for (int s = 0; s < 36; s++) {
                ItemStack item = invNew.getItem(s);
                if (item != null) {
                    taken.add(item.clone());
                    invNew.setItem(s, null);
                    slotWritesNew++;
                }
            }
            spawnerNew.removeItemsAndUpdateSellValue(taken);

            ItemStack[] currentSlots = new ItemStack[PAGE_SIZE];
            for (int s = 0; s < PAGE_SIZE; s++) {
                ItemStack it2 = invNew.getItem(s);
                currentSlots[s] = it2 != null ? it2.clone() : null;
            }
            session.adoptPageSlots(1, currentSlots);

            latenciesNew[it] = System.nanoTime() - t0;
        }
        Stats newStats = Stats.of(System.nanoTime() - startNew, latenciesNew, allocatedBytes() - allocNew0);
        session.endSession();

        reportComparison(sender, reportLines, "Old (Full Repaint)", "New (Session Adopt)",
                oldStats, newStats, slotWritesOld, slotWritesNew, "ops/s");
    }

    // ==============================================================================================
    // BENCHMARK 7: "Drop Page" Operation (drop_page at 10M scale)
    // ==============================================================================================
    private static void benchmarkDropPage(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 7: \"Drop Page\" Operation (200 ops at 10M Item Scale)");
        log(sender, reportLines, "Extracting all 45 slots of a page and debiting the count-map. The old path re-materializes");
        log(sender, reportLines, "page 1 so items compact forward; the new path blanks the page buffer (gaps are kept while");
        log(sender, reportLines, "viewing) and repaints, then the player pages on. Both include re-seeding page 1 for the next");
        log(sender, reportLines, "iteration so the two loops do identical amounts of real work.");

        List<Material> materials = getValidItemMaterials().subList(0, 50);
        final int OPS = 200;
        final long ITEMS_PER_TYPE = 10_000_000L / materials.size();

        SpawnerData spawnerOld = newSpawner(plugin, loc, "bench_drop_old", materials, ITEMS_PER_TYPE);
        VirtualInventory viOld = spawnerOld.getVirtualInventory();
        Inventory invOld = Bukkit.createInventory(null, 54, Component.text("Old Storage"));

        long[] latenciesOld = new long[OPS];
        long allocOld0 = allocatedBytes();
        long startOld = System.nanoTime();
        for (int it = 0; it < OPS; it++) {
            long t0 = System.nanoTime();
            List<ItemStack> dropped = new ArrayList<>(PAGE_SIZE);
            for (int s = 0; s < PAGE_SIZE; s++) {
                ItemStack item = invOld.getItem(s);
                if (item != null) dropped.add(item.clone());
            }
            if (!dropped.isEmpty()) {
                spawnerOld.removeItemsAndUpdateSellValue(dropped);
            }
            for (int s = 0; s < PAGE_SIZE; s++) invOld.setItem(s, null);
            paintPage(viOld, invOld, 1);
            latenciesOld[it] = System.nanoTime() - t0;
        }
        Stats oldStats = Stats.of(System.nanoTime() - startOld, latenciesOld, allocatedBytes() - allocOld0);

        SpawnerData spawnerNew = newSpawner(plugin, loc, "bench_drop_new", materials, ITEMS_PER_TYPE);
        Inventory invNew = Bukkit.createInventory(null, 54, Component.text("New Storage"));
        StorageSession session = new StorageSession(plugin, spawnerNew);

        long[] latenciesNew = new long[OPS];
        long allocNew0 = allocatedBytes();
        long startNew = System.nanoTime();
        for (int it = 0; it < OPS; it++) {
            long t0 = System.nanoTime();
            List<ItemStack> dropped = new ArrayList<>(PAGE_SIZE);
            for (int s = 0; s < PAGE_SIZE; s++) {
                ItemStack item = invNew.getItem(s);
                if (item != null) dropped.add(item.clone());
            }
            if (!dropped.isEmpty()) {
                spawnerNew.removeItemsAndUpdateSellValue(dropped);
            }
            // What handleDropPageItems does: blank the buffered page, then repaint from it.
            session.adoptPageSlots(1, new ItemStack[PAGE_SIZE]);
            ItemStack[] blanked = session.getPageSlots(1);
            for (int s = 0; s < PAGE_SIZE; s++) invNew.setItem(s, blanked[s]);

            // The player then pages on, which projects the next page from the count-map.
            session.setPageSlots(1, null);
            ItemStack[] nextSlots = session.getPageSlots(1);
            for (int s = 0; s < PAGE_SIZE; s++) invNew.setItem(s, nextSlots[s]);
            latenciesNew[it] = System.nanoTime() - t0;
        }
        Stats newStats = Stats.of(System.nanoTime() - startNew, latenciesNew, allocatedBytes() - allocNew0);
        session.endSession();

        reportComparison(sender, reportLines, "Old (Full Repaint)", "New (Session Refresh)",
                oldStats, newStats, -1, -1, "drops/s");
    }

    // ==============================================================================================
    // BENCHMARK 8: Concurrent Operations (500 Loot Adds + 500 Hopper Takes at 10M scale)
    // ==============================================================================================
    private static void benchmarkConcurrentOperations(SmartSpawner plugin, CommandSender sender, List<String> reportLines, Location loc) {
        log(sender, reportLines, "");
        log(sender, reportLines, "### BENCHMARK 8: Concurrent Operations (500 Loot Adds + 500 Hopper Takes at 10M Scale)");
        log(sender, reportLines, "Background loot generation and hopper extraction while the GUI is open.");

        List<Material> materials = getValidItemMaterials().subList(0, 50);
        final int CYCLES = 500;
        final long ITEMS_PER_TYPE = 10_000_000L / materials.size();

        SpawnerData spawnerOld = newSpawner(plugin, loc, "bench_conc_old", materials, ITEMS_PER_TYPE);
        VirtualInventory viOld = spawnerOld.getVirtualInventory();
        Inventory invOld = Bukkit.createInventory(null, 54, Component.text("Storage"));

        long[] latenciesOld = new long[CYCLES * 2];
        long allocOld0 = allocatedBytes();
        long startOld = System.nanoTime();
        for (int i = 0; i < CYCLES; i++) {
            Material m = materials.get(i % 10);

            long t0 = System.nanoTime();
            viOld.addItem(new ItemStack(m, 4), 4);
            invOld.clear();
            paintPage(viOld, invOld, 1);
            latenciesOld[i * 2] = System.nanoTime() - t0;

            long t1 = System.nanoTime();
            viOld.removeItems(Map.of(VirtualInventory.getSignature(new ItemStack(m, 1)), 1L));
            invOld.clear();
            paintPage(viOld, invOld, 1);
            latenciesOld[i * 2 + 1] = System.nanoTime() - t1;
        }
        Stats oldStats = Stats.of(System.nanoTime() - startOld, latenciesOld, allocatedBytes() - allocOld0);

        SpawnerData spawnerNew = newSpawner(plugin, loc, "bench_conc_new", materials, ITEMS_PER_TYPE);
        VirtualInventory viNew = spawnerNew.getVirtualInventory();
        StorageSession session = new StorageSession(plugin, spawnerNew);
        // A viewer must be registered for addLoot/removeLoot to do any work at all.
        session.addViewer(UUID.randomUUID());
        session.getPageSlots(1);

        long[] latenciesNew = new long[CYCLES * 2];
        long allocNew0 = allocatedBytes();
        long startNew = System.nanoTime();
        for (int i = 0; i < CYCLES; i++) {
            Material m = materials.get(i % 10);
            ItemStack lootStack = new ItemStack(m, 4);

            long t0 = System.nanoTime();
            viNew.addItem(lootStack, 4);
            session.addLoot(Map.of(VirtualInventory.getSignature(lootStack), 4L));
            latenciesNew[i * 2] = System.nanoTime() - t0;

            ItemStack hopperStack = new ItemStack(m, 1);
            long t1 = System.nanoTime();
            viNew.removeItems(Map.of(VirtualInventory.getSignature(hopperStack), 1L));
            session.removeLoot(List.of(hopperStack));
            latenciesNew[i * 2 + 1] = System.nanoTime() - t1;
        }
        Stats newStats = Stats.of(System.nanoTime() - startNew, latenciesNew, allocatedBytes() - allocNew0);
        session.endSession();

        reportComparison(sender, reportLines, "Old (Full Repaint)", "New (Session Sync)",
                oldStats, newStats, -1, -1, "ops/s");
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
