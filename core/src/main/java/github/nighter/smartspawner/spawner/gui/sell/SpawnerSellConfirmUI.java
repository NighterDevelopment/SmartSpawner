package github.nighter.smartspawner.spawner.gui.sell;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.nms.VersionInitializer;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SpawnerSellConfirmUI {
    private static final int GUI_SIZE = 27;

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    // Cached layout - loaded once for performance
    private GuiLayout cachedLayout;

    public SpawnerSellConfirmUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        loadLayout();
    }

    private void loadLayout() {
        this.cachedLayout = plugin.getGuiLayoutConfig().getCurrentSellConfirmLayout();
    }

    public void reload() {
        loadLayout();
    }

    public enum PreviousGui {
        MAIN_MENU,
        STORAGE
    }

    public void openSellConfirmGui(Player player, SpawnerData spawner, PreviousGui previousGui, boolean collectExp) {
        if (player == null || spawner == null) {
            return;
        }

        // Check if there are items to sell before opening
        if (spawner.getVirtualInventory().getUsedSlots() == 0) {
            plugin.getMessageService().sendMessage(player, "no_items");
            return;
        }

        // OPTIMIZATION: Check if sell confirmation should be skipped
        if (plugin.getGuiLayoutConfig().isSkipSellConfirmation()) {
            // Atomically claim the interaction slot – abort if another request already owns it
            if (!spawner.tryMarkInteracted()) {
                return;
            }

            // Collect exp if requested
            if (collectExp) {
                plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, true);
            }

            // Clear interaction state
            spawner.clearInteracted();

            // Trigger the actual sell operation
            plugin.getSpawnerSellManager().sellAllItems(player, spawner);

            // Close current inventory if open
            player.closeInventory();
            return;
        }

        // Atomically mark as interacted – prevents a second confirm GUI from being opened
        // while one is already pending (blocks packet-replay / GUI-spam exploits).
        if (!spawner.tryMarkInteracted()) {
            return;
        }

        // Cache title - no placeholders needed
        String title = languageManager.getGuiTitle("gui_title_sell_confirm", null);
        Inventory gui = Bukkit.createInventory(new SpawnerSellConfirmHolder(spawner, previousGui, collectExp), GUI_SIZE, title);

        populateSellConfirmGui(gui, player, spawner, collectExp);

        player.openInventory(gui);
    }

    private void populateSellConfirmGui(Inventory gui, Player player, SpawnerData spawner, boolean collectExp) {
        // OPTIMIZATION: Create placeholders once and reuse for all buttons
        Map<String, String> placeholders = createPlaceholders(spawner, collectExp);

        // OPTIMIZATION: Use cached layout instead of querying every time
        if (cachedLayout == null) {
            plugin.getLogger().warning("Sell confirm layout not loaded, using empty GUI");
            return;
        }

        // Iterate through all buttons in the layout
        for (GuiButton button : cachedLayout.getAllButtons().values()) {
            if (!button.isEnabled()) {
                continue;
            }

            ItemStack buttonItem;

            // Check if this is an info button (spawner display)
            if (button.isInfoButton()) {
                buttonItem = createSpawnerInfoButton(player, placeholders);
            } else {
                // OPTIMIZATION: Use getAnyActionFromButton to check all click types
                String action = getAnyActionFromButton(button);
                if (action == null || action.isEmpty()) {
                    continue;
                }

                switch (action) {
                    case "cancel":
                        buttonItem = createCancelButton(button.getMaterial(), placeholders);
                        break;
                    case "confirm":
                        buttonItem = createConfirmButton(button.getMaterial(), placeholders, collectExp);
                        break;
                    case "none":
                        // Display-only button (spawner info) - fallback for old format
                        buttonItem = createSpawnerInfoButton(player, placeholders);
                        break;
                    default:
                        plugin.getLogger().warning("Unknown action in sell confirm GUI: " + action);
                        continue;
                }
            }

            gui.setItem(button.getSlot(), buttonItem);
        }
    }

    private ItemStack createCancelButton(Material material, Map<String, String> placeholders) {
        String name = languageManager.getGuiItemName("button_sell_cancel.name", placeholders);
        String[] lore = languageManager.getGuiItemLore("button_sell_cancel.lore", placeholders);
        return createButton(material, name, lore);
    }

    private ItemStack createConfirmButton(Material material, Map<String, String> placeholders, boolean collectExp) {
        // Use different button key based on whether exp is collected
        String buttonKey = collectExp ? "button_sell_confirm_with_exp" : "button_sell_confirm";
        String name = languageManager.getGuiItemName(buttonKey + ".name", placeholders);
        String[] lore = languageManager.getGuiItemLore(buttonKey + ".lore", placeholders);
        return createButton(material, name, lore);
    }

    private ItemStack createSpawnerInfoButton(Player player, Map<String, String> placeholders) {
        // OPTIMIZATION: Reuse placeholders passed from parent

        // Prepare the meta modifier consumer
        Consumer<ItemMeta> metaModifier = meta -> {
            // Set display name
            meta.setDisplayName(languageManager.getGuiItemName("button_sell_info.name", placeholders));

            // Get and set lore
            String[] lore = languageManager.getGuiItemLore("button_sell_info.lore", placeholders);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        };

        ItemStack spawnerItem;

        // OPTIMIZATION: Get cached spawner type from placeholders
        if (placeholders.containsKey("spawnedItem")) {
            spawnerItem = SpawnerMobHeadTexture.getItemSpawnerHead(
                Material.valueOf(placeholders.get("spawnedItem")), player, metaModifier);
        } else {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(
                org.bukkit.entity.EntityType.valueOf(placeholders.get("entityType")),
                player, metaModifier);
        }

        if (spawnerItem.getType() == Material.SPAWNER) {
            VersionInitializer.hideTooltip(spawnerItem);
        }

        return spawnerItem;
    }

    private Map<String, String> createPlaceholders(SpawnerData spawner, boolean collectExp) {
        // OPTIMIZATION: Calculate initial capacity to avoid HashMap resizing
        Map<String, String> placeholders = new HashMap<>(8);

        // OPTIMIZATION: Get entity name once and cache
        String entityName;
        boolean isItemSpawner = spawner.isItemSpawner();

        if (isItemSpawner) {
            Material spawnedItem = spawner.getSpawnedItemMaterial();
            entityName = languageManager.getVanillaItemName(spawnedItem);
            placeholders.put("spawnedItem", spawnedItem.name());
        } else {
            org.bukkit.entity.EntityType entityType = spawner.getEntityType();
            entityName = languageManager.getFormattedMobName(entityType);
            placeholders.put("entityType", entityType.name());
        }

        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));

        // OPTIMIZATION: Check sell value dirty only once
        if (spawner.isSellValueDirty()) {
            spawner.recalculateSellValue();
        }

        // OPTIMIZATION: Get all values in single pass
        double totalSellPrice = spawner.getAccumulatedSellValue();
        int currentItems = spawner.getVirtualInventory().getUsedSlots();
        int currentExp = spawner.getSpawnerExp();

        placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));
        placeholders.put("current_items", Integer.toString(currentItems));
        placeholders.put("current_exp", Integer.toString(currentExp));

        return placeholders;
    }

    private ItemStack createButton(Material material, String name, String[] lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(name);
            }
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Get any action from button - checks click, left_click, right_click
     * OPTIMIZATION: Return first found action for item creation
     */
    private String getAnyActionFromButton(GuiButton button) {
        // Check in priority order: click -> left_click -> right_click
        String action = button.getDefaultAction(); // checks "click" first
        if (action != null && !action.isEmpty()) {
            return action;
        }

        // Check left_click
        action = button.getAction("left_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        // Check right_click
        action = button.getAction("right_click");
        if (action != null && !action.isEmpty()) {
            return action;
        }

        return null;
    }
}
