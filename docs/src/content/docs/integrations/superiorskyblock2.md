---
title: SuperiorSkyblock2 Integration
description: Advanced setup guide for SmartSpawner with SuperiorSkyblock2.
---

## Introduction

This integration provides fine-grained control over spawner interactions on islands, ensuring that only authorized players can:
- Stack spawners together
- Access the spawner management menu

## Required Permissions

Add the following permissions to your `permissions.yml` file located in `SuperiorSkyblock2/menus/`:

- `spawner_stack` - Controls who can stack spawners
- `spawner_open_menu` - Controls who can access the spawner menu

## Configuration Examples

### Spawner Stacking Permission

Add this configuration to enable spawner stacking permissions:

```yaml
spawner_stack:
  display-menu: true
  permission-enabled:
    type: SPAWNER
    name: '&fSpawner Stack'
    lore:
      - '&fPermission that allows the user to stack spawners.'
      - '&fStatus: &aEnabled'
  permission-disabled:
    type: SPAWNER
    name: '&fSpawner Stack'
    lore:
      - '&fPermission that allows the user to stack spawners.'
      - '&fStatus: &cDisabled'
  role-permission:
    type: SPAWNER
    name: '&fSpawner Stack'
    lore:
      - '&fPermission that allows the user to stack spawners.'
      - '&fRole: &e{}&f.'
      - ''
      - '{0}'
  has-access:
    sound:
      type: ENTITY_EXPERIENCE_ORB_PICKUP
      volume: 0.2
      pitch: 0.2
  no-access:
    sound:
      type: BLOCK_ANVIL_PLACE
      volume: 0.2
      pitch: 0.2
```

### Spawner Menu Access Permission

Add this configuration to control spawner menu access:

```yaml
spawner_open_menu:
  display-menu: true
  permission-enabled:
    type: SPAWNER
    name: '&fSpawner Menu'
    lore:
      - '&fPermission that allows the user to open the spawner menu.'
      - '&fStatus: &aEnabled'
  permission-disabled:
    type: SPAWNER
    name: '&fSpawner Menu'
    lore:
      - '&fPermission that allows the user to open the spawner menu.'
      - '&fStatus: &cDisabled'
  role-permission:
    type: SPAWNER
    name: '&fSpawner Menu'
    lore:
      - '&fPermission that allows the user to open the spawner menu.'
      - '&fRole: &e{}&f.'
      - ''
      - '{0}'
  has-access:
    sound:
      type: ENTITY_EXPERIENCE_ORB_PICKUP
      volume: 0.2
      pitch: 0.2
  no-access:
    sound:
      type: BLOCK_ANVIL_PLACE
      volume: 0.2
      pitch: 0.2
```

## Configuration Breakdown

### Permission States
- **permission-enabled**: Display when the permission is granted
- **permission-disabled**: Display when the permission is denied  
- **role-permission**: Display for role-based permissions

### Audio Feedback
- **has-access**: Pleasant sound when permission is granted
- **no-access**: Warning sound when permission is denied

### Display Options
- **display-menu**: Whether to show this permission in the island permissions GUI
- **type**: Icon type (SPAWNER in this case)
- **name**: Display name with color formatting
- **lore**: Description text with status indicators

## Setting Default Permissions

You can configure these permissions as defaults for new islands by editing the `island-roles` section in SuperiorSkyblock2's `config.yml`:

```yaml
island-roles:
  Owner:
    permissions:
      - spawner_stack
      - spawner_open_menu
  Admin:
    permissions:
      - spawner_stack
      - spawner_open_menu
  Moderator:
    permissions:
      - spawner_open_menu
  Member:
    permissions: []
```

## Usage Instructions

1. **Installation**: Place the permission configurations in `SuperiorSkyblock2/menus/permissions.yml`

2. **Island Management**: Island owners can use `/is permissions` to manage these permissions

3. **Role Assignment**: Assign permissions to specific roles or individual players through the SuperiorSkyblock2 GUI

4. **Permission Verification**: Players will see visual and audio feedback when attempting to use spawner features

---

*Last update: September 15, 2025 16:32:48*