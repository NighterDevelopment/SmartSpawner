---
title: Cửa hàng và Kinh tế
---

# Cửa Hàng và Kinh Tế

Tính năng bán từ spawner cần hai phần độc lập:

1. **Nguồn giá** (một plugin cửa hàng được hỗ trợ, hoặc `item_prices.yml`).
2. **Backend tiền tệ** (Vault hoặc ExcellentEconomy).

```yaml
sell_integration:
  enabled: true
  currency: VAULT
  price_source_mode: SHOP_PRIORITY
  shop_integration:
    enabled: true
    preferred_plugin: auto
  custom_prices:
    enabled: true
    default_price: 1.0
```

## Plugin nguồn giá

| Plugin | Định danh | Trang |
|---|---|---|
| EconomyShopGUI | `EconomyShopGUI`, `EconomyShopGUI-Premium` | [EconomyShopGUI](/vi/docs/integrations/shops/economyshopgui) |
| ShopGUI+ | `ShopGUIPlus` | [ShopGUI+](/vi/docs/integrations/shops/shopguiplus) |
| zShop | `zShop` | [zShop](/vi/docs/integrations/shops/zshop) |

## Backend tiền tệ

| Plugin | Định danh | Trang |
|---|---|---|
| Vault | `VAULT` | [Vault](/vi/docs/integrations/shops/vault) |
| ExcellentEconomy | `EXCELLENTECONOMY` | [ExcellentEconomy](/vi/docs/integrations/shops/excellenteconomy) |

## Chế độ nguồn giá

| Chế độ | Thứ tự tra cứu |
|---|---|
| `SHOP_ONLY` | Chỉ giá từ cửa hàng hỗ trợ |
| `SHOP_PRIORITY` | Giá cửa hàng, sau đó giá tùy chỉnh |
| `CUSTOM_ONLY` | Chỉ `item_prices.yml` |
| `CUSTOM_PRIORITY` | Giá tùy chỉnh, sau đó giá cửa hàng |

Dùng `/ss prices` để kiểm tra provider đang hoạt động và giá đã phân giải sau khi thiết lập.
