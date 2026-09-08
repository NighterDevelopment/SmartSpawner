# spawner/lootgen/

Producing loot into a spawner's virtual inventory, and deciding which spawners are active. Two
concerns that are deliberately kept apart: `SpawnerRangeChecker` decides **when**, `SpawnerLootGenerator`
does the **work** and must never make timing decisions.

| File | Role |
|---|---|
| `SpawnerRangeChecker` | The 1s timer + player-proximity gate. Activates/deactivates spawners |
| `RangeMath` | Package-private: computes which spawners have a player in range, off a player snapshot |
| `PlayerRangeWrapper` | Record snapshot of a player's world + position + spawn conditions |
| `SpawnerLootGenerator` | Rolls loot and adds it to the inventory. Also the pre-generation path |
| `LootResult` | Record: the rolled `Map<ItemSignature, Long>` items + experience |
| `loot/LootItem` | One loot-table row: a prebuilt template + its distribution |
| `loot/EntityLootConfig` | A mob's whole table: base experience + `List<LootItem>` |

## When: SpawnerRangeChecker

A single repeating task (`CHECK_INTERVAL`, 20 ticks) snapshots online players, hands them to
`RangeMath.getActiveSpawners()` on an `ExecutorService`, and then flips each spawner on its **region
thread** (`activateSpawner` / `deactivateSpawner`). Range math runs off-thread on immutable snapshots;
anything touching the spawner or world hops back with `Scheduler.runLocationTask`. `checkAndSpawnLoot`
is where an expired timer triggers a spawn. Note that `synchronization/TimerUpdateService` drives a
*second* loot path (pre-generation for open GUIs) — the two are complementary, see
`../gui/synchronization/utils/AGENTS.md`.

## Work: SpawnerLootGenerator

`spawnLootToSpawner(spawner)` returns early on `isSelling()`, takes `lootGenerationLock` (via `tryLock`,
never blocking a server thread), calls `generateLoot`, limits it to free capacity, and adds it. It does
**not** check any timer; adding one here would create a competing trigger.

`generateLoot(minMobs, maxMobs, spawner)` rolls a mob count, then for each `LootItem` chooses between
two strategies via `shouldApproximate(chance, mobCount, threshold)`:

- **Exact** (`generateExactLoot`): roll per mob with `ThreadLocalRandom`. Used for small stacks.
- **Approximate** (`generateApproximatedLoot`): expected value (`mobCount × chance × avgAmount`) with a
  ±5% jitter, used once `mobCount` is large enough that per-mob rolling is wasteful. Gated by
  `Config.get().isApproximateLoot()` and `getApproximationThreshold()`.

`limitLootToAvailableSlots` trims the rolled loot to what `getUsedSlots` says still fits, so a full
spawner does not overflow. All randomness is `ThreadLocalRandom` — no shared `Random`.

## Loot templates are prebuilt

A `LootItem` holds a **template `ItemStack` resolved once at config load** (by
`config/ConfiguredItemParser`), so supporting a new item property needs no change here or to loot
generation: `createItemStack()` clones the template and, only when the entry declared a durability
**range**, rolls the damage. A single fixed damage is already baked into the template. See
`../config/AGENTS.md`.

## Pre-generation

`preGenerateLoot` / `addPreGeneratedLoot` build the next batch ahead of time into the spawner's
`preGeneratedItems` / `preGeneratedExperience` fields so an open GUI shows it instantly. Those fields
are `volatile` and owned by `lootGenerationLock`; read and write them through `SpawnerData`, never
directly. The timing that calls these lives in `synchronization/`, not here.

## Gotchas

- Never add a time/proximity check inside `SpawnerLootGenerator`. Timing belongs to `SpawnerRangeChecker` (and the GUI pre-gen trigger); a second trigger double-spawns.
- Loot generation runs on server threads. Use `tryLock`, and never hold a lock across a `Scheduler` hop (see `../AGENTS.md`).
- `EntityLootConfig` / `LootItem` come from `config/SpawnerSettingsConfig`, which is why the price manager must initialize before spawner settings — loot rows carry a `sellPrice` read from prices.
