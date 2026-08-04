---
title: Câu hỏi thường gặp
description: Giải đáp các thắc mắc thường gặp về cảnh báo Spawner, reload, hỗ trợ Bedrock và placeholder trong SmartSpawner.
---

# Câu hỏi thường gặp

## Vì sao tôi thấy cảnh báo dưới Spawner?

![Cảnh báo hiển thị cho người chơi OP dưới spawner](/op-warning.png)

Mojang thêm cảnh báo này trong Minecraft 1.21.4. Nó chỉ hiện với người chơi là OP, và không có cách nào để tắt.

::: tip Tìm hiểu thêm
[Ghi chú phát hành Minecraft Java Edition 1.21.4](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4)
:::

## Reload SmartSpawner có an toàn không?

Hãy dùng `/smartspawner reload`. Lệnh này áp dụng an toàn hầu hết thay đổi trong file cấu hình.

Tránh các cách không an toàn sau:

- `/reload` làm hỏng plugin và gây ra hành vi bất thường.
- PlugMan load, reload hoặc unload có thể khiến SmartSpawner hoạt động sai.

Để cài đặt hoặc cập nhật SmartSpawner, hãy khởi động lại máy chủ sau khi thay đổi hoặc thêm plugin.

::: tip Tìm hiểu thêm
[Vấn đề với /reload](https://madelinemiller.dev/blog/problem-with-reload/)
:::

## Vì sao menu spawner không mở với người chơi Bedrock?

Đây là hạn chế hiện tại của Geyser, lớp tương thích Bedrock. Geyser không phân biệt được click chuột trái và click chuột phải từ client Bedrock, khác với Java Edition.

Vì vậy, khi đang cầm một công cụ như cúp, xẻng hoặc rìu, menu spawner sẽ không mở. Cú click không được đọc theo cách SmartSpawner mong đợi.

::: tip Tìm hiểu thêm
[Các hạn chế hiện tại của Geyser](https://geysermc.org/wiki/geyser/current-limitations/)
:::

## Tôi vừa cập nhật SmartSpawner và %placeholder% ngừng hoạt động

Định dạng placeholder đã đổi từ `%placeholder%` sang `{placeholder}` ở phiên bản [1.5.5](https://modrinth.com/plugin/smart-spawner-plugin/version/1.5.5).

Bạn khắc phục bằng một trong hai cách sau.

**Tạo lại file ngôn ngữ**

1. Xóa thư mục `/language/`.
2. Khởi động lại máy chủ. File mới sẽ được tạo tự động.

**Chuyển đổi thủ công**

1. Mở file ngôn ngữ bằng trình soạn thảo như VSCode hoặc Notepad++.
2. Mở Find and Replace (CTRL+H).
3. Tìm: `%(.+?)%`
4. Thay bằng: `{$1}`

Không có công cụ chuyển đổi tự động, vì vậy hãy dùng một trong hai cách trên.
