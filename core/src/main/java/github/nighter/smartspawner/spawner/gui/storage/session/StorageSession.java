package github.nighter.smartspawner.spawner.gui.storage.session;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.gui.storage.StoragePageHolder;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an active storage session for a spawner while players are viewing its storage GUI.
 * Maintains slot layouts per page to preserve gaps (empty slots) and avoid auto-compacting
 * while viewers are active. Compacting is only performed once all viewers have closed the GUI.
 */
public class StorageSession {
    @Getter
    private final SpawnerData spawner;
    private final SmartSpawner plugin;
    private final Map<Integer, ItemStack[]> pageSlots = new ConcurrentHashMap<>();
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean active = new AtomicBoolean(true);

    public StorageSession(SmartSpawner plugin, SpawnerData spawner) {
        this.plugin = plugin;
        this.spawner = spawner;
    }

    /**
     * Gets the current slot array for a specific page (0 to 44).
     * If the page has not been loaded yet, it is populated from the virtual inventory.
     */
    public synchronized ItemStack[] getPageSlots(int page) {
        return pageSlots.computeIfAbsent(page, p -> {
            ItemStack[] slots = new ItemStack[StoragePageHolder.MAX_ITEMS_PER_PAGE];
            VirtualInventory virtualInv = spawner.getVirtualInventory();
            if (virtualInv != null) {
                Int2ObjectMap<ItemStack> displayItems = virtualInv.getDisplayPage(p, StoragePageHolder.MAX_ITEMS_PER_PAGE);
                for (Int2ObjectMap.Entry<ItemStack> entry : displayItems.int2ObjectEntrySet()) {
                    int slot = entry.getIntKey();
                    if (slot >= 0 && slot < StoragePageHolder.MAX_ITEMS_PER_PAGE) {
                        slots[slot] = entry.getValue() != null ? entry.getValue().clone() : null;
                    }
                }
            }
            return slots;
        });
    }

    /**
     * Updates the slot array for a specific page.
     */
    public synchronized void setPageSlots(int page, ItemStack[] slots) {
        if (slots == null) {
            pageSlots.remove(page);
            return;
        }
        ItemStack[] copy = new ItemStack[StoragePageHolder.MAX_ITEMS_PER_PAGE];
        for (int i = 0; i < StoragePageHolder.MAX_ITEMS_PER_PAGE; i++) {
            copy[i] = (i < slots.length && slots[i] != null && slots[i].getType() != Material.AIR) ? slots[i].clone() : null;
        }
        pageSlots.put(page, copy);
    }

    /**
     * Updates a single slot on a specific page.
     */
    public synchronized void setSlot(int page, int slot, ItemStack item) {
        if (slot < 0 || slot >= StoragePageHolder.MAX_ITEMS_PER_PAGE) return;
        ItemStack[] slots = getPageSlots(page);
        slots[slot] = (item != null && item.getType() != Material.AIR) ? item.clone() : null;
    }

    public void addViewer(UUID playerId) {
        viewers.add(playerId);
    }

    public void removeViewer(UUID playerId) {
        viewers.remove(playerId);
    }

    public boolean hasViewers() {
        return !viewers.isEmpty();
    }

    public Set<UUID> getViewers() {
        return viewers;
    }

    public boolean isActive() {
        return active.get();
    }

