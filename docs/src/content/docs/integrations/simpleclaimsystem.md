---
title: SimpleClaimSystem Integration
description: Comprehensive guide for integrating SmartSpawner with SimpleClaimSystem for enhanced spawner protection and management.
---

## Overview

This integration ensures that spawner-related actions respect claim permissions, preventing unauthorized modifications within protected claims.

## Required Permissions

### Destroy Permission
- **Permission:** `Destroy`
- **Purpose:** Breaking spawners
- **Description:** Allows players to break or remove spawners within a claim

### InteractBlocks Permission
- **Permission:** `InteractBlocks`
- **Purpose:** 
  - Stacking spawners
  - Accessing spawner interfaces/GUIs
- **Description:** Enables interaction with spawner blocks for stacking and GUI access within a claim

## Granting Permissions

Permissions are managed through SimpleClaimSystem's claim settings interface:

1. Access the claim settings GUI (using `/claim settings` command)
2. Locate the relevant permission toggles
3. Enable `Destroy` and `InteractBlocks` for appropriate players/groups

![Claim Settings GUI](https://www.spigotmc.org/attachments/upload_2025-4-3_21-47-29-gif.887408/)

## Related Configuration

For reference, here are the relevant sections from SimpleClaimSystem's `config.yml`:

### Status Settings
```yaml
status-settings:
  Build: true
  Destroy: true
  Buttons: true
  Items: true
  InteractBlocks: true
  # ... other settings
```

### Default Values
```yaml
default-values-settings:
  Members:
    Build: true
    Destroy: true
    Buttons: true
    Items: true
    InteractBlocks: true
    # ... other settings
```

**Full Configuration:** [SimpleClaimSystem config.yml](https://github.com/Xyness/SimpleClaimSystem/blob/main/src/main/resources/config.yml#L284)

<br>
<br>

---

*Last update: September 21, 2025 13:38:49*