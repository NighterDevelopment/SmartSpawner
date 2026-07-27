package github.nighter.smartspawner.spawner.data.storage;

import github.nighter.smartspawner.spawner.properties.ItemSignature;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary codec for a spawner's virtual inventory.
 *
 * <p>The virtual inventory is a map of distinct item templates to a {@code long} count, and that
 * count routinely exceeds {@link ItemStack#getMaxStackSize()}, so items and amounts have to be
 * stored separately. Item templates go through Paper's raw NBT serialization
 * ({@link ItemStack#serializeItemsAsBytes(java.util.Collection)}), which keeps every item component
 * (enchantments, display name, lore, custom model data, persistent data) instead of the
 * material-and-damage summary the legacy string format kept.</p>
 *
 * <p>Layout of the produced blob:</p>
 * <pre>
 * byte   format version
 * int    entry count n
 * long   amount, repeated n times, in template order
 * int    length of the item payload
 * byte[] ItemStack.serializeItemsAsBytes(templates), n templates each with amount 1
 * </pre>
 *
 * <p>Bump {@link #FORMAT_VERSION} and keep a decode branch for the old value when the layout
 * changes. An empty inventory encodes to {@code null} so the column stays NULL rather than holding
 * an empty payload.</p>
 */
public final class SpawnerInventoryCodec {

    private static final byte FORMAT_VERSION = 1;

    private SpawnerInventoryCodec() {
    }

    /**
     * Encode a consolidated inventory snapshot.
     *
     * @param items distinct item signatures mapped to their total count
     * @return the encoded blob, or null when there is nothing to store
     */
    public static byte[] encode(Map<ItemSignature, Long> items) throws IOException {
        if (items == null || items.isEmpty()) {
            return null;
        }

        List<ItemStack> templates = new ArrayList<>(items.size());
        List<Long> amounts = new ArrayList<>(items.size());

        for (Map.Entry<ItemSignature, Long> entry : items.entrySet()) {
            Long amount = entry.getValue();
            if (amount == null || amount <= 0L) {
                continue;
            }

            ItemStack template = entry.getKey().getTemplate();
            if (template == null || template.getType() == Material.AIR) {
                continue;
            }

            templates.add(template);
            amounts.add(amount);
        }

        if (templates.isEmpty()) {
            return null;
        }

        byte[] itemPayload = ItemStack.serializeItemsAsBytes(templates);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(itemPayload.length + (amounts.size() * 8) + 16);
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(FORMAT_VERSION);
            out.writeInt(amounts.size());
            for (Long amount : amounts) {
                out.writeLong(amount);
            }
            out.writeInt(itemPayload.length);
            out.write(itemPayload);
        }

        return buffer.toByteArray();
    }

    /**
     * Decode a blob produced by {@link #encode(Map)}.
     *
     * @param blob the stored payload, may be null or empty
     * @return item templates mapped to their total count, never null
     */
    public static Map<ItemStack, Long> decode(byte[] blob) throws IOException {
        if (blob == null || blob.length == 0) {
            return Map.of();
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            byte version = in.readByte();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported spawner inventory format version: " + version);
            }

            int count = in.readInt();
            if (count < 0) {
                throw new IOException("Negative spawner inventory entry count: " + count);
            }
            if (count == 0) {
                return Map.of();
            }

            long[] amounts = new long[count];
            for (int i = 0; i < count; i++) {
                amounts[i] = in.readLong();
            }

            int payloadLength = in.readInt();
            if (payloadLength < 0) {
                throw new IOException("Negative spawner inventory payload length: " + payloadLength);
            }

            byte[] itemPayload = in.readNBytes(payloadLength);
            if (itemPayload.length != payloadLength) {
                throw new IOException("Truncated spawner inventory payload: expected " + payloadLength
                        + " bytes, got " + itemPayload.length);
            }

            ItemStack[] templates = ItemStack.deserializeItemsFromBytes(itemPayload);
            if (templates.length != count) {
                throw new IOException("Spawner inventory entry count mismatch: " + count
                        + " amounts but " + templates.length + " items");
            }

            Map<ItemStack, Long> result = new LinkedHashMap<>(Math.max(16, count * 2));
            for (int i = 0; i < count; i++) {
                ItemStack template = templates[i];
                if (template == null || template.getType() == Material.AIR || amounts[i] <= 0L) {
                    continue;
                }
                result.merge(template, amounts[i], Long::sum);
            }
            return result;
        }
    }

    /**
     * Total item count of a consolidated inventory snapshot, saturating instead of overflowing.
     * Persisted alongside the blob so item totals can be read without decoding it.
     */
    public static long totalItems(Map<ItemSignature, Long> items) {
        if (items == null || items.isEmpty()) {
            return 0L;
        }

        long total = 0L;
        for (Long amount : items.values()) {
            if (amount == null || amount <= 0L) {
                continue;
            }
            long sum = total + amount;
            if (sum < total) {
                return Long.MAX_VALUE;
            }
            total = sum;
        }
        return total;
    }
}
