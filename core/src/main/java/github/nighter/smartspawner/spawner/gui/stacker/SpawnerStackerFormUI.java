package github.nighter.smartspawner.spawner.gui.stacker;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;

public class SpawnerStackerFormUI {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final SpawnerStackerHandler stackerHandler;

    // Form cache to avoid rebuilding forms every time
    private final Map<String, CachedStackerForm> formCache = new HashMap<>();
    private static final long CACHE_EXPIRY_TIME_MS = 30000; // 30 seconds

    // Stack amounts for buttons
    private static final int[] STACK_AMOUNTS = {1, 10, 64};

    public SpawnerStackerFormUI(SmartSpawner plugin, SpawnerStackerHandler handler) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.stackerHandler = handler;
    }

    public void clearCache() {
        formCache.clear();
    }

    public void invalidateSpawnerCache(String spawnerId) {
        formCache.entrySet().removeIf(entry -> entry.getKey().startsWith(spawnerId + "|"));
    }

    public void openStackerForm(Player player, SpawnerData spawner) {
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String entityNameSmallCaps = languageManager.getSmallCaps(entityName);
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
        placeholders.put("stack_size", String.valueOf(spawner.getStackSize()));
        placeholders.put("max_stack_size", String.valueOf(spawner.getMaxStackSize()));

        String title = languageManager.getGuiTitle("bedrock.stacker_gui.title", placeholders);

        // Create cache key based on spawner state
        String cacheKey = spawner.getSpawnerId() + "|" + spawner.getStackSize() + "|" + spawner.getMaxStackSize();
        
        // Check cache first
        CachedStackerForm cachedForm = formCache.get(cacheKey);
        if (cachedForm != null && !cachedForm.isExpired()) {
            FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(cachedForm.form);
            return;
        }

        List<StackerButtonInfo> buttons = new ArrayList<>();
        
        // Add decrease buttons
        for (int amount : STACK_AMOUNTS) {
            String buttonText = languageManager.getGuiItemName("bedrock.stacker_gui.button_decrease", 
                createAmountPlaceholders(spawner, amount));
            buttons.add(new StackerButtonInfo("decrease", amount, buttonText, 
                "https://i.imgur.com/kKBt9pj.png")); // Red minus icon
        }
        
        // Add info button
        String infoText = createStackerInfoText(spawner, placeholders);
        buttons.add(new StackerButtonInfo("info", 0, infoText, 
            "https://i.imgur.com/8JlZJGT.png")); // Info icon
        
        // Add increase buttons
        for (int amount : STACK_AMOUNTS) {
            String buttonText = languageManager.getGuiItemName("bedrock.stacker_gui.button_increase", 
                createAmountPlaceholders(spawner, amount));
            buttons.add(new StackerButtonInfo("increase", amount, buttonText, 
                "https://i.imgur.com/L9zVBPL.png")); // Green plus icon
        }

        // Add back button
        String backText = languageManager.getGuiItemName("bedrock.stacker_gui.button_back", placeholders);
        buttons.add(new StackerButtonInfo("back", 0, backText, 
            "https://i.imgur.com/oTfHJ8P.png")); // Back arrow icon

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title(title);

        for (StackerButtonInfo buttonInfo : buttons) {
            formBuilder.button(buttonInfo.text, FormImage.Type.URL, buttonInfo.imageUrl);
        }

        SimpleForm form = formBuilder
                .closedOrInvalidResultHandler(() -> {
                    // Reopen main menu when form is closed
                    Scheduler.runTask(() -> {
                        if (plugin.getSpawnerMenuFormUI() != null) {
                            plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
                        }
                    });
                })
                .validResultHandler(response -> {
                    int buttonId = response.clickedButtonId();
                    if (buttonId < buttons.size()) {
                        StackerButtonInfo buttonInfo = buttons.get(buttonId);
                        Scheduler.runTask(() -> {
                            handleButtonClick(player, spawner, buttonInfo);
                        });
                    }
                })
                .build();
        
        // Cache the form
        formCache.put(cacheKey, new CachedStackerForm(form));
        
        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(form);
    }

    private Map<String, String> createAmountPlaceholders(SpawnerData spawner, int amount) {
        Map<String, String> placeholders = new HashMap<>();
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityName));
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("plural", amount > 1 ? "s" : "");
        placeholders.put("stack_size", String.valueOf(spawner.getStackSize()));
        placeholders.put("max_stack_size", String.valueOf(spawner.getMaxStackSize()));
        return placeholders;
    }

    private String createStackerInfoText(SpawnerData spawner, Map<String, String> placeholders) {
        // Get the info lines from config
        List<String> infoLines = languageManager.getGuiItemLoreAsList("bedrock.stacker_gui.info_text", placeholders);
        
        if (infoLines == null || infoLines.isEmpty()) {
            // Fallback to basic info
            return "§fStack: §9" + spawner.getStackSize() + " §7/ §9" + spawner.getMaxStackSize();
        }
        
        // Convert to Bedrock-compatible color codes and join with newlines
        StringBuilder content = new StringBuilder();
        for (String line : infoLines) {
            String bedrockLine = convertToBedrockColors(line);
            content.append(bedrockLine).append("\n");
        }
        
        // Remove trailing newline
        if (content.length() > 0) {
            content.setLength(content.length() - 1);
        }
        
        return content.toString();
    }

    private String convertToBedrockColors(String text) {
        if (text == null) return "";
        
        String result = text;
        
        // Map common hex colors to Bedrock equivalents
        result = mapHexToBedrockColors(result);
        
        // Convert & color codes to § for Bedrock
        result = result.replace("&0", "§0").replace("&1", "§1").replace("&2", "§2").replace("&3", "§3")
                .replace("&4", "§4").replace("&5", "§5").replace("&6", "§6").replace("&7", "§7")
                .replace("&8", "§8").replace("&9", "§9").replace("&a", "§a").replace("&b", "§b")
                .replace("&c", "§c").replace("&d", "§d").replace("&e", "§e").replace("&f", "§f")
                .replace("&g", "§g");
        
        return result;
    }

    private String mapHexToBedrockColors(String result) {
        Map<String, String> colorMap = new HashMap<>();
        
        // Grays and blacks
        colorMap.put("545454", "§8");
        colorMap.put("bdc3c7", "§7");
        colorMap.put("ecf0f1", "§f");
        colorMap.put("f8f8ff", "§f");
        
        // Blues
        colorMap.put("3498db", "§9");
        
        // Greens
        colorMap.put("2ecc71", "§a");
        colorMap.put("37eb9a", "§a");
        colorMap.put("2cc483", "§a");
        colorMap.put("48e89b", "§a");
        colorMap.put("00F986", "§a");
        
        // Reds
        colorMap.put("e67e22", "§6");
        colorMap.put("ff5252", "§c");
        colorMap.put("e63939", "§4");
        colorMap.put("ff7070", "§c");
        
        // Purples
        colorMap.put("d8c5ff", "§d");
        colorMap.put("7b68ee", "§5");
        
        for (Map.Entry<String, String> entry : colorMap.entrySet()) {
            result = result.replace("&#" + entry.getKey(), entry.getValue());
            result = result.replace("&#" + entry.getKey().toLowerCase(), entry.getValue());
        }
        
        return result;
    }

    private void handleButtonClick(Player player, SpawnerData spawner, StackerButtonInfo buttonInfo) {
        switch (buttonInfo.action) {
            case "decrease":
                // Validate amount to prevent exploits
                if (buttonInfo.amount <= 0 || buttonInfo.amount > 64) {
                    plugin.getLogger().warning("Invalid decrease amount from Bedrock player " + player.getName() + ": " + buttonInfo.amount);
                    return;
                }
                stackerHandler.handleStackDecrease(player, spawner, buttonInfo.amount);
                // Reopen the form to show updated values
                Scheduler.runTaskLater(() -> openStackerForm(player, spawner), 2L);
                break;
                
            case "increase":
                // Validate amount to prevent exploits
                if (buttonInfo.amount <= 0 || buttonInfo.amount > 64) {
                    plugin.getLogger().warning("Invalid increase amount from Bedrock player " + player.getName() + ": " + buttonInfo.amount);
                    return;
                }
                stackerHandler.handleStackIncrease(player, spawner, buttonInfo.amount);
                // Reopen the form to show updated values
                Scheduler.runTaskLater(() -> openStackerForm(player, spawner), 2L);
                break;
                
            case "info":
                // Just reopen the form (refresh)
                openStackerForm(player, spawner);
                break;
                
            case "back":
                // Return to main menu
                if (plugin.getSpawnerMenuFormUI() != null) {
                    plugin.getSpawnerMenuFormUI().openSpawnerForm(player, spawner);
                }
                break;
                
            default:
                plugin.getLogger().warning("Unknown action in StackerFormUI: " + buttonInfo.action);
                break;
        }
    }

    private static class StackerButtonInfo {
        final String action;
        final int amount;
        final String text;
        final String imageUrl;

        StackerButtonInfo(String action, int amount, String text, String imageUrl) {
            this.action = action;
            this.amount = amount;
            this.text = text;
            this.imageUrl = imageUrl;
        }
    }

    private static class CachedStackerForm {
        final SimpleForm form;
        final long timestamp;

        CachedStackerForm(SimpleForm form) {
            this.form = form;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME_MS;
        }
    }
}
