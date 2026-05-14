package github.nighter.smartspawner.config;

import github.nighter.smartspawner.SmartSpawner;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
public class Config {
    @Getter(AccessLevel.NONE)
    private static volatile Config instance;

    private final boolean optimizedLootgen;

    private Config(FileConfiguration config) {
        this.optimizedLootgen = config.getBoolean("loot_generation.optimized_generation");
    }

    public static Config get() {
        return instance;
    }

    public static void reload(SmartSpawner plugin) {
        load(plugin);
    }

    public static void load(SmartSpawner plugin) {
        instance = new Config(plugin.getConfig());
    }
}
