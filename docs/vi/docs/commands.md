---
outline: [2, 3]
---

# Lệnh

Mọi lệnh SmartSpawner có ba alias. Bạn có thể dùng bất kỳ alias nào:

<div style="display:flex;gap:8px;flex-wrap:wrap;margin:12px 0 24px;">
  <code style="padding:4px 12px;background:var(--vp-c-brand-soft);color:var(--vp-c-brand-1);border-radius:6px;font-weight:700;">/ss</code>
  <code style="padding:4px 12px;background:var(--vp-c-brand-soft);color:var(--vp-c-brand-1);border-radius:6px;font-weight:700;">/spawner</code>
  <code style="padding:4px 12px;background:var(--vp-c-brand-soft);color:var(--vp-c-brand-1);border-radius:6px;font-weight:700;">/smartspawner</code>
</div>

Nhấp vào lệnh hoặc quyền để sao chép vào clipboard.

::: tip
Mọi lệnh cần quyền gốc `smartspawner.command.use` cùng node cụ thể của từng lệnh. Xem trang [Quyền](/vi/docs/permissions) để biết danh sách đầy đủ.
:::

## Lệnh Trao Spawner

### /ss give spawner

<CommandRow commands="/ss give spawner &lt;player&gt; &lt;name&gt; [amount]" permission="smartspawner.command.give">

Trao Smart Spawner cho người chơi.

- `<player>`: Người chơi đích hoặc selector (`@p`, `@a`, v.v.)
- `<name>`: Tên mục cấp cao nhất trong `spawner_mobs.yml`, ví dụ `zombie_spawner`
- `[amount]`: Số lượng tùy chọn từ 1–6400, mặc định 1

</CommandRow>

### /ss give vanilla_spawner

<CommandRow commands="/ss give vanilla_spawner &lt;player&gt; &lt;type&gt; [amount]" permission="smartspawner.command.give">

Trao spawner Minecraft vanilla. Không GUI, không xếp chồng; hoạt động như block spawner mặc định đặt từ chế độ sáng tạo.

</CommandRow>

### /ss give item_spawner

<CommandRow commands="/ss give item_spawner &lt;player&gt; &lt;name&gt; [amount]" permission="smartspawner.command.give">

Trao Item Spawner cho người chơi.

- `<name>`: Tên mục cấp cao nhất trong `spawner_items.yml`, ví dụ `diamond_spawner`

</CommandRow>

## Lệnh Quản Trị

### /ss edit

<CommandRow :commands="['/ss edit smartspawner', '/ss edit itemspawner']" permission="smartspawner.command.edit">

Mở trình sửa cấu hình trong game cho từng loại spawner.

- `smartspawner`: Chỉnh các mục trong `spawner_mobs.yml`
- `itemspawner`: Chỉnh các mục trong `spawner_items.yml`
- Mỗi loại có GUI riêng và không thể chuyển qua lại giữa hai GUI.
- Thay đổi được áp dụng ngay; không cần chạy `/ss reload`.

</CommandRow>

### /ss add

<CommandRow :commands="['/ss add smartspawner &lt;mob&gt; [name] [NBT tag]', '/ss add itemspawner [name]']" permission="smartspawner.command.add">

Tạo mục mới trước khi chỉnh XP, texture và loot.

- `smartspawner`: Tự động gợi ý các mob có trong phiên bản máy chủ hiện tại. NBT là tùy chọn và mặc định là `{}`. Khi được cung cấp, NBT dùng compound SNBT giống `/summon` và được kiểm tra mà không spawn entity thật.
- `itemspawner`: Mở GUI; đặt vật phẩm nguồn vào ô giữa rồi xác nhận. Vật phẩm gốc sẽ được trả lại.
- Tên là tùy chọn. Khoảng trắng tự đổi thành `_`; nếu bỏ qua, tên mặc định là `<entity>_spawner` hoặc `<item>_spawner`.

Ví dụ:

```bash
/ss add smartspawner zombie {}
/ss add smartspawner minecraft:zombie
/ss add smartspawner zombie Boss Room {NoAI:1b,Silent:1b}
/ss add itemspawner Jump Boost Farm
```

Lệnh không ghi đè tên cấu hình đã tồn tại.

</CommandRow>

### /ss reload

<CommandRow commands="/ss reload" permission="smartspawner.command.reload">

Tải lại toàn bộ cấu hình mà không cần khởi động lại máy chủ. Áp dụng thay đổi trong `config.yml`, `spawner_mobs.yml`, `spawner_items.yml`, `sell_integration.yml`, `activity_log.yml`, file ngôn ngữ và các hook tích hợp. Các tùy chọn ghi RESTART trong `config.yml` không được áp dụng.

</CommandRow>

### /ss list

<CommandRow commands="/ss list" permission="smartspawner.command.list">

Mở GUI quản trị liệt kê mọi spawner. Hỗ trợ dịch chuyển đến spawner, lọc theo thế giới và xem spawner trên nhiều máy chủ ở chế độ MySQL.

</CommandRow>

### /ss hologram

<CommandRow commands="/ss hologram" permission="smartspawner.command.hologram">

Bật hoặc tắt hologram cho toàn bộ spawner.

</CommandRow>

### /ss prices

<CommandRow commands="/ss prices" permission="smartspawner.command.prices">

Mở GUI hiển thị giá bán của mọi vật phẩm do spawner tạo. Cần tích hợp bán đang hoạt động.

</CommandRow>

### /ss near

<CommandRow :commands="['/ss near [radius]', '/ss near cancel']" permission="smartspawner.command.near">

Quét spawner trong bán kính đã cho (mặc định 50, tối đa 200) và đánh dấu xuyên tường bằng viền BlockDisplay phát sáng.

- Quét bất đồng bộ và hiển thị tiến trình trên boss bar
- Chỉ người chạy lệnh nhìn thấy đánh dấu
- Tự hết hạn sau 30 giây; dùng `/ss near cancel` để xóa ngay

</CommandRow>

### /ss set

<CommandRow commands="/ss set &lt;property&gt; &lt;value&gt; [world x y z]" permission="smartspawner.command.set">

Đặt thuộc tính cho spawner. Nếu không có tọa độ, lệnh nhắm vào spawner người chơi đang nhìn.

- Thuộc tính: `stack_size`, `range`, `delay`
- `delay` nhận tick thô hoặc định dạng thời gian: `25s`, `1m`, `1h`

</CommandRow>

### /ss language

<CommandRow :commands="['/ss language', '/ss language &lt;locale&gt;']" permission="smartspawner.command.language">

Xem hoặc đổi ngôn ngữ đang dùng. Tab-complete tên locale từ thư mục `language/`.

</CommandRow>

### /ss gui_layout

<CommandRow :commands="['/ss gui_layout', '/ss gui_layout &lt;layout&gt;']" permission="smartspawner.command.gui_layout">

Xem hoặc đổi bố cục GUI đang dùng. Tab-complete tên layout từ thư mục `gui_layouts/`.

</CommandRow>

### /ss clear

<CommandRow :commands="['/ss clear holograms', '/ss clear ghost_spawners']" permission="smartspawner.command.clear">

- `holograms`: Xóa mọi hologram SmartSpawner. Dùng để dọn hologram bị kẹt sau crash hoặc lỗi chunk.
- `ghost_spawners`: Phát hiện và xóa bản ghi cơ sở dữ liệu của spawner không còn block thật tại vị trí đã lưu.

</CommandRow>
