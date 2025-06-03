package github.nighter.smartspawner;

import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.server.PluginEnableEvent;

public class GlobalEventHandlers implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private static final int CHECK_RADIUS = 4; // Radius to check around the chicken

    public GlobalEventHandlers(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
    }

    // Prevent spawner from spawning mobs
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(SpawnerSpawnEvent event){
        if (event.getSpawner() == null) return;
        SpawnerData spawner = spawnerManager.getSpawnerByLocation(event.getSpawner().getLocation());
        if (spawner != null && spawner.getSpawnerActive()) {
            event.setCancelled(true);
        }
    }

    // Prevent chickens from zombie chicken jockeys
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        // Only check for chickens for maximum performance
        if (entity.getType() != EntityType.CHICKEN) {
            return;
        }

        // Get spawn reason if this is a CreatureSpawnEvent
        CreatureSpawnEvent.SpawnReason spawnReason = null;
        if (event instanceof CreatureSpawnEvent) {
            spawnReason = ((CreatureSpawnEvent) event).getSpawnReason();
        }

        // Allow chickens spawned from breeding
        if (spawnReason == CreatureSpawnEvent.SpawnReason.BREEDING) {
            return;
        }

        if (spawnReason == CreatureSpawnEvent.SpawnReason.EGG) {
            return;
        }

        if (spawnReason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }

        // Check if there's a zombie spawner nearby
        if (isNearZombieSpawner(entity.getLocation())) {
            // Cancel chicken spawn if near zombie spawner (unless from breeding)
            event.setCancelled(true);
            plugin.debug("Prevented chicken spawn near zombie spawner at " + entity.getLocation());
        }
    }

    // Check if there's a zombie spawner nearby
    private boolean isNearZombieSpawner(Location location) {
        // Check in a cube area around the location
        for (int x = -CHECK_RADIUS; x <= CHECK_RADIUS; x++) {
            for (int y = -CHECK_RADIUS; y <= CHECK_RADIUS; y++) {
                for (int z = -CHECK_RADIUS; z <= CHECK_RADIUS; z++) {
                    Location checkLoc = new Location(
                            location.getWorld(),
                            location.getBlockX() + x,
                            location.getBlockY() + y,
                            location.getBlockZ() + z
                    );

                    SpawnerData spawner = spawnerManager.getSpawnerByLocation(checkLoc);
                    if (spawner != null && (spawner.getEntityType() == EntityType.ZOMBIE || spawner.getEntityType() == EntityType.ZOMBIFIED_PIGLIN)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {

        if (plugin.getShopIntegrationManager().hasShopIntegration()) {
            return;
        }
        String pluginName = event.getPlugin().getName();
        // Support EconomyShopGUI for double integration
        if (pluginName.equalsIgnoreCase("EconomyShopGUI") ||
                pluginName.equalsIgnoreCase("EconomyShopGUI-Premium")) {
            Scheduler.runTaskLater(() -> plugin.getShopIntegrationManager().reload(),
                    20L); // Run after 1 second to ensure the plugin is fully loaded
        }
    }
}