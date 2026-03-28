package github.nighter.smartspawner.hooks.protections.api;

import net.william278.huskclaims.api.BukkitHuskClaimsAPI;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.libraries.cloplib.operation.Operation;
import net.william278.huskclaims.libraries.cloplib.operation.OperationType;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.user.OnlineUser;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HuskClaims {
    public static boolean canPlayerBreakBlock(@NotNull Player player, @NotNull Location location) {
        return check(player, location, OperationType.BLOCK_BREAK);
    }

    public static boolean canPlayerStackBlock(@NotNull Player player, @NotNull Location location) {
        return check(player, location, OperationType.BLOCK_INTERACT);
    }

    public static boolean canPlayerOpenMenu(@NotNull Player player, @NotNull Location location) {
        return check(player, location, OperationType.BLOCK_INTERACT);
    }

    private static boolean check(Player player, Location location, OperationType operationType) {
        OnlineUser user = BukkitHuskClaimsAPI.getInstance().getOnlineUser(player);
        if (user == null) return true;
        Position position = BukkitHuskClaimsAPI.getInstance().getPosition(location);
        Claim claim = BukkitHuskClaimsAPI.getInstance().getClaimAt(position).orElse(null);
        if (claim == null) return true;
        return BukkitHuskClaimsAPI.getInstance().isOperationAllowed(Operation.of(user, operationType, position, true));
    }
}
