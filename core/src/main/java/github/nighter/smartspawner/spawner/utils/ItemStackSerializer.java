package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;

public class ItemStackSerializer {
    // Static lists for armor and tool materials
    private static final List<String> ARMOR_MATERIALS = Arrays.asList("LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE");
    private static final List<String> ARMOR_PIECES = Arrays.asList("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS");
    private static final List<String> TOOL_TYPES = Arrays.asList("_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE");

    @Getter
    public static class ItemGroup {
        private final Material material;
        private final Map<Integer, Integer> damageCount; // Changed from durability to damage
        private final Map<String, Integer> potionTypeCount; // Changed from potionDataCount

        public ItemGroup(Material material) {
            this.material = material;
            this.damageCount = new HashMap<>();
            this.potionTypeCount = new HashMap<>();
        }

        public void addItem(int damage, int count) {
            damageCount.merge(damage, count, Integer::sum);
        }

        public void addPotionArrow(PotionType potionType, int count) {
            String potionKey = potionType.name();
            potionTypeCount.merge(potionKey, count, Integer::sum);
        }
    }

    public static List<String> serializeInventory(Map<VirtualInventory.ItemSignature, Long> items) {
        Map<Material, ItemGroup> groupedItems = new HashMap<>();

        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            // Use getTemplateRef() to avoid cloning - we only need to read properties
            ItemStack template = entry.getKey().getTemplateRef();
            Material material = template.getType();
            ItemGroup group = groupedItems.computeIfAbsent(material, ItemGroup::new);

            if (material == Material.TIPPED_ARROW) {
                PotionMeta meta = (PotionMeta) template.getItemMeta();
                if (meta != null && meta.getBasePotionType() != null) {
                    group.addPotionArrow(meta.getBasePotionType(), entry.getValue().intValue());
                } else {
                    // Handle case where tipped arrow has no potion data (default to WATER)
                    group.addPotionArrow(PotionType.WATER, entry.getValue().intValue());
                }
            } else if (isDestructibleItem(material)) {
                // Use modern damage system instead of durability
                int damage = getDamageValue(template);
                group.addItem(damage, entry.getValue().intValue());
            } else {
                // For non-destructible items, always use damage 0
                group.addItem(0, entry.getValue().intValue());
            }
        }

        List<String> serializedItems = new ArrayList<>();
        for (ItemGroup group : groupedItems.values()) {
            if (group.getMaterial() == Material.TIPPED_ARROW) {
                // Format: TIPPED_ARROW#potion_type:count,...
                StringBuilder sb = new StringBuilder("TIPPED_ARROW#");
                boolean first = true;
                for (Map.Entry<String, Integer> entry : group.getPotionTypeCount().entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append(entry.getKey()).append(':').append(entry.getValue());
                    first = false;
                }
                serializedItems.add(sb.toString());
            } else if (isDestructibleItem(group.getMaterial())) {
                // Format for destructible items with damage
                StringBuilder sb = new StringBuilder(group.getMaterial().name());
                sb.append(';');
                boolean first = true;
                for (Map.Entry<Integer, Integer> entry : group.getDamageCount().entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append(entry.getKey()).append(':').append(entry.getValue());
                    first = false;
                }
                serializedItems.add(sb.toString());
            } else {
                // Format for normal items without damage
                int totalCount = group.getDamageCount().values().stream()
                        .mapToInt(Integer::intValue).sum();
                serializedItems.add(group.getMaterial().name() + ":" + totalCount);
            }
        }
        return serializedItems;
    }

    public static Map<ItemStack, Integer> deserializeInventory(List<String> data) {
        Map<ItemStack, Integer> result = new HashMap<>();

        for (String entry : data) {
            if (entry.startsWith("TIPPED_ARROW#")) {
                // Handle TIPPED_ARROW with modern potion types
                String[] potionEntries = entry.substring("TIPPED_ARROW#".length()).split(",");
                for (String potionEntry : potionEntries) {
                    String[] parts = potionEntry.split(":");
                    String potionTypeName = parts[0];
                    int count = Integer.parseInt(parts[1]);

                    ItemStack arrow = new ItemStack(Material.TIPPED_ARROW);
                    PotionMeta meta = (PotionMeta) arrow.getItemMeta();
                    if (meta != null) {
                        try {
                            PotionType potionType = PotionType.valueOf(potionTypeName);
                            meta.setBasePotionType(potionType);
                            arrow.setItemMeta(meta);
                        } catch (IllegalArgumentException e) {
                            // Handle case where potion type is not valid, default to WATER
                            meta.setBasePotionType(PotionType.WATER);
                            arrow.setItemMeta(meta);
                        }
                    }
                    result.put(arrow, count);
                }
            } else if (entry.contains(";")) {
                // Logic for destructible items with damage
                String[] parts = entry.split(";");
                Material material = Material.valueOf(parts[0]);

                for (String damageCount : parts[1].split(",")) {
                    String[] dc = damageCount.split(":");
                    int damage = Integer.parseInt(dc[0]);
                    int count = Integer.parseInt(dc[1]);

                    ItemStack item = new ItemStack(material);
                    setDamageValue(item, damage);
                    result.put(item, count);
                }
            } else {
                // Logic for normal items
                String[] parts = entry.split(":");
                Material material = Material.valueOf(parts[0]);
                int count = Integer.parseInt(parts[1]);

                ItemStack item = new ItemStack(material);
                // No need to set damage for non-destructible items
                result.put(item, count);
            }
        }
        return result;
    }

    /**
     * Get damage value from ItemStack using modern API
     */
    private static int getDamageValue(ItemStack item) {
        if (item.getItemMeta() instanceof Damageable damageable) {
            return damageable.getDamage();
        }
        return 0;
    }

    /**
     * Set damage value to ItemStack using modern API
     */
    private static void setDamageValue(ItemStack item, int damage) {
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(damage);
            item.setItemMeta(damageable);
        }
    }

    public static boolean isDestructibleItem(Material material) {
        String name = material.name();

        // Check if it's a tool
        for (String type : TOOL_TYPES) {
            if (name.endsWith(type)) {
                return true;
            }
        }

        // Check if it's armor
        for (String armorMaterial : ARMOR_MATERIALS) {
            for (String armorPiece : ARMOR_PIECES) {
                if (name.equals(armorMaterial + armorPiece)) {
                    return true;
                }
            }
        }

        // Check specific items that can be damaged
        return name.equals("BOW")
                || name.equals("FISHING_ROD")
                || name.equals("FLINT_AND_STEEL")
                || name.equals("SHEARS")
                || name.equals("SHIELD")
                || name.equals("ELYTRA")
                || name.equals("TRIDENT")
                || name.equals("CROSSBOW")
                || name.equals("CARROT_ON_A_STICK")
                || name.equals("WARPED_FUNGUS_ON_A_STICK")
                || name.equals("MACE");
    }
}