# spawner/gui/synchronization/listeners/

Two Bukkit listeners that keep `ViewerTrackingManager` in sync with who actually has a spawner GUI
open. Both run at `MONITOR` (they observe, they do not cancel) and are registered and unregistered by
`SpawnerGuiViewManager`.

## InventoryEventListener

On `InventoryOpenEvent` it matches the holder against an exact-class set — `SpawnerMenuHolder`,
`StoragePageHolder`, `FilterConfigHolder` — maps it to a `ViewerType` (`MAIN_MENU` / `STORAGE` /
`FILTER`), tracks the viewer, and fires the `onViewerAdded` callback that starts the periodic task if
it is not already running. On `InventoryCloseEvent` it untracks the viewer.

Two things are load-bearing:

- **`updateLastInteractedPlayer` is called on open, not on close.** `InventoryCloseEvent` is not
  guaranteed to fire before `PlayerQuitEvent` on a disconnect, so recording the interacting player at
  open time is what stops that attribution being lost.
- **Holder matching is by exact class** (`validHolderTypes.contains(holder.getClass())`), not
  `instanceof`. A new spawner GUI needs its holder added here or it will never be tracked or synced.

There is **no single-viewer gate** — several players may hold the same spawner's storage open at once,
and the storage GUI is written to be safe under that (see `storage/AGENTS.md`).

## PlayerEventListener

Untracks the player on `PlayerQuitEvent`. This is the backstop for the disconnect case where close did
not fire; it does not re-record the interacting player because open already did.

## Gotchas

- Keep both at `MONITOR`. Tracking must reflect the final open/close outcome, and these listeners must never influence whether the event succeeds.
- If you add a spawner GUI, register its holder in `InventoryEventListener.validHolderTypes` **and** give it a `ViewerType`, or live sync silently skips it.
