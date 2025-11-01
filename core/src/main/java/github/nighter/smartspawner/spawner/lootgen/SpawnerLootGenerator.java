package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.loot.LootItem;

import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SpawnerLootGenerator {
    private final SmartSpawner plugin;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final SpawnerManager spawnerManager;
    private final Random random;

    public SpawnerLootGenerator(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.random = new Random();
    }

    public LootResult generateLoot(int minMobs, int maxMobs, SpawnerData spawner) {
        int mobCount = random.nextInt(maxMobs - minMobs + 1) + minMobs;
        int totalExperience = spawner.getEntityExperienceValue() * mobCount;

        List<LootItem> validItems = spawner.getValidLootItems();

        if (validItems.isEmpty()) {
            return new LootResult(Collections.emptyList(), totalExperience);
        }

        Map<ItemStack, Integer> consolidatedLoot = new HashMap<>();

        for (LootItem lootItem : validItems) {
            int successfulDrops = 0;

            for (int i = 0; i < mobCount; i++) {
                if (random.nextDouble() * 100 <= lootItem.getChance()) {
                    successfulDrops++;
                }
            }

            if (successfulDrops > 0) {
                ItemStack prototype = lootItem.createItemStack(random);
                if (prototype != null) {
                    int totalAmount = 0;
                    for (int i = 0; i < successfulDrops; i++) {
                        totalAmount += lootItem.generateAmount(random);
                    }

                    if (totalAmount > 0) {
                        consolidatedLoot.merge(prototype, totalAmount, Integer::sum);
                    }
                }
            }
        }

        List<ItemStack> finalLoot = new ArrayList<>(consolidatedLoot.size());
        for (Map.Entry<ItemStack, Integer> entry : consolidatedLoot.entrySet()) {
            ItemStack item = entry.getKey().clone();
            item.setAmount(Math.min(entry.getValue(), item.getMaxStackSize()));
            finalLoot.add(item);

            int remaining = entry.getValue() - item.getMaxStackSize();
            while (remaining > 0) {
                ItemStack extraStack = item.clone();
                extraStack.setAmount(Math.min(remaining, item.getMaxStackSize()));
                finalLoot.add(extraStack);
                remaining -= extraStack.getAmount();
            }
        }

        return new LootResult(finalLoot, totalExperience);
    }

    public void spawnLootToSpawner(SpawnerData spawner) {
        boolean lockAcquired = spawner.getLootGenerationLock().tryLock();
        if (!lockAcquired) {
            return;
        }

        try {
            final long currentTime = System.currentTimeMillis();
            final long spawnTime;
            final int minMobs;
            final int maxMobs;
            final AtomicInteger usedSlots;
            final AtomicInteger maxSlots;
            
            spawner.getDataLock().lock();
            try {
                long lastSpawnTime = spawner.getLastSpawnTime();
                long spawnDelay = spawner.getSpawnDelay();

                if (currentTime - lastSpawnTime < spawnDelay) {
                    return;
                }

                usedSlots = new AtomicInteger(spawner.getVirtualInventory().getUsedSlots());
                maxSlots = new AtomicInteger(spawner.getMaxSpawnerLootSlots());

                if (usedSlots.get() >= maxSlots.get() && spawner.getSpawnerExp() >= spawner.getMaxStoredExp()) {
                    if (!spawner.getIsAtCapacity()) {
                        spawner.setIsAtCapacity(true);
                    }
                    return;
                }

                minMobs = spawner.getMinMobs();
                maxMobs = spawner.getMaxMobs();
                spawnTime = currentTime;
            } finally {
                spawner.getDataLock().unlock();
            }

            Scheduler.runTaskAsync(() -> {
                LootResult loot = generateLoot(minMobs, maxMobs, spawner);

                if (loot.getItems().isEmpty() && loot.getExperience() == 0) {
                    return;
                }

                Scheduler.runLocationTask(spawner.getSpawnerLocation(), () -> {
                    boolean changed = false;

                    if (loot.getExperience() > 0 && spawner.getSpawnerExp() < spawner.getMaxStoredExp()) {
                        int currentExp = spawner.getSpawnerExp();
                        int maxExp = spawner.getMaxStoredExp();
                        int newExp = Math.min(currentExp + loot.getExperience(), maxExp);

                        if (newExp != currentExp) {
                            spawner.setSpawnerExp(newExp);
                            changed = true;
                        }
                    }

                    maxSlots.set(spawner.getMaxSpawnerLootSlots());
                    usedSlots.set(spawner.getVirtualInventory().getUsedSlots());

                    if (!loot.getItems().isEmpty() && usedSlots.get() < maxSlots.get()) {
                        List<ItemStack> itemsToAdd = new ArrayList<>(loot.getItems());

                        int totalRequiredSlots = calculateRequiredSlots(itemsToAdd, spawner.getVirtualInventory());

                        if (totalRequiredSlots > maxSlots.get()) {
                            itemsToAdd = limitItemsToAvailableSlots(itemsToAdd, spawner);
                        }

                        if (!itemsToAdd.isEmpty()) {
                            spawner.addItemsAndUpdateSellValue(itemsToAdd);
                            changed = true;
                        }
                    }

                    if (!changed) {
                        return;
                    }

                    boolean updateDataLockAcquired = spawner.getDataLock().tryLock();
                    if (updateDataLockAcquired) {
                        try {
                            spawner.setLastSpawnTime(spawnTime);
                        } finally {
                            spawner.getDataLock().unlock();
                        }
                    }

                    spawner.updateCapacityStatus();
                    handleGuiUpdates(spawner);
                    spawnerManager.markSpawnerModified(spawner.getSpawnerId());
                });
            });
        } finally {
            spawner.getLootGenerationLock().unlock();
        }
    }

    private List<ItemStack> limitItemsToAvailableSlots(List<ItemStack> items, SpawnerData spawner) {
        VirtualInventory currentInventory = spawner.getVirtualInventory();
        int maxSlots = spawner.getMaxSpawnerLootSlots();

        if (currentInventory.getUsedSlots() >= maxSlots) {
            return Collections.emptyList();
        }

        Map<VirtualInventory.ItemSignature, Long> simulatedInventory = new HashMap<>(currentInventory.getConsolidatedItems());
        List<ItemStack> acceptedItems = new ArrayList<>();

        items.sort(Comparator.comparing(item -> item.getType().name()));

        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;

            Map<VirtualInventory.ItemSignature, Long> tempSimulation = new HashMap<>(simulatedInventory);
            VirtualInventory.ItemSignature sig = new VirtualInventory.ItemSignature(item);
            tempSimulation.merge(sig, (long) item.getAmount(), Long::sum);

            int slotsNeeded = calculateSlots(tempSimulation);

            if (slotsNeeded <= maxSlots) {
                acceptedItems.add(item);
                simulatedInventory = tempSimulation;
            } else {
                int maxStackSize = item.getMaxStackSize();
                long currentAmount = simulatedInventory.getOrDefault(sig, 0L);

                int remainingSlots = maxSlots - calculateSlots(simulatedInventory);
                if (remainingSlots > 0) {
                    long maxAddAmount = remainingSlots * maxStackSize - (currentAmount % maxStackSize);
                    if (maxAddAmount > 0) {
                        ItemStack partialItem = item.clone();
                        partialItem.setAmount((int) Math.min(maxAddAmount, item.getAmount()));
                        acceptedItems.add(partialItem);

                        simulatedInventory.merge(sig, (long) partialItem.getAmount(), Long::sum);
                    }
                }

                break;
            }
        }

        return acceptedItems;
    }

    private int calculateSlots(Map<VirtualInventory.ItemSignature, Long> items) {
        return items.entrySet().stream()
                .mapToInt(entry -> {
                    long amount = entry.getValue();
                    int maxStackSize = entry.getKey().getTemplateRef().getMaxStackSize();
                    return (int) ((amount + maxStackSize - 1) / maxStackSize);
                })
                .sum();
    }

    private int calculateRequiredSlots(List<ItemStack> items, VirtualInventory inventory) {
        Map<VirtualInventory.ItemSignature, Long> simulatedItems = new HashMap<>();

        if (inventory != null) {
            simulatedItems.putAll(inventory.getConsolidatedItems());
        }

        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;

            VirtualInventory.ItemSignature sig = new VirtualInventory.ItemSignature(item);
            simulatedItems.merge(sig, (long) item.getAmount(), Long::sum);
        }

        return calculateSlots(simulatedItems);
    }

    private void handleGuiUpdates(SpawnerData spawner) {
        if (plugin.getConfig().getBoolean("particle.spawner_generate_loot", true)) {
            Location loc = spawner.getSpawnerLocation();
            World world = loc.getWorld();
            if (world != null) {
                Scheduler.runLocationTask(loc, () -> {
                    world.spawnParticle(Particle.HAPPY_VILLAGER,
                            loc.clone().add(0.5, 0.5, 0.5),
                            10, 0.3, 0.3, 0.3, 0);
                });
            }
        }

        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            spawner.updateHologramData();
        }
    }
}