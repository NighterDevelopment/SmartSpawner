package github.nighter.smartspawner.commands.config.editor;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks the paged list of every entry in one settings file. */
@Getter
public class EntryListHolder implements InventoryHolder {
    private final ConfigEditorTarget target;
    private final int page;

    public EntryListHolder(ConfigEditorTarget target, int page) {
        this.target = target;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
