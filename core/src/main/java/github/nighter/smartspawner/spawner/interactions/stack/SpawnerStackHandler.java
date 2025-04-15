package github.nighter.smartspawner.spawner.interactions.stack;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerStackEvent;
import github.nighter.smartspawner.hooks.protections.CheckStackBlock;
import github.nighter.smartspawner.nms.ParticleWrapper;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.config.ConfigManager;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpawnerStackHandler {
    private static final Pattern ECONOMY_SHOP_GUI_PATTERN = Pattern.compile("§9§l([A-Za-z]+(?: [A-Za-z]+)?) §rSpawner");
    private static final long STACK_COOLDOWN = 250L; // 250ms cooldown between stacks

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final Map<UUID, Long> lastStackTime;
    private final Map<Location, UUID> stackLocks;

    public SpawnerStackHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.lastStackTime = new ConcurrentHashMap<>();
        this.stackLocks = new ConcurrentHashMap<>();

        // Start cleanup task
        startCleanupTask();
    }

    private void startCleanupTask() {
        Scheduler.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            // Clear entries older than 10 seconds
            lastStackTime.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
            // Remove locks for offline players
            stackLocks.entrySet().removeIf(entry -> plugin.getServer().getPlayer(entry.getValue()) == null);
        }, 200L, 200L); // Run every 10 seconds
    }

    public void handleSpawnerStacking(Player player, Block block, SpawnerData spawnerData, ItemStack itemInHand) {
        // Anti-spam check
        if (isOnCooldown(player)) {
            return;
        }

        // Try to acquire lock
        if (!acquireStackLock(player, block.getLocation())) {
            return;
        }

        try {
            handleSpawnerStack(player, spawnerData, itemInHand, player.isSneaking());
        } finally {
            releaseStackLock(block.getLocation());
            updateLastStackTime(player);
        }
    }

    private boolean isOnCooldown(Player player) {
        long lastTime = lastStackTime.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() - lastTime < STACK_COOLDOWN;
    }

    private void updateLastStackTime(Player player) {
        lastStackTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean acquireStackLock(Player player, Location location) {
        return stackLocks.putIfAbsent(location, player.getUniqueId()) == null;
    }

    private void releaseStackLock(Location location) {
        stackLocks.remove(location);
    }

    public boolean handleSpawnerStack(Player player, SpawnerData targetSpawner, ItemStack itemInHand, boolean stackAll) {
        // Basic validation checks
        if (itemInHand.getType() != Material.SPAWNER) {
            return false;
        }

        Location location = targetSpawner.getSpawnerLocation();
        if (!hasStackPermissions(player, location)) {
            return false;
        }

        // Always check the entity type directly without caching
        Optional<EntityType> handEntityTypeOpt = getEntityTypeFromItem(itemInHand);
        if (!handEntityTypeOpt.isPresent()) {
            languageManager.sendMessage(player, "messages.invalid-spawner");
            return false;
        }

        EntityType handEntityType = handEntityTypeOpt.get();
        EntityType targetEntityType = targetSpawner.getEntityType();

        // Verify types match
        if (handEntityType != targetEntityType) {
            languageManager.sendMessage(player, "messages.different-type");
            return false;
        }

        // Verify stack limits
        int maxStackSize = configManager.getInt("max-stack-size");
        int currentStack = targetSpawner.getStackSize();

        if (currentStack >= maxStackSize) {
            languageManager.sendMessage(player, "messages.stack-full");
            return false;
        }

        return processStackAddition(player, targetSpawner, itemInHand, stackAll, currentStack, maxStackSize);
    }

    private boolean hasStackPermissions(Player player, Location location) {
        if (!CheckStackBlock.CanPlayerPlaceBlock(player.getUniqueId(), location)) {
            languageManager.sendMessage(player, "messages.spawner-protected");
            return false;
        }

        if (!player.hasPermission("smartspawner.stack")) {
            languageManager.sendMessage(player, "no-permission");
            return false;
        }

        return true;
    }

    private Optional<EntityType> getEntityTypeFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return Optional.empty();
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        CreatureSpawner handSpawner = (CreatureSpawner) blockMeta.getBlockState();
        EntityType entityType = handSpawner.getSpawnedType();

        if (entityType != null) {
            return Optional.of(entityType);
        }

        // Fallback to checking display name formats if metadata is missing
        if (!meta.hasDisplayName()) {
            return Optional.empty();
        }

        String displayName = meta.getDisplayName();

        // Try EconomyShopGUI format
        Matcher matcher = ECONOMY_SHOP_GUI_PATTERN.matcher(displayName);
        if (matcher.matches()) {
            String entityName = matcher.group(1).replace(" ", "_").toUpperCase();
            try {
                return Optional.of(EntityType.valueOf(entityName));
            } catch (IllegalArgumentException e) {
                // Invalid entity name, continue to next check
            }
        }

        // Try language manager format
        return tryLanguageManagerFormat(displayName);
    }

    private Optional<EntityType> tryLanguageManagerFormat(String displayName) {
        for (EntityType entityType : EntityType.values()) {
            String entityTypeName = entityType.name();
            String expectedName = languageManager.getMessage("spawner-name", "%entity%", entityTypeName);
            if (displayName.equals(expectedName)) {
                return Optional.of(entityType);
            }
        }
        return Optional.empty();
    }

    private boolean processStackAddition(Player player, SpawnerData targetSpawner, ItemStack itemInHand,
                                         boolean stackAll, int currentStack, int maxStackSize) {
        int itemAmount = itemInHand.getAmount();
        int spaceLeft = maxStackSize - currentStack;

        int amountToStack = stackAll ? Math.min(spaceLeft, itemAmount) : 1;
        int newStack = currentStack + amountToStack;

        SpawnerStackEvent e = new SpawnerStackEvent(player, targetSpawner.getSpawnerLocation(), currentStack, newStack);
        Bukkit.getPluginManager().callEvent(e);
        if(e.isCancelled()) return false;
        // Update spawner data
        targetSpawner.setStackSize(newStack);
        if (targetSpawner.isAtCapacity()) {
            targetSpawner.setAtCapacity(false);
        }

        // Update player's inventory
        updatePlayerInventory(player, itemInHand, amountToStack);

        // Visual feedback
        showStackAnimation(targetSpawner, newStack, player);

        return true;
    }

    private void updatePlayerInventory(Player player, ItemStack itemInHand, int amountUsed) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            int remainingAmount = itemInHand.getAmount() - amountUsed;

            if (remainingAmount <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                itemInHand.setAmount(remainingAmount);
            }
        }
    }

    private void showStackAnimation(SpawnerData spawner, int newStack, Player player) {
        if (configManager.getBoolean("particles-spawner-stack")) {
            Location loc = spawner.getSpawnerLocation();
            World world = loc.getWorld();

            if (world != null) {
                // Use location-based scheduling for particle effects
                Scheduler.runLocationTask(loc, () -> {
                    world.spawnParticle(
                            ParticleWrapper.VILLAGER_HAPPY,
                            loc.clone().add(0.5, 0.5, 0.5),
                            10, 0.3, 0.3, 0.3, 0
                    );
                });
            }
        }

        languageManager.sendMessage(player, "messages.hand-stack", "%amount%", String.valueOf(newStack));
    }
}