package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * SpawnerEvent is called when a spawner being broke by a player or an explosion.
 */
public abstract class SpawnerEvent extends Event {
    private final Location location;
    private final int quantity;

    private static final HandlerList handlers = new HandlerList();

    /**
     * The constructor for the event.
     *
     * @param location       The location of the spawner.
     * @param quantity       The quantity of the spawner.
     */
    public SpawnerEvent(Location location, int quantity) {
        this.location = location;
        this.quantity = quantity;
    }

    /**
     * Get the location of the spawner broken.
     * @return The location of the spawner broken
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Get the quantity of the spawner broken.
     * @return The quantity of the spawner broken
     */
    public int getQuantity() {
        return quantity;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }
}
