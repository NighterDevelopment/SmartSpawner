---
title: Creation Methods
description: Methods for creating SmartSpawner, vanilla spawner, and item spawner items.
---
## Creation Methods

| Method | Description | Return Type |
|--------|-------------|-------------|
| `createSpawnerItem(EntityType)` | Creates a SmartSpawner item | `ItemStack` |
| `createSpawnerItem(EntityType, int)` | Creates multiple SmartSpawner items | `ItemStack` |
| `createVanillaSpawnerItem(EntityType)` | Creates a vanilla spawner item | `ItemStack` |
| `createVanillaSpawnerItem(EntityType, int)` | Creates multiple vanilla spawner items | `ItemStack` |
| `createItemSpawnerItem(Material)` | Creates an item spawner | `ItemStack` |
| `createItemSpawnerItem(Material, int)` | Creates multiple item spawners | `ItemStack` |

### Creating SmartSpawners

SmartSpawners are custom spawners with full SmartSpawner features including stacking, storage, and custom drops.

```java
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

// Create a single zombie spawner
ItemStack zombieSpawner = api.createSpawnerItem(EntityType.ZOMBIE);

// Create multiple skeleton spawners
ItemStack skeletonSpawners = api.createSpawnerItem(EntityType.SKELETON, 5);

// Give to player
player.getInventory().addItem(zombieSpawner);
```

### Creating Vanilla Spawners

Vanilla spawners function like standard Minecraft spawners without SmartSpawner features.

```java
// Create a vanilla creeper spawner
ItemStack vanillaSpawner = api.createVanillaSpawnerItem(EntityType.CREEPER);

// Create multiple vanilla spawners
ItemStack vanillaSpawners = api.createVanillaSpawnerItem(EntityType.COW, 3);
```

### Creating Item Spawners

Item spawners spawn items instead of entities.

```java
import org.bukkit.Material;

// Create a diamond spawner
ItemStack diamondSpawner = api.createItemSpawnerItem(Material.DIAMOND);

// Create multiple gold ingot spawners
ItemStack goldSpawners = api.createItemSpawnerItem(Material.GOLD_INGOT, 10);
```

<br>
<br>

---

*Last update: November 17, 2025 11:46:34*