# SmartSpawner Storage Performance & Memory Benchmark Report

Comparison between the **`main`** branch (legacy full-repaint storage) and
**`feature/native-take-storage`** (native take + `StorageSession`).

Raw output: [`benchmark_results.txt`](benchmark_results.txt), regenerated with `/ss benchmark`.

---

## 1. Architecture Overview

### `main` Branch (Legacy): Full Repaint & Immediate Compacting
- **Click Model**: Cancels every click unconditionally upfront (`event.setCancelled(true)`), computes removal math in plugin code, and transfers items to the player's inventory manually (`transferToPlayerInventory`).
- **Display Update**: On every click, the server clears all 45 item slots (`setItem(null)` x 45), queries `VirtualInventory.getDisplayPage(page, 45)`, allocates fresh `ItemStack` instances, and repaints all 45 slots.
- **Compacting**: Items automatically and immediately shift forward to fill gaps on every single click. Item positions jump while the player is rapidly clicking, and every click generates a full page of slot-update packets.

### `feature/native-take-storage` Branch: Hardened Native Take & Deferred Compacting
- **Native Take Click Model**: Allows native Minecraft client interactions (`event.setCancelled(false)` for slots 0..44) with an anti-exploit whitelist. The client natively handles lifting, splitting, dropping, and collecting items without rubberbanding or artificial delay.
  - *Allowed Actions*: `LEFT`, `RIGHT`, `SHIFT_LEFT`, `SHIFT_RIGHT`, `DROP` (Q), `CONTROL_DROP` (Ctrl+Q), and `DOUBLE_CLICK` (`COLLECT_TO_CURSOR`).
  - *Blocked Actions*: `SWAP_OFFHAND` (Key F), `NUMBER_KEY` (1-9 hotbar swap), `CLONE` (creative middle-click), and unknown packet types are cancelled upfront.
- **Output-Only Security Enforcement**: Spawner storage is strictly output-only; players can never deposit, place, or swap items into the spawner.
  - If the cursor is holding an item, any click attempting to place items into storage slots 0..44 is cancelled.
  - Dragging across any storage or control slot (< 54) is cancelled in `onInventoryDrag`.
  - Shift-clicking from the bottom inventory into storage is cancelled.
  - Both drop actions move items strictly outward (spawner → cursor/floor). `reconcilePage` detects the decrease and debits it from `VirtualInventory`.
- **Single-Viewer Lock**: Only one player can view a spawner's storage at a time; a second player gets `storage_in_use`. Stale viewers (disconnected players, GUIs closed without an event) are self-healed and pruned. This eliminates multi-viewer race conditions and duplication windows.
- **Diff Reconciliation**: `StorageSession.reconcilePage` compares the 45 displayed slots against the session's buffer for that page, under the session lock, and adopts the new layout in the same critical section. Only the delta is debited/credited in `VirtualInventory`, and only the dynamic sell button is repainted. Zero item slots are wiped or repainted.
- **Deferred Compacting**: Gaps stay open while the player is viewing. When the last viewer closes, the session ends; the next open projects items sequentially from `VirtualInventory.getDisplayPage()`, compacting naturally with no data loss.

### Memory model: the session is bounded by viewers, not by pages

The first draft of this branch cached slot arrays in a `Map<Integer, ItemStack[]>` that grew for the
lifetime of the session — one `ItemStack[45]` for every page a player ever scrolled past. Page count
is `maxSpawnerLootSlots / 45` and is unbounded, so a player idly holding down "next page" on a large
spawner would retain millions of `ItemStack`s until they closed the GUI.

`StorageSession` now keeps page buffers in an **access-ordered `LinkedHashMap` capped at
`max(2, viewers + 1)`**. A viewer looks at exactly one page, so the cap always covers every on-screen
page plus headroom for an in-flight page switch. Evicting a buffer is free of risk: `VirtualInventory`
is the source of truth and an evicted page is simply re-projected on the next read.

Two further memory changes back this up:

- `VirtualInventory.fillDisplayPage(page, size, out)` projects a page **straight into a slot array**,
  skipping the intermediate `Int2ObjectMap` that `getDisplayPage` has to allocate. Reading a page from
  a session buffer is now a zero-allocation operation.
