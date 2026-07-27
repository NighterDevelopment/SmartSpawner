package github.nighter.smartspawner.commands.config.editor;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.config.ConfiguredItemParser;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every read and write the in-game config editor performs.
 *
 * <p>All mutations follow the same shape: load the file from disk, apply one change, save, reload the
 * components that read it. Reloading from disk each time rather than holding a parsed copy means an
 * admin editing the file by hand while a GUI is open loses at most the single field being changed,
 * instead of having their whole file overwritten from a stale snapshot.</p>
 *
 * <p>{@link YamlConfiguration} carries comments through a load and save, so the documentation inside
 * the shipped files survives being written by the editor.</p>
 */
public class ConfigEditorService {

    private final SmartSpawner plugin;

    public ConfigEditorService(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    // ============== Reading ==============

    private File fileOf(ConfigEditorTarget target) {
        return new File(plugin.getDataFolder(), target.getFileName());
    }

    private YamlConfiguration load(ConfigEditorTarget target) {
        return YamlConfiguration.loadConfiguration(fileOf(target));
    }

    /** Entry keys, sorted. Every entry is a section, so a stray scalar is not one. */
    public List<String> listEntries(ConfigEditorTarget target) {
        YamlConfiguration config = load(target);
        List<String> keys = new ArrayList<>();
        for (String key : config.getKeys(false)) {
            if (!config.isConfigurationSection(key)) {
                continue;
            }
            keys.add(key);
        }
        keys.sort(String::compareToIgnoreCase);
        return keys;
    }

    public boolean hasEntry(ConfigEditorTarget target, String key) {
        return load(target).isConfigurationSection(key);
    }

    public int getExperience(ConfigEditorTarget target, String key) {
        return load(target).getInt(key + ".experience", 0);
    }

    /** @return the configured drop chance, or null when the entry leaves it out (meaning 100). */
    public Double getDropChance(ConfigEditorTarget target, String key) {
        YamlConfiguration config = load(target);
        String path = key + ".drop_chance";
        return config.contains(path) ? config.getDouble(path) : null;
    }

    public String getHeadMaterial(ConfigEditorTarget target, String key) {
        return load(target).getString(key + ".mob_head.item");
    }

    public String getHeadTexture(ConfigEditorTarget target, String key) {
        return load(target).getString(key + ".mob_head.hash_texture");
    }

    /** Loot entry labels for one entry, in file order. */
    public List<String> listLootKeys(ConfigEditorTarget target, String key) {
        ConfigurationSection loot = load(target).getConfigurationSection(key + ".loot");
        return loot == null ? List.of() : new ArrayList<>(loot.getKeys(false));
    }

    public LootView readLoot(ConfigEditorTarget target, String key, String lootKey) {
        ConfigurationSection section = load(target).getConfigurationSection(key + ".loot." + lootKey);
        if (section == null) {
            return null;
        }

        // A label is just a position, so there is nothing to fall back to when item: is missing.
        String rawItem = section.getString("item");
        ItemStack preview;
        try {
            preview = rawItem == null ? null : ConfiguredItemParser.parse(rawItem);
        } catch (IllegalArgumentException e) {
            preview = null;
        }
        if (rawItem == null) {
            rawItem = "";
        }

        int[] amount = parseRange(section.getString("amount", "1-1"), 1, 1);
        int[] durability = section.contains("durability")
                ? parseRange(section.getString("durability"), 0, 0)
                : null;

        return new LootView(lootKey, rawItem, preview,
                amount[0], amount[1], section.getDouble("chance", 100.0),
                durability == null ? null : durability[0],
                durability == null ? null : durability[1]);
    }

    // ============== Writing ==============

    public void setExperience(ConfigEditorTarget target, String key, int value) {
        mutate(target, config -> config.set(key + ".experience", Math.max(0, value)));
    }

    /** @param value null removes the key, which restores the implicit 100. */
    public void setDropChance(ConfigEditorTarget target, String key, Double value) {
        mutate(target, config -> config.set(key + ".drop_chance",
                value == null ? null : round(clamp(value, 0.0, 100.0))));
    }

    public void setHeadTexture(ConfigEditorTarget target, String key, String material, String texture) {
        mutate(target, config -> {
            config.set(key + ".mob_head.item", material);
            config.set(key + ".mob_head.hash_texture",
                    texture == null || texture.isBlank() ? null : texture.trim());
        });
    }

    public void setLootAmount(ConfigEditorTarget target, String key, String lootKey, int min, int max) {
        int low = Math.max(0, Math.min(min, max));
        int high = Math.max(low, Math.max(min, max));
        mutate(target, config -> config.set(key + ".loot." + lootKey + ".amount", low + "-" + high));
    }

    public void setLootChance(ConfigEditorTarget target, String key, String lootKey, double chance) {
        mutate(target, config -> config.set(key + ".loot." + lootKey + ".chance",
                round(clamp(chance, 0.0, 100.0))));
    }

    /** @param min null on either side clears the durability range entirely. */
    public void setLootDurability(ConfigEditorTarget target, String key, String lootKey, Integer min, Integer max) {
        mutate(target, config -> {
            String path = key + ".loot." + lootKey + ".durability";
            if (min == null || max == null) {
                config.set(path, null);
                return;
            }
            int low = Math.max(0, Math.min(min, max));
            int high = Math.max(low, Math.max(min, max));
            config.set(path, low == high ? String.valueOf(low) : low + "-" + high);
        });
    }

    /**
     * Points a loot entry at an item the admin dropped into the capture GUI.
     *
     * <p>A plain vanilla item is written as its material name so the file stays readable. Anything
     * carrying extra data is written as a {@code nbt:} blob, which is the only form that survives a
     * round trip without losing components.</p>
     */
    public void setLootItem(ConfigEditorTarget target, String key, String lootKey, ItemStack item) {
        String value = describesItselfFully(item)
                ? item.getType().name()
                : ConfiguredItemParser.toNbtValue(item);
        mutate(target, config -> config.set(key + ".loot." + lootKey + ".item", value));
    }

    /** Adds a loot entry for a dropped item under a generated, unused label. */
    public String addLoot(ConfigEditorTarget target, String key, ItemStack item) {
        String label = uniqueLootLabel(target, key);
        String value = describesItselfFully(item)
                ? item.getType().name()
                : ConfiguredItemParser.toNbtValue(item);

        mutate(target, config -> {
            String path = key + ".loot." + label;
            config.set(path + ".item", value);
            config.set(path + ".amount", "1-1");
            config.set(path + ".chance", 100.0);
        });
        return label;
    }

    public void removeLoot(ConfigEditorTarget target, String key, String lootKey) {
        mutate(target, config -> config.set(key + ".loot." + lootKey, null));
    }

    /**
     * Creates an empty entry.
     *
     * @return false when the key is invalid for this file or already present
     */
    public boolean createEntry(ConfigEditorTarget target, String key) {
        String normalised = key.trim().toUpperCase(Locale.ROOT);
        if (!target.isValidKey(normalised) || hasEntry(target, normalised)) {
            return false;
        }

        mutate(target, config -> {
            config.set(normalised + ".experience", 0);
            config.set(normalised + ".mob_head.item", defaultHeadMaterial(target, normalised));
            config.set(normalised + ".mob_head.hash_texture", null);
        });
        return true;
    }

    public void deleteEntry(ConfigEditorTarget target, String key) {
        mutate(target, config -> config.set(key, null));
    }

    // ============== Internals ==============

    private interface Mutation {
        void apply(YamlConfiguration config);
    }

    /** Load, change, save, reload. The one place this file is written. */
    private void mutate(ConfigEditorTarget target, Mutation mutation) {
        File file = fileOf(target);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        mutation.apply(config);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + target.getFileName() + ": " + e.getMessage());
            return;
        }

