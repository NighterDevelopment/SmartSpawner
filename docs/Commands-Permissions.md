# ⌨️ Commands & Permissions

This comprehensive guide covers all commands and permissions available in SmartSpawner, helping administrators and players effectively use the plugin's functionality.

## 🎯 Command Overview

SmartSpawner provides a unified command system with multiple aliases for convenience:

- **Primary:** `/smartspawner`
- **Aliases:** `/ss`, `/spawner`

All commands support tab completion for parameters and subcommands.

## 📝 Complete Command List

### 🔄 Administrative Commands

#### Reload Configuration
```bash
/ss reload
```
**Description:** Reloads all configuration files without restarting the server  
**Permission:** `smartspawner.reload`  
**Default:** OP only  
**Usage Examples:**
```bash
/ss reload                    # Reload all configuration
/smartspawner reload         # Alternative syntax
/spawner reload              # Using alias
```

#### Give Spawners
```bash
/ss give <player> <type> [amount] [stackSize]
```
**Description:** Give spawners to yourself or other players  
**Permission:** `smartspawner.give`  
**Default:** OP only  
**Parameters:**
- `<player>` - Target player name or `@p`, `@a`, `@r`
- `<type>` - Mob type (e.g., `zombie`, `skeleton`, `cow`)
- `[amount]` - Number of spawner items (default: 1)
- `[stackSize]` - Size of each spawner stack (default: 1)

**Usage Examples:**
```bash
/ss give Steve zombie                    # Give 1 zombie spawner to Steve
/ss give @p skeleton 5                   # Give 5 skeleton spawners to nearest player
/ss give Alice cow 2 10                  # Give 2 cow spawners to Alice, each with stack size 10
/ss give @a zombie 1 64                  # Give all players 1 zombie spawner with 64 stack size
```

#### List & Manage Spawners
```bash
/ss list [player]
```
**Description:** Open admin GUI to view and manage spawners  
**Permission:** `smartspawner.list`  
**Default:** OP only  
**Parameters:**
- `[player]` - View specific player's spawners (optional)

**Usage Examples:**
```bash
/ss list                     # View all spawners on server
/ss list Steve              # View Steve's spawners only
```

#### Hologram Management
```bash
/ss hologram [toggle|show|hide] [player]
```
**Description:** Manage hologram visibility  
**Permission:** `smartspawner.hologram`  
**Default:** OP only  
**Parameters:**
- `[toggle]` - Toggle hologram visibility
- `[show]` - Force show holograms
- `[hide]` - Force hide holograms
- `[player]` - Target specific player (optional)

**Usage Examples:**
```bash
/ss hologram                 # Toggle holograms for yourself
/ss hologram toggle Steve    # Toggle holograms for Steve
/ss hologram show @a         # Show holograms for all players
/ss hologram hide Alice      # Hide holograms for Alice
```

### ℹ️ Informational Commands

#### Help & Information
```bash
/ss help [page]
/ss info
/ss version
```
**Description:** Display plugin information and help  
**Permission:** None (public)  
**Default:** All players  

**Usage Examples:**
```bash
/ss help                     # Show main help page
/ss help 2                   # Show help page 2
/ss info                     # Show plugin information
/ss version                  # Show version information
```

#### Statistics
```bash
/ss stats [player]
```
**Description:** Display spawner statistics  
**Permission:** `smartspawner.stats`  
**Default:** True for own stats, OP for others  

**Usage Examples:**
```bash
/ss stats                    # Show your own statistics
/ss stats Steve              # Show Steve's statistics (requires permission)
```

## 🔐 Permission System

### 🏷️ Core Permissions

#### Administrative Permissions
```yaml
smartspawner.reload:
  description: "Reload plugin configuration"
  default: op
  usage: "/ss reload command"

smartspawner.give:
  description: "Give spawners to players"
  default: op
  usage: "/ss give command"
  
smartspawner.list:
  description: "Access admin spawner management GUI"
  default: op
  usage: "/ss list command and admin GUI"

smartspawner.hologram:
  description: "Manage hologram visibility"
  default: op
  usage: "/ss hologram command"
```

