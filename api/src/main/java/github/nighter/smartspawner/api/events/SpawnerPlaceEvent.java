package github.nighter.smartspawner.api.events;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * SpawnerPlaceEvent is called when a spawner being placed by a player.
 */
public class SpawnerPlaceEvent extends SpawnerEvent implements Cancellable {
    private final Player player;
    @Getter
    private final EntityType entityType;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    /**
     * The constructor for the event.
     *
     * @param player         The player who placed the spawner.
     * @param location       The location of the spawner placed.
     * @param quantity       The quantity of the spawner placed.
     */
    public SpawnerPlaceEvent(Player player, Location location, EntityType entityType, int quantity) {
        super(location, quantity);
        this.player = player;
        this.entityType = entityType;
    }

    /**
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

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}