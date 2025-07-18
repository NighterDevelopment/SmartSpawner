package github.nighter.smartspawner.hooks.economy.shops.providers.shopguiplus;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.event.ShopGUIPlusPostEnableEvent;
import net.brcdev.shopgui.event.ShopsPostLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

@RequiredArgsConstructor
public class ShopGuiPlusProvider implements ShopProvider {
    private final SmartSpawner plugin;

    @Override
    public String getPluginName() {
        return "ShopGUIPlus";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin shopGuiPlugin = Bukkit.getPluginManager().getPlugin("ShopGUIPlus");
            if (shopGuiPlugin != null && shopGuiPlugin.isEnabled()) {
                Class.forName("net.brcdev.shopgui.ShopGuiPlusApi");
                // return ShopGuiPlusApi.getPlugin().getShopManager().areShopsLoaded();
                return true;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            plugin.debug("ShopGUIPlus API not found: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Error initializing ShopGUIPlus integration: " + e.getMessage());
        }
        return false;
    }

    @Override
    public double getSellPrice(Material material) {
        try {
            ItemStack item = new ItemStack(material);
            double sellPrice = ShopGuiPlusApi.getItemStackPriceSell(item);
            return sellPrice > 0 ? sellPrice : 0.0;

        } catch (Exception e) {
            plugin.debug("Error getting sell price for " + material + " from ShopGUIPlus: " + e.getMessage());
            return 0.0;
        }
    }

    @Override
    public void unregister() {
        ShopGUIPlusPostEnableEvent.getHandlerList().unregister(plugin);
        ShopsPostLoadEvent.getHandlerList().unregister(plugin);
    }
}