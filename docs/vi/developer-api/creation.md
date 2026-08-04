# Các Phương Thức Tạo Vật Phẩm

Các phương thức tạo Smart Spawner, vanilla spawner và item spawner bằng code.

## Tham Chiếu Phương Thức

| Phương thức | Mô tả | Trả về |
|-------------|-------|--------|
| `createSpawnerItem(EntityType)` | Tạo một Smart Spawner | `ItemStack` |
| `createSpawnerItem(EntityType, int)` | Tạo nhiều Smart Spawner | `ItemStack` |
| `createVanillaSpawnerItem(EntityType)` | Tạo một vanilla spawner | `ItemStack` |
| `createVanillaSpawnerItem(EntityType, int)` | Tạo nhiều vanilla spawner | `ItemStack` |
| `createItemSpawnerItem(Material)` | Tạo một item spawner | `ItemStack` |
| `createItemSpawnerItem(Material, int)` | Tạo nhiều item spawner | `ItemStack` |

## Smart Spawner

```java
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

ItemStack zombieSpawner = api.createSpawnerItem(EntityType.ZOMBIE);
ItemStack skeletonSpawners = api.createSpawnerItem(EntityType.SKELETON, 5);
player.getInventory().addItem(zombieSpawner);
```

## Vanilla Spawner

```java
ItemStack vanillaSpawner = api.createVanillaSpawnerItem(EntityType.CREEPER);
ItemStack vanillaSpawners = api.createVanillaSpawnerItem(EntityType.COW, 3);
```

## Item Spawner

```java
import org.bukkit.Material;

ItemStack diamondSpawner = api.createItemSpawnerItem(Material.DIAMOND);
ItemStack goldSpawners = api.createItemSpawnerItem(Material.GOLD_INGOT, 10);
```
