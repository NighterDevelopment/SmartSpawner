---
title: Hỗ trợ cơ sở dữ liệu
---

# Hỗ Trợ Cơ Sở Dữ Liệu

Dữ liệu spawner được lưu trong cơ sở dữ liệu. Chọn chế độ phù hợp với máy chủ của bạn:

| Chế độ | Trường hợp sử dụng |
|--------|--------------------|
| `SQLITE` | Một máy chủ. Là một file cục bộ, không cần cài gì thêm. Mặc định |
| `MYSQL` | Nhiều máy chủ, hoặc máy chủ lớn đã có sẵn MySQL hay MariaDB |

Đặt trong `database.mode` ở `config.yml`.

Danh sách spawner liên máy chủ có sẵn trong chế độ `MYSQL` qua `/ss list`.

## Chuyển sang MySQL

1. Đặt `database.mode` thành `MYSQL`.
2. Điền `database.sql` với host, port, tên đăng nhập và mật khẩu của bạn.
3. Đặt `database.server_name` khác nhau cho từng máy chủ.
4. Khởi động lại. Dữ liệu SQLite được chuyển sang trong lần chạy đầu tiên.

## Chuyển từ YAML

Chế độ lưu trữ YAML đã bị bỏ. Nếu `config.yml` của bạn vẫn để `YAML`, plugin sẽ tự đổi sang `SQLITE`
trong lần khởi động kế tiếp và nhập toàn bộ dữ liệu từ `spawners_data.yml`. File cũ được đổi tên
thành `spawners_data.yml.migrated` để không bị nhập lại lần nữa. Bạn không cần làm gì thêm.

::: tip
Hãy sao lưu thư mục `plugins/SmartSpawner/` trước khi cập nhật, như với mọi bản cập nhật có động đến
dữ liệu đã lưu.
:::
