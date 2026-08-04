---
title: Plugin Compatibility
---

# Plugin Compatibility

SmartSpawner detects optional plugins and enables only the integrations available on your server.

<FeatureMediaCard
  icon="Gamepad2"
  title="Bedrock Form UI"
  image="https://cdn.modrinth.com/data/9tQwxSFr/images/df8098897c88f1d02f8d26b70f4834c705cfe2fb.webp"
  alt="Current SmartSpawner Bedrock Form UI"
  link="/docs/integrations/bedrock-support"
  action="Read the Bedrock setup guide"
>
Floodgate and Geyser players receive a touch-friendly menu instead of a chest GUI when `bedrock_support.enable_formui` is enabled.
</FeatureMediaCard>

## Supported integrations

<CardGrid>

<FeatureCard icon="ShieldCheck" title="Protections and Claims">

- [WorldGuard](/docs/integrations/protections/worldguard)
- [GriefPrevention](/docs/integrations/protections/griefprevention)
- [Lands](/docs/integrations/protections/lands)
- [Towny Advanced](/docs/integrations/protections/towny)
- [SimpleClaimSystem](/docs/integrations/protections/simpleclaimsystem)
- [RedProtect](/docs/integrations/protections/redprotect)
- [Residence](/docs/integrations/protections/residence)
- [FactionsUUID](/docs/integrations/protections/factionsuuid)
- [BlockLocker](/docs/integrations/protections/blocklocker)

</FeatureCard>

<FeatureCard icon="Home" title="Islands and Plots">

- [PlotSquared](/docs/integrations/islands/plotsquared)
- [minePlots](/docs/integrations/islands/mineplots)
- [SuperiorSkyblock2](/docs/integrations/islands/superiorskyblock2)
- [BentoBox](/docs/integrations/islands/bentobox) *(requires setup, see [BentoBox docs](https://docs.bentobox.world))*
- [IridiumSkyblock](/docs/integrations/islands/iridiumskyblock)

</FeatureCard>

<FeatureCard icon="ShoppingCart" title="Shops and Economy">

- [EconomyShopGUI](/docs/integrations/shops/economyshopgui) *(free and Premium)*
- [ShopGUI+](/docs/integrations/shops/shopguiplus)
- [zShop](/docs/integrations/shops/zshop)
- [Vault](/docs/integrations/shops/vault)
- [ExcellentEconomy](/docs/integrations/shops/excellenteconomy)

</FeatureCard>

<FeatureCard icon="Globe2" title="World Management">

- Multiverse-Core
- MultiWorld
- Worlds

</FeatureCard>

<FeatureCard icon="Swords" title="RPG and Mobs">

- **[AuraSkills](/docs/integrations/auraskills)**: XP from spawners counts toward skills
- **[MythicMobs](/docs/integrations/mythicmobs)**: Custom mob drop tables

</FeatureCard>

</CardGrid>

## Known conflicts

These plugins can override spawner behavior and clash with SmartSpawner. If they run without the changes below, they may override SmartSpawner and cause issues.

| Plugin | Action needed |
| --- | --- |
| WildStacker | Set `spawners: enabled:` to `false` in its `config.yml`. |
| RoseStacker | Set `stacking-enabled:` to `false` in its `config.yml`. |
| SpawnerMeta | Remove or disable it. It overrides SmartSpawner features. |
