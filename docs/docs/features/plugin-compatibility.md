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

<FeatureCard icon="ShieldCheck" title="Protections">

- WorldGuard
- GriefPrevention
- Lands
- Towny Advanced
- SimpleClaimSystem
- RedProtect
- MinePlots

</FeatureCard>

<FeatureCard icon="Globe2" title="World Management">

- Multiverse-Core
- Multiworld
- SuperiorSkyblock2
- BentoBox *(requires setup, see [BentoBox docs](https://docs.bentobox.world))*
- IridiumSkyblock

</FeatureCard>

<FeatureCard icon="Swords" title="RPG and Mobs">

- **AuraSkills**: XP from spawners counts toward skills
- **MythicMobs**: Custom mob drop tables

</FeatureCard>

</CardGrid>

## Known conflicts

These plugins can override spawner behavior and clash with SmartSpawner. If they run without the changes below, they may override SmartSpawner and cause issues.

| Plugin | Action needed |
| --- | --- |
| WildStacker | Set `spawners: enabled:` to `false` in its `config.yml`. |
| RoseStacker | Set `stacking-enabled:` to `false` in its `config.yml`. |
| SpawnerMeta | Remove or disable it. It overrides SmartSpawner features. |

See the [complete compatibility matrix](/docs/integrations/compatibility-matrix) for supported operations and setup notes.
