---
title: Item Spawner Settings Configuration
description: Configure item spawner appearance, loot drops, and experience values for resource generation.
---

SmartSpawner allows you to create item spawners that generate resources without spawning mobs. Configure item spawner appearance, drops, and experience through the `item_spawners_settings.yml` file.

:::note[Important: Drop Multiplier]
Each time a spawner is triggered, it will generate between **min_mobs** and **max_mobs** (default: 1 to 4) times the drops specified below. This means the actual drops can be significantly higher than the base amounts configured.
:::

:::caution[Current Limitations]
Item spawners currently **do not support** potions and enchanted books. Only **tipped arrows** are supported with potion effects.
:::

## Configuration Format

```yaml
# Global default for unknown items
default_material: "SPAWNER"

ITEM_MATERIAL:
  material: <MATERIAL>
  experience: <number>
  loot:
    ITEM_ID:
      amount: <min>-<max>
      chance: <percentage>
      # Optional properties
      potion_type: <POTION_TYPE>  # For tipped arrows only
  head_texture:
    material: <MATERIAL>
    custom_texture: <texture_hash>  # null for vanilla materials
```

## Core Properties

### Item Spawner Properties

| Property | Format | Description |
|----------|--------|-------------|
| **default_material** | `"SPAWNER"` | Fallback material for unknown items (global) |
| **material** | `"DIAMOND"` | The primary material this spawner generates |
| **experience** | `1` | XP generated per spawner trigger |

### Head Texture Properties

| Property | Format | Description |
|----------|--------|-------------|
| **material** | `"DIAMOND"` | Visual material for the spawner head |
| **custom_texture** | `"abc123..."` | Base64 texture hash (use `null` for vanilla materials) |

### Loot Properties

| Property | Format | Description |
|----------|--------|-------------|
| **amount** | `1-1` | Base quantity range (multiplied by min_mobs to max_mobs) |
| **chance** | `100.0` | Drop probability (0.0-100.0) |
| **potion_type** | `POISON` | Potion type for tipped arrows only |

### Validation Rules
- Experience must be non-negative number
- Amount format: `min-max` where min ≤ max
- Chance between 0.0-100.0
- Custom texture can be `null` for vanilla materials
- Potion types only work with `TIPPED_ARROW` material

## Understanding Drop Mechanics

### Base Drops vs Actual Drops
The `amount` value in the configuration is the **base amount** per spawner cycle. The actual amount generated is:

```
Actual Drops = Base Amount × Random(min_mobs, max_mobs)
```

For example, if `min_mobs=1` and `max_mobs=4` (defaults):
- Base amount `1-1` → Actual drops: `1-4` items
- Base amount `2-3` → Actual drops: `2-12` items
- Base amount `1-2` → Actual drops: `1-8` items

### Multiple Loot Entries
Each loot entry is rolled independently:
1. Check if the drop occurs (based on `chance`)
2. Determine base amount (within `amount` range)
3. Multiply by spawner trigger count (min_mobs to max_mobs)

This means a spawner can generate multiple different items from a single trigger.

## Supported Types

