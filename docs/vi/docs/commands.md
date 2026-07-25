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

<div class="command-section">

<CommandRow commands="/ss give spawner &lt;player&gt; &lt;type&gt; [amount]" permission="smartspawner.command.give">
Trao Smart Spawner cho người chơi.
<ul>
<li><code>&lt;player&gt;</code>: Người chơi đích hoặc selector (<code>@p</code>, <code>@a</code>, v.v.)</li>
<li><code>&lt;type&gt;</code>: Loại mob, ví dụ <code>zombie</code>, <code>skeleton</code>, <code>blaze</code></li>
<li><code>[amount]</code>: Số lượng tùy chọn từ 1–6400, mặc định 1</li>
</ul>
</CommandRow>

<CommandRow commands="/ss give vanilla_spawner &lt;player&gt; &lt;type&gt; [amount]" permission="smartspawner.command.give">
Trao spawner Minecraft vanilla. Không GUI, không xếp chồng; hoạt động như block spawner mặc định đặt từ chế độ sáng tạo.
</CommandRow>

<CommandRow commands="/ss give item_spawner &lt;player&gt; &lt;item_type&gt; [amount]" permission="smartspawner.command.give">
Trao Item Spawner cho người chơi.
<ul>
<li><code>&lt;item_type&gt;</code>: Tên material, ví dụ <code>DIAMOND</code>, <code>EMERALD</code>, <code>NETHERITE_INGOT</code></li>
</ul>
</CommandRow>

</div>

## Lệnh Quản Trị

<div class="command-section">

<CommandRow commands="/ss reload" permission="smartspawner.command.reload">
Tải lại toàn bộ cấu hình mà không cần khởi động lại máy chủ. Áp dụng thay đổi trong <code>config.yml</code>, <code>spawners_settings.yml</code>, <code>item_spawners_settings.yml</code>, <code>item_prices.yml</code>, file ngôn ngữ và các hook tích hợp.
</CommandRow>

<CommandRow commands="/ss list" permission="smartspawner.command.list">
Mở GUI quản trị liệt kê mọi spawner. Hỗ trợ dịch chuyển đến spawner, lọc theo thế giới và xem spawner trên nhiều máy chủ ở chế độ MySQL.
</CommandRow>

<CommandRow commands="/ss hologram" permission="smartspawner.command.hologram">
Bật hoặc tắt hologram cho toàn bộ spawner.
</CommandRow>

<CommandRow commands="/ss prices" permission="smartspawner.command.prices">
Mở GUI hiển thị giá bán của mọi vật phẩm do spawner tạo. Cần tích hợp bán đang hoạt động.
</CommandRow>

<CommandRow :commands="['/ss near [radius]', '/ss near cancel']" permission="smartspawner.command.near">
Quét spawner trong bán kính đã cho (mặc định 50, tối đa 200) và đánh dấu xuyên tường bằng viền BlockDisplay phát sáng.
<ul>
<li>Quét bất đồng bộ và hiển thị tiến trình trên boss bar</li>
<li>Chỉ người chạy lệnh nhìn thấy đánh dấu</li>
<li>Tự hết hạn sau 30 giây; dùng <code>/ss near cancel</code> để xóa ngay</li>
</ul>
</CommandRow>

<CommandRow commands="/ss set &lt;property&gt; &lt;value&gt; [world x y z]" permission="smartspawner.command.set">
Đặt thuộc tính cho spawner. Nếu không có tọa độ, lệnh nhắm vào spawner người chơi đang nhìn.
<ul>
<li>Thuộc tính: <code>stack_size</code>, <code>range</code>, <code>delay</code></li>
<li><code>delay</code> nhận tick thô hoặc định dạng thời gian: <code>25s</code>, <code>1m</code>, <code>1h</code></li>
</ul>
</CommandRow>

<CommandRow :commands="['/ss language', '/ss language &lt;locale&gt;']" permission="smartspawner.command.language">
Xem hoặc đổi ngôn ngữ đang dùng. Tab-complete tên locale từ thư mục <code>language/</code>.
</CommandRow>

<CommandRow :commands="['/ss gui_layout', '/ss gui_layout &lt;layout&gt;']" permission="smartspawner.command.gui_layout">
Xem hoặc đổi bố cục GUI đang dùng. Tab-complete tên layout từ thư mục <code>gui_layouts/</code>.
</CommandRow>

<CommandRow :commands="['/ss clear holograms', '/ss clear ghost_spawners']" permission="smartspawner.command.clear">
<ul>
<li><code>holograms</code>: Xóa mọi hologram SmartSpawner. Dùng để dọn hologram bị kẹt sau crash hoặc lỗi chunk.</li>
<li><code>ghost_spawners</code>: Phát hiện và xóa bản ghi cơ sở dữ liệu của spawner không còn block thật tại vị trí đã lưu.</li>
</ul>
</CommandRow>

</div>

<style scoped>
.command-section {
  border: 1px solid var(--vp-c-border);
  border-radius: 10px;
  overflow: hidden;
  margin-top: 24px;
  background-color: var(--vp-c-bg-soft);
}
</style>
