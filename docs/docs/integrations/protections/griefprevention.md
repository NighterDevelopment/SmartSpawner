---
title: GriefPrevention
---

# GriefPrevention

SmartSpawner requires **Build trust** for every spawner action, including just opening the menu. Container trust and access trust are **not** enough.

| Action | Requirement |
|---|---|
| Open menu | Build trust |
| Stack | Build trust |
| Break | Build trust |

## Grant access

```bash
/trust <player>
```

`/trust` gives full build trust, which covers opening, stacking, and breaking spawners.

## Commands that are not enough

- `/containertrust` allows chests and containers, but not the SmartSpawner GUI.
- `/accesstrust` allows buttons and doors only.

Outside any claim, all actions are allowed.
