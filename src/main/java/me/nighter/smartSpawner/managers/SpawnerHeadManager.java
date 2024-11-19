package me.nighter.smartSpawner.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.profile.PlayerProfile;
import org.geysermc.floodgate.api.FloodgateApi;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class SpawnerHeadManager {
    private static final Map<EntityType, ItemStack> HEAD_CACHE = new EnumMap<>(EntityType.class);
    private static final Map<EntityType, String> TEXTURE_MAP = new EnumMap<>(EntityType.class);

    static {
        // Khởi tạo texture map
        TEXTURE_MAP.put(EntityType.BLAZE, "737623f79f7eb4f3f80da65b652cc44b2148eea41f9ffe2e86a23bdf49ab77b1");
        TEXTURE_MAP.put(EntityType.BREEZE, "a275728af7e6a29c88125b675a39d88ae9919bb61fdc200337fed6ab0c49d65c");
        TEXTURE_MAP.put(EntityType.CAVE_SPIDER, "eccc4a32d45d74e8b14ef1ffd55cd5f381a06d4999081d52eaea12e13293e209");
        TEXTURE_MAP.put(EntityType.CHICKEN, "4e8f3ea46735ad0c106c549ccb6cd489c97a41e73d092497ee424becd9dfd29c");
        TEXTURE_MAP.put(EntityType.COW, "b667c0e107be79d7679bfe89bbc57c6bf198ecb529a3295fcfdfd2f24408dca3");
        TEXTURE_MAP.put(EntityType.DROWNED, "c84df79c49104b198cdad6d99fd0d0bcf1531c92d4ab6269e40b7d3cbbb8e98c");
        TEXTURE_MAP.put(EntityType.ENDERMAN, "4f24767c8138b3dfec02f77bd151994d480d4e869664ce09a26b19289212162b");
        TEXTURE_MAP.put(EntityType.EVOKER, "630ce775edb65db8c2741bdfae84f3c0d0285aba93afadc74900d55dfd9504a5");
        TEXTURE_MAP.put(EntityType.GHAST, "64ab8a22e7687cc4c78f3b6ff5b1eb04917b51cd3cd7dbce36171160b3c77ced");
        TEXTURE_MAP.put(EntityType.GLOW_SQUID, "4cb07d905888f8472252f9cfa39aa317babcad30af08cfe751adefa716b02036");
        TEXTURE_MAP.put(EntityType.GUARDIAN, "b8e725779c234c590cce854db5c10485ed8d8a33fa9b2bdc3424b68bb1380bed");
        TEXTURE_MAP.put(EntityType.HOGLIN, "7ad7b5aeb220c079e319cd70ac8800e80774a9313c22f38e77afb89999e6ec87");
        TEXTURE_MAP.put(EntityType.HUSK, "d674c63c8db5f4ca628d69a3b1f8a36e29d8fd775e1a6bdb6cabb4be4db121");
        TEXTURE_MAP.put(EntityType.IRON_GOLEM, "4271913a3fc8f56bdf6b90a4b4ed6a05c562ce0076b5344d444fb2b040ae57d");
        TEXTURE_MAP.put(EntityType.MAGMA_CUBE, "38957d5023c937c4c41aa2412d43410bda23cf79a9f6ab36b76fef2d7c429");
        TEXTURE_MAP.put(EntityType.PIG, "9b1760e3778f8087046b86bec6a0a83a567625f30f0d6bce866d4bed95dba6c1");
        TEXTURE_MAP.put(EntityType.PILLAGER, "4aee6bb37cbfc92b0d86db5ada4790c64ff4468d68b84942fde04405e8ef5333");
        TEXTURE_MAP.put(EntityType.PUFFERFISH, "292350c9f0993ed54db2c7113936325683ffc20104a9b622aa457d37e708d931");
        TEXTURE_MAP.put(EntityType.RABBIT, "cd1d2bbc3d77ab0d119c031ea74d8781c93285cf736abc422baec1eb1560e8ca");
        TEXTURE_MAP.put(EntityType.RAVAGER, "cd20bf52ec390a0799299184fc678bf84cf732bb1bd78fd1c4b441858f0235a8");
        TEXTURE_MAP.put(EntityType.SALMON, "d4d001589b86c22cf24f1618fe7efef12932aa9148b5e4fc6ff4a614b990ae12");
        TEXTURE_MAP.put(EntityType.SHEEP, "a723893df4cfb9c7240fc47b560ccf6ddeb19da9183d33083f2c71f46dad290a");
        TEXTURE_MAP.put(EntityType.SHULKER, "537a294f6b7b4ba437e5cb35fb20f46792e7ac0a490a66132a557124ec5f997a");
        TEXTURE_MAP.put(EntityType.SPIDER, "e5871c22b81c12e67f5aebd9afe0958b81cada6305c07599a07b01ab126ba2c4");
        TEXTURE_MAP.put(EntityType.SQUID, "d8705624daa2956aa45956c81bab5f4fdb2c74a596051e24192039aea3a8b8");
        TEXTURE_MAP.put(EntityType.STRAY, "2c5097916bc0565d30601c0eebfeb287277a34e867b4ea43c63819d53e89ede7");
        TEXTURE_MAP.put(EntityType.STRIDER, "125851a86ee1c54c94fc5bed017823dfb3ba08eddbcab2a914ef45b596c1603");
        TEXTURE_MAP.put(EntityType.TROPICAL_FISH, "d6dd5e6addb56acbc694ea4ba5923b1b25688178feffa72290299e2505c97281");
        TEXTURE_MAP.put(EntityType.VINDICATOR, "4f6fb89d1c631bd7e79fe185ba1a6705425f5c31a5ff626521e395d4a6f7e2");
        TEXTURE_MAP.put(EntityType.WITCH, "8aa986a6e1c2d88ff198ab2c3259e8d2674cb83a6d206f883bad2c8ada819");
        TEXTURE_MAP.put(EntityType.ZOGLIN, "c19b7b5e9ffd4e22b890ab778b4795b662faff2b4978bf815574e48b0e52b301");
        TEXTURE_MAP.put(EntityType.ZOMBIE_VILLAGER, "37e838ccc26776a217c678386f6a65791fe8cdab8ce9ca4ac6b28397a4d81c22");
        TEXTURE_MAP.put(EntityType.ZOMBIFIED_PIGLIN, "e935842af769380f78e8b8a88d1ea6ca2807c1e5693c2cf797456620833e936f");
        // Thêm các texture khác nếu cần
    }

    private static boolean isBedrockPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api.isFloodgatePlayer(uuid);
        } catch (NoClassDefFoundError | NullPointerException e) {
            return false;
        }
    }

    public static ItemStack getCustomHead(EntityType entityType, Player player) {
        // Xử lý các head có sẵn trong game trước
        switch (entityType) {
            case ZOMBIE:
                return new ItemStack(Material.ZOMBIE_HEAD);
            case SKELETON:
                return new ItemStack(Material.SKELETON_SKULL);
            case WITHER_SKELETON:
                return new ItemStack(Material.WITHER_SKELETON_SKULL);
            case CREEPER:
                return new ItemStack(Material.CREEPER_HEAD);
            case PIGLIN, PIGLIN_BRUTE:
                return new ItemStack(Material.PIGLIN_HEAD);
        }

        if (isBedrockPlayer(player)) {
            return new ItemStack(Material.SPAWNER);
        }

        // Kiểm tra cache
        if (HEAD_CACHE.containsKey(entityType)) {
            return HEAD_CACHE.get(entityType).clone();
        }

        // Nếu không có trong TEXTURE_MAP, trả về spawner
        if (!TEXTURE_MAP.containsKey(entityType)) {
            return new ItemStack(Material.SPAWNER);
        }

        // Tạo head mới
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        try {
            String texture = TEXTURE_MAP.get(entityType);

            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            URL url = new URL("http://textures.minecraft.net/texture/" + texture);
            textures.setSkin(url);
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);

            head.setItemMeta(meta);

            // Cache the head
            HEAD_CACHE.put(entityType, head.clone());

            return head;
        } catch (Exception e) {
            e.printStackTrace();
            return new ItemStack(Material.SPAWNER);
        }
    }
    // Phương thức để xóa cache nếu cần
    public static void clearCache() {
        HEAD_CACHE.clear();
    }
}
