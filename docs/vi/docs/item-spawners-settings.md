# Thiết Lập Item Spawner

File `plugins/SmartSpawner/item_spawners_settings.yml` cấu hình vật phẩm, XP và texture cho **Item Spawner**, loại spawner tạo nguyên liệu trực tiếp thay vì drop mob.

::: info Hệ số vật phẩm
Mỗi chu kỳ tạo từ **min_mobs** đến **max_mobs** lần (mặc định 1–4). Số lượng cấu hình là giá trị cơ sở được nhân lên.
:::

::: warning Giới hạn
Item Spawner không hỗ trợ potion hoặc enchanted book. Chỉ **tipped arrow** hỗ trợ hiệu ứng potion.
:::

## Định Dạng Cấu Hình

```yaml
default_material: "SPAWNER"

ITEM_MATERIAL:
  material: <MATERIAL>
  experience: <number>
  loot:
    ITEM_ID:
      amount: <min>-<max>
      chance: <percentage>
      potion_type: <TYPE>   # Tùy chọn — chỉ tipped arrow
  head_texture:
    material: <MATERIAL>
    custom_texture: <hash>  # null đối với material vanilla
```

## Tham Chiếu Thuộc Tính

| Thuộc tính | Định dạng | Mô tả |
|------------|-----------|-------|
| `material` | `"DIAMOND"` | Material chính mà spawner đại diện |
| `experience` | `1` | XP tạo ra mỗi lần kích hoạt |
| `amount` | `1-1` | Khoảng số lượng cơ sở mỗi chu kỳ |
| `chance` | `100.0` | Xác suất rơi từ 0.0–100.0 |
| `potion_type` | `POISON` | Loại potion, chỉ dành cho tipped arrow |

::: tip Tên material
Mỗi giá trị `material` là một tên material của Bukkit viết hoa, ví dụ `DIAMOND` hoặc `NETHERITE_INGOT`. Xem danh sách đầy đủ các tên hợp lệ tại đây: [Danh sách Bukkit Material](https://jd.papermc.io/paper/26.2/org/bukkit/Material.html).
:::

## Ví Dụ

### Spawner Tài Nguyên Cơ Bản

```yaml
DIAMOND:
  material: "DIAMOND"
  experience: 1
  loot:
    DIAMOND:
      amount: 1-1
      chance: 100.0
  head_texture:
    material: "DIAMOND"
    custom_texture: null
```

### Nhiều Loại Vật Phẩm

```yaml
GOLD_INGOT:
  material: "GOLD_INGOT"
  experience: 1
  loot:
    GOLD_INGOT:
      amount: 1-2
      chance: 100.0
    GOLD_NUGGET:
      amount: 3-5
      chance: 50.0
  head_texture:
    material: "GOLD_INGOT"
    custom_texture: null
```

### Custom Head

```yaml
EMERALD:
  material: "EMERALD"
  experience: 1
  loot:
    EMERALD:
      amount: 1-1
      chance: 100.0
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "abc123def456..."
```

### Tipped Arrow

```yaml
TIPPED_ARROW:
  material: "TIPPED_ARROW"
  experience: 1
  loot:
    TIPPED_ARROW:
      amount: 8-16
      chance: 100.0
      potion_type: POISON
  head_texture:
    material: "TIPPED_ARROW"
    custom_texture: null
```

### Vật Phẩm Hiếm Có Xác Suất

```yaml
TOTEM_OF_UNDYING:
  material: "TOTEM_OF_UNDYING"
  experience: 2
  loot:
    TOTEM_OF_UNDYING:
      amount: 1-1
      chance: 75.0
    EMERALD:
      amount: 1-3
      chance: 50.0
  head_texture:
    material: "TOTEM_OF_UNDYING"
    custom_texture: null
```

## Cơ Chế Tạo Vật Phẩm

```
actual_drops = base_amount × random(min_mobs, max_mobs)
```

| Số lượng cấu hình | Đầu ra có thể có |
|-------------------|------------------|
| `1-1` | 1–4 vật phẩm |
| `1-2` | 1–8 vật phẩm |
| `2-3` | 2–12 vật phẩm |

## Cấu Hình Mặc Định

SmartSpawner có sẵn cấu hình cho các nguyên liệu giá trị phổ biến.

- **Xem online:** [item_spawners_settings.yml trên GitHub](https://github.com/OpenVdra/SmartSpawner/blob/main/core/src/main/resources/item_spawners_settings.yml)
- **Đặt lại:** Xóa file rồi khởi động lại để tạo mới

## Trao Item Spawner

```bash
/ss give item_spawner <player> <MATERIAL> [amount]
```

```bash
/ss give item_spawner @p DIAMOND 1
/ss give item_spawner Player123 NETHERITE_INGOT 5
```
