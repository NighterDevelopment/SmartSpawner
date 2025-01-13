package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.WorldGuardAPI;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.managers.SpawnerManager;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class SpawnerBreakHandler implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerManager spawnerManager;
    private final HopperHandler hopperHandler;

    public SpawnerBreakHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.hopperHandler = plugin.getHopperHandler();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerBreak(BlockBreakEvent event) {

        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final Location location = event.getBlock().getLocation();

        if (event.getBlock().getType() != Material.SPAWNER) {
                return;
        }

        // Check WorldGuard restrictions
        if (SmartSpawner.hasWorldGuard)
            if (!WorldGuardAPI.canPlayerBreakBlockInRegion(player.getUniqueId(), location)) {
                event.setCancelled(true);
                return;
        }

        // Cache config values
        final boolean isEnabled = configManager.isSpawnerBreakEnabled();
        if (!isEnabled) {
            event.setCancelled(true);
            return;
        }

        // Permission check with early return
        if (!player.hasPermission("smartspawner.break")) {
            event.setCancelled(true);
            languageManager.sendMessage(player, "no-permission");
            return;
        }

        // Get spawner data with location caching
        final Location loc = block.getLocation();
        final SpawnerData spawner = spawnerManager.getSpawnerByLocation(loc);
        final CreatureSpawner cs = (CreatureSpawner) block.getState();

        if (spawner == null) {
            handleCSpawnerBreak(block, cs, player);
            event.setCancelled(true);
        } else {
            handleSpawnerBreak(block, spawner, player);
            event.setCancelled(true);
        }

        Block blockBelow = event.getBlock().getRelative(BlockFace.DOWN);
        if (blockBelow.getType() == Material.HOPPER) {
            if (hopperHandler != null) {
                hopperHandler.stopHopperTask(blockBelow.getLocation());
            }
        }
    }

    private void handleSpawnerBreak(Block block, SpawnerData spawner, Player player) {
        Location location = block.getLocation();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!validateBreakConditions(player, tool, location)) {
            return;
        }
        // Handle drops with batch processing
        SpawnerBreakResult result = processDrops(location, spawner);

        if (result.isSuccess()) {
            // Handle tool durability
            if (player.getGameMode() != GameMode.CREATIVE) {
                reduceDurability(tool, player, result.getDurabilityLoss());
            }
            if (spawner.getStackSize() <= 0) {
                cleanupSpawner(block, spawner, player);
            }
        }
    }

    // For compatibility with CreatureSpawner
    private void handleCSpawnerBreak(Block block, CreatureSpawner cs, Player player) {
        Location location = block.getLocation();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!validateBreakConditions(player, tool, location)) {
            return;
        }

        // Get the entity type from the spawner
        EntityType entityType = cs.getSpawnedType();
        ItemStack item;

        if (entityType == null || entityType == EntityType.UNKNOWN) {
            // Create empty spawner if no entity type is set
            item = new ItemStack(Material.SPAWNER);
        } else {
            // Create spawner with stored entity type
            item = createSpawnerItem(entityType);
        }

        World world = location.getWorld();
        block.setType(Material.AIR);
        world.dropItemNaturally(location, item);
        reduceDurability(tool, player, configManager.getDurabilityLossPerSpawner());
        configManager.debug("Player " + player.getName() + " broke " +
                (entityType != null ? entityType + " spawner" : "empty spawner") +
                " at " + location);
    }

    private boolean validateBreakConditions(Player player, ItemStack tool, Location location) {
        // Skip validation for creative mode players
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        if (!player.hasPermission("smartspawner.break")) {
            languageManager.sendMessage(player, "no-permission");
            return false;
        }

        if(!isValidTool(tool)) {
            return false;
        }

        // Silk touch validation
        if (configManager.isSilkTouchRequired()) {
            int requiredLevel = configManager.getSilkTouchLevel();
            if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) < requiredLevel) {
                languageManager.sendMessage(player, "messages.silk-touch-required");
                return false;
            }
        }

        return true;
    }

    private SpawnerBreakResult processDrops(Location location, SpawnerData spawner) {
        final int currentStackSize = spawner.getStackSize();
        final int dropAmount = Math.min(configManager.getDropStackAmount(), currentStackSize);
        final int durabilityLoss = configManager.getDurabilityLossPerSpawner();

        if (dropAmount <= 0) return new SpawnerBreakResult(false, 0, durabilityLoss);

        // Batch process drops
        World world = location.getWorld();
        if (world == null) return new SpawnerBreakResult(false, 0, durabilityLoss);

        // Create template item once
//        ItemStack template = new ItemStack(Material.SPAWNER);
        EntityType entityType = spawner.getEntityType();
        ItemStack template = createSpawnerItem(entityType);

        // Handle drops based on stack size
        if (currentStackSize == 1) {
            // Special case: When stack size is 1, just drop the item
            dropItems(world, location, template, 1);
            spawner.decreaseStackSizeByOne();
        } else if (dropAmount == currentStackSize) {
            // If we're dropping all stacks except one
            dropItems(world, location, template, dropAmount - 1);
            // Set the last stack
            spawner.setStackSize(1);
            // Drop the final stack
            dropItems(world, location, template, 1);
            spawner.decreaseStackSizeByOne();
        } else {
            // Normal case: dropping partial stack
            dropItems(world, location, template, dropAmount);
            int remainingStack = currentStackSize - dropAmount;
            spawner.setStackSize(remainingStack);
        }

        return new SpawnerBreakResult(true, dropAmount, durabilityLoss);
    }


    private void dropItems(World world, Location location, ItemStack template, int amount) {
        if (amount <= 5) {
            // For small amounts, process normally
            for (int i = 0; i < amount; i++) {
                world.dropItemNaturally(location, template.clone());
            }
        } else {
            // For larger amounts, use batch processing
            List<Item> drops = new ArrayList<>(amount);
            Random random = new Random();

            for (int i = 0; i < amount; i++) {
                double spreadX = random.nextDouble() * 0.5 - 0.25;
                double spreadZ = random.nextDouble() * 0.5 - 0.25;

                Location dropLoc = location.clone().add(spreadX, 0.5, spreadZ);
                Item item = world.dropItem(dropLoc, template.clone());

                item.setVelocity(new Vector(
                        random.nextDouble() * 0.2 - 0.1,
                        0.2,
                        random.nextDouble() * 0.2 - 0.1
                ));

                drops.add(item);
            }

            drops.forEach(item -> item.setPickupDelay(10));
        }
    }

    private void reduceDurability(ItemStack tool, Player player, int droppedAmount) {
        if (tool.getType().getMaxDurability() == 0) return;

        int durabilityLoss = configManager.getDurabilityLossPerSpawner() * droppedAmount;
        ItemMeta meta = tool.getItemMeta();

        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            int currentDurability = damageable.getDamage();
            int newDurability = currentDurability + durabilityLoss;

            if (newDurability >= tool.getType().getMaxDurability()) {
                // Tool breaks
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                damageable.setDamage(newDurability);
                tool.setItemMeta(meta);
            }
        }
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {

        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();
        Location location = event.getBlock().getLocation();
    
        // Check WorldGuard restrictions
        if (SmartSpawner.hasWorldGuard)
            if (!WorldGuardAPI.canPlayerBreakBlockInRegion(player.getUniqueId(), location)) {
                event.setCancelled(true);
                return;
        }

        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        // Check if the item has BlockStateMeta
        if (!(meta instanceof BlockStateMeta)) {
            // Handle non-spawner items or log an error
            event.setCancelled(true);
            languageManager.sendMessage(player, "messages.invalid-spawner-item");
            return;
        }

        // Get entity type from item meta
        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        CreatureSpawner storedState = (CreatureSpawner) blockMeta.getBlockState();
        EntityType storedEntityType = storedState.getSpawnedType();

        // Get entity type from nbt tag
        if (storedEntityType == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Added checks to ensure that the BlockState is an instance of CreatureSpawner before casting.
                BlockState blockState = block.getState();
                if (blockState instanceof CreatureSpawner) {
                    CreatureSpawner placedSpawner = (CreatureSpawner) blockState;
                    EntityType placedEntityType = placedSpawner.getSpawnedType();

                // Handle default entity type
                if ((placedEntityType == null || placedEntityType == EntityType.UNKNOWN)) {
                    placedEntityType = configManager.getDefaultEntityType();
                    placedSpawner.setSpawnedType(placedEntityType);
                    placedSpawner.update();
                }

                // Handle spawner activation
                if (configManager.getActivateOnPlace()) {
                    createNewSpawnerWithType(block, player, placedEntityType);
                } else {
                    EntityType finalPlacedEntityType = placedEntityType;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        placedSpawner.setSpawnedType(finalPlacedEntityType);
                        placedSpawner.update();
                        languageManager.sendMessage(player, "messages.entity-spawner-placed");
                        }, 2L);
                    }
                }
            }, 2L);
        } else {
            // Handle spawner activation with item meta entity type
            if (configManager.getActivateOnPlace()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    //Added checks to ensure that the BlockState is an instance of CreatureSpawner before casting.
                    BlockState blockState = block.getState();
                    if (blockState instanceof CreatureSpawner) {
                        CreatureSpawner placedSpawner = (CreatureSpawner) blockState;
                        placedSpawner.setSpawnedType(storedEntityType);
                        placedSpawner.update();
                        createNewSpawnerWithType(block, player, storedEntityType);
                    }
                }, 2L);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    //Added checks to ensure that the BlockState is an instance of CreatureSpawner before casting.
                    BlockState blockState = block.getState();
                    if (blockState instanceof CreatureSpawner) {
                        CreatureSpawner placedSpawner = (CreatureSpawner) blockState;
                        placedSpawner.setSpawnedType(storedEntityType);
                        placedSpawner.update();
                        languageManager.sendMessage(player, "messages.entity-spawner-placed");
                    }
                }, 2L);
            }
        }

        // Check for hopper below and start hopper task
        if (configManager.isHopperEnabled()) {
            Block blockBelow = block.getRelative(BlockFace.DOWN);
            if (blockBelow.getType() == Material.HOPPER) {
                if (hopperHandler != null) {
                    hopperHandler.startHopperTask(blockBelow.getLocation(), block.getLocation());
                }
            }
        }

        // Debug message
            configManager.debug("Player " + player.getName() + " placed " + storedEntityType + " spawner at " + block.getLocation());
        }

    private void createNewSpawnerWithType(Block block, Player player, EntityType entityType) {
        String newSpawnerId = UUID.randomUUID().toString().substring(0, 8);

        // Create new spawner with specific entity type
        SpawnerData spawner = new SpawnerData(newSpawnerId, block.getLocation(), entityType, plugin);
        spawner.setSpawnerActive(true);

        // Add to manager and save
        spawnerManager.addSpawner(newSpawnerId, spawner);
        spawnerManager.saveSpawnerData();

        // Visual effect
        block.getWorld().spawnParticle(
                Particle.SPELL_WITCH,
                block.getLocation().clone().add(0.5, 0.5, 0.5),
                50, 0.5, 0.5, 0.5, 0
        );

        languageManager.sendMessage(player, "messages.activated");
        configManager.debug("Created new spawner with ID: " + newSpawnerId + " at " + block.getLocation());
    }

    private void cleanupSpawner(Block block, SpawnerData spawner, Player player) {
        spawner.setSpawnerStop(true);
        block.setType(Material.AIR);
        spawnerManager.removeSpawner(spawner.getSpawnerId());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            spawnerManager.saveSpawnerData();
            configManager.debug("Player " + player.getName() +
                    " broke spawner with ID: " + spawner.getSpawnerId());
        });
    }

    private boolean isValidTool(ItemStack tool) {
        if (tool == null) return false;
        return configManager.getRequiredTools().contains(tool.getType().name());
    }

    private static class SpawnerBreakResult {
        private final boolean success;
        private final int droppedAmount;
        private final int durabilityLoss;

        public SpawnerBreakResult(boolean success, int droppedAmount, int durabilityLoss) {
            this.success = success;
            this.droppedAmount = droppedAmount;
            this.durabilityLoss = durabilityLoss;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getDroppedAmount() {
            return droppedAmount;
        }

        public int getDurabilityLoss() {
            return droppedAmount * durabilityLoss;
        }
    }

    @EventHandler
    public void onSpawnerDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Kiểm tra xem block có phải spawner không
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        // Bỏ qua nếu người chơi trong chế độ Creative
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();

        // Kiểm tra tool có rỗng không
        if (tool.getType() == Material.AIR) {
            return;
        }

        // Kiểm tra xem có phải tool phá được spawner không
        if (isValidTool(tool)) {
            // Kiểm tra thêm về Silk Touch nếu cần
            if (configManager.isSilkTouchRequired()) {
                int requiredLevel = configManager.getSilkTouchLevel();
                if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) < requiredLevel) {
                    languageManager.sendMessage(player, "messages.silk-touch-required");
                }
            }

            // Kiểm tra permission
            if (!player.hasPermission("smartspawner.break")) {
                languageManager.sendMessage(player, "no-permission");
            }

            // Gửi thông báo cảnh báo khi bắt đầu phá
            languageManager.sendMessage(player, "messages.break-warning");
        } else {
            // Nếu tool không hợp lệ, thông báo tool cần thiết
            languageManager.sendMessage(player, "messages.required-tools");
        }
    }
}