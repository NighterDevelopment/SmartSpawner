---
title: Khắc phục sự cố tích hợp
---

# Khắc Phục Sự Cố Tích Hợp

## Không Phát Hiện Tích Hợp

1. Xác nhận plugin tùy chọn đang bật bằng `/plugins`.
2. Kiểm tra định danh plugin khớp tên trong [Tương thích plugin](/vi/docs/plugin-compatibility).
3. Khởi động lại máy chủ thay vì dùng Bukkit `/reload`.
4. Tìm `integration enabled successfully` hoặc cảnh báo provider trong log khởi động.

## Nút Bán Bị Ẩn

Xác nhận mọi điều kiện:

- `sell_integration.enabled` là `true`.
- Có backend tiền tệ hoạt động.
- Chế độ nguồn giá đã chọn có ít nhất một nguồn giá hợp lệ.
- Người chơi có `smartspawner.sellall`.

## Người Chơi Tương Tác Ở Nơi Không Được Phép

Plugin bảo vệ dùng flag và hệ thống thành viên riêng. Xác minh cùng người chơi đó có thể hoặc không thể thực hiện thao tác container, đặt hay phá tương đương theo plugin bảo vệ, sau đó thử SmartSpawner lại khi không có quyền operator.
