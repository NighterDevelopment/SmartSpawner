---
title: Compatibility Matrix
---

# Compatibility Matrix

| Category | Plugins | What SmartSpawner uses |
|---|---|---|
| Shop prices | EconomyShopGUI, EconomyShopGUI Premium, ShopGUI+ | Reads item sell prices for the storage sell action |
| Economy | Vault, ExcellentEconomy | Deposits the final sale value into the player's balance |
| Regions and claims | WorldGuard, GriefPrevention, Lands, Towny, SimpleClaimSystem, RedProtect, Residence, FactionsUUID | Checks access before opening, stacking, or breaking where supported |
| Plots and islands | PlotSquared, minePlots, SuperiorSkyblock2, BentoBox, IridiumSkyblock | Respects plot/island access and custom spawner permissions |
| Bedrock | floodgate, Geyser-Spigot | Detects Bedrock players and enables the FormUI experience |
| Skills | AuraSkills | Awards configured skill XP when spawner experience is claimed |
| Custom drops | MythicMobs | Registers the `smartspawner` MythicMobs drop type |
| World loading | Multiverse-Core, MultiWorld, Worlds | Ensures optional world plugins load before SmartSpawner |

::: info Automatic detection
Install optional plugins before starting SmartSpawner. A successful hook is reported in the server log. Run `/ss reload` after configuration changes; restart the server after adding or removing plugin jars.
:::
