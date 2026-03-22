---
title: Discord Webhook Integration
description: Send spawner event logs to Discord with rich embeds – configuration guide for discord.yml
---

SmartSpawner can forward spawner events to a Discord channel via webhooks.
All Discord settings live in a **dedicated `discord.yml`** file inside the plugin data folder (`plugins/SmartSpawner/discord.yml`).
This file is created automatically on first startup and is version-managed – your customisations are preserved across plugin updates.

:::note[Prerequisite]
File-based logging must be enabled first.  Set `logging.enabled: true` in `config.yml`.
:::

---

## Creating a Webhook

1. Open your Discord server → **Server Settings** → **Integrations** → **Webhooks** → **New Webhook**.
2. Choose the target channel, copy the webhook URL.
3. Paste it into `discord.yml` under `webhook_url`.

---

## Quick Setup

```yaml
# discord.yml
enabled: true
webhook_url: "https://discord.com/api/webhooks/YOUR_ID/YOUR_TOKEN"
```

Reload with `/smartspawner reload` or restart the server.

---

## Full Configuration Reference

```yaml
# Enable / disable Discord webhook delivery.
enabled: false

# The webhook URL from Discord.
webhook_url: ""

# Show the acting player's Minecraft head as the embed thumbnail.
show_player_head: true

# Forward every logged event (overrides the logged_events list below).
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

### Available Events

| Event | Description |
|---|---|
| `SPAWNER_PLACE` | Spawner placed by a player |
| `SPAWNER_BREAK` | Spawner broken by a player |
| `SPAWNER_EXPLODE` | Spawner destroyed by an explosion |
| `SPAWNER_STACK_HAND` | Spawner stacked by hand |
| `SPAWNER_STACK_GUI` | Spawner stacked via the stacker GUI |
| `SPAWNER_DESTACK_GUI` | Spawner destacked via the stacker GUI |
| `SPAWNER_GUI_OPEN` | Main spawner GUI opened |
| `SPAWNER_STORAGE_OPEN` | Storage GUI opened |
| `SPAWNER_STACKER_OPEN` | Stacker GUI opened |
| `SPAWNER_EXP_CLAIM` | Experience collected from a spawner |
| `SPAWNER_SELL_ALL` | All items sold |
| `SPAWNER_ITEM_TAKE_ALL` | All items taken from storage |
| `SPAWNER_ITEM_DROP` | Item dropped from storage |
| `SPAWNER_ITEMS_SORT` | Storage items sorted |
| `SPAWNER_ITEM_FILTER` | Item filter toggled |
| `SPAWNER_DROP_PAGE_ITEMS` | All items on a page dropped |
| `SPAWNER_EGG_CHANGE` | Entity type changed via spawn egg |
| `COMMAND_EXECUTE_PLAYER` | Command run by a player |
| `COMMAND_EXECUTE_CONSOLE` | Command run by the console |
| `COMMAND_EXECUTE_RCON` | Command run via RCON |

---

## Embed Appearance

### YAML mode (default, recommended)

Set `embed_format: yaml` and customise the structured keys below.

```yaml
embed_format: yaml

embed:
  title: "{description}"
  description: "{description}"
  footer: "SmartSpawner • {time}"

  # Per-event hex colour codes (no leading #).
  colors:
    SPAWNER_PLACE:   "57F287"   # Green
    SPAWNER_BREAK:   "ED4245"   # Red
    SPAWNER_EXPLODE: "FF6B6B"   # Light Red
    DEFAULT:         "99AAB5"   # Grey fallback

  # Optional extra fields added to every embed.
  fields:
    - name: "Server"
      value: "Survival"
      inline: true
```

### JSON mode (advanced – full control)

Set `embed_format: json` and provide a complete Discord webhook JSON payload as a template.
All `{placeholder}` tokens are replaced per-event before the payload is sent.

```yaml
embed_format: json

embed_json: |
  {
    "embeds": [
      {
        "title": "🔔 {event_type}",
        "description": "{description}\n\n👤 `{player}` • 📍 `{location}`",
        "color": {color},
        "footer": { "text": "SmartSpawner • {time}" },
        "timestamp": "{timestamp}"
      }
    ]
  }
```

:::caution[Valid JSON required]
The value must be valid JSON.  Use `\n` for newlines **inside** JSON string values – do not insert literal line breaks inside a string.
:::

### Supported Placeholders

| Placeholder | Value |
|---|---|
| `{player}` | Player name (`N/A` for console events) |
| `{player_uuid}` | Player UUID |
| `{description}` | Human-readable event description |
| `{event_type}` | Raw event name (e.g. `SPAWNER_PLACE`) |
| `{time}` | Formatted time (`HH:mm:ss`) |
| `{timestamp}` | ISO 8601 timestamp (Discord native date) |
| `{location}` | `world (x, y, z)` |
| `{world}` | World name |
| `{x}` `{y}` `{z}` | Integer coordinates |
| `{entity}` | Mob / entity name |
| `{color}` | Decimal colour integer for this event |
| `{stack_size}`, `{amount}`, … | Any metadata key from the event |

---

## Per-Event Colour Reference

| Category | Event | Default Colour |
|---|---|---|
| Commands | `COMMAND_EXECUTE_PLAYER` | `#5865F2` Blurple |
| Commands | `COMMAND_EXECUTE_CONSOLE` | `#3B5998` Dark Blue |
| Commands | `COMMAND_EXECUTE_RCON` | `#7289DA` Light Blurple |
| Lifecycle | `SPAWNER_PLACE` | `#57F287` Green |
| Lifecycle | `SPAWNER_BREAK` | `#ED4245` Red |
| Lifecycle | `SPAWNER_EXPLODE` | `#FF6B6B` Light Red |
| Stacking | `SPAWNER_STACK_HAND` | `#FEE75C` Yellow |
| Stacking | `SPAWNER_STACK_GUI` | `#F1C40F` Gold |
| Stacking | `SPAWNER_DESTACK_GUI` | `#E67E22` Orange |
| GUI | `SPAWNER_GUI_OPEN` | `#9B59B6` Purple |
| GUI | `SPAWNER_STORAGE_OPEN` | `#8E44AD` Dark Purple |
| GUI | `SPAWNER_STACKER_OPEN` | `#BB8FCE` Light Purple |
| Economy | `SPAWNER_EXP_CLAIM` | `#2ECC71` Emerald |
| Economy | `SPAWNER_SELL_ALL` | `#27AE60` Dark Green |
| Items | `SPAWNER_ITEM_TAKE_ALL` | `#1ABC9C` Turquoise |
| Items | `SPAWNER_ITEM_DROP` | `#16A085` Dark Turquoise |
| Items | `SPAWNER_ITEMS_SORT` | `#48C9B0` Light Teal |
| Items | `SPAWNER_ITEM_FILTER` | `#45B7AF` Teal |
| Items | `SPAWNER_DROP_PAGE_ITEMS` | `#138D75` Dark Teal |
| Entity | `SPAWNER_EGG_CHANGE` | `#E91E63` Pink |
| Fallback | `DEFAULT` | `#99AAB5` Grey |

