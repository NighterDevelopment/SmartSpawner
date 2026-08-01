---
title: Hỗ trợ cơ sở dữ liệu
---

# Hỗ Trợ Cơ Sở Dữ Liệu

Dữ liệu spawner được lưu trong cơ sở dữ liệu. Chọn chế độ phù hợp với máy chủ của bạn:

| Chế độ | Trường hợp sử dụng |
|--------|--------------------|
| `SQLITE` | Một máy chủ. Là một file cục bộ, không cần cài gì thêm. Mặc định |
| `MYSQL` | Nhiều máy chủ, hoặc máy chủ lớn đã có sẵn MySQL hay MariaDB |

Đặt trong `database.type` ở `config.yml`.

Danh sách spawner liên máy chủ có sẵn trong chế độ `MYSQL` qua `/ss list`.

::: warning RESTART
Mọi tùy chọn trong mục `database`, trừ `autosave-interval`, chỉ được đọc lúc máy chủ khởi động.
`/ss reload` không áp dụng chúng.
:::

## Chuyển sang MySQL

1. Đặt `database.type` thành `MYSQL`.
2. Điền `database.host`, `database.port`, `database.database`, `database.username` và `database.password`.
3. Đặt `database.server-name` khác nhau cho từng máy chủ.
4. Khởi động lại. Dữ liệu SQLite được chuyển sang trong lần chạy đầu tiên.

## Dữ liệu được lưu bao lâu một lần

Thay đổi của spawner được gom lại và ghi theo lô thay vì ghi từng cái một.
`database.autosave-interval` quyết định khoảng cách giữa hai lần ghi, mặc định `3m` và tối thiểu
`30s`. Dữ liệu cũng được lưu khi thế giới lưu và khi máy chủ tắt, nên khoảng thời gian này là lớp bảo
vệ chứ không phải lần lưu duy nhất.

Tăng lên trên máy chủ đông người để giảm ghi đĩa. Giảm xuống để thu hẹp lượng hoạt động gần nhất có
thể mất nếu máy chủ sập. Đây là tùy chọn duy nhất trong mục này mà `/ss reload` áp dụng được.

## Chuyển từ YAML

Chế độ lưu trữ YAML đã bị bỏ. Nếu `config.yml` của bạn vẫn để `YAML`, plugin sẽ tự đổi sang `SQLITE`
trong lần khởi động kế tiếp và nhập toàn bộ dữ liệu từ `spawners_data.yml`. File cũ được đổi tên
thành `spawners_data.yml.migrated` để không bị nhập lại lần nữa. Bạn không cần làm gì thêm.

## Tên bảng

Plugin tạo hai bảng, đặt tên theo `database.table-prefix`: `sspawner_data` và
`sspawner_schema_meta`. Hãy đổi tiền tố khi có plugin khác đã dùng tên đó trong cùng cơ sở dữ liệu,
hoặc để tách hai bản cài SmartSpawner trong cùng một cơ sở dữ liệu MySQL. Bảng đang có sẽ được đổi
tên tự động khi tiền tố thay đổi.

::: tip
Hãy sao lưu thư mục `plugins/SmartSpawner/` trước khi cập nhật, như với mọi bản cập nhật có động đến
dữ liệu đã lưu.
:::
