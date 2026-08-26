package github.nighter.smartspawner.hooks.protections.api;

import github.nighter.smartspawner.hooks.protections.ProtectionHook;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GriefPrevention implements ProtectionHook {

    @Override
    public boolean canBreak(@NotNull Player player, @NotNull Location location) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        return claim.allowBreak(player, player.getLocation().getBlock().getType()) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }

    @Override
    public boolean canStack(@NotNull Player player, @NotNull Location location) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        return claim.allowBuild(player, player.getLocation().getBlock().getType()) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }

    @Override
    public boolean canOpenMenu(@NotNull Player player, @NotNull Location location) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        return claim.allowContainers(player) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }
}
