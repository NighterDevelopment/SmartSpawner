package github.nighter.smartspawner.spawner.config;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.profile.PlayerProfile;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerMobHeadTexture {
    // Thread-safe cache for player heads (Folia compatibility)
    private static final Map<EntityType, ItemStack> HEAD_CACHE = new EnumMap<>(EntityType.class);
    
    // Cache PlayerProfile objects to avoid repeated URL parsing and profile creation
    // This eliminates expensive operations before ItemStack creation
    private static final Map<EntityType, PlayerProfile> PROFILE_CACHE = new EnumMap<>(EntityType.class);
    
    // Cache Bedrock player check results per player UUID for the session
    // Cleared when player disconnects or on cache invalidation
    private static final Map<UUID, Boolean> BEDROCK_PLAYER_CACHE = new ConcurrentHashMap<>();
    
    private static final ItemStack DEFAULT_SPAWNER_BLOCK = new ItemStack(Material.SPAWNER);

    private static boolean isBedrockPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        
        // Check cache first to avoid repeated Floodgate API calls
        return BEDROCK_PLAYER_CACHE.computeIfAbsent(playerUUID, uuid -> {
            SmartSpawner plugin = SmartSpawner.getInstance();
            if (plugin == null || plugin.getIntegrationManager() == null || 
                plugin.getIntegrationManager().getFloodgateHook() == null) {
                return false;
            }
            return plugin.getIntegrationManager().getFloodgateHook().isBedrockPlayer(player);
        });
    }
    
    /**
     * Clear Bedrock player cache entry when player disconnects
     */
    public static void clearBedrockPlayerCache(UUID playerUUID) {
        BEDROCK_PLAYER_CACHE.remove(playerUUID);
    }

    public static ItemStack getCustomHead(EntityType entityType, Player player) {
        if (entityType == null) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        if (isBedrockPlayer(player)) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        return getCustomHead(entityType);
    }

    public static ItemStack getCustomHead(EntityType entityType) {
        if (entityType == null) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        SmartSpawner plugin = SmartSpawner.getInstance();
        if (plugin == null) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        SpawnerSettingsConfig settingsConfig = plugin.getSpawnerSettingsConfig();
        if (settingsConfig == null) {
            return DEFAULT_SPAWNER_BLOCK;
        }
        
        // Get material from config
        Material material = settingsConfig.getMaterial(entityType);
        
        // If it's not a player head, return the vanilla head
        if (material != Material.PLAYER_HEAD) {
            return new ItemStack(material);
        }
        
        // Check cache for player heads - return clone to prevent external modifications
        if (HEAD_CACHE.containsKey(entityType)) {
            return HEAD_CACHE.get(entityType).clone();
        }
        
        // Check if we have a custom texture
        if (!settingsConfig.hasCustomTexture(entityType)) {
            return new ItemStack(material);
        }
        
        // Try to get cached profile first, create if not exists
        PlayerProfile profile = PROFILE_CACHE.computeIfAbsent(entityType, type -> {
            try {
                String texture = settingsConfig.getCustomTexture(type);
                PlayerProfile newProfile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = newProfile.getTextures();
                URL url = new URL("http://textures.minecraft.net/texture/" + texture);
                textures.setSkin(url);
                newProfile.setTextures(textures);
                return newProfile;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
        
        // If profile creation failed, return basic player head
        if (profile == null) {
            return new ItemStack(material);
        }
        
        // Create ItemStack with cached profile (avoids URL parsing on every call)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
            // Cache the complete ItemStack for even faster subsequent access
            HEAD_CACHE.put(entityType, head.clone());
        }
        
        return head;
    }

    public static void clearCache() {
        HEAD_CACHE.clear();
        PROFILE_CACHE.clear();
        BEDROCK_PLAYER_CACHE.clear();
    }
}