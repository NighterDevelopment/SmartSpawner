---
title: Cửa hàng và Kinh tế
---

# Cửa Hàng và Kinh Tế

Tính năng bán cần hai phần riêng biệt:

1. **Nguồn giá** từ plugin cửa hàng được hỗ trợ hoặc `item_prices.yml`.
2. **Backend tiền tệ** từ Vault hoặc ExcellentEconomy.

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

## EconomyShopGUI

Định danh plugin được hỗ trợ:

- `EconomyShopGUI`
- `EconomyShopGUI-Premium`

Với `preferred_plugin: auto`, SmartSpawner kiểm tra EconomyShopGUI trước và dùng giá bán đã cấu hình. Để chọn rõ ràng:

```yaml
sell_integration:
  shop_integration:
    enabled: true
    preferred_plugin: EconomyShopGUI
```

## ShopGUI+

SmartSpawner đọc giá bán ShopGUI+ và đăng ký listener tương thích spawner khi plugin có mặt.

```yaml
sell_integration:
  shop_integration:
    enabled: true
    preferred_plugin: ShopGUIPlus
```

## zShop

Provider zShop đã có nhưng chưa được chọn bởi luồng tự động phát hiện hiện tại. Hãy xem đây là tính năng thử nghiệm và chọn thủ công:

```yaml
sell_integration:
  shop_integration:
    enabled: true
    preferred_plugin: zShop
```

Xác nhận log khởi động báo provider zShop đang hoạt động trước khi cho người chơi bán. Nếu không, hãy dùng giá tùy chỉnh.

## Vault

Vault kết nối SmartSpawner với provider kinh tế máy chủ đang sử dụng.

```yaml
sell_integration:
  currency: VAULT
```

Vault không phải plugin kinh tế độc lập. Một plugin kinh tế phải đăng ký dịch vụ Vault, nếu không tính năng bán vẫn bị tắt.

## ExcellentEconomy

ExcellentEconomy có thể dùng trực tiếp không cần Vault. Đặt định danh tiền tệ thành một currency đã tồn tại:

```yaml
sell_integration:
  currency: EXCELLENTECONOMY
  excellenteconomy_currency: money
```

Định danh phân biệt chữ hoa chữ thường theo ID currency của ExcellentEconomy. SmartSpawner tắt bán nếu không tìm thấy currency đó.

## Chế Độ Nguồn Giá

| Chế độ | Thứ tự tra cứu |
|---|---|
| `SHOP_ONLY` | Chỉ giá từ cửa hàng hỗ trợ |
| `SHOP_PRIORITY` | Giá cửa hàng, sau đó giá tùy chỉnh |
| `CUSTOM_ONLY` | Chỉ `item_prices.yml` |
| `CUSTOM_PRIORITY` | Giá tùy chỉnh, sau đó giá cửa hàng |

Dùng `/ss prices` để kiểm tra provider đang hoạt động và giá đã phân giải sau khi thiết lập.
