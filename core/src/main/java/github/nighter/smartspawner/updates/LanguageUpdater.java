package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LanguageUpdater {
    private static final String VERSION_KEY = "language_version";
    private static final String[] SUPPORTED_LANGUAGES = {"en_US", "vi_VN", "de_DE"};

    private final SmartSpawner plugin;
    private final Set<LanguageFileType> activeFileTypes = new HashSet<>();

    public LanguageUpdater(SmartSpawner plugin) {
        this(plugin, LanguageFileType.values());
    }

    public LanguageUpdater(SmartSpawner plugin, LanguageFileType... fileTypes) {
        this.plugin = plugin;
        activeFileTypes.addAll(Arrays.asList(fileTypes));
        checkAndUpdateLanguageFiles();
    }

    @Getter
    public enum LanguageFileType {
        MESSAGES("messages.yml"),
        GUI("gui.yml"),
        FORMATTING("formatting.yml"),
        ITEMS("items.yml");

        private final String fileName;
        LanguageFileType(String fileName) { this.fileName = fileName; }
    }

    /**
     * Check and update all language files for all supported languages
     */
    public void checkAndUpdateLanguageFiles() {
        for (String language : SUPPORTED_LANGUAGES) {
            File langDir = new File(plugin.getDataFolder(), "language/" + language);
            langDir.mkdirs();

            for (LanguageFileType type : activeFileTypes) {
                File langFile = new File(langDir, type.getFileName());
                String resource = "language/" + language + "/" + type.getFileName();
                ConfigVersionService.updateFile(plugin, langFile, resource, VERSION_KEY);
            }
        }
    }
}
