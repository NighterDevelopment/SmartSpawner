# Cấu Hình Chính

File `plugins/SmartSpawner/config.yml` điều khiển ngôn ngữ, hành vi spawner, kinh tế, hiệu ứng, ghi log, cơ sở dữ liệu và hiệu năng.

Nhấp vào một tùy chọn hoặc nhóm để xem thông tin chi tiết.

## Định Dạng Thời Gian

SmartSpawner hỗ trợ thời lượng ngắn, dễ đọc ở mọi nơi cần thời gian:

| Định dạng | Ý nghĩa |
|-----------|---------|
| `20s` | 20 giây |
| `5m` | 5 phút |
| `1h` | 1 giờ |
| `1d` | 1 ngày |
| `1d_2h_30m_15s` | 1 ngày, 2 giờ, 30 phút, 15 giây |

Đơn vị hỗ trợ: `s` `m` `h` `d` `w` `mo` `y`

---

<div style="background-color: var(--vp-c-bg-alt); padding: 20px; border-radius: 12px; margin-top: 20px;">

<ConfigProperty name="language" value="en_US" type="string">

Thư mục ngôn ngữ được tải từ <code>plugins/SmartSpawner/language/</code>.<br><br>

| Locale | Ngôn ngữ |
|--------|----------|
| <code>en_US</code> | Tiếng Anh |
| <code>en_US_DonutSMP</code> | Tiếng Anh – bố cục DonutSMP |
| <code>en_US_DonutSMP_v2</code> | Tiếng Anh – bố cục DonutSMP v2 |
| <code>tr_TR</code> | Tiếng Thổ Nhĩ Kỳ |
| <code>vi_VN</code> | Tiếng Việt |

Để thêm ngôn ngữ riêng, tạo thư mục mới trong <code>language/</code>, sao chép file từ <code>en_US/</code> làm mẫu rồi dịch nội dung.

</ConfigProperty>

<ConfigProperty name="gui_layout" value="default" type="string">
Thư mục bố cục GUI trong <code>plugins/SmartSpawner/gui_layouts/</code>. Tùy chọn có sẵn: <code>default</code>, <code>DonutSMP</code>, <code>DonutSMP_v2</code>.
</ConfigProperty>

<ConfigProperty name="debug" value="false" type="boolean">
Bật thêm đầu ra console để chẩn đoán. Nên giữ <code>false</code> trên máy chủ production.
</ConfigProperty>

<ConfigGroup name="spawner_properties">
<template #info>
Điều khiển hành vi mặc định của mọi Smart Spawner, trừ khi bị ghi đè bởi cấu hình riêng theo mob.
</template>

<ConfigGroup name="default">

<ConfigProperty name="min_mobs" value="1" type="number">
Số mob ảo tối thiểu mỗi chu kỳ. Giá trị thật được chọn ngẫu nhiên giữa <code>min_mobs</code> và <code>max_mobs</code>.
</ConfigProperty>

<ConfigProperty name="max_mobs" value="4" type="number">
Số mob ảo tối đa mỗi chu kỳ.
</ConfigProperty>

<ConfigProperty name="range" value="16" type="number">
Khoảng cách kích hoạt theo block. Spawner chỉ tạo vật phẩm khi có người chơi trong phạm vi này.
</ConfigProperty>

<ConfigProperty name="delay" value="25s" type="string">
Thời gian giữa các chu kỳ, dùng định dạng phía trên.
</ConfigProperty>

<ConfigProperty name="max_storage_pages" value="1" type="number">
Kích thước kho nội bộ. Mỗi trang có 45 slot.
</ConfigProperty>

<ConfigProperty name="max_stored_exp" value="1000" type="number">
XP tối đa spawner lưu trước khi ngừng tạo thêm.
</ConfigProperty>

<ConfigProperty name="max_stack_size" value="10000" type="number">
Số spawner tối đa có thể xếp trong một block.
</ConfigProperty>

<ConfigProperty name="allow_exp_mending" value="true" type="boolean">
Cho phép XP đã lưu sửa vật phẩm có Mending khi người chơi nhận XP.
</ConfigProperty>

<ConfigProperty name="protect_from_explosions" value="true" type="boolean">
Ngăn vụ nổ phá block Smart Spawner.
</ConfigProperty>

</ConfigGroup>
</ConfigGroup>

