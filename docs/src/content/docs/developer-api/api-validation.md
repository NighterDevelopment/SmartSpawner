---
title: Validation Methods
description: Methods for validating and identifying spawner types and their properties.
---
## Validation Methods

| Method | Description | Return Type |
|--------|-------------|-------------|
| `isSmartSpawner(ItemStack)` | Checks if item is a SmartSpawner | `boolean` |
| `isVanillaSpawner(ItemStack)` | Checks if item is a vanilla spawner | `boolean` |
| `isItemSpawner(ItemStack)` | Checks if item is an item spawner | `boolean` |
| `getSpawnerEntityType(ItemStack)` | Gets entity type from spawner | `EntityType` |
| `getItemSpawnerMaterial(ItemStack)` | Gets material from item spawner | `Material` |

### `isSmartSpawner()`

Checks if an ItemStack is a SmartSpawner (with custom features).

```java
@EventHandler
public void onPlayerInteract(PlayerInteractEvent event) {
    ItemStack item = event.getItem();
    
    if (api.isSmartSpawner(item)) {
        player.sendMessage("This is a SmartSpawner!");
    }
}
```

### `isVanillaSpawner()`

Checks if an ItemStack is a vanilla spawner (without SmartSpawner features).

```java
ItemStack item = player.getInventory().getItemInMainHand();

if (api.isVanillaSpawner(item)) {
    player.sendMessage("This is a vanilla spawner!");
}
```

### `isItemSpawner()`

Checks if an ItemStack is an item spawner.

```java
@EventHandler
public void onSpawnerPlace(BlockPlaceEvent event) {
    ItemStack item = event.getItemInHand();
    
    if (api.isItemSpawner(item)) {
        player.sendMessage("You placed an item spawner!");
    }
}
```

### `getSpawnerEntityType()`

Gets the entity type from any spawner item.

```java
ItemStack item = player.getItemInHand();
EntityType entityType = api.getSpawnerEntityType(item);

if (entityType != null) {
    player.sendMessage("This spawner spawns: " + entityType.name());
} else {
    player.sendMessage("This is not a valid spawner!");
}
```

### `getItemSpawnerMaterial()`

Gets the material type from an item spawner.

```java
ItemStack item = player.getItemInHand();

if (api.isItemSpawner(item)) {
    Material material = api.getItemSpawnerMaterial(item);
    if (material != null) {
        player.sendMessage("This spawner spawns: " + material.name());
    }
}
```

<br>
<br>

---

*Last update: November 17, 2025 11:46:34*