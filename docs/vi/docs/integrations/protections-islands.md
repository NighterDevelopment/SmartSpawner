---
title: Bảo vệ và Đảo
---

# Bảo Vệ, Plot và Đảo

Hook bảo vệ hoạt động tự động, không có công tắc riêng trong `config.yml`.

## Region và Claim

| Plugin | Thao tác được bảo vệ |
|---|---|
| WorldGuard | Tương tác region, xếp chồng và phá |
| GriefPrevention | Quyền claim khi mở, xếp chồng và phá |
| Lands | Tương tác container, xếp chồng và phá |
| Towny | Tương tác spawner trong thị trấn |
| SimpleClaimSystem 1.x và 2.x | Quyền claim khi mở, xếp chồng và phá |
| RedProtect | Mở và xếp chồng |
| Residence | Mở, xếp chồng và phá |

Operator và người chơi có quyền wildcard sẽ bỏ qua các kiểm tra này. Hãy thử bằng tài khoản người chơi thường trước khi mở máy chủ.

## Plot và Đảo

| Plugin | Hành vi |
|---|---|
| PlotSquared | Dùng quyền tương tác plot khi mở, xếp chồng và phá |
| minePlots | Kiểm tra quyền plot khi mở, xếp chồng và phá |
| SuperiorSkyblock2 | Đăng ký đặc quyền đảo `spawner_stack` và `spawner_open_menu`; dọn spawner được theo dõi khi đảo giải tán |
| BentoBox | Dùng flag đảo `PLACE_BLOCKS` và `CONTAINER` |
| IridiumSkyblock | Thêm `SpawnerStackPermission` và `SpawnerOpenMenuPermission` vào hệ thống quyền đảo |

Nhãn IridiumSkyblock được lưu tại:

```text
plugins/SmartSpawner/language/<locale>/iridium_skyblock.yml
```
