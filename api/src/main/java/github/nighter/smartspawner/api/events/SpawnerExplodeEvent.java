package github.nighter.smartspawner.api.events;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when a spawner is destroyed by an explosion.
 */
@Getter
public class SpawnerExplodeEvent extends SpawnerBreakEvent {

    private static final HandlerList handlers = new HandlerList();

    private final boolean exploded;

    /**
     * Creates a new spawner explode event.
     *
     * @param entity the entity that caused the explosion
     * @param location the location where the spawner was destroyed
     * @param quantity the quantity of the spawner
     * @param exploded whether the spawner was successfully exploded
     */
    public SpawnerExplodeEvent(Entity entity, Location location, int quantity, boolean exploded) {
        this(entity, location, quantity, exploded, null);
    }

    /**
     * Creates a new spawner explode event.
     *
     * @param entity the entity that caused the explosion
     * @param location the location where the spawner was destroyed
     * @param quantity the quantity of the spawner
     * @param exploded whether the spawner was successfully exploded
     * @param entityType the spawned entity type of the exploded spawner
     */
    public SpawnerExplodeEvent(Entity entity, Location location, int quantity, boolean exploded,
                               @Nullable EntityType entityType) {
        super(entity, location, quantity, entityType);
        this.exploded = exploded;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}