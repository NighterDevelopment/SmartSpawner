---
title: ExcellentEconomy
---

# ExcellentEconomy

ExcellentEconomy can be used directly without Vault.

## Configuration

Set the currency identifier to an existing ExcellentEconomy currency ID:

```yaml
sell_integration:
  enabled: true
  currency: EXCELLENTECONOMY
  excellenteconomy_currency: money
```

The `excellenteconomy_currency` value is case-sensitive and must match the configured ExcellentEconomy currency ID exactly. SmartSpawner disables selling if it cannot resolve that currency.
