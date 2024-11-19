package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.*;
import me.nighter.smartSpawner.managers.*;
import me.nighter.smartSpawner.utils.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import me.nighter.smartSpawner.utils.SpawnerStackerHolder;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;
import java.util.*;

public class SpawnerListener implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerManager spawnerManager;
    private final SpawnerStackHandler stackHandler;

    public SpawnerListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.stackHandler = plugin.getSpawnerStackHandler();
    }

    /**
     * Prevent natural spawning from modified spawners
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(SpawnerSpawnEvent event){
        if (event.getSpawner() == null) return;
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(event.getSpawner().getLocation());
        if (spawner != null && spawner.getSpawnerActive()) {
            event.setCancelled(true);
//            configManager.debug("Cancelled spawner spawn event for spawner ID: " + spawner.getSpawnerId());
        }
    }

    // Helper method to create new spawner
    private SpawnerData createNewSpawner(Block block, Player player) {
        String newSpawnerId = UUID.randomUUID().toString().substring(0, 8);
        CreatureSpawner creatureSpawner = (CreatureSpawner) block.getState();
        EntityType entityType = creatureSpawner.getSpawnedType();

        // Handle null entityType
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            entityType = configManager.getDefaultEntityType();
        }

        creatureSpawner.setSpawnedType(entityType);
        creatureSpawner.update();
        Location loc = block.getLocation();
        loc.getWorld().spawnParticle(
                Particle.SPELL_WITCH,
                loc.clone().add(0.5, 0.5, 0.5),
                50, 0.5, 0.5, 0.5, 0
        );

        // Create new spawner with default config values
        SpawnerData spawner = new SpawnerData(newSpawnerId, block.getLocation(), entityType, plugin);
        spawner.setSpawnerActive(true);

        // Add to manager and save
        spawnerManager.addSpawner(newSpawnerId, spawner);
        spawnerManager.saveSpawnerData();
        languageManager.sendMessage(player, "messages.activated");
        configManager.debug("Created new spawner with ID: " + newSpawnerId + " at " + block.getLocation());
        return spawner;
    }

    // Helper method to handle null entity type
    private void handleNullEntityType(SpawnerData spawner, Block block, Player player) {
        EntityType defaultType = configManager.getDefaultEntityType();
        spawner.setEntityType(defaultType);

        CreatureSpawner creatureSpawner = (CreatureSpawner) block.getState();
        creatureSpawner.setSpawnedType(defaultType);
        creatureSpawner.update();

        spawnerManager.saveSpawnerData();
        languageManager.sendMessage(player, "messages.activate_default",
                "%type%", languageManager.getFormattedMobName(defaultType));
    }

    @EventHandler
    public void onSpawnerClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        CreatureSpawner cspawner = (CreatureSpawner) event.getClickedBlock().getState();

        // Allow block placement when sneaking
        if (player.isSneaking() && itemInHand.getType().isBlock() && itemInHand.getType() != Material.SPAWNER) {
            event.setCancelled(false);
            return;
        }

        // For Bedrock players, only open GUI if they're not trying to break the block
        if (isBedrockPlayer(player)) {
            // Check if they're in "destroy mode" (typically when holding a tool)
            if (itemInHand.getType().name().endsWith("_PICKAXE") || itemInHand.getType().name().endsWith("_SHOVEL") || itemInHand.getType().name().endsWith("_HOE") || itemInHand.getType().name().endsWith("_AXE")) {
                event.setCancelled(false);
                return;
            }
        }
//        configManager.debug(isBedrockPlayer(player) ? "Bedrock player detected" : "Java player detected");

        event.setCancelled(true);
        // Direct O(1) lookup instead of iteration
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(block.getLocation());

        // Handle new spawner creation if it doesn't exist
        if (spawner == null) {
            spawner = createNewSpawner(block, player);
            if (spawner == null) return; // Creation failed
        } else {
            // Handle existing spawner with null entityType
            if (spawner.getEntityType() == null) {
                handleNullEntityType(spawner, block, player);
            }
        }

        // Handle spawn egg usage
        if (isSpawnEgg(itemInHand.getType())) {
            handleSpawnEggUse(player, cspawner, spawner, itemInHand);
            return;
        }

        // Handle spawner stacking
        if (itemInHand.getType() == Material.SPAWNER) {
            if (player.isSneaking()) {
                // Stack all spawners when sneaking
                boolean success = stackHandler.handleSpawnerStack(player, spawner, itemInHand, true);
                if (success) {
                    spawnerManager.saveSingleSpawner(spawner.getSpawnerId());
                    // Play stack sound
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    // Show particles
                    block.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                            block.getLocation().add(0.5, 0.5, 0.5),
                            10, 0.3, 0.3, 0.3, 0);
                }
            } else {
                // Stack single spawner when not sneaking
                if (stackHandler.handleSpawnerStack(player, spawner, itemInHand, false)) {
                    spawnerManager.saveSingleSpawner(spawner.getSpawnerId());
                }
            }
        } else {
            openSpawnerMenu(player, spawner, false);
        }
    }

    private void openSpawnerMenu(Player player, SpawnerData spawner, boolean refresh) {
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
        if (!refresh) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerMenuHolder)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        SpawnerMenuHolder holder = (SpawnerMenuHolder) event.getInventory().getHolder();
        SpawnerData spawner = holder.getSpawnerData();

        // Check if clicked in top inventory (menu)
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof SpawnerMenuHolder)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        switch (clickedItem.getType()) {
            case CHEST:
                handleChestClick(player, spawner);
                break;

            case PLAYER_HEAD, SPAWNER, ZOMBIE_HEAD, SKELETON_SKULL, WITHER_SKELETON_SKULL, CREEPER_HEAD, PIGLIN_HEAD:
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                handleSpawnerInfoClick(player, spawner);
                break;
            case EXPERIENCE_BOTTLE:
                handleExpBottleClick(player, spawner);
                break;
        }
    }

    // Helper method to create buttons
    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        button.setItemMeta(meta);
        return button;
    }

    private void handleSpawnerInfoClick(Player player, SpawnerData spawner) {
        // Create new inventory with 27 slots
        String title = languageManager.getMessage("gui-title.stacker-menu");
        Inventory gui = Bukkit.createInventory(new SpawnerStackerHolder(spawner), 27, title);

        // Create decrease buttons
        ItemStack decreaseBy64 = createButton(Material.RED_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.decrease-64"),
                Arrays.asList(languageManager.getMessage("button.lore.remove")
                        .replace("%amount%", "64")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        decreaseBy64.setAmount(64);

        ItemStack decreaseBy16 = createButton(Material.RED_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.decrease-16"),
                Arrays.asList(languageManager.getMessage("button.lore.remove")
                        .replace("%amount%", "16")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        decreaseBy16.setAmount(16);

        ItemStack decreaseBy1 = createButton(Material.RED_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.decrease-1"),
                Arrays.asList(languageManager.getMessage("button.lore.remove")
                        .replace("%amount%", "1")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        decreaseBy1.setAmount(1);

        // Create increase buttons
        ItemStack increaseBy64 = createButton(Material.LIME_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.increase-64"),
                Arrays.asList(languageManager.getMessage("button.lore.add")
                        .replace("%amount%", "64")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        increaseBy64.setAmount(64);

        ItemStack increaseBy16 = createButton(Material.LIME_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.increase-16"),
                Arrays.asList(languageManager.getMessage("button.lore.add")
                        .replace("%amount%", "16")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        increaseBy16.setAmount(16);

        ItemStack increaseBy1 = createButton(Material.LIME_STAINED_GLASS_PANE,
                languageManager.getMessage("button.name.increase-1"),
                Arrays.asList(languageManager.getMessage("button.lore.add")
                        .replace("%amount%", "1")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .split("\n")));
        increaseBy1.setAmount(1);

        ItemStack spawnerItem = createButton(Material.SPAWNER,
                languageManager.getMessage("button.name.spawner","%entity%", languageManager.getFormattedMobName(spawner.getEntityType())),
                Arrays.asList(languageManager.getMessage("button.lore.spawner")
                        .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                        .replace("%max_stack_size%", String.valueOf(configManager.getMaxStackSize()))
                        .replace("%entity%", languageManager.getFormattedMobName(spawner.getEntityType()))
                        .split("\n")));

        // Set items in inventory
        gui.setItem(9, decreaseBy64);
        gui.setItem(10, decreaseBy16);
        gui.setItem(11, decreaseBy1);
        gui.setItem(13, spawnerItem);
        gui.setItem(15, increaseBy1);
        gui.setItem(16, increaseBy16);
        gui.setItem(17, increaseBy64);

        player.openInventory(gui);
    }

    // Add new event handler for the stack control GUI
    @EventHandler
    public void onStackControlClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder)) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        SpawnerStackerHolder holder = (SpawnerStackerHolder) event.getInventory().getHolder();
        SpawnerData spawner = holder.getSpawnerData();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // Return to main menu if spawner is clicked
        if (clicked.getType() == Material.SPAWNER) {
            openSpawnerMenu(player, spawner, true);
            return;
        }

        int maxStackSize = configManager.getMaxStackSize();
        int currentSize = spawner.getStackSize();

        // Get the display name of clicked item to determine the change amount
        String displayName = clicked.getItemMeta().getDisplayName();
        if (displayName == null) return;

        // Determine the change amount based on the clicked item's name
        int change = getChangeAmount(displayName);
        if (change == 0) return;

        if (change > 0) {
            handleStackIncrease(player, spawner, currentSize, change, maxStackSize);
            handleSpawnerInfoClick(player, spawner);
        } else {
            handleStackDecrease(player, spawner, currentSize, change);
            handleSpawnerInfoClick(player, spawner);
        }
    }

    private void handleStackDecrease(Player player, SpawnerData spawner, int currentSize, int change) {
        int removeAmount = Math.abs(change);
        int targetSize = currentSize + change; // change is negative

        // Không thể giảm xuống dưới 1
        int actualChange;
        if (targetSize < 1) {
            actualChange = -(currentSize - 1);
            removeAmount = Math.abs(actualChange);
            languageManager.sendMessage(player, "messages.cannot-go-below-one", "%amount%", String.valueOf(removeAmount));
            return;
        } else {
            actualChange = change;
        }

        spawner.setStackSize(currentSize + actualChange, player);
        giveSpawnersWithMergeAndDrop(player, removeAmount, spawner.getEntityType());

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private void handleStackIncrease(Player player, SpawnerData spawner, int currentSize, int change, int maxStackSize) {
        // Tính toán số lượng thực sự cần thêm
        int spaceLeft = maxStackSize - currentSize;
        if (spaceLeft <= 0) {
            languageManager.sendMessage(player, "messages.stack-full");
            return;
        }

        // Lấy số spawner thực tế cần
        int actualChange = Math.min(change, spaceLeft);

        // Kiểm tra số lượng và type của spawner trong inventory
        int validSpawners = countValidSpawnersInInventory(player, spawner.getEntityType());

        if (validSpawners == 0 && hasDifferentSpawnerType(player, spawner.getEntityType())) {
            // Chỉ hiện thông báo different-type khi không có spawner cùng loại nào
            languageManager.sendMessage(player, "messages.different-type");
            return;
        }

        if (validSpawners < actualChange) {
            // Hiện thông báo không đủ spawner khi có ít nhất 1 spawner cùng loại
            languageManager.sendMessage(player, "messages.not-enough-spawners",
                    "%amountChange%", String.valueOf(actualChange),
                    "%amountAvailable%", String.valueOf(validSpawners));
            return;
        }

        // Chỉ remove đúng số spawner cần thiết và cùng loại
        removeValidSpawnersFromInventory(player, spawner.getEntityType(), actualChange);
        spawner.setStackSize(currentSize + actualChange, player);

        // Thông báo nếu không thể thêm hết
        if (actualChange < change) {
            languageManager.sendMessage(player, "messages.stack-full-overflow",
                    "%amount%", String.valueOf(actualChange));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private EntityType getSpawnerEntityType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return null;
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        CreatureSpawner spawner = (CreatureSpawner) blockMeta.getBlockState();
        EntityType spawnerEntity = spawner.getSpawnedType();

        // Support for stacking spawners with Spawner from EconomyShopGUI
        if (spawnerEntity == null) {
            String displayName = meta.getDisplayName();
            if (displayName.matches("§9§l[A-Za-z]+(?: [A-Za-z]+)? §rSpawner")) {
                String entityName = displayName
                        .replaceAll("§9§l", "")
                        .replaceAll(" §rSpawner", "")
                        .replace(" ", "_")
                        .toUpperCase();
                try {
                    spawnerEntity = EntityType.valueOf(entityName);
                } catch (IllegalArgumentException e) {
                    configManager.debug("Could not find entity type: " + entityName);
                }
            }
        }

        return spawnerEntity;
    }

    private boolean hasDifferentSpawnerType(Player player, EntityType requiredType) {
        for (ItemStack item : player.getInventory().getContents()) {
            EntityType spawnerEntity = getSpawnerEntityType(item);
            if (spawnerEntity != null && spawnerEntity != requiredType) {
                return true;
            }
        }
        return false;
    }

    private int countValidSpawnersInInventory(Player player, EntityType requiredType) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            EntityType spawnerEntity = getSpawnerEntityType(item);
            if (spawnerEntity == requiredType) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeValidSpawnersFromInventory(Player player, EntityType requiredType, int amountToRemove) {
        int remainingToRemove = amountToRemove;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remainingToRemove > 0; i++) {
            ItemStack item = contents[i];
            EntityType spawnerEntity = getSpawnerEntityType(item);

            if (spawnerEntity == requiredType) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remainingToRemove) {
                    player.getInventory().setItem(i, null);
                    remainingToRemove -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remainingToRemove);
                    remainingToRemove = 0;
                }
            }
        }
        player.updateInventory();
    }

    private ItemStack createSpawnerItem(EntityType entityType) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null) {
            if (entityType != null && entityType != EntityType.UNKNOWN) {
                // Set display name
                String entityTypeName = languageManager.getFormattedMobName(entityType);
                String displayName = languageManager.getMessage("spawner-name","%entity%",entityTypeName);
                meta.setDisplayName(displayName);

                // Store entity type in item NBT
                BlockStateMeta blockMeta = (BlockStateMeta) meta;
                CreatureSpawner cs = (CreatureSpawner) blockMeta.getBlockState();
                cs.setSpawnedType(entityType);
                blockMeta.setBlockState(cs);

                // Add lore
//                List<String> lore = new ArrayList<>();
//                lore.add(ChatColor.GRAY + "Entity: " + StringUtils.capitalize(entityName));
//                meta.setLore(lore);

            }
            spawner.setItemMeta(meta);
        }

        return spawner;
    }

    private synchronized void giveSpawnersWithMergeAndDrop(Player player, int amount, EntityType entityType) {
        ItemStack[] contents = player.getInventory().getContents();
        int remainingAmount = amount;

        // Phase 1: Merge với các stack chưa đầy trong inventory
        for (int i = 0; i < contents.length && remainingAmount > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.SPAWNER) {
                // Kiểm tra entity type của spawner trong inventory
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof BlockStateMeta) {
                    BlockStateMeta blockMeta = (BlockStateMeta) meta;
                    CreatureSpawner spawner = (CreatureSpawner) blockMeta.getBlockState();

                    // Chỉ merge với spawner cùng loại
                    if (spawner.getSpawnedType() == entityType) {
                        int currentAmount = item.getAmount();
                        if (currentAmount < 64) {
                            int canAdd = Math.min(64 - currentAmount, remainingAmount);
                            item.setAmount(currentAmount + canAdd);
                            remainingAmount -= canAdd;
                        }
                    }
                }
            }
        }

        // Phase 2: Tạo stack mới cho số còn lại và drop nếu không đủ chỗ
        if (remainingAmount > 0) {
            Location dropLoc = player.getLocation();
            while (remainingAmount > 0) {
                int stackSize = Math.min(64, remainingAmount);
                // Tạo spawner với đúng entity type
                ItemStack spawnerItem = createSpawnerItem(entityType);
                spawnerItem.setAmount(stackSize);

                // Thử thêm vào inventory trước
                Map<Integer, ItemStack> failed = player.getInventory().addItem(spawnerItem);

                // Nếu không thể thêm vào inventory, drop ra
                if (!failed.isEmpty()) {
                    failed.values().forEach(item ->
                            player.getWorld().dropItemNaturally(dropLoc, item)
                    );
                    languageManager.sendMessage(player, "messages.inventory-full-drop");
                }

                remainingAmount -= stackSize;
            }
        }

        // Cập nhật inventory
        player.updateInventory();
    }

    private int getChangeAmount(String displayName) {
        if (displayName.contains("-64")) return -64;
        else if (displayName.contains("-16")) return -16;
        else if (displayName.contains("-1 ")) return -1;
        else if (displayName.contains("+1 ")) return 1;
        else if (displayName.contains("+16")) return 16;
        else if (displayName.contains("+64")) return 64;
        return 0;
    }

    /**
     * Handle chest click to open spawner loot inventory
     */
    private void handleChestClick(Player player, SpawnerData spawner) {
        String title = languageManager.getGuiTitle("gui-title.loot-menu");
        SpawnerLootManager lootManager = new SpawnerLootManager(plugin);
        Inventory pageInventory = lootManager.createLootInventory(spawner, title, 1);

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        player.closeInventory();
        player.openInventory(pageInventory);
    }

    /**
     * Handle experience bottle click to give experience
     */
    private void handleExpBottleClick(Player player, SpawnerData spawner) {
        int exp = spawner.getSpawnerExp();

        if (exp <= 0) {
            languageManager.sendMessage(player, "messages.no-exp");
            return;
        }

        int remainingExp = exp;

        // Nếu allowExpMending = true, áp dụng exp cho các vật phẩm có enchant mending
        if (configManager.isAllowExpMending()) {
            PlayerInventory inventory = player.getInventory();
            ItemStack mainHand = inventory.getItemInMainHand();
            ItemStack offHand = inventory.getItemInOffHand();
            ItemStack armor = inventory.getChestplate();

            // Danh sách các item cần check
            List<ItemStack> itemsToCheck = Arrays.asList(mainHand, offHand, armor);

            for (ItemStack item : itemsToCheck) {
                if (item != null && !item.getType().equals(Material.AIR)) {
                    if (item.getEnchantments().containsKey(Enchantment.MENDING)) {
                        // Kiểm tra độ bền của item
                        if (item.getDurability() > 0) {
                            // Tính toán exp cần để sửa item
                            int durabilityToRepair = Math.min(item.getDurability(), remainingExp * 2);
                            int expNeeded = durabilityToRepair / 2;

                            if (expNeeded > 0 && remainingExp >= expNeeded) {
                                // Sửa item và trừ exp
                                item.setDurability((short) (item.getDurability() - durabilityToRepair));
                                remainingExp -= expNeeded;

                                // Hiệu ứng sửa chữa
                                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.0f);
                                player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 5);
                            }
                        }
                    }
                }
            }
        }

        // Cho player phần exp còn lại
        if (remainingExp > 0) {
            player.giveExp(remainingExp);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        spawner.setSpawnerExp(0);
        openSpawnerMenu(player, spawner, true);

        // Thông báo kết quả
        if (remainingExp < exp) {
            languageManager.sendMessage(player, "messages.exp-collected-with-mending",
                    "%exp-mending%", String.valueOf(exp-remainingExp), "%exp%", String.valueOf(remainingExp));
        } else {
            languageManager.sendMessage(player, "messages.exp-collected", "%exp%", String.valueOf(exp));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PagedSpawnerLootHolder)) return;

        PagedSpawnerLootHolder holder = (PagedSpawnerLootHolder) event.getInventory().getHolder();
        SpawnerData spawner = holder.getSpawnerData();

        // Save items from current page
        SpawnerLootManager lootManager = new SpawnerLootManager(plugin);
        lootManager.saveItems(spawner, event.getInventory());

        // Save to file
        spawnerManager.saveSpawnerData();
    }

    /**
     * Get entity type from spawn egg material
     */
    private EntityType getEntityTypeFromSpawnEgg(Material material) {
        String entityName = material.name().replace("_SPAWN_EGG", "");
        try {
            return EntityType.valueOf(entityName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isSpawnEgg(Material material) {
        return material.name().endsWith("_SPAWN_EGG");
    }

    private boolean isBedrockPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api.isFloodgatePlayer(uuid);
        } catch (NoClassDefFoundError | NullPointerException e) {
            return false;
        }
    }

    public void handleSpawnEggUse(Player player, CreatureSpawner spawner, SpawnerData spawnerData, ItemStack spawnEgg) {

        if (!player.hasPermission("smartspawner.changetype")) {
            languageManager.sendMessage(player, "no-permission");
            return;
        }
        EntityType newType = getEntityTypeFromSpawnEgg(spawnEgg.getType());

        if (newType != null && spawnerData != null) {
            spawnerData.setEntityType(newType);
            spawner.setSpawnedType(newType);
            spawner.update();
            spawnerManager.saveSingleSpawner(spawnerData.getSpawnerId());

            languageManager.sendMessage(player, "messages.changed",
                    "%type%", languageManager.getFormattedMobName(newType));
        } else {
            languageManager.sendMessage(player, "messages.invalid-egg");
        }

        if (player.getGameMode() == GameMode.SURVIVAL) {
            spawnEgg.setAmount(spawnEgg.getAmount() - 1);
        }
    }
}