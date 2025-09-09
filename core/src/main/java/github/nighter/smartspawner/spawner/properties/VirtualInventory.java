package github.nighter.smartspawner.spawner.properties;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualInventory {
    private final Map<ItemSignature, Long> consolidatedItems;
    @Getter
    private final int maxSlots;
    private final Map<Integer, ItemStack> displayInventoryCache;
    private boolean displayCacheDirty;
    private int usedSlotsCache;
    private long totalItemsCache;
    private boolean metricsCacheDirty;
    // Cache sorted entries to avoid resorting when display isn't changing
    private List<Map.Entry<ItemSignature, Long>> sortedEntriesCache;

    // Add an LRU cache for expensive item operations
    private static final int ITEM_CACHE_SIZE = 128;
    private static final Map<ItemStack, ItemSignature> signatureCache =
            Collections.synchronizedMap(new LinkedHashMap<ItemStack, ItemSignature>(ITEM_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ItemStack, ItemSignature> eldest) {
                    return size() > ITEM_CACHE_SIZE;
                }
            });

    public VirtualInventory(int maxSlots) {
        this.maxSlots = maxSlots;
        this.consolidatedItems = new ConcurrentHashMap<>();
        this.displayInventoryCache = new HashMap<>(maxSlots); // Pre-size the map
        this.displayCacheDirty = true;
        this.metricsCacheDirty = true;
        this.usedSlotsCache = 0;
        this.totalItemsCache = 0;
        this.sortedEntriesCache = null;
    }

    public static class ItemSignature {
        private final ItemStack template;
        private final int hashCode;
        @Getter
        private final String materialName;

        public ItemSignature(ItemStack item) {
            this.template = item.clone();
            this.template.setAmount(1);
            this.materialName = item.getType().name();
            this.hashCode = calculateHashCode();
        }

        // Replace the current calculateHashCode() method with:
        private int calculateHashCode() {
            // Use a faster hash algorithm and cache more item properties
            int result = 31 * template.getType().ordinal(); // Using ordinal() instead of name() hashing
            result = 31 * result + (int)template.getDurability();

            // Only access ItemMeta when needed
            if (template.hasItemMeta()) {
                ItemMeta meta = template.getItemMeta();
                // Extract only the essential meta properties that determine similarity
                result = 31 * result + (meta.hasDisplayName() ? meta.getDisplayName().hashCode() : 0);
                result = 31 * result + (meta.hasLore() ? meta.getLore().hashCode() : 0);
                result = 31 * result + (meta.hasEnchants() ? meta.getEnchants().hashCode() : 0);
            }
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemSignature)) return false;
            ItemSignature that = (ItemSignature) o;

            // First compare cheap properties
            if (template.getType() != that.template.getType() ||
                    template.getDurability() != that.template.getDurability()) {
                return false;
            }

            // Only check ItemMeta if types match
            boolean thisHasMeta = template.hasItemMeta();
            boolean thatHasMeta = that.template.hasItemMeta();

            if (thisHasMeta != thatHasMeta) {
                return false;
            }

            // If both have no meta, they're similar enough
            if (!thisHasMeta) {
                return true;
            }

            // For complex items, fall back to isSimilar but only as a last resort
            return template.isSimilar(that.template);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        public ItemStack getTemplate() {
            return template.clone();
        }

        // Non-cloning method for internal use
        public ItemStack getTemplateRef() {
            return template;
        }

    }

    public static ItemSignature getSignature(ItemStack item) {
        // First try to get from cache
        ItemSignature cachedSig = signatureCache.get(item);
        if (cachedSig != null) {
            return cachedSig;
        }

        // Create new signature and cache it
        ItemSignature newSig = new ItemSignature(item);
        signatureCache.put(item.clone(), newSig);
        return newSig;
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
            itemBatch.merge(sig, (long) item.getAmount(), Long::sum);
        }

        // Apply all changes in one operation
        if (!itemBatch.isEmpty()) {
            for (Map.Entry<ItemSignature, Long> entry : itemBatch.entrySet()) {
                consolidatedItems.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
            displayCacheDirty = true;
            metricsCacheDirty = true;
            sortedEntriesCache = null;
        }
    }
    // Remove items in bulk with minimal operations
    public boolean removeItems(List<ItemStack> items) {
        if (items.isEmpty()) return true;

        Map<ItemSignature, Long> toRemove = new HashMap<>();

        // Calculate total amounts to remove in a single pass
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;
            ItemSignature sig = new ItemSignature(item);
            toRemove.merge(sig, (long) item.getAmount(), Long::sum);
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

    // Check if items can be removed without actually removing them
    public boolean canRemoveItems(List<ItemStack> items) {
        if (items.isEmpty()) return true;

        Map<ItemSignature, Long> toRemove = new HashMap<>();

        // Calculate total amounts to remove in a single pass
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;
            ItemSignature sig = new ItemSignature(item);
            toRemove.merge(sig, (long) item.getAmount(), Long::sum);
        }

        if (toRemove.isEmpty()) return true;

        // Verify we have enough of each item
        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            Long currentAmount = consolidatedItems.getOrDefault(entry.getKey(), 0L);
            if (currentAmount < entry.getValue()) {
                return false;
            }
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
            // Use optimized comparator based on cached material name
            sortedEntriesCache.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
        }

        // Process items directly to the display inventory
        int currentSlot = 0;

        for (Map.Entry<ItemSignature, Long> entry : sortedEntriesCache) {
            if (currentSlot >= maxSlots) break;

            ItemSignature sig = entry.getKey();
            long totalAmount = entry.getValue();
            ItemStack templateItem = sig.getTemplateRef();
            int maxStackSize = templateItem.getMaxStackSize();

            // Create as many stacks as needed for this item type
            while (totalAmount > 0 && currentSlot < maxSlots) {
                int stackSize = (int) Math.min(totalAmount, maxStackSize);

                // Create the display item only once per slot
                ItemStack displayItem = templateItem.clone();
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
                int maxStackSize = entry.getKey().getTemplateRef().getMaxStackSize();
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
}