# spawner/gui/synchronization/managers/

Two small state holders behind `SpawnerGuiViewManager`. No Bukkit events, no rendering — just
tracking and task lifecycle. Both are thread-safe for Folia.

## ViewerTrackingManager

Who is viewing which spawner, categorized by `ViewerType` (`MAIN_MENU`, `STORAGE`, `FILTER`). Backed
by `ConcurrentHashMap`s:

- `playerToSpawnerMap` / `spawnerToPlayersMap` — every viewer, both directions. `trackViewer` /
  `untrackViewer` keep them consistent and drop empty sets.
- `mainMenuViewers` / `spawnerToMainMenuViewers` — main-menu viewers tracked separately because only
  they need timer updates.
- `spawnerToFilterViewersMap` — filter-GUI viewers tracked separately so they can be force-closed to
  prevent a duplication exploit.

Each viewer carries a `ViewerInfo` (its `SpawnerData` + type). The lookups the rest of the subsystem
relies on: `getViewers` (online `Player`s), `getViewerIds`, `getViewerInfo`,
`getMainMenuViewersForSpawner`, `getFilterViewersForSpawner`, `hasViewers`, `hasAnyViewers`,
`hasMainMenuViewers`, `clearAll`.

`untrackViewer` must remove the player from **every** map its type could be in (general + main-menu +
filter); the method does this from the stored `ViewerInfo`, so always untrack through it rather than
editing a single map.

## UpdateTaskManager

Owns the single periodic update task (20-tick interval, 20-tick initial delay). `startTask` (idempotent,
`synchronized`) launches it via `Scheduler.runTaskTimer`; `stopTask` cancels it; `isRunning` reports
state. `SpawnerGuiViewManager` starts it when the first viewer appears and stops it when none remain,
so an idle server runs no GUI task at all.

## Gotchas

- These maps are the whole picture of "who is watching". A viewer left in them after close/quit leaks a repaint every tick — always go through `untrackViewer`, which the listeners already call on close and quit.
- `startTask`/`stopTask` are `synchronized` and guard on `isTaskRunning`; keep that guard or a race can start two tasks or leave one orphaned.
