---
title: GriefPrevention
---

# GriefPrevention

SmartSpawner yêu cầu **Build trust** cho mọi thao tác spawner, kể cả chỉ mở menu. Container trust và access trust là **chưa đủ**.

| Thao tác | Yêu cầu |
|---|---|
| Mở menu | Build trust |
| Xếp chồng | Build trust |
| Phá | Build trust |

## Cấp quyền

```bash
/trust <player>
```

`/trust` cấp build trust đầy đủ, bao gồm mở, xếp chồng và phá spawner.

## Các lệnh không đủ

- `/containertrust` cho phép rương và container, nhưng không mở được GUI của SmartSpawner.
- `/accesstrust` chỉ cho phép nút bấm và cửa.

Bên ngoài mọi claim, tất cả thao tác đều được cho phép.
