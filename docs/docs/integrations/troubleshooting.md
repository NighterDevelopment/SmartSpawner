---
title: Integration Troubleshooting
---

# Integration Troubleshooting

## The integration is not detected

1. Confirm the optional plugin is enabled with `/plugins`.
2. Check that its plugin identifier matches the name in [Plugin Compatibility](/docs/plugin-compatibility).
3. Restart the server instead of using Bukkit `/reload`.
4. Search the startup log for `integration enabled successfully` or the provider-specific warning.

## The Sell button is hidden

Confirm all of the following:

- `enabled` is `true` in `sell_integration.yml`.
- A currency backend is available.
- The selected price mode has at least one valid price source.
- The player has `smartspawner.sellall`.

## Players can interact where they should not

Protection plugins use their own flags and membership systems. Verify the same player can or cannot perform the equivalent container, place, or break action according to that plugin, then test SmartSpawner again without operator status.
