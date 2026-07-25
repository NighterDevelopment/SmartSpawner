---
title: Ma trận tương thích
---

# Ma Trận Tương Thích

| Nhóm | Plugin | Cách SmartSpawner sử dụng |
|---|---|---|
| Giá cửa hàng | EconomyShopGUI, EconomyShopGUI Premium, ShopGUI+ | Đọc giá bán vật phẩm cho thao tác bán kho |
| Kinh tế | Vault, ExcellentEconomy | Cộng giá trị bán cuối cùng vào số dư người chơi |
| Region và claim | WorldGuard, GriefPrevention, Lands, Towny, SimpleClaimSystem, RedProtect, Residence, FactionsUUID | Kiểm tra quyền trước khi mở, xếp chồng hoặc phá |
| Plot và đảo | PlotSquared, minePlots, SuperiorSkyblock2, BentoBox, IridiumSkyblock | Tôn trọng quyền plot/đảo và quyền spawner tùy chỉnh |
| Bedrock | floodgate, Geyser-Spigot | Phát hiện người chơi Bedrock và bật trải nghiệm FormUI |
| Kỹ năng | AuraSkills | Trao XP kỹ năng đã cấu hình khi nhận kinh nghiệm spawner |
| Drop tùy chỉnh | MythicMobs | Đăng ký loại drop MythicMobs `smartspawner` |
| Tải thế giới | Multiverse-Core, MultiWorld, Worlds | Đảm bảo plugin thế giới tùy chọn tải trước SmartSpawner |

::: info Tự động phát hiện
Cài plugin tùy chọn trước khi khởi động SmartSpawner. Hook thành công sẽ được báo trong log máy chủ. Dùng `/ss reload` sau khi đổi cấu hình; khởi động lại máy chủ sau khi thêm hoặc xóa file plugin.
:::
