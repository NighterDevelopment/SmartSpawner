---
title: Shops and Economy
---

# Shops and Economy

Selling requires two separate pieces:

1. A **price source**, supplied by a supported shop plugin or `item_prices.yml`.
2. A **currency backend**, supplied by Vault or ExcellentEconomy.

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

Supported plugin identifiers:

- `EconomyShopGUI`
- `EconomyShopGUI-Premium`

With `preferred_plugin: auto`, SmartSpawner checks EconomyShopGUI first and uses its configured sell prices. To select it explicitly:

```yaml
sell_integration:
  shop_integration:
    enabled: true
    preferred_plugin: EconomyShopGUI
```

## ShopGUI+

SmartSpawner reads ShopGUI+ sell prices and registers its spawner compatibility listener when the plugin is present.

```yaml
sell_integration:
  shop_integration:
    enabled: true
    preferred_plugin: ShopGUIPlus
```

## zShop

The zShop provider exists but is not selected by the current automatic-detection path. Treat it as experimental and select it explicitly:

```yaml
sell_integration:
  shop_integration:
    enabled: true
    preferred_plugin: zShop
```

Confirm the startup log reports an active zShop provider before allowing players to sell. If it does not, use custom prices until compatibility is restored.

## Vault

Vault connects SmartSpawner to the economy provider already used by your server.

```yaml
sell_integration:
  currency: VAULT
```

Vault alone is not an economy. An economy plugin must register a Vault economy service, or selling remains disabled.

## ExcellentEconomy

ExcellentEconomy can be used directly without Vault. Set the currency identifier to an existing ExcellentEconomy currency:

```yaml
sell_integration:
  currency: EXCELLENTECONOMY
  excellenteconomy_currency: money
```

The identifier is case-sensitive according to the configured ExcellentEconomy currency ID. SmartSpawner disables selling if it cannot resolve that currency.

## Price Source Modes

| Mode | Resolution order |
|---|---|
| `SHOP_ONLY` | Supported shop price only |
| `SHOP_PRIORITY` | Shop price, then custom price |
| `CUSTOM_ONLY` | `item_prices.yml` only |
| `CUSTOM_PRIORITY` | Custom price, then shop price |

Use `/ss prices` to inspect the active providers and resolved values after setup.
