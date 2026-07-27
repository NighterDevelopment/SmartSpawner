---
title: Mineable Spawners
---

# Mineable Spawners

Players can break and collect spawners using the correct tools and enchantments. Every aspect is configurable:

```yaml
spawner_break:
  enabled: true
  direct_to_inventory: false
  required_tools:
    - IRON_PICKAXE
    - GOLDEN_PICKAXE
    - DIAMOND_PICKAXE
    - NETHERITE_PICKAXE
  durability_loss: 1
  sneak_break: true
  silk_touch:
    required: true
    level: 1
```

- **`sneak_break`**: Sneak while breaking to remove up to 64 spawners from a stack at once
- **`silk_touch.required`**: Require Silk Touch to collect the spawner item
- **`drop_chance`**: Set an optional per-mob chance that a spawner item drops when broken in `spawner_mobs.yml`
