# Sự Kiện API

SmartSpawner phát các sự kiện Bukkit mà plugin của bạn có thể lắng nghe.

## Tham Chiếu Sự Kiện

| Sự kiện | Mô tả | Có thể hủy |
|---------|-------|:----------:|
| `SpawnerBreakEvent` | Spawner bị phá bởi người chơi hoặc vụ nổ | Không |
| `SpawnerPlaceEvent` | Người chơi đặt spawner | Có |
| `SpawnerPlayerBreakEvent` | Người chơi trực tiếp phá spawner | Có |
| `SpawnerStackEvent` | Xếp chồng bằng tay | Có |
| `SpawnerSellEvent` | Bán vật phẩm từ kho | Có |
| `SpawnerExpClaimEvent` | Nhận kinh nghiệm từ spawner | Có |
| `SpawnerEggChangeEvent` | Đổi loại bằng spawn egg | Có |
| `SpawnerExplodeEvent` | Spawner bị phá bởi vụ nổ | Không |
| `SpawnerRemoveEvent` | Rút spawner khỏi stack qua GUI | Có |
| `SpawnerOpenGUIEvent` | Người chơi mở GUI spawner | Có |
| `SpawnerDropAllEvent` | Thả mọi vật phẩm trên trang kho | Có |
| `SpawnerTakeAllEvent` | Lấy mọi vật phẩm trên trang kho | Có |

## Ví Dụ

### SpawnerBreakEvent

```java
@EventHandler
public void onSpawnerBreak(SpawnerBreakEvent event) {
    Entity breaker = event.getEntity();
    Location location = event.getLocation();
    int quantity = event.getQuantity();

    if (breaker instanceof Player player) {
        player.sendMessage("You broke " + quantity + " spawner(s)!");
    }
}
```

### SpawnerPlaceEvent

```java
@EventHandler
public void onSpawnerPlace(SpawnerPlaceEvent event) {
    Player player = event.getPlayer();
    Location location = event.getLocation();
    player.sendMessage("Spawner placed at " + location.getBlockX() + ", "
        + location.getBlockY() + ", " + location.getBlockZ());
}
```

### SpawnerPlayerBreakEvent

```java
@EventHandler
public void onPlayerBreakSpawner(SpawnerPlayerBreakEvent event) {
    Player player = event.getPlayer();

    if (!player.hasPermission("yourplugin.break")) {
        event.setCancelled(true);
        player.sendMessage("You cannot break spawners here.");
    }
}
```

### SpawnerStackEvent

```java
@EventHandler
public void onSpawnerStack(SpawnerStackEvent event) {
    event.getPlayer().sendMessage("New stack size: " + event.getNewStackSize());
}
```

### SpawnerSellEvent

```java
@EventHandler
public void onSpawnerSell(SpawnerSellEvent event) {
    Player player = event.getPlayer();
    double price = event.getPrice();
    double bonus = price * 0.1;
    // economy.depositPlayer(player, bonus);
}
```

### SpawnerExpClaimEvent

```java
@EventHandler
public void onExpClaim(SpawnerExpClaimEvent event) {
    event.setExpAmount(event.getExpAmount() * 2);
}
```

### SpawnerEggChangeEvent

```java
@EventHandler
public void onSpawnerEggChange(SpawnerEggChangeEvent event) {
    Player player = event.getPlayer();
    EntityType oldType = event.getOldEntityType();
    EntityType newType = event.getNewEntityType();
    player.sendMessage("Changed from " + oldType + " to " + newType);
}
```

### SpawnerOpenGUIEvent

```java
@EventHandler
public void onSpawnerOpenGUI(SpawnerOpenGUIEvent event) {
    if (!event.getPlayer().hasPermission("yourplugin.gui")) {
        event.setCancelled(true);
    }
}
```

### SpawnerDropAllEvent và SpawnerTakeAllEvent

```java
@EventHandler
public void onSpawnerDropAll(SpawnerDropAllEvent event) {
    if (!event.getPlayer().hasPermission("yourplugin.dropall")) {
        event.setCancelled(true);
    }
}

@EventHandler
public void onSpawnerTakeAll(SpawnerTakeAllEvent event) {
    if (!event.getPlayer().hasPermission("yourplugin.takeall")) {
        event.setCancelled(true);
    }
}
```
