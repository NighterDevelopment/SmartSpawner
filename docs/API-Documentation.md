# 💻 API Documentation

SmartSpawner provides a comprehensive API for developers to integrate with the plugin's functionality. This guide covers all available API features, events, and usage examples.

## 🚀 Getting Started

### Maven Dependency

Add the SmartSpawner API to your project's `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/ptthanh02/SmartSpawner</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>github.nighter</groupId>
        <artifactId>smartspawner-api</artifactId>
        <version>1.3.5</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Gradle Dependency

Add to your `build.gradle`:

```gradle
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/ptthanh02/SmartSpawner")
    }
}

dependencies {
    compileOnly 'github.nighter:smartspawner-api:1.3.5'
}
```

### Plugin Dependencies

Add SmartSpawner as a dependency in your `plugin.yml`:

```yaml
name: YourPlugin
version: 1.0.0
main: com.yourplugin.YourPlugin
depend: [SmartSpawner]
# or for soft dependency:
softdepend: [SmartSpawner]
```

## 🔧 Core API Interface

### Getting the API Instance

```java
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerProvider;

public class YourPlugin extends JavaPlugin {
    
    private SmartSpawnerAPI smartSpawnerAPI;
    
    @Override
    public void onEnable() {
        // Get API instance
        smartSpawnerAPI = SmartSpawnerProvider.getAPI();
        
        if (smartSpawnerAPI == null) {
            getLogger().warning("SmartSpawner API not available!");
            return;
        }
        
        getLogger().info("SmartSpawner API loaded successfully!");
    }
}
```

### API Methods

#### Creating Spawner Items

```java
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

// Create a single zombie spawner
ItemStack zombieSpawner = smartSpawnerAPI.createSpawnerItem(EntityType.ZOMBIE);

// Create multiple skeleton spawners
ItemStack skeletonSpawners = smartSpawnerAPI.createSpawnerItem(EntityType.SKELETON, 5);

// Create spawners for different mob types
ItemStack creeperSpawner = smartSpawnerAPI.createSpawnerItem(EntityType.CREEPER);
ItemStack cowSpawner = smartSpawnerAPI.createSpawnerItem(EntityType.COW);
ItemStack ironGolemSpawner = smartSpawnerAPI.createSpawnerItem(EntityType.IRON_GOLEM);
```

#### Validating Spawner Items

```java
// Check if an item is a valid SmartSpawner
ItemStack item = player.getInventory().getItemInMainHand();

if (smartSpawnerAPI.isValidSpawner(item)) {
    EntityType entityType = smartSpawnerAPI.getSpawnerEntityType(item);
    player.sendMessage("You're holding a " + entityType.name() + " spawner!");
} else {
    player.sendMessage("This is not a valid spawner!");
}
```

#### Complete Example

```java
public class SpawnerManager {
    private final SmartSpawnerAPI api;
    
    public SpawnerManager() {
        this.api = SmartSpawnerProvider.getAPI();
    }
    
    public void giveRandomSpawner(Player player) {
        if (api == null) return;
        
        // Array of possible spawner types
        EntityType[] types = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.COW, EntityType.PIG, EntityType.CHICKEN
        };
        
        // Select random type
        EntityType randomType = types[new Random().nextInt(types.length)];
        
        // Create spawner item
        ItemStack spawner = api.createSpawnerItem(randomType);
        
        // Give to player
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(spawner);
            player.sendMessage("You received a " + randomType.name() + " spawner!");
        } else {
            player.getWorld().dropItem(player.getLocation(), spawner);
            player.sendMessage("Your inventory is full! Spawner dropped at your feet.");
        }
    }
}
```

## 📡 Event System

SmartSpawner provides a comprehensive event system for monitoring and controlling spawner activities.

### Available Events

#### SpawnerStackEvent

Fired when spawners are stacked together.

```java
import github.nighter.smartspawner.api.events.SpawnerStackEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class SpawnerStackListener implements Listener {
    
    @EventHandler
    public void onSpawnerStack(SpawnerStackEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        int oldQuantity = event.getOldQuantity();
        int newQuantity = event.getNewQuantity();
        SpawnerStackEvent.StackSource source = event.getSource();
        
        // Log the stacking operation
        System.out.println(player.getName() + " stacked spawners at " + 
                          location.toString() + " from " + oldQuantity + 
                          " to " + newQuantity + " via " + source);
        
        // Prevent stacking if player doesn't have permission
        if (!player.hasPermission("spawner.stack.unlimited")) {
            if (newQuantity > 64) {
                event.setCancelled(true);
                player.sendMessage("You can't stack more than 64 spawners!");
            }
        }
        
        // Reward player for stacking
        if (!event.isCancelled() && newQuantity >= 10) {
            player.sendMessage("Great job stacking spawners! Here's a bonus.");
            // Give reward logic here
        }
    }
}
```

