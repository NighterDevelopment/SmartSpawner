# Thiết Lập Spawner

File `plugins/SmartSpawner/spawners_settings.yml` điều khiển bảng vật phẩm, XP, texture head và tỷ lệ rơi tùy chọn cho từng loại mob của Smart Spawner.

::: info Hệ số vật phẩm
Mỗi chu kỳ tạo vật phẩm chạy từ **min_mobs** đến **max_mobs** lần (mặc định 1–4). Số lượng cấu hình là giá trị cơ sở cho mỗi mob nên đầu ra thực tế có thể lớn hơn.
:::

::: warning Giới hạn hiện tại
Smart Spawner không hỗ trợ potion hoặc enchanted book. Chỉ **tipped arrow** hỗ trợ hiệu ứng potion.
:::

## Định Dạng Cấu Hình

```yaml
# Material dự phòng toàn cục cho mob không xác định
default_material: "SPAWNER"

MOB_NAME:
  experience: <number>
  drop_chance: <percentage>   # Tùy chọn — mặc định 100.0 nếu bỏ qua
  head_texture:
    material: <MATERIAL>
    custom_texture: <hash>    # null đối với head vanilla
  loot:                       # Tùy chọn
    ITEM_ID:
      amount: <min>-<max>
      chance: <percentage>
      durability: <min>-<max> # Tùy chọn — công cụ/vũ khí
      potion_type: <TYPE>     # Tùy chọn — chỉ tipped arrow
```

## Tham Chiếu Thuộc Tính

### Thuộc Tính Spawner

| Thuộc tính | Định dạng | Mô tả |
|------------|-----------|-------|
| `experience` | `5` | XP tạo ra mỗi lần spawner kích hoạt |
| `drop_chance` | `75.0` | Xác suất vật phẩm Smart Spawner rơi khi bị phá; bỏ qua để dùng 100.0 |
| `material` | `"PLAYER_HEAD"` | Material head hiển thị trong block spawner |
| `custom_texture` | `"abc123..."` | Hash texture cho player head; dùng `null` cho head vanilla |

### Thuộc Tính Vật Phẩm

| Thuộc tính | Định dạng | Mô tả |
|------------|-----------|-------|
| `amount` | `1-3` | Khoảng số lượng vật phẩm mỗi chu kỳ |
| `chance` | `50.0` | Xác suất rơi từ 0.0–100.0 |
| `durability` | `1-384` | Khoảng độ bền của công cụ/vũ khí |
| `potion_type` | `POISON` | Loại potion cho tipped arrow |

## Tỷ Lệ Rơi Khi Phá Spawner

`drop_chance` quyết định **vật phẩm spawner** có rơi khi block bị phá hay không. Nó độc lập với `chance` trong `loot`, vốn điều khiển vật phẩm được tạo.

- Bỏ qua `drop_chance`: spawner luôn rơi, tỷ lệ 100%.
- Khi đặt giá trị, mỗi lần phá có xác suất tương ứng để trả lại vật phẩm spawner.
- Khi bật `sneak_break`, spawner có `drop_chance` **không thể** bị phá cả stack khi cúi; người chơi phải phá từng chiếc.
- Người có `smartspawner.break.bypassdropchance` luôn nhận vật phẩm và dùng được mọi tính năng stack.

## Ví Dụ

### Mob Dùng Custom Head

```yaml
COW:
  experience: 3
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "b667c0e107be79d7679bfe89bbc57c6bf198ecb529a3295fcfdfd2f24408dca3"
  loot:
    LEATHER:
      amount: 0-2
      chance: 66.67
    BEEF:
      amount: 1-3
      chance: 100.0
```

### Mob Dùng Head Vanilla

```yaml
SKELETON:
  experience: 5
  head_texture:
    material: "SKELETON_SKULL"
    custom_texture: null
  loot:
    BONE:
      amount: 0-2
      chance: 66.67
    ARROW:
      amount: 0-2
      chance: 66.67
    BOW:
      amount: 1-1
      chance: 8.5
      durability: 1-384
```

### Mob Có Vũ Khí

```yaml
WITHER_SKELETON:
  experience: 5
  head_texture:
    material: "WITHER_SKELETON_SKULL"
    custom_texture: null
  loot:
    COAL:
      amount: 0-1
      chance: 33.33
    BONE:
      amount: 0-2
      chance: 66.67
    WITHER_SKELETON_SKULL:
      amount: 0-1
      chance: 2.5
    STONE_SWORD:
      amount: 1-1
      chance: 8.5
      durability: 1-131
```

### Mob Có Tipped Arrow

```yaml
BOGGED:
  experience: 5
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "a3b9003ba2d05562c75119b8a62185c67130e9282f7acbac4bc2824c21eb95d9"
  loot:
    BONE:
      amount: 0-2
      chance: 66.67
    TIPPED_ARROW:
      amount: 0-2
      chance: 50.0
      potion_type: POISON
```

### Mob Có Tỷ Lệ Rơi Spawner

```yaml
ALLAY:
  experience: 0
  drop_chance: 75.0
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "df5de940bfe499c59ee8dac9f9c3919e7535eff3a9acb16f4842bf290f4c679f"
```

### Mob Không Có Vật Phẩm

```yaml
BAT:
  experience: 0
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "81c5cc1f40005a33124c60384a0f17a36a7b19ae90f1c32dcda17b5b56280a43"
  # Không có mục loot = không tạo vật phẩm
```

## Cơ Chế Tạo Vật Phẩm

Đầu ra thực tế mỗi chu kỳ:

```
actual_drops = base_amount × random(min_mobs, max_mobs)
```

Với mặc định `min_mobs=1`, `max_mobs=4`:

| Số lượng cấu hình | Đầu ra có thể có |
|-------------------|------------------|
| `1-1` | 1–4 vật phẩm |
| `2-3` | 2–12 vật phẩm |
| `1-2` | 1–8 vật phẩm |

Mỗi mục loot được roll độc lập, vì vậy một chu kỳ có thể tạo nhiều loại vật phẩm cùng lúc.

## Tìm Texture Head

- [Minecraft-Heads.com](https://minecraft-heads.com/)
- [MCHeads.net](https://mc-heads.net/)

Chỉ dùng phần hash trong URL texture, không bao gồm `http://textures.minecraft.net/texture/`.

### Material Head Vanilla

- `SKELETON_SKULL`
- `WITHER_SKELETON_SKULL`
- `ZOMBIE_HEAD`
- `PIGLIN_HEAD`
- `DRAGON_HEAD`

## Cấu Hình Mặc Định

SmartSpawner cung cấp `spawners_settings.yml` đầy đủ cho mọi mob vanilla với bảng vật phẩm dựa trên [Minecraft Wiki](https://minecraft.wiki).

- **Xem online:** [spawners_settings.yml trên GitHub](https://github.com/OpenVdra/SmartSpawner/blob/main/core/src/main/resources/spawners_settings.yml)
- **Đặt lại:** Xóa file rồi khởi động lại máy chủ

## Trao Spawner

```bash
/ss give spawner <player> <mob_type> [amount]
```

```bash
/ss give spawner @p skeleton 1
/ss give spawner Player123 wither_skeleton 3
```
