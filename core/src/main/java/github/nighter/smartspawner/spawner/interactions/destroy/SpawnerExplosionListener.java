package github.nighter.smartspawner.spawner.interactions.destroy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerExplodeEvent;
import github.nighter.smartspawner.extras.HopperHandler;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerFileHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnerExplosionListener implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerFileHandler spawnerFileHandler;
    private final SpawnerItemFactory spawnerItemFactory;
    private final HopperHandler hopperHandler;

    public SpawnerExplosionListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerFileHandler = plugin.getSpawnerFileHandler();
        this.spawnerItemFactory = plugin.getSpawnerItemFactory();
        this.hopperHandler = plugin.getHopperHandler();
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        handleExplosion(event, event.blockList());
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent event) {
        handleExplosion(null, event.blockList());
    }

    private void handleExplosion(EntityExplodeEvent event, List<Block> blockList) {
        List<Block> blocksToRemove = new ArrayList<>();

        for (Block block : blockList) {
            if (block.getType() == Material.SPAWNER) {
                SpawnerData spawnerData = this.spawnerManager.getSpawnerByLocation(block.getLocation());

                if (spawnerData != null) {
                    boolean isWindCharge = event != null && (event.getEntity().getType() == EntityType.BREEZE_WIND_CHARGE || event.getEntity().getType() == EntityType.WIND_CHARGE);
                    boolean protect = plugin.getConfig().getBoolean("spawner_properties.default.protect_from_explosions", true) || isWindCharge;
                    SpawnerExplodeEvent e = null;
                    if (protect) {
                        blocksToRemove.add(block);
                        plugin.getSpawnerGuiViewManager().closeAllViewersInventory(spawnerData);
                        cleanupAssociatedHopper(block);
                        if (SpawnerExplodeEvent.getHandlerList().getRegisteredListeners().length != 0) {
                            e = new SpawnerExplodeEvent(null, spawnerData.getSpawnerLocation(), 1, false);
                        }
                    } else {
                        double explosionDropChance = plugin.getConfig().getDouble("spawner_properties.default.explosion_drop_chance", 0.0);
                        if (passesChanceCheck(explosionDropChance)) {
                            dropSmartSpawnerItem(block, spawnerData);
                        }
                        spawnerData.getSpawnerStop().set(true);
                        String spawnerId = spawnerData.getSpawnerId();
                        cleanupAssociatedHopper(block);
                        if (SpawnerExplodeEvent.getHandlerList().getRegisteredListeners().length != 0) {
                            e = new SpawnerExplodeEvent(null, spawnerData.getSpawnerLocation(), 1, true);
                        }
                        spawnerManager.removeSpawner(spawnerId);
                        spawnerFileHandler.markSpawnerDeleted(spawnerId);
                    }
                    if (e != null) {
                        Bukkit.getPluginManager().callEvent(e);
                    }
                } else {
                    if (plugin.getConfig().getBoolean("natural_spawner.protect_from_explosions", false)) {
                        blocksToRemove.add(block);
                    } else {
                        double naturalDropChance = plugin.getConfig().getDouble("natural_spawner.explosion_drop_chance", 0.0);
                        if (passesChanceCheck(naturalDropChance)) {
                            dropVanillaSpawnerItem(block);
                        }
                    }
                }
            } else if (block.getType() == Material.RESPAWN_ANCHOR) {
                if (plugin.getConfig().getBoolean("spawner_properties.default.protect_from_explosions", true)) {
                    if (hasProtectedSpawnersNearby(block)) {
                        blocksToRemove.add(block);
                    }
                }
            }
        }

        blockList.removeAll(blocksToRemove);
    }

    private boolean hasProtectedSpawnersNearby(Block anchorBlock) {
        if (!plugin.getConfig().getBoolean("spawner_properties.default.protect_from_explosions", true)) {
            return false;
        }

        int protectionRadius = 8;

        for (int x = -protectionRadius; x <= protectionRadius; x++) {
            for (int y = -protectionRadius; y <= protectionRadius; y++) {
                for (int z = -protectionRadius; z <= protectionRadius; z++) {
                    Block nearbyBlock = anchorBlock.getRelative(x, y, z);
                    if (nearbyBlock.getType() == Material.SPAWNER) {
                        SpawnerData spawnerData = spawnerManager.getSpawnerByLocation(nearbyBlock.getLocation());
                        if (spawnerData != null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean passesChanceCheck(double chance) {
        if (chance >= 100.0) return true;
        if (chance <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    private void dropSmartSpawnerItem(Block block, SpawnerData spawnerData) {
        Location location = block.getLocation();
        World world = location.getWorld();
        if (world == null) return;

        ItemStack spawnerItem;
        if (spawnerData.isItemSpawner()) {
            spawnerItem = spawnerItemFactory.createItemSpawnerItem(spawnerData.getSpawnedItemMaterial());
        } else {
            spawnerItem = spawnerItemFactory.createSmartSpawnerItem(spawnerData.getEntityType());
        }
        world.dropItemNaturally(location.toCenterLocation(), spawnerItem);
    }

    private void dropVanillaSpawnerItem(Block block) {
        Location location = block.getLocation();
        World world = location.getWorld();
        if (world == null) return;

        if (block.getState(false) instanceof CreatureSpawner creatureSpawner) {
            EntityType entityType = creatureSpawner.getSpawnedType();
            ItemStack spawnerItem;
            if (plugin.getConfig().getBoolean("natural_spawner.convert_to_smart_spawner", false)) {
                spawnerItem = spawnerItemFactory.createSmartSpawnerItem(entityType);
            } else {
                spawnerItem = spawnerItemFactory.createVanillaSpawnerItem(entityType);
            }
            world.dropItemNaturally(location.toCenterLocation(), spawnerItem);
        }
    }

    private void cleanupAssociatedHopper(Block block) {
        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (blockBelow.getType() == Material.HOPPER && hopperHandler != null) {
            hopperHandler.stopHopperTask(blockBelow.getLocation());
        }
    }
}
