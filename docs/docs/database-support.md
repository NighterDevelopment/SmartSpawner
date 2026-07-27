---
title: Database Support
---

# Database Support

Spawner data is stored in a database. Pick the mode that matches your setup:

| Mode | Use Case |
|------|----------|
| `SQLITE` | Single server. A local file, nothing to install. The default |
| `MYSQL` | Multiple servers, or a large server that already runs MySQL or MariaDB |

Set it with `database.mode` in `config.yml`.

Cross-server spawner listing is available in `MYSQL` mode through `/ss list`.

## Switching to MySQL

1. Set `database.mode` to `MYSQL`.
2. Fill in `database.sql` with your host, port, username and password.
3. Give each server a different `database.server_name`.
4. Restart. Your SQLite data is copied over on the first start.

## Moving from YAML

YAML storage was removed. If your `config.yml` still says `YAML`, the plugin switches it to `SQLITE`
for you on the next start and imports everything from `spawners_data.yml`. The old file is renamed to
`spawners_data.yml.migrated` so it is not imported twice. Nothing to do by hand.

::: tip
Keep a copy of your `plugins/SmartSpawner/` folder before updating, as you would for any update that
touches saved data.
:::
