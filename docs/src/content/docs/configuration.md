---
title: Main Configuration
description: Detailed guide to configuring SmartSpawner plugin settings
---

This page provides a comprehensive overview of the `config.yml` file for the SmartSpawner plugin. The configuration file allows you to customize various aspects of the plugin's behavior, from spawner properties to economy settings and visual effects.

## Time Format Guide

The plugin uses a flexible time format for durations:

- **Simple formats**: `20s` (20 seconds), `5m` (5 minutes), `1h` (1 hour)
- **Complex format**: `1d_2h_30m_15s` (1 day, 2 hours, 30 minutes, 15 seconds)
- **Units**: `s` = seconds, `m` = minutes, `h` = hours, `d` = days, `w` = weeks, `mo` = months, `y` = years

## Adding Custom Language

To add a custom language:

1. Create a new folder in the language directory.
2. Use files from `en_US` as templates.
3. Modify `messages.yml`, `formatting.yml`, etc.
4. Set `language` to your custom folder name.

## Language Settings

```yaml
# Language setting (available: en_US, vi_VN, DonutSMP)
language: en_US

# Spawner GUI layout configuration (available: default, DonutSMP)
gui_layout: default

# Enable or disable debug mode (provides verbose console output)
debug: false
```

- **language**: Sets the language for plugin messages. Available options include `en_US`, `vi_VN`, and `DonutSMP`.
- **gui_layout**: Chooses the layout for the spawner GUI. Options are `default` and `DonutSMP`.
- **debug**: Enables debug mode for detailed console output, useful for troubleshooting.

## Core Spawner Properties

```yaml
spawner_properties:
  default:
    # Spawn Parameters - Controls mob generation frequency and amounts
    min_mobs: 1         # Minimum mobs spawned per cycle
    max_mobs: 4         # Maximum mobs spawned per cycle
    range: 16           # Player proximity required for activation (in blocks)
    delay: 25s          # Base delay between spawn cycles

    # Storage Settings - Defines internal inventory capacity
    max_storage_pages: 1  # Each page provides 45 inventory slots
    max_stored_exp: 1000  # Maximum experience points that can be stored
    max_stack_size: 10000  # Maximum number of spawners that can be stacked

    # Behavior Settings - Controls special spawner functionality
    allow_exp_mending: true   # Allow spawners to repair items with stored XP
    protect_from_explosions: true   # Protect spawner blocks from explosion
```

These settings define the default behavior for all spawners:

- **Spawn Parameters**:
  - `min_mobs` / `max_mobs`: Range of mobs spawned per cycle.
  - `range`: Distance in blocks a player must be within for the spawner to activate.
  - `delay`: Time between spawn cycles.

- **Storage Settings**:
  - `max_storage_pages`: Number of inventory pages (45 slots each).
  - `max_stored_exp`: Maximum XP that can be stored.
  - `max_stack_size`: Maximum spawners that can be stacked together.

- **Behavior Settings**:
  - `allow_exp_mending`: Enables repairing items using stored XP.
  - `protect_from_explosions`: Prevents spawner blocks from being destroyed by explosions.

## Spawner Breaking Mechanics

```yaml
spawner_break:
  enabled: true         # Master switch for spawner breaking feature

  # Whether to directly add spawner items to player inventory instead of dropping them on the ground
  direct_to_inventory: false

  # Tool Requirements - Which tools can break spawners
  required_tools:
    - IRON_PICKAXE
    - GOLDEN_PICKAXE
    - DIAMOND_PICKAXE
    - NETHERITE_PICKAXE

  # Durability impact on tools when breaking a spawner
  durability_loss: 1    # Number of durability points deducted

  # Enchantment Requirements for successful spawner collection
  silk_touch:
    required: true      # Whether Silk Touch is needed to obtain spawners
    level: 1            # Minimum level of Silk Touch required
```

Controls how players can break and collect spawners:

- `enabled`: Toggles the breaking feature.
- `direct_to_inventory`: Adds spawners directly to inventory instead of dropping.
- `required_tools`: List of tools that can break spawners.
- `durability_loss`: Durability points lost per break.
- `silk_touch`: Requires Silk Touch enchantment to collect spawners.

## Spawner Limitations

```yaml
spawner_limits:
  # Maximum number of spawners (including stacks) allowed per chunk
  # Set to -1 for unlimited spawners per chunk
  # Each spawner in a stack counts toward the limit (not just 1 per stack)
  # Example: 1 spawner with 64 stack + 1 spawner with 6 stack = 70 total count in chunk
  max_per_chunk: -1
```

- `max_per_chunk`: Limits the number of spawners per chunk. Set to `-1` for unlimited.

## Natural/Vanilla Spawner Settings

