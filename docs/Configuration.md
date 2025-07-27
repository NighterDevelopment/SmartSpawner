# 🔧 Configuration Guide

This comprehensive guide covers all configuration options available in SmartSpawner, helping you customize the plugin to perfectly fit your server's needs.

## 📁 Configuration Files

SmartSpawner uses multiple configuration files for different aspects:

```
plugins/SmartSpawner/
├── config.yml              # Main plugin configuration
├── item_prices.yml          # Custom item pricing
├── mob_drops.yml           # Custom mob drop rates
├── auraskills.yml          # AuraSkills integration settings
├── spawners_data.yml       # Spawner data (auto-generated)
├── language/               # Multi-language support
└── gui_layouts/            # GUI layout configurations
```

## ⚙️ Main Configuration (config.yml)

### 🌐 Language & Layout Settings

```yaml
# Language setting (available: en_US, vi_VN, it_IT, tr_TR, zh_CN)
language: en_US

# Spawner GUI layout configuration (available: default, DonutSMP)
gui_layout: default

# Enable or disable debug mode (provides verbose console output)
debug: false
```

**Available Languages:**
- `en_US` - English (Default)
- `vi_VN` - Vietnamese  
- `it_IT` - Italian
- `tr_TR` - Turkish
- `zh_CN` - Chinese (Simplified)

**Available GUI Layouts:**
- `default` - Standard SmartSpawner layout
- `DonutSMP` - Custom community layout

### 🏭 Core Spawner Properties

```yaml
spawner_properties:
  default:
    # Spawn Parameters - Controls mob generation frequency and amounts
    min_mobs: 1         # Minimum mobs spawned per cycle
    max_mobs: 4         # Maximum mobs spawned per cycle
    range: 16           # Player proximity required for activation (in blocks)
    delay: 25s          # Base delay between spawn cycles
    
    # Storage Settings - Defines internal inventory capacity
    max_storage_pages: 1    # Each page provides 45 inventory slots
    max_stored_exp: 1000    # Maximum experience points that can be stored
    max_stack_size: 10000   # Maximum number of spawners that can be stacked
    
    # Behavior Settings - Controls special spawner functionality
    allow_exp_mending: true          # Allow spawners to repair items with stored XP
    protect_from_explosions: true    # Protect spawner blocks from explosions
```

#### ⏰ Time Format Guide
SmartSpawner uses flexible time formatting:

- **Simple formats:** `20s` (20 seconds), `5m` (5 minutes), `1h` (1 hour)
- **Complex format:** `1d_2h_30m_15s` (1 day, 2 hours, 30 minutes, 15 seconds)
- **Units:** `s` = seconds, `m` = minutes, `h` = hours, `d` = days, `w` = weeks, `mo` = months, `y` = years

### 🔨 Spawner Breaking Mechanics

```yaml
spawner_break:
  enabled: true         # Master switch for spawner breaking feature
  
  # Whether to directly add spawner items to player inventory instead of dropping them
  direct_to_inventory: false
  
  # Tool Requirements - Which tools can break spawners
  required_tools:
    - IRON_PICKAXE
    - GOLDEN_PICKAXE
    - DIAMOND_PICKAXE
    - NETHERITE_PICKAXE
  
  # Durability impact on tools when breaking a spawner
  durability_loss: 1    # Number of durability points deducted
  
  # Enchantment Requirements for successful spawner collection
  silk_touch:
    required: true      # Whether Silk Touch is needed to obtain spawners
    level: 1            # Minimum level of Silk Touch required
```

### 📊 Spawner Limitations

```yaml
spawner_limits:
  # Maximum number of spawners (including stacks) allowed per chunk
  # Set to -1 for unlimited spawners per chunk
  # Each spawner in a stack counts toward the limit (not just 1 per stack)
  # Example: 1 spawner with 64 stack + 1 spawner with 6 stack = 70 total count in chunk
  max_per_chunk: -1
```

**Important:** The limit counts individual spawners in stacks, not the stack itself.

### 🏞️ Natural/Vanilla Spawner Settings

```yaml
natural_spawner:
  # Whether natural spawners can be broken and collected
  breakable: false
  
  # Convert natural spawners to smart spawners when broken
  # If false, natural spawners will drop vanilla spawner items
  convert_to_smart_spawner: false
  
  # Whether natural spawners will spawn mobs
  spawn_mobs: true
  
  # Whether natural spawner block will be protected from explosions
  protect_from_explosions: false
```

### 💰 Economy Configuration