<ConfigGroup name="spawner_break">
<template #info>
Điều khiển cách người chơi phá và thu thập Smart Spawner.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Công tắc chính cho việc phá và thu thập Smart Spawner.
</ConfigProperty>

<ConfigProperty name="direct_to_inventory" value="false" type="boolean">
Đưa spawner thẳng vào inventory thay vì rơi xuống đất.
</ConfigProperty>

<ConfigProperty name="required_tools" :value="['IRON_PICKAXE', 'GOLDEN_PICKAXE', 'DIAMOND_PICKAXE', 'NETHERITE_PICKAXE']" type="list">
Danh sách công cụ được phép phá và thu thập Smart Spawner.
</ConfigProperty>

<ConfigProperty name="durability_loss" value="1" type="number">
Số điểm độ bền trừ khỏi công cụ khi phá spawner.
</ConfigProperty>

<ConfigProperty name="sneak_break" value="true" type="boolean">
Khi bật, cúi trong lúc phá một stack sẽ lấy tối đa 64 spawner cùng lúc.<br><br>
::: warning Tỷ lệ rơi và phá khi cúi
Nếu một loại mob có `drop_chance` trong `spawner_mobs.yml`, không thể phá theo stack trừ khi người chơi có `smartspawner.break.bypassdropchance`.
:::
</ConfigProperty>

<ConfigProperty name="sell_and_xp_break" value="true" type="boolean">
Khi Smart Spawner bị xóa hoàn toàn, tự bán vật phẩm và nhận XP còn lại. Cần tích hợp bán và quyền <code>smartspawner.sellall</code>.
</ConfigProperty>

<ConfigGroup name="silk_touch">

<ConfigProperty name="required" value="true" type="boolean">
Yêu cầu enchantment Silk Touch để nhận vật phẩm spawner.
</ConfigProperty>

<ConfigProperty name="level" value="1" type="number">
Cấp Silk Touch tối thiểu.
</ConfigProperty>

</ConfigGroup>
</ConfigGroup>

<ConfigGroup name="natural_spawner">
<template #info>
Thiết lập cho spawner vanilla sinh tự nhiên trong dungeon, mineshaft và các công trình khác.
</template>

<ConfigProperty name="breakable" value="false" type="boolean">
Cho phép phá và thu thập spawner tự nhiên.
</ConfigProperty>

<ConfigProperty name="convert_to_smart_spawner" value="false" type="boolean">
Nếu bật, spawner tự nhiên bị phá sẽ trở thành Smart Spawner; nếu tắt, nó rơi vật phẩm vanilla spawner.
</ConfigProperty>

<ConfigProperty name="drop_chance" value="(commented out)" type="string">
Xác suất tùy chọn từ <code>0.0</code> đến <code>100.0</code> để spawner tự nhiên rơi vật phẩm. Dùng khóa <code>default</code> cho mọi loại mob và thêm loại mob cụ thể để ghi đè. Nếu bỏ comment hoặc không có giá trị phù hợp, tỷ lệ mặc định là 100%.

```yaml
drop_chance:
  default: 80.0
  ZOMBIE: 75.0
  SKELETON: 50.0
  BLAZE: 25.0
```
</ConfigProperty>

<ConfigProperty name="spawn_mobs" value="true" type="boolean">
Cho phép spawner tự nhiên sinh mob bình thường.
</ConfigProperty>

<ConfigProperty name="protect_from_explosions" value="false" type="boolean">
Bảo vệ block spawner tự nhiên khỏi vụ nổ.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="sell_integration">
<template #info>
Cấu hình kinh tế và cửa hàng cho nút bán trong kho spawner.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Bật bán vật phẩm trong kho. Đặt <code>false</code> để tắt toàn bộ tính năng bán.
</ConfigProperty>

<ConfigProperty name="currency" value="VAULT" type="string">
Backend kinh tế: <code>VAULT</code> hoặc <code>EXCELLENTECONOMY</code>.
</ConfigProperty>

<ConfigProperty name="excellenteconomy_currency" value="coins" type="string">
Tên currency ExcellentEconomy, chỉ dùng khi <code>currency</code> là <code>EXCELLENTECONOMY</code>.
</ConfigProperty>

<ConfigProperty name="price_source_mode" value="SHOP_PRIORITY" type="string">
Quyết định nguồn giá bán.<br><br>

