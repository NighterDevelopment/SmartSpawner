# API Bố Cục GUI

SmartSpawner cho phép plugin bên ngoài đăng ký và ghi đè bố cục GUI khi máy chủ đang chạy.

## Hai Cách Tiếp Cận

1. **Đăng ký toàn cục**: đăng ký bố cục có tên để quản trị viên chọn bằng `gui_layout`.
2. **Provider theo spawner**: chọn bố cục động theo từng cặp spawner/người chơi.

## Đăng Ký Bố Cục Toàn Cục

| Phương thức | Trả về |
|-------------|--------|
| `getLayoutRegistry()` | `GuiLayoutRegistry` |
| `registerLayout(name, main, storage, sellConfirm)` | `boolean` |
| `registerLayoutFromYaml(name, main, storage, sellConfirm)` | `boolean` |
| `unregisterLayout(String)` | `boolean` |
| `isRegistered(String)` | `boolean` |
| `getRegisteredLayouts()` | `Set<String>` |

### Tạo Bằng Code

```java
GuiLayoutData mainGui = new GuiLayoutBuilder()
    .type(GuiLayoutType.MAIN_GUI)
    .addButton("slot_11", new GuiButtonBuilder()
        .slot(11)
        .material(Material.CHEST)
        .cooldown("2s")
        .action("click", "open_storage")
        .sound("block.chest.open", 1.0f, 1.0f)
        .build())
    .addButton("slot_15", new GuiButtonBuilder()
        .slot(15)
        .material(Material.EXPERIENCE_BOTTLE)
        .action("click", "collect_exp")
        .build())
    .build();

api.getLayoutRegistry().registerLayout("myplugin_custom", mainGui, null, null);
```

Sau đó quản trị viên có thể chọn:

```yaml
gui_layout: myplugin_custom
```

### Đăng Ký Từ YAML

```java
File main = new File(getDataFolder(), "layouts/main_gui.yml");
File storage = new File(getDataFolder(), "layouts/storage_gui.yml");
File sell = new File(getDataFolder(), "layouts/sell_confirm_gui.yml");

api.getLayoutRegistry().registerLayoutFromYaml("myplugin_custom", main, storage, sell);
```

## Provider Theo Spawner

```java
api.setSpawnerLayoutProvider(new SpawnerGuiLayoutProvider() {
    @Override
    public GuiLayoutData getLayout(SpawnerDataDTO spawner, Player player, GuiLayoutType type) {
        if (type == GuiLayoutType.MAIN_GUI && isPremiumPlayer(player)) {
            return premiumMainGui;
        }
        return null;
    }

    @Override
    public String getProviderName() {
        return "MyPlugin";
    }
});
```

Trả về `null` để dùng thứ tự dự phòng:

1. Provider theo spawner
2. Bố cục đăng ký toàn cục
3. Bố cục file trong `gui_layouts/`

Xóa provider bằng `api.clearSpawnerLayoutProvider()`.

## GuiLayoutType

```java
public enum GuiLayoutType {
    MAIN_GUI,
    STORAGE_GUI,
    SELL_CONFIRM_GUI
}
```

## Action Nút Có Sẵn

| Action | Mô tả |
|--------|-------|
| `open_storage` | Mở GUI kho |
| `open_stacker` | Mở GUI stacker |
| `collect_exp` | Nhận XP đã lưu |
| `sell_and_exp` | Bán vật phẩm và nhận XP |
| `sell_all` | Bán toàn bộ |
| `take_all` | Lấy toàn bộ vật phẩm |
| `sort_items` | Sắp xếp/lọc |
| `drop_page` | Thả mọi vật phẩm trang hiện tại |
| `previous_page` / `next_page` | Chuyển trang kho |
| `return_main` | Về GUI chính |
| `confirm` / `cancel` | Xác nhận/hủy bán |
| `none` | Chỉ hiển thị |

Loại click: `click`, `left_click`, `right_click`, `shift_left_click`, `shift_right_click`.

## Lưu Ý

- `GuiLayoutData` và `GuiButtonData` bất biến và thread-safe.
- Số slot bắt đầu từ 1, giống file YAML.
- Hàng điều khiển kho dùng slot `1–9`, ánh xạ sang hàng inventory `45–53`.
- Provider theo người chơi phải giữ vị trí slot nhất quán cho cùng một spawner.
