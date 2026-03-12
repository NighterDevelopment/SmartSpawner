---
title: Complete Examples
description: Full code examples demonstrating SmartSpawner API usage patterns.
---
## Complete Examples

### Example 1: Spawner Item Validation

This example demonstrates all validation methods:

```java
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SpawnerChecker implements Listener {
    
    private final SmartSpawnerAPI api;
    
    public SpawnerChecker(SmartSpawnerAPI api) {
        this.api = api;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || item.getType() != Material.SPAWNER) {
            return;
        }
        
        // Check spawner type
        if (api.isSmartSpawner(item)) {
            EntityType type = api.getSpawnerEntityType(item);
            player.sendMessage("§aSmartSpawner: §e" + type);
        } 
        else if (api.isVanillaSpawner(item)) {
            EntityType type = api.getSpawnerEntityType(item);
            player.sendMessage("§7Vanilla Spawner: §e" + type);
        } 
        else if (api.isItemSpawner(item)) {
            Material material = api.getItemSpawnerMaterial(item);
            player.sendMessage("§6Item Spawner: §e" + material);
        }
    }
}
```

### Example 2: Spawner Data Management

This example shows how to access and modify spawner data:

```java
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import github.nighter.smartspawner.api.data.SpawnerDataModifier;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnerCommand implements CommandExecutor {
    
    private final SmartSpawnerAPI api;
    
    public SpawnerCommand(SmartSpawnerAPI api) {
        this.api = api;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Player only command!");
            return true;
        }
        
        Player player = (Player) sender;
        Location location = player.getTargetBlock(null, 5).getLocation();
        
        // Get spawner data
        SpawnerDataDTO spawnerData = api.getSpawnerByLocation(location);
        
        if (spawnerData == null) {
            player.sendMessage("§cNo spawner found at that location!");
            return true;
        }
        
        // Display spawner information
        player.sendMessage("§6=== Spawner Info ===");
        player.sendMessage("§eID: §f" + spawnerData.getSpawnerId());
        player.sendMessage("§eEntity: §f" + spawnerData.getEntityType());
        player.sendMessage("§eStack Size: §f" + spawnerData.getStackSize() + 
                          " §7(read-only)");
        player.sendMessage("§eMax Stack: §f" + spawnerData.getMaxStackSize());
        player.sendMessage("§eBase Delay: §f" + spawnerData.getBaseSpawnerDelay() + " ticks");
        player.sendMessage("§eBase Min Mobs: §f" + spawnerData.getBaseMinMobs());
        player.sendMessage("§eBase Max Mobs: §f" + spawnerData.getBaseMaxMobs());
        player.sendMessage("§eBase Max Exp: §f" + spawnerData.getBaseMaxStoredExp());
        player.sendMessage("§eBase Storage Pages: §f" + spawnerData.getBaseMaxStoragePages());
        
        // Modify spawner using SpawnerDataModifier
        if (args.length > 0 && args[0].equalsIgnoreCase("upgrade")) {
            SpawnerDataModifier modifier = api.getSpawnerModifier(spawnerData.getSpawnerId());
            
            if (modifier != null) {
                // Upgrade spawner with method chaining
                modifier.setBaseMaxMobs(modifier.getBaseMaxMobs() + 2)
                        .setBaseMinMobs(modifier.getBaseMinMobs() + 1)
                        .setBaseMaxStoredExp(modifier.getBaseMaxStoredExp() + 500)
                        .setBaseMaxStoragePages(modifier.getBaseMaxStoragePages() + 1)
                        .applyChanges();
                
                player.sendMessage("§aSpawner upgraded successfully!");
            }
        }
        
        // Modify delay using SpawnerDataModifier
        if (args.length > 0 && args[0].equalsIgnoreCase("setdelay")) {
            if (args.length > 1) {
                try {
                    long newDelay = Long.parseLong(args[1]);
                    SpawnerDataModifier modifier = api.getSpawnerModifier(spawnerData.getSpawnerId());
                    
                    if (modifier != null) {
                        modifier.setBaseSpawnerDelay(newDelay)
                                .applyChanges();
                        player.sendMessage("§aSpawner delay set to " + newDelay + " ticks!");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid delay value!");
                }
            }
        }
        
        return true;
    }
}
```

### Example 3: Spawner Statistics Command

This example creates a command to display server-wide spawner statistics:

```java
import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.data.SpawnerDataDTO;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpawnerStatsCommand implements CommandExecutor {
    
    private final SmartSpawnerAPI api;
    
    public SpawnerStatsCommand(SmartSpawnerAPI api) {
        this.api = api;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Get all spawners
        List<SpawnerDataDTO> allSpawners = api.getAllSpawners();
        
        // Count by entity type
        Map<EntityType, Integer> spawnerCounts = new HashMap<>();
        int totalStackSize = 0;
        
        for (SpawnerDataDTO spawner : allSpawners) {
            EntityType type = spawner.getEntityType();
            spawnerCounts.put(type, spawnerCounts.getOrDefault(type, 0) + 1);
            totalStackSize += spawner.getStackSize();
        }
        
        // Display statistics
        sender.sendMessage("§6=== Spawner Statistics ===");
        sender.sendMessage("§eTotal Spawners: §f" + allSpawners.size());
        sender.sendMessage("§eTotal Stack Size: §f" + totalStackSize);
        sender.sendMessage("");
        sender.sendMessage("§eSpawners by Type:");
        
        spawnerCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(entry -> {
                    sender.sendMessage("  §7- §f" + entry.getKey() + 
                                     "§7: §e" + entry.getValue());
                });
        
        return true;
    }
}
```

<br>
<br>

---

*Last update: November 17, 2025 11:38:36*