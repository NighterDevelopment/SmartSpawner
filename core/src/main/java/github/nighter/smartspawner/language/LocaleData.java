package github.nighter.smartspawner.language;

import org.bukkit.configuration.file.YamlConfiguration;

public record LocaleData(YamlConfiguration messages, YamlConfiguration gui, YamlConfiguration formatting,
                         YamlConfiguration items, YamlConfiguration commandMessages, YamlConfiguration hologram) {
}