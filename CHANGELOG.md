# Changelog

All notable changes to SmartSpawner are documented in this file.

## 1.7.1.2

### Fixed
- Buttons you move to another slot in a GUI layout now stay moved. Changing `slot_15` to `slot_17` used to leave a second copy of the button at `slot_15` on the next start or `/ss reload`. Buttons you delete stay deleted for the same reason.
- Clicking the sell button when the storage is empty no longer sends you back to the main menu. If the spawner still holds experience you now collect it and stay where you are, and if it holds nothing at all you get the storage empty message instead.
- Fresh installations no longer report `applied value migrations` for every bundled `sell_confirm_gui.yml` on the first `/ss reload`. The shipped layouts now use the current nested click-action format.
- Storage titles in the DonutSMP language presets now use the singular `Spawner` for a stack size of one and the plural `Spawners` for larger stacks. The correct title is also kept when changing pages or refreshing an open storage.
- Dynamic placeholders in storage button lore are now replaced correctly. This fixes `{total_sell_price}` when combining a DonutSMP layout with the `en_US` language, and also fixes `{current_exp}` in the collect EXP button.

### Notes
- If a button already came back before this update, move or delete it once more and it will stay put.
- The buttons in a layout file are now yours alone. When a future update adds a new button, it will not appear in your file, so copy it in by hand if you want it. The settings around the buttons, such as `skip_main_gui` and `open_sound`, are still filled in for you.
- To start a layout over, delete the file and restart. It comes back exactly as it ships, comments included.

## 1.7.1.1

### Fixed
- Drops you remove from a mob in `spawners_settings.yml` now stay removed. They were being added back every time the server started or you ran `/ss reload`.
- Parts you remove from a message now stay removed. Deleting `message` to leave only `action_bar` used to bring the chat line back on the next start, so players got the message twice, once in chat and once on the action bar. Deleting `sound`, `title` or `subtitle` had the same problem.

### Removed
- The plugin no longer writes `language/CHANGELOG.txt`. It listed the language keys each version added or changed, and was not being kept current.

### Notes
- If a drop or a message part already came back before this update, delete it once more after updating and it will stay gone. Removing a whole `loot` block for a mob works too.
- The old `language/CHANGELOG.txt` is left where it is rather than deleted for you. Nothing reads it, so remove it whenever you like.
- The drop list of every mob already in your file is now yours alone. When a future update adds a new drop to one of those mobs, it will not appear in your file, so add it by hand if you want it. A mob that is completely new to your file still arrives with its full drop list.
- Messages work the same way. A message you have already edited keeps exactly the parts you gave it. A message that is new, or one you deleted entirely, is still written into your file in full, so the plugin never asks for a message that is not there.
- Only `messages.yml` and `command_messages.yml` changed. `gui.yml`, `command_gui.yml` and `formatting.yml` are still filled in as before.

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