- `reconcilePage` compares slots positionally first. Slots that did not move are carried over with no
  clone and no `ItemSignature` construction, so a single-item take does work proportional to what
  actually changed instead of to the whole page. Live loot and hopper writes batch their slot updates
  and apply them in one pass per viewer, on the spawner's region thread.

---

## 2. Benchmark Results

Measured with `/ss benchmark` on **Paper 26.2 / Java 25**, 3970 MB max heap, Windows 11 (amd64).
Both branches' code paths run in-process against identical data, so the comparison is not affected by
run-to-run server variance.

Two memory numbers are reported and they answer different questions:

- **Alloc/op** — bytes allocated per operation (`ThreadMXBean`). This is GC pressure: how much garbage
  one player click makes.
- **Retained** — live heap still held afterwards. This is where a leak shows up.

### Benchmark 1: Display page materialization (up to 10,000,000 items)

| Inventory Scale | `main` (Uncached) | `feature` (Session) | Speedup | Alloc/op |
| :--- | :--- | :--- | :--- | :--- |
| Small — 5 types / 1,000 items | 1.68 μs (593,556 op/s) | **0.19 μs (5,252,101 op/s)** | **8.85x** | 6.50 KB → **0 B** |
| Medium — 20 types / 100,000 items | 1.30 μs (770,297 op/s) | **0.22 μs (4,615,953 op/s)** | **5.99x** | 6.48 KB → **0 B** |
| Large — 50 types / 1,000,000 items | 2.75 μs (363,280 op/s) | **0.21 μs (4,713,646 op/s)** | **12.98x** | 6.50 KB → **0 B** |
| Massive — 50 types / 5,000,000 items | 2.46 μs (406,802 op/s) | **0.17 μs (5,722,461 op/s)** | **14.07x** | 6.50 KB → **0 B** |
| Maximum — 50 types / 10,000,000 items | 2.31 μs (433,360 op/s) | **0.18 μs (5,476,451 op/s)** | **12.64x** | 6.49 KB → **0 B** |

Reading an already-buffered page allocates nothing at all — the display path produces zero garbage
regardless of how many items the spawner holds.

### Benchmark 2: Item take cycle (1,000 consecutive takes at 10M items)

| Metric | `main` (Full Repaint) | `feature` (reconcilePage) | Improvement |
| :--- | :--- | :--- | :--- |
| Total time | 33.96 ms | **4.14 ms** | **8.20x faster** |
| Average latency | 33.96 μs | **4.14 μs** | **8.20x faster** |
| P50 latency | 25.80 μs | **0.10 μs** | **258x** |
| P95 latency | 73.70 μs | **0.20 μs** | **368x** |
| P99 latency | 196.90 μs | **61.30 μs** | **3.21x** |
| Throughput | 29,450 takes/s | **241,470 takes/s** | **8.20x** |
| **Alloc / op** | 12.80 KB | **178 B** | **−98.6%** |
| Inventory slot writes | 91,000 | **90** | **−99.9%** |

This is the hot path — one click, one reconcile. It went from 12.8 KB of garbage per click to 178 B.

### Benchmark 3: Pagination (500 page switches at 10M items)

| Metric | `main` (Uncached Slices) | `feature` (Session Buffers) | Change |
| :--- | :--- | :--- | :--- |
| Average latency | 5.25 μs | 5.52 μs | 0.95x |
| P50 latency | 4.80 μs | **3.70 μs** | **1.30x** |
| Throughput | 190,454 flips/s | 181,245 flips/s | 0.95x |
| Alloc / op | 8.93 KB | **8.32 KB** | **−6.8%** |
| Page buffers held after 500 flips over 10 pages | — | **2** | — |

This is the one place the bounded cache costs something, and the trade is deliberate. Holding all 10
pages would make a flip a cache hit; holding 2 means most flips re-project the page. The measured
cost is ~0.3 μs per button press, against the retained-memory result below. Average latency is within
run-to-run noise of `main`, and P50 is actually better.

### Benchmark 4: Page cache retained memory — the leak this branch had

A player paging through a large spawner. "Unbounded" is the per-page cache this branch started with;
"Bounded" is `StorageSession` as it ships.

| Pages Walked | Unbounded (retained) | Bounded (retained) | Buffers Held | Reduction |
| :--- | :--- | :--- | :--- | :--- |
| 1,000 | 5.75 MB | **27.66 KB** | 2 | **99.53%** |
| 10,000 | 57.44 MB | **7.05 KB** | 2 | **99.99%** |
| 100,000 | **575.05 MB** | **17.49 KB** | 2 | **~100%** |

