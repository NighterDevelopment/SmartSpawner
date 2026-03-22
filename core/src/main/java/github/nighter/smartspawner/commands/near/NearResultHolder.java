package github.nighter.smartspawner.commands.near;

import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

@Getter
public class NearResultHolder implements InventoryHolder {
    private final UUID playerUUID;
    private final int currentPage;
    private final int totalPages;

    public NearResultHolder(UUID playerUUID, int currentPage, int totalPages) {
        this.playerUUID = playerUUID;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
