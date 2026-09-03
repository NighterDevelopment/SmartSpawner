package github.nighter.smartspawner.hooks.protections;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * One optional claim/region/land plugin, adapted to SmartSpawner's three spawner actions.
 *
 * <p>Every method returns {@code true} to <b>allow</b> and {@code false} to <b>deny</b>, and every
 * method defaults to allow. A hook only overrides the actions it actually guards: leaving a method at
 * its default is how a plugin opts out of an action. That is why most skyblock hooks do not override
 * {@link #canBreak} &mdash; they never policed block breaking, and the default keeps it that way.</p>
 *
 * <p>Instances are created only while their plugin is confirmed present, inside
 * {@code IntegrationManager}'s detection, and collected into
 * {@code IntegrationManager.getProtectionHooks()}. Each {@code Check*} entry point iterates that list
 * and fails closed on the first denial. Because an instance exists only when its plugin is loaded, an
 * implementation may freely reference that plugin's classes.</p>
 */
public interface ProtectionHook {

    /** @return true if the player may break the spawner at this location. */
    default boolean canBreak(Player player, Location location) {
        return true;
    }

    /** @return true if the player may stack or place the spawner at this location. */
    default boolean canStack(Player player, Location location) {
        return true;
    }

    /** @return true if the player may open the spawner GUI at this location. */
    default boolean canOpenMenu(Player player, Location location) {
        return true;
    }
}
