<div align="center">
  
![banner](https://github.com/user-attachments/assets/c976b6a9-537c-46ec-8efc-0e80cdd0840d)

[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/smart-spawner-plugin)
[![Spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg)](https://www.spigotmc.org/resources/120743/)
[![Hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg)](https://hangar.papermc.io/Nighter/SmartSpawner)

[![Documentation](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/documentation/ghpages_vector.svg)](https://nighterdevelopment.github.io/smartspawner-docs/)
[![discord-plural](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/social/discord-plural_46h.png)](http://discord.com/invite/FJN7hJKPyb)

</div>

## Requirements

- **Minecraft Version:** 1.21 - 1.21.10
- **Server Software:** Paper, Folia or compatible forks
- **Java Version:** 21+

## Localization

| Language          | Locale Code | Contributor                                     | Status |
|-------------------|-------------|-------------------------------------------------|--------|
| Chinese Simplified | `zh_CN` | [SnowCutieOwO](https://github.com/SnowCutieOwO) | v1.2.3 |
| German            | `de_DE` | [jannispkz](https://github.com/jannispkz)       | Latest |
| English           | `en_US` | core language                                   | Latest |
| Italian           | `it_IT` | [RV_SkeLe](https://github.com/RVSkeLe)          | v1.3.5 |
| Turkish           | `tr_TR` | berkkorkmaz                                     | v1.3.5 |
| Vietnamese        | `vi_VN` | [ptthanh02](https://github.com/ptthanh02)       | Latest |

## API

For developers interested in integrating with SmartSpawner, visit our [Developer API Documentation](https://nighterdevelopment.github.io/smartspawner-docs/developer-api/) for installation instructions and documentation.

## Security

### Version 1.5.5 Security Patch

**Critical Item Duplication Exploit Fixed** 🔒

Version 1.5.5 includes a comprehensive security patch that eliminates a race condition vulnerability in the spawner storage system. The patch implements an 11-layer security system to prevent item duplication exploits.

**For detailed information:**
- 📖 [Security Patch Documentation](SECURITY_PATCH_ANTI_DUPE.md) - Complete security analysis
- 🧪 [Test Scenarios](TEST_SCENARIOS_ANTI_DUPE.md) - Verification procedures
- 📊 [Implementation Details](IMPLEMENTATION_SUMMARY.md) - Technical implementation
- 🎨 [Visual Guide](VISUAL_GUIDE_ANTI_DUPE.md) - Visual explanation of the fix

**Key Features:**
- ✅ Atomic transaction processing
- ✅ Per-player transaction locking
- ✅ Rate limiting and spam protection
- ✅ Automatic rollback on failure
- ✅ Comprehensive security logging
- ✅ Thread-safe for Folia compatibility

**Impact:** All servers should update to 1.5.5+ to prevent potential item duplication exploits.

## Building

```bash
git clone https://github.com/NighterDevelopment/smartspawner.git
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

- **Issues & Bug Reports:** [GitHub Issues](https://github.com/NighterDevelopment/smartspawner/issues)
- **Discord Community:** [Join our Discord](https://discord.gg/zrnyG4CuuT)

## Statistics

[![bStats](https://bstats.org/signatures/bukkit/SmartSpawner.svg)](https://bstats.org/plugin/bukkit/SmartSpawner)

## License

This project is licensed under the CC BY-NC-SA 4.0 License - see the [LICENSE](LICENSE) file for details.
