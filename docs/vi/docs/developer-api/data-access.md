# Truy Cập Dữ Liệu Spawner

Các phương thức đọc và chỉnh sửa dữ liệu spawner qua API.

## Tham Chiếu Phương Thức

| Phương thức | Mô tả | Trả về |
|-------------|-------|--------|
| `getSpawnerByLocation(Location)` | Lấy spawner tại vị trí block | `SpawnerDataDTO` |
| `getSpawnerById(String)` | Lấy spawner theo ID duy nhất | `SpawnerDataDTO` |
| `getAllSpawners()` | Lấy toàn bộ spawner đã đăng ký | `List<SpawnerDataDTO>` |
| `getSpawnerModifier(String)` | Lấy modifier để đổi thuộc tính | `SpawnerDataModifier` |
| `removeSpawner(String)` | Xóa spawner theo ID | `CompletableFuture<Boolean>` |
| `removeSpawner(Location)` | Xóa spawner theo vị trí | `CompletableFuture<Boolean>` |

## SpawnerDataDTO Chỉ Đọc

| Phương thức | Trả về |
|-------------|--------|
| `getSpawnerId()` | `String` |
| `getLocation()` | `Location` |
| `getEntityType()` | `EntityType` |
| `getSpawnedItemMaterial()` | `Material` cho item spawner |
| `getStackSize()` | `int` |
| `getMaxStackSize()` | `int` |
| `getBaseMaxStoragePages()` | `int` |
| `getBaseMinMobs()` | `int` |
| `getBaseMaxMobs()` | `int` |
| `getBaseMaxStoredExp()` | `int` |
| `getBaseSpawnerDelay()` | `long` theo tick |
| `isItemSpawner()` | `boolean` |

## SpawnerDataModifier

`SpawnerDataModifier` là **cách duy nhất để sửa thuộc tính spawner**. Có thể chain phương thức và phải gọi `applyChanges()` để lưu. `stackSize` chỉ đọc.

| Phương thức | Trả về |
|-------------|--------|
| `getStackSize()` | `int` chỉ đọc |
| `getMaxStackSize()` / `setMaxStackSize(int)` | `int` / `SpawnerDataModifier` |
| `getBaseMaxStoragePages()` / `setBaseMaxStoragePages(int)` | `int` / `SpawnerDataModifier` |
| `getBaseMinMobs()` / `setBaseMinMobs(int)` | `int` / `SpawnerDataModifier` |
| `getBaseMaxMobs()` / `setBaseMaxMobs(int)` | `int` / `SpawnerDataModifier` |
| `getBaseMaxStoredExp()` / `setBaseMaxStoredExp(int)` | `int` / `SpawnerDataModifier` |
| `getBaseSpawnerDelay()` / `setBaseSpawnerDelay(long)` | `long` / `SpawnerDataModifier` |
| `applyChanges()` | `void` |

## Ví Dụ

### Đọc Dữ Liệu

```java
SpawnerDataDTO spawnerData = api.getSpawnerByLocation(block.getLocation());

if (spawnerData != null) {
    player.sendMessage("ID: " + spawnerData.getSpawnerId());
    player.sendMessage("Entity: " + spawnerData.getEntityType());
    player.sendMessage("Stack: " + spawnerData.getStackSize());
    player.sendMessage("Delay: " + spawnerData.getBaseSpawnerDelay() + " ticks");
}
```

### Sửa Thuộc Tính

```java
SpawnerDataModifier modifier = api.getSpawnerModifier(spawnerData.getSpawnerId());

if (modifier != null) {
    modifier.setBaseMaxMobs(15)
            .setBaseMinMobs(5)
            .setBaseSpawnerDelay(400L)
            .setBaseMaxStoredExp(5000)
            .setBaseMaxStoragePages(3)
            .applyChanges();
}
```

### Liệt Kê Toàn Bộ

```java
List<SpawnerDataDTO> all = api.getAllSpawners();

for (SpawnerDataDTO s : all) {
    player.sendMessage("- " + s.getEntityType() + " at " + s.getLocation()
        + " (stack: " + s.getStackSize() + ")");
}
```

### Xóa Spawner

`removeSpawner` trả về `CompletableFuture<Boolean>` vì chunk có thể phải tải bất đồng bộ. Kết quả `true` khi dọn xong, hoặc `false` nếu spawner không tồn tại hay đang được chỉnh sửa.

```java
api.removeSpawner(spawnerId).thenAccept(removed -> {
    player.sendMessage(removed ? "Spawner removed." : "Spawner not found or busy.");
});

api.removeSpawner(block.getLocation()).thenAccept(removed -> {
    player.sendMessage(removed ? "Removed." : "Nothing found there.");
});
```