    /**
     * Adds generated loot into active storage session slots without resetting or shifting existing items.
     * First attempts to stack with existing matching items, then fills empty slots.
     */
    public synchronized void addLoot(Map<ItemSignature, Long> lootToAdd) {
        if (lootToAdd == null || lootToAdd.isEmpty() || !active.get()) {
            return;
        }

        int maxSlots = spawner.getMaxSpawnerLootSlots();
        List<Integer> sortedPages = new ArrayList<>(pageSlots.keySet());
        Collections.sort(sortedPages);

        if (sortedPages.isEmpty()) {
            return;
        }

        for (Map.Entry<ItemSignature, Long> entry : lootToAdd.entrySet()) {
            ItemSignature sig = entry.getKey();
            long amountRemaining = entry.getValue();
            int maxStackSize = sig.getMaxStackSize();

            // 1. Try to stack onto existing matching items in currently loaded pages
            for (int p : sortedPages) {
                if (amountRemaining <= 0) break;
                ItemStack[] slots = pageSlots.get(p);
                if (slots == null) continue;
                for (int s = 0; s < StoragePageHolder.MAX_ITEMS_PER_PAGE && amountRemaining > 0; s++) {
                    ItemStack slotItem = slots[s];
                    if (slotItem != null && slotItem.isSimilar(sig.getUnsafeTemplateRef())) {
                        int space = maxStackSize - slotItem.getAmount();
                        if (space > 0) {
                            int toAdd = (int) Math.min(space, amountRemaining);
                            slotItem.setAmount(slotItem.getAmount() + toAdd);
                            amountRemaining -= toAdd;
                            updateOpenGuiSlot(p, s, slotItem);
                        }
                    }
                }
            }

            // 2. Put into empty slots in currently loaded pages
            for (int p : sortedPages) {
                if (amountRemaining <= 0) break;
                ItemStack[] slots = pageSlots.get(p);
                if (slots == null) continue;
                for (int s = 0; s < StoragePageHolder.MAX_ITEMS_PER_PAGE && amountRemaining > 0; s++) {
                    int globalSlot = (p - 1) * StoragePageHolder.MAX_ITEMS_PER_PAGE + s;
                    if (globalSlot >= maxSlots) break;

                    if (slots[s] == null || slots[s].getType() == Material.AIR) {
                        int toAdd = (int) Math.min(maxStackSize, amountRemaining);
                        ItemStack newStack = sig.getTemplate();
                        newStack.setAmount(toAdd);
                        slots[s] = newStack;
                        amountRemaining -= toAdd;
                        updateOpenGuiSlot(p, s, newStack);
                    }
                }
            }
        }
    }

    /**
     * Removes items from active storage session (e.g. hopper extraction).
     */
    public synchronized void removeLoot(List<ItemStack> removedItems) {
        if (removedItems == null || removedItems.isEmpty() || !active.get()) {
            return;
        }

        for (ItemStack toRemove : removedItems) {
            if (toRemove == null || toRemove.getAmount() <= 0) continue;
            int remaining = toRemove.getAmount();

            List<Integer> sortedPages = new ArrayList<>(pageSlots.keySet());
            Collections.sort(sortedPages);

            for (int page : sortedPages) {
                if (remaining <= 0) break;
                ItemStack[] slots = pageSlots.get(page);
                if (slots == null) continue;

                for (int s = 0; s < StoragePageHolder.MAX_ITEMS_PER_PAGE && remaining > 0; s++) {
                    ItemStack slotItem = slots[s];
                    if (slotItem != null && slotItem.isSimilar(toRemove)) {
                        int currentAmt = slotItem.getAmount();
                        if (currentAmt <= remaining) {
                            remaining -= currentAmt;
                            slots[s] = null;
                            updateOpenGuiSlot(page, s, null);
                        } else {
                            slotItem.setAmount(currentAmt - remaining);
                            remaining = 0;
                            updateOpenGuiSlot(page, s, slotItem);
                        }
                    }
                }
            }
        }
    }

    private void updateOpenGuiSlot(int page, int slot, ItemStack item) {
        Set<Player> activeViewers = plugin.getSpawnerGuiViewManager().getViewers(spawner.getSpawnerId());
        for (Player viewer : activeViewers) {
            if (!viewer.isOnline()) continue;
            Inventory openInv = viewer.getOpenInventory().getTopInventory();
            if (openInv.getHolder(false) instanceof StoragePageHolder holder) {
                if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())
                        && holder.getCurrentPage() == page) {
                    openInv.setItem(slot, item != null ? item.clone() : null);
                    viewer.updateInventory();
                }
            }
        }
    }

    /**
     * Ends the active storage session when all viewers have closed the GUI.
     * Clears page slots so that the next time storage is opened, items are naturally
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

    /**
     * Alias for endSession() for backwards compatibility.
     */
    public synchronized void compactAndSave() {
        endSession();
    }
}