| Chế độ | Hành vi |
|--------|---------|
| <code>SHOP_ONLY</code> | Chỉ dùng giá plugin cửa hàng |
| <code>SHOP_PRIORITY</code> | Giá cửa hàng trước, giá tùy chỉnh dự phòng |
| <code>CUSTOM_ONLY</code> | Chỉ dùng <code>item_prices.yml</code> |
| <code>CUSTOM_PRIORITY</code> | Giá tùy chỉnh trước, giá cửa hàng dự phòng |

</ConfigProperty>

<ConfigGroup name="shop_integration">

<ConfigProperty name="enabled" value="true" type="boolean">
Bật tra cứu giá từ plugin cửa hàng.
</ConfigProperty>

<ConfigProperty name="preferred_plugin" value="auto" type="string">
Plugin cửa hàng ưu tiên: <code>auto</code>, <code>EconomyShopGUI</code>, <code>EconomyShopGUI-Premium</code>, <code>ShopGUIPlus</code> hoặc <code>zShop</code>.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="custom_prices">

<ConfigProperty name="enabled" value="true" type="boolean">
Bật giá tùy chỉnh từ <code>item_prices.yml</code>.
</ConfigProperty>

<ConfigProperty name="default_price" value="1.0" type="number">
Giá dự phòng cho vật phẩm chưa cấu hình. Đặt <code>0.0</code> để không cho bán.
</ConfigProperty>

</ConfigGroup>
</ConfigGroup>

<ConfigGroup name="hopper">
<template #info>
Điều khiển tự chuyển vật phẩm từ kho spawner qua hopper bên dưới.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
Bật hopper lấy vật phẩm từ kho spawner.
</ConfigProperty>

<ConfigProperty name="check_delay" value="3s" type="string">
Khoảng thời gian giữa các lần kiểm tra chuyển vật phẩm.
</ConfigProperty>

<ConfigProperty name="stack_per_transfer" value="5" type="number">
Số stack được chuyển mỗi chu kỳ, tối đa 5.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="bedrock_support">

<ConfigProperty name="enable_formui" value="true" type="boolean">
Hiển thị form thân thiện với di động cho người chơi Bedrock qua Floodgate/Geyser thay vì chest GUI.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="hologram">
<template #info>
Điều khiển văn bản nổi phía trên block spawner.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
Hiển thị loại spawner và kích thước stack phía trên block.
</ConfigProperty>
<ConfigProperty name="offset_x" value="0.5" type="number">Độ lệch ngang từ tâm block.</ConfigProperty>
<ConfigProperty name="offset_y" value="1.6" type="number">Độ lệch dọc phía trên block.</ConfigProperty>
<ConfigProperty name="offset_z" value="0.5" type="number">Độ lệch chiều sâu từ tâm block.</ConfigProperty>
<ConfigProperty name="alignment" value="CENTER" type="string">Căn chữ: <code>CENTER</code>, <code>LEFT</code> hoặc <code>RIGHT</code>.</ConfigProperty>
<ConfigProperty name="shadowed_text" value="true" type="boolean">Thêm bóng cho chữ hologram.</ConfigProperty>
<ConfigProperty name="see_through" value="false" type="boolean">Cho phép nhìn hologram xuyên block.</ConfigProperty>
<ConfigProperty name="transparent_background" value="false" type="boolean">Loại bỏ nền của hologram.</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="particle">
<template #info>
Hiệu ứng particle tùy chọn cho sự kiện spawner.
</template>

<ConfigProperty name="spawner_stack" value="true" type="boolean">Hiển thị particle khi xếp chồng.</ConfigProperty>
<ConfigProperty name="spawner_activate" value="true" type="boolean">Hiển thị particle khi spawner kích hoạt.</ConfigProperty>
<ConfigProperty name="spawner_generate_loot" value="true" type="boolean">Hiển thị particle khi vật phẩm được thêm vào kho.</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="logging">
<template #info>
Ghi thao tác spawner vào file log xoay vòng để kiểm tra và chẩn đoán.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">Bật ghi log file.</ConfigProperty>
<ConfigProperty name="json_format" value="false" type="boolean">Ghi JSON khi bật, ngược lại dùng văn bản dễ đọc.</ConfigProperty>
<ConfigProperty name="console_output" value="false" type="boolean">In thêm bản ghi ra console.</ConfigProperty>
<ConfigProperty name="max_log_files" value="10" type="number">Số file log xoay vòng được giữ.</ConfigProperty>
<ConfigProperty name="max_log_size_mb" value="10" type="number">Dung lượng tối đa mỗi file trước khi xoay.</ConfigProperty>
<ConfigProperty name="log_all_events" value="false" type="boolean">Ghi mọi sự kiện và bỏ qua <code>logged_events</code>.</ConfigProperty>

