# 🔌 Third-Party Integrations

SmartSpawner seamlessly integrates with a wide variety of popular Bukkit/Spigot plugins, enhancing functionality and providing a cohesive server experience. This guide covers all supported integrations and their configuration.

## 🏪 Shop Plugin Integrations

SmartSpawner supports automatic price integration with major shop plugins for the sell-all feature.

### 🛒 EconomyShopGUI & EconomyShopGUI-Premium

**Plugin Links:**
- [EconomyShopGUI](https://www.spigotmc.org/resources/economyshopgui.69927/)
- [EconomyShopGUI-Premium](https://www.spigotmc.org/resources/economyshopgui-premium.101902/)

**Configuration:**
```yaml
custom_economy:
  shop_integration:
    enabled: true
    preferred_plugin: EconomyShopGUI  # or EconomyShopGUI-Premium
```

**Features:**
- Automatic price detection from shop configurations
- Real-time price updates
- Multi-world shop support
- Category-based pricing

**Setup Example:**
```yaml
# EconomyShopGUI config.yml
items:
  ROTTEN_FLESH:
    material: ROTTEN_FLESH
    sellPrice: 0.5
    buyPrice: 1.0
  BONE:
    material: BONE
    sellPrice: 1.0
    buyPrice: 2.0
```

### 🏬 ShopGUIPlus

**Plugin Link:** [ShopGUIPlus](https://www.spigotmc.org/resources/shopgui.6515/)

**Configuration:**
```yaml
custom_economy:
  shop_integration:
    enabled: true
    preferred_plugin: ShopGUIPlus
```

**Features:**
- Integration with shop items and prices
- Support for shop permissions
- Dynamic pricing updates
- Custom shop categories

**Setup Example:**
```yaml
# ShopGUIPlus shops/blocks.yml
shops:
  blocks:
    items:
      cobblestone:
        type: COBBLESTONE
        sellPrice: 0.1
        buyPrice: 0.2
```

### 🛍️ zShop

**Plugin Link:** [zShop](https://www.spigotmc.org/resources/zshop.85980/)

**Configuration:**
```yaml
custom_economy:
  shop_integration:
    enabled: true
    preferred_plugin: zShop
```

**Features:**
- Full zShop price compatibility
- Multi-currency support
- Player shop integration
- Dynamic market prices

### 🎪 ExcellentShop

**Plugin Link:** [ExcellentShop](https://www.spigotmc.org/resources/excellentshop.61859/)

**Configuration:**
```yaml
custom_economy:
  shop_integration:
    enabled: true
    preferred_plugin: ExcellentShop
```

**Features:**
- Advanced shop integration
- Stock management compatibility
- Price history tracking
- Custom shop modules

### 🔄 Auto-Detection

SmartSpawner can automatically detect your shop plugin:

```yaml
custom_economy:
  shop_integration:
    enabled: true
    preferred_plugin: auto  # Automatically detects installed shop plugin
```

**Detection Priority:**
1. EconomyShopGUI-Premium
2. EconomyShopGUI
3. ShopGUIPlus
4. zShop
5. ExcellentShop

## 💰 Economy Plugin Integrations

### 🏦 Vault

**Plugin Link:** [Vault](https://www.spigotmc.org/resources/vault.34315/)

**Configuration:**
```yaml
custom_economy:
  enabled: true
  currency: VAULT
```

**Features:**
- Universal economy integration
- Multi-currency support
- Bank account integration
- Permission-based access

**Compatible Economy Plugins:**
- Essentials Economy
- CMI Economy
- TNE (The New Economy)
- UltimateEconomy
- MultiEconomy

**Setup Example:**
```yaml
# Vault automatically detects your economy plugin
# No additional configuration needed
```

### 💎 CoinsEngine

**Plugin Link:** [CoinsEngine](https://www.spigotmc.org/resources/coinsengine.84121/)

**Configuration:**
```yaml
custom_economy:
  enabled: true
  currency: COINSENGINE
  coinsengine_currency: coins  # Currency name from CoinsEngine
```

**Features:**
- Multiple currency types
- Custom currency names
- Exchange rate support
- Player currency limits

**Setup Example:**
```yaml
# CoinsEngine currencies.yml
currencies:
  coins:
    name: "Coins"
    symbol: "$"
    format: "{symbol}{amount}"
  gems:
    name: "Gems"
    symbol: "💎"
    format: "{amount} {symbol}"
```

**Multi-Currency Support:**
```yaml
# Use different currencies for different item types
custom_economy:
  currency: COINSENGINE
  coinsengine_currency: coins  # Default currency
  
# Advanced configuration (future feature)
currency_mapping:
  DIAMOND: gems
  EMERALD: gems
  default: coins
```

## 🛡️ Protection Plugin Integrations

SmartSpawner respects region and claim protections from major protection plugins.

### 🏰 WorldGuard

**Plugin Link:** [WorldGuard](https://dev.bukkit.org/projects/worldguard)

**Features:**
- Region-based spawner protection
- Custom flag support
- Priority-based permissions
- Global/region-specific rules

**Supported Actions:**
- Spawner placement
- Spawner breaking
- Spawner stacking
- GUI access

**Custom Flags:**
```yaml
# WorldGuard regions.yml (example)
regions:
  spawn:
    priority: 100
    flags:
      spawner-place: deny
      spawner-break: deny
      spawner-stack: allow
```

**Setup Example:**
```bash
# WorldGuard commands
/rg flag spawn spawner-place deny
/rg flag spawn spawner-break deny
/rg flag vip-area spawner-place allow
/rg flag vip-area spawner-break allow
```

### 🏡 GriefPrevention

**Plugin Link:** [GriefPrevention](https://www.spigotmc.org/resources/griefprevention.1884/)

**Features:**
- Claim-based protection
- Trust level integration
- Automatic claim detection
- Subdivision support

**Trust Levels:**
- **Public:** No spawner permissions
- **Build:** Can place/break spawners
- **Container:** Can access spawner GUIs
- **Access:** Can use spawner features
- **Manager:** Full spawner control

**Configuration:**
```yaml
# Automatic integration - no configuration needed
# SmartSpawner respects GriefPrevention claims automatically
```

### 🌍 Lands

**Plugin Link:** [Lands](https://www.spigotmc.org/resources/lands.53313/)

**Features:**
- Land-based permissions
- Role system integration
- Nation support
- War system compatibility

**Role Permissions:**
```yaml
# Lands roles.yml (example)
roles:
  member:
    permissions:
      - spawner_place
      - spawner_stack
  trusted:
    permissions:
      - spawner_place
      - spawner_break
      - spawner_stack
      - spawner_gui
```

### 🏘️ Towny

**Plugin Link:** [Towny](https://www.spigotmc.org/resources/towny.72694/)

**Features:**
- Town/nation permissions
- Plot-based protection
- Resident trust system
- PvP zone compatibility

**Town Permissions:**
```yaml
# Towny config.yml (example)
town_block_permissions:
  spawner:
    resident: true
    nation: true
    ally: false
    outsider: false
```

### 🛡️ RedProtect

**Plugin Link:** [RedProtect](https://www.spigotmc.org/resources/redprotect.15841/)

**Features:**
- Region claim protection
- Player-based permissions
- Admin region support
- Multi-world compatibility

### 🏗️ Other Protection Plugins

**SimpleClaimSystem**
- Basic claim protection
- Player permission system

**MinePlots**
- Plot-based protection
- Plot member permissions

**BentoBox/SuperiorSkyblock2**
- Island-based protection
- Team member permissions
- Island level integration

## 🌐 World Management Integrations

### 🌍 Multiverse-Core

**Plugin Link:** [Multiverse-Core](https://dev.bukkit.org/projects/multiverse-core)

**Features:**
- Per-world spawner settings
- World-specific configurations
- Portal integration
- World inheritance

**Configuration:**
```yaml
# Per-world configuration
worlds:
  survival:
    spawner_properties:
      max_stack_size: 64
  creative:
    spawner_properties:
      max_stack_size: 1000
  skyblock:
    spawner_properties:
      max_stack_size: 10000
```

### 🗺️ MultiWorld

**Plugin Link:** [MultiWorld](https://www.spigotmc.org/resources/multiworld.4482/)

**Features:**
- World creation integration
- Automatic world detection
- Cross-world teleportation

### 🌌 Worlds

**Plugin Link:** [Worlds](https://www.spigotmc.org/resources/worlds.18962/)

**Features:**
- Advanced world management
- World-specific rules
- Template system integration

## ⚔️ RPG & Skills Integrations

### ✨ AuraSkills

**Plugin Link:** [AuraSkills](https://www.spigotmc.org/resources/auraskills.81069/)

**Configuration File:** `auraskills.yml`
```yaml
# AuraSkills integration settings
auraskills:
  enabled: true
  
  # XP rewards for spawner activities
  spawner_place:
    skill: building
    xp: 10
  
  spawner_break:
    skill: building
    xp: 5
  
  spawner_stack:
    skill: building
    xp: 15
  
  # Skill requirements
  requirements:
    spawner_advanced:
      skill: building
      level: 50
```

**Features:**
- Skill XP rewards for spawner activities
- Skill level requirements
- Custom ability integration
- Stats tracking

**Skill Integration:**
```yaml
# Example skill rewards
activities:
  place_spawner:
    - skill: building
      xp: 20
  break_spawner:
    - skill: mining
      xp: 15
  stack_spawner:
    - skill: building
      xp: 25
```

## 👾 Mob & Drop Integrations

### 🐉 MythicMobs

**Plugin Link:** [MythicMobs](https://www.spigotmc.org/resources/mythicmobs.5702/)

**Configuration File:** `mob_drops.yml`
```yaml
# Custom mob drops configuration
mythicmobs:
  enabled: true
  
  # Custom drops for MythicMobs entities
  custom_drops:
    SkeletonKing:
      drops:
        - material: DIAMOND
          min_amount: 1
          max_amount: 3
          chance: 0.1
        - material: ENCHANTED_BOOK
          min_amount: 1
          max_amount: 1
          chance: 0.05
      experience:
        min: 50
        max: 100
    
    DragonLord:
      drops:
        - material: DRAGON_EGG
          min_amount: 1
          max_amount: 1
          chance: 1.0
        - material: NETHER_STAR
          min_amount: 1
          max_amount: 5
          chance: 0.5
```

**Features:**
- Custom drop tables for MythicMobs
- Enhanced loot generation
- Skill-based drop modifications
- Experience scaling

## 📊 Statistics & Analytics

### 📈 bStats

**Built-in Integration**
SmartSpawner automatically reports anonymous usage statistics to bStats.

**Metrics Collected:**
- Server software and version
- Player count
- Plugin version
- Feature usage statistics

**Privacy:**
- All data is anonymous
- No personal information collected
- Helps improve plugin development

**Opt-out:**
```yaml
# In bStats config
opt-out: true
```

## 🔧 Advanced Integration Examples

### Custom Integration Setup

```java
// Example: Custom protection plugin integration
public class CustomProtectionIntegration implements Listener {
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerPlace(SpawnerPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getLocation();
        
        // Check custom protection
        if (!CustomProtectionAPI.canBuild(player, location)) {
            event.setCancelled(true);
            player.sendMessage("You cannot place spawners here!");
        }
    }
}
```

### Multi-Plugin Economy

```java
// Example: Multiple economy system support
public class MultiEconomyHandler {
    
    public boolean withdrawMoney(Player player, double amount) {
        // Try Vault first
        if (VaultEconomy.isEnabled()) {
            return VaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
        }
        
        // Fallback to CoinsEngine
        if (CoinsEngineAPI.isEnabled()) {
            return CoinsEngineAPI.withdraw(player.getUniqueId(), "coins", amount);
        }
        
        // Fallback to custom economy
        return CustomEconomy.withdraw(player, amount);
    }
}
```

## ⚙️ Integration Configuration

### Global Integration Settings

```yaml
# Main config.yml
integrations:
  # Economy integrations
  vault:
    enabled: true
    primary: true
  coinsengine:
    enabled: true
    fallback: true
  
  # Shop integrations
  shops:
    enabled: true
    update_interval: 5m
    cache_prices: true
  
  # Protection integrations
  protection:
    enabled: true
    check_on_place: true
    check_on_break: true
    check_on_gui: true
  
  # World management
  worlds:
    per_world_config: true
    inherit_settings: true
```

### Performance Optimization

```yaml
# Optimize integration performance
performance:
  integration_cache: true
  async_checks: true
  batch_operations: true
  
  # Cache durations
  cache_durations:
    protection_checks: 30s
    shop_prices: 5m
    player_data: 1m
```

## 🔍 Troubleshooting Integrations

### Common Issues

#### Shop Integration Not Working
**Symptoms:** Sell prices showing as $0.00

**Solutions:**
1. Verify shop plugin is installed and loaded
2. Check shop plugin configuration for items
3. Ensure `shop_integration.enabled: true`
4. Test with `/ss reload`

#### Protection Plugin Conflicts
**Symptoms:** Players can't place spawners in their own claims

**Solutions:**
1. Check protection plugin permissions
2. Verify claim/region settings
3. Test with different trust levels
4. Review protection plugin logs

#### Economy Plugin Issues
**Symptoms:** Money not being added/removed

**Solutions:**
1. Verify Vault is installed for economy plugins
2. Check economy plugin configuration
3. Test with `/balance` command
4. Ensure sufficient funds for transactions

### Debug Mode

Enable debug mode for integration troubleshooting:

```yaml
# config.yml
debug: true
integration_debug: true

# Console output will show:
# [SmartSpawner] [DEBUG] Checking WorldGuard permissions for player...
# [SmartSpawner] [DEBUG] Shop integration found price: $1.50 for COBBLESTONE
# [SmartSpawner] [DEBUG] Vault economy transaction: +$45.00 to player
```

## 📋 Integration Checklist

### For Server Administrators

**Before Installing SmartSpawner:**
- [ ] Install required economy plugin (Vault recommended)
- [ ] Configure protection plugins
- [ ] Set up shop plugin (if using sell features)
- [ ] Review world management settings

**After Installing SmartSpawner:**
- [ ] Test spawner placement in different regions
- [ ] Verify sell-all functionality with shop integration
- [ ] Check economy transactions
- [ ] Test protection plugin compatibility
- [ ] Review logs for integration errors

**Regular Maintenance:**
- [ ] Update integration plugins
- [ ] Monitor performance impact
- [ ] Review integration logs
- [ ] Test new features after updates

## 🔗 Related Documentation

- **[🔧 Configuration](Configuration.md)** - Integration configuration options
- **[💻 API Documentation](API-Documentation.md)** - Custom integration development
- **[⌨️ Commands & Permissions](Commands-Permissions.md)** - Integration-specific permissions
- **[🔍 Troubleshooting](Troubleshooting.md)** - Integration problem solving

---

**Maximize your server potential** with SmartSpawner's extensive plugin integrations! Join our [Discord](http://discord.com/invite/FJN7hJKPyb) for integration support and configuration assistance.