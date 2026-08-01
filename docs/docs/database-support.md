---
title: Database Support
---

# Database Support

Spawner data is stored in a database. Pick the mode that matches your setup:

| Mode | Use Case |
|------|----------|
| `SQLITE` | Single server. A local file, nothing to install. The default |
| `MYSQL` | Multiple servers, or a large server that already runs MySQL or MariaDB |

Set it with `database.type` in `config.yml`.

Cross-server spawner listing is available in `MYSQL` mode through `/ss list`.

::: warning RESTART
Every setting in the `database` section except `autosave-interval` is only read when the server
starts. `/ss reload` does not apply them.
:::

## Switching to MySQL

1. Set `database.type` to `MYSQL`.
2. Fill in `database.host`, `database.port`, `database.database`, `database.username` and `database.password`.
3. Give each server a different `database.server-name`.
4. Restart. Existing SQLite data is copied over on the first start.

## How often data is saved

Spawner changes are collected and written in batches rather than one at a time.
`database.autosave-interval` sets how often that happens, and defaults to `3m` with a minimum of
`30s`. Data is also saved when the world saves and when the server stops, so the interval is a safety
net rather than the only save.

Raise it on a busy server to cut disk writes. Lower it to shorten how much recent activity a server
crash could lose. It is the only setting in the section that `/ss reload` applies.

## Moving from YAML

YAML storage was removed. If your `config.yml` still says `YAML`, the plugin switches it to `SQLITE`
for you on the next start and imports everything from `spawners_data.yml`. The old file is renamed to
`spawners_data.yml.migrated` so it is not imported twice. Nothing to do by hand.

## Table names

The plugin creates two tables, named after `database.table-prefix`: `sspawner_data` and
`sspawner_schema_meta`. Change the prefix when another plugin already uses those names in the same
database, or to keep two SmartSpawner installs apart in one MySQL database. Existing tables are
renamed automatically when the prefix changes.

With `sync-across-servers` on, each server gets its own table named after it, for example
`sspawner_server1_data`. Turning the setting on or off renames the table to match, and a table
already sitting at the target name is never overwritten. The console says so if it finds one.

::: tip
Keep a copy of your `plugins/SmartSpawner/` folder before updating, as you would for any update that
touches saved data.
:::
