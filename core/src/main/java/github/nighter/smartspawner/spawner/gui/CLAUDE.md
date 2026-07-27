# spawner/gui/

Every inventory GUI attached to a spawner, plus the machinery that keeps open GUIs in sync while the
spawner keeps producing loot. The list/management/prices GUIs reached from commands live in
`commands/list/` and `commands/prices/` instead, but they follow the same holder convention.

## The three-part pattern

Every GUI here is three classes. Follow it when adding one.

| Part | Naming | Job |
|---|---|---|
| Holder | `*Holder` | Implements `InventoryHolder` **and** `SpawnerHolder`. Carries the `SpawnerData` and view state (page, layout). No logic |
| UI | `*UI` | Builds the `Inventory` and its ItemStacks. No click handling |
| Action / Handler | `*Action` or `*Handler` | The `Listener`. Reads the holder, mutates state |

`SpawnerHolder` exists so any click handler can recover the spawner from
`event.getInventory().getHolder()`. **Never** identify a GUI by its title, and never keep a
player-to-spawner map as the primary lookup: titles are localized and a map desyncs. Cast the holder.

Current GUIs:

| Directory | Holder | Purpose |
|---|---|---|
| `main/` | `SpawnerMenuHolder` | The main spawner menu. `SpawnerMenuFormUI` is the Bedrock/Floodgate variant |
| `storage/` | `StoragePageHolder` | Paged view of the virtual inventory, 5 rows / 45 items per page |
| `storage/filter/` | `FilterConfigHolder` | Which materials the spawner accepts |
| `stacker/` | `SpawnerStackerHolder` | Changing stack size |
| `sell/` | `SpawnerSellConfirmHolder` | Sell confirmation, skippable via layout config |

`SpawnerMenuFormUI` is only constructed when Floodgate is present and
`bedrock_support.enable_formui` is true, so it is **nullable**. Null-check it; `initializeFormUIComponents()`
also re-runs on reload.

## Layouts are data, not code

Slots, materials, names and click actions come from YAML in `plugins/SmartSpawner/gui_layouts/<name>/`,
shipped as `core/src/main/resources/gui_layouts/` (`default`, `DonutSMP`, `DonutSMP_v2`). Three files
per layout: `main_gui.yml`, `storage_gui.yml`, `sell_confirm_gui.yml`.

`layout/GuiLayoutConfig` loads the active layout (config key `gui_layout`) into three `GuiLayout`
objects. A `GuiLayout` is a bidirectional map: button type name to `GuiButton`, and slot number to
button type. So a click handler resolves intent by slot:

```java
layout.getButtonTypeAtSlot(slot).ifPresent(type -> ...)
```

Do not hardcode slot numbers in handlers. A hardcoded slot works on `default` and breaks on every
other layout.

Behaviour flags also come from the layout file, not `config.yml`: `skip_main_gui` and `open_sound`
from `main_gui.yml`, `skip_sell_confirmation` from `sell_confirm_gui.yml`.

Addons can supply layouts through the `api/` module: `ExternalGuiLayoutLoader` plus
`GuiLayoutRegistryImpl`, with `SpawnerGuiLayoutProvider` able to override per-player. Adding a new
button type means touching the shipped YAML for all three layouts, `GuiButton` parsing, and
`updates/GuiLayoutUpdater` so existing servers get the key.

`GuiButtonInteractionService` is a registered `Listener` that handles the layout-declared click
actions and sounds generically. Prefer declaring an action in YAML over adding a hardcoded branch.

## Live synchronization

`synchronization/SpawnerGuiViewManager` is a facade. It **registers its own listeners in its
constructor**, which is why `SmartSpawner.registerListeners()` skips it. Do not register it again.

Internals:

| Class | Role |
|---|---|
| `managers/ViewerTrackingManager` | Who is looking at which spawner |
| `managers/UpdateTaskManager` | The repeating task, started only when a viewer exists and stopped when the last one leaves |
| `services/TimerUpdateService` | Countdown display, and triggers loot generation when a timer expires |
| `services/GuiUpdateService` | Batched redraws of pending updates |
| `services/StorageUpdateService` | Storage-page specific refresh |
| `listeners/InventoryEventListener`, `listeners/PlayerEventListener` | Add and remove viewers |
| `utils/LootPreGenerationHelper`, `utils/TimerFormatter` | Helpers |

Consequences to respect:

- Loot generation is **driven from here**, not from a global ticker. `TimerUpdateService` decides when `SpawnerLootGenerator` runs. Do not add a second trigger.
- Updates are batched. Mark a spawner as needing a redraw and let `GuiUpdateService` flush it; calling `player.openInventory` or rebuilding an inventory per change fights the batching.
- The update task self-stops with zero viewers. Anything that adds a viewer outside `InventoryEventListener` must go through `ViewerTrackingManager` or the task never starts.
- Viewers must be cleaned up on quit and on inventory close, or the manager holds stale `Player` references.

## Caching and reload

Several UIs cache built ItemStacks, and the reload work is **split across two places**. See the root
`CLAUDE.md`: `ReloadSubCommand.reloadAll` runs the ordered chain and calls `SmartSpawner.reload()`
last. A new cache needs a line in whichever of the two matches its dependencies.

In `ReloadSubCommand`, before `plugin.reload()`:

- `GuiLayoutConfig.loadLayout()` then `GuiButtonInteractionService.clear()`, deliberately **before** the UIs that read the layout
- `SpawnerMenuUI.loadConfig()`
- `SpawnerClickManager.loadConfig()`
- `SpawnerGuiViewManager.recheckTimerPlaceholders()`, after the language reload, to notice GUI text changes

In `SmartSpawner.reload()`:

- `GuiLayoutConfig.reloadLayouts()`
- `SpawnerMenuUI.clearCache()`
- `SpawnerStorageUI.reload()` (and `cleanup()` on disable)
- `FilterConfigUI.reload()`
- `SpawnerSellConfirmUI.reload()`
- `SpawnerStorageAction.loadConfig()`
- `SpawnerMenuAction.reload()`
- `SpawnerMobHeadTexture.clearCache()`, paired with `prewarmCache()` at startup so default heads do not flash on first open
- `initializeFormUIComponents()`, which rebuilds or nulls the Bedrock FormUI

## Gotchas

- `SpawnerStackerHandler` is 1141 lines and `SpawnerStorageAction` is 916. Read the region around your edit.
- `StoragePageHolder` clamps page numbers in its setters. Do not clamp again at call sites.
- `oldUsedSlots` on the holder is a change-detection snapshot; refresh it with `updateOldUsedSlots()` after a redraw or the diff logic misfires.
- `storage/utils/ItemMoveHelper.moveItems` fills the player inventory and reports what fit as an `ItemMoveResult`. It takes **no locks and does not check `isSelling()`**, and it does not remove anything from the `VirtualInventory` (the `virtualInv` parameter is currently unused). The caller owns `inventoryLock`, the sell guard, and the removal from virtual storage.
- GUI text comes from `LanguageManager.gui()` / `commandGui()`. See `language/CLAUDE.md`.
