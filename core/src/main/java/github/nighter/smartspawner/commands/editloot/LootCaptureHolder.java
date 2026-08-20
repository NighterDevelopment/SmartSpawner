package github.nighter.smartspawner.commands.editloot;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks the one-slot "drop an item" capture screen.
 *
 * <p>Typing an item out by hand cannot express a custom item from another plugin, so the editor reads
 * the real stack the admin drops in. What happens to the captured item depends on {@link Purpose}.</p>
 */
@Getter
public class LootCaptureHolder implements InventoryHolder {

    public enum Purpose {
        /** Add a new loot row for the captured item. */
        ADD_LOOT,
        /** Repoint an existing loot row at the captured item. */
        REPLACE_LOOT
    }

    private final LootEditorTarget target;
    private final Purpose purpose;
    private final String entryKey;
    private final String lootKey;

    public LootCaptureHolder(LootEditorTarget target, Purpose purpose, String entryKey, String lootKey) {
        this.target = target;
        this.purpose = purpose;
        this.entryKey = entryKey;
        this.lootKey = lootKey;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
