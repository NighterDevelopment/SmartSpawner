package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class SpawnerRemoveEvent extends SpawnerEvent implements Cancellable {
    private final Player player;
    private final int changeAmount;
    private boolean cancelled = false;

    /**
     * The constructor for the event.
     *
     * @param location The location of the spawner.
     * @param newQuantity The quantity of the spawner.
     */
    public SpawnerRemoveEvent(Player player, Location location, int newQuantity, int changeAmount) {
        super(location, newQuantity);
        this.player = player;
        this.changeAmount = changeAmount;
    }

    public int getChangeAmount() {
        return changeAmount;
    }

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
