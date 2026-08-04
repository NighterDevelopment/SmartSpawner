# Changelog

All notable changes to SmartSpawner are documented in this file.

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
