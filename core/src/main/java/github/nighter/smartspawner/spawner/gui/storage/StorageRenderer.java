package github.nighter.smartspawner.spawner.gui.storage;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Diff renderer for the storage GUI. Given a fully-built {@code target} layout, it compares each
 * slot against the {@link StorageView} cache and calls {@link Inventory#setItem} only where the
 * contents actually changed. Two consequences versus the old clear-and-rewrite path:
 *
 * <ul>
 *   <li>Taking one item repaints ~1-3 slots instead of all 45 item slots plus every button.</li>
 *   <li>No {@code player.updateInventory()} — Bukkit ships the individual changed slots, so an
 *       unchanged page produces zero packets.</li>
 * </ul>
 *
 * <p>Runs entirely outside the inventory lock: the caller snapshots the page under the lock into
 * an immutable {@code target} array first, then hands it here.
 */
public final class StorageRenderer {

    private StorageRenderer() {
    }

    /**
     * Patches {@code inventory} to match {@code target}, touching only changed slots.
     *
     * @param inventory the open GUI inventory
     * @param view      per-open render cache for this inventory
     * @param target    desired contents keyed by slot index; {@code null} entries mean empty
     * @param version   the storage version {@code target} was snapshotted at
     */
    public static void patch(Inventory inventory, StorageView view, ItemStack[] target, long version) {
        int size = inventory.getSize();
        view.ensureSize(size);

        for (int slot = 0; slot < size; slot++) {
            ItemStack desired = (slot < target.length) ? target[slot] : null;
            ItemStack cached = view.get(slot);
            if (!itemsEqual(cached, desired)) {
                inventory.setItem(slot, desired);
                // target entries are freshly built each render and never mutated afterwards,
                // so caching the reference directly (no clone) is safe.
                view.set(slot, desired);
            }
        }

        view.setRenderedVersion(version);
    }

    private static boolean itemsEqual(ItemStack a, ItemStack b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        // ItemStack.equals compares type, amount and meta – exactly "is this slot showing the
        // same stack", which is what decides whether a repaint packet is needed.
        return a.equals(b);
    }
}
