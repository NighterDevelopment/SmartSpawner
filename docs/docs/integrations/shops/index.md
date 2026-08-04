---
title: Shops and Economy
---

# Shops and Economy

Selling from a spawner needs two independent pieces:

1. A **price source** (a supported shop plugin, or `item_prices.yml`).
2. A **currency backend** (Vault or ExcellentEconomy).

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

## Price source plugins

| Plugin | Identifier | Page |
|---|---|---|
| EconomyShopGUI | `EconomyShopGUI`, `EconomyShopGUI-Premium` | [EconomyShopGUI](/docs/integrations/shops/economyshopgui) |
| ShopGUI+ | `ShopGUIPlus` | [ShopGUI+](/docs/integrations/shops/shopguiplus) |
| zShop | `zShop` | [zShop](/docs/integrations/shops/zshop) |

## Currency backends

| Plugin | Identifier | Page |
|---|---|---|
| Vault | `VAULT` | [Vault](/docs/integrations/shops/vault) |
| ExcellentEconomy | `EXCELLENTECONOMY` | [ExcellentEconomy](/docs/integrations/shops/excellenteconomy) |

## Price source modes

| Mode | Resolution order |
|---|---|
| `SHOP_ONLY` | Supported shop price only |
| `SHOP_PRIORITY` | Shop price, then custom price |
| `CUSTOM_ONLY` | `item_prices.yml` only |
| `CUSTOM_PRIORITY` | Custom price, then shop price |

Run `/ss prices` to inspect the active providers and resolved values after setup.
