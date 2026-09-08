# logging/

Operator-facing audit logging of spawner actions: an async file log and an optional Discord webhook.
This is diagnostics for the server owner, not player-facing text (that is `language/`) and not the
`plugin.getLogger()` diagnostics. Configured entirely by `activity_log.yml`.

| File | Role |
|---|---|
| `SpawnerActionLogger` | The hub: an async queue, file writing + rotation, and the optional Discord logger. `plugin.getSpawnerActionLogger()` |
| `SpawnerEventType` | Enum of everything loggable (place/break/stack/GUI-open/take-all/sell/exp/command/egg-change…) |
| `SpawnerLogEntry` | One event: type, player, location, entity, metadata. Has a builder |
| `SpawnerAuditListener` | Turns the `api/` spawner events into log entries |
| `LoggingConfig` | The `file` section of `activity_log.yml` (enabled, json vs text, console echo, rotation, event filter) |
| `discord/` | The webhook path: `DiscordWebhookLogger` + embed config/builders |

## How something gets logged

Two producers feed `SpawnerActionLogger.log(...)`:

- **`SpawnerAuditListener`** (priority `MONITOR`, `ignoreCancelled`) observes the public `api/` events
  (`SpawnerPlaceEvent`, `SpawnerBreakEvent`, `SpawnerExplodeEvent`, …) and logs the ones that fire as
  events. It observes only; it never cancels.
- **Direct calls** elsewhere use the builder form, `log(type, builder -> builder.player(...).location(...)
  .metadata(...))`, for actions that are not API events — the storage GUI logs `SPAWNER_STORAGE_OPEN`,
  `SPAWNER_ITEM_TAKE_ALL`, `SPAWNER_DROP_PAGE_ITEMS`, `SPAWNER_ITEMS_SORT` this way. Callers guard on
  `plugin.getSpawnerActionLogger() != null`.

`log` enqueues and returns; writing is **always async** off a drained queue, so a log call is safe on
any thread and never blocks the region thread. `SpawnerActionLogger` owns file rotation
(`max_log_files`) and, on shutdown, drains the queue.

## Two independent sinks

The **file** sink is gated by `LoggingConfig.enabled` and an event filter (`log_all_events`, or the
`logged_events` allow-list parsed into a `Set<SpawnerEventType>`); it writes text or JSON
(`json_format`) and optionally echoes to console (`console_output`).

The **Discord** sink is separate: `DiscordWebhookLogger` is constructed **only** when
`DiscordWebhookConfig.isEnabled()` at startup, so treat it as nullable. Embeds are shaped per event
type by `DiscordEmbedConfigManager` / `DiscordEventEmbedConfig`. Turning Discord on needs a restart, not
`/ss reload`.

## Reload

`SpawnerAuditListener` is re-registered on reload, and re-registering a listener needs
`HandlerList.unregisterAll(old)` first or events fire twice — this is the reference example cited in the
root `AGENTS.md` reload section. `SpawnerActionLogger` holds the async task and open file handle, so it
belongs in `cleanupResources()`.

## Gotchas

- `activity_log.yml` and its `file` section were assembled in 1.8.0 from the old `discord_logging.yml` plus the `logging` section of `config.yml`, by `ActivityLogConfigUpdater` (which lives in this package). That updater is one of the two cross-file, whole-file-rewrite migrations — see `../updates/AGENTS.md`.
- A new loggable action needs a `SpawnerEventType` constant **and** a producer (an `api/` event picked up by the audit listener, or a direct `log(...)` call). Adding only the enum logs nothing.
- Never log on the hot path synchronously; `log()` is already async — do not wrap it in your own scheduling.
