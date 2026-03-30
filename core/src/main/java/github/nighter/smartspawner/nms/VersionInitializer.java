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
 * version-dependent utilities for tooltip hiding and other version-specific features.
 */
public class VersionInitializer {
    private final SmartSpawner plugin;
    private final String serverVersion;
    private static boolean supportsDataComponentAPI = false;
    private static Class<?> dataComponentTypeKeysClass = null;
    private static Class<?> dataComponentTypesClass = null;
    private static Class<?> tooltipDisplayClass = null;

    // Cached objects for DataComponent tooltip path (built once at init, reused on every call)
    private static Object cachedTooltipDisplayType = null;
    private static Object cachedTooltipDisplay = null;
    private static Method cachedSetDataMethod = null;

    // Cached methods for custom-model-data checks on 1.21.5+
    private static Method cachedHasCustomModelDataComponentMethod = null;
    private static Method cachedGetCustomModelDataComponentMethod = null;

    // Cached ItemFlag for the legacy tooltip-hide fallback path
    private static ItemFlag cachedHideAdditionalTooltip = null;
    private static boolean tooltipFlagAvailable = false;

    public VersionInitializer(SmartSpawner plugin) {
        this.plugin = plugin;
        this.serverVersion = Bukkit.getServer().getBukkitVersion();
    }

    /**
     * Initialize version-specific components.
     * Detects if the server supports DataComponentTypeKeys (1.21.5+) or needs fallback.
     */
    public void initialize() {
        plugin.debug("Server version: " + serverVersion);
        detectDataComponentAPISupport();
    }

    /**
     * Detect if the server supports the DataComponent API (Paper 1.21.5+).
     * Also pre-builds all cached reflection objects so that runtime hot-paths
     * (hideTooltip, hasCustomModelData, etc.) perform zero Class.forName / getMethod calls.
     */
    private void detectDataComponentAPISupport() {
        try {
            // Load the three marker classes that signal DataComponent API presence
            dataComponentTypeKeysClass = Class.forName("io.papermc.paper.registry.keys.DataComponentTypeKeys");
            dataComponentTypesClass    = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            tooltipDisplayClass        = Class.forName("io.papermc.paper.datacomponent.item.TooltipDisplay");

            // ── Pre-build the reusable TooltipDisplay value ──────────────────────────
            Class<?> dataComponentTypeClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
            Class<?> registryAccessClass    = Class.forName("io.papermc.paper.registry.RegistryAccess");
            Class<?> registryKeyClass       = Class.forName("io.papermc.paper.registry.RegistryKey");

            Object tooltipDisplayType  = dataComponentTypesClass.getField("TOOLTIP_DISPLAY").get(null);
            Object registryAccess      = registryAccessClass.getMethod("registryAccess").invoke(null);
            Object dataComponentTypeKey = registryKeyClass.getField("DATA_COMPONENT_TYPE").get(null);
            Object registry            = registryAccess.getClass()
                    .getMethod("getRegistry", registryKeyClass)
                    .invoke(registryAccess, dataComponentTypeKey);
            Object blockEntityDataKey  = dataComponentTypeKeysClass.getField("BLOCK_ENTITY_DATA").get(null);
            Object blockEntityData     = registry.getClass()
                    .getMethod("get", Object.class)
                    .invoke(registry, blockEntityDataKey);

            Object builder = tooltipDisplayClass.getMethod("tooltipDisplay").invoke(null);
            builder = builder.getClass()
                    .getMethod("hiddenComponents", Set.class)
                    .invoke(builder, Set.of(blockEntityData));
            Object tooltipDisplay = builder.getClass().getMethod("build").invoke(builder);

            // Cache the pre-built objects and the ItemStack#setData method
            cachedTooltipDisplayType = tooltipDisplayType;
            cachedTooltipDisplay     = tooltipDisplay;
            cachedSetDataMethod      = ItemStack.class.getMethod("setData", dataComponentTypeClass, Object.class);

            // Cache ItemMeta convenience methods for custom model data
            cachedHasCustomModelDataComponentMethod = ItemMeta.class.getMethod("hasCustomModelDataComponent");
            cachedGetCustomModelDataComponentMethod = ItemMeta.class.getMethod("getCustomModelDataComponent");

            supportsDataComponentAPI = true;
            plugin.getLogger().info("Server supports DataComponent API (Paper 1.21.5+)");
        } catch (Exception e) {
            supportsDataComponentAPI = false;
            plugin.getLogger().info("Server does not support DataComponent API, using fallback methods (Paper < 1.21.5)");
        }

        // Always cache the legacy ItemFlag regardless of which path is active,
        // as it also serves as the DataComponent-path fallback.
        try {
            cachedHideAdditionalTooltip = ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP");
            tooltipFlagAvailable = true;
        } catch (IllegalArgumentException e) {
            tooltipFlagAvailable = false;
        }
    }