```yaml
custom_economy:
  # Enable or disable selling items from spawners
  enabled: true
  
  # Supported types: VAULT, COINSENGINE
  currency: VAULT
  
  # Specifies the name of the currency used by COINSENGINE
  # This setting is only required when using COINSENGINE as the economy currency
  coinsengine_currency: coins
  
  # Price source modes (see detailed explanation below)
  price_source_mode: SHOP_PRIORITY
  
  # Shop plugin integration
  shop_integration:
    enabled: true
    # Supported: auto, EconomyShopGUI, EconomyShopGUI-Premium, ShopGUIPlus, zShop, ExcellentShop
    preferred_plugin: auto
  
  # Custom sell price configuration
  custom_prices:
    enabled: true
    price_file_name: "item_prices.yml"
    default_price: 1.0  # Default price for items without specific custom prices
```

#### 🛒 Price Source Modes

**SHOP_ONLY:**
- Uses ONLY shop integration prices
- Custom prices are completely ignored
- Items without shop prices cannot be sold
- Requires valid shop integration

**SHOP_PRIORITY (Recommended):**
- Primary: Shop integration prices
- Fallback: Custom prices if shop price not found
- Best for servers with shop plugins + custom backup prices

**CUSTOM_ONLY:**
- Uses ONLY custom prices from configuration
- Shop integration prices ignored
- Items without custom prices use default price
- Full control over pricing

**CUSTOM_PRIORITY:**
- Primary: Custom prices from configuration
- Fallback: Shop integration prices if custom price not found
- Ideal for servers wanting price control with shop fallback

### 🚰 Item Collection System

```yaml
hopper:
  enabled: false
  check_delay: 3s       # Time between collection checks
  stack_per_transfer: 5 # Number of item stacks transferred in one operation (max 5)
```

### 🎨 Visual Effects

#### Hologram Configuration
```yaml
hologram:
  enabled: false        # Show floating text above spawners
  
  # Hologram Text Display (supports color codes and placeholders)
  text:
    - '[&#f8f8ff%stack_size%] &#7b68ee%entity% ꜱᴘᴀᴡɴᴇʀ'
    - '&#ab7afd• &#e6e6faxᴘ: &#37eb9a%current_exp%&#f8f8ff/&#37eb9a%max_exp%'
    - '&#ab7afd• &#e6e6faɪᴛᴇᴍꜱ: &#37eb9a%used_slots%&#f8f8ff/&#37eb9a%max_slots%'
  
  # Position Offset from spawner block center
  offset_x: 0.5
  offset_y: 1.6
  offset_z: 0.5
  
  # Display Settings
  alignment: CENTER     # Text alignment (CENTER, LEFT, or RIGHT)
  shadowed_text: true   # Apply shadow effect to text
  see_through: false    # Text visible through blocks
```

#### Available Placeholders
- `%entity%` - Type of mob (e.g., "Zombie", "Skeleton")
- `%stack_size%` - Number of stacked spawners
- `%current_exp%` - Current stored XP
- `%max_exp%` - Maximum XP capacity
- `%used_slots%` - Used inventory slots
- `%max_slots%` - Total inventory slots

#### Particle Effects
```yaml
particle:
  spawner_stack: true           # Show effects when spawners are stacked
  spawner_activate: true        # Show effects when spawner activates
  spawner_generate_loot: true   # Show effects when items are generated
```

### 💾 Data Management

```yaml
data_saving:
  # Periodic auto-save interval
  interval: 5m          # Time between saves
  
  # Save spawner data on server shutdown
  save_on_shutdown: true

# Ghost spawners are error spawners that exist in data but not in world
ghost_spawners:
  # Remove ghost spawners when server starts up
  remove_on_startup: true
  
  # Remove ghost spawners when players approach them
  remove_on_approach: false
```

## 💵 Item Prices Configuration (item_prices.yml)

Customize sell prices for items from spawner storage:

```yaml
# Item prices for selling from spawner storage
# Format: MATERIAL_NAME: price

# Common drops
ROTTEN_FLESH: 0.5
BONE: 1.0
ARROW: 0.25
STRING: 0.75
GUNPOWDER: 2.0
SPIDER_EYE: 1.5

# Valuable drops
DIAMOND: 50.0
EMERALD: 25.0
GOLD_INGOT: 10.0
IRON_INGOT: 5.0

# Food items
COOKED_BEEF: 3.0
COOKED_PORKCHOP: 3.0
BREAD: 1.5

# Experience bottles
EXPERIENCE_BOTTLE: 5.0

# Special items
ENCHANTED_BOOK: 100.0
NETHER_STAR: 500.0
```

