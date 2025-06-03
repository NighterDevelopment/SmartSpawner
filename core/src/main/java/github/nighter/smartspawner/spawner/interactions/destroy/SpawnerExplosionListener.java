package github.nighter.smartspawner.spawner.interactions.destroy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerExplodeEvent;
import github.nighter.smartspawner.extras.HopperHandler;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.utils.SpawnerFileHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;

import java.util.ArrayList;
import java.util.List;

public class SpawnerExplosionListener implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerFileHandler spawnerFileHandler;
    private final HopperHandler hopperHandler;

    public SpawnerExplosionListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerFileHandler = plugin.getSpawnerFileHandler();
        this.hopperHandler = plugin.getHopperHandler();
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent event) {
        // Handle respawn anchor explosions and other block explosions
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blockList) {
        List<Block> blocksToRemove = new ArrayList<>();

        for (Block block : blockList) {
            if (block.getType() == Material.SPAWNER) {
                SpawnerData spawnerData = this.spawnerManager.getSpawnerByLocation(block.getLocation());

                if (spawnerData != null) {
                    SpawnerExplodeEvent e = null;
                    if (plugin.getConfig().getBoolean("spawner_properties.default.protect_from_explosions", true)) {
                        // Protect spawner from explosion
                        blocksToRemove.add(block);
                        plugin.getSpawnerGuiViewManager().closeAllViewersInventory(spawnerData);
                        cleanupAssociatedHopper(block);
                        if (SpawnerExplodeEvent.getHandlerList().getRegisteredListeners().length != 0) {
                            e = new SpawnerExplodeEvent(null, spawnerData.getSpawnerLocation(), 1, false);
                        }
                    } else {
                        // Allow spawner to be destroyed
                        spawnerData.setSpawnerStop(true);
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
                    // If no spawner data is found, allow the spawner block to be destroyed
                    // So don't add it to the blocksToRemove list
                }
            } else if (block.getType() == Material.RESPAWN_ANCHOR) {
                // Handle respawn anchor explosion protection using existing config
                if (plugin.getConfig().getBoolean("spawner_properties.default.protect_from_explosions", true)) {
                    // Check if there are any spawners nearby that should be protected
                    if (hasProtectedSpawnersNearby(block)) {
                        blocksToRemove.add(block);
                    }
                }
            }
        }

        // Remove the protected blocks from the explosion list
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
                            return true; // Found a protected spawner nearby
                        }
                    }
                }
            }
        }
        return false;
    }

    private void cleanupAssociatedHopper(Block block) {
        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (blockBelow.getType() == Material.HOPPER && hopperHandler != null) {
            hopperHandler.stopHopperTask(blockBelow.getLocation());
        }
    }
}