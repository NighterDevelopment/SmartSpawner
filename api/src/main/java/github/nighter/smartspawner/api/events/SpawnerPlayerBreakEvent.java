package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class SpawnerPlayerBreakEvent extends SpawnerBreakEvent implements Cancellable {
    private final Player player;
    private boolean cancelled = false;

    /**
     * The constructor for the event.
     *
     * @param player   The player who broke the spawner
     * @param location The location who broke of the spawner.
     * @param quantity The quantity of the spawner.
     */
    public SpawnerPlayerBreakEvent(Player player, Location location, int quantity) {
        super(player, location, quantity);
        this.player = player;
    }

    /**
     * Get the player who broke the spawner
     * @return Player who broke the spawner
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