## 👾 Mob Drops Configuration (mob_drops.yml)

Configure custom drops for different mob types:

```yaml
# Custom mob drops configuration
# This overrides default Minecraft drops

ZOMBIE:
  drops:
    - material: ROTTEN_FLESH
      min_amount: 0
      max_amount: 2
      chance: 1.0
    - material: IRON_INGOT
      min_amount: 1
      max_amount: 1
      chance: 0.025
  experience:
    min: 5
    max: 5

SKELETON:
  drops:
    - material: BONE
      min_amount: 0
      max_amount: 2
      chance: 1.0
    - material: ARROW
      min_amount: 0
      max_amount: 2
      chance: 1.0
  experience:
    min: 5
    max: 5

CREEPER:
  drops:
    - material: GUNPOWDER
      min_amount: 0
      max_amount: 2
      chance: 1.0
  experience:
    min: 5
    max: 5
```

## 🎨 GUI Layout Configuration

### Default Layout (gui_layouts/default/storage_gui.yml)

```yaml
# GUI Layout for spawner storage
title: "Spawner Storage"
size: 54  # Must be multiple of 9 (9, 18, 27, 36, 45, 54)

# Button positions and configurations
buttons:
  info:
    slot: 4
    material: SPAWNER
    name: "&#f8f8ff&lSpawner Information"
    lore:
      - "&#7b68ee&l• &#e6e6faType: &#37eb9a%entity%"
      - "&#7b68ee&l• &#e6e6faStack Size: &#37eb9a%stack_size%"
      - "&#7b68ee&l• &#e6e6faStored XP: &#37eb9a%current_exp%&#f8f8ff/&#37eb9a%max_exp%"
  
  sell_all:
    slot: 49
    material: EMERALD
    name: "&#32cd32&lSell All Items"
    lore:
      - "&#98fb98Click to sell all items"
      - "&#98fb98in spawner storage"
  
  close:
    slot: 53
    material: BARRIER
    name: "&#ff6b6b&lClose"
    lore:
      - "&#ffa07aClick to close this menu"
```

## 🌟 Advanced Configuration Examples

### High-Performance Server Setup
```yaml
# Optimized for servers with 100+ players
spawner_properties:
  default:
    delay: 30s                    # Slower spawn rate
    max_storage_pages: 2          # More storage
    max_stack_size: 5000          # Moderate stacking
    
spawner_limits:
  max_per_chunk: 50               # Limit per chunk

hologram:
  enabled: false                  # Disable for performance

data_saving:
  interval: 10m                   # Less frequent saves
```

### Economy-Focused Setup
```yaml
custom_economy:
  enabled: true
  currency: VAULT
  price_source_mode: CUSTOM_PRIORITY

hopper:
  enabled: true
  check_delay: 5s
  stack_per_transfer: 3

# Enable sell-all for quick transactions
```

### Skyblock Server Setup
```yaml
spawner_properties:
  default:
    max_stack_size: 50000         # High stacking
    max_storage_pages: 3          # Large storage
    protect_from_explosions: true # Protect valuable spawners

spawner_limits:
  max_per_chunk: -1               # Unlimited for islands

natural_spawner:
  breakable: true                 # Allow collection
  convert_to_smart_spawner: true  # Convert to smart spawners
```

## 🔄 Reloading Configuration

After making changes to any configuration file:

```bash
# In-game or console
/ss reload
```

This will reload all configuration files without requiring a server restart.

## ⚠️ Important Notes

### Configuration Tips
1. **Always backup** your config before major changes
2. **Test changes** on a staging server first
3. **Validate YAML syntax** using online validators
4. **Monitor performance** after changing spawner limits
5. **Check console** for any configuration errors after reload

### Common Mistakes
- Invalid YAML indentation (use spaces, not tabs)
- Missing quotes around special characters
- Incorrect material names (use exact Minecraft material names)
- Time format errors (remember the underscore for complex times)

### Performance Considerations
- Higher `max_stack_size` values require more memory
- More `max_storage_pages` increases GUI load times
- Shorter `delay` times increase server CPU usage
- Enabled holograms add entity overhead

## 🔗 Related Documentation

- **[🖱️ GUI System](GUI-System.md)** - Understanding the interface
- **[⌨️ Commands & Permissions](Commands-Permissions.md)** - Command usage
- **[🔌 Integrations](Integrations.md)** - Plugin integration setup
- **[🔍 Troubleshooting](Troubleshooting.md)** - Common configuration issues

---

**Need help with configuration?** Join our [Discord community](http://discord.com/invite/FJN7hJKPyb) for personalized assistance!