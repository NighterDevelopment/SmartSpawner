---
title: Nhật ký thao tác
---

# Nhật Ký Thao Tác

SmartSpawner có thể ghi lại mọi thao tác spawner quan trọng: ai làm, ở đâu và khi nào. Dùng để truy vết và kiểm duyệt. Nhật ký có hai đầu ra độc lập là file log cục bộ và Discord, mỗi đầu ra bật hoặc tắt riêng.

Tất cả được cấu hình trong `activity_log.yml`.

## File log

File log được ghi vào thư mục `logs` bên trong thư mục plugin. Phần này nằm trong mục `file`.

| Tùy chọn | Mặc định | Chức năng |
|----------|----------|-----------|
| `enabled` | `true` | Bật hoặc tắt hệ thống ghi file log. |
| `json_format` | `false` | `false` ghi dạng văn bản dễ đọc. `true` ghi dạng JSON có cấu trúc. |
| `console_output` | `false` | Đồng thời in sự kiện ra console máy chủ. Hữu ích khi gỡ lỗi. |
| `max_log_files` | `10` | Số file log xoay vòng được giữ lại. File cũ nhất bị xóa trước. |
| `max_log_size_mb` | `10` | Dung lượng (MB) một file log đạt tới trước khi xoay sang file mới. |
| `log_all_events` | `false` | Ghi mọi sự kiện. Khi `true`, danh sách `logged_events` bị bỏ qua. |
| `logged_events` | danh sách | Các sự kiện cụ thể được ghi khi `log_all_events` là `false`. |

## Ghi log qua Discord

SmartSpawner có thể gửi cùng các sự kiện đó tới một kênh Discord thông qua webhook. Phần này nằm trong mục `discord`, nên bạn có thể chạy ghi log Discord dù có bật ghi file hay không.

### Thiết lập webhook

1. Trong Discord, mở phần cài đặt máy chủ.
2. Vào Integrations, rồi Webhooks, rồi New Webhook.
3. Chọn kênh đích và sao chép URL webhook.
4. Trong mục `discord`, đặt `enabled` thành `true` và dán URL vào `webhook_url`.

### Tùy chọn

| Tùy chọn | Mặc định | Chức năng |
|----------|----------|-----------|
| `enabled` | `false` | Bật hoặc tắt gửi log qua Discord. |
| `webhook_url` | trống | URL webhook của Discord. Bắt buộc khi bật. |
| `show_player_head` | `true` | Hiển thị đầu nhân vật Minecraft của người chơi làm thumbnail của tin nhắn. |
| `log_all_events` | `false` | Chuyển tiếp mọi sự kiện. Khi `true`, danh sách `logged_events` bị bỏ qua. |
| `logged_events` | danh sách | Các sự kiện cụ thể được chuyển tiếp khi `log_all_events` là `false`. |

### Hình thức tin nhắn

Mục `embeds` chứa một khối cho mỗi sự kiện. Bạn có thể đổi tiêu đề, màu sắc, mô tả và các trường, đồng thời dùng placeholder như `{player}`, `{location}`, `{entity}` và `{time}` để điền chi tiết của từng sự kiện. Sự kiện không có khối ở đó vẫn được gửi, dưới dạng tin nhắn đơn giản.

## Các sự kiện có sẵn

Cả hai đầu ra dùng chung tên sự kiện.

**Spawner:** `SPAWNER_PLACE`, `SPAWNER_BREAK`, `SPAWNER_EXPLODE`, `SPAWNER_STACK_HAND`, `SPAWNER_STACK_GUI`, `SPAWNER_DESTACK_GUI`, `SPAWNER_EGG_CHANGE`

**GUI:** `SPAWNER_GUI_OPEN`, `SPAWNER_STORAGE_OPEN`, `SPAWNER_STACKER_OPEN`

**Kho và bán:** `SPAWNER_EXP_CLAIM`, `SPAWNER_SELL_ALL`, `SPAWNER_ITEM_TAKE_ALL`, `SPAWNER_ITEM_DROP`, `SPAWNER_ITEMS_SORT`, `SPAWNER_ITEM_FILTER`, `SPAWNER_DROP_PAGE_ITEMS`

**Lệnh:** `COMMAND_EXECUTE_PLAYER`, `COMMAND_EXECUTE_CONSOLE`, `COMMAND_EXECUTE_RCON`

## Nâng cấp từ phiên bản cũ

Bạn không cần làm gì. Ở lần khởi động đầu tiên, `discord_logging.yml` được đổi tên thành `activity_log.yml` và mục `logging` trong `config.yml` được chuyển vào đó. Các thiết lập của bạn được giữ nguyên.