#### SpawnerBreakEvent

Fired when a spawner is broken.

```java
import github.nighter.smartspawner.api.events.SpawnerBreakEvent;

@EventHandler
public void onSpawnerBreak(SpawnerBreakEvent event) {
    Entity entity = event.getEntity();
    Location location = event.getLocation();
    int quantity = event.getQuantity();
    
    if (entity instanceof Player) {
        Player player = (Player) entity;
        
        // Log spawner breaking
        getLogger().info(player.getName() + " broke " + quantity + 
                        " spawners at " + location.toString());
        
        // Check if player is in protected region
        if (isInProtectedRegion(location)) {
            // Handle protected region logic
            player.sendMessage("You cannot break spawners in this area!");
        }
    }
}
```

#### SpawnerPlaceEvent

Fired when a spawner is placed.

```java
import github.nighter.smartspawner.api.events.SpawnerPlaceEvent;

@EventHandler
public void onSpawnerPlace(SpawnerPlaceEvent event) {
    Player player = event.getPlayer();
    Location location = event.getLocation();
    int quantity = event.getQuantity();
    
    // Check spawner limits per player
    int playerSpawnerCount = getPlayerSpawnerCount(player);
    int maxSpawners = getMaxSpawnersForPlayer(player);
    
    if (playerSpawnerCount + quantity > maxSpawners) {
        event.setCancelled(true);
        player.sendMessage("You've reached your spawner limit of " + maxSpawners);
        return;
    }
    
    // Welcome message for first spawner
    if (playerSpawnerCount == 0) {
        player.sendMessage("Congratulations on placing your first spawner!");
    }
}
```

#### SpawnerExpClaimEvent

Fired when a player claims experience from a spawner.

```java
import github.nighter.smartspawner.api.events.SpawnerExpClaimEvent;

@EventHandler
public void onSpawnerExpClaim(SpawnerExpClaimEvent event) {
    Player player = event.getPlayer();
    Location location = event.getLocation();
    int expAmount = event.getExpAmount();
    
    // Bonus experience for VIP players
    if (player.hasPermission("spawner.exp.bonus")) {
        int bonusExp = expAmount / 2; // 50% bonus
        player.giveExp(bonusExp);
        player.sendMessage("VIP Bonus: +" + bonusExp + " extra experience!");
    }
    
    // Track experience statistics
    updatePlayerStats(player, "exp_claimed", expAmount);
}
```

#### SpawnerSellEvent

Fired when items are sold from spawner storage.

```java
import github.nighter.smartspawner.api.events.SpawnerSellEvent;

@EventHandler
public void onSpawnerSell(SpawnerSellEvent event) {
    Player player = event.getPlayer();
    Location location = event.getLocation();
    double amount = event.getAmount();
    
    // Tax system for selling
    if (player.hasPermission("spawner.sell.tax")) {
        double tax = amount * 0.1; // 10% tax
        double finalAmount = amount - tax;
        
        // Modify the sell amount
        event.setAmount(finalAmount);
        
        player.sendMessage("Sold items for $" + finalAmount + " (Tax: $" + tax + ")");
    }
    
    // Achievement system
    updateSellAchievements(player, amount);
}
```

#### SpawnerEggChangeEvent

Fired when a spawner type is changed using spawn eggs.

```java
import github.nighter.smartspawner.api.events.SpawnerEggChangeEvent;

@EventHandler
public void onSpawnerEggChange(SpawnerEggChangeEvent event) {
    Player player = event.getPlayer();
    Location location = event.getLocation();
    EntityType oldType = event.getOldEntityType();
    EntityType newType = event.getNewEntityType();
    
    // Prevent changing to certain mob types
    if (newType == EntityType.WITHER || newType == EntityType.ENDER_DRAGON) {
        event.setCancelled(true);
        player.sendMessage("You cannot change spawners to this mob type!");
        return;
    }
    
    // Cost system for changing spawner types
    double cost = getChangeCost(oldType, newType);
    if (!chargePlayer(player, cost)) {
        event.setCancelled(true);
        player.sendMessage("You need $" + cost + " to change this spawner type!");
        return;
    }
    
    player.sendMessage("Spawner type changed from " + oldType.name() + 
                      " to " + newType.name() + " for $" + cost);
}
```

