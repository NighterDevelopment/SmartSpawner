# Changelog

All notable changes to SmartSpawner are documented in this file.

## 1.8.0

### Added
- Custom items can now be spawner drops. Potions, enchanted gear, named items and items from other plugins all work.
- Added `/ss config spawnerloot`, an in-game editor for mob drops and item spawners. Changes apply immediately, without a restart.
- Loot is added in the editor by dropping the real item into the window. Experience, drop chance and head texture are set there too.

### Fixed
- Items stored in a spawner keep their enchantments, custom names, lore and durability across restarts.
- The total item count in `/ss list` is now exact instead of an estimate.

### Changed
- SQLite is now the default storage mode and is faster than the old YAML files on servers of any size. MySQL and MariaDB work as before, including cross-server spawner listing.
- SQLite reads and saves at the same time, so `/ss list` no longer waits for a save to finish.
- How often spawner data is saved can now be set with `database.autosave-interval`, which defaults to 3 minutes. It was previously fixed at 5 minutes.
- Selling is now in its own file, `sell_integration.yml`. It replaces the `sell_integration` section of `config.yml` and `item_prices.yml`, and existing settings and prices move across on the first start.
- Action logging is now in one file, `activity_log.yml`. It replaces `discord_logging.yml` and the `logging` section of `config.yml`, and existing settings move across on the first start.
- The `database` section of `config.yml` was simplified. Every setting sits at one level and the connection pool has a single size option.
- Settings that only take effect after a full restart are now marked RESTART in `config.yml`.
- Bedrock form menus are off by default. Set `bedrock_support.enable_formui` to `true` and restart to use them.
- The two spawner settings files were renamed, and loot entries use a new format. See the details below.

### Removed
- YAML storage was removed. Servers still set to `YAML` are switched to `SQLITE` automatically.

### Notes
- **Spawner settings are not carried over.** The two renamed files are created fresh in the new format, and the old files are left untouched beside them so customised drop tables can be copied across by hand. The console reports this on the first start.
- Spawner data is migrated automatically. `spawners_data.yml` is renamed to `spawners_data.yml.migrated` so nothing is imported twice.
- Back up the `plugins/SmartSpawner/` folder before updating. The plugin also copies the old data inside the database before converting it.
- If the database cannot be opened, the plugin stops instead of running without saving. The console reports the reason.
- Every renamed config key is migrated on the first start, so no config file needs editing by hand after updating.

<details>
<summary>Configuration file details</summary>

- `spawners_settings.yml` is now `spawner_mobs.yml`, and `item_spawners_settings.yml` is now `spawner_items.yml`.
- Each loot entry names its item on an `item` line. It accepts a material, the item text `/give` completes in game, or a code copied from the game.
- The name above each entry is now only a label, so one mob can drop several versions of the same item, such as two different tipped arrows.
- The old format, where the entry name doubled as the material name, is no longer read. The `material` line in item spawner entries was removed with it.
- The `potion_type` line was removed. Name the potion inside `item` instead.
- The spawner block head moved to `mob_head`, with `item` for the material and `hash_texture` for the texture code. It replaces `head_texture` with `material` and `custom_texture`.
- The `default_material` line was removed. The fallback head is now built in.
- A loot entry the server cannot read is skipped, and the console names the mob and the entry.
- The guide in the comments at the top of both files was replaced with a link to the documentation site.
- `discord_logging.yml` is now `activity_log.yml`, and the `logging` section of `config.yml` became its `file` section. Both moves happen on the first start and keep the configured values.
- Inside that file the Discord settings sit under `discord`, and the per-event message templates sit under `embeds`, one block per event with the `embed` line removed.
- The `sell_integration` section of `config.yml` became the top level of `sell_integration.yml`, so its keys lost the `sell_integration.` prefix. `item_prices.yml` became the `custom_prices.prices` section of that file and is deleted once its prices have been copied across.
- A price removed from `custom_prices.prices` stays removed and is not added back on the next start.
- The `database` section was flattened and its keys now use hyphens. `mode` is `type`, `table_prefix` is `table-prefix`, `server_name` is `server-name`, `sync_across_servers` is `sync-across-servers`, `migrate_from_local` is `migrate-from-local`, `sqlite.file` is `sqlite-file`, and the four `sql` connection keys moved up a level.
- `sql.pool.maximum-size` is now `pool-size` and covers both storage modes. The other pool tuning keys and `sqlite.pool_size` were removed and are set internally.
- The new `database.autosave-interval` accepts the usual time format, with a minimum of 30 seconds. It is the only setting in the section that `/ss reload` applies.
- Database columns were shortened: `world_name` is `world`, `item_spawner_material` is `itemspawner_type`, `spawner_exp` is `exp`, `spawner_active` is `active`, `spawner_range` is `activation_range`, `spawner_stop` is `stop`, `spawn_delay` is `delay`, and `max_spawner_loot_slots` is `max_loot_slots`. `entity_type` keeps its name.
- Columns are now grouped by subject in the table, so location, spawner type, stacking, spawning, stored loot, stored experience and player settings each sit together.
- The `server_name` column was removed. A table now holds one server's spawners and says which in its name, so the column held the same value on every row.
- With `sync-across-servers` on, this server's table is `<prefix><server-name>_data` instead of `<prefix>data`. Switching the setting renames the table, and an existing table at the target name is never overwritten.

