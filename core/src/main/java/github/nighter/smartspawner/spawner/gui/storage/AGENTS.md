# spawner/gui/storage/

The paged view of a spawner's virtual inventory: 5 rows / 45 item slots (0-44) per page plus a
control row (45-53), 54 slots total. It is the most invariant-heavy GUI in the plugin because the
inventory it shows is virtual — item counts are `long` and routinely exceed a stack — and because a
single spawner can be viewed by several players at once.

## The count-map is the source of truth

`VirtualInventory.getConsolidatedItems()` (`Map<ItemSignature, Long>`) holds the real total of every
item; the Bukkit inventory is only a **projection** of it, one page at a time. A repaint reads the
page with `virtualInv.getDisplayPage(page, 45)` (an `Int2ObjectMap<ItemStack>`), never the other way
round. Removals go through a `SpawnerData` primitive (`removeItemsAndUpdateSellValue`) that mutates
the count-map, and the page is repainted afterwards.

## Files

| File | Role |
|---|---|
| `SpawnerStorageUI` | Builds the inventory, buttons and title; owns the button caches; `updateDisplay` is the one repaint path. `reload()` / `cleanup()` / `cancelTasks()` |
| `StoragePageHolder` | `InventoryHolder` + `SpawnerHolder`: carries `SpawnerData`, `currentPage`, `totalPages`, `oldUsedSlots` and the resolved `GuiLayout`. Clamps page in its constructor and setters |
| `SpawnerStorageAction` | The `Listener`. Classifies every click, takes items to the player inventory, runs the control buttons, and queues the save on close. `plugin.getSpawnerStorageAction()` |
| `filter/` | `FilterConfigHolder` / `FilterConfigUI`: which materials the spawner is allowed to store |
| `utils/` | `ItemClickHandler`, `ItemMoveHelper`, `ItemMoveResult` — **dead code**, no live callers. Do not build on it; delete or rewrite if you touch this area |

## Click model: cancel everything, then transfer by hand

`SpawnerStorageAction.onInventoryClick` (priority `LOWEST`) **cancels every click up front**
(`event.setCancelled(true)`) and then decides what to do. There is no native item movement and no
deposit path — items only ever leave storage, and only through code:

1. `spawner.isSelling()` → `action_in_progress` and stop. First gate; it closes the sell/reopen dupe
   window (a reopen could otherwise let the same items be taken by the player and by `applySellResult`).
2. Item slot `0..44` → `handleItemSlotClick`: **left** = take 1, **right** = take half (`ceil/2`),
   **shift** = take the whole stack. The amount is copied into the player inventory by hand
   (`transferToPlayerInventory`: stack onto similar slots first, then fill empties, slots 0-35), then
   `spawner.removeItemsAndUpdateSellValue(removed)` debits exactly what was moved. A 100ms per-player
   cooldown (`click_too_fast`) throttles spam.
3. Control slot (`layout.isSlotUsed(slot)`) → `handleControlSlotClick`, gated by
   `GuiButtonInteractionService.tryUse` (cooldown + permission) and dispatched on the button's
   **action string**, not its name.
4. `onInventoryDrag` cancels any drag on a storage inventory outright.

## Control-button actions

Resolved from the layout's action string (`getActionWithFallback(clickType)`), so a button is defined
entirely in `gui_layout`:

`sort_items`, `open_filter`, `previous_page` / `next_page`, `take_all`, `drop_page`, `sell_all`,
`sell_and_exp`, `collect_exp`, `return_main`, `none`.

`take_all` and `drop_page` are page-scoped: they read the **currently displayed** slots (0-44),
fire `SpawnerTakeAllEvent` / `SpawnerDropAllEvent` (the only place these API events fire — single
takes have no event), let a listener rewrite the list, then remove that list from the count-map.
`take_all` fills the player inventory and stops when it is full; `drop_page` throws the items on the
ground in the player's facing direction. `sort_items` cycles `preferredSortItem` through the spawner's
loot materials, persists it, and calls `virtualInv.sortItems(...)`.

## Page counts — one formula, three sites

Pages come from **packed used slots**, not display geometry:
`ceil(getVirtualInventory().getUsedSlots() / 45)`, min 1. This exact formula is duplicated in
`SpawnerStorageUI.calculateTotalPages`, `SpawnerStorageAction.calculateTotalPages`, and
`StorageUpdateService.calculateTotalPages(int)`. **They must stay identical** — if they diverge you
get "page N shows N/N-1, back button stuck" bugs. `StoragePageHolder.oldUsedSlots` is a used-slot
snapshot refreshed by `updateOldUsedSlots()` after every redraw; it drives page-count change
detection and the `isAtCapacity` reset, so refresh it or the diff misfires.

## Rendering

`updateDisplay` is the single repaint path. It takes `inventoryLock.tryLock()` and **skips the tick
if the lock is busy** (the acting player and the next sync tick will repaint anyway). It clears the 45
item slots and every used button slot, then writes the page items (`getDisplayPage`) plus the enabled
buttons. There is no diff renderer — every repaint rewrites the whole page. Buttons are cached in
`SpawnerStorageUI` (static / navigation / page-indicator / sort caches) and evicted by a 30s task;
sell, sort, collect-exp and info buttons are built per repaint because they show live values.

## Cross-viewer sync (push, not polled)

Multiple players can view one spawner's storage at once — there is **no single-viewer gate** on main.
When code changes the inventory it calls `SpawnerGuiViewManager.updateSpawnerMenuViewers(spawner)`,
which schedules each storage viewer a `StorageUpdateService.processStorageUpdateDirect` on that
viewer's region thread (retitle + repaint, or close+reopen if the page count moved). Callers include
`SpawnerLootGenerator`, `SpawnerRangeChecker`, `SpawnerSellManager`, `HopperTransfer` and the actions
here. See `synchronization/AGENTS.md`.

## Persistence

Item moves call `spawner.markStorageDirty()`; the spawner is queued for saving only on **close**
(`handleInventoryClose` → `markSpawnerModified` + `clearStorageDirty`), which runs on the closing
player's region thread — do not defer it to the player scheduler, or the block may resolve on the
wrong region. Sorting also calls `queueSpawnerForSaving` directly so the preference survives.

## Gotchas

- Never read a GUI slot to decide a single-item debit. `handleItemSlotClick` debits exactly the amount it moved into the player inventory, computed from the click, not from the slot's post-click state.
- `StoragePageHolder` clamps page numbers in its constructor and setters. Do not clamp again at call sites.
- `take_all`/`drop_page` reading the display slots is safe only because they immediately remove that exact list from the count-map. Keep the read-then-remove atomic to the handler.
- Recover the spawner by casting the holder, never by parsing the localized title.