#### SpawnerExplodeEvent

Fired when a spawner is destroyed by an explosion.

```java
import github.nighter.smartspawner.api.events.SpawnerExplodeEvent;

@EventHandler
public void onSpawnerExplode(SpawnerExplodeEvent event) {
    Location location = event.getLocation();
    int quantity = event.getQuantity();
    
    // Log explosion damage to spawners
    getLogger().warning("Explosion destroyed " + quantity + 
                       " spawners at " + location.toString());
    
    // Compensation system
    if (hasInsurance(location)) {
        // Restore spawners after explosion
        Bukkit.getScheduler().runTaskLater(this, () -> {
            restoreSpawners(location, quantity);
        }, 100L); // 5 seconds delay
    }
}
```

#### SpawnerRemoveEvent

Fired when a spawner is removed from the world.

```java
import github.nighter.smartspawner.api.events.SpawnerRemoveEvent;

@EventHandler
public void onSpawnerRemove(SpawnerRemoveEvent event) {
    Location location = event.getLocation();
    int quantity = event.getQuantity();
    
    // Clean up any associated data
    cleanupSpawnerData(location);
    
    // Update region statistics
    updateRegionStats(location, -quantity);
    
    getLogger().info("Removed " + quantity + " spawners at " + location.toString());
}
```

## 🎯 Practical Examples

### Custom Spawner Shop

```java
public class SpawnerShop implements Listener {
    private final SmartSpawnerAPI api;
    private final Map<EntityType, Double> prices;
    
    public SpawnerShop() {
        this.api = SmartSpawnerProvider.getAPI();
        this.prices = new HashMap<>();
        
        // Setup prices
        prices.put(EntityType.ZOMBIE, 100.0);
        prices.put(EntityType.SKELETON, 150.0);
        prices.put(EntityType.SPIDER, 200.0);
        prices.put(EntityType.CREEPER, 300.0);
        prices.put(EntityType.COW, 50.0);
        prices.put(EntityType.PIG, 50.0);
        prices.put(EntityType.CHICKEN, 50.0);
        prices.put(EntityType.IRON_GOLEM, 1000.0);
    }
    
    public void openShop(Player player) {
        Inventory shop = Bukkit.createInventory(null, 27, "Spawner Shop");
        
        int slot = 0;
        for (Map.Entry<EntityType, Double> entry : prices.entrySet()) {
            if (slot >= 27) break;
            
            EntityType type = entry.getKey();
            double price = entry.getValue();
            
            ItemStack spawner = api.createSpawnerItem(type);
            ItemMeta meta = spawner.getItemMeta();
            
            List<String> lore = new ArrayList<>();
            lore.add("§7Price: §6$" + price);
            lore.add("§7Click to purchase!");
            meta.setLore(lore);
            
            spawner.setItemMeta(meta);
            shop.setItem(slot, spawner);
            slot++;
        }
        
        player.openInventory(shop);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Spawner Shop")) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        
        if (item == null || !api.isValidSpawner(item)) return;
        
        EntityType type = api.getSpawnerEntityType(item);
        double price = prices.get(type);
        
        if (hasEnoughMoney(player, price)) {
            chargeMoney(player, price);
            player.getInventory().addItem(api.createSpawnerItem(type));
            player.sendMessage("§aPurchased " + type.name() + " spawner for $" + price);
            player.closeInventory();
        } else {
            player.sendMessage("§cYou don't have enough money! Need $" + price);
        }
    }
}
```

### Spawner Statistics Tracker

