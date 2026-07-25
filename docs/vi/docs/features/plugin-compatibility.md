---
title: Tương thích plugin
---

# Tương Thích Plugin

SmartSpawner tự phát hiện plugin tùy chọn và chỉ bật những tích hợp có trên máy chủ.

<FeatureMediaCard icon="Gamepad2" title="Form UI Bedrock" image="https://cdn.modrinth.com/data/9tQwxSFr/images/df8098897c88f1d02f8d26b70f4834c705cfe2fb.webp" alt="Form UI Bedrock hiện tại" link="/vi/docs/integrations/bedrock-support" action="Đọc hướng dẫn Bedrock">
Người chơi Floodgate và Geyser nhận menu thân thiện với cảm ứng thay vì chest GUI khi bật `bedrock_support.enable_formui`.
</FeatureMediaCard>

## Các Tích Hợp Được Hỗ Trợ

<CardGrid>

<FeatureCard icon="ShieldCheck" title="Bảo Vệ">

- WorldGuard
- GriefPrevention
- Lands
- Towny Advanced
- SimpleClaimSystem
- RedProtect
- MinePlots

</FeatureCard>

<FeatureCard icon="Globe2" title="Quản Lý Thế Giới">

- Multiverse-Core
- Multiworld
- SuperiorSkyblock2
- BentoBox *(cần thiết lập, xem [tài liệu BentoBox](https://docs.bentobox.world))*
- IridiumSkyblock

</FeatureCard>

<FeatureCard icon="Swords" title="RPG và Mob">

- **AuraSkills**: XP từ spawner được tính vào kỹ năng
- **MythicMobs**: Bảng vật phẩm mob tùy chỉnh

</FeatureCard>

</CardGrid>

## Xung Đột Đã Biết

Các plugin sau có thể ghi đè hành vi spawner và xung đột với SmartSpawner. Nếu chạy mà không thực hiện thay đổi bên dưới, chúng có thể ghi đè SmartSpawner và gây lỗi.

| Plugin | Việc cần làm |
| --- | --- |
| WildStacker | Đặt `spawners: enabled:` thành `false` trong `config.yml` của nó. |
| RoseStacker | Đặt `stacking-enabled:` thành `false` trong `config.yml` của nó. |
| SpawnerMeta | Gỡ hoặc tắt plugin. Nó ghi đè các tính năng của SmartSpawner. |

Xem [ma trận tương thích đầy đủ](/vi/docs/integrations/compatibility-matrix) để biết thao tác hỗ trợ và lưu ý thiết lập.
