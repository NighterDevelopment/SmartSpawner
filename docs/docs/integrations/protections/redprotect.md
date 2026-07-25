---
title: RedProtect
---

# RedProtect

SmartSpawner checks RedProtect region permissions for two of the three actions.

| Action | Permission |
|---|---|
| Open menu | `chest` (canChest) |
| Stack | `build` (canBuild) |
| Break | Not checked by this hook |

## Notes

- Opening the menu needs the region's **chest** permission.
- Stacking needs the region's **build** permission.
- Breaking is not gated by the RedProtect hook. RedProtect's own block-break protection still applies through the normal Bukkit events, so a player who cannot break blocks in the region still cannot break the spawner.

Default flags can be edited in `plugins/RedProtect/<world>/config.yml` and the flag files. Use `/rp flag info` to inspect a region.
