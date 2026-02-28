package github.nighter.smartspawner.spawner.interactions.destroy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerExplodeEvent;
import github.nighter.smartspawner.extras.HopperService;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.data.SpawnerFileHandler;
import github.nighter.smartspawner.utils.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class SpawnerExplosionListener implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerFileHandler spawnerFileHandler;
    private final HopperService hopperService;

    public SpawnerExplosionListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerFileHandler = plugin.getSpawnerFileHandler();
        this.hopperService = plugin.getHopperService();
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
                        spawnerData.getSpawnerStop().set(true);
                        String spawnerId = spawnerData.getSpawnerId();
                        cleanupAssociatedHopper(block);
                        if (SpawnerExplodeEvent.getHandlerList().getRegisteredListeners().length != 0) {
                            e = new SpawnerExplodeEvent(null, spawnerData.getSpawnerLocation(), 1, true);
                        }
                        spawnerManager.removeSpawner(spawnerId);
                        spawnerManager.markSpawnerDeleted(spawnerId);
                    }
                    if (e != null) {
                        Bukkit.getPluginManager().callEvent(e);
                    }
                } else {
                    // Allow vanilla spawners to be destroyed
                    if (plugin.getConfig().getBoolean("natural_spawner.protect_from_explosions", false)) {
                        blocksToRemove.add(block);
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

    // TODO: deduplicate
    public void cleanupAssociatedHopper(Block block) {
        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (plugin.getHopperConfig().isHopperEnabled() && blockBelow.getType() == Material.HOPPER) {
            hopperService.getRegistry().remove(new BlockPos(blockBelow.getLocation()));
        }
    }
}
