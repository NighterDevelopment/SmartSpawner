# spawner/gui/synchronization/services/

The three services `SpawnerGuiViewManager` drives. All rendering runs on the viewer's region thread —
`GuiUpdateService` and `TimerUpdateService` are called from the (possibly async) periodic task and
must hop with `Scheduler.runLocationTask` before touching an inventory; `StorageUpdateService` is
called with a thread already chosen by the facade.

## GuiUpdateService — batched main-menu redraws

Main-menu updates are coalesced. `scheduleUpdate(uuid, flags)` records a pending player and a bitmask
(`UPDATE_CHEST | UPDATE_INFO | UPDATE_EXP`, or `UPDATE_ALL`). `processPendingUpdates` drains the set
once per tick, hops to each player's region thread, confirms the open inventory is still a
`SpawnerMenuHolder`, rebuilds only the flagged buttons, and calls `player.updateInventory()` **only if
a slot actually changed** (`areItemsEqual` = same amount + `isSimilar`). So a mutation marks a redraw
and lets the batch flush it — do not rebuild or reopen an inventory per change; that fights the
batching. Offline players are untracked here.

## StorageUpdateService — storage page repaints

`processStorageUpdateDirect(viewer, inv, spawner, holder, oldPages, newPages)` runs on the viewer's
region thread (the facade schedules it from `updateSpawnerMenuViewers`). If the page count is
unchanged it repaints the current page (`SpawnerStorageUI.updateDisplay`) and refreshes
`oldUsedSlots`. If the count changed it retitles and repaints, falling back to close+reopen if
`setTitle` throws. `calculateTotalPages(int)` here is the same `ceil(items / 45)` used everywhere else
in storage — keep it identical (see `storage/AGENTS.md`). There is no version gate: a repaint is only
scheduled when something already changed the spawner.

## TimerUpdateService — countdown + loot driver

Renders the `{time}` countdown on the main-menu info item and, through `LootPreGenerationHelper` (see
`utils/AGENTS.md`), triggers loot pre-generation and early-add as a spawner's timer nears zero. It
short-circuits entirely when the active layout has no `{time}` placeholder (`shouldProcessTimerUpdates`
/ `hasTimerPlaceholders`, re-checked on reload by `recheckTimerPlaceholders`). Updates are throttled
per player, capped (`MAX_PLAYERS_PER_BATCH = 10`) and skipped when the value is unchanged, so a room
full of menu viewers stays cheap. `forceStateChangeUpdate` clears the per-player cache to force an
immediate refresh when spawner state (active/full) flips.

## Gotchas

- Never touch an inventory straight from the task thread. Wrap it in `Scheduler.runLocationTask(viewer.getLocation(), …)` and re-validate `isOnline()` and the holder type inside, exactly as these services do — a viewer can close or move between scheduling and running.
- Loot timing lives here (via `TimerUpdateService` → `LootPreGenerationHelper`), not in `SpawnerLootGenerator`. Do not add a competing time-based trigger.
- `GuiUpdateService.areItemsEqual` is the guard that stops needless `updateInventory()` calls. Keep the "only flush on real change" check when adding a new button type.