#### Player Permissions
```yaml
smartspawner.stack:
  description: "Allow stacking spawners together"
  default: true
  usage: "Placing spawners near each other"

smartspawner.break:
  description: "Allow breaking and collecting spawners"
  default: true
  usage: "Breaking spawner blocks with tools"

smartspawner.sellall:
  description: "Allow selling items from spawner storage"
  default: true
  usage: "Sell All button in spawner GUI"

smartspawner.changetype:
  description: "Allow changing spawner type with spawn eggs"
  default: op
  usage: "Right-clicking spawner with spawn egg"
```

#### Special Permissions
```yaml
smartspawner.limits.bypass:
  description: "Bypass spawner limits per chunk"
  default: false
  usage: "Placing spawners beyond chunk limits"

smartspawner.stats:
  description: "View spawner statistics"
  default: true
  usage: "/ss stats command"

smartspawner.stats.others:
  description: "View other players' statistics"
  default: op
  usage: "/ss stats <player> command"
```

### 🎭 Permission Groups

#### Default Player Group
```yaml
permissions:
  - smartspawner.stack
  - smartspawner.break
  - smartspawner.sellall
  - smartspawner.stats
```

#### VIP Player Group
```yaml
permissions:
  - smartspawner.stack
  - smartspawner.break
  - smartspawner.sellall
  - smartspawner.stats
  - smartspawner.changetype
```

#### Moderator Group
```yaml
permissions:
  - smartspawner.*
  - -smartspawner.limits.bypass  # Exclude bypass permission
```

#### Administrator Group
```yaml
permissions:
  - smartspawner.*
```

## 🛠️ Permission Plugin Integration

### LuckPerms Configuration

#### Basic Setup
```bash
# Create permission groups
/lp creategroup spawner_basic
/lp creategroup spawner_vip
/lp creategroup spawner_admin

# Assign basic permissions
/lp group spawner_basic permission set smartspawner.stack true
/lp group spawner_basic permission set smartspawner.break true
/lp group spawner_basic permission set smartspawner.sellall true

# Assign VIP permissions
/lp group spawner_vip parent add spawner_basic
/lp group spawner_vip permission set smartspawner.changetype true

# Assign admin permissions
/lp group spawner_admin permission set smartspawner.* true
```

#### Per-World Permissions
```bash
# World-specific permissions
/lp group spawner_basic permission set smartspawner.stack true server=survival
/lp group spawner_basic permission set smartspawner.break false server=creative

# Context-based permissions
/lp user Steve permission set smartspawner.limits.bypass true world=skyblock
```

### PermissionsEx Configuration

#### groups.yml
```yaml
groups:
  default:
    permissions:
      - smartspawner.stack
      - smartspawner.break
      - smartspawner.sellall
      - smartspawner.stats
  
  vip:
    inheritance:
      - default
    permissions:
      - smartspawner.changetype
  
  moderator:
    inheritance:
      - vip
    permissions:
      - smartspawner.reload
      - smartspawner.give
      - smartspawner.list
      - smartspawner.hologram
  
  admin:
    permissions:
      - smartspawner.*
```

### GroupManager Configuration

#### groups.yml
```yaml
groups:
  default:
    permissions:
      - smartspawner.stack
      - smartspawner.break
      - smartspawner.sellall
    
  trusted:
    inherits:
      - default
    permissions:
      - smartspawner.changetype
      
  staff:
    inherits:
      - trusted
    permissions:
      - smartspawner.reload
      - smartspawner.give
      - smartspawner.list
      - smartspawner.hologram
```

## 🎪 Advanced Command Usage

### 🎯 Targeting Players

#### Player Selectors
```bash
# Target specific players
/ss give Steve zombie 5          # Specific player name
/ss give "Player Name" skeleton 3 # Names with spaces (quoted)

# Target by selector
/ss give @p zombie 1             # Nearest player
/ss give @a skeleton 2           # All players
/ss give @r cow 1                # Random player

# Advanced selectors
/ss give @a[world=survival] zombie 1     # All players in specific world
/ss give @p[r=50] skeleton 1             # Nearest player within 50 blocks
```

