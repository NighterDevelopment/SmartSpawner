---
title: Islands and Plots
---

# Islands and Plots

Like [protections](/docs/integrations/protections/), island and plot hooks are automatic. SmartSpawner asks the plugin before a player opens the menu, stacks, or breaks a spawner.

::: tip Who bypasses every check
Operators and players with the `*` wildcard permission skip all checks. Test with a regular player account.
:::

## Quick reference

| Plugin | Open menu | Stack | Break |
|---|---|---|---|
| [PlotSquared](/docs/integrations/islands/plotsquared) | Added / trusted to plot | Added / trusted to plot | Added / trusted to plot |
| [minePlots](/docs/integrations/islands/mineplots) | Plot access | Plot access | Plot access |
| [SuperiorSkyblock2](/docs/integrations/islands/superiorskyblock2) | `spawner_open_menu` | `spawner_stack` | Not checked |
| [BentoBox](/docs/integrations/islands/bentobox) | `CONTAINER` | `PLACE_BLOCKS` | Not checked |
| [IridiumSkyblock](/docs/integrations/islands/iridiumskyblock) | `SpawnerOpenMenuPermission` | `SpawnerStackPermission` | Not checked |

Outside any plot or island, every action is allowed by default.
