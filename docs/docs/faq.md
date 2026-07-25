---
title: FAQ
description: Answers to common questions about SmartSpawner warnings, reloading, Bedrock support, and placeholders.
---

# Frequently Asked Questions

## Why am I seeing a warning under Spawners?

![Warning shown to OP players under a spawner](/op-warning.png)

Mojang added this warning in Minecraft 1.21.4. It only shows to players who are OP, and there is no way to remove it.

::: tip Read more
[Minecraft Java Edition 1.21.4 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4)
:::

## Is it safe to reload SmartSpawner?

Use `/smartspawner reload`. It safely applies most changes made to the config files.

Avoid these unsafe methods:

- `/reload` breaks plugins and causes unexpected behavior.
- PlugMan load, reload, or unload can make SmartSpawner malfunction.

To install or update SmartSpawner, restart your server after making changes or adding the plugin.

::: tip Read more
[The problem with /reload](https://madelinemiller.dev/blog/problem-with-reload/)
:::

## Why won't the spawner menu open for Bedrock players?

This is a current limitation of Geyser, the Bedrock compatibility layer. Geyser cannot tell left-click and right-click apart from Bedrock clients, unlike on Java Edition.

Because of this, holding a tool such as a pickaxe, shovel, or axe stops the spawner menu from opening. The click cannot be read the way SmartSpawner expects.

::: tip Read more
[Geyser current limitations](https://geysermc.org/wiki/geyser/current-limitations/)
:::

## I updated SmartSpawner and my %placeholders% stopped working

The placeholder format changed from `%placeholder%` to `{placeholder}` in version [1.5.5](https://modrinth.com/plugin/smart-spawner-plugin/version/1.5.5).

Fix it with one of these two methods.

**Regenerate the language files**

1. Delete the `/language/` folder.
2. Restart your server. Updated files are generated automatically.

**Convert manually**

1. Open your language files in a text editor such as VSCode or Notepad++.
2. Open Find and Replace (CTRL+H).
3. Find: `%(.+?)%`
4. Replace: `{$1}`

There is no automatic converter, so use one of the two methods above.
