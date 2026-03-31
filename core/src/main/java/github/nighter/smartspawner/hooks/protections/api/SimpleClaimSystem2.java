package github.nighter.smartspawner.hooks.protections.api;

import java.util.Optional;

import fr.xyness.SimpleClaimSystem.API.SCS_API;
import fr.xyness.SimpleClaimSystem.API.SCS_API_Provider;
import fr.xyness.SimpleClaimSystem.Types.Claim;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SimpleClaimSystem2 {
    private static final SCS_API api = SCS_API_Provider.get();

    public static boolean canPlayerBreakClaimBlock(@NotNull final Player player, @NotNull Location location) {
        if (api == null) return true;
        Optional<Claim> claim = api.getClaim(player.getLocation().getChunk());
        if(claim.isPresent()) {
            Claim c = claim.get();
            boolean canDestroy = c.getPermission(c.getRole(player.getUniqueId()), "destroy_block");
            if(canDestroy) {
                boolean canDestroySpawners = c.getPermission(c.getRole(player.getUniqueId()), "destroy_spawners");
                return canDestroySpawners;
            }
            return canDestroy;
        }
        return true;
    }

    public static boolean canPlayerStackClaimBlock(@NotNull final Player player, @NotNull Location location) {
        if (api == null) return true;
        Optional<Claim> claim = api.getClaim(player.getLocation().getChunk());
        if(claim.isPresent()) {
            Claim c = claim.get();
            boolean canInteract = c.getPermission(c.getRole(player.getUniqueId()), "interact_spawner");
            return canInteract;
        }
        return true;
    }

    public static boolean canPlayerOpenMenuOnClaim(@NotNull final Player player, @NotNull Location location) {
        if (api == null) return true;
        Optional<Claim> claim = api.getClaim(player.getLocation().getChunk());
        if(claim.isPresent()) {
            Claim c = claim.get();
            boolean canInteract = c.getPermission(c.getRole(player.getUniqueId()), "interact_spawner");
            return canInteract;
        }
        return true;
    }
}