```yaml
natural_spawner:
  # Whether natural spawners can be broken and collected
  breakable: false

  # Convert natural spawners to smart spawners when broken
  # If false, natural spawners will drop vanilla spawner items
  convert_to_smart_spawner: false

  # Whether natural spawners will spawn mobs
  spawn_mobs: true

  # Whether natural spawner block will be protected from explosions
  protect_from_explosions: false
```

Settings for naturally generated dungeon spawners:

- `breakable`: Allows breaking natural spawners.
- `convert_to_smart_spawner`: Converts natural spawners to SmartSpawners when broken.
- `spawn_mobs`: Enables mob spawning from natural spawners.
- `protect_from_explosions`: Protects natural spawners from explosions.

## Economy Settings

```yaml
custom_economy:
  # Enable or disable selling items from spawners
  enabled: true

  # Supported types: VAULT, COINSENGINE (more will be added in the future)
  currency: VAULT

  # Specifies the name of the currency used by COINSENGINE
  # This setting is only required when using COINSENGINE as the economy currency
  coinsengine_currency: coins

  # Price source modes (see detailed explanations below)
  price_source_mode: SHOP_PRIORITY

  # Shop plugin integration
  shop_integration:
    enabled: true
    # Supported shop plugins: auto, EconomyShopGUI, EconomyShopGUI-Premium, ShopGUIPlus, zShop, ExcellentShop
    preferred_plugin: auto

  # Custom sell price configuration
  custom_prices:
    enabled: true
    price_file_name: "item_prices.yml"
    default_price: 1.0
```

Configures the economy system for selling spawner items:

- `enabled`: Toggles economy features.
- `currency`: Economy plugin to use (`VAULT` or `COINSENGINE`).
- `coinsengine_currency`: Currency name for CoinsEngine.
- `price_source_mode`: Determines how prices are sourced (see below).
- `shop_integration`: Integrates with shop plugins.
- `custom_prices`: Uses custom price file for selling.

### Price Source Modes

- **SHOP_ONLY**: Uses only shop integration prices.
- **SHOP_PRIORITY**: Prefers shop prices, falls back to custom.
- **CUSTOM_ONLY**: Uses only custom prices.
- **CUSTOM_PRIORITY**: Prefers custom prices, falls back to shop.

## Item Collection System

```yaml
hopper:
  enabled: false
  check_delay: 3s       # Time between collection checks
  stack_per_transfer: 5 # Number of item stacks transferred in one operation (max 5)
```

- `enabled`: Enables automatic item collection.
- `check_delay`: Frequency of collection checks.
- `stack_per_transfer`: Stacks transferred per operation.

## Visual Effects

### Hologram

```yaml
hologram:
  enabled: false        # Show floating text above spawners

  text:
    - '[&#f8f8ff%stack_size%] &#7b68ee%ᴇɴᴛɪᴛʏ% ꜱᴘᴀᴡɴᴇʀ'
    - '&#ab7afd• &#e6e6faxᴘ: &#37eb9a%current_exp%&#f8f8ff/&#37eb9a%max_exp%'
    - '&#ab7afd• &#e6e6faɪᴛᴇᴍꜱ: &#37eb9a%used_slots%&#f8f8ff/&#37eb9a%max_slots%'

  # Position Offset from spawner block center
  offset_x: 0.5
  offset_y: 1.6
  offset_z: 0.5

  # Display Settings
  alignment: CENTER     # Text alignment (CENTER, LEFT, or RIGHT)
  shadowed_text: true   # Apply shadow effect to text
  see_through: false    # Text visible through blocks
```

- `enabled`: Shows floating text above spawners.
- `text`: Customizable hologram lines with placeholders.
- `offset_*`: Position adjustments.
- `alignment`, `shadowed_text`, `see_through`: Display options.

### Particles

```yaml
particle:
  spawner_stack: true           # Show effects when spawners are stacked
  spawner_activate: true        # Show effects when spawner activates
  spawner_generate_loot: true   # Show effects when items are generated
```

Toggles particle effects for various spawner actions.

## Data Management

```yaml
data_saving:
  # Periodic auto-save interval
  interval: 5m          # Time between saves

  # Save spawner data on server shutdown
  save_on_shutdown: true
```

- `interval`: Auto-save frequency.
- `save_on_shutdown`: Saves data on server stop.

## Ghost Spawners

```yaml
ghost_spawners:
  # Remove ghost spawners when server starts up
  remove_on_startup: true

  # Remove ghost spawners when players approach them
  remove_on_approach: false
```

Handles erroneous spawners without physical blocks:

- `remove_on_startup`: Cleans up on server start.
- `remove_on_approach`: Removes when players get near.