        applyReload(target);
    }

    /**
     * Rereads the file into the live plugin. Spawner settings feed the loot tables held by every
     * loaded spawner, so those have to be recalculated too, and the spawner item shows its loot in
     * the lore, so its caches go as well.
     */
    private void applyReload(ConfigEditorTarget target) {
        if (target == ConfigEditorTarget.SMART_SPAWNER) {
            plugin.getSpawnerSettingsConfig().reload();
        } else {
            plugin.getItemSpawnerSettingsConfig().reload();
        }

        plugin.getSpawnerManager().reloadSpawnerDropsAndConfigs();
        if (plugin.getSpawnerItemFactory() != null) {
            plugin.getSpawnerItemFactory().clearAllCaches();
        }
    }

    /**
     * True when the material name alone rebuilds this item, so the config can stay readable instead
     * of holding an opaque blob.
     */
    private boolean describesItselfFully(ItemStack item) {
        return !item.hasItemMeta() || item.getItemMeta() == null || item.getItemMeta().equals(
                new ItemStack(item.getType()).getItemMeta());
    }

    /**
     * Loot entries are labelled by position, so a new one takes the lowest free number rather than
     * being named after its item. A label carries no meaning: the {@code item:} line is what says
     * what drops, which is why several entries can hold variants of the same material.
     */
    private String uniqueLootLabel(ConfigEditorTarget target, String key) {
        List<String> taken = listLootKeys(target, key);
        for (int i = 1; i <= taken.size() + 1; i++) {
            String candidate = String.valueOf(i);
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return String.valueOf(taken.size() + 1);
    }

    private String defaultHeadMaterial(ConfigEditorTarget target, String key) {
        if (target == ConfigEditorTarget.ITEM_SPAWNER) {
            return key;
        }
        return "PLAYER_HEAD";
    }

    private static int[] parseRange(String raw, int fallbackMin, int fallbackMax) {
        String value = raw == null ? "" : raw.trim();
        try {
            int separator = value.indexOf('-', 1);
            if (separator < 0) {
                int single = Integer.parseInt(value);
                return new int[]{single, single};
            }
            return new int[]{
                    Integer.parseInt(value.substring(0, separator).trim()),
                    Integer.parseInt(value.substring(separator + 1).trim())
            };
        } catch (NumberFormatException e) {
            return new int[]{fallbackMin, fallbackMax};
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Keeps percentages to one decimal so the file does not fill up with float noise. */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** A loot entry as the GUI needs to show it. */
    public record LootView(String key, String rawItem, ItemStack preview,
                           int minAmount, int maxAmount, double chance,
                           Integer minDurability, Integer maxDurability) {

        public boolean isBroken() {
            return preview == null;
        }

        public String amountLabel() {
            return minAmount == maxAmount ? String.valueOf(minAmount) : minAmount + "-" + maxAmount;
        }

        public String durabilityLabel() {
            if (minDurability == null || maxDurability == null) {
                return null;
            }
            return minDurability.equals(maxDurability)
                    ? String.valueOf(minDurability)
                    : minDurability + "-" + maxDurability;
        }
    }
}
