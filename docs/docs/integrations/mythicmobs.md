---
title: MythicMobs
---

# MythicMobs

SmartSpawner registers a MythicMobs drop named `smartspawner`. Use it in a MythicMobs drop table to create a Smart Spawner item:

```yaml
ExampleDrops:
  Drops:
    - smartspawner ZOMBIE 1
    - smartspawner SKELETON 1-3
```

Syntax:

```text
smartspawner <ENTITY_TYPE> [amount|minimum-maximum]
```

The entity must be a valid Bukkit entity type. Invalid entity names or ranges are rejected and logged.
