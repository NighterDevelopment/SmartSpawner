---
title: Discord Webhook Integration
description: Forward spawner events to Discord with fully customisable per-event embeds – configuration guide for discord.yml and discord/events/*.yml
---

SmartSpawner can forward spawner events to a Discord channel via webhooks.
Configuration is split into two layers:

| File | Purpose |
|---|---|
| `discord.yml` | Global settings – webhook URL, enable switch, event filtering |
| `discord/events/<EVENT>.yml` | Per-event embed appearance (title, description, colour, fields, …) |

The per-event files are **generated automatically** from built-in defaults the first time Discord is enabled. You only need to edit the ones you want to customise.

:::note[Prerequisite]
File-based logging must be enabled. Set `logging.enabled: true` in `config.yml`.
:::

---

## Quick Setup

1. Create a webhook: **Discord Server Settings → Integrations → Webhooks → New Webhook**. Copy the URL.
2. Edit `discord.yml`:

```yaml
enabled: true
webhook_url: "https://discord.com/api/webhooks/YOUR_ID/YOUR_TOKEN"
```

3. Run `/smartspawner reload` or restart. Per-event config files appear in `discord/events/`.

---

## `discord.yml` – Global Settings

```yaml
# Enable / disable all Discord webhook delivery.
enabled: false

# Discord webhook URL.
webhook_url: ""

# Show the player's Minecraft head as embed thumbnail.
show_player_head: true

# true → forward every logged event (ignores logged_events list).
log_all_events: false

# Events to forward when log_all_events is false.
logged_events:
  - SPAWNER_PLACE
  - SPAWNER_BREAK
  - SPAWNER_EXPLODE
  - SPAWNER_STACK_HAND
  - SPAWNER_STACK_GUI
  - SPAWNER_DESTACK_GUI
  - SPAWNER_GUI_OPEN
  - COMMAND_EXECUTE_PLAYER
  - COMMAND_EXECUTE_CONSOLE
  - COMMAND_EXECUTE_RCON
```

### Available Event Names

| Category | Events |
|---|---|
| Lifecycle | `SPAWNER_PLACE` `SPAWNER_BREAK` `SPAWNER_EXPLODE` |
| Stacking | `SPAWNER_STACK_HAND` `SPAWNER_STACK_GUI` `SPAWNER_DESTACK_GUI` |
| GUI | `SPAWNER_GUI_OPEN` `SPAWNER_STORAGE_OPEN` `SPAWNER_STACKER_OPEN` |
| Economy / XP | `SPAWNER_EXP_CLAIM` `SPAWNER_SELL_ALL` |
| Items | `SPAWNER_ITEM_TAKE_ALL` `SPAWNER_ITEM_DROP` `SPAWNER_ITEMS_SORT` `SPAWNER_ITEM_FILTER` `SPAWNER_DROP_PAGE_ITEMS` |
| Entity | `SPAWNER_EGG_CHANGE` |
| Commands | `COMMAND_EXECUTE_PLAYER` `COMMAND_EXECUTE_CONSOLE` `COMMAND_EXECUTE_RCON` |

---

## Per-Event Embed Files

Each event type gets its own file at:

```
plugins/SmartSpawner/discord/events/SPAWNER_PLACE.yml
plugins/SmartSpawner/discord/events/SPAWNER_BREAK.yml
plugins/SmartSpawner/discord/events/COMMAND_EXECUTE_PLAYER.yml
… (one file per event)
```

Files are created on first startup with sensible defaults. **Only files for events in your `logged_events` list are extracted** – unused events cost zero memory.

### YAML format (default)

```yaml
# discord/events/SPAWNER_PLACE.yml
embed_format: yaml

embed:
  title: "✅ Spawner Placed"
  description: "**{player}** placed a **{entity}** spawner"
  color: "57F287"          # Hex colour without #
  footer: "SmartSpawner • {time}"
  fields:
    - name: "📍 Location"
      value: "`{location}`"
      inline: false
    - name: "🐾 Entity"
      value: "`{entity}`"
      inline: true
    - name: "🔢 Quantity"
      value: "`{quantity}`"
      inline: true
```

### JSON format (full Discord control)

Switch any event file to JSON mode for complete Discord embed customisation:

```yaml
# discord/events/SPAWNER_BREAK.yml
embed_format: json

embed_json: |
  {
    "embeds": [
      {
        "title": "❌ {player} broke a spawner",
        "description": "Location: **{location}**",
        "color": 15548997,
        "fields": [
          { "name": "Entity", "value": "{entity}", "inline": true },
          { "name": "Stack",  "value": "{quantity}",   "inline": true }
        ],
        "footer": { "text": "SmartSpawner • {time}" },
        "timestamp": "{timestamp}"
      }
    ]
  }
```

:::caution
The JSON must be valid. Use `\n` for newlines **inside** string values. Do not insert literal line breaks inside a JSON string.
:::

---

## Placeholders

These placeholders work in **both** YAML and JSON mode in every event file.

| Placeholder | Value |
|---|---|
| `{player}` | Player name (`N/A` for console/explosion events) |
| `{player_uuid}` | Player UUID |
| `{description}` | Human-readable event description |
| `{event_type}` | Raw event name (e.g. `SPAWNER_PLACE`) |
| `{time}` | Formatted time `HH:mm:ss` |
| `{timestamp}` | ISO 8601 – use in `"timestamp"` field for Discord native date |
| `{location}` | `world (x, y, z)` |
| `{world}` `{x}` `{y}` `{z}` | Individual coordinates |
| `{entity}` | Mob / entity name |
| `{color}` | Decimal colour integer for this event |

### Event-specific metadata placeholders

| Event | Extra placeholders |
|---|---|
| `SPAWNER_PLACE` / `SPAWNER_BREAK` | `{quantity}` |
| `SPAWNER_EXPLODE` | `{quantity}` |
| `SPAWNER_STACK_HAND` / `SPAWNER_STACK_GUI` | `{amount_added}` `{old_stack_size}` `{new_stack_size}` |
| `SPAWNER_EXP_CLAIM` | `{exp_amount}` |
| `SPAWNER_SELL_ALL` | `{items_sold}` `{total_price}` |
| `SPAWNER_ITEM_TAKE_ALL` | `{items_taken}` `{items_left}` |
| `SPAWNER_ITEMS_SORT` | `{sort_item}` `{previous_sort}` |
| `SPAWNER_DROP_PAGE_ITEMS` | `{items_dropped}` `{page_number}` |
| `SPAWNER_STORAGE_OPEN` | `{page}` `{total_pages}` |
| `COMMAND_*` | `{full_command}` |

---

## Performance Notes

- **If `enabled: false`** – `DiscordEmbedConfigManager` is never instantiated; zero memory is allocated for embed configs.
- **Lazy loading** – each event's `.yml` file is read from disk only on the first webhook for that event type, then cached in an `EnumMap` for O(1) lookup.
- **Only active events are extracted** – files are generated only for events listed in `logged_events` (or all events when `log_all_events: true`).
- **Rate limiting** – max 25 webhook requests per minute (Discord allows 30); excess entries are queued and retried on the next tick window.
