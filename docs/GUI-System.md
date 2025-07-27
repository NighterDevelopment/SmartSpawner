# 🖱️ GUI System

SmartSpawner features an advanced, customizable GUI system that provides an intuitive interface for managing spawners. This guide covers all aspects of the GUI system, from basic usage to advanced customization.

## 🎯 Overview

The SmartSpawner GUI system consists of multiple interfaces designed for different purposes:

- **Main Spawner GUI** - Primary spawner management interface
- **Storage GUI** - Inventory management for spawner items
- **Admin List GUI** - Administrative spawner overview
- **Hologram Configuration** - Visual display customization

## 🏠 Main Spawner GUI

### Accessing the GUI
- **Right-click** any SmartSpawner block to open the main GUI
- Ensure you have the `smartspawner.break` permission
- The GUI will display spawner information and controls

### GUI Layout Structure

```
┌─────────────────────────────────────────────────┐
│  [Info]    [Stack]    [Settings]    [Storage]   │
│                                                 │
│           Spawner Information Panel             │
│                                                 │
│  [Exp]     [Collect]   [Upgrade]    [Close]    │
└─────────────────────────────────────────────────┘
```

### 📊 Information Panel

The central information panel displays:

```yaml
Spawner Type: Zombie Spawner
Stack Size: 15 spawners
Stored Experience: 450/1000 XP
Storage: 23/45 slots used
Last Activity: 2 minutes ago
```

#### Available Placeholders
- `%entity%` - Mob type (e.g., "Zombie", "Skeleton")
- `%stack_size%` - Number of stacked spawners
- `%current_exp%` - Current stored experience
- `%max_exp%` - Maximum experience capacity
- `%used_slots%` - Used storage slots
- `%max_slots%` - Total storage slots
- `%last_activity%` - Time since last spawn

### 🔘 Interactive Buttons

#### Stack Management
```yaml
Stack Button:
  Function: Combine spawners
  Requirements: Same mob type
  Left-click: Stack all nearby spawners
  Right-click: Stack from inventory
  Permission: smartspawner.stack
```

#### Storage Access
```yaml
Storage Button:
  Function: Open storage inventory
  Capacity: Configurable pages (45 slots each)
  Features: Item filtering, auto-sorting
  Permission: Built-in access
```

#### Experience Management
```yaml
Experience Button:
  Function: Manage stored XP
  Left-click: Withdraw all XP
  Right-click: Withdraw specific amount
  Shift-click: Use for mending items
  Permission: Built-in access
```

## 📦 Storage GUI System

### Storage Interface Layout

```
Storage GUI (54 slots)
┌─────────────────────────────────────────────────┐
│ [Info] [ ]  [ ]  [ ]  [Nav] [ ]  [ ]  [ ]  [ ]  │
│ [ ]    [ ]  [ ]  [ ]  [ ]   [ ]  [ ]  [ ]  [ ]  │
│ [ ]    [ ]  [ ]  [ ]  [ ]   [ ]  [ ]  [ ]  [ ]  │
│ [ ]    [ ]  [ ]  [ ]  [ ]   [ ]  [ ]  [ ]  [ ]  │
│ [ ]    [ ]  [ ]  [ ]  [ ]   [ ]  [ ]  [ ]  [ ]  │
│ [Sell] [ ]  [ ]  [ ]  [ ]   [ ]  [ ]  [ ] [Close]│
└─────────────────────────────────────────────────┘
```

### Storage Features

#### 📄 Multi-Page System
```yaml
Page Configuration:
  Default Pages: 1 (45 slots)
  Maximum Pages: 10 (450 slots)
  Navigation: Arrow buttons
  Page Indicator: Current/Total display
```

#### 🔄 Item Management
- **Drag & Drop** - Move items between slots
- **Quick Transfer** - Shift-click to move items
- **Auto-Sort** - Automatically organize items
- **Filtering** - Filter by item type or name

#### 💰 Sell-All System
```yaml
Sell-All Button:
  Location: Bottom-left corner
  Function: Sell all items in storage
  Price Source: Configurable (shop/custom)
  Requirements: Economy plugin + permissions
  Confirmation: Optional confirmation dialog
```

