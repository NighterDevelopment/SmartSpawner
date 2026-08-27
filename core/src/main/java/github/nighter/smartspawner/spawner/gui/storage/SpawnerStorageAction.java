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
import github.nighter.smartspawner.spawner.properties.ItemSignature;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
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
        event.setCancelled(true);

        // Block ALL storage interactions while a sell is in progress.
        // This closes the race window where the storage GUI could be reopened (by the
        // reopenPreviousGui callback) before the async sell's item-removal step has run,
        // which would otherwise allow items to be taken from the virtual inventory twice –
        // once by the player and once by applySellResult.
        if (spawner.isSelling()) {
            plugin.getMessageService().sendMessage(player, "action_in_progress");
            return;
        }

        // Handle clicks outside valid storage GUI area
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        // Handle item slot clicks (taking items from storage)
        if (isItemSlot(slot)) {
            // Filler slots (beyond capacity on a partial last page) are display-only.
            if (slot >= SpawnerStorageUI.usableItemSlots(spawner, holder.getCurrentPage())) {
                return;
            }
            handleItemSlotClick(player, slot, holder, spawner, event);
            return;
        }

        // Handle control button clicks
        if (isControlSlot(slot, holder.getLayout())) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            handleControlSlotClick(
                    player, slot, holder, spawner, event.getInventory(), event.getClick(), holder.getLayout());
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
     * Handles clicks on item slots in the storage GUI.
     * ALL clicks transfer items directly to player inventory (no cursor interaction).
     * - LEFT CLICK: Take 1 item from stack
     * - RIGHT CLICK: Take half of stack
     * - SHIFT CLICK: Take entire stack
     */
    private void handleItemSlotClick(Player player, int slot, StoragePageHolder holder,
                                    SpawnerData spawner, InventoryClickEvent event) {
        // Anti-spam check
        if (isItemClickTooFrequent(player)) {
            return;
        }
        lastItemClickTime.put(player.getUniqueId(), System.currentTimeMillis());

        Inventory inventory = event.getInventory();
        ItemStack clickedItem = inventory.getItem(slot);

        // Nothing to take from empty slot
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Determine amount to take based on click type
        ClickType clickType = event.getClick();
        int amountToTake;

        switch (clickType) {
            case LEFT:
                // Left click = take only 1 item
                amountToTake = 1;
                break;
            case RIGHT:
                // Right click = take half
                amountToTake = (int) Math.ceil(clickedItem.getAmount() / 2.0);
                break;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                // Shift click = take all
                amountToTake = clickedItem.getAmount();
                break;
            default:
                // Ignore other click types
                return;
        }

        // Transfer items to player inventory
        transferToPlayerInventory(player, clickedItem, amountToTake, inventory, spawner, holder);
    }

    /**
     * Transfers a single item type from storage to the player inventory.
     *
     * <p>Transactional order (dupe-safe): compute how much the bag can accept (read-only),
     * ask {@link SpawnerData#takeItems(Map)} to remove exactly that much from the source of
     * truth, then place back into the bag only what was actually removed. The clicked GUI slot
     * only identifies WHICH item; the amount is governed by capacity and by the atomic take.
     */
    private void transferToPlayerInventory(Player player, ItemStack clickedItem, int amountToTake,
                                          Inventory storageInv, SpawnerData spawner, StoragePageHolder holder) {
        PlayerInventory playerInv = player.getInventory();
        ItemStack template = clickedItem.asQuantity(1);

        // How much of this item can the bag actually accept, capped by the requested amount.
        int acceptable = computeAcceptableAmount(playerInv, template, amountToTake);
        if (acceptable <= 0) {
            messageService.sendMessage(player, "inventory_full");
            return;
        }

        ItemSignature signature = VirtualInventory.getSignature(template);
        Map<ItemSignature, Long> removed = spawner.takeItems(Map.of(signature, (long) acceptable));
        long removedAmount = removed.getOrDefault(signature, 0L);

        if (removedAmount <= 0) {
            // Nothing left – another viewer emptied it, or a sell is running. Refresh the stale slot.
            updatePageAfterRemoval(player, storageInv, spawner, holder);
            return;
        }

        // removedAmount <= acceptable, so this always fits completely.
        addToPlayerInventory(playerInv, template, (int) removedAmount);

        updatePageAfterRemoval(player, storageInv, spawner, holder);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);

        // Only warn "inventory full" when the bag (not the storage) was the limiter.
        if (acceptable < amountToTake) {
            messageService.sendMessage(player, "inventory_full");
        }
    }

    /**
     * Read-only: how much of {@code template} the player's main inventory can accept, capped
     * at {@code cap}. Counts space in matching partial stacks plus empty slots. Does not mutate.
     */
    private int computeAcceptableAmount(PlayerInventory playerInv, ItemStack template, int cap) {
        if (cap <= 0) {
            return 0;
        }
        int maxStack = template.getMaxStackSize();
        int space = 0;
        for (int i = 0; i < 36 && space < cap; i++) {
            ItemStack slot = playerInv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                space += maxStack;
            } else if (slot.isSimilar(template)) {
                int room = maxStack - slot.getAmount();
                if (room > 0) {
                    space += room;
                }
            }
        }
        return Math.min(space, cap);
    }

    /**
     * Places {@code amount} of {@code template} into the player's main inventory, stacking into
     * matching partial stacks first, then empty slots. The caller must have verified via
     * {@link #computeAcceptableAmount} that {@code amount} fits, so nothing is dropped.
     */
    private void addToPlayerInventory(PlayerInventory playerInv, ItemStack template, int amount) {
        int remaining = amount;
        int maxStack = template.getMaxStackSize();

        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack slot = playerInv.getItem(i);
            if (slot != null && slot.getType() != Material.AIR && slot.isSimilar(template)) {
                int room = maxStack - slot.getAmount();
                if (room > 0) {
                    int add = Math.min(room, remaining);
                    slot.setAmount(slot.getAmount() + add);
                    remaining -= add;
                }
            }
        }

        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack slot = playerInv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                int add = Math.min(remaining, maxStack);
                ItemStack newStack = template.clone();
                newStack.setAmount(add);
                playerInv.setItem(i, newStack);
                remaining -= add;
            }
        }
    }

    /**
     * Simulates filling the player's bag with the given signatures (in iteration order) without
     * mutating the real inventory. Returns how much of each signature would fit, competing for
     * the same empty slots as a real fill would. Used to size a take-all before committing it.
     */
    private Map<ItemSignature, Long> simulateBagFill(PlayerInventory playerInv,
                                                     Collection<Map.Entry<ItemSignature, Long>> ordered) {
        ItemStack[] slots = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack s = playerInv.getItem(i);
            slots[i] = (s == null || s.getType() == Material.AIR) ? null : s.clone();
        }

        Map<ItemSignature, Long> acceptable = new HashMap<>();
        for (Map.Entry<ItemSignature, Long> entry : ordered) {
            ItemSignature signature = entry.getKey();
            long avail = entry.getValue() == null ? 0L : entry.getValue();
            if (signature == null || avail <= 0) {
                continue;
            }

            ItemStack template = signature.getTemplate();
            int maxStack = template.getMaxStackSize();
            long placed = 0;

            for (int i = 0; i < 36 && placed < avail; i++) {
                ItemStack slot = slots[i];
                if (slot != null && slot.isSimilar(template)) {
                    int room = maxStack - slot.getAmount();
                    if (room > 0) {
                        int add = (int) Math.min(room, avail - placed);
                        slot.setAmount(slot.getAmount() + add);
                        placed += add;
                    }
                }
            }
            for (int i = 0; i < 36 && placed < avail; i++) {
                if (slots[i] == null) {
                    int add = (int) Math.min(maxStack, avail - placed);
                    ItemStack ns = template.clone();
                    ns.setAmount(add);
                    slots[i] = ns;
                    placed += add;
                }
            }

            if (placed > 0) {
                acceptable.put(signature, placed);
            }
        }
        return acceptable;
    }

    /** Splits a signature-to-amount map into displayable stacks keyed by sequential slot index. */
    private Map<Integer, ItemStack> projectToSlots(Map<ItemSignature, Long> items) {
        Map<Integer, ItemStack> out = new HashMap<>();
        int slot = 0;
        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            ItemSignature signature = entry.getKey();
            long remaining = entry.getValue();
            int maxStack = signature.getMaxStackSize();
            while (remaining > 0) {
                ItemStack stack = signature.getTemplate();
                int amt = (int) Math.min(remaining, maxStack);
                stack.setAmount(amt);
                out.put(slot++, stack);
                remaining -= amt;
            }
        }
        return out;
    }

    /** Consolidates a slot-keyed ItemStack map back into signature-to-amount. */
    private Map<ItemSignature, Long> consolidateSlots(Map<Integer, ItemStack> slots) {
        Map<ItemSignature, Long> out = new HashMap<>();
        for (ItemStack item : slots.values()) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            out.merge(VirtualInventory.getSignature(item), (long) item.getAmount(), Long::sum);
        }
        return out;
    }


    /**
     * Updates the page display after items are removed from storage.
     */
    private void updatePageAfterRemoval(Player player, Inventory inventory,
                                       SpawnerData spawner, StoragePageHolder holder) {
        // Recalculate pages
        int newTotalPages = calculateTotalPages(spawner);
        int currentPage = holder.getCurrentPage();

        // Clamp to valid page range
        int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));

        holder.setTotalPages(newTotalPages);
        if (adjustedPage != currentPage) {
            holder.setCurrentPage(adjustedPage);
        }
        holder.updateOldUsedSlots();

        // Update display
        SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
        spawnerStorageUI.updateDisplay(inventory, spawner, adjustedPage, newTotalPages);

        // Update title if pages changed
        if (newTotalPages != currentPage || adjustedPage != currentPage) {
            updateInventoryTitle(player, spawner, adjustedPage, newTotalPages);
        }

        // Update hologram and other viewers
        spawner.updateHologramData();
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        // Check capacity
        if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
            spawner.setIsAtCapacity(false);
        }

        // Mark as modified
        spawner.markStorageDirty();
    }

    private boolean handleDropPageItems(Player player, SpawnerData spawner, Inventory inventory) {
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder == null) {
            return false;
        }

        VirtualInventory virtualInv = spawner.getVirtualInventory();

        // Project the current page from the source of truth (count-map), not the GUI slots.
        Int2ObjectMap<ItemStack> pageDisplay =
                virtualInv.getDisplayPage(holder.getCurrentPage(), StoragePageHolder.MAX_ITEMS_PER_PAGE);
        if (pageDisplay.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        List<ItemStack> pageItems = new ArrayList<>(pageDisplay.values());

        if (SpawnerDropAllEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerDropAllEvent event = new SpawnerDropAllEvent(player, spawner.getSpawnerLocation(), pageItems);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            pageItems = event.getItems();
        }

        // Build the requested amounts from the (possibly addon-modified) list.
        Map<ItemSignature, Long> desired = new HashMap<>();
        for (ItemStack item : pageItems) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            desired.merge(VirtualInventory.getSignature(item), (long) item.getAmount(), Long::sum);
        }
        if (desired.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        // Atomic removal; drop back exactly what was removed (dupe-safe against stale views).
        Map<ItemSignature, Long> removed = spawner.takeItems(desired);
        if (removed.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        List<ItemStack> toDrop = new ArrayList<>();
        long itemsFoundCount = 0;
        for (Map.Entry<ItemSignature, Long> entry : removed.entrySet()) {
            ItemSignature signature = entry.getKey();
            long remaining = entry.getValue();
            itemsFoundCount += remaining;
            int maxStack = signature.getMaxStackSize();
            while (remaining > 0) {
                ItemStack stack = signature.getTemplate();
                int amt = (int) Math.min(remaining, maxStack);
                stack.setAmount(amt);
                toDrop.add(stack);
                remaining -= amt;
            }
        }

        final long itemsFound = itemsFoundCount;

        dropItemsInDirection(player, toDrop);

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

        // Apply the new sort preference: re-sorts and re-pins the frozen order (Phase 4), and bumps
        // the storage version so other viewers redraw in the new order.
        spawner.applySortPreference(nextSort);

        // Mark spawner as modified to save the preference
        spawner.markStorageDirty();
        spawnerManager.queueSpawnerForSaving(spawner.getSpawnerId());

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
        PlayerInventory playerInv = player.getInventory();

        // Source of truth, not the GUI slots: everything currently stored.
        Map<ItemSignature, Long> available = virtualInv.getConsolidatedItems();
        if (available.isEmpty()) {
            messageService.sendMessage(player, "spawner_storage_empty");
            return false;
        }

        // How much the bag can actually accept, competing for the same slots as a real fill.
        Map<ItemSignature, Long> desired = simulateBagFill(playerInv, available.entrySet());
        if (desired.isEmpty()) {
            messageService.sendMessage(player, "inventory_full");
            return false;
        }

        if (SpawnerTakeAllEvent.getHandlerList().getRegisteredListeners().length != 0) {
            Map<Integer, ItemStack> projected = projectToSlots(desired);
            SpawnerTakeAllEvent event = new SpawnerTakeAllEvent(player, spawner.getSpawnerLocation(), projected);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            desired = consolidateSlots(event.getItems());
            if (desired.isEmpty()) return false;
        }

        // Atomic removal; place back exactly what was removed (dupe-safe against stale views).
        Map<ItemSignature, Long> removed = spawner.takeItems(desired);
        if (removed.isEmpty()) {
            messageService.sendMessage(player, "inventory_full");
            return false;
        }

        long totalMoved = 0;
        for (Map.Entry<ItemSignature, Long> entry : removed.entrySet()) {
            addToPlayerInventory(playerInv, entry.getKey().getTemplate(), entry.getValue().intValue());
            totalMoved += entry.getValue();
        }
        final long totalMovedFinal = totalMoved;
        spawner.updateHologramData();
        player.updateInventory();

        int newTotalPages = calculateTotalPages(spawner);
        int currentPage = holder.getCurrentPage();

        // Clamp current page to valid range (e.g., if on page 6 but only 5 pages remain)
        int adjustedPage = Math.max(1, Math.min(currentPage, newTotalPages));

        holder.setTotalPages(newTotalPages);
        holder.updateOldUsedSlots();
        if (adjustedPage != currentPage) {
            holder.setCurrentPage(adjustedPage);
            SpawnerStorageUI spawnerStorageUI = plugin.getSpawnerStorageUI();
            spawnerStorageUI.updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
        } else {
            // Same page: still repaint so the emptied slots clear.
            plugin.getSpawnerStorageUI().updateDisplay(sourceInventory, spawner, adjustedPage, newTotalPages);
        }

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
                            .metadata("items_taken", totalMovedFinal)
                            .metadata("items_left", itemsLeft)
            );
        }
        return true;
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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StoragePageHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder(false) instanceof StoragePageHolder holder)) {
            return;
        }

        // Inventory close events already execute on the owning player's region thread.
        // Do not defer this work to the player's scheduler: after closing, a block-backed
        // inventory could belong to a different region and resolving its holder there
        // violates Folia's thread ownership rules.
        handleInventoryClose(holder);
    }

    private void handleInventoryClose(StoragePageHolder holder) {
        SpawnerData spawner = holder.getSpawnerData();
        if (spawner.isStorageDirty()){
            plugin.getSpawnerManager().markSpawnerModified(spawner.getSpawnerId());
            spawner.clearStorageDirty();
        }
    }
}