    /**
     * Check if the server supports the DataComponent API
     * @return true if DataComponentTypeKeys is available, false otherwise
     */
    public static boolean supportsDataComponentAPI() {
        return supportsDataComponentAPI;
    }

    /**
     * Hide tooltip for spawner items in a version-independent way.
     * Uses DataComponent API for 1.21.5+ or ItemFlag.HIDE_ADDITIONAL_TOOLTIP for older versions.
     * @param item The item to hide tooltips for
     */
    public static void hideTooltip(ItemStack item) {
        if (item == null) return;

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
     * Hide tooltip using the cached DataComponent API objects (Paper 1.21.5+).
     * All reflection lookups were performed once at startup; this method only
     * invokes the pre-resolved Method with pre-built arguments.
     */
    private static void hideTooltipUsingDataComponent(ItemStack item) {
        try {
            cachedSetDataMethod.invoke(item, cachedTooltipDisplayType, cachedTooltipDisplay);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hide tooltip using DataComponent API", e);
        }
    }

    /**
     * Check if an ItemMeta has custom model data in a version-independent way.
     * Uses hasCustomModelDataComponent() on 1.21.5+, hasCustomModelData() on older versions.
     * @param meta The ItemMeta to check
     * @return true if custom model data is present
     */
    public static boolean hasCustomModelData(ItemMeta meta) {
        if (meta == null) return false;
        if (supportsDataComponentAPI && cachedHasCustomModelDataComponentMethod != null) {
            try {
                return (boolean) cachedHasCustomModelDataComponentMethod.invoke(meta);
            } catch (Exception e) {
                return meta.hasCustomModelData();
            }
        }
        return meta.hasCustomModelData();
    }

    /**
     * Get a string representation of an item's custom model data in a version-independent way.
     * Should only be called when hasCustomModelData() returns true.
     * @param meta The ItemMeta to read
     * @return String representation of custom model data, or empty string if unavailable
     */
    public static String getCustomModelDataString(ItemMeta meta) {
        if (meta == null) return "";
        if (supportsDataComponentAPI && cachedGetCustomModelDataComponentMethod != null) {
            try {
                Object component = cachedGetCustomModelDataComponentMethod.invoke(meta);
                return component != null ? component.toString() : "";
            } catch (Exception e) {
                return meta.hasCustomModelData() ? String.valueOf(meta.getCustomModelData()) : "";
            }
        }
        return meta.hasCustomModelData() ? String.valueOf(meta.getCustomModelData()) : "";
    }

    /**
     * Hide tooltip using the cached ItemFlag (Paper &lt; 1.21.5 fallback).
     * The flag valueOf() lookup is performed once at startup and reused here.
     */
    private static void hideTooltipUsingItemFlag(ItemStack item) {
        if (!tooltipFlagAvailable) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(cachedHideAdditionalTooltip);
            item.setItemMeta(meta);
        }
    }
}