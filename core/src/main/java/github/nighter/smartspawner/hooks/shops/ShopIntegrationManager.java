package github.nighter.smartspawner.hooks.shops;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.shops.api.economyshopgui.EconomyShopGUI;
import github.nighter.smartspawner.hooks.shops.api.shopguiplus.ShopGuiPlus;
import github.nighter.smartspawner.hooks.economies.CustomSellPricesIntegration;
import github.nighter.smartspawner.hooks.shops.api.zshop.ZShop;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class ShopIntegrationManager {
    private final SmartSpawner plugin;
    private IShopIntegration shopIntegration;
    private boolean hasShopIntegration = false;
    @Getter
    private boolean isShopGUIPlusEnabled = false;
    @Getter
    private boolean isUsingCustomPrices = false;
    private final Map<String, Function<SmartSpawner, IShopIntegration>> shopIntegrations = new LinkedHashMap<>();

    public ShopIntegrationManager(SmartSpawner plugin) {
        this.plugin = plugin;
        registerShopIntegrations();
    }

    private void registerShopIntegrations() {
        shopIntegrations.put("economyshopgui-premium", EconomyShopGUI::new);
        shopIntegrations.put("economyshopgui", EconomyShopGUI::new);
        shopIntegrations.put("shopguiplus", ShopGuiPlus::new);
        shopIntegrations.put("zshop", ZShop::new);
        shopIntegrations.put("custom_sell_prices", CustomSellPricesIntegration::new);
    }

    public void initialize() {
        // Check if custom sell prices are enabled - if so, skip shop integration
        if (plugin.getConfig().getBoolean("custom_sell_prices.enabled", false)) {
            setupCustomSellPricesIntegration();
            return;
        }

        if (!plugin.getConfig().getBoolean("shop_integration.enabled", true)) {
            plugin.getLogger().info("Shop integration is disabled by configuration");
            return;
        }

        String configuredShopType = plugin.getConfig().getString("shop_integration.type", "auto").toLowerCase();

        if ("auto".equals(configuredShopType)) {
            autoDetectAndSetupShop();
        } else {
            setupSpecificShop(configuredShopType);
        }
    }

    private void setupCustomSellPricesIntegration() {
        Function<SmartSpawner, IShopIntegration> integrationCreator = shopIntegrations.get("custom_sell_prices");
        if (integrationCreator != null) {
            try {
                shopIntegration = integrationCreator.apply(plugin);
                hasShopIntegration = shopIntegration.isEnabled();
                if (hasShopIntegration) {
                    isUsingCustomPrices = true;
                    plugin.getLogger().info("Custom sell prices enabled successfully!");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to setup custom sell prices: " + e.getMessage());
                hasShopIntegration = false;
                isUsingCustomPrices = false;
            }
        }
    }

    private void setupSpecificShop(String shopType) {
        Function<SmartSpawner, IShopIntegration> integrationCreator = shopIntegrations.get(shopType);

        if (integrationCreator != null) {
            String bukkitPluginName = getBukkitPluginName(shopType);

            plugin.getLogger().info("Checking for " + bukkitPluginName + "...");
            Plugin shopPlugin = Bukkit.getPluginManager().getPlugin(bukkitPluginName);

            if (shopPlugin != null && shopPlugin.isEnabled()) {
                setupShopIntegration(bukkitPluginName, integrationCreator);
            } else {
                plugin.getLogger().warning(bukkitPluginName + " not found - integration disabled");
            }
        } else {
            plugin.getLogger().warning("No integration found for shop type: " + shopType);
        }
    }

    private String getBukkitPluginName(String shopType) {
        return switch (shopType.toLowerCase()) {
            case "economyshopgui" -> "EconomyShopGUI";
            case "economyshopgui-premium" -> "EconomyShopGUI-Premium";
            case "shopguiplus" -> "ShopGUIPlus";
            case "zshop" -> "zShop";
            default -> shopType;
        };
    }

    private void autoDetectAndSetupShop() {
        plugin.getLogger().info("Auto-detecting available shop plugins...");

        String[] pluginNames = {
                "EconomyShopGUI-Premium",
                "EconomyShopGUI",
                "ShopGUIPlus",
                "zShop"
        };

        for (String bukkitPluginName : pluginNames) {
            Plugin shopPlugin = Bukkit.getPluginManager().getPlugin(bukkitPluginName);

            if (shopPlugin != null && shopPlugin.isEnabled()) {
                String lowercasePluginName = bukkitPluginName.toLowerCase();
                // Handle the special case for EconomyShopGUI-Premium
                if (lowercasePluginName.equals("economyshopgui-premium")) {
                    lowercasePluginName = "economyshopgui-premium";
                }

                Function<SmartSpawner, IShopIntegration> integrationCreator = shopIntegrations.get(lowercasePluginName);

                if (integrationCreator != null) {
                    try {
                        setupShopIntegration(bukkitPluginName, integrationCreator);
                        return;
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to setup " + bukkitPluginName + " integration: " + e.getMessage());
                    }
                }
            }
        }

        plugin.getLogger().warning("No compatible shop plugins were found during auto-detection.");
    }

    private void setupShopIntegration(String pluginName, Function<SmartSpawner, IShopIntegration> integrationCreator) {
        try {
            shopIntegration = integrationCreator.apply(plugin);
            hasShopIntegration = true;
            isShopGUIPlusEnabled = pluginName.equals("ShopGUIPlus");
            isUsingCustomPrices = false;
            plugin.getLogger().info(pluginName + " integration enabled successfully!");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to setup " + pluginName + " integration: " + e.getMessage());
            hasShopIntegration = false;
            isShopGUIPlusEnabled = false;
            isUsingCustomPrices = false;
        }
    }

    public IShopIntegration getShopIntegration() {
        if (shopIntegration != null && shopIntegration.isEnabled()) {
            return shopIntegration;
        }
        return null;
    }

    public boolean hasShopIntegration() {
        return hasShopIntegration;
    }

    public void reload() {
        hasShopIntegration = false;
        isShopGUIPlusEnabled = false;
        isUsingCustomPrices = false;
        shopIntegration = null;
        initialize();
    }
}