package github.nighter.smartspawner.spawner.properties;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualInventory {
    private final Map<ItemSignature, Long> consolidatedItems;
    @Getter private int maxSlots;
    // Cache sorted entries to avoid resorting when display isn't changing
    private List<Map.Entry<ItemSignature, Long>> sortedEntriesCache;
    private Material preferredSortMaterial;

    public VirtualInventory(int maxSlots) {
        this.maxSlots = maxSlots;
        this.consolidatedItems = new ConcurrentHashMap<>();
        this.sortedEntriesCache = null;
        this.preferredSortMaterial = null;
    }

    public static ItemSignature getSignature(ItemStack item) {
        return new ItemSignature(item);
    }

    public void setMaxSlots(int maxSlots) {
        this.maxSlots = Math.max(0, maxSlots);
    }

    /*
     * FAST PATH
     * Used for loading already-consolidated storage data.
     */
    public void addItem(ItemStack item, long amount) {
        if (item == null || amount <= 0) {
            return;
        }

        ItemSignature signature = getSignature(item);

        consolidatedItems.merge(signature, amount, Long::sum);

        sortedEntriesCache = null;
    }

    /*
     * Bulk insert for already-consolidated storage data.
     */
    public void addItems(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        boolean changed = false;

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            Long amountValue = entry.getValue();

            if (amountValue <= 0) {
                continue;
            }

            consolidatedItems.merge(signature, amountValue, Long::sum);
            changed = true;
        }

        if (changed) {
            sortedEntriesCache = null;
        }
    }
    /**
     * Adds an already-consolidated entry: one item template plus its total count.
     *
     * @param template the item template, its own amount is ignored
     * @param amount   how many of that item are stored, ignored when not positive
     */
    public void addConsolidatedItem(ItemStack template, long amount) {
        addItem(template, amount);
    }

    public boolean removeItems(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        Map<ItemSignature, Long> toRemove = new HashMap<>(items.size());

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            Number amountValue = entry.getValue();

            if (signature == null || amountValue == null) {
                continue;
            }

            long amount = amountValue.longValue();
            if (amount <= 0) {
                continue;
            }

            toRemove.merge(signature, amount, Long::sum);
        }

        if (toRemove.isEmpty()) {
            return true;
        }

        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            if (consolidatedItems.getOrDefault(entry.getKey(), 0L) < entry.getValue()) {
                return false;
            }
        }

        for (Map.Entry<ItemSignature, Long> entry : toRemove.entrySet()) {
            consolidatedItems.computeIfPresent(entry.getKey(), (key, current) -> {
                long remaining = current - entry.getValue();
                return remaining <= 0 ? null : remaining;
            });
        }

        sortedEntriesCache = null;

        return true;
    }

    public Int2ObjectMap<ItemStack> getDisplayPage(int page, int pageSize) {
        if (pageSize <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        int safePage = Math.max(1, page);
        int startSlot = (safePage - 1) * pageSize;
        return buildDisplaySection(startSlot, pageSize);
    }

    public Int2ObjectMap<ItemStack> getDisplayRange(int startSlot, int maxResults) {
        return buildDisplaySection(startSlot, maxResults);
    }

    public Map<ItemSignature, Long> getConsolidatedItems() {
        return new HashMap<>(consolidatedItems);
    }

    public int getUsedSlots() {
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
    }

    /**
     * Projects one page directly into {@code out}, skipping the intermediate map that
     * {@link #getDisplayPage} has to build. The paged GUI reads a page on every repaint and every
     * page flip, so this is the allocation-free path for it.
     *
     * @param out array of at least {@code pageSize} entries; fully overwritten, gaps set to null
     */
    public void fillDisplayPage(int page, int pageSize, ItemStack[] out) {
        if (out == null) {
            return;
        }
        int limit = Math.min(out.length, Math.max(0, pageSize));
        Arrays.fill(out, 0, limit, null);
        if (limit <= 0) {
            return;
        }
        fillDisplaySection((Math.max(1, page) - 1) * pageSize, limit, out);
    }

    private Int2ObjectMap<ItemStack> buildDisplaySection(int startSlot, int maxResults) {
        int sectionLimit = sectionLimit(startSlot, maxResults);
        if (sectionLimit <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        ItemStack[] scratch = new ItemStack[sectionLimit];
        int filled = fillDisplaySection(startSlot, sectionLimit, scratch);
        if (filled <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        Int2ObjectOpenHashMap<ItemStack> section = new Int2ObjectOpenHashMap<>(filled);
        for (int i = 0; i < filled; i++) {
            if (scratch[i] != null) {
                section.put(i, scratch[i]);
            }
        }
        return Int2ObjectMaps.unmodifiable(section);
    }

    /** How many display slots this section can actually cover, or 0 when there is nothing to show. */
    private int sectionLimit(int startSlot, int maxResults) {
        if (maxResults <= 0 || startSlot >= maxSlots || consolidatedItems.isEmpty()) {
            return 0;
        }
        return Math.min(maxResults, maxSlots - Math.max(0, startSlot));
    }

    /**
     * Walks the sorted count-map, skipping whole entries until {@code startSlot}, and writes the
     * stacks of the requested window into {@code out} starting at index 0.
     *
     * @return the number of slots written (the window may end early when items run out)
     */
    private int fillDisplaySection(int startSlot, int maxResults, ItemStack[] out) {
        int sectionLimit = sectionLimit(startSlot, maxResults);
        if (sectionLimit <= 0) {
            return 0;
        }

        int safeStart = Math.max(0, startSlot);
        List<Map.Entry<ItemSignature, Long>> sortedEntries = getSortedEntries();

        int currentGlobalSlot = 0;
        int relativeSlot = 0;

        for (Map.Entry<ItemSignature, Long> entry : sortedEntries) {
            if (relativeSlot >= sectionLimit || currentGlobalSlot >= maxSlots) {
                break;
            }

            ItemSignature sig = entry.getKey();
            int maxStackSize = sig.getMaxStackSize();
            if (maxStackSize <= 0) {
                continue;
            }

            long totalAmount = entry.getValue();
            int stacksForEntry = (int) Math.min(
                    Integer.MAX_VALUE,
                    (totalAmount + maxStackSize - 1L) / maxStackSize
            );

            if (currentGlobalSlot + stacksForEntry <= safeStart) {
                currentGlobalSlot += stacksForEntry;
                continue;
            }

            int stacksToSkip = Math.max(0, safeStart - currentGlobalSlot);
            long remainingAmount = totalAmount - ((long) stacksToSkip * maxStackSize);
            currentGlobalSlot += stacksToSkip;

            while (remainingAmount > 0 && relativeSlot < sectionLimit && currentGlobalSlot < maxSlots) {
                ItemStack displayItem = sig.getTemplate();
                displayItem.setAmount((int) Math.min(remainingAmount, maxStackSize));
                out[relativeSlot++] = displayItem;

                remainingAmount -= maxStackSize;
                currentGlobalSlot++;
            }
        }

        return relativeSlot;
    }

    private List<Map.Entry<ItemSignature, Long>> getSortedEntries() {
        if (sortedEntriesCache == null) {
            sortedEntriesCache = new ArrayList<>(consolidatedItems.entrySet());
            sortEntries(sortedEntriesCache);
        }
        return sortedEntriesCache;
    }

    private void sortEntries(List<Map.Entry<ItemSignature, Long>> entries) {
        if (preferredSortMaterial != null) {
            entries.sort((e1, e2) -> {
                boolean e1Preferred = e1.getKey().getMaterial() == preferredSortMaterial;
                boolean e2Preferred = e2.getKey().getMaterial() == preferredSortMaterial;

                if (e1Preferred && !e2Preferred) return -1;
                if (!e1Preferred && e2Preferred) return 1;

                return e1.getKey().getMaterialName().compareTo(e2.getKey().getMaterialName());
            });
            return;
        }

        entries.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));
    }
}