```java
public class SpawnerStats implements Listener {
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    
    public class PlayerStats {
        public int spawnersPlaced = 0;
        public int spawnersBroken = 0;
        public int spawnersStacked = 0;
        public int expClaimed = 0;
        public double moneySold = 0.0;
    }
    
    @EventHandler
    public void onSpawnerPlace(SpawnerPlaceEvent event) {
        PlayerStats stats = getPlayerStats(event.getPlayer());
        stats.spawnersPlaced += event.getQuantity();
    }
    
    @EventHandler
    public void onSpawnerBreak(SpawnerBreakEvent event) {
        if (event.getEntity() instanceof Player) {
            PlayerStats stats = getPlayerStats((Player) event.getEntity());
            stats.spawnersBroken += event.getQuantity();
        }
    }
    
    @EventHandler
    public void onSpawnerStack(SpawnerStackEvent event) {
        PlayerStats stats = getPlayerStats(event.getPlayer());
        stats.spawnersStacked += (event.getNewQuantity() - event.getOldQuantity());
    }
    
    @EventHandler
    public void onExpClaim(SpawnerExpClaimEvent event) {
        PlayerStats stats = getPlayerStats(event.getPlayer());
        stats.expClaimed += event.getExpAmount();
    }
    
    @EventHandler
    public void onSpawnerSell(SpawnerSellEvent event) {
        PlayerStats stats = getPlayerStats(event.getPlayer());
        stats.moneySold += event.getAmount();
    }
    
    private PlayerStats getPlayerStats(Player player) {
        return playerStats.computeIfAbsent(player.getUniqueId(), k -> new PlayerStats());
    }
    
    public void showStats(Player player) {
        PlayerStats stats = getPlayerStats(player);
        
        player.sendMessage("§6=== Spawner Statistics ===");
        player.sendMessage("§7Spawners Placed: §a" + stats.spawnersPlaced);
        player.sendMessage("§7Spawners Broken: §c" + stats.spawnersBroken);
        player.sendMessage("§7Spawners Stacked: §e" + stats.spawnersStacked);
        player.sendMessage("§7Experience Claimed: §b" + stats.expClaimed);
        player.sendMessage("§7Money from Sales: §6$" + String.format("%.2f", stats.moneySold));
    }
}
```

### Spawner Region Protection

```java
public class SpawnerRegionProtection implements Listener {
    private final Set<Location> protectedSpawners = new HashSet<>();
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerBreak(SpawnerBreakEvent event) {
        Location location = event.getLocation();
        
        if (protectedSpawners.contains(location)) {
            if (event.getEntity() instanceof Player) {
                Player player = (Player) event.getEntity();
                if (!player.hasPermission("spawner.break.protected")) {
                    event.setCancelled(true);
                    player.sendMessage("§cThis spawner is protected!");
                }
            } else {
                // Prevent non-player breaking (explosions, etc.)
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onSpawnerPlace(SpawnerPlaceEvent event) {
        Location location = event.getLocation();
        Player player = event.getPlayer();
        
        // Auto-protect spawners in certain worlds
        if (isProtectedWorld(location.getWorld())) {
            protectedSpawners.add(location);
            player.sendMessage("§aYour spawner is now protected!");
        }
    }
    
    public void protectSpawner(Location location) {
        protectedSpawners.add(location);
    }
    
    public void unprotectSpawner(Location location) {
        protectedSpawners.remove(location);
    }
    
    public boolean isProtected(Location location) {
        return protectedSpawners.contains(location);
    }
}
```

## 📚 Advanced API Usage

### Custom Spawner Types

```java
public class CustomSpawnerTypes {
    private final SmartSpawnerAPI api;
    
    public CustomSpawnerTypes() {
        this.api = SmartSpawnerProvider.getAPI();
    }
    
    public ItemStack createBossSpawner() {
        // Create a special "boss" spawner using zombie type as base
        ItemStack spawner = api.createSpawnerItem(EntityType.ZOMBIE);
        ItemMeta meta = spawner.getItemMeta();
        
        meta.setDisplayName("§4§lBoss Spawner");
        List<String> lore = Arrays.asList(
            "§7Spawns powerful boss mobs",
            "§7with special abilities",
            "§c§lDangerous!"
        );
        meta.setLore(lore);
        
        // Add custom NBT data for identification
        spawner.setItemMeta(meta);
        return spawner;
    }
    
    public boolean isBossSpawner(ItemStack item) {
        if (!api.isValidSpawner(item)) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        String displayName = meta.getDisplayName();
        return displayName != null && displayName.contains("Boss Spawner");
    }
}
```

### Integration with Other Plugins

