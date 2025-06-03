<div align="center">

<br>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<img src="https://github.com/user-attachments/assets/05e5f050-b661-40ed-a400-bcb7eea07430" alt="GUI Spawner Plugin for Minecraft Servers" />

<br>

[![Release](https://img.shields.io/github/v/release/ptthanh02/Smart-Spawner-Plugin?logo=github&logoColor=white&label=release&labelColor=%230D597F&color=%23116BBF)](https://github.com/ptthanh02/Smart-Spawner-Plugin/releases/latest)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/smart-spawner-plugin?logo=modrinth&logoColor=white&label=downloads&labelColor=%23139549&color=%2318c25f)](https://modrinth.com/plugin/smart-spawner-plugin)
[![Spigot Downloads](https://img.shields.io/spiget/downloads/120743?logo=spigotmc&logoColor=white&label=spigot%20downloads&labelColor=%23ED8106&color=%23FF994C)](https://www.spigotmc.org/resources/smart-spawner-gui-spawner-plugin%E2%9C%A8-1-21-1-21-3-%EF%B8%8F.120743/)
[![License](https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-brightgreen.svg)](LICENSE)

[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/smart-spawner-plugin)
[![Spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg)](https://www.spigotmc.org/resources/120743/)
[![Hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg)](https://hangar.papermc.io/Nighter/SmartSpawner)

</div>

<br>

## Requirements

- **Minecraft Version:** 1.20 - 1.21.4
- **Server Software:** Paper, Folia or compatible forks
- **Java Version:** 21+

### Optional Dependencies

- **Economy Plugins** - For shop integration and item selling
- **Protection Plugins** - WorldGuard, GriefPrevention, Lands, Towny, and more
- **World Management** - Multiverse, SuperiorSkyblock2, BentoBox compatibility

## Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/smart-spawner-plugin)
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin in `plugins/SmartSpawner/config.yml`
5. Reload with `/ss reload`

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/ss reload` | `smartspawner.reload` | Reload plugin configuration |
| `/ss give <player> <type> <amount>` | `smartspawner.give` | Give spawners to players |
| `/ss list` | `smartspawner.list` | Open admin spawner management GUI |
| `/ss hologram` | `smartspawner.hologram` | Toggle hologram visibility |

**Aliases:** `/ss`, `/spawner`, `/smartspawner`

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `smartspawner.reload` | OP | Access to reload command |
| `smartspawner.give` | OP | Access to give command |
| `smartspawner.list` | OP | Access to admin GUI |
| `smartspawner.hologram` | OP | Toggle hologram display |
| `smartspawner.stack` | true | Allow spawner stacking |
| `smartspawner.break` | true | Allow spawner breaking |
| `smartspawner.sellall` | true | Allow selling storage items |
| `smartspawner.changetype` | OP | Allow changing spawner types with eggs |

## Localization

| Language | Locale Code | Contributor | Status |
|----------|-------------|-------------|--------|
| English | `en_US` | Core language | Latest |
| Vietnamese | `vi_VN` | [maiminhdung](https://github.com/maiminhdung), [ptthanh02](https://github.com/ptthanh02) | Latest |
| Italian | `it_IT` | [RV_SkeLe](https://github.com/RVSkeLe) | Latest |
| Turkish | `tr_TR` | [@berkkorkmaz](https://discord.com/users/berkkorkmaz) | Latest |
| Chinese Simplified | `zh_CN` | [SnowCutieOwO](https://github.com/SnowCutieOwO) | v1.2.3 |

## API

For developers interested in integrating with SmartSpawner, visit our [API Documentation](https://github.com/ptthanh02/SmartSpawner/wiki/SmartSpawner-API-Documentation) for installation instructions and documentation.

## Building

```bash
git clone https://github.com/ptthanh02/SmartSpawner.git
cd SmartSpawner
./gradlew build
```

The compiled JAR will be available in `build/libs/`

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes and test thoroughly
4. Commit your changes (`git commit -m 'Add amazing feature'`)
5. Push to the branch (`git push origin feature/amazing-feature`)
6. Submit a pull request

## Support

- **Issues & Bug Reports:** [GitHub Issues](https://github.com/ptthanh02/SmartSprawner/issues)
- **Discord Community:** [Join our Discord](https://discord.gg/zrnyG4CuuT)

## Statistics

[![bStats](https://bstats.org/signatures/bukkit/SmartSpawner.svg)](https://bstats.org/plugin/bukkit/SmartSpawner)

## License

This project is licensed under the CC BY-NC-SA 4.0 License - see the [LICENSE](LICENSE) file for details.