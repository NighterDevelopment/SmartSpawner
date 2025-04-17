package github.nighter.smartspawner.spawner.gui.stacker;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerRemoveEvent;
import github.nighter.smartspawner.api.events.SpawnerStackEvent;
import github.nighter.smartspawner.holders.SpawnerStackerHolder;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.config.ConfigManager;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpawnerStackerHandler implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;

    // Sound constants
    private static final Sound STACK_SOUND = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private static final Sound CLICK_SOUND = Sound.UI_BUTTON_CLICK;
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 1.0f;

    // GUI slots & operation constants
    private static final Pattern SPAWNER_NAME_PATTERN = Pattern.compile("§9§l([A-Za-z]+(?: [A-Za-z]+)?) §rSpawner");
    private static final int[] DECREASE_SLOTS = {9, 10, 11};
    private static final int[] INCREASE_SLOTS = {17, 16, 15};
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int[] STACK_AMOUNTS = {64, 10, 1};

    // Player interaction tracking
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private final Map<UUID, Scheduler.Task> pendingUpdates = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> activeViewers = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> updateLocks = new ConcurrentHashMap<>();

    // Click cooldown in milliseconds
    private static final long CLICK_COOLDOWN = 200L;

    // Batch update delay in ticks
    private static final long UPDATE_DELAY = 2L;

    public SpawnerStackerHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();

        // Start cleanup task
        startCleanupTask();
    }

    private void startCleanupTask() {
        Scheduler.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            lastClickTime.entrySet().removeIf(entry -> now - entry.getValue() > 5000);
            updateLocks.entrySet().removeIf(entry -> !lastClickTime.containsKey(entry.getKey()));
        }, 100L, 100L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder holder)) return;

        // Cancel the event to prevent item movement
        event.setCancelled(true);

        // Check for cooldown
        if (isOnCooldown(player.getUniqueId())) return;

        // Get the clicked item
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        SpawnerData spawner = holder.getSpawnerData();

        // Handle navigation back to main menu if spawner is clicked
        if (clickedItem.getType() == Material.SPAWNER) {
            navigateToMainMenu(player, spawner);
            return;
        }

        // Process stack modification
        int slotIndex = event.getRawSlot();
        int changeAmount = determineChangeAmount(slotIndex);

        if (changeAmount != 0) {
            // Mark click time and attempt modification
            markClick(player.getUniqueId());
            processStackModification(player, spawner, changeAmount);

            // Schedule a single batch update for all viewers
            scheduleViewersUpdate(spawner, player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Prevent any dragging in the stacker GUI
        if (event.getInventory().getHolder() instanceof SpawnerStackerHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder holder)) return;

        // Track player as viewer of this spawner
        String spawnerId = holder.getSpawnerData().getSpawnerId();
        activeViewers.computeIfAbsent(spawnerId, k -> ConcurrentHashMap.newKeySet())
                .add(player.getUniqueId());

        // Initialize lock for this player
        updateLocks.putIfAbsent(player.getUniqueId(), new AtomicBoolean(false));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder holder)) return;

        String spawnerId = holder.getSpawnerData().getSpawnerId();

        // Verify the player is really closing the GUI (not just inventory updates)
        Scheduler.runTaskLater(() -> {
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (!(topInventory.getHolder() instanceof SpawnerStackerHolder)) {
                // Remove viewer and cancel any pending updates
                removeViewer(spawnerId, player.getUniqueId());
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        cleanupPlayer(playerId);
    }

    // ---- Helper Methods ----

    private void cleanupPlayer(UUID playerId) {
        // Cancel any pending tasks
        Scheduler.Task task = pendingUpdates.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        // Remove player from tracking
        lastClickTime.remove(playerId);
        updateLocks.remove(playerId);

        // Remove from all viewer sets
        activeViewers.values().forEach(viewers -> viewers.remove(playerId));
        activeViewers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void cleanupAll() {
        pendingUpdates.values().forEach(task -> {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        });
        pendingUpdates.clear();
        lastClickTime.clear();
        updateLocks.clear();
        activeViewers.clear();
    }

    private void removeViewer(String spawnerId, UUID playerId) {
        Set<UUID> viewers = activeViewers.get(spawnerId);
        if (viewers != null) {
            viewers.remove(playerId);
            if (viewers.isEmpty()) {
                activeViewers.remove(spawnerId);
            }
        }
        cleanupPlayer(playerId);
    }

    private boolean isOnCooldown(UUID playerId) {
        Long lastClick = lastClickTime.get(playerId);
        if (lastClick == null) return false;

        return System.currentTimeMillis() - lastClick < CLICK_COOLDOWN;
    }

    private void markClick(UUID playerId) {
        lastClickTime.put(playerId, System.currentTimeMillis());
    }

    private int determineChangeAmount(int slotIndex) {
        // Check decrease buttons
        for (int i = 0; i < DECREASE_SLOTS.length; i++) {
            if (slotIndex == DECREASE_SLOTS[i]) {
                return -STACK_AMOUNTS[i];
            }
        }

        // Check increase buttons
        for (int i = 0; i < INCREASE_SLOTS.length; i++) {
            if (slotIndex == INCREASE_SLOTS[i]) {
                return STACK_AMOUNTS[i];
            }
        }

        return 0; // Not a modification button
    }

    private void navigateToMainMenu(Player player, SpawnerData spawner) {
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
        player.playSound(player.getLocation(), CLICK_SOUND, SOUND_VOLUME, SOUND_PITCH);
    }

    private void processStackModification(Player player, SpawnerData spawner, int changeAmount) {
        if (changeAmount < 0) {
            // Handle stack decrease
            handleStackDecrease(player, spawner, changeAmount);
        } else {
            // Handle stack increase
            handleStackIncrease(player, spawner, changeAmount);
        }
    }

    private void handleStackDecrease(Player player, SpawnerData spawner, int changeAmount) {
        int currentSize = spawner.getStackSize();
        int removeAmount = Math.abs(changeAmount);
        int targetSize = Math.max(1, currentSize - removeAmount);
        int actualChange = currentSize - targetSize;

        // Stop if no change
        if (actualChange <= 0) {
            languageManager.sendMessage(player, "messages.cannot-go-below-one",
                    "%amount%", String.valueOf(currentSize - 1));
            return;
        }

        SpawnerRemoveEvent e = new SpawnerRemoveEvent(player, spawner.getSpawnerLocation(), spawner.getStackSize() - changeAmount, changeAmount);
        Bukkit.getPluginManager().callEvent(e);
        if(e.isCancelled()) return;
        // Update stack size and give spawners to player
        spawner.setStackSize(targetSize, player);
        giveSpawnersToPlayer(player, actualChange, spawner.getEntityType());

        // Play sound
        player.playSound(player.getLocation(), STACK_SOUND, SOUND_VOLUME, SOUND_PITCH);
    }

    private void handleStackIncrease(Player player, SpawnerData spawner, int changeAmount) {
        int currentSize = spawner.getStackSize();
        int maxStackSize = configManager.getInt("max-stack-size");

        // Calculate how many more can be added
        int spaceLeft = maxStackSize - currentSize;
        if (spaceLeft <= 0) {
            languageManager.sendMessage(player, "messages.stack-full");
            return;
        }

        // Limit change to available space
        int actualChange = Math.min(changeAmount, spaceLeft);

        // Check player inventory for spawners
        int availableSpawners = countValidSpawners(player, spawner.getEntityType());

        // Handle different spawner types
        if (availableSpawners == 0 && hasDifferentSpawnerType(player, spawner.getEntityType())) {
            languageManager.sendMessage(player, "messages.different-type");
            return;
        }

        // Check if player has enough spawners
        if (availableSpawners < actualChange) {
            languageManager.sendMessage(player, "messages.not-enough-spawners",
                    "%amountChange%", String.valueOf(actualChange),
                    "%amountAvailable%", String.valueOf(availableSpawners));
            return;
        }

        // Remove from inventory and update stack
        SpawnerStackEvent e = new SpawnerStackEvent(player, spawner.getSpawnerLocation(), spawner.getStackSize(), spawner.getStackSize() + actualChange, SpawnerStackEvent.StackSource.GUI);
        Bukkit.getPluginManager().callEvent(e);
        if(e.isCancelled()) return;
        removeValidSpawnersFromInventory(player, spawner.getEntityType(), actualChange);
        spawner.setStackSize(currentSize + actualChange, player);

        // Notify if max stack reached
        if (actualChange < changeAmount) {
            languageManager.sendMessage(player, "messages.stack-full-overflow",
                    "%amount%", String.valueOf(actualChange));
        }

        // Play sound
        player.playSound(player.getLocation(), STACK_SOUND, SOUND_VOLUME, SOUND_PITCH);
    }

    private void scheduleViewersUpdate(SpawnerData spawner, Player initiator) {
        String spawnerId = spawner.getSpawnerId();
        Set<UUID> viewers = activeViewers.getOrDefault(spawnerId, ConcurrentHashMap.newKeySet());

        // Only schedule update if viewers exist
        if (viewers.isEmpty()) return;

        // Schedule single update task for all viewers
        Scheduler.runTaskLater(() -> {
            for (UUID viewerId : viewers) {
                Player viewer = plugin.getServer().getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()) {
                    // Try to get update lock for this player
                    AtomicBoolean lock = updateLocks.computeIfAbsent(viewerId, k -> new AtomicBoolean(false));

                    // Only update if not currently updating
                    if (lock.compareAndSet(false, true)) {
                        try {
                            updateGui(viewer, spawner);
                        } finally {
                            // Always release lock
                            lock.set(false);
                        }
                    }
                }
            }
        }, UPDATE_DELAY);
    }

    private void updateGui(Player player, SpawnerData spawner) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv.getHolder() instanceof SpawnerStackerHolder) {
            // Update spawner info in slot 13
            updateInfoItem(inv, spawner);

            // Update decrease buttons
            for (int i = 0; i < DECREASE_SLOTS.length; i++) {
                updateDecreaseButton(inv, spawner, STACK_AMOUNTS[i], DECREASE_SLOTS[i]);
            }

            // Update increase buttons
            for (int i = 0; i < INCREASE_SLOTS.length; i++) {
                updateIncreaseButton(inv, spawner, STACK_AMOUNTS[i], INCREASE_SLOTS[i]);
            }

            // Force client refresh
            player.updateInventory();
        }
    }

    private void updateInfoItem(Inventory inventory, SpawnerData spawner) {
        ItemStack infoItem = inventory.getItem(SPAWNER_INFO_SLOT);
        if (infoItem == null || !infoItem.hasItemMeta()) return;

        ItemMeta meta = infoItem.getItemMeta();
        String[] lore = languageManager.getMessage("button.lore.spawner")
                .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                .replace("%max_stack_size%", String.valueOf(configManager.getInt("max-stack-size")))
                .replace("%entity%", languageManager.getFormattedMobName(spawner.getEntityType()))
                .split("\n");

        meta.setLore(Arrays.asList(lore));
        infoItem.setItemMeta(meta);
    }

    private void updateDecreaseButton(Inventory inventory, SpawnerData spawner, int amount, int slot) {
        ItemStack button = inventory.getItem(slot);
        if (button == null || !button.hasItemMeta()) return;

        ItemMeta meta = button.getItemMeta();
        String[] lore = languageManager.getMessage("button.lore.remove")
                .replace("%amount%", String.valueOf(amount))
                .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                .split("\n");

        meta.setLore(Arrays.asList(lore));
        button.setItemMeta(meta);
    }

    private void updateIncreaseButton(Inventory inventory, SpawnerData spawner, int amount, int slot) {
        ItemStack button = inventory.getItem(slot);
        if (button == null || !button.hasItemMeta()) return;

        ItemMeta meta = button.getItemMeta();
        String[] lore = languageManager.getMessage("button.lore.add")
                .replace("%amount%", String.valueOf(amount))
                .replace("%stack_size%", String.valueOf(spawner.getStackSize()))
                .split("\n");

        meta.setLore(Arrays.asList(lore));
        button.setItemMeta(meta);
    }

    // The following methods would need to be implemented based on your existing code
    private int countValidSpawners(Player player, EntityType entityType) {
        // Implementation based on your existing countValidSpawnersInInventory method
        // Count spawners of the required type in player inventory
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.SPAWNER) {
                // Check if it's the right entity type
                if (isCorrectEntityType(item, entityType)) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }

    private boolean hasDifferentSpawnerType(Player player, EntityType requiredType) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.SPAWNER) {
                Optional<EntityType> typeOptional = getSpawnerEntityType(item);
                if (typeOptional.isPresent() && typeOptional.get() != requiredType) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<EntityType> getSpawnerEntityType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return Optional.empty();
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta)) {
            return Optional.empty();
        }

        CreatureSpawner spawner = (CreatureSpawner) blockMeta.getBlockState();
        EntityType spawnerEntity = spawner.getSpawnedType();

        // Support for stacking spawners with Spawner from EconomyShopGUI
        if (spawnerEntity == null) {
            String displayName = meta.getDisplayName();
            Matcher matcher = SPAWNER_NAME_PATTERN.matcher(displayName);

            if (matcher.matches()) {
                String entityName = matcher.group(1)
                        .replace(" ", "_")
                        .toUpperCase();
                try {
                    return Optional.of(EntityType.valueOf(entityName));
                } catch (IllegalArgumentException e) {
                    configManager.debug("Could not find entity type: " + entityName);
                }
            }
        }

        return Optional.ofNullable(spawnerEntity);
    }


    private boolean isCorrectEntityType(ItemStack item, EntityType requiredType) {
        // Helper method to check if a spawner is of the required type
        Optional<EntityType> typeOptional = getSpawnerEntityType(item);
        return typeOptional.isPresent() && typeOptional.get() == requiredType;
    }

    private void removeValidSpawnersFromInventory(Player player, EntityType requiredType, int amountToRemove) {
        int remainingToRemove = amountToRemove;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remainingToRemove > 0; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            Optional<EntityType> spawnerType = getSpawnerEntityType(item);
            if (spawnerType.isPresent() && spawnerType.get() == requiredType) {
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

    private synchronized void giveSpawnersToPlayer(Player player, int amount, EntityType entityType) {
        final int MAX_STACK_SIZE = 64;
        ItemStack[] contents = player.getInventory().getContents();
        int remainingAmount = amount;

        // First pass: Try to merge with existing stacks
        for (int i = 0; i < contents.length && remainingAmount > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.SPAWNER) continue;

            Optional<EntityType> itemEntityType = getSpawnerEntityType(item);
            if (itemEntityType.isEmpty() || itemEntityType.get() != entityType) continue;

            int currentAmount = item.getAmount();
            if (currentAmount < MAX_STACK_SIZE) {
                int canAdd = Math.min(MAX_STACK_SIZE - currentAmount, remainingAmount);
                item.setAmount(currentAmount + canAdd);
                remainingAmount -= canAdd;
            }
        }

        // Second pass: Create new stacks for remaining items
        if (remainingAmount > 0) {
            while (remainingAmount > 0) {
                int stackSize = Math.min(MAX_STACK_SIZE, remainingAmount);
                ItemStack spawnerItem = createSpawnerItem(entityType);
                spawnerItem.setAmount(stackSize);

                // Try to add to inventory first
                Map<Integer, ItemStack> failedItems = player.getInventory().addItem(spawnerItem);

                // Drop any items that couldn't fit
                if (!failedItems.isEmpty()) {
                    failedItems.values().forEach(item ->
                            player.getWorld().dropItemNaturally(player.getLocation(), item)
                    );
                    languageManager.sendMessage(player, "messages.inventory-full-drop");
                }

                remainingAmount -= stackSize;
            }
        }

        // Update inventory
        player.updateInventory();
    }

    private ItemStack createSpawnerItem(EntityType entityType) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            // Set display name
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            String displayName = languageManager.getMessage("spawner-name", "%entity%", entityTypeName);
            meta.setDisplayName(displayName);

            // Store entity type in item NBT
            BlockStateMeta blockMeta = (BlockStateMeta) meta;
            CreatureSpawner cs = (CreatureSpawner) blockMeta.getBlockState();
            cs.setSpawnedType(entityType);
            blockMeta.setBlockState(cs);

            spawner.setItemMeta(meta);
        }

        return spawner;
    }

    public void closeAllViewersInventory(String spawnerId) {
        Set<UUID> viewers = activeViewers.getOrDefault(spawnerId, Collections.emptySet());

        // Create a copy to avoid concurrent modification
        Set<UUID> viewersCopy = new HashSet<>(viewers);

        // Close inventory for each player
        for (UUID viewerId : viewersCopy) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                viewer.closeInventory();
            }
        }
    }
}