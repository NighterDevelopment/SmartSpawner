# spawner/data/

The in-memory registry of spawners and the SQL persistence layer behind one interface.

## Layout

| File | Role |
|---|---|
| `SpawnerManager` | In-memory registry. The only thing the rest of the plugin should ask for a spawner |
| `WorldEventHandler` | Decides *when* spawners load, keyed off world load/unload events |
| `storage/SpawnerStorage` | The persistence interface. Code outside this package targets this, nothing else |
| `storage/StorageMode` | `SQLITE` (default), `MYSQL`. Use `StorageMode.fromConfig` to read the config value |
| `storage/SpawnerInventoryCodec` | Encodes a virtual inventory into the `items` blob |
| `legacy/LegacyInventoryCodec` | Read-only reader for the removed string item format. Migration only |
| `database/DatabaseManager` | HikariCP pool, schema creation, schema migrations, shared by both modes |
| `database/SpawnerDatabaseHandler` | The one storage backend |
| `database/YamlToDatabaseMigration` | `spawners_data.yml` to SQL, one time |
| `database/SqliteToMySqlMigration` | SQLite file to MySQL, one time, MySQL mode only |

## Backend selection

YAML storage was removed in 1.8. `SmartSpawner.initializeStorage()` reads `database.mode` and
**has no fallback**: if the pool or the handler fails to come up it returns false and `onEnable`
disables the plugin. Running without persistence would discard every spawner on the next restart,
which is worse than not starting. A config still set to `YAML` resolves to `SQLITE`
(`StorageMode.fromConfig`, plus a value migration in `ConfigMigrations`), and the leftover
`spawners_data.yml` is imported once.

`plugin.getDatabaseManager()` and `plugin.getSpawnerStorage()` are both non-null whenever the plugin
is enabled. Still go through `getSpawnerStorage()` rather than the handler.

Migrations run right after a successful init, gated on `database.migrate_from_local` (default
true) and each migration's own `needsMigration()`.

## Database schema

Two tables, both on the `spawner_` prefix: `spawner_data` and `spawner_schema_meta`
(renamed from `smart_spawners` / `smartspawner_meta` in schema v3). Names live in
`DatabaseManager.TABLE_SPAWNERS` / `TABLE_META`, not as literals in queries.

`spawner_schema_meta.schema_version` drives `runSchemaMigrations()`. Adding a step means bumping
`CURRENT_SCHEMA_VERSION` and adding a case to `applyMigrationStep`. Two ordering rules:

- Renaming legacy tables happens in `renameLegacyTables()` **before** anything reads the version, because the meta table is itself one of the renamed tables.
- `createTables()` uses `CREATE TABLE IF NOT EXISTS` with the current schema, so it is a no-op for existing databases. Migration steps must bring an old table up to shape themselves.

`chunk_x` / `chunk_z` are written by the handler, indexed as `idx_chunk`. Nothing reads them yet;
they exist for per-chunk spawner loading.

## SpawnerManager

Three indexes, all kept in step by `addSpawner` / `removeSpawner` / `addSpawnerToIndexes`:

- `spawners`: id to `SpawnerData` (`ConcurrentHashMap`)
- `locationIndex`: block-precision `LocationKey` to `SpawnerData` (plain `HashMap`)
- `worldIndex`: world name to set of spawners (plain `HashMap`)

Two of the three are **not** thread safe. Mutate the registry from the main/region thread, and treat
`getAllSpawners()` and `getSpawnersInWorld()` as snapshots to iterate, not as live views to mutate.

`LocationKey` uses `getBlockX/Y/Z`, so sub-block coordinate differences collapse to the same spawner.
Pass the block location; do not construct your own key.

`addSpawner` queues a save automatically. `removeSpawner` does not, so a removal has to be paired with
`markSpawnerDeleted`.

Ghost spawners (a record whose block is gone) are handled by `isGhostSpawner` / `removeGhostSpawner`,
with verdicts cached in `confirmedGhostSpawners`. The `/ss clear` command drives this.

