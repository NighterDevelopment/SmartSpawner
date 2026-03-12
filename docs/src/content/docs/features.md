---
title: Features Overview
description: Comprehensive overview of SmartSpawner's professional features for optimal server performance.
---

## Core Features

SmartSpawner provides enterprise-grade spawner management with focus on performance, usability, and integration. Here are some of its key features:

### Intuitive GUI System
A **user-friendly** interface that lets players effortlessly view, manage, and interact with spawners.

#### Main Spawner Interface

<br>
  
<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/e04521147d7feb847e42fb560db80070ade7c9ae.png" alt="Spawner Main GUI"/>
  <strong>Spawner Main GUI</strong>
</p>

**Key Features:**
- Drop and experience management
- Quick access controls
- Spawner metrics display

#### Storage Management

<br>

<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/ca850c8bda2b9adf89dfeb073ca5b81f437fa7b7.png" alt="Spawner Storage GUI"/>
  <strong>Spawner Storage GUI</strong>
</p>

**Capabilities:**
- Automatic item collection
- One-click selling integration
- Bulk item management
- Sort and filter options

### Advanced Stacking System
Reduce server lag with **seamless stacking mechanics**. Players can stack spawners **directly by right-clicking** or using a dedicated GUI.

#### Stacking Methods
Players can stack spawners using various methods:

| Method | Description | Usage |
|--------|-------------|-------|
| **Right-click** | Add one spawner to stack | Right-click with spawner in hand |
| **Bulk Stacking** | Stack all spawners | Shift + Right-click |
| **GUI Control** | Precise stack management | Use Stacker GUI |

#### Stacking By Hand
Players can stack spawners manually by placing them in the desired configuration.

<p align="center">
  <img src="https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExcnp1YjBpa2Iwbjh4OGhwdDNxamtpd2hhZDd2bzAwZGxlNDJ2MjIwdyZlcD12MV9pbnRlcm5naWZfYnlfaWQmY3Q9Zw/cIHKpupJheWzhAvBPz/giphy.gif" alt="Stacking a Spawner"/>
  <strong>Stacking a Spawner</strong>
</p>

#### Stacker Interface

<br>

<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/487f0ba815827dab3ab0a8978023d44ac379ffc6.png" alt="Spawner Stacker GUI"/>
  <strong>Spawner Stacker GUI</strong>
</p>

**Benefits:**
- Scalable for players who need more spawners
- Easy visual management
- Ensures drop rates scale proportionally with stack size

### Mineable Spawners  
Players can **break and collect spawners** using **specific tools** (e.g., Silk Touch), fully customizable via configuration.
```yml
spawner_break:
  # Master switch for spawner breaking feature
  enabled: true

  # Whether to directly add spawner items to player inventory
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

### Shop Integration  
Fully compatible and integrated with **popular economy/shop plugins**, allowing direct item sales from the **Spawner Storage GUI**.

<details>
<summary>Economy</summary>

- [Vault](https://www.spigotmc.org/resources/vault.34315/) - Universal economy API
- [CoinsEngine](https://modrinth.com/plugin/coinsengine) - Multi-currency system

</details>

<details>
<summary>Shops</summary>

- [EconomyShopGUI](https://www.spigotmc.org/resources/economyshopgui.69927/) - GUI-based shop system
- [EconomyShopGUI Premium](https://www.spigotmc.org/resources/economyshopgui-premium.104414/) - Premium version with advanced features
- [ShopGUI+](https://www.spigotmc.org/resources/shopgui-1-8-1-21.6515/) - Popular GUI shop plugin
- [zShop](https://www.spigotmc.org/resources/zshop-advanced-shop-plugin.74073/) - Advanced shop management
- [ExcellentShop](https://www.spigotmc.org/resources/excellentshop-%E2%AD%90-4-in-1-multi-currency-shop-chest-shop-overhaul.50696/) - 4-in-1 shop solution

</details>

## Other Supported Plugins

<details>
<summary>Protections</summary>

- [WorldGuard](https://modrinth.com/plugin/worldguard) - World protection and region management
- [GriefPrevention](https://modrinth.com/plugin/griefprevention) - Anti-grief protection system
- [Lands](https://www.spigotmc.org/resources/lands-%E2%AD%95-land-claim-plugin-%E2%9C%85-grief-prevention-protection-gui-management-nations-wars-1-21-support.53313/) - Land claiming with nations and wars
- [Towny Advanced](https://www.spigotmc.org/resources/towny-advanced.72694/) - Town and nation management
- [SimpleClaimSystem](https://modrinth.com/plugin/simpleclaimsystem) - Simple land claiming
- [RedProtect](https://www.spigotmc.org/resources/redprotect-anti-grief-server-protection-region-management-mod-mobs-flag-compat-1-7-1-21.15841/) - Comprehensive protection system
- [MinePlots](https://builtbybit.com/resources/mineplots.21646/) - Plot-based world management

</details>

<details>
<summary>Worlds</summary>

- [Multiverse-Core](https://modrinth.com/plugin/multiverse-core) - Multi-world management
- [Multiworld](https://modrinth.com/plugin/multiworld-bukkit) - Simple world handling
- [SuperiorSkyblock2](https://www.spigotmc.org/resources/%E2%9A%A1%EF%B8%8F-superiorskyblock2-%E2%9A%A1%EF%B8%8F-the-best-core-on-market-%E2%9A%A1%EF%B8%8F-1-21-3-support.87411/) - Advanced skyblock core
- [BentoBox](https://www.spigotmc.org/resources/bentobox-bskyblock-acidisland-skygrid-caveblock-aoneblock-boxed.73261/) - Modular addon framework • [📋 Setup Guide](https://docs.bentobox.world/en/latest/BentoBox/Set-a-BentoBox-world-as-the-server-default-world/#introduction) (Required to work with SmartSpawner)
- [IridiumSkyblock](https://www.spigotmc.org/resources/iridium-skyblock-1-13-1-21-5.62480/) - Premium skyblock experience with advanced features

</details>

<details>
<summary>RPG</summary>

- [AuraSkills](https://modrinth.com/plugin/auraskills) - Comprehensive skills system

</details>

<details>
<summary>Mob Plugins</summary>

- [MythicMobs](https://www.spigotmc.org/resources/mythicmobs.5702/) - Custom mob creation and management

</details>

---

*Last update: September 15, 2025 16:32:48*