# Changelog

All notable changes to SmartSpawner are documented in this file.

## 1.7.1

### Added
- Added FactionsUUID support. Spawners inside faction territory now respect that faction's access rules when players open menus or stack spawners.
- Added BlockLocker support. A spawner locked with a `[Private]` sign now only lets its owner and trusted players open the menu, stack it, or break it.

### Changed
- Config and language files now stay up to date on their own. When you update the plugin, any new options are added for you and your own settings and comments are kept exactly as you left them.

### Notes
- Nothing to do when updating. The plugin no longer drops backup copies of your config files into the folder on each update, since your settings are now kept in place.
- The old `config_version` line at the top of config files is no longer needed and is removed automatically on the next start.

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
