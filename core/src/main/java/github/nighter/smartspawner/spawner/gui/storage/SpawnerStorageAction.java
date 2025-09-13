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
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.sell.SpawnerSellManager;
import lombok.Setter;
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

public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final MessageService messageService;
    private final FilterConfigUI filterConfigUI;
    private final SpawnerSellManager spawnerSellManager;
    private final SpawnerManager spawnerManager;
    @Setter
    private GuiLayoutConfig guiLayoutConfig;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;

    private final Map<ClickType, ItemClickHandler> clickHandlers;
    private final Map<UUID, Inventory> openStorageInventories = new HashMap<>();
    private final Map<UUID, Long> lastItemClickTime = new ConcurrentHashMap<>();
    private Random random = new Random();

    public SpawnerStorageAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.clickHandlers = initializeClickHandlers();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.messageService = plugin.getMessageService();
        this.filterConfigUI = plugin.getFilterConfigUI();
        this.spawnerSellManager = plugin.getSpawnerSellManager();
        this.guiLayoutConfig = plugin.getGuiLayoutConfig();
        this.spawnerManager = plugin.getSpawnerManager();
    }

    public void reload() {
        this.guiLayoutConfig = plugin.getGuiLayoutConfig();
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
            handleControlSlotClick(player, slot, holder, spawner, event.getInventory());
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
                                        SpawnerData spawner, Inventory inventory) {
        GuiLayout layout = plugin.getSpawnerStorageUI().getLayoutConfig().getCurrentLayout();
        if (layout == null) {
            return;
        }

        Optional<String> buttonTypeOpt = layout.getButtonTypeAtSlot(slot);
        if (buttonTypeOpt.isEmpty()) {
            return;
        }

        String buttonType = buttonTypeOpt.get();

        switch (buttonType) {
            case "discard_all":
                handleDiscardAllItems(player, spawner, inventory);
                break;
            case "item_filter":
                openFilterConfig(player, spawner);
                break;
            case "previous_page":
                if (holder.getCurrentPage() > 1) {
                    playButtonSound(player, buttonType);
                    updatePageContent(player, spawner, holder.getCurrentPage() - 1, inventory, false);
                }
                break;
            case "take_all":
                handleTakeAllItems(player, inventory);
                break;
            case "next_page":
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    playButtonSound(player, buttonType);
                    updatePageContent(player, spawner, holder.getCurrentPage() + 1, inventory, false);
                }
                break;
            case "drop_page":
                handleDropPageItems(player, spawner, inventory);
                break;
            case "shop_indicator":
            case "sell":
                if (plugin.hasSellIntegration()) {
                    if (!player.hasPermission("smartspawner.sellall")) {
                        messageService.sendMessage(player, "no_permission");
                        return;
                    }
                    if (isClickTooFrequent(player)) {
                        return;
                    }
                    playButtonSound(player, buttonType);
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
        if (event.getInventory().getHolder(false) instanceof StoragePageHolder) {
            event.setCancelled(true);
        }
    }

    private boolean isControlSlot(int slot) {
        GuiLayout layout = guiLayoutConfig.getCurrentLayout();
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
        virtualInv.removeItems(itemsToRemove);

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

    private void handleDropPageItems(Player player, SpawnerData spawner, Inventory inventory) {
        if (isClickTooFrequent(player)) {
            return;
        }

        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        if (holder == null) {
            return;
        }

        List<ItemStack> pageItems = new ArrayList<>();
        int itemsFound = 0;

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                pageItems.add(item.clone());
                itemsFound += item.getAmount();
                inventory.setItem(i, null);
            }
        }

        if (pageItems.isEmpty()) {
            messageService.sendMessage(player, "no_items_to_drop");
            return;
        }

        VirtualInventory virtualInv = spawner.getVirtualInventory();
        virtualInv.removeItems(pageItems);

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
        if (!spawner.isInteracted()) {
            spawner.markInteracted();
        }

        updatePageContent(player, spawner, holder.getCurrentPage(), inventory, false);
        playButtonSound(player, "drop_page");
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
        playButtonSound(player, "item_filter");
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
            virtualInv.removeItems(result.getMovedItems());
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

        // Note: Sound is now handled by playButtonSound in the calling method
        // This method no longer plays sounds directly
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private void updateInventoryTitle(Player player, Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        String baseTitle = languageManager.getGuiTitle("gui_title_storage");
        String newTitle = baseTitle + " - [" + page + "/" + totalPages + "]";

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
    }

    private void openMainMenu(Player player, SpawnerData spawner) {
        playButtonSound(player, "return");
        if (spawner.isInteracted()){
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());
            spawner.clearInteracted();
        }
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
    }

    private void handleDiscardAllItems(Player player, SpawnerData spawner, Inventory inventory) {
        if (isClickTooFrequent(player)) {
            return;
        }

        VirtualInventory virtualInv = spawner.getVirtualInventory();

        if (virtualInv.getUsedSlots() == 0) {
            messageService.sendMessage(player, "no_items_to_discard");
            return;
        }

        long totalItems = virtualInv.getTotalItems();

        Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();
        List<ItemStack> itemsToRemove = new ArrayList<>();

        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            while (amount > 0) {
                ItemStack stack = template.clone();
                int stackSize = (int) Math.min(amount, template.getMaxStackSize());
                stack.setAmount(stackSize);
                itemsToRemove.add(stack);
                amount -= stackSize;
            }
        }

        virtualInv.removeItems(itemsToRemove);

        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder(false);
        // int oldTotalPages = calculateTotalPages(spawner);

        spawner.updateHologramData();
        if (spawner.getIsAtCapacity()) {
            spawner.setIsAtCapacity(false);
        }

        int newTotalPages = calculateTotalPages(spawner);
        holder.setTotalPages(newTotalPages);
        holder.setCurrentPage(1);
        holder.updateOldUsedSlots();

        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        updatePageContent(player, spawner, 1, inventory, false);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", languageManager.formatNumber(totalItems));
        messageService.sendMessage(player, "discard_all_success", placeholders);
        playButtonSound(player, "discard_all");
        if (!spawner.isInteracted()) {
            spawner.markInteracted();
        }
    }

    private void openLootPage(Player player, SpawnerData spawner, int page, boolean refresh) {
        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
        String title = languageManager.getGuiTitle("gui_title_storage");

        int totalPages = calculateTotalPages(spawner);

        page = Math.max(1, Math.min(page, totalPages));

        UUID playerId = player.getUniqueId();
        Inventory existingInventory = openStorageInventories.get(playerId);

        if (existingInventory != null && !refresh && existingInventory.getHolder(false) instanceof StoragePageHolder) {
            StoragePageHolder holder = (StoragePageHolder) existingInventory.getHolder(false);

            holder.setTotalPages(totalPages);
            holder.setCurrentPage(page);
            holder.updateOldUsedSlots();

            updatePageContent(player, spawner, page, existingInventory, false);
            return;
        }

        Inventory pageInventory = lootManager.createInventory(spawner, title, page, totalPages);

        openStorageInventories.put(playerId, pageInventory);

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

            holder.setTotalPages(newTotalPages);

            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
            if (!spawner.isInteracted()) {
                spawner.markInteracted();
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
            virtualInv.removeItems(itemsToRemove);
            StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder(false);
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
            playButtonSound(player, "take_all");
        }
    }

    /**
     * Plays the configured sound for a specific button type
     */
    private void playButtonSound(Player player, String buttonType) {
        GuiLayout layout = guiLayoutConfig.getCurrentLayout();
        if (layout == null) {
            return;
        }

        GuiButton button = layout.getButton(buttonType);
        if (button != null && button.hasSound()) {
            try {
                player.playSound(player.getLocation(), button.getSound(), 1.0f, 1.0f);
            } catch (Exception e) {
                // Log and continue gracefully if sound playback fails
                plugin.getLogger().warning("Failed to play sound for button '" + buttonType + "': " + e.getMessage());
            }
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
        }

        SpawnerData spawner = holder.getSpawnerData();
        if (spawner.isInteracted()){
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());
            spawner.clearInteracted();
        }
    }

    private record TransferResult(boolean anyItemMoved, boolean inventoryFull, int totalMoved) {}
}
