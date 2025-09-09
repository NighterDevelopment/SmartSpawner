package github.nighter.smartspawner.spawner.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerSellEvent;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.loot.LootItem;
import github.nighter.smartspawner.Scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class SpawnerSellManager {
    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerGuiViewManager spawnerGuiViewManager;

    public SpawnerSellManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
    }

    /**
     * Sells all items from the spawner's virtual inventory
     * This method is async-optimized and handles large inventories efficiently
     */
    public void sellAllItems(Player player, SpawnerData spawner) {
        // Try to acquire lock for thread safety
        boolean lockAcquired = spawner.getLock().tryLock();
        if (!lockAcquired) {
            messageService.sendMessage(player, "sale_failed");
            return;
        }

        try {
            VirtualInventory virtualInv = spawner.getVirtualInventory();

            // Quick check if there are items to sell
            if (virtualInv.getUsedSlots() == 0) {
                messageService.sendMessage(player, "no_items");
                return;
            }

            // Get all items for async processing
            Map<VirtualInventory.ItemSignature, Long> consolidatedItems = virtualInv.getConsolidatedItems();

            // Process selling async to avoid blocking main thread
            Scheduler.runTaskAsync(() -> {
                SellResult result = calculateSellValue(consolidatedItems, spawner);

                // Store the result in SpawnerData for later access
                spawner.setLastSellResult(result);

                // Return to main thread for inventory operations and player interaction
                Scheduler.runLocationTask(spawner.getSpawnerLocation(), () -> {
                    processSellResult(player, spawner, result);
                });
            });

        } finally {
            spawner.getLock().unlock();
        }
    }


    /**
     * Process the sell result on the main thread
     */
    private void processSellResult(Player player, SpawnerData spawner, SellResult sellResult) {
        // Re-acquire lock for final operations
        boolean finalLockAcquired = spawner.getLock().tryLock();
        if (!finalLockAcquired) {
            messageService.sendMessage(player, "sale_failed");
            return;
        }

        try {
            VirtualInventory virtualInv = spawner.getVirtualInventory();

            // Double-check that we still have items and they match what we calculated
            if (!sellResult.isSuccessful()) {
                messageService.sendMessage(player, "no_sellable_items");
                return;
            }

            // Validate that we can still remove the calculated items before depositing money
            if (!virtualInv.canRemoveItems(sellResult.getItemsToRemove())) {
                messageService.sendMessage(player, "sell_failed_items_changed");
                return;
            }

            // Perform the actual sale
            double amount = sellResult.getTotalValue();
            if(SpawnerSellEvent.getHandlerList().getRegisteredListeners().length != 0) {
                SpawnerSellEvent event = new SpawnerSellEvent(player, spawner.getSpawnerLocation(), sellResult.getItemsToRemove(), amount);
                Bukkit.getPluginManager().callEvent(event);
                if(event.isCancelled()) return;
                if(event.getMoneyAmount() >= 0) amount = event.getMoneyAmount();
            }
            boolean depositSuccess = plugin.getItemPriceManager()
                    .deposit(amount, player);

            if (!depositSuccess) {
                messageService.sendMessage(player, "sell_failed");
                return;
            }

            // Remove sold items from virtual inventory - this should succeed since we validated above
            boolean removalSuccess = virtualInv.removeItems(sellResult.getItemsToRemove());
            if (!removalSuccess) {
                // This should not happen since we validated above, but handle it defensively
                // Attempt to refund the money
                plugin.getItemPriceManager().withdraw(amount, player);
                messageService.sendMessage(player, "sell_failed_items_changed");
                return;
            }

            // Update spawner state
            spawner.updateHologramData();

            // Update capacity status if needed
            if (spawner.getIsAtCapacity() &&
                    virtualInv.getUsedSlots() < spawner.getMaxSpawnerLootSlots()) {
                spawner.setIsAtCapacity(false);
            }

            // Update GUI viewers
            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
            player.closeInventory();

            // Send success message
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", plugin.getLanguageManager().formatNumber(sellResult.getItemsSold()));
            placeholders.put("price", plugin.getLanguageManager().formatNumber(amount));
            messageService.sendMessage(player, "sell_success", placeholders);

            // Mark spawner as modified for saving
            plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());

            // Update the result as successful after processing
            spawner.markLastSellAsProcessed();

        } finally {
            spawner.getLock().unlock();
        }
    }

    /**
     * Get the current sell value without actually selling (preview)
     */
    public SellResult previewSellValue(SpawnerData spawner) {
        boolean lockAcquired = spawner.getLock().tryLock();
        if (!lockAcquired) {
            return SellResult.empty();
        }

        try {
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            if (virtualInv.getUsedSlots() == 0) {
                return SellResult.empty();
            }

            Map<VirtualInventory.ItemSignature, Long> consolidatedItems =
                    new HashMap<>(virtualInv.getConsolidatedItems());

            return calculateSellValue(consolidatedItems, spawner);
        } finally {
            spawner.getLock().unlock();
        }
    }

    /**
     * Calculates the total sell value of items asynchronously
     * This method processes large inventories efficiently without blocking
     */
    private SellResult calculateSellValue(Map<VirtualInventory.ItemSignature, Long> consolidatedItems,
                                          SpawnerData spawner) {
        double totalValue = 0.0;
        long totalItemsSold = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        // Get valid loot items for price lookup
        List<LootItem> allLootItems = spawner.getLootConfig().getAllItems();

        // Create durability-ignorant price cache for efficiency
        Map<String, Double> priceCache = createDurabilityIgnorantPriceCache(allLootItems);

        // Process each item type in the inventory
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : consolidatedItems.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            // Find the price for this item using the new cache system
            double itemPrice = findItemPriceByKey(template, priceCache);

            // Skip items with no sell value
            if (itemPrice <= 0.0) {
                continue;
            }

            // Calculate total value for this item type
            double itemTotalValue = itemPrice * amount;
            totalValue += itemTotalValue;
            totalItemsSold += amount;

            // Create ItemStacks to remove (handle stacking properly)
            long remainingAmount = amount;
            while (remainingAmount > 0) {
                ItemStack stackToRemove = template.clone();
                int stackSize = (int) Math.min(remainingAmount, template.getMaxStackSize());
                stackToRemove.setAmount(stackSize);
                itemsToRemove.add(stackToRemove);
                remainingAmount -= stackSize;
            }
        }

        return new SellResult(totalValue, totalItemsSold, itemsToRemove);
    }

    /**
     * Finds the sell price for an item using the key-based price cache
     * This method is optimized for handling durability differences efficiently
     */
    private double findItemPriceByKey(ItemStack item, Map<String, Double> priceCache) {
        if (item == null) {
            return 0.0;
        }

        String itemKey = createItemKey(item);
        Double price = priceCache.get(itemKey);

        return price != null ? price : 0.0;
    }

    /**
     * Create a price cache that groups items by their base type
     * This is more efficient for large inventories with many durability variants
     */
    private Map<String, Double> createDurabilityIgnorantPriceCache(List<LootItem> validLootItems) {
        Map<String, Double> cache = new HashMap<>();

        for (LootItem lootItem : validLootItems) {
            if (lootItem.getSellPrice() > 0.0) {
                ItemStack template = lootItem.createItemStack(new Random());
                if (template != null) {
                    String key = createItemKey(template);
                    cache.put(key, lootItem.getSellPrice());
                }
            }
        }

        return cache;
    }

    /**
     * Creates a unique key for an item that ignores durability
     */
    private String createItemKey(ItemStack item) {
        if (item == null) {
            return "null";
        }

        StringBuilder key = new StringBuilder();
        key.append(item.getType().name());

        // Add enchantments if present
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            key.append("_enchants:");
            item.getItemMeta().getEnchants().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(enchantment -> enchantment.getKey().toString())))
                    .forEach(entry -> key.append(entry.getKey().getKey()).append(":").append(entry.getValue()).append(","));
        }

        // Add custom model data if present
        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            key.append("_cmd:").append(item.getItemMeta().getCustomModelData());
        }

        // Add display name if present
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            key.append("_name:").append(item.getItemMeta().getDisplayName());
        }

        return key.toString();
    }
}