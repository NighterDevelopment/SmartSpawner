package github.nighter.smartspawner.nms;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * VersionInitializer handles version-specific initialization and provides
 * version-dependent utilities for tooltip hiding and other version-specific
 * features.
 */
public class VersionInitializer {
    private final SmartSpawner plugin;
    private final String serverVersion;
    private static boolean supportsDataComponentAPI = false;
    private static Class<?> dataComponentTypeKeysClass = null;
    private static Class<?> dataComponentTypesClass = null;
    private static Class<?> tooltipDisplayClass = null;

    // Pre-built TooltipDisplay and cached reflection for hideTooltip (avoid per-call Class.forName + getMethod)
    private static volatile Object cachedTooltipDisplay = null;
    private static volatile Object cachedTooltipDisplayType = null;
    private static volatile Method cachedSetDataMethod = null;
    private static Class<?> cachedDataComponentTypeClass = null;

    // Cached reflection for ItemMeta methods (lazy-initialized on first use)
    private static volatile Method hasCustomModelDataComponentMethod = null;
    private static volatile Method getCustomModelDataComponentMethod = null;

    public VersionInitializer(SmartSpawner plugin) {
        this.plugin = plugin;
        this.serverVersion = Bukkit.getServer().getBukkitVersion();
    }

    /**
     * Initialize version-specific components.
     * Detects if the server supports DataComponentTypeKeys (1.21.5+) or needs
     * fallback.
     */
    public void initialize() {
        plugin.debug("Server version: " + serverVersion);
        detectDataComponentAPISupport();
    }

    /**
     * Detect if the server supports the DataComponent API (Paper 1.21.5+).
     * Class detection and pre-building are intentionally separated: a prebuild
     * failure (e.g. registry API change in a newer Paper build) must not mask
     * the fact that the API classes are present.
     */
    private void detectDataComponentAPISupport() {
        // Step 1 – class existence check only
        try {
            dataComponentTypeKeysClass = Class.forName("io.papermc.paper.registry.keys.DataComponentTypeKeys");
            dataComponentTypesClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            tooltipDisplayClass = Class.forName("io.papermc.paper.datacomponent.item.TooltipDisplay");
            supportsDataComponentAPI = true;
            plugin.getLogger().info("Server supports DataComponent API (Paper 1.21.5+)");
        } catch (Exception e) {
            supportsDataComponentAPI = false;
            plugin.getLogger()
                    .info("Server does not support DataComponent API, using fallback methods (Paper < 1.21.5)");
            return;
        }

        // Step 2 – pre-build the TooltipDisplay cache (best-effort; failure is non-fatal)
        try {
            prebuildTooltipDisplay();
        } catch (Exception e) {
            plugin.debug("Could not pre-build TooltipDisplay cache, "
                    + "will build lazily on first use: " + e.getMessage());
        }
    }

    /**
     * Pre-build the TooltipDisplay object and cache all reflection members used
     * by hideTooltip/hasCustomModelData to avoid repeated Class.forName() and
     * getMethod() calls on every item operation.
     */
    private void prebuildTooltipDisplay() throws Exception {
        cachedDataComponentTypeClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
        Class<?> registryAccessClass = Class.forName("io.papermc.paper.registry.RegistryAccess");
        Class<?> registryKeyClass = Class.forName("io.papermc.paper.registry.RegistryKey");

        cachedTooltipDisplayType = dataComponentTypesClass.getField("TOOLTIP_DISPLAY").get(null);

        Object registryAccess = registryAccessClass.getMethod("registryAccess").invoke(null);
        Object dataComponentTypeKey = registryKeyClass.getField("DATA_COMPONENT_TYPE").get(null);
        Object registry = registryAccess.getClass().getMethod("getRegistry", registryKeyClass)
                .invoke(registryAccess, dataComponentTypeKey);
        Object blockEntityDataKey = dataComponentTypeKeysClass.getField("BLOCK_ENTITY_DATA").get(null);
        Object blockEntityDataComponent = registry.getClass().getMethod("get", Object.class)
                .invoke(registry, blockEntityDataKey);

        Set<Object> hiddenComponents = Set.of(blockEntityDataComponent);
        Object builder = tooltipDisplayClass.getMethod("tooltipDisplay").invoke(null);
        builder = builder.getClass().getMethod("hiddenComponents", Set.class).invoke(builder, hiddenComponents);
        cachedTooltipDisplay = builder.getClass().getMethod("build").invoke(builder);
    }

    /**
     * Check if the server supports the DataComponent API
     * 
     * @return true if DataComponentTypeKeys is available, false otherwise
     */
    public static boolean supportsDataComponentAPI() {
        return supportsDataComponentAPI;
    }

