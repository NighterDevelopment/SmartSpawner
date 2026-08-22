package github.nighter.smartspawner.hooks.economy.shops.providers.economyshopgui;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.objects.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

@RequiredArgsConstructor
public class EconomyShopGUIProvider implements ShopProvider {
    private final SmartSpawner plugin;
    private static final String[] PLUGIN_NAMES = {"EconomyShopGUI", "EconomyShopGUI-Premium"};

    @Override
    public String getPluginName() {
        if (plugin.getServer().getPluginManager().getPlugin("EconomyShopGUI-Premium") != null) {
            return "EconomyShopGUI-Premium";
        }
        return "EconomyShopGUI";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin economyShopGUI = null;
            for (String pluginName : PLUGIN_NAMES) {
                economyShopGUI = Bukkit.getPluginManager().getPlugin(pluginName);
                if (economyShopGUI != null) {
                    break;
                }
            }

            if (economyShopGUI != null) {
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error initializing EconomyShopGUI integration: " + e.getMessage());
        }
        return false;
    }

    @Override
    public double getSellPrice(Material material) {
        try {
            ItemStack item = new ItemStack(material);
            ShopItem shopItem = EconomyShopGUIHook.getShopItem(item);

            if (shopItem == null) {
                return 0.0;
            }

            Double sellPrice = EconomyShopGUIHook.getItemSellPrice(shopItem, item);
            return sellPrice != null ? sellPrice : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}