<ConfigProperty name="logged_events" :value="['SPAWNER_PLACE', 'SPAWNER_BREAK', 'SPAWNER_STACK_HAND', 'SPAWNER_SELL_ALL', 'COMMAND_EXECUTE_PLAYER']" type="list">
Các sự kiện được ghi khi <code>log_all_events</code> là <code>false</code>.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="database">
<template #info>
Cấu hình nơi lưu dữ liệu spawner.
</template>

<ConfigProperty name="mode" value="SQLITE" type="string">
Backend lưu trữ: <code>SQLITE</code> hoặc <code>MYSQL</code>. Cấu hình còn để <code>YAML</code> sẽ tự chuyển sang <code>SQLITE</code> trong lần khởi động kế tiếp.
</ConfigProperty>

<ConfigProperty name="table_prefix" value="sspawner_" type="string">
Tiền tố cho hai bảng plugin tạo ra là <code>sspawner_data</code> và <code>sspawner_schema_meta</code>. Chỉ giữ lại chữ cái, chữ số và dấu gạch dưới, ký tự khác bị loại bỏ. Đổi khi có plugin khác đã dùng tên đó trong cùng cơ sở dữ liệu, hoặc để tách hai bản cài SmartSpawner trong cùng một cơ sở dữ liệu MySQL.

Hãy đặt trước lần khởi động đầu tiên. Đổi về sau sẽ để lại bảng cũ và plugin khởi động với cơ sở dữ liệu rỗng, nên cần đổi tên bảng thủ công trước.
</ConfigProperty>

<ConfigProperty name="server_name" value="server1" type="string">
Tên máy chủ duy nhất cho mô hình MySQL liên máy chủ.
</ConfigProperty>

<ConfigProperty name="sync_across_servers" value="false" type="boolean">
Hiển thị trang chọn máy chủ trong <code>/ss list</code> để xem spawner từ mọi máy chủ dùng chung MySQL.
</ConfigProperty>

<ConfigProperty name="migrate_from_local" value="true" type="boolean">
Tự chuyển dữ liệu local khi đổi chế độ. File đã chuyển được thêm hậu tố <code>.migrated</code>.
</ConfigProperty>

<ConfigProperty name="database" value="smartspawner" type="string">
Tên cơ sở dữ liệu MySQL hoặc MariaDB cần dùng. Bỏ qua ở chế độ <code>SQLITE</code>.
</ConfigProperty>

<ConfigGroup name="sqlite">
<template #info>
Chỉ dùng ở chế độ <code>SQLITE</code>.
</template>

<ConfigProperty name="file" value="spawners.db" type="string">
Tên file cơ sở dữ liệu, nằm trong <code>plugins/SmartSpawner/</code>.
</ConfigProperty>

<ConfigProperty name="pool_size" value="4" type="number">
Số kết nối trong pool. Cơ sở dữ liệu chạy chế độ WAL nên việc đọc không bị chặn trong lúc lưu. Chỉ hạ xuống <code>1</code> khi thấy lỗi khoá trong console.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="sql">
<template #info>
Thông tin kết nối MySQL và MariaDB. Chỉ dùng ở chế độ <code>MYSQL</code>.
</template>

<ConfigProperty name="host" value="localhost" type="string">
Địa chỉ máy chủ cơ sở dữ liệu.
</ConfigProperty>

<ConfigProperty name="port" value="3306" type="number">
Cổng máy chủ cơ sở dữ liệu.
</ConfigProperty>

<ConfigProperty name="username" value="root" type="string">
Người dùng cơ sở dữ liệu.
</ConfigProperty>

<ConfigProperty name="password" value="" type="string">
Mật khẩu của người dùng đó.
</ConfigProperty>

<ConfigGroup name="pool">
<template #info>
Tinh chỉnh pool kết nối. Giá trị mặc định phù hợp với hầu hết máy chủ, chỉ đổi khi cần xử lý một vấn đề cụ thể.
</template>

