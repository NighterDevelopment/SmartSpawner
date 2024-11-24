package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.*;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.managers.SpawnerHeadManager;
import me.nighter.smartSpawner.managers.SpawnerLootManager;
import me.nighter.smartSpawner.holders.PagedSpawnerLootHolder;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.holders.SpawnerMenuHolder;
import me.nighter.smartSpawner.utils.VirtualInventory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GUIClickHandler implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    public GUIClickHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof PagedSpawnerLootHolder)) return;

        switch (event.getClick()) {
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
            case DOUBLE_CLICK:
            case DROP:
            case CONTROL_DROP:
            case WINDOW_BORDER_LEFT:
            case WINDOW_BORDER_RIGHT:
            case NUMBER_KEY:
                event.setCancelled(true);
                return;
        }

        Player player = (Player) event.getWhoClicked();
        PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) event.getInventory().getHolder();
        SpawnerData spawner = holder.getSpawnerData();
        int slot = event.getRawSlot();

        // Xử lý các click thông thường
        if (slot >= 0 && slot <= 53) {
            event.setCancelled(true);
            handleSlotClick(player, slot, holder, spawner, event.getInventory());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PagedSpawnerLootHolder) {
            event.setCancelled(true);
        }
    }

    // Tách logic xử lý click vào method riêng để dễ maintain
    private void handleSlotClick(Player player, int slot, PagedSpawnerLootHolder holder,
                                 SpawnerData spawner, Inventory inventory) {

        // Lưu trang hiện tại trước khi điều hướng
        SpawnerLootManager lootManager = new SpawnerLootManager(plugin);
        lootManager.saveItems(spawner, inventory);

        // Xử lý các nút điều hướng
        if (slot == 48 && holder.getCurrentPage() > 1) {
            openLootPage(player, spawner, holder.getCurrentPage() - 1, false);
        }
        else if (slot == 50 && holder.getCurrentPage() < holder.getTotalPages()) {
            openLootPage(player, spawner, holder.getCurrentPage() + 1, false);
        }
        else if (slot == 49 && plugin.isEconomyShopGUI()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

            if (!player.hasPermission("smartspawner.sellall")) {
                player.sendMessage(languageManager.getMessage("no-permission"));
                return;
            }

            if (plugin.getShopIntegration().sellAllItems(player, spawner)) {
                openLootPage(player, spawner, holder.getCurrentPage(), true);
            }
        }
        else if (slot == 53) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openSpawnerMenu(player, spawner);
        }
        else if (slot == 45) {
            handleTakeAllItems(player, inventory);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        }
        else if (slot == 46 && configManager.isAllowToggleEquipmentItems()) {
            boolean allowEquipment = !spawner.isAllowEquipmentItems();
            spawner.setAllowEquipmentItems(allowEquipment);
            openLootPage(player, spawner, holder.getCurrentPage(), true);
        }
    }

    private void openLootPage(Player player, SpawnerData spawner, int page, boolean refresh) {
        SpawnerLootManager lootManager = new SpawnerLootManager(plugin);
        String title = languageManager.getGuiTitle("gui-title.loot-menu");
        Inventory pageInventory = lootManager.createLootInventory(spawner, title, page);

        if (refresh) {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND
                    , 1.2f, 1.2f);
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f); // UI_BUTTON_CLICK
        }
        player.openInventory(pageInventory);
    }

    private void openSpawnerMenu(Player player, SpawnerData spawner) {
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String title;
        if (spawner.getStackSize() >1){
            title = languageManager.getGuiTitle("gui-title.stacked-menu", "%amount%", String.valueOf(spawner.getStackSize()), "%entity%", entityName);
        } else {
            title = languageManager.getGuiTitle("gui-title.menu", "%entity%", entityName);
        }

        // Tạo inventory với custom holder
        Inventory menu = Bukkit.createInventory(new SpawnerMenuHolder(spawner), 27, title);

        // Create chest item
        ItemStack chestItem = new ItemStack(Material.CHEST);
        ItemMeta chestMeta = chestItem.getItemMeta();
        chestMeta.setDisplayName(languageManager.getMessage("spawner-loot-item.name"));

        List<String> chestLore = new ArrayList<>();
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getAllItems().size();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = (int) ((double) currentItems / maxSlots * 100);
        String loreMessageChest = languageManager.getMessage("spawner-loot-item.lore.chest")
                .replace("%max_slots%", String.valueOf(maxSlots))
                .replace("%current_items%", String.valueOf(currentItems))
                .replace("%percent_storage%", String.valueOf(percentStorage));

        chestLore.addAll(Arrays.asList(loreMessageChest.split("\n")));
        chestMeta.setLore(chestLore);
        chestItem.setItemMeta(chestMeta);

        // Create spawner info item
        ItemStack spawnerItem = SpawnerHeadManager.getCustomHead(spawner.getEntityType(), player);
        ItemMeta spawnerMeta = spawnerItem.getItemMeta();
        spawnerMeta.setDisplayName(languageManager.getMessage("spawner-info-item.name"));
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%entity%", entityName);
        replacements.put("%stack_size%", String.valueOf(spawner.getStackSize()));
        replacements.put("%range%", String.valueOf(spawner.getSpawnerRange()));
        replacements.put("%delay%", String.valueOf(spawner.getSpawnDelay() / 20)); // Convert ticks to seconds
        replacements.put("%min_mobs%", String.valueOf(spawner.getMinMobs()));
        replacements.put("%max_mobs%", String.valueOf(spawner.getMaxMobs()));
        String lorePath = "spawner-info-item.lore.spawner-info";
        String loreMessage = languageManager.getMessage(lorePath, replacements);
        List<String> lore = Arrays.asList(loreMessage.split("\n"));
        spawnerMeta.setLore(lore);
        spawnerItem.setItemMeta(spawnerMeta);

        // Create exp bottle item
        ItemStack expItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta expMeta = expItem.getItemMeta();
        Map<String, String> nameReplacements = new HashMap<>();
        String formattedExp = languageManager.formatNumber(spawner.getSpawnerExp());
        String formattedMaxExp = languageManager.formatNumber(spawner.getMaxStoredExp());
        int percentExp = (int) ((double) spawner.getSpawnerExp() / spawner.getMaxStoredExp() * 100);

        nameReplacements.put("%current_exp%", String.valueOf(spawner.getSpawnerExp()));
        expMeta.setDisplayName(languageManager.getMessage("exp-info-item.name", nameReplacements));
        Map<String, String> loreReplacements = new HashMap<>();
        loreReplacements.put("%current_exp%", formattedExp);
        loreReplacements.put("%max_exp%", formattedMaxExp);
        loreReplacements.put("%percent_exp%", String.valueOf(percentExp));
        loreReplacements.put("%u_max_exp%", String.valueOf(spawner.getMaxStoredExp()));
        String lorePathExp = "exp-info-item.lore.exp-bottle";
        String loreMessageExp = languageManager.getMessage(lorePathExp, loreReplacements);
        List<String> loreEx = Arrays.asList(loreMessageExp.split("\n"));
        expMeta.setLore(loreEx);
        expItem.setItemMeta(expMeta);

        // Set items in menu
        menu.setItem(11, chestItem);
        menu.setItem(13, spawnerItem);
        menu.setItem(15, expItem);

        // Open menu and play sound
        player.openInventory(menu);
    }

    private void handleTakeAllItems(Player player, Inventory sourceInventory) {
        // Collect all non-null and non-air items
        Map<Integer, ItemStack> sourceItems = new HashMap<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = sourceInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                sourceItems.put(i, item.clone());
            }
        }

        if (sourceItems.isEmpty()) {
            languageManager.sendMessage(player, "messages.no-items-to-take");
            return;
        }

        // Try to transfer items
        boolean anyItemMoved = false;
        boolean inventoryFull = false;
        PlayerInventory playerInv = player.getInventory();
        int totalAmountMoved = 0;

        // Process each source slot
        for (Map.Entry<Integer, ItemStack> entry : sourceItems.entrySet()) {
            int sourceSlot = entry.getKey();
            ItemStack itemToMove = entry.getValue();

            // Try to partially add items
            int amountToMove = itemToMove.getAmount();
            int amountMoved = 0;

            // Check each inventory slot for partial stacking
            for (int i = 0; i < 36 && amountToMove > 0; i++) {
                ItemStack targetItem = playerInv.getItem(i);

                if (targetItem == null || targetItem.getType() == Material.AIR) {
                    // Empty slot - can take full stack or remaining amount
                    ItemStack newStack = itemToMove.clone();
                    newStack.setAmount(Math.min(amountToMove, itemToMove.getMaxStackSize()));
                    playerInv.setItem(i, newStack);
                    amountMoved += newStack.getAmount();
                    amountToMove -= newStack.getAmount();
                    anyItemMoved = true;
                }
                else if (targetItem.isSimilar(itemToMove)) {
                    // Similar item - can stack
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

            // Update or remove source item based on how much was moved
            if (amountMoved > 0) {
                totalAmountMoved += amountMoved;
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

        // Send appropriate message
        if (!anyItemMoved) {
            languageManager.sendMessage(player, "messages.inventory-full");
        } else if (inventoryFull) {
            languageManager.sendMessage(player, "messages.take-some-items", "%amount%", String.valueOf(totalAmountMoved));
        } else {
            languageManager.sendMessage(player, "messages.take-all-items", "%amount%", String.valueOf(totalAmountMoved));
        }

        player.updateInventory();
    }
}