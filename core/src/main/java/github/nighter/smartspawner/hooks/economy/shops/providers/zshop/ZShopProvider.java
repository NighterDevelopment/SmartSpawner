package github.nighter.smartspawner.hooks.economy.shops.providers.zshop;

import fr.maxlego08.shop.api.ShopManager;
import fr.maxlego08.shop.api.buttons.ItemButton;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.logging.Level;

@RequiredArgsConstructor
public class ZShopProvider implements ShopProvider {
    private final SmartSpawner plugin;
    private ShopManager shopManager;

    @Override
    public String getPluginName() {
        return "zShop";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin zShopPlugin = Bukkit.getPluginManager().getPlugin("zShop");
            if (zShopPlugin != null && zShopPlugin.isEnabled()) {
                // Check if the zShop API classes are available
                Class.forName("fr.maxlego08.shop.api.ShopManager");
                Class.forName("fr.maxlego08.shop.api.buttons.ItemButton");

                // Try to get the ShopManager service
                ShopManager manager = getShopManager();
                return manager != null;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // zShop API not present; integration unavailable.
        } catch (Exception e) {
            plugin.getLogger().warning("Error initializing zShop integration: " + e.getMessage());
        }
        return false;
    }

    @Override
    public double getSellPrice(Material material) {
        try {
            Optional<ItemButton> itemButtonOpt = getItemButton(material);
            if (itemButtonOpt.isEmpty()) {
                return 0.0;
            }

            ItemButton itemButton = itemButtonOpt.get();
            double sellPrice = itemButton.getSellPrice();
            return sellPrice > 0 ? sellPrice : 0.0;

        } catch (Exception e) {
            return 0.0;
        }
    }

    private ShopManager getShopManager() {
        if (this.shopManager != null) {
            return this.shopManager;
        }

        try {
            this.shopManager = plugin.getServer().getServicesManager()
                    .getRegistration(ShopManager.class)
                    .getProvider();
            return this.shopManager;
        } catch (Exception e) {
            return null;
        }
    }

    private Optional<ItemButton> getItemButton(Material material) {
        try {
            ShopManager manager = getShopManager();
            if (manager == null) {
                return Optional.empty();
            }
            return manager.getItemButton(material);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting item button from zShop", e);
            return Optional.empty();
        }
    }
}
