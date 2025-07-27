# 🔍 Troubleshooting Guide

This comprehensive troubleshooting guide helps you diagnose and resolve common issues with SmartSpawner. Follow the sections relevant to your specific problem.

## 🚨 Common Issues & Solutions

### 🔧 Installation & Setup Issues

#### Plugin Not Loading
**Symptoms:**
- No SmartSpawner messages in console
- Plugin not listed in `/plugins`
- Commands not recognized

**Solutions:**
1. **Check Server Requirements:**
   ```
   Minecraft Version: 1.20-1.21.8
   Server Software: Paper, Folia, Spigot
   Java Version: 21+
   ```

2. **Verify Plugin File:**
   - Ensure the `.jar` file is in the `plugins/` folder
   - Check file isn't corrupted (re-download if needed)
   - Verify file permissions are readable

3. **Check Console Logs:**
   ```
   [ERROR] Could not load plugin SmartSpawner
   [ERROR] Unsupported API version
   ```
   - Look for specific error messages
   - Update server software if API version mismatch

4. **Dependency Check:**
   - Ensure no conflicting plugins
   - Check for required dependencies

#### Configuration Errors
**Symptoms:**
- Plugin loads but features don't work
- Console shows YAML errors
- Settings not applying after reload

**Solutions:**
1. **YAML Syntax Validation:**
   ```yaml
   # Correct syntax
   spawner_properties:
     default:
       min_mobs: 1
   
   # Incorrect (missing colon, wrong indentation)
   spawner_properties
       default
         min_mobs 1
   ```

