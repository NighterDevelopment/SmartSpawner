package github.nighter.smartspawner.commands.config.editor;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Locale;

/**
 * The two settings files the in-game editor can change, and the rules that differ between them.
 *
 * <p>They share a shape (a top-level key per entry, each with {@code experience}, {@code loot} and
 * {@code mob_head}) but differ in what a key means and in which extra fields exist.</p>
 */
@Getter
public enum ConfigEditorTarget {

    /** {@code spawner_mobs.yml}, keyed by {@link EntityType}. */
    SMART_SPAWNER("spawner_mobs.yml", "config_editor.mob_list"),

    /** {@code spawner_items.yml}, keyed by {@link Material}. */
    ITEM_SPAWNER("spawner_items.yml", "config_editor.item_list");

    private final String fileName;
    private final String titleKey;

    ConfigEditorTarget(String fileName, String titleKey) {
        this.fileName = fileName;
        this.titleKey = titleKey;
    }

    /** Only mobs carry a spawner item drop chance. */
    public boolean supportsDropChance() {
        return this == SMART_SPAWNER;
    }

    /** True when {@code key} names something this file may hold an entry for. */
    public boolean isValidKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        String upper = key.trim().toUpperCase(Locale.ROOT);
        try {
            if (this == SMART_SPAWNER) {
                EntityType.valueOf(upper);
            } else {
                Material material = Material.valueOf(upper);
                return material.isItem();
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public ConfigEditorTarget other() {
        return this == SMART_SPAWNER ? ITEM_SPAWNER : SMART_SPAWNER;
    }
}
