# Phương Thức Kiểm Tra

Các phương thức xác định và đọc vật phẩm spawner.

## Tham Chiếu

| Phương thức | Mô tả | Trả về |
|-------------|-------|--------|
| `isSmartSpawner(ItemStack)` | Kiểm tra Smart Spawner | `boolean` |
| `isVanillaSpawner(ItemStack)` | Kiểm tra vanilla spawner | `boolean` |
| `isItemSpawner(ItemStack)` | Kiểm tra item spawner | `boolean` |
| `getSpawnerEntityType(ItemStack)` | Lấy entity từ bất kỳ spawner nào | `EntityType` |
| `getItemSpawnerMaterial(ItemStack)` | Lấy material từ item spawner | `Material` |

## Ví Dụ

### Xác Định Loại Spawner

```java
@EventHandler
public void onPlayerInteract(PlayerInteractEvent event) {
    ItemStack item = event.getItem();
    if (item == null) return;

    if (api.isSmartSpawner(item)) {
        player.sendMessage("Smart Spawner: " + api.getSpawnerEntityType(item));
    } else if (api.isVanillaSpawner(item)) {
        player.sendMessage("Vanilla Spawner: " + api.getSpawnerEntityType(item));
    } else if (api.isItemSpawner(item)) {
        player.sendMessage("Item Spawner: " + api.getItemSpawnerMaterial(item));
    }
}
```

### Lấy Entity Type

```java
EntityType type = api.getSpawnerEntityType(player.getItemInHand());

if (type != null) {
    player.sendMessage("Spawns: " + type.name());
} else {
    player.sendMessage("Not a valid spawner.");
}
```

### Lấy Material Item Spawner

```java
ItemStack item = player.getItemInHand();

if (api.isItemSpawner(item)) {
    Material mat = api.getItemSpawnerMaterial(item);
    if (mat != null) {
        player.sendMessage("Generates: " + mat.name());
    }
}
```
