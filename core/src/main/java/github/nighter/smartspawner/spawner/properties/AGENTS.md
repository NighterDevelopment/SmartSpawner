# spawner/properties/

The domain data structures a spawner is made of. Three classes, no Bukkit scheduling and no
persistence — just state and the primitives that mutate it. `SpawnerData` is documented as the domain
object in the parent `spawner/AGENTS.md` (field tiers, locking rules, identity); this file is the map
for the two structures it holds.

| File | Role |
|---|---|
| `SpawnerData` | The spawner as an object: state, stack size, exp, the locks. See `../AGENTS.md` for its invariants |
| `VirtualInventory` | The stored items, as a count-map keyed by `ItemSignature` |
| `ItemSignature` | The identity used to consolidate equal item stacks |

## VirtualInventory is a count-map, not a slot array

The whole inventory is `Map<ItemSignature, Long>` (`consolidatedItems`, a `ConcurrentHashMap`): one
entry per distinct item, a `long` count that routinely exceeds a stack. There is no per-slot storage.
A GUI "slot" is computed on demand by walking the sorted entries and splitting counts into stacks.

- `getConsolidatedItems()` returns a **copy** — safe to iterate, useless to mutate.
- `getUsedSlots()` is a **capped estimate**: it sums `ceil(amount / maxStack)` and stops at `maxSlots`.
  It is not exact past the cap and is the number the capacity checks and page counts use.
- `getDisplayPage(page, pageSize)` / `getDisplayRange(start, n)` materialize **only that window** of
  `ItemStack`s from the sorted entries (`buildDisplaySection`), so rendering one page never builds the
  whole logical inventory. This is why a spawner holding billions of items still paints instantly.
- `sortItems(material)` sets a preferred material and rebuilds `sortedEntriesCache`; display order is
  that cache (preferred material first, then by material name). The cache is invalidated on every
  mutation.

### Adding and removing

- `addItem` / `addItems` / `addConsolidatedItem` merge into the count-map. Use `addConsolidatedItem`
  (template + total) when loading from the DB — a loop of single-stack `addItems` costs one map merge
  per stack, so one entry of a few million items becomes millions of merges.
- `removeItems(map)` is **transactional**: it first checks every requested signature has enough, and
  returns `false` touching nothing if any is short; only then does it decrement (`computeIfPresent`,
  dropping entries that hit zero). This is what makes take/sell dupe-safe even against a stale GUI view.

**VirtualInventory does not lock itself.** The map is concurrent, but multi-step operations
(check-then-remove, sort-then-read) are only atomic because the caller holds `SpawnerData.inventoryLock`.
Never call these primitives from outside that lock on a shared spawner.

## ItemSignature — cheap equality for consolidation

`ItemSignature` wraps a quantity-1 template and precomputes a hash from material ordinal, damage
value, and (only if present) display name / lore / enchants. `equals` short-circuits on material +
damage + "has meta?", and only falls back to `ItemStack.isSimilar` for items that actually carry meta.
So the common case (plain drops) compares in a few field reads, and equal stacks collapse to one
count-map entry.

- `getTemplate()` clones; `getUnsafeTemplateRef()` does not — read-only, never mutate the returned stack.
- `damage` is part of identity, so two otherwise-identical items with different durability are
  distinct entries. The DB codec groups these back together by base item (see `../data/AGENTS.md`).
- Changing what `ItemSignature` hashes or compares is a **data-format change**: it decides which stacks
  merge, and the codec iterates these entries.

## Gotchas

- `getUsedSlots()` is an estimate capped at `maxSlots`; do not treat it as an exact item-slot count past capacity. Capacity/percent checks want exactly this capped value.
- `getConsolidatedItems()` is a defensive copy; mutating it does nothing. Go through the add/remove primitives under `inventoryLock`.
- `maxSlots` is `int` and scales with stack size (`SpawnerData.setMaxSpawnerLootSlots`); it is not the number of GUI slots (45), it is the spawner's item-slot capacity.
