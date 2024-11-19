package me.nighter.smartSpawner.managers;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.utils.SpawnerData;
import org.bukkit.*;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class SpawnerStackHandler {
    private final SmartSpawner plugin;
    private ConfigManager configManager;
    private LanguageManager languageManager;

    public SpawnerStackHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    public boolean handleSpawnerStack(Player player, SpawnerData targetSpawner, ItemStack itemInHand, boolean stackAll) {
        if (itemInHand.getType() != Material.SPAWNER) {
            return false;
        }

        if (!player.hasPermission("smartspawner.stack")) {
            languageManager.sendMessage(player, "no-permission");
            return false;
        }

        // Check spawner type from item in hand
        ItemMeta meta = itemInHand.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            languageManager.sendMessage(player, "messages.invalid-spawner");
            return false;
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        CreatureSpawner handSpawner = (CreatureSpawner) blockMeta.getBlockState();
        EntityType handEntityType = handSpawner.getSpawnedType();

        // Get target spawner's entity type
        EntityType targetEntityType = targetSpawner.getEntityType();

        // Support for stacking spawners with Spawner from EconomyShopGUI
        if (handEntityType == null) {
            String displayName = meta.getDisplayName();
            if (displayName.matches("§9§l[A-Za-z]+(?: [A-Za-z]+)? §rSpawner")) {
                String entityName = displayName
                        .replaceAll("§9§l", "")
                        .replaceAll(" §rSpawner", "")
                        .replace(" ", "_")
                        .toUpperCase();
                handEntityType = EntityType.valueOf(entityName.toUpperCase());
            }
        }

        // Check if two spawners are the same type
        if (handEntityType != targetEntityType) {
            languageManager.sendMessage(player, "messages.different-type");
            return false;
        }

        int maxStackSize = configManager.getMaxStackSize();
        int currentStack = targetSpawner.getStackSize();
        if (currentStack >= maxStackSize) {
            languageManager.sendMessage(player, "messages.stack-full");
            return false;
        }

        int itemAmount = itemInHand.getAmount();
        int spaceLeft = maxStackSize - currentStack;

        int amountToStack;
        if (stackAll) {
            amountToStack = Math.min(spaceLeft, itemAmount);
        } else {
            amountToStack = 1;
        }

        int newStack = currentStack + amountToStack;
        targetSpawner.setStackSize(newStack);

        if (player.getGameMode() != GameMode.CREATIVE) {
            if (itemAmount <= amountToStack) {
                player.getInventory().setItemInMainHand(null);
            } else {
                itemInHand.setAmount(itemAmount - amountToStack);
            }
        }

        showStackAnimation(targetSpawner, newStack, player);
        return true;
    }

    private void showStackAnimation(SpawnerData spawner, int newStack, Player player) {
        Location loc = spawner.getSpawnerLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.VILLAGER_HAPPY,
                loc.clone().add(0.5, 0.5, 0.5),
                10, 0.3, 0.3, 0.3, 0);

        languageManager.sendMessage(player, "messages.hand-stack", "%amount%", String.valueOf(newStack));
    }
}