### Storage Permissions
```yaml
smartspawner.sellall: true    # Allow selling items
smartspawner.stack: true      # Allow accessing storage
```

## 🛡️ Admin List GUI

### Administrative Overview
The admin list GUI provides server administrators with comprehensive spawner management tools.

#### Accessing Admin GUI
```bash
/ss list
# Requires: smartspawner.list permission
```

#### Admin GUI Features

```
Admin List GUI
┌─────────────────────────────────────────────────┐
│                Spawner List                     │
│                                                 │
│ [Spawner 1] [Location] [Stack] [Teleport]      │
│ [Spawner 2] [Location] [Stack] [Teleport]      │
│ [Spawner 3] [Location] [Stack] [Teleport]      │
│                                                 │
│ [Previous]           [Info]          [Next]    │
└─────────────────────────────────────────────────┘
```

#### Administrative Functions
- **Teleportation** - Quick travel to any spawner
- **Stack Information** - View spawner details
- **Bulk Management** - Mass operations
- **Statistics** - Server-wide spawner data

## 🎨 GUI Customization

### Layout Selection
SmartSpawner supports multiple GUI layouts:

```yaml
# In config.yml
gui_layout: default    # Options: default, DonutSMP
```

#### Available Layouts

**Default Layout**
- Clean, modern design
- Intuitive button placement
- Suitable for most servers

**DonutSMP Layout**
- Community-designed layout
- Enhanced visual elements
- Optimized for specific gameplay

### 🎭 Custom Layout Creation

#### Layout File Structure
```yaml
# gui_layouts/custom/storage_gui.yml
title: "My Custom Storage"
size: 54

buttons:
  info:
    slot: 4
    material: SPAWNER
    name: "&6Spawner Info"
    lore:
      - "&7Type: %entity%"
      - "&7Stack: %stack_size%"
  
  sell_all:
    slot: 49
    material: EMERALD
    name: "&aSell All"
    click_sound: ENTITY_EXPERIENCE_ORB_PICKUP
    
  navigation:
    previous:
      slot: 45
      material: ARROW
      name: "&7Previous Page"
    next:
      slot: 53
      material: ARROW
      name: "&7Next Page"
```

#### Button Configuration Options
```yaml
button_name:
  slot: 0-53              # GUI slot position
  material: MATERIAL_NAME # Minecraft material
  name: "Display Name"    # Button title (color codes supported)
  lore:                   # Tooltip text
    - "Line 1"
    - "Line 2"
  click_sound: SOUND_NAME # Sound effect on click
  enchanted: true         # Add enchantment glow
  amount: 1               # Item stack size
```

### 🌈 Color & Formatting

#### Supported Color Formats
```yaml
# Traditional color codes
name: "&6Golden Text &l&oBold Italic"

# Hex colors (1.16+)
name: "&#FFD700Golden &#FF6B6BRed Text"

# Gradient colors
name: "<gradient:#FF0000:#0000FF>Rainbow Text</gradient>"

# MiniMessage format
name: "<color:#ff0000>Red <color:#00ff00>Green"
```

#### Common Color Codes
| Code | Color | Example |
|------|-------|---------|
| `&0` | Black | `&0Dark Text` |
| `&1` | Dark Blue | `&1Navy Blue` |
| `&2` | Dark Green | `&2Forest Green` |
| `&3` | Dark Aqua | `&3Teal` |
| `&4` | Dark Red | `&4Maroon` |
| `&5` | Dark Purple | `&5Purple` |
| `&6` | Gold | `&6Golden` |
| `&7` | Gray | `&7Silver` |
| `&8` | Dark Gray | `&8Charcoal` |
| `&9` | Blue | `&9Bright Blue` |
| `&a` | Green | `&aLime Green` |
| `&b` | Aqua | `&bCyan` |
| `&c` | Red | `&cBright Red` |
| `&d` | Light Purple | `&dPink` |
| `&e` | Yellow | `&eYellow` |
| `&f` | White | `&fWhite` |

