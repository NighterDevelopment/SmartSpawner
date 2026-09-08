# spawner/config/

Parsing the two spawner-content files into the runtime loot tables and the cage previews. This is where
a `spawner_mobs.yml` / `spawner_items.yml` entry becomes an `EntityLootConfig` full of prebuilt
`LootItem` templates. Consumed by `spawner/lootgen/`.

| File | Role |
|---|---|
| `SpawnerSettingsConfig` | Loads `spawner_mobs.yml` (mob loot tables + base exp). Legacy name `spawners_settings.yml` |
| `ItemSpawnerSettingsConfig` | Loads `spawner_items.yml` (item-spawner definitions). Legacy name `item_spawners_settings.yml` |
| `LootEntryParser` | One config entry → a `LootItem` (amount range, chance, durability range, price) |
| `ConfiguredItemParser` | Builds the entry's template `ItemStack` from `item:` (material, `/give` component syntax, or `nbt:` + Base64) |
| `SpawnerMobHeadTexture` | The player-head textures used for mob icons and item-spawner heads in GUIs |
| `SpawnerDisplayConfigurator` | The vanilla cage preview (`EntitySnapshot` for mobs, captured item for item spawners) |
| `SpawnerConfigName` | `normalize()` — canonicalizes a user-facing type name once so lookups are O(1) |
| `SupersededConfigNotice` | Console notice, once, that a settings file was renamed in 1.8.0 |

## Templates are resolved here, once

`ConfiguredItemParser` turns the `item:` field into a finished `ItemStack` at load time, and
`LootEntryParser` wraps it in a `LootItem`. Nothing downstream re-inspects the item to decide what to
build — a new item property is expressed entirely in the config's `item:` value. Only a durability
*range* is left to roll per drop; a single value is baked into the template. See `../lootgen/AGENTS.md`.

Because the template carries a `sellPrice` read from the price manager, `ItemPriceManager` must be
constructed **before** these configs load (`initializeEconomyComponents` before spawner settings).

## The rename that is not a migration

The spawner settings files were renamed in 1.8.0 and their contents are deliberately **not** carried
across: the new files ship in a format the old ones cannot express, so copying an old file would import
loot entries that no longer parse. `SupersededConfigNotice` just tells the operator, once, that the old
file (`spawners_settings.yml` / `item_spawners_settings.yml`) was left untouched on disk for them to
read by hand. It changes nothing. This is separate from the version-less config top-up in `updates/`.

## The owned-section trap

`SpawnerSettingsConfig.load()` passes `path -> path.endsWith(".loot")` as `YamlMigrator.OwnedSection`,
so the shipped file's relabelled default entries are not added next to a user's existing ones — without
it, `addMissingKeys` would silently double every drop. **Keep that argument** if you touch the load
call. `spawner_items.yml` has no migrator, so it only affects fresh installs. See `../../updates/AGENTS.md`.

## Gotchas

- `spawner_mobs.yml` / `spawner_items.yml` are the current files; `spawners_settings.yml` / `item_spawners_settings.yml` are the superseded names. Load code references the current ones as `RESOURCE`, the old ones as `LEGACY_RESOURCE`.
- Cage previews must be applied everywhere a spawner is materialized (item metadata, placed blocks, DB restore, reloads) via `SpawnerDisplayConfigurator`, or the preview silently loses entity NBT / item components.
- Any code that branches on spawner kind must handle item spawners (`EntityType.ITEM`); see `../AGENTS.md`.
