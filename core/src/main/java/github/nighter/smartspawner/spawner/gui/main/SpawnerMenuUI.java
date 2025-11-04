package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.nms.VersionInitializer;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import github.nighter.smartspawner.spawner.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.loot.LootItem;
import github.nighter.smartspawner.spawner.config.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.api.events.SpawnerOpenGUIEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerMenuUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    // Cache frequently used formatting strings and pattern lookups
    private static final String LOOT_ITEM_FORMAT_KEY = "spawner_storage_item.loot_items";
    private static final String EMPTY_LOOT_MESSAGE_KEY = "spawner_storage_item.loot_items_empty";

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    // Format strings - initialized in constructor to avoid repeated lookups
    private String lootItemFormat;
    private String emptyLootMessage;

    // Cache for GUI items - cleared when spawner data changes
    // Using ConcurrentHashMap for thread-safety with Folia's async scheduler
    private final Map<String, ItemStack> itemCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_TIME_MS = 30000; // 30 seconds
    
    // Cache for base spawner info items (without timer placeholders)
    // Key: spawnerId|entityType|stackSize|hasShopPermission|materialType
    private final Map<String, ItemStack> baseSpawnerInfoCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Cache for entity names to avoid repeated lookups
    private final Map<EntityType, String> entityNameCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<EntityType, String> entityNameSmallCapsCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Cache for permission checks per player session (cleared on GUI close or cache clear)
    private final Map<UUID, Boolean> playerShopPermissionCache = new java.util.concurrent.ConcurrentHashMap<>();

    public SpawnerMenuUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        loadConfig();
    }

    public void loadConfig() {
        clearCache();
        this.lootItemFormat = languageManager.getGuiItemName(LOOT_ITEM_FORMAT_KEY, EMPTY_PLACEHOLDERS);
        this.emptyLootMessage = languageManager.getGuiItemName(EMPTY_LOOT_MESSAGE_KEY, EMPTY_PLACEHOLDERS);
    }

    public void clearCache() {
        itemCache.clear();
        cacheTimestamps.clear();
        baseSpawnerInfoCache.clear();
        entityNameCache.clear();
        entityNameSmallCapsCache.clear();
        playerShopPermissionCache.clear();
    }

    public void invalidateSpawnerCache(String spawnerId) {
        itemCache.entrySet().removeIf(entry -> entry.getKey().startsWith(spawnerId + "|"));
        cacheTimestamps.entrySet().removeIf(entry -> entry.getKey().startsWith(spawnerId + "|"));
        baseSpawnerInfoCache.entrySet().removeIf(entry -> entry.getKey().startsWith(spawnerId + "|"));
    }
    
    /**
     * Clear player-specific caches when player closes GUI or disconnects
     */
    public void clearPlayerCache(UUID playerUUID) {
        playerShopPermissionCache.remove(playerUUID);
    }

    private boolean isCacheEntryExpired(String cacheKey) {
        Long timestamp = cacheTimestamps.get(cacheKey);
        return timestamp == null || System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME_MS;
    }

    public void openSpawnerMenu(Player player, SpawnerData spawner, boolean refresh) {
        if(SpawnerOpenGUIEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerOpenGUIEvent openEvent = new SpawnerOpenGUIEvent(
                    player,
                    spawner.getSpawnerLocation(),
                    spawner.getEntityType(),
                    spawner.getStackSize(),
                    refresh
            );
            Bukkit.getPluginManager().callEvent(openEvent);

            if (openEvent.isCancelled()) {
                return;
            }
        }

        Inventory menu = createMenu(spawner);
        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMainLayout();

        // Populate menu items based on layout configuration
        ItemStack[] items = new ItemStack[INVENTORY_SIZE];
        
        // Add storage button if enabled in layout
        GuiButton storageButton = layout.getButton("storage");
        if (storageButton != null) {
            items[storageButton.getSlot()] = createLootStorageItem(spawner);
        }
        
        // Add spawner info button if enabled in layout - handle conditional buttons
        GuiButton spawnerInfoButton = getSpawnerInfoButton(layout, player);
        if (spawnerInfoButton != null) {
            items[spawnerInfoButton.getSlot()] = createSpawnerInfoItem(player, spawner);
        }
        
        // Add exp button if enabled in layout
        GuiButton expButton = layout.getButton("exp");
        if (expButton != null) {
            items[expButton.getSlot()] = createExpItem(spawner);
        }

        // Set all items at once instead of one by one
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                menu.setItem(i, items[i]);
            }
        }

        // Open inventory and play sound if not refreshing
        player.openInventory(menu);

        if (!refresh) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        }

        // Force an immediate timer update for the newly opened GUI (only if timer placeholders are enabled)
        // This ensures the timer displays correctly from the start
        // Skip timer update if player is in spectator mode to prevent activating spawner
        if (plugin.getSpawnerGuiViewManager().isTimerPlaceholdersEnabled()
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            plugin.getSpawnerGuiViewManager().forceTimerUpdate(player, spawner);
        }
    }

    private Inventory createMenu(SpawnerData spawner) {
        EntityType entityType = spawner.getEntityType();
        
        // Get entity names from cache
        String entityName = entityNameCache.computeIfAbsent(entityType, 
            type -> languageManager.getFormattedMobName(type));
        String entityNameSmallCaps = entityNameSmallCapsCache.computeIfAbsent(entityType,
            type -> languageManager.getSmallCaps(languageManager.getFormattedMobName(type)));

        // Use string builder for efficient placeholder creation
        Map<String, String> placeholders = new HashMap<>(4);
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
        placeholders.put("amount", String.valueOf(spawner.getStackSize()));

        String title;
        if (spawner.getStackSize() > 1) {
            title = languageManager.getGuiTitle("gui_title_main.stacked_spawner", placeholders);
        } else {
            title = languageManager.getGuiTitle("gui_title_main.single_spawner", placeholders);
        }

        return Bukkit.createInventory(new SpawnerMenuHolder(spawner), INVENTORY_SIZE, title);
    }

    public ItemStack createLootStorageItem(SpawnerData spawner) {
        // Generate cache key based on spawner state
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        String cacheKey = spawner.getSpawnerId() + "|storage|" + currentItems + "|" + maxSlots + "|" + virtualInventory.hashCode();
        
        // Check cache first
        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            return cachedItem.clone();
        }

        // Not in cache, create new item
        ItemStack chestItem = new ItemStack(Material.CHEST);
        ItemMeta chestMeta = chestItem.getItemMeta();
        if (chestMeta == null) return chestItem;

        // Smart placeholder detection: First, get the raw name and lore templates
        String nameTemplate = languageManager.getGuiItemName("spawner_storage_item.name", EMPTY_PLACEHOLDERS);
        List<String> loreTemplate = languageManager.getGuiItemLoreAsList("spawner_storage_item.lore", EMPTY_PLACEHOLDERS);
        
        // Define all available placeholders
        Set<String> availablePlaceholders = Set.of(
            "max_slots", "current_items", "percent_storage_rounded", "loot_items"
        );
        
        // Detect which placeholders are actually used
        Set<String> usedPlaceholders = new HashSet<>();
        usedPlaceholders.addAll(detectUsedPlaceholders(nameTemplate, availablePlaceholders));
        usedPlaceholders.addAll(detectUsedPlaceholders(loreTemplate, availablePlaceholders));
        
        // Build only the placeholders that are actually used
        Map<String, String> placeholders = new HashMap<>();
        
        if (usedPlaceholders.contains("max_slots")) {
            placeholders.put("max_slots", languageManager.formatNumber(maxSlots));
        }
        if (usedPlaceholders.contains("current_items")) {
            placeholders.put("current_items", String.valueOf(currentItems));
        }
        if (usedPlaceholders.contains("percent_storage_rounded")) {
            int percentStorage = calculatePercentage(currentItems, maxSlots);
            placeholders.put("percent_storage_rounded", String.valueOf(percentStorage));
        }
        if (usedPlaceholders.contains("loot_items")) {
            Map<VirtualInventory.ItemSignature, Long> storedItems = virtualInventory.getConsolidatedItems();
            String lootItemsText = buildLootItemsText(spawner.getEntityType(), storedItems);
            placeholders.put("loot_items", lootItemsText);
        }

        // Set display name
        chestMeta.setDisplayName(languageManager.getGuiItemName("spawner_storage_item.name", placeholders));

        // Get lore efficiently
        List<String> lore = languageManager.getGuiItemLoreWithMultilinePlaceholders("spawner_storage_item.lore", placeholders);
        chestMeta.setLore(lore);
        chestItem.setItemMeta(chestMeta);

        // Cache the result
        itemCache.put(cacheKey, chestItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return chestItem;
    }

    private String buildLootItemsText(EntityType entityType, Map<VirtualInventory.ItemSignature, Long> storedItems) {
        // Create material-to-amount map for quick lookups
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : storedItems.entrySet()) {
            Material material = entry.getKey().getTemplateRef().getType();
            materialAmountMap.merge(material, entry.getValue(), Long::sum);
        }

        // Get possible loot items
        EntityLootConfig lootConfig = plugin.getSpawnerSettingsConfig().getLootConfig(entityType);
        List<LootItem> possibleLootItems = lootConfig != null
                ? lootConfig.getAllItems()
                : Collections.emptyList();

        // Return early for empty cases
        if (possibleLootItems.isEmpty() && storedItems.isEmpty()) {
            return emptyLootMessage;
        }

        // Use StringBuilder for efficient string concatenation
        StringBuilder builder = new StringBuilder(Math.max(possibleLootItems.size(), storedItems.size()) * 40);

        if (!possibleLootItems.isEmpty()) {
            // Sort items by name for consistent display
            possibleLootItems.sort(Comparator.comparing(item -> languageManager.getVanillaItemName(item.getMaterial())));

            for (LootItem lootItem : possibleLootItems) {
                Material material = lootItem.getMaterial();
                long amount = materialAmountMap.getOrDefault(material, 0L);

                String materialName = languageManager.getVanillaItemName(material);
                String materialNameSmallCaps = languageManager.getSmallCaps(languageManager.getVanillaItemName(material));
                String formattedAmount = languageManager.formatNumber(amount);
                String chance = String.format("%.1f", lootItem.getChance()) + "%";

                // Format the line with minimal string operations
                String line = lootItemFormat
                        .replace("{item_name}", materialName)
                        .replace("{ɪᴛᴇᴍ_ɴᴀᴍᴇ}", materialNameSmallCaps)
                        .replace("{amount}", formattedAmount)
                        .replace("{raw_amount}", String.valueOf(amount))
                        .replace("{chance}", chance);

                builder.append(line).append('\n');
            }
        } else if (!storedItems.isEmpty()) {
            // Sort items by name
            List<Map.Entry<VirtualInventory.ItemSignature, Long>> sortedItems =
                    new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));

            for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : sortedItems) {
                ItemStack templateItem = entry.getKey().getTemplateRef();
                Material material = templateItem.getType();
                long amount = entry.getValue();

                String materialName = languageManager.getVanillaItemName(material);
                String materialNameSmallCaps = languageManager.getSmallCaps(languageManager.getVanillaItemName(material));
                String formattedAmount = languageManager.formatNumber(amount);

                // Format with minimal replacements
                String line = lootItemFormat
                        .replace("{item_name}", materialName)
                        .replace("{ɪᴛᴇᴍ_ɴᴀᴍᴇ}", materialNameSmallCaps)
                        .replace("{amount}", formattedAmount)
                        .replace("{raw_amount}", String.valueOf(amount))
                        .replace("{chance}", "");

                builder.append(line).append('\n');
            }
        }

        // Remove trailing newline if it exists
        int length = builder.length();
        if (length > 0 && builder.charAt(length - 1) == '\n') {
            builder.setLength(length - 1);
        }

        return builder.toString();
    }

    public ItemStack createSpawnerInfoItem(Player player, SpawnerData spawner) {
        // Get layout configuration and entity type upfront
        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMainLayout();
        GuiButton spawnerInfoButton = layout.getButton("spawner_info");
        EntityType entityType = spawner.getEntityType();
        
        // Cache permission check for this player
        UUID playerUUID = player.getUniqueId();
        boolean hasShopPermission = playerShopPermissionCache.computeIfAbsent(playerUUID, 
            uuid -> plugin.hasSellIntegration() && player.hasPermission("smartspawner.sellall"));
        
        // Determine material type for cache key
        Material headMaterial = (spawnerInfoButton != null) ? spawnerInfoButton.getMaterial() : Material.PLAYER_HEAD;
        
        // Create base cache key (excludes dynamic timer placeholder)
        String baseCacheKey = spawner.getSpawnerId() + "|spawner_info|" + entityType + "|" 
            + spawner.getStackSize() + "|" + hasShopPermission + "|" + headMaterial;
        
        // Check if we need to detect timer placeholder usage
        String loreKey = hasShopPermission ? "spawner_info_item.lore" : "spawner_info_item.lore_no_shop";
        String nameTemplate = languageManager.getGuiItemName("spawner_info_item.name", EMPTY_PLACEHOLDERS);
        List<String> loreTemplate = languageManager.getGuiItemLoreAsList(loreKey, EMPTY_PLACEHOLDERS);
        
        // Check if timer placeholder is used
        boolean usesTimer = nameTemplate.contains("{time}") || 
                           loreTemplate.stream().anyMatch(line -> line.contains("{time}"));
        
        // If timer is used, we need to create fresh item each time with updated timer
        // Otherwise, we can use full caching like other items
        if (usesTimer) {
            // Get or create base item (everything except timer)
            ItemStack baseItem = getOrCreateBaseSpawnerInfoItem(
                baseCacheKey, spawner, player, entityType, hasShopPermission, headMaterial, 
                spawnerInfoButton, nameTemplate, loreTemplate, loreKey
            );
            
            // Clone base and update only timer placeholder
            ItemStack displayItem = baseItem.clone();
            updateTimerPlaceholder(displayItem, spawner, player, nameTemplate, loreTemplate, loreKey);
            return displayItem;
        } else {
            // No timer, can use full caching
            return getOrCreateBaseSpawnerInfoItem(
                baseCacheKey, spawner, player, entityType, hasShopPermission, headMaterial,
                spawnerInfoButton, nameTemplate, loreTemplate, loreKey
            );
        }
    }
    
    /**
     * Get or create the base spawner info item (without timer updates)
     * This caches the expensive SkullMeta creation and most placeholder replacements
     */
    private ItemStack getOrCreateBaseSpawnerInfoItem(
            String cacheKey, SpawnerData spawner, Player player, EntityType entityType,
            boolean hasShopPermission, Material headMaterial, GuiButton spawnerInfoButton,
            String nameTemplate, List<String> loreTemplate, String loreKey) {
        
        // Check cache first
        ItemStack cachedItem = baseSpawnerInfoCache.get(cacheKey);
        if (cachedItem != null) {
            return cachedItem.clone();
        }
        
        // Create the head ItemStack (expensive operation - cached by SpawnerMobHeadTexture)
        ItemStack spawnerItem;
        if (headMaterial == Material.PLAYER_HEAD) {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, player);
        } else if (spawnerInfoButton != null) {
            spawnerItem = new ItemStack(spawnerInfoButton.getMaterial());
        } else {
            spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, player);
        }
        
        ItemMeta spawnerMeta = spawnerItem.getItemMeta();
        if (spawnerMeta == null) return spawnerItem;

        // Define all available placeholders
        Set<String> availablePlaceholders = Set.of(
            "entity", "ᴇɴᴛɪᴛʏ", "entity_type", "stack_size", "range", "delay", "min_mobs", "max_mobs",
            "current_items", "max_items", "percent_storage_decimal", "percent_storage_rounded",
            "current_exp", "max_exp", "raw_current_exp", "raw_max_exp", "percent_exp_decimal", "percent_exp_rounded",
            "total_sell_price", "time"
        );
        
        // Detect which placeholders are actually used (optimized batch detection)
        Set<String> usedPlaceholders = new HashSet<>();
        usedPlaceholders.addAll(detectUsedPlaceholders(nameTemplate, availablePlaceholders));
        usedPlaceholders.addAll(detectUsedPlaceholders(loreTemplate, availablePlaceholders));
        
        // Build placeholders map with cached entity names
        Map<String, String> placeholders = buildSpawnerInfoPlaceholders(
            spawner, entityType, usedPlaceholders, hasShopPermission
        );
        
        // Set display name and lore
        spawnerMeta.setDisplayName(languageManager.getGuiItemName("spawner_info_item.name", placeholders));
        List<String> lore = languageManager.getGuiItemLoreWithMultilinePlaceholders(loreKey, placeholders);
        spawnerMeta.setLore(lore);
        spawnerItem.setItemMeta(spawnerMeta);
        
        if (spawnerItem.getType() == Material.SPAWNER) {
            VersionInitializer.hideTooltip(spawnerItem);
        }
        
        // Cache the base item
        baseSpawnerInfoCache.put(cacheKey, spawnerItem.clone());
        
        return spawnerItem;
    }
    
    /**
     * Update only the timer placeholder in an existing item
     * This is much faster than recreating the entire item
     */
    private void updateTimerPlaceholder(ItemStack item, SpawnerData spawner, Player player,
                                       String nameTemplate, List<String> loreTemplate, String loreKey) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // Calculate timer value
        String timerValue = plugin.getSpawnerGuiViewManager().calculateTimerDisplay(spawner, player);
        
        // Create minimal placeholder map with just timer
        Map<String, String> timerPlaceholder = Collections.singletonMap("time", timerValue);
        
        // Update display name if it contains timer
        if (nameTemplate.contains("{time}")) {
            String currentName = meta.getDisplayName();
            String updatedName = currentName.replace("{time}", timerValue);
            meta.setDisplayName(updatedName);
        }
        
        // Update lore if it contains timer
        if (loreTemplate.stream().anyMatch(line -> line.contains("{time}"))) {
            List<String> currentLore = meta.getLore();
            if (currentLore != null) {
                List<String> updatedLore = new ArrayList<>(currentLore.size());
                for (String line : currentLore) {
                    updatedLore.add(line.replace("{time}", timerValue));
                }
                meta.setLore(updatedLore);
            }
        }
        
        item.setItemMeta(meta);
    }
    
    /**
     * Build placeholder map for spawner info with cached entity names
     * Only computes placeholders that are actually used
     */
    private Map<String, String> buildSpawnerInfoPlaceholders(
            SpawnerData spawner, EntityType entityType, Set<String> usedPlaceholders, 
            boolean hasShopPermission) {
        
        Map<String, String> placeholders = new HashMap<>();
        int stackSize = spawner.getStackSize();
        
        // Entity information - use cached names
        if (usedPlaceholders.contains("entity") || usedPlaceholders.contains("ᴇɴᴛɪᴛʏ")) {
            String entityName = entityNameCache.computeIfAbsent(entityType, 
                type -> languageManager.getFormattedMobName(type));
            
            if (usedPlaceholders.contains("entity")) {
                placeholders.put("entity", entityName);
            }
            if (usedPlaceholders.contains("ᴇɴᴛɪᴛʏ")) {
                String entityNameSmallCaps = entityNameSmallCapsCache.computeIfAbsent(entityType,
                    type -> languageManager.getSmallCaps(entityName));
                placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
            }
        }
        if (usedPlaceholders.contains("entity_type")) {
            placeholders.put("entity_type", entityType.toString());
        }

        // Stack information
        if (usedPlaceholders.contains("stack_size")) {
            placeholders.put("stack_size", String.valueOf(stackSize));
        }

        // Spawner settings
        if (usedPlaceholders.contains("range")) {
            placeholders.put("range", String.valueOf(spawner.getSpawnerRange()));
        }
        if (usedPlaceholders.contains("delay")) {
            long delaySeconds = spawner.getSpawnDelay() / TICKS_PER_SECOND;
            placeholders.put("delay", String.valueOf(delaySeconds));
        }
        if (usedPlaceholders.contains("min_mobs")) {
            placeholders.put("min_mobs", String.valueOf(spawner.getMinMobs()));
        }
        if (usedPlaceholders.contains("max_mobs")) {
            placeholders.put("max_mobs", String.valueOf(spawner.getMaxMobs()));
        }

        // Storage information
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        
        if (usedPlaceholders.contains("current_items")) {
            placeholders.put("current_items", String.valueOf(currentItems));
        }
        if (usedPlaceholders.contains("max_items")) {
            placeholders.put("max_items", languageManager.formatNumber(maxSlots));
        }
        if (usedPlaceholders.contains("percent_storage_decimal") || usedPlaceholders.contains("percent_storage_rounded")) {
            double percentStorageDecimal = maxSlots > 0 ? ((double) currentItems / maxSlots) * 100 : 0;
            if (usedPlaceholders.contains("percent_storage_decimal")) {
                placeholders.put("percent_storage_decimal", String.format("%.1f", percentStorageDecimal));
            }
            if (usedPlaceholders.contains("percent_storage_rounded")) {
                placeholders.put("percent_storage_rounded", String.valueOf((int) Math.round(percentStorageDecimal)));
            }
        }

        // Experience information
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        
        if (usedPlaceholders.contains("current_exp")) {
            placeholders.put("current_exp", languageManager.formatNumber(currentExp));
        }
        if (usedPlaceholders.contains("max_exp")) {
            placeholders.put("max_exp", languageManager.formatNumber(maxExp));
        }
        if (usedPlaceholders.contains("raw_current_exp")) {
            placeholders.put("raw_current_exp", String.valueOf(currentExp));
        }
        if (usedPlaceholders.contains("raw_max_exp")) {
            placeholders.put("raw_max_exp", String.valueOf(maxExp));
        }
        if (usedPlaceholders.contains("percent_exp_decimal") || usedPlaceholders.contains("percent_exp_rounded")) {
            double percentExpDecimal = maxExp > 0 ? ((double) currentExp / maxExp) * 100 : 0;
            if (usedPlaceholders.contains("percent_exp_decimal")) {
                placeholders.put("percent_exp_decimal", String.format("%.1f", percentExpDecimal));
            }
            if (usedPlaceholders.contains("percent_exp_rounded")) {
                placeholders.put("percent_exp_rounded", String.valueOf((int) Math.round(percentExpDecimal)));
            }
        }

        // Total sell price information
        if (usedPlaceholders.contains("total_sell_price")) {
            if (spawner.isSellValueDirty()) {
                spawner.recalculateSellValue();
            }
            double totalSellPrice = spawner.getAccumulatedSellValue();
            placeholders.put("total_sell_price", languageManager.formatNumber(totalSellPrice));
        }

        // Note: Timer is NOT included here - it's handled separately in updateTimerPlaceholder
        
        return placeholders;
    }

    public ItemStack createExpItem(SpawnerData spawner) {
        // Get important data upfront
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        int percentExp = calculatePercentage(currentExp, maxExp);

        // Create cache key for this specific spawner's exp state
        String cacheKey = spawner.getSpawnerId() + "|exp|" + currentExp + "|" + maxExp;

        // Check cache first
        ItemStack cachedItem = itemCache.get(cacheKey);
        if (cachedItem != null && !isCacheEntryExpired(cacheKey)) {
            return cachedItem.clone();
        }

        // Not in cache, create the ItemStack
        ItemStack expItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta expMeta = expItem.getItemMeta();
        if (expMeta == null) return expItem;

        // Format numbers once for display
        String formattedExp = languageManager.formatNumber(currentExp);
        String formattedMaxExp = languageManager.formatNumber(maxExp);

        // Prepare all placeholders
        Map<String, String> placeholders = new HashMap<>(5); // Preallocate with expected capacity
        placeholders.put("current_exp", formattedExp);
        placeholders.put("raw_current_exp", String.valueOf(currentExp));
        placeholders.put("max_exp", formattedMaxExp);
        placeholders.put("percent_exp_rounded", String.valueOf(percentExp));
        placeholders.put("u_max_exp", String.valueOf(maxExp));

        // Set name and lore
        expMeta.setDisplayName(languageManager.getGuiItemName("exp_info_item.name", placeholders));
        List<String> loreExp = languageManager.getGuiItemLoreAsList("exp_info_item.lore", placeholders);
        expMeta.setLore(loreExp);

        expItem.setItemMeta(expMeta);

        // Cache the result
        itemCache.put(cacheKey, expItem.clone());
        cacheTimestamps.put(cacheKey, System.currentTimeMillis());

        return expItem;
    }

    private int calculatePercentage(long current, long maximum) {
        return maximum > 0 ? (int) ((double) current / maximum * 100) : 0;
    }

    /**
     * Optimized placeholder detection - scans text once and checks all placeholders
     * @param text The text to scan for placeholders
     * @param availablePlaceholders Set of all available placeholder keys
     * @return Set of placeholder keys that are actually used in the text
     */
    private Set<String> detectUsedPlaceholders(String text, Set<String> availablePlaceholders) {
        Set<String> usedPlaceholders = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return usedPlaceholders;
        }
        
        // Single pass through text, checking all placeholders at once
        // This is more efficient than the previous approach which iterated placeholders
        for (String placeholder : availablePlaceholders) {
            String wrappedPlaceholder = "{" + placeholder + "}";
            if (text.contains(wrappedPlaceholder)) {
                usedPlaceholders.add(placeholder);
            }
        }
        return usedPlaceholders;
    }

    /**
     * Optimized batch placeholder detection for multiple text lines
     * Combines all text and scans once instead of scanning each line separately
     * @param textList The list of strings to scan for placeholders
     * @param availablePlaceholders Set of all available placeholder keys
     * @return Set of placeholder keys that are actually used in any of the texts
     */
    private Set<String> detectUsedPlaceholders(List<String> textList, Set<String> availablePlaceholders) {
        if (textList == null || textList.isEmpty()) {
            return new HashSet<>();
        }
        
        // Combine all lines into single string with delimiter for single-pass scanning
        // This reduces iterations from O(lines * placeholders) to O(placeholders)
        String combinedText = String.join("\n", textList);
        return detectUsedPlaceholders(combinedText, availablePlaceholders);
    }

    private GuiButton getSpawnerInfoButton(GuiLayout layout, Player player) {
        // Check for shop integration permission
        boolean hasShopPermission = plugin.hasSellIntegration() && player.hasPermission("smartspawner.sellall");

        // Try to get the appropriate conditional button first
        if (hasShopPermission) {
            GuiButton shopButton = layout.getButton("spawner_info_with_shop");
            if (shopButton != null) {
                return shopButton;
            }
        } else {
            GuiButton noShopButton = layout.getButton("spawner_info_no_shop");
            if (noShopButton != null) {
                return noShopButton;
            }
        }

        // Fallback to the generic spawner_info button if conditional ones don't exist
        return layout.getButton("spawner_info");
    }
}
