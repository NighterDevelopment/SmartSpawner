package me.nighter.smartSpawner.holders;

import me.nighter.smartSpawner.utils.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PagedSpawnerLootHolder implements InventoryHolder {
    private final SpawnerData spawnerData;
    private final int currentPage;
    private final int totalPages;
    private static final int ROWS_PER_PAGE = 5; // 45 slots for items
    private static final int SLOTS_PER_PAGE = ROWS_PER_PAGE * 9;

    public PagedSpawnerLootHolder(SpawnerData spawnerData, int currentPage, int totalPages) {
        this.spawnerData = spawnerData;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public SpawnerData getSpawnerData() {
        return spawnerData;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
