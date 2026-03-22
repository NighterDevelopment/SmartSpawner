# SmartSpawner Changelog

All notable changes to SmartSpawner are documented here.
Auto-updated by the Gradle `generateChangelog` task when a new version is built.

Repository: <https://github.com/NighterDevelopment/SmartSpawner>
Releases:   <https://github.com/NighterDevelopment/SmartSpawner/releases>

---

## [1.6.3] – 2026-03-22

**Compare:** [v1.6.2 → v1.6.3](https://github.com/NighterDevelopment/SmartSpawner/compare/v1.6.2...v1.6.3)

### Improvements
- Merged `discord.yml` and `discord/event_defaults.yml` into a single `discord_logging.yml`
  for centralised Discord configuration.

### Added
- `language/CHANGELOG.txt` — human-readable language-key changelog extracted to the
  plugin data folder on every update so server owners know which keys changed.
- Migration path: legacy `discord.yml` is automatically migrated to `discord_logging.yml`
  on first server start with the new version.

---

