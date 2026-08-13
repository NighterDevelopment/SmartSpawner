package github.nighter.smartspawner.commands.config.editor;

import lombok.Getter;
import github.nighter.smartspawner.spawner.config.SpawnerConfigName;

/**
 * The two settings files the in-game editor can change, and the rules that differ between them.
 *
 * <p>They share a shape (a top-level key per entry, each with {@code experience}, {@code loot} and
 * {@code mob_head}) but use different child keys to select what they spawn.</p>
 */
@Getter
public enum ConfigEditorTarget {

    /** {@code spawner_mobs.yml}, with an {@code entity} child key. */
    SMART_SPAWNER("smartspawner", "spawner_mobs.yml", "config_editor.mob_list"),

    /** {@code spawner_items.yml}, with an {@code item} child key. */
    ITEM_SPAWNER("itemspawner", "spawner_items.yml", "config_editor.item_list");

    private final String commandArgument;
    private final String fileName;
    private final String titleKey;

    ConfigEditorTarget(String commandArgument, String fileName, String titleKey) {
        this.commandArgument = commandArgument;
        this.fileName = fileName;
        this.titleKey = titleKey;
    }

    /** Only mobs carry a spawner item drop chance. */
    public boolean supportsDropChance() {
        return this == SMART_SPAWNER;
    }

    /** True when {@code key} can be normalized into a stable configuration name. */
    public boolean isValidKey(String key) {
        return !SpawnerConfigName.normalize(key).isEmpty();
    }

}