```java
// Example: Integration with WorldGuard
public class WorldGuardIntegration implements Listener {
    
    @EventHandler
    public void onSpawnerPlace(SpawnerPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        
        // Check WorldGuard region permissions
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            if (!canPlaceSpawner(player, location)) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot place spawners in this region!");
            }
        }
    }
    
    private boolean canPlaceSpawner(Player player, Location location) {
        // WorldGuard API integration code here
        return true; // Simplified for example
    }
}

// Example: Integration with Economy plugins
public class EconomyIntegration implements Listener {
    
    @EventHandler
    public void onSpawnerSell(SpawnerSellEvent event) {
        Player player = event.getPlayer();
        double amount = event.getAmount();
        
        // Add money using Vault economy
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            EconomyResponse response = VaultEconomy.depositPlayer(player, amount);
            if (response.transactionSuccess()) {
                player.sendMessage("§a$" + amount + " has been added to your account!");
            }
        }
    }
}
```

## 🔍 Error Handling & Best Practices

### Null Checks and Validation

```java
public class SafeAPIUsage {
    
    public void safeAPIExample(Player player, String mobType) {
        // Always check if API is available
        SmartSpawnerAPI api = SmartSpawnerProvider.getAPI();
        if (api == null) {
            player.sendMessage("§cSmartSpawner is not available!");
            return;
        }
        
        // Validate entity type
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(mobType.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid mob type: " + mobType);
            return;
        }
        
        // Check if entity type is valid for spawners
        if (!entityType.isSpawnable()) {
            player.sendMessage("§cThis mob type cannot be spawned!");
            return;
        }
        
        // Create spawner safely
        try {
            ItemStack spawner = api.createSpawnerItem(entityType);
            
            // Check if creation was successful
            if (spawner != null && api.isValidSpawner(spawner)) {
                player.getInventory().addItem(spawner);
                player.sendMessage("§aGave you a " + entityType.name() + " spawner!");
            } else {
                player.sendMessage("§cFailed to create spawner!");
            }
        } catch (Exception e) {
            player.sendMessage("§cAn error occurred while creating the spawner!");
            e.printStackTrace();
        }
    }
}
```

### Performance Considerations

```java
public class PerformantAPIUsage {
    
    // Cache API instance instead of getting it repeatedly
    private final SmartSpawnerAPI api;
    
    public PerformantAPIUsage() {
        this.api = SmartSpawnerProvider.getAPI();
    }
    
    // Batch operations when possible
    public void giveMassSpawners(List<Player> players, EntityType type, int amount) {
        if (api == null) return;
        
        // Pre-create the spawner item once
        ItemStack spawnerTemplate = api.createSpawnerItem(type, amount);
        
        // Give to all players
        for (Player player : players) {
            if (player.isOnline()) {
                // Clone the template for each player
                ItemStack spawner = spawnerTemplate.clone();
                player.getInventory().addItem(spawner);
            }
        }
    }
    
    // Use async operations for heavy tasks
    public void processSpawnerData(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Heavy data processing here
            
            // Switch back to main thread for API calls
            Bukkit.getScheduler().runTask(plugin, () -> {
                // API calls must be on main thread
                ItemStack spawner = api.createSpawnerItem(EntityType.ZOMBIE);
                player.getInventory().addItem(spawner);
            });
        });
    }
}
```

## 📖 Additional Resources

### JavaDoc Reference

The complete JavaDoc documentation is available at: [API Documentation](https://ptthanh02.github.io/SmartSpawner/javadoc/)

### Example Plugins

Check out these example plugins using the SmartSpawner API:

- **SpawnerShop** - Complete spawner shop system
- **SpawnerStats** - Advanced statistics tracking
- **SpawnerProtection** - Region-based protection system

### Community Resources

- **Discord Server:** [Join for API support](http://discord.com/invite/FJN7hJKPyb)
- **GitHub Examples:** [API Examples Repository](https://github.com/ptthanh02/SmartSpawner-Examples)
- **Wiki Tutorials:** [Step-by-step guides](https://github.com/ptthanh02/SmartSpawner/wiki)

## 🔗 Related Documentation

- **[🔧 Configuration](Configuration.md)** - API configuration options
- **[⌨️ Commands & Permissions](Commands-Permissions.md)** - Command API integration
- **[🔌 Integrations](Integrations.md)** - Third-party plugin integrations
- **[🔍 Troubleshooting](Troubleshooting.md)** - API-related issues

---

**Build amazing integrations** with the SmartSpawner API! Join our [Discord](http://discord.com/invite/FJN7hJKPyb) for development support and share your creations with the community.