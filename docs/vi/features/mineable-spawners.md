---
title: Đào Spawner
---

# Đào Spawner

Người chơi có thể phá và thu thập spawner bằng đúng công cụ và enchantment. Mọi hành vi đều có thể cấu hình:

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

- **`sneak_break`**: Cúi khi phá để lấy tối đa 64 spawner khỏi stack cùng lúc
- **`silk_touch.required`**: Yêu cầu Silk Touch để nhận vật phẩm spawner
- **`drop_chance`**: Đặt tỷ lệ rơi riêng theo mob trong `spawner_mobs.yml`
