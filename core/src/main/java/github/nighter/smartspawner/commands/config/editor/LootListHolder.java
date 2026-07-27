package github.nighter.smartspawner.commands.config.editor;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks the loot list of one mob or item spawner entry. */
@Getter
public class LootListHolder implements InventoryHolder {
    private final ConfigEditorTarget target;
    private final String entryKey;
    private final int listPage;

    public LootListHolder(ConfigEditorTarget target, String entryKey, int listPage) {
        this.target = target;
        this.entryKey = entryKey;
        this.listPage = listPage;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
