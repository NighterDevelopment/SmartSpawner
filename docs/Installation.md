# ⚙️ Installation Guide

This guide will walk you through the complete installation and initial setup of SmartSpawner on your Minecraft server.

## 📋 Requirements

Before installing SmartSpawner, ensure your server meets these requirements:

### 🖥️ **Server Requirements**
- **Minecraft Version:** 1.20 - 1.21.8
- **Server Software:** Paper, Folia, or compatible forks
- **Java Version:** 21 or higher
- **RAM:** Minimum 2GB (4GB+ recommended)

### 🔌 **Optional Dependencies**
While not required, these plugins enhance SmartSpawner's functionality:

#### Economy Plugins
- [Vault](https://www.spigotmc.org/resources/vault.34315/) - Required for economy features
- [CoinsEngine](https://www.spigotmc.org/resources/coinsengine.84121/) - Alternative economy system

#### Shop Plugins
- [EconomyShopGUI](https://www.spigotmc.org/resources/economyshopgui.69927/)
- [ShopGUIPlus](https://www.spigotmc.org/resources/shopgui.6515/)
- [zShop](https://www.spigotmc.org/resources/zshop.85980/)
- [ExcellentShop](https://www.spigotmc.org/resources/excellentshop.61859/)

#### Protection Plugins
- [WorldGuard](https://dev.bukkit.org/projects/worldguard)
- [GriefPrevention](https://www.spigotmc.org/resources/griefprevention.1884/)
- [Lands](https://www.spigotmc.org/resources/lands.53313/)
- [Towny](https://www.spigotmc.org/resources/towny.72694/)
- [RedProtect](https://www.spigotmc.org/resources/redprotect.15841/)

## 📥 Download

Choose your preferred platform to download SmartSpawner:

### 🟢 **Recommended: Modrinth**
1. Visit [SmartSpawner on Modrinth](https://modrinth.com/plugin/smart-spawner-plugin)
2. Click "Download" for the latest version
3. Save the `.jar` file to your downloads folder

### 🟡 **Alternative: Spigot**
1. Visit [SmartSpawner on Spigot](https://www.spigotmc.org/resources/120743/)
2. Click "Download Now"
3. Save the `.jar` file to your downloads folder

### 🔵 **Alternative: Hangar**
1. Visit [SmartSpawner on Hangar](https://hangar.papermc.io/Nighter/SmartSpawner)
2. Download the latest version
3. Save the `.jar` file to your downloads folder

## 🚀 Installation Steps

### Step 1: Stop Your Server
```bash
# Stop your server safely
/stop
```

### Step 2: Upload Plugin File
1. Navigate to your server's `plugins` folder
2. Upload the `SmartSpawner-x.x.x.jar` file
3. Ensure the file has proper permissions (readable by server process)

### Step 3: Start Your Server
```bash
# Start your server
./start.sh
# or
java -jar server.jar
```

### Step 4: Verify Installation
Check your server console for the SmartSpawner startup message:
```
[INFO] [SmartSpawner] Loading SmartSpawner v1.x.x
[INFO] [SmartSpawner] SmartSpawner has been successfully loaded!
```

## ⚡ First Time Setup

### 1. Initial Configuration
After the first startup, SmartSpawner will generate default configuration files:

```
plugins/SmartSpawner/
├── config.yml              # Main configuration
├── spawners_data.yml        # Spawner data storage
├── item_prices.yml          # Custom item prices
├── mob_drops.yml           # Custom mob drops
├── auraskills.yml          # AuraSkills integration
├── language/               # Language files
│   ├── en_US/
│   ├── vi_VN/
│   ├── it_IT/
│   └── tr_TR/
└── gui_layouts/            # GUI layout configurations
    ├── default/
    └── DonutSMP/
```

### 2. Basic Configuration
Edit the main configuration file:

```yaml
# plugins/SmartSpawner/config.yml

# Set your preferred language
language: en_US

# Choose GUI layout
gui_layout: default

# Enable economy features (requires Vault)
custom_economy:
  enabled: true
  currency: VAULT

# Enable hologram display
hologram:
  enabled: true
```

### 3. Reload Configuration
Apply your changes without restarting:
```
/ss reload
```

## 🔧 Permission Setup

### Default Permissions
SmartSpawner comes with sensible defaults:

```yaml
# Default player permissions
smartspawner.stack: true      # Allow stacking spawners
smartspawner.break: true      # Allow breaking spawners
smartspawner.sellall: true    # Allow selling items

# Admin permissions (OP by default)
smartspawner.reload           # Reload plugin
smartspawner.give            # Give spawners to players
smartspawner.list            # Access admin GUI
smartspawner.hologram        # Toggle holograms
smartspawner.changetype      # Change spawner types
smartspawner.limits.bypass   # Bypass spawner limits
```

### Permission Plugin Setup

#### For LuckPerms:
```bash
# Give basic permissions to default group
/lp group default permission set smartspawner.stack true
/lp group default permission set smartspawner.break true
/lp group default permission set smartspawner.sellall true

# Give admin permissions to staff
/lp group admin permission set smartspawner.* true
```

#### For PermissionsEx:
```yaml
groups:
  default:
    permissions:
      - smartspawner.stack
      - smartspawner.break
      - smartspawner.sellall
  admin:
    permissions:
      - smartspawner.*
```

## 🌐 Multi-language Configuration

### Available Languages
- `en_US` - English (Default)
- `vi_VN` - Vietnamese
- `it_IT` - Italian  
- `tr_TR` - Turkish
- `zh_CN` - Chinese (Simplified)

### Setting Language
```yaml
# In config.yml
language: vi_VN  # Change to your preferred language
```

### Custom Language
1. Copy the `en_US` folder in `language/`
2. Rename it to your language code (e.g., `es_ES`)
3. Translate the files inside
4. Set `language: es_ES` in config.yml

## 🧪 Testing Installation

### Verify Plugin Functions
1. **Place a spawner** and check if the GUI opens when right-clicked
2. **Test stacking** by placing another spawner of the same type nearby
3. **Check commands** by running `/ss help`
4. **Verify permissions** by testing player actions

### Test Economy Integration
If you have Vault and an economy plugin:
1. Open a spawner GUI
2. Add items to the spawner storage
3. Try the "Sell All" function
4. Check if money is added to player balance

### Test Holograms
If holograms are enabled:
1. Place a spawner
2. Check if hologram appears above it
3. Test the `/ss hologram` command to toggle visibility

## 🔍 Troubleshooting Installation

### Common Issues

#### Plugin Not Loading
**Symptoms:** No startup message, plugin not in `/plugins` list

**Solutions:**
- Check Java version (must be 21+)
- Verify server software compatibility
- Check file permissions
- Review server startup logs for errors

#### Console Errors
**Symptoms:** Red error messages during startup

**Solutions:**
- Update to latest SmartSpawner version
- Check for plugin conflicts
- Verify all dependencies are installed
- Join Discord support for help

#### GUI Not Opening
**Symptoms:** Right-clicking spawner doesn't open GUI

**Solutions:**
- Check if player has `smartspawner.break` permission
- Verify spawner is a valid type
- Test with different spawner types
- Check for protection plugin conflicts

### Performance Issues
**Symptoms:** Server lag, high CPU usage

**Solutions:**
- Reduce hologram update frequency
- Limit spawners per chunk
- Disable particle effects if needed
- Monitor with `/spark profiler`

## 📞 Getting Help

### Support Channels
- **Discord Community:** [Join our Discord](http://discord.com/invite/FJN7hJKPyb)
- **GitHub Issues:** [Report problems](https://github.com/ptthanh02/SmartSpawner/issues)
- **Documentation:** [Read full docs](README.md)

### Before Asking for Help
1. Check this installation guide
2. Review [troubleshooting section](Troubleshooting.md)
3. Check console for error messages
4. Test with minimal plugin setup
5. Provide server version and SmartSpawner version

## ✅ Next Steps

Once SmartSpawner is installed and working:

1. **[📖 Configuration](Configuration.md)** - Customize plugin settings
2. **[🖱️ GUI System](GUI-System.md)** - Learn the interface
3. **[⌨️ Commands](Commands-Permissions.md)** - Master the commands
4. **[🔌 Integrations](Integrations.md)** - Setup plugin integrations

---

**Congratulations!** 🎉 SmartSpawner is now installed and ready to transform your server's spawner experience!