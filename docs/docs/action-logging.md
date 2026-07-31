---
title: Action Logging
---

# Action Logging

SmartSpawner can record every important spawner action: who did it, where, and when. Use it for audit trails and moderation. Logging has two independent outputs, local log files and Discord, and each can be turned on or off on its own.

Everything is configured in `activity_log.yml`.

## Log files

Log files are written to the `logs` folder inside the plugin folder. They are set up in the `file` section.

| Option | Default | What it does |
|--------|---------|--------------|
| `enabled` | `true` | Turn the file logging system on or off. |
| `json_format` | `false` | `false` writes human-readable lines. `true` writes structured JSON. |
| `console_output` | `false` | Also print events to the server console. Useful for debugging. |
| `max_log_files` | `10` | How many rotated log files to keep. The oldest are deleted first. |
| `max_log_size_mb` | `10` | Size in MB a log file reaches before it rotates to a new one. |
| `log_all_events` | `false` | Log every event. When `true`, the `logged_events` list is ignored. |
| `logged_events` | list | The specific events to record when `log_all_events` is `false`. |

## Discord logging

SmartSpawner can send the same events to a Discord channel through a webhook. It is set up in the `discord` section, so you can run Discord logging with or without file logging.

### Set up the webhook

1. In Discord, open your server settings.
2. Go to Integrations, then Webhooks, then New Webhook.
3. Choose the target channel and copy the webhook URL.
4. In the `discord` section, set `enabled` to `true` and paste the URL into `webhook_url`.

### Options

| Option | Default | What it does |
|--------|---------|--------------|
| `enabled` | `false` | Turn Discord delivery on or off. |
| `webhook_url` | empty | The Discord webhook URL. Required when enabled. |
| `show_player_head` | `true` | Show the acting player's Minecraft head as the message thumbnail. |
| `log_all_events` | `false` | Forward every event. When `true`, the `logged_events` list is ignored. |
| `logged_events` | list | The specific events to forward when `log_all_events` is `false`. |

### Message appearance

The `embeds` section holds one block per event. You can change the title, color, description, and fields, and use placeholders such as `{player}`, `{location}`, `{entity}`, and `{time}` to fill in the details of each event. An event with no block there is still sent, as a plain message.

## Available events

Both outputs use the same event names.

**Spawner:** `SPAWNER_PLACE`, `SPAWNER_BREAK`, `SPAWNER_EXPLODE`, `SPAWNER_STACK_HAND`, `SPAWNER_STACK_GUI`, `SPAWNER_DESTACK_GUI`, `SPAWNER_EGG_CHANGE`

**GUI:** `SPAWNER_GUI_OPEN`, `SPAWNER_STORAGE_OPEN`, `SPAWNER_STACKER_OPEN`

**Storage and selling:** `SPAWNER_EXP_CLAIM`, `SPAWNER_SELL_ALL`, `SPAWNER_ITEM_TAKE_ALL`, `SPAWNER_ITEM_DROP`, `SPAWNER_ITEMS_SORT`, `SPAWNER_ITEM_FILTER`, `SPAWNER_DROP_PAGE_ITEMS`

**Commands:** `COMMAND_EXECUTE_PLAYER`, `COMMAND_EXECUTE_CONSOLE`, `COMMAND_EXECUTE_RCON`

## Upgrading from an older version

Nothing to do. On the first start, `discord_logging.yml` is renamed to `activity_log.yml` and the `logging` section of `config.yml` moves into it. Your settings are kept.
