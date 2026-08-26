package github.nighter.smartspawner.hooks.protections.api;

import dev.kitteh.factions.Board;
import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.permissible.PermissibleAction;
import dev.kitteh.factions.permissible.PermissibleActions;

import github.nighter.smartspawner.hooks.protections.ProtectionHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

// FactionsUUID (dev.kitteh) protection hook
public class Factions implements ProtectionHook {

    @Override
    public boolean canOpenMenu(@NotNull Player player, @NotNull Location location) {
        return hasAccess(player, location, PermissibleActions.CONTAINER);
    }

    @Override
    public boolean canBreak(@NotNull Player player, @NotNull Location location) {
        return hasAccess(player, location, PermissibleActions.DESTROY);
    }

    @Override
    public boolean canStack(@NotNull Player player, @NotNull Location location) {
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
