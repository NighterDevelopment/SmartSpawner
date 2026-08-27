package github.nighter.smartspawner.hooks.protections.api;

import github.nighter.smartspawner.hooks.protections.ProtectionHook;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * GriefPrevention support with per-action trust levels, mirroring how the plugin's own claim checks
 * work. Breaking and placing a spawner is a build action, so it needs Build trust ({@code /trust});
 * opening a spawner's storage GUI is a container action, so it only needs Container trust
 * ({@code /containertrust}). GriefPrevention's trust is hierarchical - Build grants Inventory and
 * Access, and the owner (and Manage trust) passes every check - so requiring the lower level for the
 * menu lets a container-trusted player use the storage without also being able to break the block.
 */
public class GriefPrevention implements ProtectionHook {

    /** {@code checkPermission} returns null when the player is allowed, or a denial message supplier. */
    private boolean hasTrust(@NotNull Player player, @NotNull Location location, @NotNull ClaimPermission level) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        return claim.checkPermission(player, level, null) == null;
    }

    @Override
    public boolean canBreak(@NotNull Player player, @NotNull Location location) {
        return hasTrust(player, location, ClaimPermission.Build);
    }

    @Override
    public boolean canStack(@NotNull Player player, @NotNull Location location) {
        return hasTrust(player, location, ClaimPermission.Build);
    }

    @Override
    public boolean canOpenMenu(@NotNull Player player, @NotNull Location location) {
        return hasTrust(player, location, ClaimPermission.Inventory);
    }
}
