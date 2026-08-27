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

    // Frozen display order: while a storage viewer is present the signature order is pinned so stored
    // items keep their slots and freshly generated loot appends at the end instead of being re-sorted
    // into the middle. Guarded by orderLock because the display is read without the owning
    // SpawnerData.inventoryLock in a few paths.
    private final Object orderLock = new Object();
    private volatile boolean orderFrozen = false;
    private List<ItemSignature> frozenOrder;

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

    /**
     * Current stored count for a single signature. Read-only.
     */
    public long available(ItemSignature signature) {
        if (signature == null) {
            return 0L;
        }
        return consolidatedItems.getOrDefault(signature, 0L);
    }

    /**
     * Atomic, non-failing removal: for each requested signature removes
     * {@code min(desired, available)} and returns the amount actually removed.
     * Unlike {@link #removeItems(Map)} it never rejects the whole batch, so it is
     * safe against stale views – a second caller acting on outdated display data
     * simply gets back an empty or reduced map instead of over-removing.
     *
     * <p>Callers must hold the owning {@code SpawnerData.inventoryLock} for the
     * removal to be atomic against concurrent mutations.
     *
     * @param desired signature to requested amount
     * @return signature to amount actually removed (only positive entries)
     */
    public Map<ItemSignature, Long> removeUpTo(Map<ItemSignature, Long> desired) {
        if (desired == null || desired.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ItemSignature, Long> removed = new HashMap<>(desired.size());
        boolean changed = false;

        for (Map.Entry<ItemSignature, Long> entry : desired.entrySet()) {
            ItemSignature signature = entry.getKey();
            Long wantValue = entry.getValue();
            if (signature == null || wantValue == null || wantValue <= 0) {
                continue;
            }

            long want = wantValue;
            long[] takenHolder = new long[1];
            consolidatedItems.computeIfPresent(signature, (key, current) -> {
                long take = Math.min(current, want);
                takenHolder[0] = take;
                long remaining = current - take;
                return remaining <= 0 ? null : remaining;
            });

            if (takenHolder[0] > 0) {
                removed.put(signature, takenHolder[0]);
                changed = true;
            }
        }

        if (changed) {
            sortedEntriesCache = null;
        }

        return removed;
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

    private Int2ObjectMap<ItemStack> buildDisplaySection(int startSlot, int maxResults) {
        if (maxResults <= 0 || startSlot >= maxSlots) {
            return Int2ObjectMaps.emptyMap();
        }

        if (consolidatedItems.isEmpty()) {
            return Int2ObjectMaps.emptyMap();
        }

        int safeStart = Math.max(0, startSlot);
        int sectionLimit = Math.min(maxResults, maxSlots - safeStart);
        if (sectionLimit <= 0) {
            return Int2ObjectMaps.emptyMap();
        }

        Int2ObjectOpenHashMap<ItemStack> section = new Int2ObjectOpenHashMap<>(Math.min(sectionLimit, 45));
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
                section.put(relativeSlot++, displayItem);

                remainingAmount -= maxStackSize;
                currentGlobalSlot++;
            }
        }

        return Int2ObjectMaps.unmodifiable(section);
    }

    /**
     * Pins the current display order. While frozen, {@link #getSortedEntries()} returns signatures in
     * the captured order; a signature seen later (freshly looted) is appended at the end rather than
     * sorted into the middle, and a depleted signature keeps its place so a refill reappears where it
     * was. Callers hold {@code SpawnerData.inventoryLock}; the extra {@code orderLock} guards against
     * the unlocked display reads.
     */
    public void freezeOrder() {
        synchronized (orderLock) {
            List<Map.Entry<ItemSignature, Long>> sorted = getSortedEntries();
            List<ItemSignature> order = new ArrayList<>(sorted.size());
            for (Map.Entry<ItemSignature, Long> entry : sorted) {
                order.add(entry.getKey());
            }
            frozenOrder = order;
            orderFrozen = true;
        }
    }

    /** Releases the pinned order so the next display re-sorts by the sort preference. */
    public void unfreezeOrder() {
        synchronized (orderLock) {
            orderFrozen = false;
            frozenOrder = null;
            sortedEntriesCache = null;
        }
    }

    /** @return true if the display order is currently pinned. */
    public boolean isOrderFrozen() {
        return orderFrozen;
    }

    private List<Map.Entry<ItemSignature, Long>> getSortedEntries() {
        if (orderFrozen) {
            List<Map.Entry<ItemSignature, Long>> frozen = buildFrozenEntries();
            if (frozen != null) {
                return frozen;
            }
        }
        if (sortedEntriesCache == null) {
            sortedEntriesCache = new ArrayList<>(consolidatedItems.entrySet());
            sortEntries(sortedEntriesCache);
        }
        return sortedEntriesCache;
    }

    /**
     * Builds the display entry list honouring {@link #frozenOrder}: frozen signatures first (only
     * those still present), then any newcomer signatures sorted and appended, growing the frozen
     * order so their position stays stable for the rest of the freeze. Returns {@code null} if the
     * order was concurrently unfrozen.
     */
    private List<Map.Entry<ItemSignature, Long>> buildFrozenEntries() {
        synchronized (orderLock) {
            if (!orderFrozen || frozenOrder == null) {
                return null;
            }
            List<Map.Entry<ItemSignature, Long>> result = new ArrayList<>(frozenOrder.size() + 4);
            Set<ItemSignature> known = new HashSet<>(frozenOrder);
            for (ItemSignature sig : frozenOrder) {
                Long amount = consolidatedItems.get(sig);
                if (amount != null && amount > 0) {
                    result.add(Map.entry(sig, amount));
                }
            }
            List<ItemSignature> newcomers = null;
            for (ItemSignature sig : consolidatedItems.keySet()) {
                if (!known.contains(sig)) {
                    if (newcomers == null) {
                        newcomers = new ArrayList<>();
                    }
                    newcomers.add(sig);
                }
            }
            if (newcomers != null) {
                sortSignatures(newcomers);
                for (ItemSignature sig : newcomers) {
                    frozenOrder.add(sig);
                    Long amount = consolidatedItems.get(sig);
                    if (amount != null && amount > 0) {
                        result.add(Map.entry(sig, amount));
                    }
                }
            }
            return result;
        }
    }

    private void sortSignatures(List<ItemSignature> signatures) {
        if (preferredSortMaterial != null) {
            signatures.sort((a, b) -> {
                boolean ap = a.getMaterial() == preferredSortMaterial;
                boolean bp = b.getMaterial() == preferredSortMaterial;
                if (ap && !bp) return -1;
                if (!ap && bp) return 1;
                return a.getMaterialName().compareTo(b.getMaterialName());
            });
        } else {
            signatures.sort(Comparator.comparing(ItemSignature::getMaterialName));
        }
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
