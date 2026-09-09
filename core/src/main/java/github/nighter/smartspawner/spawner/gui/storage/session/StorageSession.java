package github.nighter.smartspawner.spawner.gui.storage.session;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an active storage session for a spawner while players are viewing its storage GUI.
 * Maintains slot layouts for the pages that are on screen to preserve gaps (empty slots) and avoid
 * auto-compacting while viewers are active. Compacting is only performed once all viewers have closed.
 *
 * <p>Memory: page buffers live in a bounded access-ordered map sized from the viewer count, so a
 * session costs O(viewers) slot arrays no matter how many pages the spawner has. Paging through a
 * 100k-page storage evicts as it goes instead of retaining one array per visited page. Evicting a
 * buffer is always safe: {@link VirtualInventory} is the source of truth and an evicted page is
 * simply re-projected from it on the next read.
 */
public class StorageSession {
    private static final int PAGE_SIZE = StoragePageHolder.MAX_ITEMS_PER_PAGE;

    @Getter
    private final SpawnerData spawner;
    private final SmartSpawner plugin;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean active = new AtomicBoolean(true);

    /** Access-ordered page buffers, capped at one per viewer plus one for in-flight page switches. */
    private final LinkedHashMap<Integer, ItemStack[]> pageSlots = new LinkedHashMap<>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, ItemStack[]> eldest) {
            return size() > Math.max(2, viewers.size() + 1);
        }
    };

    /** A single slot write queued for the viewers currently displaying {@code page}. */
    private record SlotUpdate(int page, int slot, ItemStack item) {}

    /**
     * Net change between a page as the player left it and the page as the session had it,
     * already split into what must be credited to and debited from the count-map.
     */
    public record PageDiff(Map<ItemSignature, Long> added, Map<ItemSignature, Long> removed) {
        public static final PageDiff EMPTY = new PageDiff(Map.of(), Map.of());

        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty();
        }
    }

    public StorageSession(SmartSpawner plugin, SpawnerData spawner) {
        this.plugin = plugin;
        this.spawner = spawner;
    }

    /**
     * Gets the current slot array for a specific page.
     * If the page is not buffered, it is projected from the virtual inventory.
     */
    public synchronized ItemStack[] getPageSlots(int page) {
        ItemStack[] slots = pageSlots.get(page);
        if (slots != null) {
            return slots;
        }
        slots = projectPage(page);
        pageSlots.put(page, slots);
        return slots;
    }

    /**
     * Copies {@code slots} into the buffer for a page. Passing null drops the buffered page.
     */
    public synchronized void setPageSlots(int page, ItemStack[] slots) {
        if (slots == null) {
            pageSlots.remove(page);
            return;
        }
        ItemStack[] copy = new ItemStack[PAGE_SIZE];
        int limit = Math.min(PAGE_SIZE, slots.length);
        for (int i = 0; i < limit; i++) {
            ItemStack item = slots[i];
            copy[i] = (item != null && item.getType() != Material.AIR) ? item.clone() : null;
        }
        pageSlots.put(page, copy);
    }

    /**
     * Installs an array the caller has already cloned and will not touch again, skipping the
     * defensive copy {@link #setPageSlots} makes. The array must be exactly PAGE_SIZE long and hold
     * only non-air stacks or nulls.
     */
    public synchronized void adoptPageSlots(int page, ItemStack[] slots) {
        if (slots == null || slots.length != PAGE_SIZE) {
            setPageSlots(page, slots);
            return;
        }
        pageSlots.put(page, slots);
    }

    /**
     * Diffs the slots a viewer currently has on screen against this session's buffer for that page
     * and adopts the new layout, all under the session lock so live loot and hopper writes cannot
     * interleave with the snapshot.
     *
     * <p>Slots that did not move are carried over untouched — no clone, no {@link ItemSignature}
     * construction — so a single-item take costs work proportional to the slots that actually
     * changed rather than to the whole page.
     *
     * @param liveSlots the raw stacks read out of the open inventory; the array is not retained and
     *                  anything kept is cloned, since Bukkit hands back live mirrors
     * @return what to credit to and debit from the count-map, {@link PageDiff#EMPTY} if nothing moved
     */
    public synchronized PageDiff reconcilePage(int page, ItemStack[] liveSlots) {
        ItemStack[] previous = getPageSlots(page);
        ItemStack[] next = new ItemStack[PAGE_SIZE];
        Map<ItemSignature, Long> delta = null;

        int liveLimit = liveSlots == null ? 0 : Math.min(PAGE_SIZE, liveSlots.length);
        for (int i = 0; i < PAGE_SIZE; i++) {
            ItemStack live = i < liveLimit ? liveSlots[i] : null;
            if (live != null && (live.getType() == Material.AIR || live.getAmount() <= 0)) {
                live = null;
            }
            ItemStack prev = i < previous.length ? previous[i] : null;

            if (live == null && prev == null) {
                continue;
            }

            if (live != null && prev != null && live.getAmount() == prev.getAmount() && live.isSimilar(prev)) {
                next[i] = prev; // Unchanged: keep the session-owned stack instead of re-cloning
                continue;
            }

            if (delta == null) {
                delta = new HashMap<>(8);
            }
            if (prev != null) {
                delta.merge(VirtualInventory.getSignature(prev), -(long) prev.getAmount(), Long::sum);
            }
            if (live != null) {
                delta.merge(VirtualInventory.getSignature(live), (long) live.getAmount(), Long::sum);
                next[i] = live.clone();
            }
        }

        pageSlots.put(page, next);

        if (delta == null) {
            return PageDiff.EMPTY;
        }

        Map<ItemSignature, Long> added = null;
        Map<ItemSignature, Long> removed = null;
        for (Map.Entry<ItemSignature, Long> entry : delta.entrySet()) {
            long net = entry.getValue();
            if (net > 0) {
                if (added == null) added = new HashMap<>(4);
                added.put(entry.getKey(), net);
            } else if (net < 0) {
                if (removed == null) removed = new HashMap<>(4);
                removed.put(entry.getKey(), -net);
            }
        }

        if (added == null && removed == null) {
            return PageDiff.EMPTY;
        }
        return new PageDiff(added == null ? Map.of() : added, removed == null ? Map.of() : removed);
    }

    /**
     * Drops every buffered page while keeping viewers registered, so the next repaint re-projects
     * from {@link VirtualInventory}. Used after an operation rewrites the whole inventory
     * (sorting, selling) with the GUI still open.
     */
    public synchronized void resetPages() {
        pageSlots.clear();
    }

    private ItemStack[] projectPage(int page) {
        ItemStack[] slots = new ItemStack[PAGE_SIZE];
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        if (virtualInv != null) {
            // Writes straight into the array: no intermediate map, and the stacks it builds are
            // fresh, so the session can own them without a defensive clone.
            virtualInv.fillDisplayPage(page, PAGE_SIZE, slots);
        }
        return slots;
    }

    public synchronized boolean addViewer(UUID playerId) {
        if (!viewers.isEmpty() && !viewers.contains(playerId)) {
            // Self-heal: prune viewers who went offline or closed the GUI without event
            viewers.removeIf(id -> {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) return true;
                return !(p.getOpenInventory().getTopInventory().getHolder(false) instanceof StoragePageHolder);
            });
            if (!viewers.isEmpty() && !viewers.contains(playerId)) {
                return false; // Storage is locked by another active viewer
            }
        }
        viewers.add(playerId);
        return true;
    }

    public void removeViewer(UUID playerId) {
        viewers.remove(playerId);
    }

    public boolean hasViewers() {
        return !viewers.isEmpty();
    }

    public Set<UUID> getViewers() {
        return Collections.unmodifiableSet(viewers);
    }

    public boolean isActive() {
        return active.get();
    }

    /** Number of page buffers currently held. Exposed for diagnostics and benchmarks. */
    public synchronized int getBufferedPageCount() {
        return pageSlots.size();
    }

    /**
     * Adds generated loot into the on-screen slots without resetting or shifting existing items.
     * First tops up existing matching stacks, then fills empty slots.
     * The count-map is updated by the caller; this only mirrors the change into the open GUI.
     */
    public void addLoot(Map<ItemSignature, Long> lootToAdd) {
        if (lootToAdd == null || lootToAdd.isEmpty() || !active.get() || viewers.isEmpty()) {
            return;
        }

        List<SlotUpdate> updates;
        synchronized (this) {
            if (pageSlots.isEmpty()) {
                return;
            }
            updates = new ArrayList<>();
            int maxSlots = spawner.getMaxSpawnerLootSlots();
            int[] pages = bufferedPagesAscending();

            for (Map.Entry<ItemSignature, Long> entry : lootToAdd.entrySet()) {
                ItemSignature sig = entry.getKey();
                long amountRemaining = entry.getValue();
                int maxStackSize = sig.getMaxStackSize();
                if (maxStackSize <= 0 || amountRemaining <= 0) {
                    continue;
                }

                // 1. Top up existing matching stacks on the buffered pages
                for (int p : pages) {
                    if (amountRemaining <= 0) break;
                    ItemStack[] slots = pageSlots.get(p);
                    if (slots == null) continue;
                    for (int s = 0; s < PAGE_SIZE && amountRemaining > 0; s++) {
                        ItemStack slotItem = slots[s];
                        if (slotItem == null || !slotItem.isSimilar(sig.getUnsafeTemplateRef())) continue;
                        int space = maxStackSize - slotItem.getAmount();
                        if (space <= 0) continue;
                        int toAdd = (int) Math.min(space, amountRemaining);
                        slotItem.setAmount(slotItem.getAmount() + toAdd);
                        amountRemaining -= toAdd;
                        updates.add(new SlotUpdate(p, s, slotItem.clone()));
                    }
                }

                // 2. Fill empty slots on the buffered pages
                for (int p : pages) {
                    if (amountRemaining <= 0) break;
                    ItemStack[] slots = pageSlots.get(p);
                    if (slots == null) continue;
                    for (int s = 0; s < PAGE_SIZE && amountRemaining > 0; s++) {
                        int globalSlot = (p - 1) * PAGE_SIZE + s;
                        if (globalSlot >= maxSlots) break;
                        if (slots[s] != null) continue;

                        int toAdd = (int) Math.min(maxStackSize, amountRemaining);
                        ItemStack newStack = sig.getTemplate();
                        newStack.setAmount(toAdd);
                        slots[s] = newStack;
                        amountRemaining -= toAdd;
                        updates.add(new SlotUpdate(p, s, newStack.clone()));
                    }
                }
            }
        }

        pushSlotUpdates(updates);
    }

    /**
     * Removes items from the on-screen slots (e.g. hopper extraction).
     * The count-map is updated by the caller; this only mirrors the change into the open GUI.
     */
    public void removeLoot(List<ItemStack> removedItems) {
        if (removedItems == null || removedItems.isEmpty() || !active.get() || viewers.isEmpty()) {
            return;
        }

        List<SlotUpdate> updates;
        synchronized (this) {
            if (pageSlots.isEmpty()) {
                return;
            }
            updates = new ArrayList<>();
            int[] pages = bufferedPagesAscending();

            for (ItemStack toRemove : removedItems) {
                if (toRemove == null || toRemove.getAmount() <= 0) continue;
                int remaining = toRemove.getAmount();

                for (int page : pages) {
                    if (remaining <= 0) break;
                    ItemStack[] slots = pageSlots.get(page);
                    if (slots == null) continue;

                    for (int s = 0; s < PAGE_SIZE && remaining > 0; s++) {
                        ItemStack slotItem = slots[s];
                        if (slotItem == null || !slotItem.isSimilar(toRemove)) continue;

                        int currentAmt = slotItem.getAmount();
                        if (currentAmt <= remaining) {
                            remaining -= currentAmt;
                            slots[s] = null;
                            updates.add(new SlotUpdate(page, s, null));
                        } else {
                            slotItem.setAmount(currentAmt - remaining);
                            remaining = 0;
                            updates.add(new SlotUpdate(page, s, slotItem.clone()));
                        }
                    }
                }
            }
        }

        pushSlotUpdates(updates);
    }

    /** Buffered page numbers in ascending order. At most a handful of entries. */
    private int[] bufferedPagesAscending() {
        int[] pages = new int[pageSlots.size()];
        int i = 0;
        for (Integer page : pageSlots.keySet()) {
            pages[i++] = page;
        }
        Arrays.sort(pages);
        return pages;
    }

    /**
     * Applies queued slot writes to every open GUI showing the matching page.
     * Always hops to the spawner's region thread: loot generation and hopper transfer can call in
     * from async tasks, and Bukkit inventories must not be touched off-region.
     */
    private void pushSlotUpdates(List<SlotUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        Location spawnerLocation = spawner.getSpawnerLocation();
        if (Scheduler.isOwnedByCurrentThread(spawnerLocation)) {
            applySlotUpdates(updates); // Already on the owning thread: skip a task and a tick of latency
        } else {
            Scheduler.runLocationTask(spawnerLocation, () -> applySlotUpdates(updates));
        }
    }

    private void applySlotUpdates(List<SlotUpdate> updates) {
        Set<Player> activeViewers = plugin.getSpawnerGuiViewManager().getViewers(spawner.getSpawnerId());
        if (activeViewers == null || activeViewers.isEmpty()) {
            return;
        }
        String spawnerId = spawner.getSpawnerId();

        for (Player viewer : activeViewers) {
            if (!viewer.isOnline()) continue;
            Inventory openInv = viewer.getOpenInventory().getTopInventory();
            if (!(openInv.getHolder(false) instanceof StoragePageHolder holder)) continue;
            if (!holder.getSpawnerData().getSpawnerId().equals(spawnerId)) continue;

            int viewerPage = holder.getCurrentPage();
            boolean touched = false;
            for (SlotUpdate update : updates) {
                if (update.page() != viewerPage) continue;
                ItemStack item = update.item();
                openInv.setItem(update.slot(), item != null ? item.clone() : null);
                touched = true;
            }
            if (touched) {
                viewer.updateInventory();
            }
        }
    }

    /**
     * Ends the active storage session when all viewers have closed the GUI.
     * Releases page buffers so that the next time storage is opened, items are naturally
     * compacted from slot 0 using VirtualInventory without losing any items across any pages.
     */
    public synchronized void endSession() {
        if (!active.compareAndSet(true, false)) {
            return; // Already ended
        }

        pageSlots.clear();
        viewers.clear();
        spawner.updateHologramData();
        spawner.clearStorageDirty();
        plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
    }
}
