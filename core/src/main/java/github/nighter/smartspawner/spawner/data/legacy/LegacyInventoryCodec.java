package github.nighter.smartspawner.spawner.data.legacy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only reader for the pre-1.8 spawner inventory format.
 *
 * <p>That format described items as strings holding only material, damage and (for tipped arrows)
 * potion type, so it could not represent enchantments, names, lore or any other item component.
 * It is kept solely so existing {@code spawners_data.yml} files and pre-v3 database rows can be
 * read once and rewritten through
 * {@link github.nighter.smartspawner.spawner.data.storage.SpawnerInventoryCodec}. Nothing writes
 * this format any more, and this class can be deleted once the migration window closes.</p>
 *
 * <p>Two encodings existed. YAML stored a string list directly; the database wrapped that same list
 * in a hand-rolled JSON array, which {@link #parseJsonArray(String)} unwraps.</p>
 */
public final class LegacyInventoryCodec {

    private LegacyInventoryCodec() {
    }

    /**
     * Split the hand-rolled JSON array the old database column used.
     *
     * @param jsonData a string shaped like {@code ["STONE:12","BOW;3:1"]}
     * @return the raw entries, or an empty list when the input is not in that shape
     */
    public static List<String> parseJsonArray(String jsonData) {
        List<String> entries = new ArrayList<>();
        if (jsonData == null || jsonData.isEmpty()) {
            return entries;
        }

        if (!jsonData.startsWith("[") || !jsonData.endsWith("]")) {
            return entries;
        }

        String content = jsonData.substring(1, jsonData.length() - 1);
        if (content.isEmpty()) {
            return entries;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (char c : content.toCharArray()) {
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (c == ',' && !inQuotes) {
                if (!current.isEmpty()) {
                    entries.add(current.toString());
                    current = new StringBuilder();
                }
                continue;
            }

            current.append(c);
        }

        if (!current.isEmpty()) {
            entries.add(current.toString());
        }

        return entries;
    }

    /**
     * Rebuild item templates and their counts from the legacy string entries.
     * Unparseable entries are skipped rather than failing the whole inventory.
     *
     * @param data legacy entries, one per distinct item grouping
     * @return item templates mapped to their total count
     */
    public static Map<ItemStack, Long> deserialize(List<String> data) {
        Map<ItemStack, Long> result = new LinkedHashMap<>();
        if (data == null || data.isEmpty()) {
            return result;
        }

        for (String entry : data) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            try {
                if (entry.startsWith("TIPPED_ARROW#")) {
                    readTippedArrows(entry, result);
                } else if (entry.contains(";")) {
                    readDamagedItems(entry, result);
                } else {
                    readPlainItem(entry, result);
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                // Unknown material, malformed count: drop this entry and keep the rest.
            }
        }

        return result;
    }

    private static void readTippedArrows(String entry, Map<ItemStack, Long> result) {
        String[] potionEntries = entry.substring("TIPPED_ARROW#".length()).split(",");
        for (String potionEntry : potionEntries) {
            String[] parts = potionEntry.split(":");
            if (parts.length < 2) {
                continue;
            }

            long count = Long.parseLong(parts[1]);
            if (count <= 0) {
                continue;
            }

            ItemStack arrow = new ItemStack(Material.TIPPED_ARROW);
            if (arrow.getItemMeta() instanceof PotionMeta meta) {
                PotionType potionType;
                try {
                    potionType = PotionType.valueOf(parts[0]);
                } catch (IllegalArgumentException e) {
                    potionType = PotionType.WATER;
                }
                meta.setBasePotionType(potionType);
                arrow.setItemMeta(meta);
            }

            result.merge(arrow, count, Long::sum);
        }
    }

    private static void readDamagedItems(String entry, Map<ItemStack, Long> result) {
        String[] parts = entry.split(";");
        if (parts.length < 2) {
            return;
        }

        Material material = Material.valueOf(parts[0]);
        for (String damageCount : parts[1].split(",")) {
            String[] pair = damageCount.split(":");
            if (pair.length < 2) {
                continue;
            }

            int damage = Integer.parseInt(pair[0]);
            long count = Long.parseLong(pair[1]);
            if (count <= 0) {
                continue;
            }

            ItemStack item = new ItemStack(material);
            if (item.getItemMeta() instanceof Damageable damageable) {
                damageable.setDamage(damage);
                item.setItemMeta(damageable);
            }

            result.merge(item, count, Long::sum);
        }
    }

    private static void readPlainItem(String entry, Map<ItemStack, Long> result) {
        String[] parts = entry.split(":");
        if (parts.length < 2) {
            return;
        }

        Material material = Material.valueOf(parts[0]);
        long count = Long.parseLong(parts[1]);
        if (count <= 0) {
            return;
        }

        result.merge(new ItemStack(material), count, Long::sum);
    }
}