2. **Use Online YAML Validators:**
   - Copy your config to [yamllint.com](http://yamllint.com)
   - Fix indentation errors (use spaces, not tabs)
   - Ensure proper quoting for special characters

3. **Reset Configuration:**
   ```bash
   # Backup current config
   mv plugins/SmartSpawner/config.yml plugins/SmartSpawner/config.yml.backup
   
   # Restart server to generate default config
   # Then re-apply your custom settings
   ```

### 🖱️ GUI & Interface Issues

#### GUI Not Opening
**Symptoms:**
- Right-clicking spawner does nothing
- No GUI appears when expected
- Console shows no errors

**Solutions:**
1. **Check Permissions:**
   ```yaml
   # Required permission
   smartspawner.break: true
   ```

2. **Verify Spawner Type:**
   - Only SmartSpawner blocks open GUIs
   - Vanilla spawners won't work unless configured
   - Use `/ss give` to create test spawners

3. **Test with OP:**
   ```bash
   # Give yourself OP temporarily
   /op <username>
   # Test if GUI opens
   # Remove OP after testing
   /deop <username>
   ```

4. **Check Protection Plugins:**
   - Ensure no region restrictions
   - Verify claim permissions
   - Test in unprotected areas

#### GUI Buttons Not Working
**Symptoms:**
- GUI opens but clicking does nothing
- Buttons don't respond to clicks
- No error messages

**Solutions:**
1. **Permission Check:**
   ```yaml
   # Button-specific permissions
   smartspawner.sellall: true    # For sell button
   smartspawner.stack: true      # For stack button
   ```

2. **Economy Integration:**
   ```yaml
   # For sell button issues
   custom_economy:
     enabled: true
     currency: VAULT
   ```
   - Ensure Vault is installed
   - Verify economy plugin is loaded
   - Test with `/balance` command

3. **Clear GUI Cache:**
   ```bash
   /ss reload
   ```

#### Visual Glitches
**Symptoms:**
- Items appear incorrectly
- Colors not displaying
- Text formatting broken

**Solutions:**
1. **Client-Side Issues:**
   - Update Minecraft client
   - Disable resource packs temporarily
   - Test with vanilla client

2. **Color Code Issues:**
   ```yaml
   # Check for proper color code syntax
   # Correct:
   name: "&6Golden Text"
   # Incorrect:
   name: "§6Golden Text"  # Use & instead of §
   ```

3. **Clear Client Cache:**
   - Restart Minecraft client
   - Clear resource pack cache

### 💰 Economy & Shop Issues

#### Sell-All Not Working
**Symptoms:**
- Sell button does nothing
- No money added to account
- Error messages about economy

**Solutions:**
1. **Economy Plugin Setup:**
   ```bash
   # Check if Vault is loaded
   /plugins | grep Vault
   
   # Check if economy plugin is loaded
   /plugins | grep -E "(Essentials|CMI|TNE)"
   ```

2. **Configuration Check:**
   ```yaml
   custom_economy:
     enabled: true
     currency: VAULT
     shop_integration:
       enabled: true
       preferred_plugin: auto
   ```

3. **Test Economy Manually:**
   ```bash
   # Test economy commands
   /balance
   /pay <player> 10
   /eco give <player> 100
   ```

4. **Price Configuration:**
   ```yaml
   # Check item_prices.yml
   COBBLESTONE: 0.1
   ROTTEN_FLESH: 0.5
   
   # Ensure default_price is not 0
   custom_prices:
     default_price: 1.0
   ```

#### Incorrect Prices
**Symptoms:**
- Items selling for wrong prices
- Prices showing as $0.00
- Inconsistent pricing

**Solutions:**
1. **Price Source Mode:**
   ```yaml
   custom_economy:
     price_source_mode: SHOP_PRIORITY  # or CUSTOM_PRIORITY
   ```

2. **Shop Integration Check:**
   ```bash
   # Verify shop plugin is detected
   # Check console logs for:
   [SmartSpawner] Detected shop plugin: ShopGUIPlus
   ```

3. **Update Price Files:**
   ```bash
   /ss reload  # Reload prices
   ```

### 🏗️ Spawner Functionality Issues

#### Spawners Not Stacking
**Symptoms:**
- Placing spawners doesn't combine them
- Stack size remains at 1
- No stacking animation

**Solutions:**
1. **Permission Check:**
   ```yaml
   smartspawner.stack: true
   ```

2. **Configuration Verification:**
   ```yaml
   spawner_properties:
     default:
       max_stack_size: 10000  # Ensure not set to 1
   ```

3. **Location Requirements:**
   - Spawners must be same type
   - Must be placed adjacent or on same block
   - Check for obstruction

4. **Protection Plugin Conflicts:**
   - Test in unprotected areas
   - Check claim/region permissions

#### Spawners Not Breaking
**Symptoms:**
- Can't break spawners with tools
- Spawners don't drop when broken
- Break animations but no item drop

**Solutions:**
1. **Tool Requirements:**
   ```yaml
   spawner_break:
     required_tools:
       - IRON_PICKAXE
       - DIAMOND_PICKAXE
       - NETHERITE_PICKAXE
   ```

2. **Silk Touch Check:**
   ```yaml
   spawner_break:
     silk_touch:
       required: true  # Set to false if not required
       level: 1
   ```

3. **Inventory Space:**
   - Ensure player has inventory space
   - Check if `direct_to_inventory: true`

4. **Protection Conflicts:**
   - Test in unprotected areas
   - Verify break permissions in regions

### 🌟 Visual Effects Issues

#### Holograms Not Showing
**Symptoms:**
- No hologram text above spawners
- Holograms appear but are blank
- Holograms in wrong location

**Solutions:**
1. **Enable Holograms:**
   ```yaml
   hologram:
     enabled: true
   ```

2. **Check Hologram Toggle:**
   ```bash
   /ss hologram show
   ```

3. **Position Adjustment:**
   ```yaml
   hologram:
     offset_x: 0.5
     offset_y: 1.6  # Adjust if too high/low
     offset_z: 0.5
   ```

4. **Text Configuration:**
   ```yaml
   hologram:
     text:
       - '&6%entity% Spawner'  # Ensure text is not empty
       - '&7Stack: %stack_size%'
   ```

#### Particles Not Working
**Symptoms:**
- No particle effects during spawner activities
- Particles appear in wrong location
- Performance issues with particles

**Solutions:**
1. **Enable Particles:**
   ```yaml
   particle:
     spawner_stack: true
     spawner_activate: true
     spawner_generate_loot: true
   ```

2. **Client Settings:**
   - Check Minecraft particle settings
   - Ensure particles not set to minimal

3. **Server Performance:**
   - Monitor TPS during particle events
   - Reduce particle effects if causing lag

### 🔌 Integration Issues

#### Protection Plugin Conflicts
**Symptoms:**
- Can't place spawners in own claims
- Permission errors in protected areas
- Inconsistent behavior across regions

**Solutions:**
1. **WorldGuard Setup:**
   ```bash
   /rg flag __global__ spawner-place allow
   /rg flag protectedregion spawner-place deny
   ```

2. **GriefPrevention Trust:**
   ```bash
   /trust <player>  # Give build trust
   /containertrust <player>  # Give container access
   ```

3. **Lands Configuration:**
   ```yaml
   # In Lands config
   roles:
     member:
       permissions:
         - spawner_place
         - spawner_break
   ```

4. **Check Integration Status:**
   ```bash
   # Look for integration messages in console
   [SmartSpawner] Hooked into WorldGuard
   [SmartSpawner] Hooked into GriefPrevention
   ```

### 🗂️ Data & Storage Issues

#### Spawner Data Lost
**Symptoms:**
- Spawners lose stack information after restart
- Storage inventories empty
- Experience reset to 0

**Solutions:**
1. **Check Data Saving:**
   ```yaml
   data_saving:
     interval: 5m
     save_on_shutdown: true
   ```

2. **File Permissions:**
   ```bash
   # Check file permissions
   ls -la plugins/SmartSpawner/spawners_data.yml
   
   # Fix permissions if needed
   chmod 644 plugins/SmartSpawner/spawners_data.yml
   ```

3. **Backup Restoration:**
   ```bash
   # Restore from backup if available
   cp plugins/SmartSpawner/spawners_data.yml.backup plugins/SmartSpawner/spawners_data.yml
   ```

4. **Ghost Spawner Cleanup:**
   ```yaml
   ghost_spawners:
     remove_on_startup: true
     remove_on_approach: true
   ```

#### Storage Items Disappearing
**Symptoms:**
- Items vanish from spawner storage
- Storage GUI shows empty slots
- Items not being collected

**Solutions:**
1. **Check Storage Limits:**
   ```yaml
   spawner_properties:
     default:
       max_storage_pages: 1  # Increase if needed
   ```

2. **Item Collection:**
   ```yaml
   hopper:
     enabled: true
     check_delay: 3s
     stack_per_transfer: 5
   ```

3. **Server Restart Safety:**
   - Ensure data saves before restart
   - Check for corruption in storage data

## 🛠️ Diagnostic Tools

### Debug Mode
Enable detailed logging for troubleshooting:

```yaml
# config.yml
debug: true
```

**Debug Output Examples:**
```
[SmartSpawner] [DEBUG] Player placed spawner at world: 100, 64, 200
[SmartSpawner] [DEBUG] Checking permissions for player: username
[SmartSpawner] [DEBUG] Vault economy transaction: +$15.50
[SmartSpawner] [DEBUG] WorldGuard check result: ALLOW
```

### Console Commands for Diagnostics

```bash
# Check plugin status
/plugins SmartSpawner

# Verify configuration
/ss reload

# Test permissions
/lp user <player> permission check smartspawner.break

# Check economy
/balance <player>

# Test spawner creation
/ss give @p zombie 1
```

### Performance Monitoring

```bash
# Check server performance
/tps
/spark profiler start
/spark profiler stop
/timings on
/timings paste
```

### Log Analysis

**Important Log Locations:**
- `logs/latest.log` - Current session logs
- `plugins/SmartSpawner/debug.log` - Debug output (if enabled)
- Protection plugin logs for integration issues

**Key Log Patterns to Look For:**
```
[ERROR] Could not save spawner data
[WARN] Unknown player in spawner data
[INFO] SmartSpawner successfully hooked into
[DEBUG] Spawner operation completed
```

## 📞 Getting Additional Help

### Before Asking for Help

1. **Check This Guide:** Review all relevant sections
2. **Enable Debug Mode:** Gather detailed logs
3. **Test Minimal Setup:** Disable other plugins temporarily
4. **Document the Issue:** Note exact steps to reproduce
5. **Gather Information:**
   - Server software and version
   - SmartSpawner version
   - List of other plugins
   - Console errors (full stack traces)
   - Configuration files

### Support Channels

#### Discord Community
**Link:** [Join our Discord](http://discord.com/invite/FJN7hJKPyb)

**What to Include:**
- Server information (software, version, player count)
- SmartSpawner version
- Detailed problem description
- Console logs (use pastebin for long logs)
- Configuration files (if relevant)

#### GitHub Issues
**Link:** [Report Issues](https://github.com/ptthanh02/SmartSpawner/issues)

**Issue Template:**
```markdown
**Bug Description:**
Clear description of the issue

**Server Information:**
- Server Software: Paper 1.21.1
- SmartSpawner Version: 1.3.5
- Java Version: 21

**Steps to Reproduce:**
1. Step one
2. Step two
3. Issue occurs

**Expected Behavior:**
What should happen

**Actual Behavior:**
What actually happens

**Console Logs:**
```paste logs here```

**Additional Information:**
Any other relevant details
```

### Self-Help Resources

1. **Wiki Documentation:** Complete feature guides
2. **Configuration Examples:** Sample configurations
3. **API Documentation:** For developers
4. **Video Tutorials:** Community-created guides
5. **Example Setups:** Common server configurations

### Community Resources

- **Reddit:** r/admincraft for general server administration
- **SpigotMC Forums:** General plugin discussions  
- **Discord Servers:** Minecraft server administrator communities
- **YouTube:** Tutorial videos and setup guides

## 🔄 Regular Maintenance

### Preventive Measures

1. **Regular Backups:**
   ```bash
   # Backup spawner data
   cp plugins/SmartSpawner/spawners_data.yml backups/spawners_$(date +%Y%m%d).yml
   ```

2. **Monitor Performance:**
   - Check TPS regularly
   - Monitor memory usage
   - Review console for warnings

3. **Keep Updated:**
   - Update SmartSpawner regularly
   - Update dependent plugins
   - Check compatibility after updates

4. **Test Changes:**
   - Test configuration changes on staging server
   - Create backups before major updates
   - Monitor logs after changes

### Health Checks

**Weekly Checks:**
- [ ] Verify spawner data integrity
- [ ] Check console for errors
- [ ] Test core functionality
- [ ] Review performance metrics

**Monthly Checks:**
- [ ] Update plugins if needed
- [ ] Clean up ghost spawners
- [ ] Review and optimize configuration
- [ ] Check for memory leaks

## 🔗 Related Documentation

- **[⚙️ Installation](Installation.md)** - Initial setup guidance
- **[🔧 Configuration](Configuration.md)** - Configuration options
- **[🖱️ GUI System](GUI-System.md)** - Interface troubleshooting
- **[⌨️ Commands & Permissions](Commands-Permissions.md)** - Permission issues
- **[🔌 Integrations](Integrations.md)** - Third-party plugin conflicts

---

**Still having issues?** Don't hesitate to reach out to our supportive community on [Discord](http://discord.com/invite/FJN7hJKPyb) - we're here to help!