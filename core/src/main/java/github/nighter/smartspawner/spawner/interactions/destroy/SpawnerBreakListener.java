package github.nighter.smartspawner.spawner.interactions.destroy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerPlayerBreakEvent;
import github.nighter.smartspawner.extras.HopperHandler;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.hooks.protections.CheckBreakBlock;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.utils.SpawnerFileHandler;
import github.nighter.smartspawner.spawner.limits.ChunkSpawnerLimiter;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class SpawnerBreakListener implements Listener {
    private static final int MAX_STACK_SIZE = 64;

    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final HopperHandler hopperHandler;
    private final SpawnerItemFactory spawnerItemFactory;
    private final SpawnerFileHandler spawnerFileHandler;
    private ChunkSpawnerLimiter chunkSpawnerLimiter;

    public SpawnerBreakListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.hopperHandler = plugin.getHopperHandler();
        this.spawnerItemFactory = plugin.getSpawnerItemFactory();
        this.spawnerFileHandler = plugin.getSpawnerFileHandler();
        this.chunkSpawnerLimiter = plugin.getChunkSpawnerLimiter();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final Location location = block.getLocation();

        if (block.getType() != Material.SPAWNER) {
            return;
        }

        if (!CheckBreakBlock.CanPlayerBreakBlock(player, location)) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getConfig().getBoolean("spawner_break.enabled", true)) {
            event.setCancelled(true);
            return;
        }

        final SpawnerData spawner = spawnerManager.getSpawnerByLocation(location);

        if (!plugin.getConfig().getBoolean("natural_spawner.breakable", false)) {
            if (spawner == null) {
                block.setType(Material.AIR);
                event.setCancelled(true);
                messageService.sendMessage(player, "natural_spawner_break_blocked");
                return;
            }
        }

        if (!player.hasPermission("smartspawner.break")) {
            event.setCancelled(true);
            messageService.sendMessage(player, "spawner_break_no_permission");
            return;
        }

        if (spawner != null) {
            handleSmartSpawnerBreak(block, spawner, player);
            plugin.getRangeChecker().stopSpawnerTask(spawner);
        } else {
            CreatureSpawner creatureSpawner = (CreatureSpawner) block.getState();
            if(callAPIEvent(player, block.getLocation(), 1)) {
                event.setCancelled(true);
                return;
            }
            handleVanillaSpawnerBreak(block, creatureSpawner, player);
        }

        event.setCancelled(true);
        cleanupAssociatedHopper(block);
    }

    private void handleSmartSpawnerBreak(Block block, SpawnerData spawner, Player player) {
        Location location = block.getLocation();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!validateBreakConditions(player, tool, spawner)) {
            return;
        }

        plugin.getSpawnerGuiViewManager().closeAllViewersInventory(spawner);

        SpawnerBreakResult result = processDrops(player, location, spawner, player.isSneaking(), block);

        if (result.isSuccess()) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                reduceDurability(tool, player, result.getDurabilityLoss());
            }
        }
    }

    private void handleVanillaSpawnerBreak(Block block, CreatureSpawner creatureSpawner, Player player) {
        Location location = block.getLocation();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!validateBreakConditions(player, tool, null)) {
            return;
        }

        EntityType entityType = creatureSpawner.getSpawnedType();
        ItemStack spawnerItem;
        if (plugin.getConfig().getBoolean("natural_spawner.convert_to_smart_spawner", false)) {
            spawnerItem = spawnerItemFactory.createSpawnerItem(entityType);
        } else {
            spawnerItem = spawnerItemFactory.createVanillaSpawnerItem(entityType);
        }

        boolean directToInventory = plugin.getConfig().getBoolean("spawner_break.direct_to_inventory", false);

        World world = location.getWorld();
        if (world != null) {
            block.setType(Material.AIR);

            // Unregister vanilla spawner from chunk limiter (stack size 1)
            chunkSpawnerLimiter.unregisterSpawner(location, 1);

            if (directToInventory) {
                giveSpawnersToPlayer(player, 1, spawnerItem);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            } else if (plugin.getConfig().getBoolean("natural_spawner.drop_spawner", true)) {
                world.dropItemNaturally(location.toCenterLocation(), spawnerItem);
            }

            reduceDurability(tool, player, plugin.getConfig().getInt("spawner_break.durability_loss", 1));
        }
    }

    private boolean validateBreakConditions(Player player, ItemStack tool, SpawnerData spawner) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        if (!player.hasPermission("smartspawner.break")) {
            messageService.sendMessage(player, "spawner_break_no_permission");
            return false;
        }

        if (!isValidTool(tool)) {
            messageService.sendMessage(player, "spawner_break_required_tools");
            return false;
        }

        if (plugin.getConfig().getBoolean("spawner_break.silk_touch.required", true)) {
            int requiredLevel = plugin.getConfig().getInt("spawner_break.silk_touch.level", 1);
            if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) < requiredLevel) {
                messageService.sendMessage(player, "spawner_break_silk_touch_required");
                return false;
            }
        }

        return true;
    }

    private SpawnerBreakResult processDrops(Player player, Location location, SpawnerData spawner, boolean isCrouching, Block spawnerBlock) {
        final int currentStackSize = spawner.getStackSize();
        final int durabilityLoss = plugin.getConfig().getInt("spawner_break.durability_loss", 1);

        World world = location.getWorld();
        if (world == null) {
            return new SpawnerBreakResult(false, 0, durabilityLoss);
        }

        EntityType entityType = spawner.getEntityType();
        ItemStack template = spawnerItemFactory.createSpawnerItem(entityType);

        int dropAmount;
        boolean shouldDeleteSpawner = false;

        if (isCrouching) {
            if (currentStackSize <= MAX_STACK_SIZE) {
                dropAmount = currentStackSize;
                if(callAPIEvent(player, location, dropAmount)) return new SpawnerBreakResult(false, dropAmount, 0);
                // Unregister entire spawner stack
                chunkSpawnerLimiter.unregisterSpawner(location, currentStackSize);
            } else {
                dropAmount = MAX_STACK_SIZE;
                if(callAPIEvent(player, location, dropAmount)) return new SpawnerBreakResult(false, dropAmount, 0);
                // Unregister only the dropped amount
                chunkSpawnerLimiter.unregisterSpawner(location, MAX_STACK_SIZE);
                spawner.setStackSize(currentStackSize - MAX_STACK_SIZE);
            }
        } else {
            dropAmount = 1;
            if(callAPIEvent(player, location, dropAmount)) return new SpawnerBreakResult(false, dropAmount, 0);
            // Unregister only 1 spawner
            chunkSpawnerLimiter.unregisterSpawner(location, 1);
            if (currentStackSize <= 1) {
                shouldDeleteSpawner = true;
            } else {
                spawner.setStackSize(currentStackSize - 1);
            }
        }

        if (dropAmount == currentStackSize || shouldDeleteSpawner) {
            cleanupSpawner(spawnerBlock, spawner);
        } else {
            spawnerManager.markSpawnerModified(spawner.getSpawnerId());
        }

        boolean directToInventory = plugin.getConfig().getBoolean("spawner_break.direct_to_inventory", false);

        if (directToInventory) {
            giveSpawnersToPlayer(player, dropAmount, template);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        } else {
            template.setAmount(dropAmount);
            world.dropItemNaturally(location.toCenterLocation(), template.clone());
        }

        return new SpawnerBreakResult(true, dropAmount, durabilityLoss);
    }

    private boolean callAPIEvent(Player player, Location location, int dropAmount) {
        if(SpawnerPlayerBreakEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerPlayerBreakEvent e = new SpawnerPlayerBreakEvent(player, location, dropAmount);
            Bukkit.getPluginManager().callEvent(e);
            return e.isCancelled();
        }
        return false;
    }

    private void reduceDurability(ItemStack tool, Player player, int durabilityLoss) {
        if (tool.getType().getMaxDurability() == 0) {
            return;
        }

        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            int currentDurability = damageable.getDamage();
            int newDurability = currentDurability + durabilityLoss;

            if (newDurability >= tool.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                damageable.setDamage(newDurability);
                tool.setItemMeta(meta);
            }
        }
    }

    private void cleanupSpawner(Block block, SpawnerData spawner) {
        spawner.setSpawnerStop(true);
        block.setType(Material.AIR);

        String spawnerId = spawner.getSpawnerId();

        spawnerManager.removeSpawner(spawnerId);
        spawnerFileHandler.markSpawnerDeleted(spawnerId);
    }

    private void cleanupAssociatedHopper(Block block) {
        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (blockBelow.getType() == Material.HOPPER && hopperHandler != null) {
            hopperHandler.stopHopperTask(blockBelow.getLocation());
        }
    }

    private boolean isValidTool(ItemStack tool) {
        if (tool == null) {
            return false;
        }
        return plugin.getConfig().getStringList("spawner_break.required_tools").contains(tool.getType().name());
    }

    private void giveSpawnersToPlayer(Player player, int amount, ItemStack template) {
        final int MAX_STACK_SIZE = 64;

        ItemStack itemToGive = template.clone();
        itemToGive.setAmount(Math.min(amount, MAX_STACK_SIZE));

        Map<Integer, ItemStack> failedItems = player.getInventory().addItem(itemToGive);

        if (!failedItems.isEmpty()) {
            for (ItemStack failedItem : failedItems.values()) {
                player.getWorld().dropItemNaturally(player.getLocation().toCenterLocation(), failedItem);
            }
            messageService.sendMessage(player, "inventory_full_items_dropped");
        }

        player.updateInventory();
    }

    private static class SpawnerBreakResult {
        @Getter private final boolean success;
        private final int droppedAmount;
        private final int baseDurabilityLoss;

        public SpawnerBreakResult(boolean success, int droppedAmount, int baseDurabilityLoss) {
            this.success = success;
            this.droppedAmount = droppedAmount;
            this.baseDurabilityLoss = baseDurabilityLoss;
        }

        public int getDurabilityLoss() {
            return droppedAmount * baseDurabilityLoss;
        }
    }

    @EventHandler
    public void onSpawnerDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() != Material.SPAWNER) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType() == Material.AIR) {
            return;
        }

        SpawnerData spawner = spawnerManager.getSpawnerByLocation(block.getLocation());
        if (spawner != null) {
            messageService.sendMessage(player, "spawner_break_warning");
        }

        if (isValidTool(tool)) {
            if (plugin.getConfig().getBoolean("spawner_break.silk_touch.required", true)) {
                int requiredLevel = plugin.getConfig().getInt("spawner_break.silk_touch.level", 1);
                if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) < requiredLevel) {
                    messageService.sendMessage(player, "spawner_break_silk_touch_required");
                    return;
                }
            }

            if (!player.hasPermission("smartspawner.break")) {
                messageService.sendMessage(player, "spawner_break_no_permission");
            }

        } else {
            messageService.sendMessage(player, "spawner_break_required_tools");
        }
    }
}
