package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerDropAllEvent;
import github.nighter.smartspawner.api.events.SpawnerTakeAllEvent;
import github.nighter.smartspawner.api.gui.GuiLayoutType;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.storage.filter.FilterConfigUI;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.lootgen.loot.LootItem;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.gui.storage.session.StorageSession;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.*;
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
import org.bukkit.util.Vector;
import org.bukkit.entity.Item;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static github.nighter.smartspawner.spawner.gui.sell.SpawnerSellConfirmUI.PreviousGui.STORAGE;

public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final MessageService messageService;
    private final FilterConfigUI filterConfigUI;
    private final SpawnerManager spawnerManager;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;

    private record TransferResult(boolean anyItemMoved, boolean inventoryFull, int totalMoved) {}
    private final Map<UUID, Long> lastItemClickTime = new ConcurrentHashMap<>();
    private static final long ITEM_CLICK_DELAY_MS = 100;

    public SpawnerStorageAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.messageService = plugin.getMessageService();
        this.filterConfigUI = plugin.getFilterConfigUI();
        this.spawnerManager = plugin.getSpawnerManager();
        loadConfig();
    }

    public void loadConfig() {
        // Layouts are resolved and stored per inventory session.
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) ||
                !(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        SpawnerData spawner = holder.getSpawnerData();
        int slot = event.getRawSlot();

        // Block ALL storage interactions while a sell is in progress.
        if (spawner.isSelling()) {
            event.setCancelled(true);
            plugin.getMessageService().sendMessage(player, "action_in_progress");
            return;
        }

        // Handle clicks outside valid window
        if (slot < 0) {
            return;
        }

        // Handle control button clicks (slots 45-53)
        if (slot >= STORAGE_SLOTS && slot < INVENTORY_SIZE) {
            event.setCancelled(true);
            if (isControlSlot(slot, holder.getLayout())) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                    handleControlSlotClick(
                            player, slot, holder, spawner, event.getInventory(), event.getClick(), holder.getLayout());
                }
            }
            return;
        }

        // Handle clicks inside player inventory (slot >= 54)
        if (slot >= INVENTORY_SIZE) {
            // Block shift-clicking from player inventory into storage
            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }
            // Allow native organization within player inventory
            return;
        }

        // Handle item slot clicks (slots 0-44)
        if (isItemSlot(slot)) {
            // Block placing non-loot items into storage
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (!isValidStorageItem(spawner, cursor)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Block hotbar swap with non-loot item
            if (event.getClick() == ClickType.NUMBER_KEY) {
                int hotbarSlot = event.getHotbarButton();
                if (hotbarSlot >= 0 && hotbarSlot < 9) {
                    ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                    if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                        if (!isValidStorageItem(spawner, hotbarItem)) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }

            // Native take allowed
            event.setCancelled(false);

            // Reconcile page after click on region thread
            Scheduler.runLocationTask(player.getLocation(), () -> {
                if (!player.isOnline()) return;
                Inventory topInv = player.getOpenInventory().getTopInventory();
                if (topInv.getHolder(false) instanceof StoragePageHolder pageHolder
                        && pageHolder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())) {
                    reconcileStoragePage(player, topInv, spawner, pageHolder);
                }
            });
        }
    }

    private void handleControlSlotClick(Player player, int slot, StoragePageHolder holder,
                                        SpawnerData spawner, Inventory inventory, org.bukkit.event.inventory.ClickType clickType, GuiLayout layout) {
        // OPTIMIZATION: Get button and action with click type fallback
        Optional<github.nighter.smartspawner.spawner.gui.layout.GuiButton> buttonOpt = layout.getButtonAtSlot(slot);
        if (buttonOpt.isEmpty()) {
            return;
        }

        var button = buttonOpt.get();
        String clickTypeString = getClickTypeString(clickType);
        String action = button.getActionWithFallback(clickTypeString);

        if (action == null || action.isEmpty()) {
            return;
        }
        if ("none".equals(action)) {
            return;
        }
        if (!plugin.getGuiButtonInteractionService().tryUse(
                player, GuiLayoutType.STORAGE_GUI, button)) {
            return;
        }

        // OPTIMIZATION: Handle actions based on action value, not button name
        switch (action) {
            case "sort_items":
                playActionResult(player, button, clickTypeString,
                        handleSortItemsClick(player, spawner, inventory));
                break;
            case "open_filter":
                plugin.getGuiButtonInteractionService().playNavigateSound(
                        player, button, clickTypeString);
                openFilterConfig(player, spawner);
                break;
            case "previous_page":
                if (holder.getCurrentPage() > 1) {
                    plugin.getGuiButtonInteractionService().playNavigateSound(
                            player, button, clickTypeString);
                    reconcileStoragePage(player, inventory, spawner, holder);
                    updatePageContent(player, spawner, holder.getCurrentPage() - 1, inventory);
                }
                break;
            case "take_all":
                playActionResult(player, button, clickTypeString,
                        handleTakeAllItems(player, inventory));
                break;
            case "next_page":
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    plugin.getGuiButtonInteractionService().playNavigateSound(
                            player, button, clickTypeString);
                    reconcileStoragePage(player, inventory, spawner, holder);
                    updatePageContent(player, spawner, holder.getCurrentPage() + 1, inventory);
                }
                break;
            case "drop_page":
                playActionResult(player, button, clickTypeString,
                        handleDropPageItems(player, spawner, inventory));
                break;
            case "sell_all":
                handleSellAction(player, spawner, false, button, clickTypeString, inventory);
                break;
            case "sell_and_exp":
                handleSellAction(player, spawner, true, button, clickTypeString, inventory);
                break;
            case "collect_exp":
                playActionResult(player, button, clickTypeString,
                        handleCollectExpAction(player, spawner, inventory));
                break;
            case "return_main":
                plugin.getGuiButtonInteractionService().playNavigateSound(
                        player, button, clickTypeString);
                handleReturnToMainMenu(player, spawner);
                break;
            case "none":
                // Display-only button — consume click, do nothing
                break;
            default:
                // Unknown action, log warning
                plugin.getLogger().warning("Unknown storage action: " + action);
                break;
        }
    }

    /**
     * Convert Bukkit ClickType to string for action lookup
     * OPTIMIZATION: Cached string values to avoid repeated string creation
     */
    private String getClickTypeString(org.bukkit.event.inventory.ClickType clickType) {
        return switch (clickType) {
            case LEFT -> "left_click";
            case RIGHT -> "right_click";
            case SHIFT_LEFT -> "shift_left_click";
            case SHIFT_RIGHT -> "shift_right_click";
            default -> "left_click";
        };
    }

    /**
     * Handle sell action with optional exp collection
     * OPTIMIZATION: Extracted common sell logic to reduce code duplication
     */
    private void handleSellAction(Player player, SpawnerData spawner, boolean collectExp,
                                  github.nighter.smartspawner.spawner.gui.layout.GuiButton sourceButton,
                                  String sourceClickType, Inventory inventory) {
        if (!plugin.hasSellIntegration()) {
            plugin.getGuiButtonInteractionService().playFailSound(
                    player, sourceButton, sourceClickType);
            return;
        }

        if (!player.hasPermission("smartspawner.sellall")) {
            messageService.sendMessage(player, "no_permission");
            plugin.getGuiButtonInteractionService().playFailSound(
                    player, sourceButton, sourceClickType);
            return;
        }

        // Check if there are items to sell
        if (spawner.getVirtualInventory().getUsedSlots() == 0) {
            if (collectExp && spawner.getSpawnerExp() > 0) {
                // No items to sell, but collect the stored exp without leaving the storage GUI
                boolean success = handleCollectExpAction(player, spawner, inventory);
                playActionResult(player, sourceButton, sourceClickType, success);
            } else {
                messageService.sendMessage(player, "spawner_storage_empty");
                plugin.getGuiButtonInteractionService().playFailSound(
                        player, sourceButton, sourceClickType);
            }
            return;
        }

        // Open confirmation GUI
        plugin.getSpawnerSellConfirmUI().openSellConfirmGui(
                player, spawner, STORAGE, collectExp, sourceButton, sourceClickType);
    }

    /**
     * Collects stored XP from the spawner while keeping the player on the storage GUI.
     */
    private boolean handleCollectExpAction(Player player, SpawnerData spawner, Inventory inventory) {
        boolean collected = plugin.getSpawnerMenuAction().tryCollectExpForPlayer(player, spawner);
        if (collected) {
            // Refresh button display so the XP counter updates to 0
            StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
            if (holder != null) {
                plugin.getSpawnerStorageUI().updateDisplay(inventory, spawner, holder.getCurrentPage(), holder.getTotalPages());
            }
        }
        return collected;
    }

    /**
     * Handle return to main menu action
     */
    private void handleReturnToMainMenu(Player player, SpawnerData spawner) {
        player.closeInventory();
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
    }

    private boolean isControlSlot(int slot, GuiLayout layout) {
        return layout != null && layout.isSlotUsed(slot);
    }

    private boolean isItemSlot(int slot) {
        // First 45 slots (0-44) are for storage items
        return slot >= 0 && slot < STORAGE_SLOTS;
    }

    /**
     * Checks if an item is a valid loot item for this spawner.
     * Prevents players from depositing non-spawner items into storage.
     */
    private boolean isValidStorageItem(SpawnerData spawner, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (spawner.isItemSpawner()) {
            return item.getType() == spawner.getSpawnedItemMaterial();
        }
        if (spawner.getLootConfig() == null) {
            return false;
        }
        return spawner.getValidLootItems().stream()
                .anyMatch(loot -> loot.material() == item.getType());
    }

    /**
     * Reconciles the current page contents with StorageSession and VirtualInventory.
     * Preserves empty slots (does NOT auto-compact while viewing).
     */
    private void reconcileStoragePage(Player player, Inventory inventory, SpawnerData spawner, StoragePageHolder holder) {
        int page = holder.getCurrentPage();
        if (plugin.getStorageSessionManager() == null) {
            return;
        }
        StorageSession session = plugin.getStorageSessionManager().getOrCreateSession(spawner);

        ItemStack[] currentSlots = new ItemStack[StoragePageHolder.MAX_ITEMS_PER_PAGE];
        List<ItemStack> currList = new ArrayList<>();

        for (int i = 0; i < StoragePageHolder.MAX_ITEMS_PER_PAGE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                currentSlots[i] = item.clone();
                currList.add(item.clone());
            } else {
                currentSlots[i] = null;
            }
        }

        ItemStack[] previousSlots = session.getPageSlots(page);
        List<ItemStack> prevList = new ArrayList<>();
        if (previousSlots != null) {
            for (ItemStack prev : previousSlots) {
                if (prev != null && prev.getType() != Material.AIR && prev.getAmount() > 0) {
                    prevList.add(prev.clone());
                }
            }
        }

        // Update session's slot records for this page (preserves gaps!)
        session.setPageSlots(page, currentSlots);

        // Cancel out matching items between current and previous using isSimilar
        for (ItemStack curr : currList) {
            for (ItemStack prev : prevList) {
                if (curr.getAmount() > 0 && prev.getAmount() > 0 && curr.isSimilar(prev)) {
                    int matched = Math.min(curr.getAmount(), prev.getAmount());
                    curr.setAmount(curr.getAmount() - matched);
                    prev.setAmount(prev.getAmount() - matched);
                }
            }
        }

        // Any remainder in prevList with amount > 0 was REMOVED from storage
        Map<ItemSignature, Long> removed = new HashMap<>();
        for (ItemStack prev : prevList) {
            if (prev.getAmount() > 0) {
                removed.merge(VirtualInventory.getSignature(prev), (long) prev.getAmount(), Long::sum);
            }
        }

        // Any remainder in currList with amount > 0 was ADDED to storage
        Map<ItemSignature, Long> added = new HashMap<>();
        for (ItemStack curr : currList) {
            if (curr.getAmount() > 0) {
                added.merge(VirtualInventory.getSignature(curr), (long) curr.getAmount(), Long::sum);
            }
        }

        if (!removed.isEmpty()) {
            spawner.removeItemsAndUpdateSellValue(removed);
            spawner.markStorageDirty();
        }
        if (!added.isEmpty()) {
            spawner.addItemsAndUpdateSellValue(added);
            spawner.markStorageDirty();
        }

        if (!removed.isEmpty() || !added.isEmpty()) {
            spawner.updateHologramData();
            holder.updateOldUsedSlots();

            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }

            // Update only the sell button display, keeping item slots untouched
            updateSellButtonOnly(inventory, spawner, holder.getLayout());

            // Sync other viewers viewing the exact same page
            syncOtherPageViewers(spawner, player.getUniqueId(), page, currentSlots);
        }
    }

    /**
     * Updates sell buttons in the control row without redrawing storage item slots.
     */
    private void updateSellButtonOnly(Inventory inventory, SpawnerData spawner, GuiLayout layout) {
        if (layout == null) return;
        for (var button : layout.getAllButtons().values()) {
            if (!button.isEnabled()) continue;
            String action = button.getActionWithFallback("left_click");
            if ("sell_all".equals(action)) {
                inventory.setItem(button.getSlot(), plugin.getSpawnerStorageUI().createSellButton(spawner, button));
            } else if ("sell_and_exp".equals(action)) {
                inventory.setItem(button.getSlot(), plugin.getSpawnerStorageUI().createSellAndExpButton(spawner, button));
            }
        }
    }

    /**
     * Syncs item slots to other players viewing the same page without shifting or repacking.
     */
    private void syncOtherPageViewers(SpawnerData spawner, UUID actingPlayerId, int page, ItemStack[] currentSlots) {
        Set<Player> viewers = plugin.getSpawnerGuiViewManager().getViewers(spawner.getSpawnerId());
        if (viewers == null || viewers.isEmpty()) return;

        for (Player viewer : viewers) {
            if (viewer.getUniqueId().equals(actingPlayerId) || !viewer.isOnline()) {
                continue;
            }
            Inventory openInv = viewer.getOpenInventory().getTopInventory();
            if (openInv.getHolder(false) instanceof StoragePageHolder otherHolder) {
                if (otherHolder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())
                        && otherHolder.getCurrentPage() == page) {
                    for (int i = 0; i < StoragePageHolder.MAX_ITEMS_PER_PAGE; i++) {
                        openInv.setItem(i, currentSlots[i] != null ? currentSlots[i].clone() : null);
                    }
                    updateSellButtonOnly(openInv, spawner, otherHolder.getLayout());
                    viewer.updateInventory();
                }
            }
        }
    }

    private boolean handleDropPageItems(Player player, SpawnerData spawner, Inventory inventory) {
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder == null) {
            return false;
        }

        List<ItemStack> pageItems = new ArrayList<>();
        int itemsFoundCount = 0;

        // Collect items from GUI display
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                pageItems.add(item.clone());
                itemsFoundCount += item.getAmount();
                inventory.setItem(i, null);
            }
        }

        if (pageItems.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        if (SpawnerDropAllEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerDropAllEvent event = new SpawnerDropAllEvent(player, spawner.getSpawnerLocation(), pageItems);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            pageItems = event.getItems();
        }

        final int itemsFound = itemsFoundCount;

        // Remove from VirtualInventory
        spawner.removeItemsAndUpdateSellValue(pageItems);

        if (plugin.getStorageSessionManager() != null) {
            StorageSession session = plugin.getStorageSessionManager().getSession(spawner.getSpawnerId());
            if (session != null) {
                session.setPageSlots(holder.getCurrentPage(), new ItemStack[StoragePageHolder.MAX_ITEMS_PER_PAGE]);
            }
        }

        dropItemsInDirection(player, pageItems);

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
        spawner.markStorageDirty();

        // Log drop page items action
        if (plugin.getSpawnerActionLogger() != null) {
            plugin.getSpawnerActionLogger().log(github.nighter.smartspawner.logging.SpawnerEventType.SPAWNER_DROP_PAGE_ITEMS, builder ->
                    builder.player(player.getName(), player.getUniqueId())
                            .location(spawner.getSpawnerLocation())
                            .entityType(spawner.getEntityType())
                            .metadata("items_dropped", itemsFound)
                            .metadata("page_number", holder.getCurrentPage())
            );
        }

        updatePageContent(player, spawner, holder.getCurrentPage(), inventory);
        return true;
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

        Vector velocity = new Vector(
                sinYaw * cosPitch * 0.3,
                sinPitch * 0.3 + 0.1,
                cosYaw * cosPitch * 0.3
        );

        for (ItemStack item : items) {
            Item droppedItem = world.dropItem(dropLocation, item, drop -> {
                drop.setThrower(playerUUID);
                drop.setPickupDelay(40);
            });


            droppedItem.setVelocity(velocity);
        }
    }


    private void openFilterConfig(Player player, SpawnerData spawner) {
        filterConfigUI.openFilterConfigGUI(player, spawner);
    }


    private void updatePageContent(Player player, SpawnerData spawner, int newPage, Inventory inventory) {
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);

        int totalPages = calculateTotalPages(spawner);

        assert holder != null;
        holder.setTotalPages(totalPages);
        holder.setCurrentPage(newPage);
        holder.updateOldUsedSlots();

        spawnerStorageUI.updateDisplay(inventory, spawner, newPage, totalPages);

        updateInventoryTitle(player, spawner, newPage, totalPages);

    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private void updateInventoryTitle(Player player, SpawnerData spawner, int page, int totalPages) {
        String newTitle = plugin.getSpawnerStorageUI().getStorageTitle(spawner, page, totalPages);

        try {
            player.getOpenInventory().setTitle(newTitle);
        } catch (Exception e) {
            openLootPage(player, spawner, page);
        }
    }

    private boolean isItemClickTooFrequent(Player player) {
        long now = System.currentTimeMillis();
        long last = lastItemClickTime.getOrDefault(player.getUniqueId(), 0L);

        if ((now - last) < ITEM_CLICK_DELAY_MS) {
            messageService.sendMessage(player, "click_too_fast");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastItemClickTime.remove(playerId);
    }

    private boolean handleSortItemsClick(Player player, SpawnerData spawner, Inventory inventory) {
        // Validate loot config
        if (spawner.getLootConfig() == null || spawner.getLootConfig().getAllItems() == null) {
            return false;
        }

        var lootItems = spawner.getLootConfig().getAllItems();
        if (lootItems.isEmpty()) {
            return false;
        }

        // Get current sort item
        Material currentSort = spawner.getPreferredSortItem();

        // Build sorted list of available materials
        var sortedLoot = lootItems.stream()
                .map(LootItem::material)
                .distinct() // Remove duplicates if any
                .sorted(Comparator.comparing(Material::name))
                .toList();

        if (sortedLoot.isEmpty()) {
            return false;
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
        spawner.markStorageDirty();
        spawnerManager.queueSpawnerForSaving(spawner.getSpawnerId());

        // Re-sort VirtualInventory
        spawner.getVirtualInventory().sortItems(nextSort);

        // Reset session if present so it reloads with new sort order
        if (plugin.getStorageSessionManager() != null) {
            plugin.getStorageSessionManager().removeSession(spawner.getSpawnerId());
        }

        // Update GUI display to reflect VirtualInventory state
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder != null) {
            updatePageContent(player, spawner, holder.getCurrentPage(), inventory);
        }

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
        return true;
    }

    private void openLootPage(Player player, SpawnerData spawner, int page) {
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        int totalPages = calculateTotalPages(spawner);
        final int finalPage = Math.max(1, Math.min(page, totalPages));
        Inventory pageInventory = spawnerStorageUI.createStorageInventory(player, spawner, finalPage, totalPages);

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

        player.openInventory(pageInventory);
    }

    public boolean handleTakeAllItems(Player player, Inventory sourceInventory) {
        StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
        SpawnerData spawner = holder.getSpawnerData();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        // Collect items from GUI
        Map<Integer, ItemStack> sourceItems = new HashMap<>();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack item = sourceInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                sourceItems.put(i, item.clone());
            }
        }

        if (sourceItems.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        if (SpawnerTakeAllEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerTakeAllEvent event = new SpawnerTakeAllEvent(player, spawner.getSpawnerLocation(), sourceItems);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            sourceItems = event.getItems();
        }

        // Transfer items and update VirtualInventory
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
                SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
                spawnerStorageUI.updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
            }

            // Update the inventory title to reflect new page count
            updateInventoryTitle(player, spawner, adjustedPage, newTotalPages);

            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
            spawner.markStorageDirty();

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
        return result.anyItemMoved;
    }

    private void playActionResult(
            Player player,
            github.nighter.smartspawner.spawner.gui.layout.GuiButton button,
            String clickType,
            boolean success) {
        if (success) {
            plugin.getGuiButtonInteractionService().playSuccessSound(player, button, clickType);
        } else {
            plugin.getGuiButtonInteractionService().playFailSound(player, button, clickType);
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

        // Update VirtualInventory
        if (!itemsToRemove.isEmpty()) {
            StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
            SpawnerData spawnerData = holder.getSpawnerData();

            spawnerData.removeItemsAndUpdateSellValue(itemsToRemove);
            spawnerData.updateHologramData();

            holder.updateOldUsedSlots();

            if (plugin.getStorageSessionManager() != null) {
                StorageSession session = plugin.getStorageSessionManager().getSession(spawnerData.getSpawnerId());
                if (session != null) {
                    ItemStack[] currentSlots = new ItemStack[StoragePageHolder.MAX_ITEMS_PER_PAGE];
                    for (int i = 0; i < StoragePageHolder.MAX_ITEMS_PER_PAGE; i++) {
                        ItemStack it = sourceInventory.getItem(i);
                        currentSlots[i] = (it != null && it.getType() != Material.AIR) ? it.clone() : null;
                    }
                    session.setPageSlots(holder.getCurrentPage(), currentSlots);
                }
            }
        }

        return new TransferResult(anyItemMoved, inventoryFull, totalAmountMoved);
    }


    private void sendTransferMessage(Player player, TransferResult result) {
        if (!result.anyItemMoved) {
            messageService.sendMessage(player, "inventory_full");
        }
    }


    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        SpawnerData spawner = holder.getSpawnerData();
        if (spawner.isSelling()) {
            event.setCancelled(true);
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            // Never allow dragging on control buttons
            if (rawSlot >= STORAGE_SLOTS && rawSlot < INVENTORY_SIZE) {
                event.setCancelled(true);
                return;
            }
            // Check dragging into storage slots
            if (rawSlot >= 0 && rawSlot < STORAGE_SLOTS) {
                ItemStack dragged = event.getOldCursor();
                if (!isValidStorageItem(spawner, dragged)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Allow drag and reconcile on region thread
        event.setCancelled(false);
        if (event.getWhoClicked() instanceof Player player) {
            Scheduler.runLocationTask(player.getLocation(), () -> {
                if (!player.isOnline()) return;
                Inventory topInv = player.getOpenInventory().getTopInventory();
                if (topInv.getHolder(false) instanceof StoragePageHolder pageHolder
                        && pageHolder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())) {
                    reconcileStoragePage(player, topInv, spawner, pageHolder);
                }
            });
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        if (event.getPlayer() instanceof Player player) {
            SpawnerData spawner = holder.getSpawnerData();

            // Reconcile final page state
            reconcileStoragePage(player, inventory, spawner, holder);

            if (plugin.getStorageSessionManager() != null) {
                StorageSession session = plugin.getStorageSessionManager().getSession(spawner.getSpawnerId());
                if (session != null) {
                    session.removeViewer(player.getUniqueId());

                    // Check if any other viewers are still viewing storage of this spawner
                    boolean hasRemainingStorageViewers = false;
                    Set<Player> viewers = plugin.getSpawnerGuiViewManager().getViewers(spawner.getSpawnerId());
                    for (Player viewer : viewers) {
                        if (viewer.isOnline() && !viewer.getUniqueId().equals(player.getUniqueId())) {
                            Inventory topInv = viewer.getOpenInventory().getTopInventory();
                            if (topInv.getHolder(false) instanceof StoragePageHolder) {
                                hasRemainingStorageViewers = true;
                                break;
                            }
                        }
                    }

                    if (!hasRemainingStorageViewers && !session.hasViewers()) {
                        // All viewers left! End session so next open naturally loads compacted VirtualInventory
                        session.endSession();
                        plugin.getStorageSessionManager().removeSession(spawner.getSpawnerId());
                        plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
                    }
                }
            }

            handleInventoryClose(holder);
        }
    }

    private void handleInventoryClose(StoragePageHolder holder) {
        SpawnerData spawner = holder.getSpawnerData();
        if (spawner.isStorageDirty()){
            plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
            spawner.clearStorageDirty();
        }
    }
}
