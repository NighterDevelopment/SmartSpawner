package github.nighter.smartspawner.hooks.protections.api;

import dev.kitteh.factions.Board;
import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.permissible.PermissibleAction;
import dev.kitteh.factions.permissible.PermissibleActions;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

// FactionsUUID (dev.kitteh) protection hook
public class Factions {

    public static boolean canPlayerOpenMenu(@NotNull Player player, @NotNull Location location) {
        return hasAccess(player, location, PermissibleActions.CONTAINER);
    }

    public static boolean canPlayerBreakClaimBlock(@NotNull Player player, @NotNull Location location) {
        return hasAccess(player, location, PermissibleActions.DESTROY);
    }

    public static boolean canPlayerStackClaimBlock(@NotNull Player player, @NotNull Location location) {
        return hasAccess(player, location, PermissibleActions.BUILD);
    }

    private static boolean hasAccess(@NotNull Player player, @NotNull Location location, @NotNull PermissibleAction action) {
        try {
            FLocation fLocation = new FLocation(location);
            Faction faction = Board.board().factionAt(fLocation);

            // Wilderness, safezone or warzone: leave it to the other checks
            if (faction == null || !faction.isNormal()) {
                return true;
            }

            FPlayer fPlayer = FPlayers.fPlayers().get(player.getUniqueId());
            return faction.hasAccess(fPlayer, action, fLocation);
        } catch (Exception e) {
            // On any API error, don't block the player
            return true;
        }
    }
}
