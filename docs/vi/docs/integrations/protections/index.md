---
title: Bảo vệ và Claim
---

# Bảo Vệ và Claim

Hook bảo vệ hoạt động **tự động**, không có công tắc trong `config.yml`. Khi có plugin bảo vệ được hỗ trợ, SmartSpawner sẽ hỏi plugin đó trước ba thao tác:

- **Mở menu**: chuột phải vào spawner để mở GUI.
- **Xếp chồng**: thêm spawner vào chồng.
- **Phá**: đào hoặc gỡ spawner.

Flag hoặc mức trust quyết định từng thao tác nằm bên trong plugin bảo vệ, không nằm trong SmartSpawner. Chọn plugin của bạn bên dưới để xem cấu hình chính xác.

::: tip Ai bỏ qua mọi kiểm tra
Operator và người chơi có quyền wildcard `*` (cùng với `worldguard.region.bypass` cho WorldGuard) bỏ qua toàn bộ kiểm tra bảo vệ. Hãy thử bằng tài khoản người chơi thường.
:::

## Tra cứu nhanh

| Plugin | Mở menu | Xếp chồng | Phá |
|---|---|---|---|
| [WorldGuard](/vi/docs/integrations/protections/worldguard) | `interact` | `block-place` | `block-break` |
| [GriefPrevention](/vi/docs/integrations/protections/griefprevention) | Build trust | Build trust | Build trust |
| [Lands](/vi/docs/integrations/protections/lands) | `INTERACT_CONTAINER` | `BLOCK_PLACE` | `BLOCK_BREAK` |
| [Towny](/vi/docs/integrations/protections/towny) | Cư dân / trusted | Cư dân / trusted | Cư dân / trusted |
| [Residence](/vi/docs/integrations/protections/residence) | `use` | `build` | `build` |
| [RedProtect](/vi/docs/integrations/protections/redprotect) | `chest` | `build` | Không kiểm tra |
| [SimpleClaimSystem](/vi/docs/integrations/protections/simpleclaimsystem) | `InteractBlocks` / `interact_spawner` | Như trên | `Destroy` / quyền phá |

Bên ngoài mọi region hoặc claim, tất cả thao tác đều được cho phép mặc định.

Với các plugin plot và đảo (PlotSquared, SuperiorSkyblock2, BentoBox, v.v.), xem [Đảo và Plot](/vi/docs/integrations/islands/).
