package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player claims experience from a spawner.
 */
@Getter
@Setter
public class SpawnerExpClaimEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final Location location;
    private long expAmount;
    private boolean cancelled = false;

    /**
     * Creates a new spawner experience claim event.
     *
     * @param player the player claiming the experience
     * @param location the location of the spawner
     * @param expAmount the amount of experience claimed
     */
    public SpawnerExpClaimEvent(Player player, Location location, long expAmount) {
        this.player = player;
        this.location = location;
        this.expAmount = expAmount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
