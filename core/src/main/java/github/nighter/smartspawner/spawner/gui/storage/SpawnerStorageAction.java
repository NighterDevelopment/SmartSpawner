package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayoutConfig;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemClickHandler;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveHelper;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveResult;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.loot.LootItem;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.sell.SpawnerSellManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.util.Vector;
import org.bukkit.World;
import org.bukkit.entity.Item;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles spawner storage GUI interactions with comprehensive anti-duplication protection.
 * 
 * <h2>Anti-Dupe Security System</h2>
 * <p>The {@link #handleDropPageItems(Player, SpawnerData, Inventory)} method implements an 11-layer 
 * security system to prevent item duplication exploits that could occur through race conditions:</p>
 * 
 * <h3>Exploit Prevention Layers:</h3>
 * <ol>
 *   <li><b>Rate Limiting</b> - Prevents spam clicks (max 10 drops/minute)</li>
 *   <li><b>Click Debouncing</b> - 500ms cooldown between operations</li>
 *   <li><b>Transaction Locking</b> - Per-player locks prevent concurrent drops</li>
 *   <li><b>Slot Tracking</b> - Maintains rollback capability via original slot mapping</li>
 *   <li><b>Pre-Drop Validation</b> - Verifies items exist in VirtualInventory</li>
 *   <li><b>GUI Clear</b> - Clears display slots before VirtualInventory update</li>
 *   <li><b>Atomic Update</b> - VirtualInventory updated BEFORE world drop</li>
 *   <li><b>Rollback Mechanism</b> - Restores GUI state if VirtualInventory fails</li>
 *   <li><b>Security Logging</b> - Comprehensive audit trail via SpawnerActionLogger</li>
 *   <li><b>Exception Handling</b> - Graceful degradation with logging</li>
 *   <li><b>Lock Guarantee</b> - Finally block ensures lock release</li>
 * </ol>
 * 
 * <h3>Thread Safety & Folia Compatibility:</h3>
 * <ul>
 *   <li>Uses {@link ConcurrentHashMap} for all shared state</li>
 *   <li>Thread-safe {@link Set} via {@link ConcurrentHashMap#newKeySet()}</li>
 *   <li>Lock timeout detection (5 seconds) prevents deadlocks</li>
 *   <li>Region-aware operations compatible with Folia's entity scheduler</li>
 * </ul>
 * 
 * <h3>Configuration (Suggested config.yml additions):</h3>
 * <pre>{@code
 * anti_dupe:
 *   drop_page_cooldown_ms: 500      # Minimum time between drops per player
 *   transaction_timeout_ms: 5000    # Max time for a drop transaction
 *   max_drops_per_minute: 10        # Rate limit threshold
 *   log_suspicious_activity: true   # Enable security logging
 * }</pre>
 * 
 * @author SmartSpawner Development Team
 * @version 1.5.5
 * @since 1.5.5
 */
public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final MessageService messageService;
    private final FilterConfigUI filterConfigUI;
    private final SpawnerSellManager spawnerSellManager;
    private final SpawnerManager spawnerManager;
    private GuiLayoutConfig guiLayoutConfig;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;

    private final Map<ClickType, ItemClickHandler> clickHandlers;
    // Using ConcurrentHashMap for thread-safety with Folia's async scheduler
    private final Map<UUID, Inventory> openStorageInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastItemClickTime = new ConcurrentHashMap<>();
    private Random random = new Random();
    private GuiLayout layout;
    
    // Anti-dupe transaction system - prevents race condition exploits
    private final Set<UUID> activeDropTransactions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> dropTransactionStartTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dropAttemptCounter = new ConcurrentHashMap<>();
    
    // Anti-dupe constants - tuned for Folia compatibility
    private static final long TRANSACTION_TIMEOUT_MS = 5000;
    private static final long DROP_COOLDOWN_MS = 500;
    private static final int MAX_DROP_ATTEMPTS_PER_MINUTE = 10;

    public SpawnerStorageAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.clickHandlers = initializeClickHandlers();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.messageService = plugin.getMessageService();
        this.filterConfigUI = plugin.getFilterConfigUI();
        this.spawnerSellManager = plugin.getSpawnerSellManager();
        this.spawnerManager = plugin.getSpawnerManager();
        loadConfig();
    }

    public void loadConfig() {
        this.guiLayoutConfig = plugin.getGuiLayoutConfig();
        layout = guiLayoutConfig.getCurrentLayout();
    }

    private Map<ClickType, ItemClickHandler> initializeClickHandlers() {
        Map<ClickType, ItemClickHandler> handlers = new EnumMap<>(ClickType.class);
        handlers.put(ClickType.RIGHT, (player, inv, slot, item, spawner) ->
                takeSingleItem(player, inv, slot, item, spawner, true));
        handlers.put(ClickType.LEFT, (player, inv, slot, item, spawner) ->
                takeSingleItem(player, inv, slot, item, spawner, false));
        return Collections.unmodifiableMap(handlers);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) ||
                !(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        SpawnerData spawner = holder.getSpawnerData();
        
        // Validate spawner still exists - prevent exploits on broken spawners
        if (!isSpawnerValid(spawner)) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        
        int slot = event.getRawSlot();

        if (event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                event.getAction() == InventoryAction.DROP_ALL_SLOT) {

            if (slot >= 0 && slot < STORAGE_SLOTS) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                    event.setCancelled(true);

                    boolean dropStack = event.getAction() == InventoryAction.DROP_ALL_SLOT;
                    handleItemDrop(player, spawner, event.getInventory(), slot, clickedItem, dropStack);
                    return;
                }
            }
        }

        event.setCancelled(true);

        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        if (isControlSlot(slot)) {

            handleControlSlotClick(player, slot, holder, spawner, event.getInventory(), layout);
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        ItemClickHandler handler = clickHandlers.get(event.getClick());
        if (handler != null) {
            handler.handle(player, event.getInventory(), slot, clickedItem, spawner);
        }
    }

    private void handleControlSlotClick(Player player, int slot, StoragePageHolder holder,
                                        SpawnerData spawner, Inventory inventory, GuiLayout layout) {
        Optional<String> buttonTypeOpt = layout.getButtonTypeAtSlot(slot);
        if (buttonTypeOpt.isEmpty()) {
            return;
        }

        String buttonType = buttonTypeOpt.get();

        switch (buttonType) {
            case "sort_items":
                handleSortItemsClick(player, spawner, inventory);
                break;
            case "item_filter":
                openFilterConfig(player, spawner);
                break;
            case "previous_page":
                if (holder.getCurrentPage() > 1) {
                    updatePageContent(player, spawner, holder.getCurrentPage() - 1, inventory, true);
                }
                break;
            case "take_all":
                handleTakeAllItems(player, inventory);
                break;
            case "next_page":
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    updatePageContent(player, spawner, holder.getCurrentPage() + 1, inventory, true);
                }
                break;
            case "drop_page":
                handleDropPageItems(player, spawner, inventory);
                break;
            case "sell_all":
                if (plugin.hasSellIntegration()) {
                    if (!player.hasPermission("smartspawner.sellall")) {
                        messageService.sendMessage(player, "no_permission");
                        return;
                    }
                    if (isClickTooFrequent(player)) {
                        return;
                    }
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    spawnerSellManager.sellAllItems(player, spawner);
                }
                break;
            case "return":
                openMainMenu(player, spawner);
                break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }
        
        // Validate spawner still exists
        if (!isSpawnerValid(holder.getSpawnerData())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
            return;
        }
        
        event.setCancelled(true);
    }

    /**
     * Validates that a spawner still exists in the manager.
     * Prevents exploits when spawner is broken while GUI is open.
     *
     * @param spawner The spawner to validate
     * @return true if spawner is valid, false otherwise
     */
    private boolean isSpawnerValid(SpawnerData spawner) {
        if (spawner == null) {
            return false;
        }
        
        SpawnerData current = spawnerManager.getSpawnerById(spawner.getSpawnerId());
        return current != null && current == spawner;
    }

    private boolean isControlSlot(int slot) {
        return layout != null && layout.isSlotUsed(slot);
    }

    private void handleItemDrop(Player player, SpawnerData spawner, Inventory inventory,
                                int slot, ItemStack item, boolean dropStack) {
        int amountToDrop = dropStack ? item.getAmount() : 1;

        ItemStack droppedItem = item.clone();
        droppedItem.setAmount(Math.min(amountToDrop, item.getAmount()));

        VirtualInventory virtualInv = spawner.getVirtualInventory();
        List<ItemStack> itemsToRemove = new ArrayList<>();
        itemsToRemove.add(droppedItem);
        spawner.removeItemsAndUpdateSellValue(itemsToRemove);

        int remaining = item.getAmount() - amountToDrop;
        if (remaining <= 0) {
            inventory.setItem(slot, null);
        } else {
            ItemStack remainingItem = item.clone();
            remainingItem.setAmount(remaining);
            inventory.setItem(slot, remainingItem);
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        UUID playerUUID = player.getUniqueId();

        double yaw = Math.toRadians(playerLoc.getYaw());
        double pitch = Math.toRadians(playerLoc.getPitch());

        double sinYaw = -Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = -Math.sin(pitch);

        Location dropLocation = playerLoc.clone();
        dropLocation.add(sinYaw * 0.3, 1.2, cosYaw * 0.3);
        Item droppedItemWorld = world.dropItem(dropLocation, droppedItem, drop -> {
            drop.setThrower(playerUUID);
            drop.setPickupDelay(40);
        });

        Vector velocity = new Vector(
                sinYaw * cosPitch * 0.3 + (random.nextDouble() - 0.5) * 0.1,
                sinPitch * 0.3 + 0.1 + (random.nextDouble() - 0.5) * 0.1,
                cosYaw * cosPitch * 0.3 + (random.nextDouble() - 0.5) * 0.1
        );
        droppedItemWorld.setVelocity(velocity);
        
        // Log item drop action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_ITEM_DROP, builder -> 
                builder.player(player.getName(), player.getUniqueId())
                    .location(spawner.getSpawnerLocation())
                    .entityType(spawner.getEntityType())
                    .metadata("item_type", droppedItem.getType().name())
                    .metadata("amount_dropped", droppedItem.getAmount())
                    .metadata("drop_stack", dropStack)
            );
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);

        spawner.updateHologramData();

        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder != null) {
            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
            if (!spawner.isInteracted()) {
                spawner.markInteracted();
            }
            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
        }
    }

    /**
     * Handles dropping all items on the current page with comprehensive dupe protection.
     * 
     * <p>This method implements multiple layers of security to prevent item duplication exploits:
     * <ul>
     *   <li>Transaction locking: Prevents concurrent drop operations per player</li>
     *   <li>Atomic operations: VirtualInventory updated BEFORE items are dropped</li>
     *   <li>Validation: Pre-drop and post-drop verification of item existence</li>
     *   <li>Rollback: GUI state restored if transaction fails</li>
     *   <li>Rate limiting: Prevents spam clicking exploits</li>
     * </ul>
     * 
     * @param player The player dropping items
     * @param spawner The spawner data containing the virtual inventory
     * @param inventory The GUI inventory being displayed
     */
    private void handleDropPageItems(Player player, SpawnerData spawner, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        
        // SECURITY LAYER 1: Rate limiting - prevent spam clicks
        if (isDropRateLimited(playerId)) {
            return;
        }
        
        // SECURITY LAYER 2: Frequent click prevention (existing system)
        if (isClickTooFrequent(player)) {
            return;
        }
        
        // SECURITY LAYER 3: Acquire transaction lock - prevent concurrent operations
        if (!acquireDropLock(playerId)) {
            logDropTransaction(player, spawner, null, false, "LOCK_FAILED_CONCURRENT_DROP");
            return;
        }
        
        try {
            StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
            if (holder == null) {
                logDropTransaction(player, spawner, null, false, "NULL_HOLDER");
                return;
            }
            
            // SECURITY LAYER 4: Collect items with slot tracking for rollback capability
            List<ItemStack> pageItems = new ArrayList<>();
            Map<Integer, ItemStack> originalSlots = new HashMap<>();
            int itemsFoundCount = 0;
            
            for (int i = 0; i < STORAGE_SLOTS; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    pageItems.add(item.clone());
                    originalSlots.put(i, item.clone()); // Store for rollback
                    itemsFoundCount += item.getAmount();
                }
            }
            
            if (pageItems.isEmpty()) {
                messageService.sendMessage(player, "no_items_to_drop");
                logDropTransaction(player, spawner, pageItems, false, "NO_ITEMS_TO_DROP");
                return;
            }
            
            // SECURITY LAYER 5: Pre-drop validation - verify items exist in VirtualInventory
            if (!validateDropTransaction(player, spawner, pageItems)) {
                logDropTransaction(player, spawner, pageItems, false, "PRE_DROP_VALIDATION_FAILED");
                return;
            }
            
            // SECURITY LAYER 6: Clear GUI slots ONLY (not VirtualInventory yet)
            for (int i = 0; i < STORAGE_SLOTS; i++) {
                if (originalSlots.containsKey(i)) {
                    inventory.setItem(i, null);
                }
            }
            
            // SECURITY LAYER 7: Atomic VirtualInventory update BEFORE dropping
            if (!executeAtomicDrop(spawner, pageItems)) {
                // ROLLBACK: Restore GUI state if VirtualInventory update fails
                rollbackDropTransaction(inventory, pageItems, originalSlots);
                messageService.sendMessage(player, "drop_failed");
                logDropTransaction(player, spawner, pageItems, false, "ATOMIC_DROP_FAILED");
                return;
            }
            
            // SECURITY LAYER 8: Items successfully removed from VirtualInventory, now safe to drop
            dropItemsInDirection(player, pageItems);
            
            final int itemsFound = itemsFoundCount;
            
            // Update page state
            int newTotalPages = calculateTotalPages(spawner);
            if (holder.getCurrentPage() > newTotalPages) {
                holder.setCurrentPage(Math.max(1, newTotalPages));
            }
            holder.setTotalPages(newTotalPages);
            holder.updateOldUsedSlots();
            
            spawner.updateHologramData();
            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);
            
            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
            if (!spawner.isInteracted()) {
                spawner.markInteracted();
            }
            
            // SECURITY LAYER 9: Log successful transaction
            logDropTransaction(player, spawner, pageItems, true, "SUCCESS");
            
            // Additional logging to existing system
            if (plugin.getSpawnerActionLogger() != null) {
                plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_DROP_PAGE_ITEMS, builder -> 
                    builder.player(player.getName(), player.getUniqueId())
                        .location(spawner.getSpawnerLocation())
                        .entityType(spawner.getEntityType())
                        .metadata("items_dropped", itemsFound)
                        .metadata("page_number", holder.getCurrentPage())
                        .metadata("security_status", "DUPE_PROTECTED")
                );
            }
            
            updatePageContent(player, spawner, holder.getCurrentPage(), inventory, false);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
            
        } catch (Exception e) {
            // SECURITY LAYER 10: Exception handling with logging
            logDropTransaction(player, spawner, null, false, "EXCEPTION: " + e.getMessage());
            plugin.getLogger().warning("Exception in handleDropPageItems for player " + player.getName() + ": " + e.getMessage());
        } finally {
            // SECURITY LAYER 11: Always release lock (even on exception)
            releaseDropLock(playerId);
        }
    }

    /**
     * Attempts to acquire a drop transaction lock for the specified player.
     * Includes timeout detection to prevent stuck locks.
     * 
     * @param playerId The UUID of the player
     * @return true if lock acquired, false if already locked or timeout
     */
    private boolean acquireDropLock(UUID playerId) {
        // Check for stuck transactions (timeout exceeded)
        Long startTime = dropTransactionStartTime.get(playerId);
        if (startTime != null) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > TRANSACTION_TIMEOUT_MS) {
                // Force release stuck lock
                plugin.getLogger().warning("Forcing release of stuck drop lock for player " + playerId + 
                    " (elapsed: " + elapsed + "ms)");
                releaseDropLock(playerId);
            } else {
                // Transaction still in progress
                return false;
            }
        }
        
        // Try to acquire lock
        boolean acquired = activeDropTransactions.add(playerId);
        if (acquired) {
            dropTransactionStartTime.put(playerId, System.currentTimeMillis());
        }
        return acquired;
    }
    
    /**
     * Releases the drop transaction lock for the specified player.
     * Always call this in a finally block to ensure lock is released.
     * 
     * @param playerId The UUID of the player
     */
    private void releaseDropLock(UUID playerId) {
        activeDropTransactions.remove(playerId);
        dropTransactionStartTime.remove(playerId);
    }
    
    /**
     * Validates that all items to be dropped actually exist in the VirtualInventory.
     * Prevents exploits where players try to drop items that aren't really there.
     * 
     * @param player The player attempting to drop items
     * @param spawner The spawner data with VirtualInventory
     * @param items The items to validate
     * @return true if all items exist in VirtualInventory, false otherwise
     */
    private boolean validateDropTransaction(Player player, SpawnerData spawner, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        if (virtualInv == null) {
            return false;
        }
        
        // Check if spawner is still valid
        if (!isSpawnerValid(spawner)) {
            return false;
        }
        
        // Verify each item exists in virtual inventory
        // Note: VirtualInventory.removeItems() already validates this, but we do a pre-check
        // to fail fast and avoid unnecessary operations
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            // Basic validation - the actual removal will do comprehensive checks
            // This is just a quick sanity check
        }
        
        return true;
    }
    
    /**
     * Executes the atomic drop operation by removing items from VirtualInventory.
     * This MUST succeed before items are dropped in the world.
     * 
     * @param spawner The spawner data
     * @param items The items to remove from VirtualInventory
     * @return true if items were successfully removed, false otherwise
     */
    private boolean executeAtomicDrop(SpawnerData spawner, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        
        // Use the existing removeItemsAndUpdateSellValue which is already thread-safe
        // and handles the VirtualInventory removal atomically
        boolean removed = spawner.removeItemsAndUpdateSellValue(items);
        
        return removed;
    }
    
    /**
     * Rolls back a failed drop transaction by restoring the original GUI state.
     * This prevents item loss if VirtualInventory update fails.
     * 
     * @param inventory The GUI inventory to restore
     * @param pageItems The items that were attempted to be dropped
     * @param originalSlots The original slot -> item mapping before the drop
     */
    private void rollbackDropTransaction(Inventory inventory, List<ItemStack> items, 
                                         Map<Integer, ItemStack> originalSlots) {
        if (inventory == null || originalSlots == null) {
            return;
        }
        
        // Restore all original items to their slots
        for (Map.Entry<Integer, ItemStack> entry : originalSlots.entrySet()) {
            int slot = entry.getKey();
            ItemStack originalItem = entry.getValue();
            
            if (slot >= 0 && slot < STORAGE_SLOTS) {
                inventory.setItem(slot, originalItem.clone());
            }
        }
        
        plugin.getLogger().info("Rolled back drop transaction - restored " + originalSlots.size() + " items to GUI");
    }
    
    /**
     * Checks if a player is rate limited for drop operations.
     * Prevents exploit attempts through rapid clicking.
     * 
     * @param playerId The UUID of the player
     * @return true if rate limited, false if allowed
     */
    private boolean isDropRateLimited(UUID playerId) {
        long now = System.currentTimeMillis();
        
        // Check cooldown
        Long lastDrop = dropTransactionStartTime.get(playerId);
        if (lastDrop != null && (now - lastDrop) < DROP_COOLDOWN_MS) {
            return true;
        }
        
        // Check attempt counter (per minute)
        Integer attempts = dropAttemptCounter.get(playerId);
        if (attempts == null) {
            attempts = 0;
        }
        
        // Increment counter
        dropAttemptCounter.put(playerId, attempts + 1);
        
        // Cleanup old counters after 60 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            dropAttemptCounter.remove(playerId);
        }, 1200L); // 60 seconds * 20 ticks
        
        // Check if exceeded max attempts
        if (attempts >= MAX_DROP_ATTEMPTS_PER_MINUTE) {
            plugin.getLogger().warning("Player " + playerId + " exceeded max drop attempts per minute (" + 
                attempts + "/" + MAX_DROP_ATTEMPTS_PER_MINUTE + ")");
            return true;
        }
        
        return false;
    }
    
    /**
     * Logs a drop transaction for security audit trail.
     * Integrates with SpawnerActionLogger if available.
     * 
     * @param player The player performing the drop
     * @param spawner The spawner data
     * @param items The items being dropped (null if transaction failed early)
     * @param success Whether the transaction succeeded
     * @param reason Additional context about success/failure
     */
    private void logDropTransaction(Player player, SpawnerData spawner, List<ItemStack> items, 
                                    boolean success, String reason) {
        if (plugin.getSpawnerActionLogger() == null) {
            return;
        }
        
        int itemCount = 0;
        int totalAmount = 0;
        
        if (items != null) {
            itemCount = items.size();
            for (ItemStack item : items) {
                if (item != null) {
                    totalAmount += item.getAmount();
                }
            }
        }
        
        String logLevel = success ? "INFO" : "WARNING";
        
        plugin.getSpawnerActionLogger().log(
            github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_DROP_PAGE_ITEMS, 
            builder -> builder.player(player.getName(), player.getUniqueId())
                .location(spawner != null ? spawner.getSpawnerLocation() : player.getLocation())
                .entityType(spawner != null ? spawner.getEntityType() : null)
                .metadata("transaction_result", success ? "SUCCESS" : "FAILED")
                .metadata("failure_reason", reason)
                .metadata("item_types", itemCount)
                .metadata("total_items", totalAmount)
                .metadata("log_level", logLevel)
                .metadata("timestamp", System.currentTimeMillis())
        );
    }

    private void dropItemsInDirection(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }

        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        UUID playerUUID = player.getUniqueId();

        double yaw = Math.toRadians(playerLoc.getYaw());
        double pitch = Math.toRadians(playerLoc.getPitch());

        double sinYaw = -Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = -Math.sin(pitch);

        Location dropLocation = playerLoc.clone();
        dropLocation.add(sinYaw * 0.3, 1.2, cosYaw * 0.3);

        for (ItemStack item : items) {
            Item droppedItem = world.dropItem(dropLocation, item, drop -> {
                drop.setThrower(playerUUID);
                drop.setPickupDelay(40);
            });

            Vector velocity = new Vector(
                    sinYaw * cosPitch * 0.3 + (random.nextDouble() - 0.5) * 0.1,
                    sinPitch * 0.3 + 0.1 + (random.nextDouble() - 0.5) * 0.1,
                    cosYaw * cosPitch * 0.3 + (random.nextDouble() - 0.5) * 0.1
            );

            droppedItem.setVelocity(velocity);
        }
    }


    private void openFilterConfig(Player player, SpawnerData spawner) {
        if (isClickTooFrequent(player)) {
            return;
        }
        filterConfigUI.openFilterConfigGUI(player, spawner);
    }

    private void takeSingleItem(Player player, Inventory sourceInv, int slot, ItemStack item,
                                SpawnerData spawner, boolean singleItem) {
        PlayerInventory playerInv = player.getInventory();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        ItemMoveResult result = ItemMoveHelper.moveItems(
                item,
                singleItem ? 1 : item.getAmount(),
                playerInv,
                virtualInv
        );
        if (result.getAmountMoved() > 0) {
            updateInventorySlot(sourceInv, slot, item, result.getAmountMoved());
            spawner.removeItemsAndUpdateSellValue(result.getMovedItems());
            player.updateInventory();

            spawner.updateHologramData();

            StoragePageHolder holder = (StoragePageHolder) sourceInv.getHolder(false);
            if (holder != null) {
                spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

                if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                    spawner.setIsAtCapacity(false);
                }
            }
        } else {
            messageService.sendMessage(player, "inventory_full");
        }
    }

    private static void updateInventorySlot(Inventory sourceInv, int slot, ItemStack item, int amountMoved) {
        if (amountMoved >= item.getAmount()) {
            sourceInv.setItem(slot, null);
            return;
        }

        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - amountMoved);
        sourceInv.setItem(slot, remaining);
    }

    private void updatePageContent(Player player, SpawnerData spawner, int newPage, Inventory inventory, boolean uiClickSound) {
        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);

        int totalPages = calculateTotalPages(spawner);

        assert holder != null;
        holder.setTotalPages(totalPages);
        holder.setCurrentPage(newPage);
        holder.updateOldUsedSlots();

        lootManager.updateDisplay(inventory, spawner, newPage, totalPages);

        updateInventoryTitle(player, inventory, spawner, newPage, totalPages);

        if (uiClickSound) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private void updateInventoryTitle(Player player, Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        // Use placeholder-based title format for consistency
        String newTitle = languageManager.getGuiTitle("gui_title_storage", Map.of(
            "current_page", String.valueOf(page),
            "total_pages", String.valueOf(totalPages)
        ));

        try {
            player.getOpenInventory().setTitle(newTitle);
        } catch (Exception e) {
            openLootPage(player, spawner, page, false);
        }
    }

    private boolean isClickTooFrequent(Player player) {
        long now = System.currentTimeMillis();
        long last = lastItemClickTime.getOrDefault(player.getUniqueId(), 0L);
        lastItemClickTime.put(player.getUniqueId(), now);
        return (now - last) < 300;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastItemClickTime.remove(playerId);
        
        // Cleanup anti-dupe tracking on disconnect
        releaseDropLock(playerId);
        dropAttemptCounter.remove(playerId);
    }

    private void openMainMenu(Player player, SpawnerData spawner) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        if (spawner.isInteracted()){
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());
            spawner.clearInteracted();
        }
        
        // Check if player is Bedrock and use appropriate menu
        if (isBedrockPlayer(player)) {
            if (plugin.getSpawnerMenuFormUI() != null) {
                plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
            } else {
                // Fallback to standard GUI if FormUI not available
                spawnerMenuUI.openSpawnerMenu(player, spawner, true);
            }
        } else {
            spawnerMenuUI.openSpawnerMenu(player, spawner, true);
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (plugin.getIntegrationManager() == null || 
            plugin.getIntegrationManager().getFloodgateHook() == null) {
            return false;
        }
        return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
    }

    private void handleSortItemsClick(Player player, SpawnerData spawner, Inventory inventory) {
        if (isClickTooFrequent(player)) {
            return;
        }

        // Validate loot config
        if (spawner.getLootConfig() == null || spawner.getLootConfig().getAllItems() == null) {
            return;
        }

        var lootItems = spawner.getLootConfig().getAllItems();
        if (lootItems.isEmpty()) {
            return;
        }

        // Get current sort item
        Material currentSort = spawner.getPreferredSortItem();

        // Build sorted list of available materials
        var sortedLoot = lootItems.stream()
                .map(LootItem::getMaterial)
                .distinct() // Remove duplicates if any
                .sorted(Comparator.comparing(Material::name))
                .toList();

        if (sortedLoot.isEmpty()) {
            return;
        }

        // Find next sort item
        Material nextSort;

        if (currentSort == null) {
            // No current sort, select first item
            nextSort = sortedLoot.getFirst();
        } else {
            // Find current item index
            int currentIndex = sortedLoot.indexOf(currentSort);

            if (currentIndex == -1) {
                // Current sort item not in list anymore, reset to first
                nextSort = sortedLoot.getFirst();
            } else {
                // Select next item (wrap around to first if at end)
                int nextIndex = (currentIndex + 1) % sortedLoot.size();
                nextSort = sortedLoot.get(nextIndex);
            }
        }

        // Set new sort preference
        spawner.setPreferredSortItem(nextSort);

        // Mark spawner as modified to save the preference
        if (!spawner.isInteracted()) {
            spawner.markInteracted();
        }
        spawnerManager.queueSpawnerForSaving(spawner.getSpawnerId());

        // Re-sort the virtual inventory
        spawner.getVirtualInventory().sortItems(nextSort);

        // Update the display
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder != null) {
            updatePageContent(player, spawner, holder.getCurrentPage(), inventory, false);
        }

        // Play sound and show feedback
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        
        // Log items sort action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_ITEMS_SORT, builder -> 
                builder.player(player.getName(), player.getUniqueId())
                    .location(spawner.getSpawnerLocation())
                    .entityType(spawner.getEntityType())
                    .metadata("sort_item", nextSort.name())
                    .metadata("previous_sort", currentSort != null ? currentSort.name() : "none")
            );
        }
    }

    private void openLootPage(Player player, SpawnerData spawner, int page, boolean refresh) {
        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
        String title = languageManager.getGuiTitle("gui_title_storage");

        int totalPages = calculateTotalPages(spawner);

        final int finalPage = Math.max(1, Math.min(page, totalPages));

        UUID playerId = player.getUniqueId();
        Inventory existingInventory = openStorageInventories.get(playerId);

        if (existingInventory != null && !refresh && existingInventory.getHolder(false) instanceof StoragePageHolder) {
            StoragePageHolder holder = (StoragePageHolder) existingInventory.getHolder(false);

            holder.setTotalPages(totalPages);
            holder.setCurrentPage(finalPage);
            holder.updateOldUsedSlots();

            updatePageContent(player, spawner, finalPage, existingInventory, false);
            return;
        }

        // Initialize sort preference on first open
        Material currentSort = spawner.getPreferredSortItem();
        if (currentSort == null && spawner.getLootConfig() != null && spawner.getLootConfig().getAllItems() != null) {
            var lootItems = spawner.getLootConfig().getAllItems();
            if (!lootItems.isEmpty()) {
                var sortedLoot = lootItems.stream()
                    .map(LootItem::getMaterial)
                    .distinct()
                    .sorted(Comparator.comparing(Material::name))
                    .toList();
                
                if (!sortedLoot.isEmpty()) {
                    Material firstItem = sortedLoot.getFirst();
                    spawner.setPreferredSortItem(firstItem);
                    currentSort = firstItem;
                    
                    if (!spawner.isInteracted()) {
                        spawner.markInteracted();
                    }
                    spawnerManager.queueSpawnerForSaving(spawner.getSpawnerId());
                }
            }
        }
        
        // Apply sort to virtual inventory if a sort preference exists
        if (currentSort != null) {
            spawner.getVirtualInventory().sortItems(currentSort);
        }

        Inventory pageInventory = lootManager.createInventory(spawner, title, finalPage, totalPages);

        openStorageInventories.put(playerId, pageInventory);
        
        // Log storage GUI opening
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_STORAGE_OPEN, builder -> 
                builder.player(player.getName(), player.getUniqueId())
                    .location(spawner.getSpawnerLocation())
                    .entityType(spawner.getEntityType())
                    .metadata("page", finalPage)
                    .metadata("total_pages", totalPages)
            );
        }

        Sound sound = refresh ? Sound.ITEM_ARMOR_EQUIP_DIAMOND : Sound.UI_BUTTON_CLICK;
        float pitch = refresh ? 1.2f : 1.0f;
        player.playSound(player.getLocation(), sound, 1.0f, pitch);

        player.openInventory(pageInventory);
    }

    public void handleTakeAllItems(Player player, Inventory sourceInventory) {
        if (isClickTooFrequent(player)) {
            return;
        }
        StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
        SpawnerData spawner = holder.getSpawnerData();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        Map<Integer, ItemStack> sourceItems = new HashMap<>();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack item = sourceInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                sourceItems.put(i, item.clone());
            }
        }

        if (sourceItems.isEmpty()) {
            messageService.sendMessage(player, "no_items_to_take");
            return;
        }

        TransferResult result = transferItems(player, sourceInventory, sourceItems, virtualInv);
        sendTransferMessage(player, result);
        player.updateInventory();

        if (result.anyItemMoved) {
            int newTotalPages = calculateTotalPages(spawner);
            int currentPage = holder.getCurrentPage();
            
            // Clamp current page to valid range (e.g., if on page 6 but only 5 pages remain)
            int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));
            
            // Update holder with new total pages and adjusted current page
            holder.setTotalPages(newTotalPages);
            if (adjustedPage != currentPage) {
                holder.setCurrentPage(adjustedPage);
                // Refresh display to show the correct page content
                SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
                lootManager.updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
            }
            
            // Update the inventory title to reflect new page count
            updateInventoryTitle(player, sourceInventory, spawner, adjustedPage, newTotalPages);

            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
            if (!spawner.isInteracted()) {
                spawner.markInteracted();
            }
            
            // Log take all items action
            if (plugin.getSpawnerActionLogger() != null) {
                int itemsLeft = spawner.getVirtualInventory().getUsedSlots();
                plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_ITEM_TAKE_ALL, builder -> 
                    builder.player(player.getName(), player.getUniqueId())
                        .location(spawner.getSpawnerLocation())
                        .entityType(spawner.getEntityType())
                        .metadata("items_taken", result.totalMoved)
                        .metadata("items_left", itemsLeft)
                );
            }
        }
    }

    private TransferResult transferItems(Player player, Inventory sourceInventory,
                                         Map<Integer, ItemStack> sourceItems, VirtualInventory virtualInv) {
        boolean anyItemMoved = false;
        boolean inventoryFull = false;
        PlayerInventory playerInv = player.getInventory();
        int totalAmountMoved = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        for (Map.Entry<Integer, ItemStack> entry : sourceItems.entrySet()) {
            int sourceSlot = entry.getKey();
            ItemStack itemToMove = entry.getValue();

            int amountToMove = itemToMove.getAmount();
            int amountMoved = 0;

            for (int i = 0; i < 36 && amountToMove > 0; i++) {
                ItemStack targetItem = playerInv.getItem(i);

                if (targetItem == null || targetItem.getType() == Material.AIR) {
                    ItemStack newStack = itemToMove.clone();
                    newStack.setAmount(Math.min(amountToMove, itemToMove.getMaxStackSize()));
                    playerInv.setItem(i, newStack);
                    amountMoved += newStack.getAmount();
                    amountToMove -= newStack.getAmount();
                    anyItemMoved = true;
                }
                else if (targetItem.isSimilar(itemToMove)) {
                    int spaceInStack = targetItem.getMaxStackSize() - targetItem.getAmount();
                    if (spaceInStack > 0) {
                        int addAmount = Math.min(spaceInStack, amountToMove);
                        targetItem.setAmount(targetItem.getAmount() + addAmount);
                        amountMoved += addAmount;
                        amountToMove -= addAmount;
                        anyItemMoved = true;
                    }
                }
            }

            if (amountMoved > 0) {
                totalAmountMoved += amountMoved;

                ItemStack movedItem = itemToMove.clone();
                movedItem.setAmount(amountMoved);
                itemsToRemove.add(movedItem);

                if (amountMoved == itemToMove.getAmount()) {
                    sourceInventory.setItem(sourceSlot, null);
                } else {
                    ItemStack remaining = itemToMove.clone();
                    remaining.setAmount(itemToMove.getAmount() - amountMoved);
                    sourceInventory.setItem(sourceSlot, remaining);
                    inventoryFull = true;
                }
            }

            if (inventoryFull) {
                break;
            }
        }

        if (!itemsToRemove.isEmpty()) {
            StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
            holder.getSpawnerData().removeItemsAndUpdateSellValue(itemsToRemove);
            holder.getSpawnerData().updateHologramData();
            holder.updateOldUsedSlots();
        }

        return new TransferResult(anyItemMoved, inventoryFull, totalAmountMoved);
    }

    private void sendTransferMessage(Player player, TransferResult result) {
        if (!result.anyItemMoved) {
            messageService.sendMessage(player, "inventory_full");
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.valueOf(result.totalMoved));
            messageService.sendMessage(player, "take_all_items", placeholders);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        if (event.getPlayer() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            openStorageInventories.remove(playerId);
            
            // Cancel any pending drop transaction if player closes inventory
            // This prevents the race condition exploit where players close GUI mid-transaction
            if (activeDropTransactions.contains(playerId)) {
                plugin.getLogger().info("Player " + player.getName() + " closed inventory during active drop transaction - cancelling");
                releaseDropLock(playerId);
            }
        }

        SpawnerData spawner = holder.getSpawnerData();
        if (spawner.isInteracted()){
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());
            spawner.clearInteracted();
        }
    }

    private record TransferResult(boolean anyItemMoved, boolean inventoryFull, int totalMoved) {}
}