#### Formatting Codes
| Code | Effect | Example |
|------|--------|---------|
| `&l` | Bold | `&lBold Text` |
| `&o` | Italic | `&oItalic Text` |
| `&n` | Underline | `&nUnderlined` |
| `&m` | Strikethrough | `&mCrossed Out` |
| `&k` | Magic/Obfuscated | `&kRandom` |
| `&r` | Reset | `&r&fReset to white` |

## 🔊 Sound Effects

### Available Click Sounds
```yaml
# Common UI sounds
CLICK: UI_BUTTON_CLICK
SUCCESS: ENTITY_EXPERIENCE_ORB_PICKUP
ERROR: ENTITY_VILLAGER_NO
NAVIGATION: ITEM_BOOK_PAGE_TURN

# Action sounds
SELL: ENTITY_PLAYER_LEVELUP
TELEPORT: ENTITY_ENDERMAN_TELEPORT
UPGRADE: BLOCK_ENCHANTMENT_TABLE_USE
```

### Sound Configuration
```yaml
button:
  click_sound: ENTITY_EXPERIENCE_ORB_PICKUP
  sound_volume: 1.0      # 0.0 to 1.0
  sound_pitch: 1.0       # 0.5 to 2.0
```

## 📱 Responsive Design

### Screen Size Adaptation
SmartSpawner GUIs automatically adapt to different screen resolutions and GUI scales:

- **Compact Mode** - For smaller screens
- **Standard Mode** - Default experience
- **Expanded Mode** - For larger displays

### Mobile-Friendly Features
- Large click targets
- Clear visual feedback
- Simplified navigation
- Touch-optimized layouts

## 🔧 Performance Optimization

### GUI Performance Tips

#### Client-Side Optimization
```yaml
# Reduce visual complexity
gui_settings:
  animations: false          # Disable animated elements
  particle_effects: false    # Reduce particle spam
  update_frequency: slow     # Slower GUI updates
```

#### Server-Side Optimization
```yaml
# Optimize GUI handling
performance:
  gui_cache: true           # Cache GUI layouts
  async_loading: true       # Load GUIs asynchronously
  max_concurrent: 50        # Limit concurrent GUIs
```

## 🐛 Troubleshooting GUI Issues

### Common Problems

#### GUI Not Opening
**Symptoms:** Right-click doesn't open GUI

**Solutions:**
1. Check `smartspawner.break` permission
2. Verify spawner is a SmartSpawner (not vanilla)
3. Check for protection plugin conflicts
4. Test in different world/location

#### Buttons Not Working
**Symptoms:** Clicking buttons has no effect

**Solutions:**
1. Check specific button permissions
2. Verify economy plugin for sell button
3. Check console for error messages
4. Test with different player/permission level

#### Visual Glitches
**Symptoms:** Items appear incorrectly, colors wrong

**Solutions:**
1. Update to latest SmartSpawner version
2. Check for resource pack conflicts
3. Test with vanilla client
4. Verify color code syntax

#### Performance Issues
**Symptoms:** GUI loading slowly, lag when opening

**Solutions:**
1. Reduce maximum storage pages
2. Disable hologram updates
3. Optimize layout complexity
4. Check server TPS during GUI usage

## 📋 GUI Best Practices

### For Server Administrators
1. **Test layouts** thoroughly before deploying
2. **Monitor performance** impact of GUI features
3. **Train staff** on admin GUI functions
4. **Regular backups** of custom layouts
5. **Player feedback** on GUI usability

### For Players
1. **Learn shortcuts** for faster navigation
2. **Use storage** efficiently with organization
3. **Understand permissions** for each button
4. **Report issues** to server administrators
5. **Respect limits** set by server configuration

## 🔗 Related Documentation

- **[🔧 Configuration](Configuration.md)** - GUI layout configuration
- **[⌨️ Commands & Permissions](Commands-Permissions.md)** - GUI-related commands
- **[🔍 Troubleshooting](Troubleshooting.md)** - GUI problem solving
- **[💻 API Documentation](API-Documentation.md)** - GUI development API

---

**Master the GUI system** to provide the best spawner management experience for your players! Join our [Discord](http://discord.com/invite/FJN7hJKPyb) for GUI design tips and community layouts.