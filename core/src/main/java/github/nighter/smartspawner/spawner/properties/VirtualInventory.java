package github.nighter.smartspawner.spawner.properties;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualInventory {
    private final Map<ItemSignature, Long> consolidatedItems;
    @Getter
    private int maxSlots;
    private final Map<Integer, ItemStack> displayInventoryCache;
    private boolean displayCacheDirty;
    private int usedSlotsCache;
    private long totalItemsCache;
    private boolean metricsCacheDirty;
    // Cache sorted entries to avoid resorting when display isn't changing
    private List<Map.Entry<ItemSignature, Long>> sortedEntriesCache;
    private org.bukkit.Material preferredSortMaterial;

    public VirtualInventory(int maxSlots) {
        this.maxSlots = maxSlots;
        this.consolidatedItems = new ConcurrentHashMap<>();
        this.displayInventoryCache = new HashMap<>(maxSlots); // Pre-size the map
        this.displayCacheDirty = true;
        this.metricsCacheDirty = true;
        this.usedSlotsCache = 0;
        this.totalItemsCache = 0;
        this.sortedEntriesCache = null;
        this.preferredSortMaterial = null;
    }

    public static ItemSignature getSignature(ItemStack item) {
        return new ItemSignature(item);
    }

    // Add items in bulk with minimal operations
    public void addItems(List<ItemStack> items) {
        if (items.isEmpty()) return;

        // Pre-allocate space for batch processing
        Map<ItemSignature, Long> itemBatch = new HashMap<>(items.size());

        // Consolidate all items first
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;
            ItemSignature sig = getSignature(item); // Use cached signature
            itemBatch.merge(sig, (long) item.getAmount(), (a, b) -> a + b);
        }

        // Apply all changes in one operation
        if (!itemBatch.isEmpty()) {
            for (Map.Entry<ItemSignature, Long> entry : itemBatch.entrySet()) {
                consolidatedItems.merge(entry.getKey(), entry.getValue(), (a, b) -> a + b);
            }
            displayCacheDirty = true;
            metricsCacheDirty = true;
            sortedEntriesCache = null;
        }
    }
    /**
     * Adds an already-consolidated entry: one item template plus its total count.
     * <p>
     * Storage backends load items this way. Going through {@link #addItems(List)} would mean
     * splitting the count into max-stack-sized batches first, which costs one map merge per stack
     * and turns a single entry of a few million items into millions of merges.
     *
     * @param template the item template, its own amount is ignored
     * @param amount   how many of that item are stored, ignored when not positive
     */
    public void addConsolidatedItem(ItemStack template, long amount) {
        if (template == null || amount <= 0) return;

        consolidatedItems.merge(getSignature(template), amount, Long::sum);
        displayCacheDirty = true;
        metricsCacheDirty = true;
        sortedEntriesCache = null;
    }

    // Remove items in bulk with minimal operations
    public boolean removeItems(List<ItemStack> items) {
        if (items.isEmpty()) return true;

        Map<ItemSignature, Long> toRemove = new HashMap<>();

        // Calculate total amounts to remove in a single pass
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;
            // Use cached signature to avoid excessive cloning
            ItemSignature sig = getSignature(item);
            toRemove.merge(sig, (long) item.getAmount(), (a, b) -> a + b);
        }

        if (toRemove.isEmpty()) return true;

        // Verify we have enough of each item
        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            Long currentAmount = consolidatedItems.getOrDefault(entry.getKey(), 0L);
            if (currentAmount < entry.getValue()) {
                return false;
            }
        }

        // Perform removals all at once
        boolean updated = false;
        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            ItemSignature sig = entry.getKey();
            long amountToRemove = entry.getValue();

            consolidatedItems.computeIfPresent(sig, (key, current) -> {
                long newAmount = current - amountToRemove;
                return newAmount <= 0 ? null : newAmount;
            });

            updated = true;
        }

        if (updated) {
            displayCacheDirty = true;
            metricsCacheDirty = true;
            sortedEntriesCache = null; // Invalidate sorted entries cache
        }

        return true;
    }

    // Optimized getDisplayInventory method
    public Map<Integer, ItemStack> getDisplayInventory() {
        // Return cached result if available
        if (!displayCacheDirty) {
            // Return a shallow copy to prevent modification of the cache
            return Collections.unmodifiableMap(displayInventoryCache);
        }

        // Clear the cache for a fresh rebuild but reuse the existing map
        displayInventoryCache.clear();

        if (consolidatedItems.isEmpty()) {
            displayCacheDirty = false;
            usedSlotsCache = 0;
            return Collections.emptyMap();
        }

        // Get and sort the items - only use cached sort result if available
        if (sortedEntriesCache == null) {
            sortedEntriesCache = new ArrayList<>(consolidatedItems.entrySet());
            // Apply preferred sort if set, otherwise sort alphabetically
            if (preferredSortMaterial != null) {
                sortedEntriesCache.sort((e1, e2) -> {
                    // Use getTemplateRef() to avoid cloning - we only need to read the type
                    boolean e1Preferred = e1.getKey().getMaterial() == preferredSortMaterial;
                    boolean e2Preferred = e2.getKey().getMaterial() == preferredSortMaterial;

                    if (e1Preferred && !e2Preferred) return -1;
                    if (!e1Preferred && e2Preferred) return 1;
                    
                    // Both preferred or both not preferred, sort by material name
                    return e1.getKey().getMaterialName().compareTo(e2.getKey().getMaterialName());
                });
            } else {
                // Use optimized comparator based on cached material name
                sortedEntriesCache.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
            }
        }

        // Process items directly to the display inventory
        int currentSlot = 0;

        for (Map.Entry<ItemSignature, Long> entry : sortedEntriesCache) {
            if (currentSlot >= maxSlots) break;

            ItemSignature sig = entry.getKey();
            long totalAmount = entry.getValue();
            int maxStackSize = sig.getMaxStackSize();

            // Create as many stacks as needed for this item type
            while (totalAmount > 0 && currentSlot < maxSlots) {
                int stackSize = (int) Math.min(totalAmount, maxStackSize);

                // Create the display item only once per slot
                ItemStack displayItem = sig.getTemplate();
                displayItem.setAmount(stackSize);

                // Store in cache
                displayInventoryCache.put(currentSlot, displayItem);

                totalAmount -= stackSize;
                currentSlot++;
            }
        }

        // Update cache state
        displayCacheDirty = false;
        usedSlotsCache = displayInventoryCache.size();

        // Return unmodifiable map to prevent external changes
        return Collections.unmodifiableMap(displayInventoryCache);
    }

    public long getTotalItems() {
        if (metricsCacheDirty) {
            updateMetricsCache();
        }
        return totalItemsCache;
    }

    public Map<ItemSignature, Long> getConsolidatedItems() {
        return new HashMap<>(consolidatedItems);
    }

    public int getUsedSlots() {
        // If cache is dirty but we haven't regenerated the display inventory yet,
        // calculate a quick estimate instead of rebuilding the whole display
        if (displayCacheDirty) {
            if (consolidatedItems.isEmpty()) {
                return 0;
            }

            // Quick estimate - not perfectly accurate but avoids full rebuilds
            int estimatedSlots = 0;
            for (Map.Entry<ItemSignature, Long> entry : consolidatedItems.entrySet()) {
                long amount = entry.getValue();
                int maxStackSize = entry.getKey().getMaxStackSize();
                estimatedSlots += (int) Math.ceil((double) amount / maxStackSize);
                if (estimatedSlots >= maxSlots) {
                    return maxSlots; // Cap at max slots
                }
            }
            return estimatedSlots;
        }

        return usedSlotsCache;
    }

    private void updateMetricsCache() {
        totalItemsCache = consolidatedItems.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        metricsCacheDirty = false;
    }

    public boolean isDirty() {
        return displayCacheDirty;
    }

    /**
     * Sorts items with the specified material type prioritized first.
     * This method optimizes by only invalidating caches when necessary.
     * 
     * @param preferredMaterial The material to sort first, or null for no preference
     */
    public void sortItems(org.bukkit.Material preferredMaterial) {
        // Store the preferred material for future cache rebuilds
        this.preferredSortMaterial = preferredMaterial;
        
        // Clear the sorted cache to force re-sorting with new preference
        this.sortedEntriesCache = null;
        
        // Only proceed if we have items to sort
        if (consolidatedItems.isEmpty()) {
            this.displayCacheDirty = true;
            return;
        }
        
        // Generate new sorted entries with preference
        if (preferredMaterial != null) {
            this.sortedEntriesCache = consolidatedItems.entrySet().stream()
                .sorted((e1, e2) -> {
                    // Use getTemplateRef() to avoid cloning - we only need to read the type
                    boolean e1Preferred = e1.getKey().getMaterial() == preferredMaterial;
                    boolean e2Preferred = e2.getKey().getMaterial() == preferredMaterial;

                    if (e1Preferred && !e2Preferred) return -1;
                    if (!e1Preferred && e2Preferred) return 1;
                    
                    // Both preferred or both not preferred, sort by material name
                    return e1.getKey().getMaterialName().compareTo(e2.getKey().getMaterialName());
                })
                .collect(java.util.stream.Collectors.toList());
        } else {
            // No preference, sort alphabetically by material name
            this.sortedEntriesCache = consolidatedItems.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getMaterialName()))
                .collect(java.util.stream.Collectors.toList());
        }
        
        // Mark display cache as dirty to force regeneration
        this.displayCacheDirty = true;
    }

    /**
     * Resizes the virtual inventory to a new maximum slot count.
     * If the new size is smaller and items exceed the new capacity,
     * items will be truncated based on the current sort order.
     *
     * @param newMaxSlots The new maximum number of slots
     */
    public void resize(int newMaxSlots) {
        if (newMaxSlots == this.maxSlots) {
            return; // No change needed
        }

        this.maxSlots = newMaxSlots;

        // Mark caches as dirty since slot count changed
        this.displayCacheDirty = true;

        // If downsizing, we may need to remove items that exceed capacity
        if (newMaxSlots < usedSlotsCache) {
            // Let the display inventory rebuild handle the truncation naturally
            // Items beyond maxSlots will simply not be displayed
            // Note: This doesn't remove items from consolidatedItems,
            // but they won't be accessible in the display
        }
    }
}
