---
title: HuskClaims Integration
description: Guide for integrating SmartSpawner with HuskClaims for spawner protection within claimed land.
---

SmartSpawner automatically respects HuskClaims land protection. No additional configuration is required, once both plugins are detected on the server, spawner actions are validated against HuskClaims' permission system.

## How It Works

SmartSpawner maps each spawner action to a HuskClaims operation type:

| Spawner Action | HuskClaims Operation |
|---|---|
| Break spawner | `BLOCK_BREAK` |
| Stack spawner | `BLOCK_INTERACT` |
| Open spawner GUI | `BLOCK_INTERACT` |

If HuskClaims denies the operation for that player at that location, SmartSpawner will block the action.

## Trust Levels

HuskClaims uses a trust-based permission model. The table below shows which default trust levels allow each spawner action:

| Trust Level | Break Spawner | Stack Spawner | Open GUI |
|---|:---:|:---:|:---:|
| **Build & Container** | ✅ | ✅ | ✅ |
| **Container** | ❌ | ✅ | ✅ |
| **Access** | ❌ | ✅ | ✅ |
| **None / Untrusted** | ❌ | ❌ | ❌ |

> The claim owner and server administrators always retain full access regardless of trust level.

## Granting Access

Use HuskClaims' standard `/trust` commands while standing in the claimed area:

```
/trust <player> build      # Allows break, stack, and open GUI
/trust <player> container  # Allows stack and open GUI
/trust <player> access     # Allows stack and open GUI
```

To remove trust:
```
/untrust <player>
```

## Admin Bypass

Server operators or players with the `huskclaims.bypass` permission will bypass all claim checks and can perform any spawner action regardless of claim ownership.

## Notes

- This integration works with both single-server and cross-server (proxy) HuskClaims setups.
- Child claims inherit parent claim trust levels unless overridden.
- SmartSpawner does **not** create or modify HuskClaims flags — it only reads operation permissions at runtime.

<br>
<br>

---

*Last update: March 30, 2026 8:29:45*