#### Bulk Operations
```bash
# Give spawners to all online players
/ss give @a zombie 1 10

# Give different spawners to random players
/ss give @r[c=5] skeleton 1    # 5 random players get skeleton spawners
```

### 🔄 Automation & Scripts

#### Command Blocks
```bash
# Automatic spawner gifts for new players
/ss give @p[tag=newplayer] zombie 1 5

# Daily spawner rewards
/ss give @a[scores={daily=1}] cow 1 3
```

#### Plugin Integration
```bash
# With Skript
on join:
    if player has permission "spawner.daily":
        execute console command "/ss give %player% zombie 1"

# With CommandHelper
/alias /daily = /ss give @p zombie 1 iff(has_permission('spawner.daily'))
```

## 📊 Command Statistics & Monitoring

### Usage Tracking
```yaml
# Enable command logging in config.yml
logging:
  commands: true
  level: INFO
  file: "commands.log"
```

### Performance Monitoring
```bash
# Monitor command performance
/timings on
/ss give @a zombie 100    # Test bulk operations
/timings paste           # Generate performance report
```

## 🔍 Troubleshooting Commands & Permissions

### Common Command Issues

#### Command Not Found
**Error:** `Unknown command. Type "/help" for help.`

**Solutions:**
1. Verify SmartSpawner is installed and loaded
2. Check plugin.yml for command registration
3. Restart server if commands aren't registering
4. Check for plugin conflicts

#### Permission Denied
**Error:** `You don't have permission to use this command.`

**Solutions:**
1. Check specific permission requirements
2. Verify permission plugin configuration
3. Test with OP status temporarily
4. Check permission inheritance

#### Invalid Arguments
**Error:** `Invalid command syntax.`

**Solutions:**
1. Review command syntax documentation
2. Use tab completion for valid parameters
3. Check for typos in mob names
4. Verify player names exist

### Permission Testing

#### Debug Commands
```bash
# Test specific permissions
/lp user Steve permission check smartspawner.give
/pex user Steve has smartspawner.reload

# List all SmartSpawner permissions
/lp search smartspawner
/pex user Steve list
```

#### Permission Auditing
```bash
# Check effective permissions
/lp user Steve permission info
/lp group default permission info

# Test command access
/sudo Steve ss give @p zombie 1
```

## 📋 Command Reference Quick Sheet

### Essential Commands
| Command | Permission | Description |
|---------|------------|-------------|
| `/ss reload` | `smartspawner.reload` | Reload configuration |
| `/ss give <player> <type> [amount]` | `smartspawner.give` | Give spawners |
| `/ss list` | `smartspawner.list` | Admin spawner management |
| `/ss hologram` | `smartspawner.hologram` | Toggle holograms |
| `/ss help` | None | Show help information |

### Player Actions
| Action | Permission | Description |
|--------|------------|-------------|
| Stack spawners | `smartspawner.stack` | Combine spawners |
| Break spawners | `smartspawner.break` | Collect spawners |
| Sell items | `smartspawner.sellall` | Use sell-all feature |
| Change type | `smartspawner.changetype` | Use spawn eggs |
| View stats | `smartspawner.stats` | Personal statistics |

### Mob Types
Common mob types for `/ss give` command:
- `zombie`, `skeleton`, `spider`, `creeper`
- `cow`, `pig`, `chicken`, `sheep`
- `iron_golem`, `villager`, `witch`
- `blaze`, `enderman`, `ghast`
- `guardian`, `elder_guardian`

## 🔗 Related Documentation

- **[🔧 Configuration](Configuration.md)** - Permission configuration options
- **[🖱️ GUI System](GUI-System.md)** - GUI-related permissions
- **[🔌 Integrations](Integrations.md)** - Third-party permission plugins
- **[🔍 Troubleshooting](Troubleshooting.md)** - Command and permission issues

---

**Master the commands** to efficiently manage spawners on your server! Join our [Discord](http://discord.com/invite/FJN7hJKPyb) for command scripting help and permission setup assistance.