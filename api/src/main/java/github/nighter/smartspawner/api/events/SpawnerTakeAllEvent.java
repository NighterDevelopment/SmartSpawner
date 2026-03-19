package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Called when all items are taken from a spawner's storage.
 */
@Getter
@Setter
public class SpawnerTakeAllEvent extends Event implements Cancellable {
    private final Player player;
    private final Location location;
    private final Map<Integer, ItemStack> items;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    /**
     * Creates a new spawner take all event.
     *
     * @param player the player taking the items
     * @param location the location of the spawner
     * @param items the items being taken
     */
    public SpawnerTakeAllEvent(Player player, Location location, Map<Integer, ItemStack> items) {
        this.player = player;
        this.location = location;
        this.items = items;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
