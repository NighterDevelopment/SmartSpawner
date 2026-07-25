---
title: Đảo và Plot
---

# Đảo và Plot

Giống [bảo vệ](/vi/docs/integrations/protections/), hook đảo và plot hoạt động tự động. SmartSpawner hỏi plugin trước khi người chơi mở menu, xếp chồng hoặc phá spawner.

::: tip Ai bỏ qua mọi kiểm tra
Operator và người chơi có quyền wildcard `*` bỏ qua toàn bộ kiểm tra. Hãy thử bằng tài khoản người chơi thường.
:::

## Tra cứu nhanh

| Plugin | Mở menu | Xếp chồng | Phá |
|---|---|---|---|
| [PlotSquared](/vi/docs/integrations/islands/plotsquared) | Được add / trust vào plot | Được add / trust vào plot | Được add / trust vào plot |
| [minePlots](/vi/docs/integrations/islands/mineplots) | Có quyền plot | Có quyền plot | Có quyền plot |
| [SuperiorSkyblock2](/vi/docs/integrations/islands/superiorskyblock2) | `spawner_open_menu` | `spawner_stack` | Không kiểm tra |
| [BentoBox](/vi/docs/integrations/islands/bentobox) | `CONTAINER` | `PLACE_BLOCKS` | Không kiểm tra |
| [IridiumSkyblock](/vi/docs/integrations/islands/iridiumskyblock) | `SpawnerOpenMenuPermission` | `SpawnerStackPermission` | Không kiểm tra |

Bên ngoài mọi plot hoặc đảo, tất cả thao tác đều được cho phép mặc định.
