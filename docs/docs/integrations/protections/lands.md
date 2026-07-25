---
title: Lands
---

# Lands

SmartSpawner checks Lands role flags against the `SPAWNER` material.

| Action | Flag |
|---|---|
| Open menu | `INTERACT_CONTAINER` |
| Stack | `BLOCK_PLACE` |
| Break | `BLOCK_BREAK` |

## Configure roles

Open the Lands menu and enable the matching flag for the role you want to allow:

```bash
/lands menu
```

- **Interact Container** allows opening the spawner menu.
- **Block Place** allows stacking.
- **Block Break** allows breaking.

If Lands is not enabled in the world where the spawner sits, no check is applied and the action is allowed.
