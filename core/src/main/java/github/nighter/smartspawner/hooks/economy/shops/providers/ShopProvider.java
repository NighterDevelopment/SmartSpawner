package github.nighter.smartspawner.hooks.economy.shops.providers;

import org.bukkit.Material;

public interface ShopProvider {

    String getPluginName();

    boolean isAvailable();

    double getSellPrice(Material material);

    default void unregister() {}
}