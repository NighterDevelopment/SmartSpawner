package github.nighter.smartspawner.api.data;

/**
 * Interface for accessing and modifying spawner data through the API.
 * This provides controlled access to spawner properties.
 * All properties can be modified except stackSize which is read-only.
 * After modifications, call {@link #applyChanges()} to recalculate and apply values.
 */
public interface SpawnerDataModifier {

    /**
     * Gets the spawner's current stack size.
     * Note: Stack size is read-only and cannot be modified through this interface.
     *
     * @return current stack size
     */
    int getStackSize();

    /**
     * Gets the spawner's maximum stack size.
     *
     * @return maximum stack size
     */
    int getMaxStackSize();

    /**
     * Sets the spawner's maximum stack size.
     *
     * @param maxStackSize new maximum stack size value
     * @return this modifier for method chaining
     */
    SpawnerDataModifier setMaxStackSize(int maxStackSize);

    /**
     * Gets the base maximum storage pages value.
     *
     * @return base maximum storage pages
     */
    int getBaseMaxStoragePages();

    /**
     * Sets the base maximum storage pages value.
     *
     * @param baseMaxStoragePages new base maximum storage pages
     * @return this modifier for method chaining
     */
    SpawnerDataModifier setBaseMaxStoragePages(int baseMaxStoragePages);

    /**
     * Gets the base minimum mobs value.
     *
     * @return base minimum mobs
     */
    int getBaseMinMobs();

    /**
     * Sets the base minimum mobs value.
     *
     * @param baseMinMobs new base minimum mobs
     * @return this modifier for method chaining
     */
    SpawnerDataModifier setBaseMinMobs(int baseMinMobs);

    /**
     * Gets the base maximum mobs value.
     *
     * @return base maximum mobs
     */
    int getBaseMaxMobs();

    /**
     * Sets the base maximum mobs value.
     *
     * @param baseMaxMobs new base maximum mobs
     * @return this modifier for method chaining
     */
    SpawnerDataModifier setBaseMaxMobs(int baseMaxMobs);

    /**
     * Gets the base maximum stored experience value.
     *
     * @return base maximum stored experience
     */
    long getBaseMaxStoredExp();

    /**
     * Sets the base maximum stored experience value.
     *
     * @param baseMaxStoredExp new base maximum stored experience
     * @return this modifier for method chaining
     */
    SpawnerDataModifier setBaseMaxStoredExp(long baseMaxStoredExp);

    /**
     * Gets the base spawner delay value.
     *
     * @return base spawner delay in ticks
     */
    long getBaseSpawnerDelay();

    /**
     * Sets the base spawner delay value.
     *
     * @param baseSpawnerDelay new base spawner delay in ticks
     * @return this modifier for method chaining
     */
    SpawnerDataModifier setBaseSpawnerDelay(long baseSpawnerDelay);

    /**
     * Applies all pending changes and recalculates spawner values.
     * This method should be called after making modifications to ensure
     * all changes take effect properly.
     */
    void applyChanges();
}

