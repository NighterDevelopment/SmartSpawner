package github.nighter.smartspawner.hooks.hologram;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Bukkit;

/**
 * Integration hook for HologramLib plugin.
 * Provides hologram management functionality using HologramLib API.
 */
public class HologramLibHook {
    private final SmartSpawner plugin;
    private boolean enabled;
    private Object hologramManager;

    public HologramLibHook(SmartSpawner plugin) {
        this.plugin = plugin;
        this.enabled = false;
    }

    /**
     * Initialize the HologramLib integration
     * @return true if successfully initialized, false otherwise
     */
    public boolean initialize() {
        if (Bukkit.getPluginManager().getPlugin("HologramLib") == null) {
            plugin.getLogger().info("HologramLib not found, using fallback hologram system");
            return false;
        }

        try {
            // Try to get HologramLib manager
            Class<?> hologramLibClass = Class.forName("me.hsgamer.hologramlib.spigot.HologramLib");
            Object hologramLibInstance = hologramLibClass.getMethod("getManager").invoke(null);
            
            if (hologramLibInstance == null) {
                plugin.getLogger().warning("Failed to initialize HologramLib manager");
                return false;
            }

            this.hologramManager = hologramLibInstance;
            this.enabled = true;
            plugin.getLogger().info("Successfully hooked into HologramLib!");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into HologramLib: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if HologramLib integration is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the HologramLib manager instance
     * @return the hologram manager or null if not initialized
     */
    public Object getHologramManager() {
        return hologramManager;
    }
}
