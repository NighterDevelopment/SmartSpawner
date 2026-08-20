package github.nighter.smartspawner.commands.editloot;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks the 27-slot loot list for one spawner entry.
 *
 * <p>The editor tells its screens apart by their holder, never by title, so a translated or renamed
 * title cannot break click routing.</p>
 */
@Getter
public class LootListHolder implements InventoryHolder {

    private final LootEditorTarget target;
    private final String entryKey;

    public LootListHolder(LootEditorTarget target, String entryKey) {
        this.target = target;
        this.entryKey = entryKey;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
