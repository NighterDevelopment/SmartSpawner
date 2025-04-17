package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public class SpawnerExplodeEvent extends SpawnerBreakEvent {
    private final boolean exploded;
    /**
     * The constructor for the event.
     *
     * @param entity   The entity who explode the spawner
     * @param location The location where the spawner was exploded.
     * @param quantity The quantity of the spawner.
     */
    public SpawnerExplodeEvent(Entity entity, Location location, int quantity, boolean exploded) {
        super(entity, location, quantity);
        this.exploded = exploded;
    }

    public boolean isExploded() {
        return exploded;
    }
}
