package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * SpawnerPlaceEvent is called when a spawner being placed by a player.
 */
public class SpawnerPlaceEvent extends SpawnerEvent implements Cancellable {
    private final Player player;
    private boolean cancelled = false;

    /**
     * The constructor for the event.
     *
     * @param player         The player who placed the spawner.
     * @param location       The location of the spawner placed.
     * @param quantity       The quantity of the spawner placed.
     */
    public SpawnerPlaceEvent(Player player, Location location, int quantity) {
        super(location, quantity);
        this.player = player;
    }

    /**
     * Get the player who placed the spawner.
     * @return The player who placed the spawner.
     */
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
