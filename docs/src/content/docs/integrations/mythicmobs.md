---
title: MythicMobs Integration
description: How to integrate SmartSpawner with MythicMobs for custom mob drops.
---

**SmartSpawner** seamlessly integrates with **MythicMobs**, allowing you to configure spawners as mob drops using a simple and intuitive syntax.

## Syntax

Use the following syntax in your MythicMobs configuration file:

```yaml
smartspawner <entity> <quantity/range>
```

### Parameters
- `<entity>`: The Minecraft entity type (supports all vanilla Minecraft entities)
- `<quantity/range>`: Either a fixed number or a range (min-max)

## Examples

### Example 1: Fixed Quantity
```yaml
smartspawner BLAZE 1
```
**Result:** The mob will drop **1 blaze spawner** when killed.

### Example 2: Random Range
```yaml
smartspawner BLAZE 1-5
```
**Result:** The mob will drop a **random number of blaze spawners between 1 and 5** when killed.

### Example 3: Multiple Entity Types
```yaml
drops:
  - smartspawner ZOMBIE 1
  - smartspawner SKELETON 2-3
  - smartspawner CREEPER 1
```
**Result:** The mob will drop multiple different spawner types with their respective quantities.

## Complete MythicMobs Example

```yaml
CustomBoss:
  Type: ZOMBIE
  Health: 100
  Drops:
  - smartspawner BLAZE 1-2 0.5
  - smartspawner WITHER_SKELETON 1 0.25
  - gold_ingot 5-10 1.0
```

This configuration creates a custom boss that has a 50% chance to drop 1-2 blaze spawners and a 25% chance to drop 1 wither skeleton spawner.

---

*Last update: September 15, 2025 16:32:48*