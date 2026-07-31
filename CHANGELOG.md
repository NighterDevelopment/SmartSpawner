# Changelog

All notable changes to SmartSpawner are documented in this file.

## 1.8.0

### Added
- Added `/ss config mobs` and `/ss config items`. Both open an in-game editor for the matching settings file, so mob drops and item spawners can be set up without opening a text editor. Changes are written to the file straight away and applied without a restart.
- In the editor you can change experience, spawner drop chance and head texture, add, edit and remove loot, and create or delete whole entries. Numbers are set with sliders, and an item is added by dropping the real item into the window, which keeps custom items from other plugins exactly as they are.
- Loot entries in `spawner_mobs.yml` and `spawner_items.yml` can now name their item with an `item` line. It accepts a plain material, the same item text the `/give` command completes for you in game, or a code copied straight out of the game. Potions, enchanted gear, named items and custom items all work as spawner drops now.
- Because the item is named on its own line, the name above it is just a label. The bundled files number them 1, 2, 3, but any text works, and one mob can now drop several versions of the same item, such as two different tipped arrows.
- A loot entry the server cannot read is now skipped with a console message naming the mob and the entry, instead of being dropped silently. Turning on `debug` also reports what each `item` line produced.

### Fixed
- Items stored in a spawner now keep everything about them. Enchantments, custom names, lore and durability used to be lost every time the server restarted, so an enchanted sword came back as a plain one. Items are now saved exactly as they are.
- Spawner storage no longer breaks past roughly 2.1 billion of a single item. Very large stacked spawners kept the correct count.
- The total item count shown in `/ss list` is now exact. It was previously an estimate.

### Changed
- The two spawner settings files were renamed. `spawners_settings.yml` is now `spawner_mobs.yml` and `item_spawners_settings.yml` is now `spawner_items.yml`, so they sit next to each other in the folder and say plainly what they hold.
- Every loot entry now names its item on an `item` line. The old style, where the entry name doubled as the material name, is no longer read.
- The head shown on a spawner block is now set under `mob_head`, with `item` for the material and `hash_texture` for the texture code. It was `head_texture` with `material` and `custom_texture`.
- The guide that used to sit in comments at the top of both settings files was removed. The files now link to the documentation site instead, which is kept up to date.
- SQLite is now the default storage mode, and it is faster than the old YAML files on servers of any size.
- Servers on MySQL or MariaDB keep working as before, including cross-server spawner listing.
- SQLite now handles reading and saving at the same time, so opening `/ss list` no longer waits for a save to finish.

### Removed
- Item spawner entries no longer repeat their own name in a `material` line. It served no purpose, and an entry where the two disagreed used to be skipped without producing that item spawner at all.
- The `default_material` line was removed from both settings files. It was only ever a fallback for a head that does not exist, so it is now built in and there is nothing to configure.
- The `potion_type` line in loot entries was removed. Name the potion inside `item` instead, as the bundled files now do.
- YAML storage was removed. Servers still set to `YAML` are switched to `SQLITE` automatically.

### Notes
- **Your spawner settings are not carried over.** The two renamed files are created fresh with the new format, and your old `spawners_settings.yml` and `item_spawners_settings.yml` are left untouched beside them so you can copy your own changes across by hand. The console says so on the first start. If you had customised drop tables, plan for that before updating.
- Spawner data itself is migrated automatically. Only the two settings files above need manual work.
- Your spawners are imported into the new storage on the first start, and your old `spawners_data.yml` is renamed to `spawners_data.yml.migrated` so nothing is imported twice.
- Back up your `plugins/SmartSpawner/` folder before updating, as with any update that touches saved data. The plugin also makes its own copy of the old data inside the database before converting it.
- SmartSpawner now downloads the SQLite driver on first start, so the server needs internet access that one time. It is cached afterwards.
- If the plugin cannot open its database, it now refuses to start instead of running without saving. Check the console for the reason.

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
