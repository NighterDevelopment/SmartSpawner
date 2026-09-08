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
| `SpawnerStorageUI` | Builds the inventory, buttons and title; loads page slots from `StorageSession` (falling back to `VirtualInventory.getDisplayPage`); owns button caches |
| `StoragePageHolder` | `InventoryHolder` + `SpawnerHolder`: carries `SpawnerData`, `currentPage`, `totalPages`, `oldUsedSlots` and the resolved `GuiLayout`. Clamps page in its constructor and setters |
| `SpawnerStorageAction` | The `Listener`. Handles native clicks, reconciles page diffs via `isSimilar`, manages control buttons, and handles session lifecycle on close |
| `session/StorageSession` | Ephemeral session holding page slot arrays (`ItemStack[]`) during active viewing; preserves slot gaps, syncs real-time loot/hopper changes, and defers compacting |
| `session/StorageSessionManager` | Tracks active `StorageSession`s per spawner; handles lifecycle and safe cleanup on shutdown |
| `filter/` | `FilterConfigHolder` / `FilterConfigUI`: which materials the spawner is allowed to store |
| `utils/` | `ItemClickHandler`, `ItemMoveHelper`, `ItemMoveResult` — **dead code**, no live callers. Do not build on it; delete or rewrite if you touch this area |

## Click model: native take with diff reconciliation

`SpawnerStorageAction.onInventoryClick` allows native Minecraft interactions for taking items while
protecting control buttons and spawner integrity:

1. `spawner.isSelling()` → cancel click, send `action_in_progress` and stop.
2. Control slots `45..53` (`layout.isSlotUsed(slot)`) → cancel click (`event.setCancelled(true)`),
   check `GuiButtonInteractionService.tryUse`, and dispatch `handleControlSlotClick` based on button action.
3. Item slots `0..44` → allow native interaction (`event.setCancelled(false)`). The player's client handles
   left-click, right-click, shift-click (taking from storage), and dragging natively with zero rubberbanding.
4. Foreign item protection: shift-clicks originating from the player's bottom inventory into storage are
   cancelled to prevent accidental or unauthorized deposits.
5. Diff reconciliation (`reconcileStoragePage`): runs immediately after native click execution. Diffs the
   viewed slots (0-44) against the cached `StorageSession` page array using `ItemStack.isSimilar()`. Any
   difference is applied directly to `VirtualInventory` via `removeItemsAndUpdateSellValue` or
   `addItemsAndUpdateSellValue`. Dynamic buttons (such as the sell button) are updated in place without
   repainting item slots.

## Deferred compacting

Items do **not** automatically shift or compact forward while a player is viewing the storage. When an
item is taken, its slot remains empty so the player can take items without slots shifting unpredictably:

1. As long as at least one viewer has the GUI open, `StorageSession` retains the slot layout with gaps.
2. When the last viewer closes the GUI (`activeViewers == 0`), `storageSessionManager.endSession(spawner)`
   discards the session. Because `VirtualInventory` was already kept up to date during diff reconciliation,
   the session ends without overwriting `VirtualInventory` (preventing item loss or race conditions).
3. When any player next opens the storage GUI, `VirtualInventory.getDisplayPage()` projects remaining
   items sequentially starting from slot 0, naturally compacting all items without gaps.

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
`previous_page` and `next_page` reconcile the current page before navigating to the target page.

## Page counts — one formula, three sites

Pages come from **packed used slots**, not display geometry:
`ceil(getVirtualInventory().getUsedSlots() / 45)`, min 1. This exact formula is duplicated in
`SpawnerStorageUI.calculateTotalPages`, `SpawnerStorageAction.calculateTotalPages`, and
`StorageUpdateService.calculateTotalPages(int)`. **They must stay identical** — if they diverge you
get "page N shows N/N-1, back button stuck" bugs. `StoragePageHolder.oldUsedSlots` is a used-slot
snapshot refreshed by `updateOldUsedSlots()` after every redraw; it drives page-count change
detection and the `isAtCapacity` reset, so refresh it or the diff misfires.

## Rendering and sessions

When opening or updating a page, `SpawnerStorageUI` checks `StorageSessionManager.getOrCreateSession(spawner)`.
If page slots exist in the session, they are rendered directly to preserve open gaps. Otherwise, slots are
populated from `virtualInv.getDisplayPage()` and recorded in the session.
Buttons are cached in `SpawnerStorageUI` (static / navigation / page-indicator / sort caches) and evicted by
a 30s task; sell, sort, collect-exp and info buttons are built per repaint because they show live values.

## Cross-viewer sync and live loot

Multiple players can view one spawner's storage at once:
- When loot is generated by `SpawnerLootGenerator`, items are added to `VirtualInventory` and also inserted
  into active `StorageSession`s (`addLoot`), populating existing gaps or appending to slots.
- When hoppers extract items via `HopperTransfer`, items are removed from both `VirtualInventory` and active
  `StorageSession`s (`removeLoot`).
- Push updates schedule `StorageUpdateService.processStorageUpdateDirect` to repaint viewers when necessary.

## Persistence

Item moves call `spawner.markStorageDirty()`; the spawner is queued for saving on **close**
(`handleInventoryClose` → `markSpawnerModified` + `clearStorageDirty`), which runs on the closing
player's region thread — do not defer it to the player scheduler, or the block may resolve on the
wrong region. Sorting also calls `queueSpawnerForSaving` directly so the preference survives.

## Gotchas

- Diff reconciliation must use `ItemStack.isSimilar()` rather than comparing references, as Bukkit creates new stack instances on client interactions.
- Never write `StorageSession` slots directly into `VirtualInventory` on close; `VirtualInventory` is the source of truth updated incrementally via diff reconciliation.
- `StoragePageHolder` clamps page numbers in its constructor and setters. Do not clamp again at call sites.
- `take_all`/`drop_page` reading the display slots is safe only because they immediately remove that exact list from the count-map. Keep the read-then-remove atomic to the handler.
- Recover the spawner by casting the holder, never by parsing the localized title.