One player, one spawner, 100k pages: **575 MB retained** versus **17 KB**. The bounded numbers do not
grow with page count — the residual KB is measurement noise around a fixed two-array footprint, not a
trend.

### Benchmark 5: Sort items (cycling preferred sort item)

| Scale | `main` (Uncached) | `feature` (Session) | Speedup | Alloc/op |
| :--- | :--- | :--- | :--- | :--- |
| 1,000,000 items | 83.30 μs (12,005/s) | **33.99 μs (29,422/s)** | **2.45x** | 12.55 KB → **11.94 KB** |
| 5,000,000 items | 20.64 μs (48,438/s) | **13.63 μs (73,389/s)** | **1.52x** | 12.55 KB → **11.94 KB** |
| 10,000,000 items | 23.50 μs (42,549/s) | **15.93 μs (62,788/s)** | **1.48x** | 12.55 KB → **11.94 KB** |

`sort_items` now calls `resetSession` rather than `removeSession`: buffers are dropped so the new
order shows, but the session and its viewer lock survive under the player still standing in the GUI.

### Benchmark 6: "Take All" page operation (200 ops at 10M items)

| Metric | `main` (Full Repaint) | `feature` (Session Adopt) | Improvement |
| :--- | :--- | :--- | :--- |
| Average latency | 58.15 μs | **5.45 μs** | **10.66x faster** |
| P95 latency | 145.00 μs | **6.30 μs** | **23.02x** |
| P99 latency | 334.70 μs | **31.30 μs** | **10.69x** |
| Throughput | 17,197 ops/s | **183,352 ops/s** | **10.66x** |
| **Alloc / op** | 25.72 KB | **2.20 KB** | **−91.5%** |
| Inventory slot writes | 18,000 | **36** | **−99.8%** |

### Benchmark 7: "Drop Page" operation (200 ops at 10M items)

| Metric | `main` (Full Repaint) | `feature` (Session Refresh) | Improvement |
| :--- | :--- | :--- | :--- |
| Average latency | 21.95 μs | **16.41 μs** | **1.34x faster** |
| P95 latency | 42.80 μs | **21.50 μs** | **1.99x** |
| P99 latency | 144.70 μs | **58.80 μs** | **2.46x** |
| Throughput | 45,552 drops/s | **60,927 drops/s** | **1.34x** |
| Alloc / op | 28.77 KB | **27.21 KB** | **−5.4%** |

### Benchmark 8: Concurrent loot generation + hopper extraction (1,000 ops at 10M items)

| Metric | `main` (Full Repaint) | `feature` (Session Sync) | Improvement |
| :--- | :--- | :--- | :--- |
| Average latency | 11.28 μs | **7.97 μs** | **1.42x faster** |
| P50 latency | 9.80 μs | **3.20 μs** | **3.06x** |
| Throughput | 88,688 ops/s | **125,511 ops/s** | **1.42x** |
| **Alloc / op** | 12.51 KB | **1.04 KB** | **−91.7%** |

`addLoot` / `removeLoot` are called from async tasks. They mutate buffers under the session lock and
then hand the resulting slot writes to the spawner's region thread — inline when the caller already
owns it, otherwise via `Scheduler.runLocationTask` — batched into one `updateInventory()` per viewer
instead of one per changed slot.

---

## 3. Summary

| Dimension | Result |
| :--- | :--- |
| Single item take | **8.2x faster**, 98.6% less garbage, 99.9% fewer slot writes |
| Take all / drop page | **10.7x / 1.3x faster**, 91% / 5% less garbage |
| Display page read | **6–14x faster**, zero allocation |
| Live loot + hopper sync | **1.4x faster**, 91.7% less garbage |
| Page cache retained memory | **575 MB → 17 KB** at 100k pages |
| Pagination | ~parity (0.95x avg, 1.30x P50), the cost of bounding the cache |

## 4. Reproducing

```bash
./gradlew runServer
```

Then in the server console:

```
smartspawner benchmark
```

The report is written to `run/storage_benchmark_report.txt`. The benchmark runs both the legacy and
the current code paths in the same JVM against identical data, so it does not need a `main` checkout
to compare against. Allocation tracking needs a HotSpot JVM; the header line says whether it is
active, and the columns read `n/a` if it is not.
