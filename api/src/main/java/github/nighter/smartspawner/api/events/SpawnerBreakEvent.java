package github.nighter.smartspawner.api.events;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when a spawner is broken by a player or explosion.
 */
@Getter
public class SpawnerBreakEvent extends SpawnerEvent {

    private static final HandlerList handlers = new HandlerList();

    private final Entity entity;
    private final EntityType entityType;

    /**
     * Creates a new spawner break event.
     *
     * @param entity the entity that broke the spawner
     * @param location the location of the broken spawner
     * @param quantity the quantity of the broken spawner
     */
    public SpawnerBreakEvent(Entity entity, Location location, int quantity) {
        this(entity, location, quantity, null);
    }

    /**
     * Creates a new spawner break event.
     *
     * @param entity the entity that broke the spawner
     * @param location the location of the broken spawner
     * @param quantity the quantity of the broken spawner
     * @param entityType the spawned entity type of the broken spawner (nullable for item spawners)
     */
    public SpawnerBreakEvent(Entity entity, Location location, int quantity, @Nullable EntityType entityType) {
        super(location, quantity);
        this.entity = entity;
        this.entityType = entityType;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}