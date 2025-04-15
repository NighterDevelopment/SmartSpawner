package github.nighter.smartspawner.spawner.interactions.place;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerPlaceEvent;
import github.nighter.smartspawner.extras.HopperHandler;
import github.nighter.smartspawner.nms.ParticleWrapper;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.config.ConfigManager;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * Handles spawner placement events, including entity type inheritance
 * and activation behavior.
 */
public class SpawnerPlaceListener implements Listener {
    private static final double PARTICLE_OFFSET = 0.5;

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerManager spawnerManager;
    private final HopperHandler hopperHandler;

    /**
     * Creates a new spawner placement handler with the given plugin instance.
     *
     * @param plugin The main plugin instance
     */
    public SpawnerPlaceListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.hopperHandler = plugin.getHopperHandler();
    }

    /**
     * Handles spawner placement events, managing entity type inheritance
     * and activation behavior.
     *
     * @param event The block place event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        Block block = event.getBlock();

        if (block.getType() != Material.SPAWNER) {
            return;
        }

        // Validate spawner item
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            event.setCancelled(true);
            languageManager.sendMessage(player, "messages.invalid-spawner-item");
            return;
        }

        // Extract stored entity type from item
        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        EntityType storedEntityType = null;

        if (blockMeta.hasBlockState() && blockMeta.getBlockState() instanceof CreatureSpawner) {
            CreatureSpawner storedState = (CreatureSpawner) blockMeta.getBlockState();
            storedEntityType = storedState.getSpawnedType();
        }

        SpawnerPlaceEvent e = new SpawnerPlaceEvent(player, block.getLocation(), 1);
        Bukkit.getPluginManager().callEvent(e);
        //TODO: Check if this can be optimized moving it
        if(e.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        // Handle spawner initialization asynchronously
        initializeSpawner(block, player, storedEntityType);

        // Set up hopper integration if enabled
        setupHopperIntegration(block);

        // Log placement
        logDebugInfo("Player " + player.getName() + " placed " +
                (storedEntityType != null ? storedEntityType : "unknown") +
                " spawner at " + block.getLocation());
    }

    /**
     * Initializes a newly placed spawner, handling entity type and activation
     *
     * @param block The spawner block
     * @param player The player who placed the spawner
     * @param storedEntityType The entity type from the item, or null
     */
    private void initializeSpawner(Block block, Player player, EntityType storedEntityType) {
        // We need to use location-based scheduling to avoid the "Cannot read world asynchronously" error
        Scheduler.runLocationTaskLater(block.getLocation(), () -> {
            BlockState blockState = block.getState();
            if (!(blockState instanceof CreatureSpawner)) {
                return;
            }

            CreatureSpawner spawner = (CreatureSpawner) blockState;
            EntityType entityType = getEntityType(storedEntityType, spawner);

            // Ensure entity type is explicitly set
            spawner.setSpawnedType(entityType);
            spawner.update(true, false); // Force update

            // Activate spawner if configured
            if (configManager.getBoolean("activate-on-place")) {
                createSmartSpawner(block, player, entityType);
            } else {
                // Send confirmation message
                languageManager.sendMessage(player, "messages.entity-spawner-placed");

                // Double-check entity type after a short delay
                Scheduler.runLocationTaskLater(block.getLocation(), () -> {
                    BlockState recheckedState = block.getState();
                    if (recheckedState instanceof CreatureSpawner) {
                        CreatureSpawner recheckedSpawner = (CreatureSpawner) recheckedState;
                        if (recheckedSpawner.getSpawnedType() != entityType) {
                            recheckedSpawner.setSpawnedType(entityType);
                            recheckedSpawner.update(true, false);
                            configManager.debug("Fixed entity type on spawner at " + block.getLocation());
                        }
                    }
                }, 3L);

                // Create spawner data but don't activate
                String spawnerId = UUID.randomUUID().toString().substring(0, 8);

                // Ensure the block state is properly updated
                BlockState state = block.getState();
                if (state instanceof CreatureSpawner) {
                    CreatureSpawner cspawner = (CreatureSpawner) state;
                    cspawner.setSpawnedType(entityType);
                    cspawner.update(true, false);
                }

                // Create and configure new spawner
                SpawnerData cspawner = new SpawnerData(spawnerId, block.getLocation(), entityType, plugin);
                cspawner.setSpawnerActive(false);

                // Register with manager
                spawnerManager.addSpawner(spawnerId, cspawner);

                // Save spawner data
                spawnerManager.queueSpawnerForSaving(spawnerId);
            }
        }, 2L);
    }

    /**
     * Determines the correct entity type for a spawner
     *
     * @param storedEntityType The entity type stored in the item, if any
     * @param placedSpawner The creature spawner that was placed
     * @return The determined entity type
     */
    private EntityType getEntityType(EntityType storedEntityType, CreatureSpawner placedSpawner) {
        EntityType entityType = storedEntityType;

        // Determine entity type
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            entityType = placedSpawner.getSpawnedType();

            // Apply default type if needed
            if (entityType == null || entityType == EntityType.UNKNOWN) {
                entityType = configManager.getDefaultEntityType();
            }

            // Make sure we explicitly set and update
            placedSpawner.setSpawnedType(entityType);
            placedSpawner.update(true, false);

            // Log the entity type we're setting
            configManager.debug("Setting spawner entity type to: " + entityType);
        }

        return entityType;
    }

    /**
     * Creates a smart spawner instance for the placed block
     *
     * @param block The spawner block
     * @param player The player who placed the spawner
     * @param entityType The entity type for the spawner
     */
    private void createSmartSpawner(Block block, Player player, EntityType entityType) {
        String spawnerId = UUID.randomUUID().toString().substring(0, 8);

        // Ensure the block state is properly updated
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner) {
            CreatureSpawner spawner = (CreatureSpawner) state;
            spawner.setSpawnedType(entityType);
            spawner.update(true, false);
        }

        // Create and configure new spawner
        SpawnerData spawner = new SpawnerData(spawnerId, block.getLocation(), entityType, plugin);
        spawner.setSpawnerActive(true);

        // Register with manager
        spawnerManager.addSpawner(spawnerId, spawner);

        // Save spawner data
        spawnerManager.queueSpawnerForSaving(spawnerId);

        // Visual effect if enabled
        if (configManager.getBoolean("particles-spawner-activate")) {
            showCreationParticles(block);
        }

        languageManager.sendMessage(player, "messages.activated");
        logDebugInfo("Created new spawner with ID: " + spawnerId + " at " + block.getLocation() +
                " with entity type: " + entityType);
    }

    /**
     * Shows visual particles for spawner creation
     *
     * @param block The spawner block
     */
    private void showCreationParticles(Block block) {
        // Use location-based scheduling to ensure particles are shown in the right region
        Scheduler.runLocationTask(block.getLocation(), () -> {
            Location particleLocation = block.getLocation().clone().add(
                    PARTICLE_OFFSET, PARTICLE_OFFSET, PARTICLE_OFFSET);
            block.getWorld().spawnParticle(
                    ParticleWrapper.SPELL_WITCH,
                    particleLocation,
                    50, PARTICLE_OFFSET, PARTICLE_OFFSET, PARTICLE_OFFSET, 0
            );
        });
    }

    /**
     * Sets up hopper integration for a newly placed spawner
     *
     * @param block The spawner block
     */
    private void setupHopperIntegration(Block block) {
        if (configManager.getBoolean("hopper-enabled")) {
            // Run this task in the block's region
            Scheduler.runLocationTask(block.getLocation(), () -> {
                Block blockBelow = block.getRelative(BlockFace.DOWN);
                if (blockBelow.getType() == Material.HOPPER && hopperHandler != null) {
                    hopperHandler.startHopperTask(blockBelow.getLocation(), block.getLocation());
                }
            });
        }
    }

    /**
     * Logs debug information if debug mode is enabled
     *
     * @param message The debug message
     */
    private void logDebugInfo(String message) {
        configManager.debug(message);
    }
}