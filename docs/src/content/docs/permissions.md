---
title: Permissions Reference
description: Complete permission reference for SmartSpawner.
---

## Permissions System

SmartSpawner uses a comprehensive permission system to control access to features.

### Default Permission Values

Understanding the **Default** column in the permissions table:

- **`op`** - Only server operators (admins) have this permission by default
- **`true`** - All players have this permission by default (everyone can use this feature)
- **`false`** - No players have this permission by default (must be explicitly granted)

### Permission Nodes

> **Note:** To execute commands, you must first grant the base permission `smartspawner.command.use`, then grant the specific command permission (e.g., both `smartspawner.command.use and smartspawner.command.reload are required to use the reload command)

#### Command Permissions

| **Permission**                    | **Description**                                       | **Default** |
|-----------------------------------|-------------------------------------------------------|-------------|
| `smartspawner.command.use`       | Main command access (base permission for all commands) | `op`        |
| `smartspawner.command.reload`    | Permission to reload SmartSpawner plugin               | `op`        |
| `smartspawner.command.give`      | Allow giving spawners to players                       | `op`        |
| `smartspawner.command.list`      | Allow viewing list of spawners in your server          | `op`        |
| `smartspawner.command.hologram`  | Allow toggling hologram for spawners                   | `op`        |
| `smartspawner.command.prices`    | Allow viewing spawner prices GUI                       | `op`        |
| `smartspawner.command.clear`     | Allow clearing holograms and ghost spawners            | `op`        |

#### Feature Permissions

| **Permission**                    | **Description**                                       | **Default** |
|-----------------------------------|-------------------------------------------------------|-------------|
| `smartspawner.changetype`        | Allow changing spawner type with spawn egg           | `op`        |
| `smartspawner.stack`             | Allow stacking spawners                              | `true`      |
| `smartspawner.break`             | Allow breaking spawners                              | `true`      |
| `smartspawner.sellall`           | Allow selling items in spawner storage GUI           | `true`      |

<br>
<br>

---

*Last update: October 16, 2025 15:13:54*