    /**
     * Hide tooltip for spawner items in a version-independent way.
     * Uses DataComponent API for 1.21.5+ or ItemFlag.HIDE_ADDITIONAL_TOOLTIP for
     * older versions.
     * 
     * @param item The item to hide tooltips for
     */
    public static void hideTooltip(ItemStack item) {
        if (item == null)
            return;

        if (supportsDataComponentAPI) {
            // Use DataComponent API for 1.21.5+
            try {
                hideTooltipUsingDataComponent(item);
            } catch (Exception e) {
                // Fallback if something goes wrong
                hideTooltipUsingItemFlag(item);
            }
        } else {
            // Use ItemFlag for older versions
            hideTooltipUsingItemFlag(item);
        }
    }

    /**
     * Hide tooltip using DataComponent API (Paper 1.21.5+).
     * Uses pre-built cachedTooltipDisplay when available; falls back to lazy
     * init if the pre-build was skipped (e.g. registry not ready at startup).
     */
    private static void hideTooltipUsingDataComponent(ItemStack item) {
        try {
            // Lazy-build cache if prebuildTooltipDisplay() was skipped at startup
            if (cachedTooltipDisplay == null) {
                synchronized (VersionInitializer.class) {
                    if (cachedTooltipDisplay == null) {
                        Class<?> registryAccessClass = Class.forName("io.papermc.paper.registry.RegistryAccess");
                        Class<?> registryKeyClass = Class.forName("io.papermc.paper.registry.RegistryKey");
                        Object tooltipDisplayType = dataComponentTypesClass.getField("TOOLTIP_DISPLAY").get(null);
                        Object registryAccess = registryAccessClass.getMethod("registryAccess").invoke(null);
                        Object dataComponentTypeKey = registryKeyClass.getField("DATA_COMPONENT_TYPE").get(null);
                        Object registry = registryAccess.getClass().getMethod("getRegistry", registryKeyClass)
                                .invoke(registryAccess, dataComponentTypeKey);
                        Object blockEntityDataKey = dataComponentTypeKeysClass.getField("BLOCK_ENTITY_DATA").get(null);
                        Object blockEntityDataComponent = registry.getClass().getMethod("get", Object.class)
                                .invoke(registry, blockEntityDataKey);
                        Set<Object> hidden = Set.of(blockEntityDataComponent);
                        Object builder = tooltipDisplayClass.getMethod("tooltipDisplay").invoke(null);
                        builder = builder.getClass().getMethod("hiddenComponents", Set.class).invoke(builder, hidden);
                        cachedTooltipDisplay = builder.getClass().getMethod("build").invoke(builder);
                        cachedTooltipDisplayType = tooltipDisplayType;
                        cachedDataComponentTypeClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
                    }
                }
            }
            if (cachedSetDataMethod == null) {
                cachedSetDataMethod = item.getClass().getMethod("setData", cachedDataComponentTypeClass, Object.class);
            }
            cachedSetDataMethod.invoke(item, cachedTooltipDisplayType, cachedTooltipDisplay);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hide tooltip using DataComponent API", e);
        }
    }

    /**
     * Check if an ItemMeta has custom model data in a version-independent way.
     * Uses hasCustomModelDataComponent() on 1.21.5+, hasCustomModelData() on older
     * versions.
     * 
     * @param meta The ItemMeta to check
     * @return true if custom model data is present
     */
    public static boolean hasCustomModelData(ItemMeta meta) {
        if (meta == null)
            return false;
        if (supportsDataComponentAPI) {
            try {
                if (hasCustomModelDataComponentMethod == null) {
                    hasCustomModelDataComponentMethod = meta.getClass().getMethod("hasCustomModelDataComponent");
                }
                return (boolean) hasCustomModelDataComponentMethod.invoke(meta);
            } catch (Exception e) {
                return meta.hasCustomModelData();
            }
        } else {
            return meta.hasCustomModelData();
        }
    }

    /**
     * Get a string representation of an item's custom model data in a
     * version-independent way.
     * Should only be called when hasCustomModelData() returns true.
     * 
     * @param meta The ItemMeta to read
     * @return String representation of custom model data, or empty string if
     *         unavailable
     */
    public static String getCustomModelDataString(ItemMeta meta) {
        if (meta == null)
            return "";
        if (supportsDataComponentAPI) {
            try {
                if (getCustomModelDataComponentMethod == null) {
                    getCustomModelDataComponentMethod = meta.getClass().getMethod("getCustomModelDataComponent");
                }
                Object component = getCustomModelDataComponentMethod.invoke(meta);
                return component != null ? component.toString() : "";
            } catch (Exception e) {
                return meta.hasCustomModelData() ? String.valueOf(meta.getCustomModelData()) : "";
            }
        } else {
            return meta.hasCustomModelData() ? String.valueOf(meta.getCustomModelData()) : "";
        }
    }

    /**
     * Hide tooltip using ItemFlag (Paper < 1.21.5)
     */
    private static void hideTooltipUsingItemFlag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                // Try to get HIDE_ADDITIONAL_TOOLTIP flag
                ItemFlag hideAdditionalTooltip = ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP");
                meta.addItemFlags(hideAdditionalTooltip);
                item.setItemMeta(meta);
            } catch (IllegalArgumentException e) {
                // HIDE_ADDITIONAL_TOOLTIP doesn't exist in this version, do nothing
                // This is expected for very old versions
            }
        }
    }
}