<ConfigProperty name="maximum-size" value="10" type="number">
Số kết nối tối đa pool được phép mở.
</ConfigProperty>

<ConfigProperty name="minimum-idle" value="2" type="number">
Số kết nối giữ mở khi rảnh.
</ConfigProperty>

<ConfigProperty name="connection-timeout" value="10000" type="number">
Số mili giây chờ một kết nối rảnh trước khi bỏ cuộc.
</ConfigProperty>

<ConfigProperty name="max-lifetime" value="1800000" type="number">
Số mili giây một kết nối tồn tại trước khi bị thay mới. Hãy giữ thấp hơn thời gian chờ của chính cơ sở dữ liệu.
</ConfigProperty>

<ConfigProperty name="idle-timeout" value="600000" type="number">
Số mili giây giữ một kết nối rảnh trước khi đóng. Phải nhỏ hơn <code>max-lifetime</code>. <code>0</code> nghĩa là bằng <code>max-lifetime</code>.
</ConfigProperty>

<ConfigProperty name="keepalive-time" value="30000" type="number">
Số mili giây giữa các truy vấn keepalive giúp cơ sở dữ liệu không ngắt kết nối rảnh. <code>0</code> để tắt.
</ConfigProperty>

<ConfigProperty name="leak-detection-threshold" value="0" type="number">
Số mili giây một kết nối được giữ trước khi ghi cảnh báo. <code>0</code> để tắt cảnh báo.
</ConfigProperty>

</ConfigGroup>

</ConfigGroup>

</ConfigGroup>

<ConfigGroup name="performance">
<template #info>
Điều khiển cách SmartSpawner tính vật phẩm cho stack cực lớn.
</template>

<ConfigProperty name="approximate_loot" value="true" type="boolean">
Dùng phép tính trung bình nhanh cho batch cực lớn thay vì roll từng mob. Khuyến nghị cho hầu hết máy chủ.
</ConfigProperty>

<ConfigProperty name="approximation_threshold" value="1000" type="number">
Ngưỡng bắt đầu xấp xỉ khi <code>approximate_loot</code> được bật.

| Giá trị | Hành vi |
|---------|---------|
| 10–100 | Rất mạnh, phù hợp stack khổng lồ |
| 100–1000 | Cân bằng hiệu năng và độ chính xác |
| 1000–10000 | Thận trọng, gần với roll chính xác từng mob |
</ConfigProperty>

</ConfigGroup>

</div>

## Các Sự Kiện Log

| Sự kiện | Mô tả |
|---------|-------|
| `SPAWNER_PLACE` | Người chơi đặt spawner |
| `SPAWNER_BREAK` | Người chơi phá spawner |
| `SPAWNER_EXPLODE` | Spawner bị phá bởi vụ nổ |
| `SPAWNER_STACK_HAND` | Xếp chồng bằng tay |
| `SPAWNER_STACK_GUI` | Xếp chồng qua GUI |
| `SPAWNER_DESTACK_GUI` | Rút stack qua GUI |
| `SPAWNER_GUI_OPEN` | Mở GUI chính |
| `SPAWNER_STORAGE_OPEN` | Mở GUI kho |
| `SPAWNER_STACKER_OPEN` | Mở GUI stacker |
| `SPAWNER_EXP_CLAIM` | Nhận XP |
| `SPAWNER_SELL_ALL` | Bán vật phẩm trong kho |
| `SPAWNER_ITEM_TAKE_ALL` | Lấy toàn bộ vật phẩm |
| `SPAWNER_ITEM_DROP` | Thả vật phẩm bằng phím drop |
| `SPAWNER_ITEMS_SORT` | Sắp xếp vật phẩm |
| `SPAWNER_ITEM_FILTER` | Bật/tắt bộ lọc |
| `SPAWNER_DROP_PAGE_ITEMS` | Thả mọi vật phẩm ở trang hiện tại |
| `SPAWNER_EGG_CHANGE` | Đổi mob bằng spawn egg |
| `COMMAND_EXECUTE_PLAYER` | Người chơi chạy lệnh |
| `COMMAND_EXECUTE_CONSOLE` | Console chạy lệnh |
| `COMMAND_EXECUTE_RCON` | RCON chạy lệnh |
