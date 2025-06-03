package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.Scheduler.Task;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerStorageUI {
    private static final int NAVIGATION_ROW = 5;
    private static final int INVENTORY_SIZE = 54;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    // Precomputed buttons to avoid repeated creation
    private final Map<String, ItemStack> staticButtons;

    // Lightweight caches with better eviction strategies
    private final Map<String, ItemStack> navigationButtonCache;
    private final Map<String, ItemStack> pageIndicatorCache;

    // Cache expiry time reduced for more responsive updates
    private static final int MAX_CACHE_SIZE = 100;

    // Cleanup task to remove stale entries from caches
    private Task cleanupTask;

    public SpawnerStorageUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();

        // Initialize caches with appropriate initial capacity
        this.staticButtons = new HashMap<>(4);
        this.navigationButtonCache = new ConcurrentHashMap<>(16);
        this.pageIndicatorCache = new ConcurrentHashMap<>(16);

        initializeStaticButtons();
        startCleanupTask();
    }

    private void initializeStaticButtons() {
        // Create return button
        staticButtons.put("return", createButton(
                Material.RED_STAINED_GLASS_PANE,
                languageManager.getGuiItemName("return_button.name"),
                languageManager.getGuiItemLoreAsList("return_button.lore")
        ));

        // Create take all button
        staticButtons.put("takeAll", createButton(
                Material.CHEST,
                languageManager.getGuiItemName("take_all_button.name"),
                languageManager.getGuiItemLoreAsList("take_all_button.lore" )
        ));

        // Create discard all button
        staticButtons.put("discardAll", createButton(
                Material.CAULDRON,
                languageManager.getGuiItemName("discard_all_button.name"),
                languageManager.getGuiItemLoreAsList("discard_all_button.lore")
        ));

        // Pre-create equipment toggle buttons
        staticButtons.put("itemFilter", createButton(
                Material.HOPPER,
                languageManager.getGuiItemName("item_filter_button.name"),
                languageManager.getGuiItemLoreAsList("item_filter_button.lore")
        ));
    }

    public Inventory createInventory(SpawnerData spawner, String title, int page, int totalPages) {
        // Get total pages efficiently
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Clamp page number to valid range
        page = Math.max(1, Math.min(page, totalPages));

        // Create inventory with title including page info
        Inventory pageInv = Bukkit.createInventory(
                new StoragePageHolder(spawner, page, totalPages),
                INVENTORY_SIZE,
                title + " - [" + page + "/" + totalPages + "]"
        );

        // Cache inventory reference in holder for better performance
        StoragePageHolder holder = (StoragePageHolder) pageInv.getHolder();
        holder.setInventory(pageInv);
        holder.updateOldUsedSlots();

        // Populate the inventory
        updateDisplay(pageInv, spawner, page, totalPages);
        return pageInv;
    }

    public void updateDisplay(Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Track both changes and slots that need to be emptied
        Map<Integer, ItemStack> updates = new HashMap<>();
        Set<Integer> slotsToEmpty = new HashSet<>();

        // Clear storage area slots first
        for (int i = 0; i < StoragePageHolder.MAX_ITEMS_PER_PAGE; i++) {
            slotsToEmpty.add(i);
        }

        // Add items from virtual inventory
        addPageItems(updates, slotsToEmpty, spawner, page);

        // Add navigation buttons
        addNavigationButtons(updates, spawner, page, totalPages);

        // Apply all updates in a batch
        for (int slot : slotsToEmpty) {
            if (!updates.containsKey(slot)) {
                inventory.setItem(slot, null);
            }
        }

        for (Map.Entry<Integer, ItemStack> entry : updates.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }

        // Update hologram if enabled
        if (plugin.getConfig().getBoolean("hologram.enabled", false)) {
            Scheduler.runLocationTask(spawner.getSpawnerLocation(), spawner::updateHologramData);
        }

        // Check if we need to update total pages
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder();
        assert holder != null;
        int oldUsedSlots = holder.getOldUsedSlots();
        int currentUsedSlots = spawner.getVirtualInventory().getUsedSlots();

        // Only recalculate total pages if there's a significant change
        if (oldUsedSlots != currentUsedSlots) {
            int newTotalPages = calculateTotalPages(spawner);
            holder.setTotalPages(newTotalPages);
            holder.updateOldUsedSlots();
        }
    }

    private void addPageItems(Map<Integer, ItemStack> updates, Set<Integer> slotsToEmpty,
                              SpawnerData spawner, int page) {
        // Get display items directly from virtual inventory
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();

        if (displayItems.isEmpty()) {
            return;
        }

        // Calculate start index for current page
        int startIndex = (page - 1) * StoragePageHolder.MAX_ITEMS_PER_PAGE;

        // Add items for this page
        for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
            int globalIndex = entry.getKey();

            // Check if item belongs on this page
            if (globalIndex >= startIndex && globalIndex < startIndex + StoragePageHolder.MAX_ITEMS_PER_PAGE) {
                int displaySlot = globalIndex - startIndex;
                updates.put(displaySlot, entry.getValue());
                slotsToEmpty.remove(displaySlot);
            }
        }
    }

    private void addNavigationButtons(Map<Integer, ItemStack> updates, SpawnerData spawner, int page, int totalPages) {
        if (totalPages == -1) {
            totalPages = calculateTotalPages(spawner);
        }

        // Navigation row base slot
        int navRowBase = NAVIGATION_ROW * 9;

        // Add previous page button if not on first page
        if (page > 1) {
            String cacheKey = "prev-" + (page - 1);
            ItemStack prevButton = navigationButtonCache.computeIfAbsent(
                    cacheKey, k -> createNavigationButton("previous", page - 1));
            updates.put(navRowBase + 3, prevButton);
        }

        // Add next page button if not on last page
        if (page < totalPages) {
            String cacheKey = "next-" + (page + 1);
            ItemStack nextButton = navigationButtonCache.computeIfAbsent(
                    cacheKey, k -> createNavigationButton("next", page + 1));
            updates.put(navRowBase + 5, nextButton);
        }

        // Add take all button in the center position (where page indicator was)
        updates.put(navRowBase + 4, staticButtons.get("takeAll"));

        // Add discard all button in the first position
        updates.put(navRowBase, staticButtons.get("discardAll"));

        // Add item filter button
        updates.put(navRowBase + 1, staticButtons.get("itemFilter"));

        // Add return button
        updates.put(navRowBase + 8, staticButtons.get("return"));

        // Add shop page indicator only if shop integration is available
        if (plugin.hasShopIntegration()) {
            String indicatorKey = getPageIndicatorKey(page, totalPages, spawner);
            int finalTotalPages = totalPages;
            ItemStack shopIndicator = pageIndicatorCache.computeIfAbsent(
                    indicatorKey, k -> createShopPageIndicator(page, finalTotalPages, spawner)
            );
            updates.put(navRowBase + 7, shopIndicator);
        }
    }

    private String getPageIndicatorKey(int page, int totalPages, SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        int usedSlots = virtualInv.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        return page + "-" + totalPages + "-" + usedSlots + "-" + maxSlots;
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavigationButton(String type, int targetPage) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("target_page", String.valueOf(targetPage));

        String buttonName;
        String buttonKey;

        if (type.equals("previous")) {
            buttonKey = "navigation_button_previous";
        } else {
            buttonKey = "navigation_button_next";
        }

        buttonName = languageManager.getGuiItemName(buttonKey + ".name", placeholders);
        String[] buttonLore = languageManager.getGuiItemLore(buttonKey + ".lore", placeholders);

        return createButton(Material.SPECTRAL_ARROW, buttonName, Arrays.asList(buttonLore));
    }

    private ItemStack createShopPageIndicator(int currentPage, int totalPages, SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int usedSlots = virtualInv.getUsedSlots();
        int percentStorage = maxSlots > 0 ? (int) ((double) usedSlots / maxSlots * 100) : 0;

        String formattedMaxSlots = languageManager.formatNumber(maxSlots);
        String formattedUsedSlots = languageManager.formatNumber(usedSlots);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("current_page", String.valueOf(currentPage));
        placeholders.put("total_pages", String.valueOf(totalPages));
        placeholders.put("max_slots", formattedMaxSlots);
        placeholders.put("used_slots", formattedUsedSlots);
        placeholders.put("percent_storage", String.valueOf(percentStorage));

        String name = languageManager.getGuiItemName("shop_page_indicator.name", placeholders);
        List<String> lore = languageManager.getGuiItemLoreAsList("shop_page_indicator.lore", placeholders);

        return createButton(Material.GOLD_INGOT, name, lore);
    }

    private void startCleanupTask() {
        cleanupTask = Scheduler.runTaskTimer(this::cleanupCaches, 20L * 30, 20L * 30); // Run every 30 seconds
    }

    public void cancelTasks() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    private void cleanupCaches() {
        // LRU-like cleanup for navigation buttons
        if (navigationButtonCache.size() > MAX_CACHE_SIZE) {
            int toRemove = navigationButtonCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(navigationButtonCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                navigationButtonCache.remove(keysToRemove.get(i));
            }
        }

        // LRU-like cleanup for page indicators
        if (pageIndicatorCache.size() > MAX_CACHE_SIZE) {
            int toRemove = pageIndicatorCache.size() - (MAX_CACHE_SIZE / 2);
            List<String> keysToRemove = new ArrayList<>(pageIndicatorCache.keySet());
            for (int i = 0; i < Math.min(toRemove, keysToRemove.size()); i++) {
                pageIndicatorCache.remove(keysToRemove.get(i));
            }
        }
    }

    public void cleanup() {
        navigationButtonCache.clear();
        pageIndicatorCache.clear();

        // Cancel scheduled tasks
        cancelTasks();

        // Re-initialize static buttons (just in case language has changed)
        initializeStaticButtons();
    }
}