## Loading is world-driven, not startup-driven

`SpawnerManager` is constructed with `initializeWithoutLoading()`. Nothing is loaded until
`WorldEventHandler.attemptInitialSpawnerLoad()` runs at the end of `onEnable`.

A spawner whose world is not loaded yet is kept in `pendingSpawners` and materialized on
`WorldLoadEvent`. This is why `SpawnerStorage.loadAllSpawnersRaw()` is documented to return **null
values** for unloadable spawners and why `getRawLocationString(id)` exists: the handler needs the
world name before it can build a `Location`. Null-check the values of that map.

On `WorldUnloadEvent` the handler **flushes first**, then drops the spawners from the indexes via
`SpawnerManager.unloadSpawnersInWorld`. Order matters: storage handlers resolve dirty spawners back
out of `SpawnerManager`, so unloading before flushing loses the pending writes. They are unloaded,
not deleted.

Because `plugin.yml` declares `load: POSTWORLD`, worlds usually exist by `onEnable`, but
Multiverse-style late world creation is exactly the case this machinery covers.

## Writes are batched, always

Mutating a spawner does not write to disk. The flow is:

```
mutate SpawnerData -> markSpawnerModified(id) / queueSpawnerForSaving(id) -> flushChanges() later
```

`SpawnerDatabaseHandler` keeps `dirtySpawners` and `deletedSpawners` sets and flushes on a
**hardcoded 5-minute** async timer (6000 ticks, `startSaveTask()`), plus on `WorldSaveEvent` and on
shutdown. `shutdown()` is contractually required to flush before returning, and
`SmartSpawner.saveAndCleanup()` calls `spawnerStorage.shutdown()` before `databaseManager.shutdown()`.
Keep that order: closing the pool first would lose the final flush.

Practical consequences:

- Forgetting to mark a spawner dirty means the change survives until restart and then vanishes. This is the most common bug in this area and there is no test to catch it.
- Do not add a synchronous save on a hot path. Mark and let the batch run.
- Flush work runs async (`Scheduler.runTaskTimerAsync`) and touches `SpawnerData`, so it must take the same locks described in `../CLAUDE.md`.
- **The queue holds IDs, not snapshots.** `saveSpawnerBatch` resolves each ID back through `SpawnerManager` and skips it when the lookup returns null. Anything that evicts a spawner from the registry must flush first, the way `WorldEventHandler` does on world unload. Per-chunk eviction cannot be added until this is snapshot-based.

## Data format versioning

`SpawnerData` on disk is versioned by `SmartSpawner.DATA_VERSION` (currently 3) under the
`data_version` key in `spawners_data.yml`. A lower version logs a notice at load and is converted by
`migration/SpawnerDataMigration` + `SpawnerDataConverter`, invoked from `migrateDataIfNeeded()`
**before** components initialize.

This is separate from config migration, which is version-less and lives in `updates/`. Do not
conflate the two: bump `DATA_VERSION` for spawner data shape changes only.

## Item serialization

`SpawnerInventoryCodec` owns the `items` blob. Item templates go through Paper's
`ItemStack.serializeItemsAsBytes`, which is raw NBT and keeps every item component, and each entry
carries a `DataVersion` so items survive a Minecraft version upgrade. Counts are `long` per distinct
item and routinely exceed a stack, so amounts are written as a separate array rather than as
`ItemStack` amounts. `total_items` is denormalized alongside the blob so item totals can be read
without decoding it.

Blob layout: `[byte version][int n][long amount × n][int payloadLen][payload]`. Changing it means
bumping `FORMAT_VERSION` and keeping a decode branch for the old value.

On load use `VirtualInventory.addConsolidatedItem(template, amount)`, never a loop of
`addItems(singletonList(batch))`: the latter costs one map merge per stack, so a single entry of a
few million items becomes millions of merges.

`ItemSignature` groups equal stacks in `VirtualInventory` and is what the codec iterates. Changing
either is a data format change.
