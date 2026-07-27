package github.nighter.smartspawner.commands.config.editor;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks the one-slot inventory used to read an item off the admin's cursor.
 *
 * <p>Typing an item out by hand cannot express a custom item from another plugin, so the editor asks
 * for the real stack instead. What happens to the captured item depends on {@link Purpose}.</p>
 */
@Getter
public class ItemCaptureHolder implements InventoryHolder {

    public enum Purpose {
        /** Add a new loot entry for the captured item. */
        ADD_LOOT,
        /** Repoint an existing loot entry at the captured item. */
        REPLACE_LOOT,
        /** Read the captured item's type as the key of a brand new entry. */
        NEW_ENTRY
    }

    private final ConfigEditorTarget target;
    private final Purpose purpose;
    private final String entryKey;
    private final String lootKey;
    private final int listPage;

    public ItemCaptureHolder(ConfigEditorTarget target, Purpose purpose, String entryKey,
                             String lootKey, int listPage) {
        this.target = target;
        this.purpose = purpose;
        this.entryKey = entryKey;
        this.lootKey = lootKey;
        this.listPage = listPage;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
