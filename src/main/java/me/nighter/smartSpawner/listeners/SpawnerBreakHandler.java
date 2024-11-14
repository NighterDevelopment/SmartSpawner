package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.managers.SpawnerManager;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import org.bukkit.*;
import org.bukkit.block.Block;
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

    public SpawnerBreakHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerManager = plugin.getSpawnerManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER) {
            return;
        }

        final Player player = event.getPlayer();
        final Block block = event.getBlock();

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
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // Skip if item doesn't have meta
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return;
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;

        // Create a new Spawner when placed by a player
        String newSpawnerId = UUID.randomUUID().toString().substring(0, 8);

        // Get the stored spawner state
        CreatureSpawner storedState = (CreatureSpawner) blockMeta.getBlockState();
        EntityType entityType = storedState.getSpawnedType();
        Block placedBlock = event.getBlock();
        CreatureSpawner placedSpawner = (CreatureSpawner) placedBlock.getState();

        if (entityType == null || entityType == EntityType.UNKNOWN) {
            entityType = configManager.getDefaultEntityType();
        }

        placedSpawner.setSpawnedType(entityType);
        placedSpawner.update();
        Location location = placedBlock.getLocation();
        location.getWorld().spawnParticle(
                Particle.WITCH,
                location.clone().add(0.5, 0.5, 0.5),
                50, 0.5, 0.5, 0.5, 0
        );

        // Create new spawner
        SpawnerData spawner = new SpawnerData(newSpawnerId, location, entityType, plugin);
        spawner.setSpawnerActive(true);

        spawnerManager.addSpawner(newSpawnerId, spawner);
        spawnerManager.saveSpawnerData();

        languageManager.sendMessage(player, "messages.activated");
        // Debug message
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
