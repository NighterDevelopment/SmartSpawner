---
title: ExcellentEconomy
---

# ExcellentEconomy

**Tải về:** [Modrinth](https://modrinth.com/plugin/excellenteconomy)

ExcellentEconomy có thể dùng trực tiếp mà không cần Vault.

## Cấu hình

Đặt định danh tiền tệ thành một currency ID đã tồn tại trong ExcellentEconomy:

```yaml
sell_integration:
  enabled: true
  currency: EXCELLENTECONOMY
  excellenteconomy_currency: money
```

Giá trị `excellenteconomy_currency` phân biệt chữ hoa chữ thường và phải khớp chính xác với currency ID đã cấu hình trong ExcellentEconomy. SmartSpawner tắt bán nếu không tìm thấy currency đó.