### Common Materials
**Full List:** [Paper Material Documentation](https://jd.papermc.io/paper/org/bukkit/Material.html)


### Potion Types (for Tipped Arrows Only)
**Full List:** [Paper PotionType Documentation](https://jd.papermc.io/paper/org/bukkit/potion/PotionType.html)

Potion types use predefined names from the Paper API:

- **Basic**: Standard effect duration and potency
- **Extended**: Same potency but longer duration (prefix: `LONG_`)
- **Strong**: Higher potency but standard duration (prefix: `STRONG_`)

Examples:
- `POISON`: Causes damage over time
- `LONG_POISON`: Extended poison effect
- `STRONG_POISON`: Stronger poison
- `HEALING`, `SWIFTNESS`, `STRENGTH`, etc.

:::caution
Potion types are **only supported for tipped arrows**. Potions and enchanted books are not currently supported in item spawners.
:::

## Examples

### Basic Resource Spawner
```yaml
# Simple diamond spawner
DIAMOND:
  material: "DIAMOND"
  experience: 1
  loot:
    DIAMOND:
      amount: 1-1  # Will generate 1-4 diamonds per trigger (multiplied by min_mobs to max_mobs)
      chance: 100.0
  head_texture:
    material: "DIAMOND"
    custom_texture: null
```

### Rare Item Spawner
```yaml
# Nether star spawner with guaranteed drops
NETHER_STAR:
  material: "NETHER_STAR"
  experience: 1
  loot:
    NETHER_STAR:
      amount: 1-1  # Will generate 1-4 nether stars per trigger
      chance: 100.0
  head_texture:
    material: "NETHER_STAR"
    custom_texture: null
```

### Item Spawner with Custom Head
```yaml
# Emerald spawner with custom player head texture
EMERALD:
  material: "EMERALD"
  experience: 1
  loot:
    EMERALD:
      amount: 1-1
      chance: 100.0
  head_texture:
    material: "PLAYER_HEAD"
    custom_texture: "abc123def456..."  # Custom base64 texture
```

### Multiple Drop Item Spawner
```yaml
# Gold spawner with variable drops
GOLD_INGOT:
  material: "GOLD_INGOT"
  experience: 1
  loot:
    GOLD_INGOT:
      amount: 1-2  # Will generate 1-8 gold ingots per trigger (1-2 multiplied by 1-4)
      chance: 100.0
    GOLD_NUGGET:
      amount: 3-5  # Additional chance for gold nuggets
      chance: 50.0
  head_texture:
    material: "GOLD_INGOT"
    custom_texture: null
```

### Tipped Arrow Item Spawner
```yaml
# Tipped arrow spawner with poison effect
TIPPED_ARROW:
  material: "TIPPED_ARROW"
  experience: 1
  loot:
    TIPPED_ARROW:
      amount: 8-16  # Will generate 8-64 poison arrows per trigger
      chance: 100.0
      potion_type: POISON
  head_texture:
    material: "TIPPED_ARROW"
    custom_texture: null
```

### Item Spawner with Chance-Based Drops
```yaml
# Totem spawner with lower drop chance
TOTEM_OF_UNDYING:
  material: "TOTEM_OF_UNDYING"
  experience: 2
  loot:
    TOTEM_OF_UNDYING:
      amount: 1-1
      chance: 75.0  # 75% chance to drop per trigger
    EMERALD:
      amount: 1-3
      chance: 50.0  # 50% chance for bonus emeralds
  head_texture:
    material: "TOTEM_OF_UNDYING"
    custom_texture: null
```

## Default Configuration

SmartSpawner includes basic default configurations for common valuable items.

- **View Online:** [GitHub Repository](https://github.com/NighterDevelopment/smartspawner/blob/main/core/src/main/resources/item_spawners_settings.yml)
- **Auto-Regenerate:** Delete `item_spawners_settings.yml` and restart server

## Head Texture Notes

### Using Vanilla Materials
For most items, you can use the vanilla material directly:
```yaml
head_texture:
  material: "DIAMOND"
  custom_texture: null
```

### Using Custom Textures
For custom player head textures:
```yaml
head_texture:
  material: "PLAYER_HEAD"
  custom_texture: "a3b9003ba2d05562c75119b8a62185c67130e9282f7acbac4bc2824c21eb95d9"
```

Custom player head textures can be obtained from:
- [Minecraft-Heads.com](https://minecraft-heads.com/)
- [MCHeads.net](https://mc-heads.net/)
- [MCHeads.ru](https://mcheads.ru/en)
- Extract from existing player heads using `/give` commands

## Command Usage

To give item spawners to players, use:

```bash
/ss give item_spawner <player> <item_type> [amount]
```

**Example:**
```bash
/ss give item_spawner @p diamond 1
/ss give item_spawner Player123 netherite_ingot 5
```

See the [Commands](/commands) page for more details.
