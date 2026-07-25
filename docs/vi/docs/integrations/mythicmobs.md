---
title: MythicMobs
---

# MythicMobs

SmartSpawner đăng ký drop MythicMobs tên `smartspawner`. Dùng nó trong bảng drop MythicMobs để tạo vật phẩm Smart Spawner:

```yaml
ExampleDrops:
  Drops:
    - smartspawner ZOMBIE 1
    - smartspawner SKELETON 1-3
```

Cú pháp:

```text
smartspawner <ENTITY_TYPE> [amount|minimum-maximum]
```

Entity phải là loại Bukkit hợp lệ. Tên hoặc khoảng giá trị không hợp lệ sẽ bị từ chối và ghi vào log.
