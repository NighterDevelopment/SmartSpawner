package github.nighter.smartspawner.language;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class LanguageManager {
    private final JavaPlugin plugin;
    @Getter private String defaultLocale;
    private final Map<String, LocaleData> localeMap = new HashMap<>();
    private final Set<String> activeLocales = new HashSet<>();
    private final Set<LanguageFileType> activeFileTypes = new HashSet<>();
    private LocaleData cachedDefaultLocaleData;
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    // Enhanced cache implementation
    private final LRUCache<String, String> formattedStringCache;
    private final LRUCache<String, String[]> loreCache;
    private final LRUCache<String, List<String>> loreListCache;

    private final LRUCache<String, String> guiItemNameCache;
    private final LRUCache<String, String[]> guiItemLoreCache;
    private final LRUCache<String, List<String>> guiItemLoreListCache;

    private final LRUCache<String, String> entityNameCache;
    private final LRUCache<String, String> smallCapsCache;
    private final LRUCache<String, String> materialNameCache;

    // Cache statistics
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);

    // Cache configuration
    private static final int DEFAULT_STRING_CACHE_SIZE = 1000;
    private static final int DEFAULT_LORE_CACHE_SIZE = 250;
    private static final int DEFAULT_LORE_LIST_CACHE_SIZE = 250;

    // Enum to represent the different language file types
    @Getter
    public enum LanguageFileType {
        MESSAGES("messages.yml"),
        GUI("gui.yml"),
        FORMATTING("formatting.yml"),
        ITEMS("items.yml"),
        COMMAND_MESSAGES("command_messages.yml"),
        HOLOGRAM("hologram.yml");
        private final String fileName;
        LanguageFileType(String fileName) {
            this.fileName = fileName;
        }
    }

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.defaultLocale = plugin.getConfig().getString("language", "en_US");
        activeFileTypes.addAll(Arrays.asList(LanguageFileType.values()));

        this.formattedStringCache = new LRUCache<>(DEFAULT_STRING_CACHE_SIZE);
        this.loreCache = new LRUCache<>(DEFAULT_LORE_CACHE_SIZE);
        this.loreListCache = new LRUCache<>(DEFAULT_LORE_LIST_CACHE_SIZE);

        this.guiItemNameCache = new LRUCache<>(DEFAULT_STRING_CACHE_SIZE);
        this.guiItemLoreCache = new LRUCache<>(DEFAULT_LORE_CACHE_SIZE);
        this.guiItemLoreListCache = new LRUCache<>(DEFAULT_LORE_LIST_CACHE_SIZE);

        this.entityNameCache = new LRUCache<>(250);
        this.smallCapsCache = new LRUCache<>(500);
        this.materialNameCache = new LRUCache<>(250);

        loadLanguages();
        saveDefaultFiles();
        cacheDefaultLocaleData();
    }

    public LanguageManager(SmartSpawner plugin, LanguageFileType... fileTypes) {
        this.plugin = plugin;
        this.defaultLocale = plugin.getConfig().getString("language", "en_US");
        activeFileTypes.addAll(Arrays.asList(fileTypes));

        this.formattedStringCache = new LRUCache<>(DEFAULT_STRING_CACHE_SIZE);
        this.loreCache = new LRUCache<>(DEFAULT_LORE_CACHE_SIZE);
        this.loreListCache = new LRUCache<>(DEFAULT_LORE_LIST_CACHE_SIZE);

        this.guiItemNameCache = new LRUCache<>(DEFAULT_STRING_CACHE_SIZE);
        this.guiItemLoreCache = new LRUCache<>(DEFAULT_LORE_CACHE_SIZE);
        this.guiItemLoreListCache = new LRUCache<>(DEFAULT_LORE_LIST_CACHE_SIZE);

        this.entityNameCache = new LRUCache<>(250);
        this.smallCapsCache = new LRUCache<>(500);
        this.materialNameCache = new LRUCache<>(250);

        loadLanguages(fileTypes);
        saveDefaultFiles();
        cacheDefaultLocaleData();
    }

    //---------------------------------------------------
    //                 Core Methods
    //---------------------------------------------------

    private void saveDefaultFiles() {
        Map<String, Set<LanguageFileType>> localeFileMap = new HashMap<>();
        localeFileMap.put("vi_VN", EnumSet.allOf(LanguageFileType.class));
        localeFileMap.put("DonutSMP", EnumSet.allOf(LanguageFileType.class));
        localeFileMap.put("de_DE", EnumSet.allOf(LanguageFileType.class));

        localeFileMap.forEach((locale, fileTypes) -> {
            fileTypes.forEach(fileType -> {
                saveResource(String.format("language/%s/%s", locale, fileType.getFileName()));
            });
        });
    }

    private void saveResource(String resourcePath) {
        File resourceFile = new File(plugin.getDataFolder(), resourcePath);
        if (!resourceFile.exists()) {
            resourceFile.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }

    public void loadLanguages() {
        loadLanguages(activeFileTypes.toArray(new LanguageFileType[0]));
    }

    public void loadLanguages(LanguageFileType... fileTypes) {
        File langDir = new File(plugin.getDataFolder(), "language");
        if (!langDir.exists() && !langDir.mkdirs()) {
            plugin.getLogger().severe("Failed to create language directory!");
            return;
        }

        // Clear existing locale data for the default locale to ensure a fresh load
        localeMap.remove(defaultLocale);

        // Load only the default locale
        loadLocale(defaultLocale, fileTypes);
        activeLocales.add(defaultLocale);
    }

    private void cacheDefaultLocaleData() {
        cachedDefaultLocaleData = localeMap.get(defaultLocale);
        if (cachedDefaultLocaleData == null) {
            plugin.getLogger().severe("Failed to cache default locale data for " + defaultLocale);
            // Create empty configs as fallback
            cachedDefaultLocaleData = new LocaleData(
                    new YamlConfiguration(),
                    new YamlConfiguration(),
                    new YamlConfiguration(),
                    new YamlConfiguration(),
                    new YamlConfiguration(),
                    new YamlConfiguration()
            );
            localeMap.put(defaultLocale, cachedDefaultLocaleData);
        }
    }

    public void reloadLanguages() {
        // Clear all caches first to avoid using stale data
        clearCache();

        // Get the previous default locale
        String previousDefaultLocale = this.defaultLocale;

        // Update the default locale from config
        this.defaultLocale = plugin.getConfig().getString("language", "en_US");

        // Force reload all locale files for all active locales
        for (String locale : activeLocales) {
            // Remove current locale data to ensure fresh load
            localeMap.remove(locale);
            // Force reload all file types for this locale
            for (LanguageFileType fileType : activeFileTypes) {
                YamlConfiguration config = loadOrCreateFile(locale, fileType.getFileName(), true);
                updateLocaleData(locale, fileType, config);
            }
        }

        // Load the new default locale if it's not already loaded
        if (!activeLocales.contains(this.defaultLocale)) {
            loadLocale(this.defaultLocale, activeFileTypes.toArray(new LanguageFileType[0]));
            activeLocales.add(this.defaultLocale);
        }

        // Re-cache the default locale data
        cacheDefaultLocaleData();

        plugin.getLogger().info("Successfully reloaded language files for language " + this.defaultLocale);
    }

    // Add this helper method to update locale data
    private void updateLocaleData(String locale, LanguageFileType fileType, YamlConfiguration config) {
        LocaleData existingData = localeMap.getOrDefault(locale,
                new LocaleData(new YamlConfiguration(), new YamlConfiguration(),
                        new YamlConfiguration(), new YamlConfiguration(), new YamlConfiguration(),
                        new YamlConfiguration()));

        switch (fileType) {
            case MESSAGES:
                localeMap.put(locale, new LocaleData(config, existingData.gui(),
                        existingData.formatting(), existingData.items(), existingData.commandMessages(),
                        existingData.hologram()));
                break;
            case GUI:
                localeMap.put(locale, new LocaleData(existingData.messages(), config,
                        existingData.formatting(), existingData.items(), existingData.commandMessages(),
                        existingData.hologram()));
                break;
            case FORMATTING:
                localeMap.put(locale, new LocaleData(existingData.messages(), existingData.gui(),
                        config, existingData.items(), existingData.commandMessages(),
                        existingData.hologram()));
                break;
            case ITEMS:
                localeMap.put(locale, new LocaleData(existingData.messages(), existingData.gui(),
                        existingData.formatting(), config, existingData.commandMessages(),
                        existingData.hologram()));
                break;
            case COMMAND_MESSAGES:
                localeMap.put(locale, new LocaleData(existingData.messages(), existingData.gui(),
                        existingData.formatting(), existingData.items(), config,
                        existingData.hologram()));
                break;
            case HOLOGRAM:
                localeMap.put(locale, new LocaleData(existingData.messages(), existingData.gui(),
                        existingData.formatting(), existingData.items(), existingData.commandMessages(),
                        config));
                break;
        }
    }

    // Modify the loadOrCreateFile method to add a parameter to force reload
    private YamlConfiguration loadOrCreateFile(String locale, String fileName, boolean forceReload) {
        File file = new File(plugin.getDataFolder(), "language/" + locale + "/" + fileName);
        YamlConfiguration defaultConfig = new YamlConfiguration();
        YamlConfiguration userConfig = new YamlConfiguration();

        // Check if the default resource exists before trying to load it
        boolean defaultResourceExists = plugin.getResource("language/" + defaultLocale + "/" + fileName) != null;

        // Load default configuration from resources if it exists
        if (defaultResourceExists) {
            try (InputStream inputStream = plugin.getResource("language/" + defaultLocale + "/" + fileName)) {
                if (inputStream != null) {
                    defaultConfig.loadFromString(new String(inputStream.readAllBytes()));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load default " + fileName, e);
            }
        }

        // Create file if it doesn't exist and the default resource exists
        if (!file.exists() && defaultResourceExists) {
            try (InputStream inputStream = plugin.getResource("language/" + defaultLocale + "/" + fileName)) {
                if (inputStream != null) {
                    file.getParentFile().mkdirs(); // Ensure parent directory exists
                    Files.copy(inputStream, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create " + fileName + " for locale " + locale, e);
                return new YamlConfiguration(); // Return empty config to avoid further errors
            }
        }

        // Load user configuration if file exists
        if (file.exists()) {
            try {
                // When forceReload is true, we always create a new YamlConfiguration
                // instance to ensure we're not using any cached data
                if (forceReload) {
                    userConfig = new YamlConfiguration();
                }

                // Use loadConfiguration for a fresh load from disk when forceReload is true
                if (forceReload) {
                    userConfig = YamlConfiguration.loadConfiguration(file);
                } else {
                    userConfig.load(file);
                }

                // Log reload information
//                if (forceReload) {
//                    plugin.getLogger().info("Force reloaded " + fileName + " for locale " + locale);
//                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load " + fileName + " for locale " + locale + ". Using defaults.", e);
                return defaultConfig; // Return default config if user config can't be loaded
            }

            // Merge configurations (add missing keys from default to user config)
            boolean updated = false;
            for (String key : defaultConfig.getKeys(false)) {
                if (!userConfig.contains(key)) {
                    userConfig.set(key, defaultConfig.get(key));
                    updated = true;
                }
            }

            // Save if updated
            if (updated) {
                try {
                    userConfig.save(file);
                    plugin.getLogger().info("Updated " + fileName + " for locale " + locale);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to save updated " + fileName + " for locale " + locale, e);
                }
            }

            return userConfig;
        } else {
            // If file doesn't exist and we couldn't create it, return empty config
            return new YamlConfiguration();
        }
    }

    // Overload for backward compatibility
    private YamlConfiguration loadOrCreateFile(String locale, String fileName) {
        return loadOrCreateFile(locale, fileName, false);
    }

    private void loadLocale(String locale, LanguageFileType... fileTypes) {
        File localeDir = new File(plugin.getDataFolder(), "language/" + locale);
        if (!localeDir.exists() && !localeDir.mkdirs()) {
            plugin.getLogger().severe("Failed to create locale directory for " + locale);
            return;
        }

        // Create and load or update only the specified files
        YamlConfiguration messages = null;
        YamlConfiguration gui = null;
        YamlConfiguration formatting = null;
        YamlConfiguration items = null;
        YamlConfiguration commandMessages = null;
        YamlConfiguration hologram = null;

        for (LanguageFileType fileType : fileTypes) {
            switch (fileType) {
                case MESSAGES:
                    messages = loadOrCreateFile(locale, fileType.getFileName());
                    break;
                case GUI:
                    gui = loadOrCreateFile(locale, fileType.getFileName());
                    break;
                case FORMATTING:
                    formatting = loadOrCreateFile(locale, fileType.getFileName());
                    break;
                case ITEMS:
                    items = loadOrCreateFile(locale, fileType.getFileName());
                    break;
                case COMMAND_MESSAGES:
                    commandMessages = loadOrCreateFile(locale, fileType.getFileName());
                    break;
                case HOLOGRAM:
                    hologram = loadOrCreateFile(locale, fileType.getFileName());
                    break;
            }
        }

        // If a file wasn't specified, create an empty configuration
        if (messages == null) messages = new YamlConfiguration();
        if (gui == null) gui = new YamlConfiguration();
        if (formatting == null) formatting = new YamlConfiguration();
        if (items == null) items = new YamlConfiguration();
        if (commandMessages == null) commandMessages = new YamlConfiguration();
        if (hologram == null) hologram = new YamlConfiguration();

        localeMap.put(locale, new LocaleData(messages, gui, formatting, items, commandMessages, hologram));
    }

    //---------------------------------------------------
    //               Messages Methods
    //---------------------------------------------------

    /**
     * Looks up a message key first in command_messages.yml, then falls back to messages.yml.
     */
    private String resolveMessageString(String path) {
        String value = cachedDefaultLocaleData.commandMessages().getString(path);
        if (value == null) {
            value = cachedDefaultLocaleData.messages().getString(path);
        }
        return value;
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        if (!isMessageEnabled(key)) {
            return null;
        }

        String message = resolveMessageString(key + ".message");

        if (message == null) {
            return "Missing message: " + key;
        }

        // Apply prefix
        String prefix = getPrefix();
        message = prefix + message;

        // Apply placeholders and color formatting
        return applyPlaceholdersAndColors(message, placeholders);
    }

    public String getMessageWithoutPrefix(String key, Map<String, String> placeholders) {
        if (!isMessageEnabled(key)) {
            return null;
        }

        String message = resolveMessageString(key + ".message");

        if (message == null) {
            return "Missing message: " + key;
        }

        // Apply placeholders and color formatting
        return applyPlaceholdersAndColors(message, placeholders);
    }

    public String getMessageForConsole(String key, Map<String, String> placeholders) {
        if (!isMessageEnabled(key)) {
            return null;
        }

        String message = resolveMessageString(key + ".message");

        if (message == null) {
            return "Missing message: " + key;
        }

        return applyOnlyPlaceholders(message, placeholders);
    }

    public String getTitle(String key, Map<String, String> placeholders) {
        if (!isMessageEnabled(key)) {
            return null;
        }
        return getRawMessage(key + ".title", placeholders);
    }

    public String getSubtitle(String key, Map<String, String> placeholders) {
        if (!isMessageEnabled(key)) {
            return null;
        }
        return getRawMessage(key + ".subtitle", placeholders);
    }

    public String getActionBar(String key, Map<String, String> placeholders) {
        if (!isMessageEnabled(key)) {
            return null;
        }
        return getRawMessage(key + ".action_bar", placeholders);
    }

    public String getSound(String key) {
        if (!isMessageEnabled(key)) {
            return null;
        }
        String sound = cachedDefaultLocaleData.commandMessages().getString(key + ".sound");
        if (sound == null) {
            sound = cachedDefaultLocaleData.messages().getString(key + ".sound");
        }
        return sound;
    }

    private String getPrefix() {
        return cachedDefaultLocaleData.messages().getString("prefix", "&7[Server] &r");
    }

    String getRawMessage(String path, Map<String, String> placeholders) {
        String message = cachedDefaultLocaleData.commandMessages().getString(path);
        if (message == null) {
            message = cachedDefaultLocaleData.messages().getString(path);
        }

        if (message == null) {
            return null;  // Return null instead of error message
        }

        return applyPlaceholdersAndColors(message, placeholders);
    }

    private boolean isMessageEnabled(String key) {
        // Check if this message has an enabled flag, default to true if not specified
        boolean enabledInCmd = cachedDefaultLocaleData.commandMessages().getBoolean(key + ".enabled", true);
        // If the key doesn't exist in commandMessages at all, also check messages
        if (!cachedDefaultLocaleData.commandMessages().contains(key)) {
            return cachedDefaultLocaleData.messages().getBoolean(key + ".enabled", true);
        }
        return enabledInCmd;
    }

    public boolean keyExists(String key) {
        return cachedDefaultLocaleData.commandMessages().contains(key)
                || cachedDefaultLocaleData.messages().contains(key);
    }

    /**
     * Returns a raw (colour-formatted) string from command_messages.yml at an arbitrary path.
     * Useful for reading component/bossbar text that does not follow the standard message key format.
     * Returns {@code defaultValue} if the key is absent.
     */
    public String getCommandConfig(String path, String defaultValue) {
        String value = cachedDefaultLocaleData.commandMessages().getString(path);
        if (value == null) return defaultValue;
        return applyPlaceholdersAndColors(value, EMPTY_PLACEHOLDERS);
    }

    /**
     * Returns a raw string from command_messages.yml with placeholder substitution.
     */
    public String getCommandConfig(String path, String defaultValue, Map<String, String> placeholders) {
        String value = cachedDefaultLocaleData.commandMessages().getString(path);
        if (value == null) return defaultValue;
        return applyPlaceholdersAndColors(value, placeholders);
    }

    //---------------------------------------------------
    //                  GUI Methods
    //---------------------------------------------------

    public String getGuiTitle(String key) {
        return getGuiTitle(key, EMPTY_PLACEHOLDERS);
    }

    public String getGuiTitle(String key, Map<String, String> placeholders) {
        if (!activeFileTypes.contains(LanguageFileType.GUI)) {
            return null;
        }

        String title = cachedDefaultLocaleData.gui().getString(key);

        if (title == null) {
            return "Missing GUI title: " + key;
        }

        return applyPlaceholdersAndColors(title, placeholders);
    }

    public String getGuiItemName(String key) {
        return getGuiItemName(key, EMPTY_PLACEHOLDERS);
    }

    public String getGuiItemName(String key, Map<String, String> placeholders) {
        if (!activeFileTypes.contains(LanguageFileType.GUI)) {
            return null;
        }

        // Generate cache key
        String cacheKey = key + "|" + generateCacheKey("", placeholders);

        // Check cache first
        String cachedName = guiItemNameCache.get(cacheKey);
        if (cachedName != null) {
            cacheHits.incrementAndGet();
            return cachedName;
        }

        // Cache miss, generate the name
        cacheMisses.incrementAndGet();
        String name = cachedDefaultLocaleData.gui().getString(key);

        if (name == null) {
            return "Missing item name: " + key;
        }

        String result = applyPlaceholdersAndColors(name, placeholders);

        // Cache the result
        guiItemNameCache.put(cacheKey, result);

        return result;
    }

    public String[] getGuiItemLore(String key) {
        return getGuiItemLore(key, EMPTY_PLACEHOLDERS);
    }

    public String[] getGuiItemLore(String key, Map<String, String> placeholders) {
        if (!activeFileTypes.contains(LanguageFileType.GUI)) {
            return new String[0];
        }

        // Generate cache key
        String cacheKey = key + "|" + generateCacheKey("", placeholders);

        // Check cache first
        String[] cachedLore = guiItemLoreCache.get(cacheKey);
        if (cachedLore != null) {
            cacheHits.incrementAndGet();
            return cachedLore;
        }

        // Cache miss, generate the lore
        cacheMisses.incrementAndGet();
        List<String> loreList = cachedDefaultLocaleData.gui().getStringList(key);
        String[] result = loreList.stream()
                .map(line -> applyPlaceholdersAndColors(line, placeholders))
                .toArray(String[]::new);

        // Cache the result
        guiItemLoreCache.put(cacheKey, result);

        return result;
    }

    public List<String> getGuiItemLoreAsList(String key) {
        return getGuiItemLoreAsList(key, EMPTY_PLACEHOLDERS);
    }

    public List<String> getGuiItemLoreAsList(String key, Map<String, String> placeholders) {
        if (!activeFileTypes.contains(LanguageFileType.GUI)) {
            return Collections.emptyList();
        }

        // Generate cache key
        String cacheKey = key + "|" + generateCacheKey("", placeholders);

        // Check cache first
        List<String> cachedLore = guiItemLoreListCache.get(cacheKey);
        if (cachedLore != null) {
            cacheHits.incrementAndGet();
            return cachedLore;
        }

        // Cache miss, generate the lore
        cacheMisses.incrementAndGet();
        List<String> loreList = cachedDefaultLocaleData.gui().getStringList(key);
        List<String> result = loreList.stream()
                .map(line -> applyPlaceholdersAndColors(line, placeholders))
                .toList();

        // Cache the result
        guiItemLoreListCache.put(cacheKey, result);

        return result;
    }
    
    /**
     * Gets GUI item lore with support for multi-line placeholders
     * This method expands any placeholder that contains newline characters into multiple lines
     */
    public List<String> getGuiItemLoreWithMultilinePlaceholders(String key, Map<String, String> placeholders) {
        if (!activeFileTypes.contains(LanguageFileType.GUI)) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        List<String> loreList = cachedDefaultLocaleData.gui().getStringList(key);

        for (String line : loreList) {
            // Check if the line contains a placeholder that might have multiple lines
            boolean containsMultilinePlaceholder = false;

            // First, identify placeholders in the line
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (line.contains(placeholder) && entry.getValue().contains("\n")) {
                    containsMultilinePlaceholder = true;
                    break;
                }
            }

            if (containsMultilinePlaceholder) {
                // Process special case: line contains multi-line placeholder
                String processedLine = line;

                // Apply non-multiline placeholders first
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    String placeholder = "{" + entry.getKey() + "}";
                    String value = entry.getValue();

                    if (!value.contains("\n")) {
                        processedLine = processedLine.replace(placeholder, value);
                    }
                }

                // Now handle multiline placeholders
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    String placeholder = "{" + entry.getKey() + "}";
                    String value = entry.getValue();

                    if (processedLine.contains(placeholder) && value.contains("\n")) {
                        // Split the placeholder value into lines
                        String[] valueLines = value.split("\n");

                        // Replace the placeholder in the first line
                        String firstLine = processedLine.replace(placeholder, valueLines[0]);
                        result.add(ColorUtil.translateHexColorCodes(firstLine));

                        // Add remaining lines with the same formatting/indentation
                        String lineStart = processedLine.substring(0, processedLine.indexOf(placeholder));
                        for (int i = 1; i < valueLines.length; i++) {
                            result.add(ColorUtil.translateHexColorCodes(lineStart + valueLines[i]));
                        }
                    }
                }
            } else {
                // Standard processing for lines without multi-line placeholders
                result.add(applyPlaceholdersAndColors(line, placeholders));
            }
        }

        return result;
    }

    //---------------------------------------------------
    //                  Items Methods
    //---------------------------------------------------

    public String getVanillaItemName(Material material) {
        if (material == null) {
            return "Unknown Item";
        }

        // Generate cache key
        String cacheKey = "material|" + material.name();

        // Check cache first
        String cachedName = materialNameCache.get(cacheKey);
        if (cachedName != null) {
            cacheHits.incrementAndGet();
            return cachedName;
        }

        // Cache miss, generate the name
        cacheMisses.incrementAndGet();

        // Generate the key using the material name directly
        String key = "item." + material.name() + ".name";

        // Get from items.yml if available
        String name = null;
        if (activeFileTypes.contains(LanguageFileType.ITEMS)) {
            name = cachedDefaultLocaleData.items().getString(key);
        }

        // If the key doesn't exist in the config, fall back to a nicely formatted material name
        if (name == null) {
            name = formatEnumName(material.name());
        } else {
            name = applyPlaceholdersAndColors(name, null);
        }

        // Cache the result
        materialNameCache.put(cacheKey, name);

        return name;
    }

    public String[] getVanillaItemLore(Material material) {
        if (material == null) {
            return new String[0];
        }

        // Generate the key using the material name directly
        String key = "item." + material.name() + ".lore";

        // Reuse existing method with the constructed key
        return getItemLore(key);
    }

    public String getItemName(String key) {
        return getItemName(key, EMPTY_PLACEHOLDERS);
    }

    public String getItemName(String key, Map<String, String> placeholders) {
        if (!activeFileTypes.contains(LanguageFileType.ITEMS)) {
            return key;
        }

        String name = cachedDefaultLocaleData.items().getString(key);
        if (name == null) {
            return key;
        }

        return applyPlaceholdersAndColors(name, placeholders);
    }

    public String[] getItemLore(String key) {
        return getItemLore(key, EMPTY_PLACEHOLDERS);
    }

    public String[] getItemLore(String key, Map<String, String> placeholders) {
        if (!activeFileTypes.contains(LanguageFileType.ITEMS)) {
            return new String[0];
        }

        // Generate cache key
        String cacheKey = key + "|" + generateCacheKey("", placeholders);

        // Check cache first
        String[] cachedLore = loreCache.get(cacheKey);
        if (cachedLore != null) {
            cacheHits.incrementAndGet();
            return cachedLore;
        }

        // Cache miss, generate the lore
        cacheMisses.incrementAndGet();
        List<String> loreList = cachedDefaultLocaleData.items().getStringList(key);
        String[] result = loreList.stream()
                .map(line -> applyPlaceholdersAndColors(line, placeholders))
                .toArray(String[]::new);

        // Cache the result
        loreCache.put(cacheKey, result);

        return result;
    }

    /**
     * Gets item lore with support for multi-line placeholders
     * This method expands any placeholder that contains newline characters into multiple lines
     */
    public List<String> getItemLoreWithMultilinePlaceholders(String key, Map<String, String> placeholders) {
        if (!activeFileTypes.contains(LanguageFileType.ITEMS)) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        List<String> loreList = cachedDefaultLocaleData.items().getStringList(key);

        for (String line : loreList) {
            // Check if the line contains a placeholder that might have multiple lines
            boolean containsMultilinePlaceholder = false;

            // First, identify placeholders in the line
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (line.contains(placeholder) && entry.getValue().contains("\n")) {
                    containsMultilinePlaceholder = true;
                    break;
                }
            }

            if (containsMultilinePlaceholder) {
                // Process special case: line contains multi-line placeholder
                String processedLine = line;

                // Apply non-multiline placeholders first
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    String placeholder = "{" + entry.getKey() + "}";
                    String value = entry.getValue();

                    if (!value.contains("\n")) {
                        processedLine = processedLine.replace(placeholder, value);
                    }
                }

                // Now handle multiline placeholders
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    String placeholder = "{" + entry.getKey() + "}";
                    String value = entry.getValue();

                    if (processedLine.contains(placeholder) && value.contains("\n")) {
                        // Split the placeholder value into lines
                        String[] valueLines = value.split("\n");

                        // Replace the placeholder in the first line
                        String firstLine = processedLine.replace(placeholder, valueLines[0]);
                        result.add(ColorUtil.translateHexColorCodes(firstLine));

                        // Add remaining lines with the same formatting/indentation
                        String lineStart = processedLine.substring(0, processedLine.indexOf(placeholder));
                        for (int i = 1; i < valueLines.length; i++) {
                            result.add(ColorUtil.translateHexColorCodes(lineStart + valueLines[i]));
                        }
                    }
                }
            } else {
                // Standard processing for lines without multi-line placeholders
                result.add(applyPlaceholdersAndColors(line, placeholders));
            }
        }

        return result;
    }

    //---------------------------------------------------
    //               Formatting Methods
    //---------------------------------------------------


    public String formatNumber(double number) {
        if (!activeFileTypes.contains(LanguageFileType.FORMATTING)) {
            // Return a default format if formatting file type is not active
            if (number >= 1_000_000_000_000L) {
                double value = Math.round(number / 1_000_000_000_000.0 * 10) / 10.0;
                return formatDecimal(value) + "T";
            } else if (number >= 1_000_000_000L) {
                double value = Math.round(number / 1_000_000_000.0 * 10) / 10.0;
                return formatDecimal(value) + "B";
            } else if (number >= 1_000_000L) {
                double value = Math.round(number / 1_000_000.0 * 10) / 10.0;
                return formatDecimal(value) + "M";
            } else if (number >= 1_000L) {
                double value = Math.round(number / 1_000.0 * 10) / 10.0;
                return formatDecimal(value) + "K";
            } else {
                double value = Math.round(number * 10) / 10.0;
                return formatDecimal(value);
            }
        }

        String format;
        double value;

        if (number >= 1_000_000_000_000L) {
            format = cachedDefaultLocaleData.formatting().getString("format_number.trillion", "{s}T");
            value = Math.round(number / 1_000_000_000_000.0 * 10) / 10.0;
        } else if (number >= 1_000_000_000L) {
            format = cachedDefaultLocaleData.formatting().getString("format_number.billion", "{s}B");
            value = Math.round(number / 1_000_000_000.0 * 10) / 10.0;
        } else if (number >= 1_000_000L) {
            format = cachedDefaultLocaleData.formatting().getString("format_number.million", "{s}M");
            value = Math.round(number / 1_000_000.0 * 10) / 10.0;
        } else if (number >= 1_000L) {
            format = cachedDefaultLocaleData.formatting().getString("format_number.thousand", "{s}K");
            value = Math.round(number / 1_000.0 * 10) / 10.0;
        } else {
            format = cachedDefaultLocaleData.formatting().getString("format_number.default", "{s}");
            value = Math.round(number * 10) / 10.0;
        }

        // Replace {s} with the formatted value
        return format.replace("{s}", formatDecimal(value));
    }

    private String formatDecimal(double value) {
        // Check if the value is a whole number (after our rounding)
        if (value == Math.floor(value)) {
            return String.valueOf((int)value);
        } else {
            return String.valueOf(value);
        }
    }

    public String getFormattedMobName(EntityType type) {
        if (type == null || type == EntityType.UNKNOWN) {
            return "Unknown";
        }

        // Get the internal name key of the entity type
        String mobNameKey = type.name();

        // Create a special cache key for mob names
        String cacheKey = "mob_name|" + mobNameKey;

        // Check cache first
        String cachedName = entityNameCache.get(cacheKey);
        if (cachedName != null) {
            cacheHits.incrementAndGet();
            return cachedName;
        }

        // Cache miss, generate the name
        cacheMisses.incrementAndGet();
        String result;

        // Try to get from formatting.yml first
        if (activeFileTypes.contains(LanguageFileType.FORMATTING)) {
            String formattedName = cachedDefaultLocaleData.formatting().getString("mob_names." + mobNameKey);

            if (formattedName != null) {
                result = applyPlaceholdersAndColors(formattedName, null);
                entityNameCache.put(cacheKey, result);
                return result;
            }
        }

        // Default fallback: convert SNAKE_CASE to Title Case
        result = formatEnumName(mobNameKey);

        // Cache the result
        entityNameCache.put(cacheKey, result);

        return result;
    }

    // Helper method to convert enum names like "CAVE_SPIDER" to "Cave Spider"
    public String formatEnumName(String enumName) {
        String[] words = enumName.split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(word.charAt(0))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    //---------------------------------------------------
    //                     Utilities
    //---------------------------------------------------

    public String getSmallCaps(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Generate cache key
        String cacheKey = "smallcaps|" + text;

        // Check cache first
        String cachedText = smallCapsCache.get(cacheKey);
        if (cachedText != null) {
            cacheHits.incrementAndGet();
            return cachedText;
        }

        // Cache miss, generate small caps
        cacheMisses.incrementAndGet();
        StringBuilder result = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                // Convert all alphabetic characters to small caps regardless of case
                char lowercaseChar = Character.toLowerCase(c);
                char smallCapsChar = getSmallCapsChar(lowercaseChar);
                result.append(smallCapsChar);
            } else {
                // Keep non-alphabetic characters as they are
                result.append(c);
            }
        }

        String smallCapsText = result.toString();

        // Cache the result
        smallCapsCache.put(cacheKey, smallCapsText);

        return smallCapsText;
    }

    private char getSmallCapsChar(char c) {
        return switch (c) {
            case 'a' -> 'ᴀ';
            case 'b' -> 'ʙ';
            case 'c' -> 'ᴄ';
            case 'd' -> 'ᴅ';
            case 'e' -> 'ᴇ';
            case 'f' -> 'ꜰ';
            case 'g' -> 'ɢ';
            case 'h' -> 'ʜ';
            case 'i' -> 'ɪ';
            case 'j' -> 'ᴊ';
            case 'k' -> 'ᴋ';
            case 'l' -> 'ʟ';
            case 'm' -> 'ᴍ';
            case 'n' -> 'ɴ';
            case 'o' -> 'ᴏ';
            case 'p' -> 'ᴘ';
            case 'q' -> 'ǫ';
            case 'r' -> 'ʀ';
            case 's' -> 'ꜱ';
            case 't' -> 'ᴛ';
            case 'u' -> 'ᴜ';
            case 'v' -> 'ᴠ';
            case 'w' -> 'ᴡ';
            case 'x' -> 'x';
            case 'y' -> 'ʏ';
            case 'z' -> 'ᴢ';
            default -> c;
        };
    }

    public String applyPlaceholdersAndColors(String text, Map<String, String> placeholders) {
        if (text == null) return null;

        // Create a cache key based on the text and placeholders
        String cacheKey = generateCacheKey(text, placeholders);

        // Check if we have a cached result
        String cachedResult = formattedStringCache.get(cacheKey);
        if (cachedResult != null) {
            cacheHits.incrementAndGet();
            return cachedResult;
        }

        // Process the text if not cached
        cacheMisses.incrementAndGet();
        String result = text;

        // Apply placeholders only if there are any
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        // Apply hex colors
        result = ColorUtil.translateHexColorCodes(result);

        // Cache the result for future use
        formattedStringCache.put(cacheKey, result);

        return result;
    }

    public String getColorCode(String path) {
        if (!activeFileTypes.contains(LanguageFileType.GUI)) {
            return ChatColor.WHITE.toString();
        }

        String colorStr = cachedDefaultLocaleData.gui().getString(path);
        if (colorStr == null) {
            return ChatColor.WHITE.toString();
        }

        return applyPlaceholdersAndColors(colorStr, EMPTY_PLACEHOLDERS);
    }

    public String applyOnlyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return null;

        // Create a cache key based on the text and placeholders
        String cacheKey = generateCacheKey(text, placeholders);

        // Check if we have a cached result
        String cachedResult = formattedStringCache.get(cacheKey);
        if (cachedResult != null) {
            cacheHits.incrementAndGet();
            return cachedResult;
        }

        // Process the text if not cached
        cacheMisses.incrementAndGet();
        String result = text;

        // Apply placeholders only if there are any
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        // Cache the result for future use
        formattedStringCache.put(cacheKey, result);

        return result;
    }

    //---------------------------------------------------
    //               Hologram Methods
    //---------------------------------------------------

    public String getHologramText() {
        // Try reading from per-language hologram.yml first
        YamlConfiguration hologramCfg = cachedDefaultLocaleData.hologram();
        if (hologramCfg != null && hologramCfg.contains("text")) {
            List<String> lines = hologramCfg.getStringList("text");
            if (!lines.isEmpty()) {
                return String.join("\n", lines);
            }
            String single = hologramCfg.getString("text");
            if (single != null) return single;
        }

        // Fallback: legacy hologram.text key in config.yml
        if (plugin.getConfig().contains("hologram.text")) {
            List<String> lines = plugin.getConfig().getStringList("hologram.text");
            if (!lines.isEmpty()) {
                return String.join("\n", lines);
            }
            return plugin.getConfig().getString("hologram.text");
        }

        // Default value if not defined anywhere
        return "&e%entity% Spawner &7[&f%stack_size%x&7]\n" +
                "&7XP: &a%current_exp%&7/&a%max_exp%\n" +
                "&7Items: &a%used_slots%&7/&a%max_slots%";
    }

    //---------------------------------------------------
    //                 Cache Methods
    //---------------------------------------------------
    public void clearCache() {
        formattedStringCache.clear();
        loreCache.clear();
        loreListCache.clear();
        guiItemNameCache.clear();
        guiItemLoreCache.clear();
        guiItemLoreListCache.clear();
        entityNameCache.clear();
        smallCapsCache.clear();
        materialNameCache.clear();
    }
    private String generateCacheKey(String text, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return text;
        }

        StringBuilder keyBuilder = new StringBuilder(text);

        // Sort keys for consistent cache keys
        List<String> keys = new ArrayList<>(placeholders.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            keyBuilder.append('|').append(key).append('=').append(placeholders.get(key));
        }
        return keyBuilder.toString();
    }


    /**
     * Gets cache statistics for monitoring
     * @return Map with statistics about cache usage
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("string_cache_size", formattedStringCache.size());
        stats.put("string_cache_capacity", formattedStringCache.capacity());
        stats.put("lore_cache_size", loreCache.size());
        stats.put("lore_cache_capacity", loreCache.capacity());
        stats.put("lore_list_cache_size", loreListCache.size());
        stats.put("lore_list_cache_capacity", loreListCache.capacity());
        stats.put("gui_name_cache_size", guiItemNameCache.size());
        stats.put("gui_name_cache_capacity", guiItemNameCache.capacity());
        stats.put("gui_lore_cache_size", guiItemLoreCache.size());
        stats.put("gui_lore_cache_capacity", guiItemLoreCache.capacity());
        stats.put("gui_lore_list_cache_size", guiItemLoreListCache.size());
        stats.put("gui_lore_list_cache_capacity", guiItemLoreListCache.capacity());
        stats.put("entity_name_cache_size", entityNameCache.size());
        stats.put("entity_name_cache_capacity", entityNameCache.capacity());
        stats.put("small_caps_cache_size", smallCapsCache.size());
        stats.put("small_caps_cache_capacity", smallCapsCache.capacity());
        stats.put("material_name_cache_size", materialNameCache.size());
        stats.put("material_name_cache_capacity", materialNameCache.capacity());
        stats.put("cache_hits", cacheHits.get());
        stats.put("cache_misses", cacheMisses.get());
        stats.put("hit_ratio", cacheHits.get() > 0 ?
                (double) cacheHits.get() / (cacheHits.get() + cacheMisses.get()) : 0);
        return stats;
    }
}
