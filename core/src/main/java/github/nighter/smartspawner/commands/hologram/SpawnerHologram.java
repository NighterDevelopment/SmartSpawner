package github.nighter.smartspawner.commands.hologram;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.ColorUtil;
import github.nighter.smartspawner.language.LanguageManager;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SpawnerHologram {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    
    // TextDisplay fallback (used when HologramLib is not available)
    private final AtomicReference<TextDisplay> textDisplay = new AtomicReference<>(null);
    
    // HologramLib hologram (used when HologramLib is available)
    private Object hologramLibHologram;
    
    private final Location spawnerLocation;
    private int stackSize;
    private EntityType entityType;
    private int currentExp;
    private int maxExp;
    private int currentItems;
    private int maxSlots;
    private static final String HOLOGRAM_IDENTIFIER = "SmartSpawner-Holo";
    private final String uniqueIdentifier;

    private static final Vector3f SCALE = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final Vector3f TRANSLATION = new Vector3f(0.0f, 0.0f, 0.0f);
    private static final AxisAngle4f ROTATION = new AxisAngle4f(0, 0, 0, 0);

    public SpawnerHologram(Location location) {
        this.plugin = SmartSpawner.getInstance();
        this.spawnerLocation = location;
        this.languageManager = plugin.getLanguageManager();
        this.uniqueIdentifier = generateUniqueIdentifier(location);
    }

    private String generateUniqueIdentifier(Location location) {
        return HOLOGRAM_IDENTIFIER + "-" +
                location.getWorld().getName() + "-" +
                location.getBlockX() + "-" +
                location.getBlockY() + "-" +
                location.getBlockZ();
    }

    public void createHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        // Clean up any existing hologram for this spawner first
        cleanupExistingHologram();

        // Get backend preference from config
        String backend = plugin.getConfig().getString("hologram.backend", "auto").toLowerCase();
        
        boolean useHologramLib = false;
        boolean hologramLibAvailable = plugin.getIntegrationManager().getHologramLibHook() != null && 
                                      plugin.getIntegrationManager().getHologramLibHook().isEnabled();

        switch (backend) {
            case "hologramlib":
                if (hologramLibAvailable) {
                    useHologramLib = true;
                } else {
                    plugin.getLogger().warning("HologramLib backend requested but not available, falling back to TextDisplay");
                    useHologramLib = false;
                }
                break;
            case "textdisplay":
                useHologramLib = false;
                break;
            case "auto":
            default:
                useHologramLib = hologramLibAvailable;
                break;
        }

        if (useHologramLib) {
            createHologramLibHologram();
        } else {
            createTextDisplayHologram();
        }
    }

    private void createHologramLibHologram() {
        try {
            double offsetX = plugin.getConfig().getDouble("hologram.offset_x", 0.5);
            double offsetY = plugin.getConfig().getDouble("hologram.offset_y", 0.5);
            double offsetZ = plugin.getConfig().getDouble("hologram.offset_z", 0.5);

            Location holoLoc = spawnerLocation.clone().add(offsetX, offsetY, offsetZ);

            // Get configuration values
            String alignmentStr = plugin.getConfig().getString("hologram.alignment", "CENTER");
            boolean shadowed = plugin.getConfig().getBoolean("hologram.shadowed_text", true);
            boolean seeThrough = plugin.getConfig().getBoolean("hologram.see_through", false);

            // Use reflection to create HologramLib hologram
            Class<?> textHologramClass = Class.forName("me.hsgamer.hologramlib.spigot.hologram.TextHologram");
            Object hologram = textHologramClass.getConstructor(String.class)
                    .newInstance(uniqueIdentifier);

            // Set text (will be updated later with actual data)
            textHologramClass.getMethod("setText", String.class)
                    .invoke(hologram, "Loading...");

            // Set see through blocks
            textHologramClass.getMethod("setSeeThroughBlocks", boolean.class)
                    .invoke(hologram, seeThrough);

            // Set billboard
            Class<?> billboardClass = Class.forName("org.bukkit.entity.Display$Billboard");
            Object centerBillboard = billboardClass.getField("CENTER").get(null);
            textHologramClass.getMethod("setBillboard", billboardClass)
                    .invoke(hologram, centerBillboard);

            // Set shadow
            textHologramClass.getMethod("setShadow", boolean.class)
                    .invoke(hologram, shadowed);

            // Set alignment
            try {
                Class<?> alignmentClass = Class.forName("org.bukkit.entity.TextDisplay$TextAlignment");
                Object alignment = alignmentClass.getField(alignmentStr.toUpperCase()).get(null);
                textHologramClass.getMethod("setAlignment", alignmentClass)
                        .invoke(hologram, alignment);
            } catch (Exception e) {
                // Use default alignment if invalid
                plugin.getLogger().warning("Invalid hologram alignment in config: " + alignmentStr + ". Using CENTER as default.");
            }

            // Spawn the hologram
            Object manager = plugin.getIntegrationManager().getHologramLibHook().getHologramManager();
            manager.getClass().getMethod("spawn", Object.class, Location.class)
                    .invoke(manager, hologram, holoLoc);

            this.hologramLibHologram = hologram;
            updateText();
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating HologramLib hologram: " + e.getMessage());
            e.printStackTrace();
            // Fall back to TextDisplay
            createTextDisplayHologram();
        }
    }

    private void createTextDisplayHologram() {
        double offsetX = plugin.getConfig().getDouble("hologram.offset_x", 0.5);
        double offsetY = plugin.getConfig().getDouble("hologram.offset_y", 0.5);
        double offsetZ = plugin.getConfig().getDouble("hologram.offset_z", 0.5);

        Location holoLoc = spawnerLocation.clone().add(offsetX, offsetY, offsetZ);

        // Use the location scheduler to spawn the entity in the correct region
        Scheduler.runLocationTask(holoLoc, () -> {
            try {
                TextDisplay display = spawnerLocation.getWorld().spawn(holoLoc, TextDisplay.class, td -> {
                    td.setBillboard(Display.Billboard.CENTER);
                    // Get alignment from config with CENTER as default
                    String alignmentStr = plugin.getConfig().getString("hologram.alignment", "CENTER");
                    TextDisplay.TextAlignment alignment;
                    try {
                        alignment = TextDisplay.TextAlignment.valueOf(alignmentStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        alignment = TextDisplay.TextAlignment.CENTER;
                        plugin.getLogger().warning("Invalid hologram alignment in config: " + alignmentStr + ". Using CENTER as default.");
                    }
                    td.setAlignment(alignment);
                    td.setViewRange(16.0f);
                    td.setShadowed(plugin.getConfig().getBoolean("hologram.shadowed_text", true));
                    td.setDefaultBackground(false);
                    td.setTransformation(new Transformation(TRANSLATION, ROTATION, SCALE, ROTATION));
                    td.setSeeThrough(plugin.getConfig().getBoolean("hologram.see_through", false));
                    // Add custom name for identification
                    td.setCustomName(uniqueIdentifier);
                    td.setCustomNameVisible(false);
                });

                textDisplay.set(display);
                updateText();
            } catch (Exception e) {
                plugin.getLogger().severe("Error creating hologram: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void updateText() {
        // Prepare the text content
        String hologramText = prepareHologramText();
        if (hologramText == null) return;

        // Apply color codes
        final String finalText = ColorUtil.translateHexColorCodes(hologramText);

        // Update based on which system is being used
        if (hologramLibHologram != null) {
            updateHologramLibText(finalText);
        } else {
            updateTextDisplayText(finalText);
        }
    }

    private String prepareHologramText() {
        if (entityType == null) return null;

        // Prepare the text content
        String entityTypeName = languageManager.getFormattedMobName(entityType);

        // Create replacements map
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%entity%", entityTypeName);
        replacements.put("%ᴇɴᴛɪᴛʏ%", languageManager.getSmallCaps(entityTypeName));
        replacements.put("%stack_size%", String.valueOf(stackSize));
        replacements.put("%current_exp%", languageManager.formatNumber(currentExp));
        replacements.put("%max_exp%", languageManager.formatNumber(maxExp));
        replacements.put("%used_slots%", languageManager.formatNumber(currentItems));
        replacements.put("%max_slots%", languageManager.formatNumber(maxSlots));

        String hologramText = languageManager.getHologramText();

        // Apply replacements
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            hologramText = hologramText.replace(entry.getKey(), entry.getValue());
        }

        return hologramText;
    }

    private void updateHologramLibText(String text) {
        try {
            hologramLibHologram.getClass().getMethod("setText", String.class)
                    .invoke(hologramLibHologram, text);
        } catch (Exception e) {
            plugin.getLogger().severe("Error updating HologramLib text: " + e.getMessage());
        }
    }

    private void updateTextDisplayText(String text) {
        TextDisplay display = textDisplay.get();
        if (display == null) return;

        // Schedule the entity update on the entity's thread
        Scheduler.runEntityTask(display, () -> {
            if (display.isValid()) {
                display.setText(text);
            }
        });
    }

    public void updateData(int stackSize, EntityType entityType, int currentExp, int maxExp, int currentItems, int maxSlots) {
        // First, ensure we have a valid hologram
        boolean needsRecreate = false;
        
        if (hologramLibHologram != null) {
            // HologramLib is being used, check if it's still valid
            try {
                // HologramLib holograms don't have a simple validity check, assume valid if not null
                needsRecreate = false;
            } catch (Exception e) {
                needsRecreate = true;
            }
        } else {
            // TextDisplay is being used
            TextDisplay display = textDisplay.get();
            if (display == null || !display.isValid()) {
                needsRecreate = true;
            }
        }
        
        if (needsRecreate) {
            createHologram();
        }

        // Update data values
        this.stackSize = stackSize;
        this.entityType = entityType;
        this.currentExp = currentExp;
        this.maxExp = maxExp;
        this.currentItems = currentItems;
        this.maxSlots = maxSlots;

        // Update the text display
        updateText();
    }

    public void remove() {
        // Remove HologramLib hologram if it exists
        if (hologramLibHologram != null) {
            try {
                Object manager = plugin.getIntegrationManager().getHologramLibHook().getHologramManager();
                manager.getClass().getMethod("remove", Object.class)
                        .invoke(manager, hologramLibHologram);
                hologramLibHologram = null;
            } catch (Exception e) {
                plugin.getLogger().severe("Error removing HologramLib hologram: " + e.getMessage());
            }
        }
        
        // Remove TextDisplay hologram if it exists
        TextDisplay display = textDisplay.get();
        if (display != null && display.isValid()) {
            // Run on the entity's thread
            Scheduler.runEntityTask(display, display::remove);
            textDisplay.set(null);
        }
    }

    public void cleanupExistingHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        // Remove HologramLib hologram if it exists
        if (hologramLibHologram != null) {
            try {
                Object manager = plugin.getIntegrationManager().getHologramLibHook().getHologramManager();
                manager.getClass().getMethod("remove", Object.class)
                        .invoke(manager, hologramLibHologram);
                hologramLibHologram = null;
            } catch (Exception e) {
                plugin.getLogger().severe("Error cleaning up HologramLib hologram: " + e.getMessage());
            }
        }

        // First, check if our tracked hologram is still valid
        TextDisplay display = textDisplay.get();
        if (display != null) {
            if (display.isValid()) {
                // If it's valid but we're cleaning up, remove it
                display.remove();
            }
            textDisplay.set(null);
        }

        Scheduler.runLocationTask(spawnerLocation, () -> {
            // Define a tighter search radius just to catch any potentially duplicated holograms
            // with the same identifier (which shouldn't happen but being safe)
            double searchRadius = 2.0;

            // Look for any entity with our specific unique identifier
            spawnerLocation.getWorld().getNearbyEntities(spawnerLocation, searchRadius, searchRadius, searchRadius)
                    .stream()
                    .filter(entity -> entity instanceof TextDisplay && entity.getCustomName() != null)
                    .filter(entity -> entity.getCustomName().equals(uniqueIdentifier))
                    .forEach(Entity::remove);
        });
    }
}