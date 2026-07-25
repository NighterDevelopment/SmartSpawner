---
title: Protections and Islands
---

# Protections, Plots, and Islands

Protection hooks are automatic. There is no separate toggle in `config.yml`.

## Regions and Claims

| Plugin | Protected operations |
|---|---|
| WorldGuard | Region interaction, stacking, and breaking |
| GriefPrevention | Claim access for opening, stacking, and breaking |
| Lands | Container interaction, stacking, and breaking |
| Towny | Spawner interaction inside towns |
| SimpleClaimSystem 1.x and 2.x | Claim access for opening, stacking, and breaking |
| RedProtect | Opening and stacking |
| Residence | Opening, stacking, and breaking |

Operators and players with the wildcard permission bypass these checks. Test with a regular player account before opening the server to players.

## Plots and Islands

| Plugin | Behavior |
|---|---|
| PlotSquared | Uses plot interaction access for opening, stacking, and breaking |
| minePlots | Checks plot access for opening, stacking, and breaking |
| SuperiorSkyblock2 | Registers `spawner_stack` and `spawner_open_menu` island privileges; cleans tracked spawners when an island is disbanded |
| BentoBox | Uses the island `PLACE_BLOCKS` and `CONTAINER` flags |
| IridiumSkyblock | Adds `SpawnerStackPermission` and `SpawnerOpenMenuPermission` to the island permission system |

IridiumSkyblock labels are stored in:

```text
plugins/SmartSpawner/language/<locale>/iridium_skyblock.yml
```