</details>

## 1.7.1.2

### Fixed
- GUI layout button changes now persist correctly after a restart or `/ss reload`, including moved and deleted buttons.
- Clicking the sell button in an empty storage no longer returns to the main menu. Stored experience is collected when available; otherwise, the storage empty message is shown.
- Updated the bundled `sell_confirm_gui.yml` layouts to the current click-action format, preventing unnecessary value migrations on fresh installations.
- DonutSMP storage titles now use the correct singular or plural form and remain consistent after pagination or refreshes.
- Dynamic storage lore placeholders, including `{total_sell_price}` and `{current_exp}`, are now replaced correctly.

### Notes
- Previously restored buttons must be moved or deleted once after updating.
- Customized button sections no longer receive new bundled buttons automatically. Other layout settings continue to update normally.
- Delete a layout file and restart the server to restore its default content.

## 1.7.1.1

### Fixed
- Removed mob drops in `spawners_settings.yml` are no longer restored after a restart or `/ss reload`.
- Removed message components, such as chat messages, action bars, sounds, titles, and subtitles, are no longer restored automatically.

### Removed
- Removed generation of the outdated `language/CHANGELOG.txt` file.

### Notes
- Previously restored drops or message components must be removed once after updating.
- Customized drop lists and messages are preserved, while entirely new mobs and messages are still added with their default content.
- Existing `language/CHANGELOG.txt` files are left unchanged and can be removed safely.
- GUI and formatting language files retain their previous update behavior.

## 1.7.1

### Added
- Added FactionsUUID support. Spawners now respect faction access rules when opening menus or stacking.
- Added BlockLocker support. Spawners protected by `[Private]` signs now restrict menu access, stacking, and breaking to authorized players.

### Changed
- Config and language files now update automatically while preserving existing values and comments.
- Update backups are no longer created because configuration changes are applied in place.
- Obsolete `config_version` entries are now removed automatically.

### Notes
- No manual configuration changes are required.

## 1.7.0.2

### Fixed
- Fixed a crash that could happen when breaking a vanilla spawner that had no mob type set. This no longer throws an error and the spawner now breaks normally.
- Fixed an error that could appear in the console when a Bedrock player (via Floodgate/Geyser) opened a spawner menu or storage on Folia based servers. Menus now open reliably for these players.
- Removed the "Server Version Not Supported" warning message. It was showing up incorrectly on newer supported server versions and is no longer needed.
- Update notifications will no longer mention SmartSpawner2 versions (2.0.0 and above). SmartSpawner2 is a separate product, so 1.x servers will only be notified about relevant 1.x updates.

### Added
- Re-added RedProtect support. Spawners inside RedProtect regions now respect region permissions again when opening menus or stacking spawners.

### Notes
- This is a maintenance release focused on stability. No configuration changes are required to update.
