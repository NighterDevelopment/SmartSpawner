# api/ (module)

The public API other plugins compile against. This is a **separate Gradle module** with no Bukkit
runtime logic and no dependency on `core` — just interfaces, events, DTOs and GUI builders. `core`
depends on it and provides the implementation. `CONTRIBUTING.md` asks contributors to prefer growing
this module over `core`, so an addon-facing feature usually starts here.

## Getting the API

An addon calls `SmartSpawnerProvider.getAPI()` — a static helper that finds the `SmartSpawner` plugin,
checks it implements `SmartSpawnerPlugin`, and returns its `SmartSpawnerAPI` (or `null` if not loaded).
`core`'s main class implements `SmartSpawnerPlugin`; `SmartSpawnerAPIImpl` (in `core`) is the concrete
`SmartSpawnerAPI`. Keep the two in step: a method added to the interface must be implemented there.

## Surface

| Area | What it offers |
|---|---|
| `SmartSpawnerAPI` | Create spawner items (`createSpawnerItem` / `createVanillaSpawnerItem` / `createItemSpawnerItem`), classify them (`isSmartSpawner` / `isVanillaSpawner` / `isItemSpawner`), read them (`getSpawnerByLocation` / `getSpawnerById` / `getAllSpawners` → `SpawnerDataDTO`), mutate (`getSpawnerModifier` → `SpawnerDataModifier`), remove (`removeSpawner` → `CompletableFuture<Boolean>`), and register GUI layouts |
| `data/` | `SpawnerDataDTO` (read-only snapshot) and `SpawnerDataModifier` (staged edits). Never expose `core`'s `SpawnerData` directly |
| `events/` | `SpawnerEvent` base + concrete events other plugins listen to or cancel |
| `gui/` | `GuiLayoutRegistry` + `GuiLayoutBuilder` / `GuiButtonBuilder` so addons ship custom layouts, and `SpawnerGuiLayoutProvider` to override layout per spawner |

## Events

Every event extends `SpawnerEvent` (which carries the shared `HandlerList`). `core` fires them from the
matching feature (`SpawnerBreakEvent` / `SpawnerPlayerBreakEvent` from break handling,
`SpawnerTakeAllEvent` / `SpawnerDropAllEvent` from the storage GUI, `SpawnerSellEvent` from selling, …).
Two consumers inside `core` also listen: `logging/SpawnerAuditListener` records them, and cancellable
ones gate the action. When you fire a new event from `core`, check `getHandlerList().getRegisteredListeners().length`
before building its payload if the payload is expensive — the storage GUI does this for the take/drop
events.

## Rules

- **No `core` types leak across the boundary.** The API speaks `SpawnerDataDTO` / `SpawnerDataModifier`, `EntityType`, `Material`, `Location`, `ItemStack` — never `core`'s `SpawnerData` or GUI holders.
- **Additive only.** Third parties compile against this; removing or re-signing a method breaks them. Add, deprecate, keep.
- A new API method is two edits: the interface here and `SmartSpawnerAPIImpl` in `core`. The build fails on the missing override, which is the safety net.
- GUI layout contributions go through `GuiLayoutRegistry` (`registerLayout` / `registerLayoutFromYaml` / `unregisterLayout`); `core`'s live layout code is in `core .../spawner/gui/layout/`.
