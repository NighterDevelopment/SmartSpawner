---
title: Protections and Claims
---

# Protections and Claims

Protection hooks are **automatic**. There is no toggle in `config.yml`. When a supported protection plugin is present, SmartSpawner asks it before three actions:

- **Open menu**: right-click a spawner to open the GUI.
- **Stack**: add spawners to a stack.
- **Break**: mine or remove a spawner.

The flag or trust level that allows each action lives inside the protection plugin, not inside SmartSpawner. Pick your plugin below for the exact settings.

::: tip Who bypasses every check
Operators and players with the `*` wildcard permission (plus `worldguard.region.bypass` for WorldGuard) skip all protection checks. Test with a regular player account.
:::

## Quick reference

| Plugin | Open menu | Stack | Break |
|---|---|---|---|
| [WorldGuard](/docs/integrations/protections/worldguard) | `interact` | `block-place` | `block-break` |
| [GriefPrevention](/docs/integrations/protections/griefprevention) | Build trust | Build trust | Build trust |
| [Lands](/docs/integrations/protections/lands) | `INTERACT_CONTAINER` | `BLOCK_PLACE` | `BLOCK_BREAK` |
| [Towny](/docs/integrations/protections/towny) | Resident / trusted | Resident / trusted | Resident / trusted |
| [Residence](/docs/integrations/protections/residence) | `use` | `build` | `build` |
| [RedProtect](/docs/integrations/protections/redprotect) | `chest` | `build` | Not checked |
| [SimpleClaimSystem](/docs/integrations/protections/simpleclaimsystem) | `InteractBlocks` / `interact_spawner` | Same | `Destroy` / spawner perms |

Outside any region or claim, every action is allowed by default.

For plot and island plugins (PlotSquared, SuperiorSkyblock2, BentoBox, and more), see [Islands and Plots](/docs/integrations/islands/).
