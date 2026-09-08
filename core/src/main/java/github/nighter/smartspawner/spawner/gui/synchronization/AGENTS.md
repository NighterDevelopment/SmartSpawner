# spawner/gui/synchronization/

Keeps every open spawner GUI in step with a `SpawnerData` that other players and the loot loop are
changing, and drives the `{time}` countdown and loot pre-generation. `SpawnerGuiViewManager` is the
facade; everything else here is a collaborator it constructs and owns.

## Composition

`SpawnerGuiViewManager` wires:

| Kind | Class | Job |
|---|---|---|
| manager | `managers/ViewerTrackingManager` | Who is viewing what, by GUI type. See `managers/AGENTS.md` |
| manager | `managers/UpdateTaskManager` | Start/stop the single periodic task |
| service | `services/GuiUpdateService` | Batched main-menu redraws |
| service | `services/StorageUpdateService` | Storage page repaints + retitle |
| service | `services/TimerUpdateService` | `{time}` countdown + loot trigger. See `services/AGENTS.md` |
| listener | `listeners/InventoryEventListener` | Track viewers on open/close. See `listeners/AGENTS.md` |
| listener | `listeners/PlayerEventListener` | Untrack on quit |

## One shared task, three update paths

`UpdateTaskManager` runs **one** repeating task (20 ticks / 1s), started when the first viewer opens a
GUI and stopped when the last one leaves (`onViewerAdded` / the tail of `processPeriodicUpdates`).
Each tick it drains batched main-menu updates and, if timer placeholders are in use, runs timer
updates. The three ways a viewer gets refreshed are deliberately different:

- **Main menu — batched pull.** A mutation calls `updateSpawnerMenuViewers`, which
  `guiUpdateService.scheduleUpdate(uuid, flags)`. The task drains the pending set once per tick and
  repaints only the flagged buttons. Coalesces bursts into one redraw.
- **Storage — pushed.** `updateSpawnerMenuViewers` also finds each viewer whose open inventory is a
  `StoragePageHolder` for this spawner and schedules `StorageUpdateService.processStorageUpdateDirect`
  immediately on that viewer's region thread. There is no per-tick storage polling and no version gate.
- **Timer — periodic.** `TimerUpdateService.processTimerUpdates` runs from the task itself, throttled
  and capped per player.

## `updateSpawnerMenuViewers` is the entry point

External code (`SpawnerLootGenerator`, `SpawnerRangeChecker`, `SpawnerSellManager`, `HopperTransfer`,
the GUI actions) triggers sync through this one method after changing a spawner. It invalidates the
menu cache, schedules the batched main-menu redraw, and pushes storage repaints. Do not reach past it
into the services.

## Folia

Every repaint hops to the viewer's region thread with `Scheduler.runLocationTask(viewer.getLocation(),
…)` and re-validates `isOnline()` and the holder type **inside** the hop — a viewer can close or move
between scheduling and running. The periodic task may run async; never touch an inventory straight
from it.

## Lifecycle

`cleanup()` (plugin disable) stops the task, clears all tracking, and
`HandlerList.unregisterAll(...)` both listeners to avoid a leak on reload/disable.
`closeAllViewersInventory` force-closes both storage and filter viewers of a spawner (the filter GUI
is included to close a duplication exploit) — used when a spawner is removed.
