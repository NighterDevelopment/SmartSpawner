# SmartSpawner – AI Agent Guide

## Project Overview
SmartSpawner is a Paper/Folia Minecraft plugin (MC 1.21–1.21.11, Java 21) that replaces vanilla spawner behaviour with a GUI-driven, virtual-inventory system. Spawners generate loot/XP without spawning mobs, support stacking, and integrate with dozens of economy/protection/shop plugins.

## Module Structure
```
SmartSpawner/
  api/    – Public API jar (SmartSpawnerAPI interface, events, DTOs). No Paper internals.
  core/   – Plugin implementation. Depends on :api. All gameplay logic lives here.
  nms/    – Reserved placeholder (currently empty/unused).
```
Version is declared once in the **root** `build.gradle.kts` (`version = "1.6.4"`). Resource filtering injects it into `plugin.yml` / `paper-plugin.yml`.

## Build & Output
```bash
./gradlew shadowJar                     # Deployable plugin JAR → core/build/libs/SmartSpawner-X.Y.Z.jar
./gradlew build                         # Alias for shadowJar (configured in core/build.gradle.kts)
./gradlew :api:build                    # Standalone API jar → api/build/libs/api-X.Y.Z.jar
./gradlew :core:generateLanguageChangelog  # Diffs en_US lang keys vs latest GitHub release; prepends to language/CHANGELOG.txt
```
- `tasks.jar` produces `SmartSpawnerJar-*.jar` (no shaded deps) – **not** the server JAR.
- Shaded & relocated: `HikariCP` → `github.nighter.smartspawner.libs.hikari`, `mariadb-java-client` → `...libs.mariadb`.
- SQLite JDBC is `compileOnly`; it must be present on the server (Paper bundles it).

## Key Classes to Know
| Class | Role |
|---|---|
| `SmartSpawner` | JavaPlugin main class; constructs and wires all services |
| `SpawnerData` | Per-spawner state (inventory, exp, stack size, config). Uses **lock striping** (3 `ReentrantLock`s + `AtomicBoolean selling`). |
| `SpawnerManager` | In-memory registry: three indexes – by ID (`String`), by `Location`, by world name |
| `SpawnerStorage` (interface) | Persistence abstraction; impls: `SpawnerFileHandler` (YAML), `SpawnerDatabaseHandler` (MySQL/SQLite) |
| `Scheduler` | **Always use this** instead of `Bukkit.getScheduler()` – transparently supports Folia region threading |
| `IntegrationManager` | Detects and initialises all optional plugin hooks at startup |
| `MessageService` | All player messages go through `sendMessage(sender, key)` or `sendMessage(sender, key, Map<>)` |
| `VersionInitializer` | Version branching for Paper 1.21.5+ DataComponent API vs older ItemFlag fallback; call `VersionInitializer.hideTooltip(item)` for tooltip suppression |
| `SpawnerGuiViewManager` | Tracks all players currently viewing any spawner GUI; drives real-time GUI sync via `synchronization/` services |
| `SpawnerActionLogger` | Audit log dispatcher; routes spawner events to file log and/or Discord webhook (configured in `discord_logging.yml`) |
| `HopperService` | Optional hopper-to-virtual-inventory transfer; enabled via `hopper.enabled` in `config.yml` |
| `BrigadierCommandManager` | Registers all `/ss` subcommands using the Paper Brigadier API |

## Threading & Folia Rules
- **Never** call `Bukkit.getScheduler()` directly. Use `Scheduler.runTask()`, `Scheduler.runLocationTask()`, `Scheduler.runAsync()`, etc.
- `SpawnerData` lock order: acquire at most one of `inventoryLock`, `lootGenerationLock`, `dataLock` at a time. Use `tryLock()` (with short timeout) to avoid blocking the server thread – see `SpawnerLootGenerator.spawnLootToSpawner()` for the canonical pattern.
- Sell exclusion is guarded by `SpawnerData.selling` (`AtomicBoolean`); use `selling.compareAndSet(false, true)` / `selling.set(false)` rather than a lock – see `SpawnerSellManager` for the canonical pattern.

## Storage Modes
Configured via `config.yml`. Three modes: `YAML` (default, `spawners_data.yml`), `MYSQL`, `SQLITE`.  
Migration utilities: `YamlToDatabaseMigration`, `SqliteToMySqlMigration`.

## Configuration Files (in `core/src/main/resources/`)
| File | Purpose |
|---|---|
| `config.yml` | Core settings: language, GUI layout, spawner properties, break rules |
| `spawners_settings.yml` | Per-entity loot tables + mob head textures; versioned by plugin version |
| `item_spawners_settings.yml` | Loot for item-type spawners |
| `item_prices.yml` | Per-item sell prices for the sell GUI |
| `gui_layouts/{layout}/main_gui.yml` | Main GUI slot layout + `skip_main_gui` option |
| `gui_layouts/{layout}/sell_confirm_gui.yml` | Sell confirmation GUI slot layout + `skip_sell_confirmation` option |
| `gui_layouts/{layout}/storage_gui.yml` | Storage GUI slot layout |
| `gui_layouts/default/main_gui.yml` | Per-GUI layout overrides (also `sell_confirm_gui.yml`, `storage_gui.yml`) |
| `language/{locale}/` | Localisation files (`en_US`, `de_DE`, `vi_VN`, `en_US_DonutSMP`, `en_US_DonutSMP_v2`) |
| `discord_logging.yml` | Discord webhook settings; per-event embed templates; event filter list |
| `auraskills.yml` | AuraSkills RPG integration settings |

Config versioning: `SpawnerSettingsConfig` tracks `config_version` (= plugin version) and auto-migrates on mismatch.

## Adding a New Integration
1. Add a `compileOnly` dependency in `core/build.gradle.kts` with `exclude(group = "*")` if the lib has transitive deps.
2. Declare a `boolean hasXxx` flag in `IntegrationManager` and populate it in `checkProtectionPlugins()` or `checkIntegrationPlugins()`.
3. Create your hook class under `core/src/main/java/.../hooks/{category}/`.
4. Register the plugin as optional in `paper-plugin.yml` under `dependencies.server`.

## Localisation Pattern
Each locale directory contains **six** files: `messages.yml`, `gui.yml`, `items.yml`, `formatting.yml`, `command_messages.yml`, `hologram.yml`. Add keys to the appropriate file in `core/src/main/resources/language/en_US/`. Send via:
```java
plugin.getMessageService().sendMessage(player, "your.key");
plugin.getMessageService().sendMessage(player, "your.key", Map.of("{placeholder}", value));
```
Never hardcode chat strings; missing keys produce a console warning and a red fallback message.

## Lombok Usage
Heavily used: `@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Accessors(chain = false)` on `SmartSpawner`.  
Do **not** add `chain = true` – accessor chaining is intentionally disabled on the main class.

## Public API (for external plugins)
External plugins obtain the API via `SmartSpawnerProvider.getAPI()` (returns `SmartSpawnerAPI`).  
Events are in `api/src/main/java/.../api/events/`. Data transfer is via `SpawnerDataDTO` / `SpawnerDataModifier`.  
The API module has no dependency on `core`; it only depends on Paper API.

