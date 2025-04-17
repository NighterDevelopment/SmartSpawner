package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * SpawnerBreakEvent is called when a spawner being broke by a player or an explosion.
 */
public class SpawnerBreakEvent extends SpawnerEvent {
    private final Entity entity;

    /**
     * The constructor for the event.
     *
     * @param entity         The entity who broke the spawner.
     * @param location       The location of the spawner broken.
     * @param quantity       The quantity of the spawner broken.
     */
    public SpawnerBreakEvent(Entity entity, Location location, int quantity) {
        super(location, quantity);
        this.entity = entity;
    }

    /**
     * The entity who broke the spawner.
     */
    public Entity getEntity() {
        return entity;
    }
}
