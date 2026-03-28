package github.nighter.smartspawner.hooks.protections.api;

import com.bekvon.bukkit.residence.api.ResidenceApi;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Residence {
    public static boolean canPlayerBreakBlock(@NotNull Player player, @NotNull Location location) {
        return check(player, location, "build");
    }

    public static boolean canInteract(@NotNull Player player, @NotNull Location location) {
        return check(player, location, "use");
    }

    public static boolean canStack(@NotNull Player player, @NotNull Location location) {
        return check(player, location, "build");
    }

    private static boolean check(Player player, Location location, String flagName) {
        ClaimedResidence claimedResidence = ResidenceApi.getResidenceManager().getByLoc(location);
        if (claimedResidence == null) return true;
        FlagPermissions perms = claimedResidence.getPermissions();
        if (perms == null) return true;
        try {
            boolean globalDefault = perms.has(Flags.valueOf(flagName), true);
            return perms.playerHas(player, Flags.valueOf(flagName), globalDefault);
        } catch (IllegalArgumentException ex) {
            return true;
        }
    }
}
