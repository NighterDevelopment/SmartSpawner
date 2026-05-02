package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when spawners are stacked together.
 */
@Getter
public class SpawnerStackEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final Location location;
    private final int oldStackSize;
    private final int newStackSize;
    private final StackSource source;
    private final EntityType entityType;

    @Setter
    private boolean cancelled = false;

    /**
     * Creates a new spawner stack event.
     *
     * @param player the player who stacked the spawner
     * @param location the location of the spawner
     * @param oldStackSize the old stack size
     * @param newStackSize the new stack size
     * @param source the source of the stack operation
     */
    public SpawnerStackEvent(Player player, Location location, int oldStackSize, int newStackSize, StackSource source) {
        this(player, location, oldStackSize, newStackSize, source, null);
    }

    /**
     * Creates a new spawner stack event.
     *
     * @param player the player who stacked the spawner
     * @param location the location of the spawner
     * @param oldStackSize the old stack size
     * @param newStackSize the new stack size
     * @param source the source of the stack operation
     * @param entityType the spawned entity type of the spawner
     */
    public SpawnerStackEvent(Player player, Location location, int oldStackSize, int newStackSize,
                             StackSource source, @Nullable EntityType entityType) {
        this.player = player;
        this.location = location;
        this.oldStackSize = oldStackSize;
        this.newStackSize = newStackSize;
        this.source = source;
        this.entityType = entityType;
    }

    /**
     * Creates a new spawner stack event with default PLACE source.
     *
     * @param player the player who stacked the spawner
     * @param location the location of the spawner
     * @param oldStackSize the old stack size
     * @param newStackSize the new stack size
     */
    public SpawnerStackEvent(Player player, Location location, int oldStackSize, int newStackSize) {
        this(player, location, oldStackSize, newStackSize, StackSource.PLACE, null);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }

    /**
     * Represents the source of a stack operation.
     */
    public enum StackSource {
        /**
         * Stacked by placing a spawner on another.
         */
        PLACE,

        /**
         * Stacked through the GUI interface.
         */
        GUI
    }
}