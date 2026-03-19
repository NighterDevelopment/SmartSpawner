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

import java.util.List;
import java.util.Map;

/**
 * Called when all items are dropped from a spawner's storage.
 */
@Getter
@Setter
public class SpawnerDropAllEvent extends Event implements Cancellable {
    private final Player player;
    private final Location location;
    private final List<ItemStack> items;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    /**
     * Creates a new spawner drop all event.
     *
     * @param player the player dropping the items
     * @param location the location of the spawner
     * @param items the items being dropped
     */
    public SpawnerDropAllEvent(Player player, Location location, List<ItemStack> items) {
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
