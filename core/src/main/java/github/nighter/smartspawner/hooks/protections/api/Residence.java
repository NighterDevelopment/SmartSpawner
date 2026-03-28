package github.nighter.smartspawner.hooks.protections.api;

import com.bekvon.bukkit.residence.api.ResidenceApi;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

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
        Map<String, Boolean> flags = claimedResidence.getPermissions().getPlayerFlags(player.getUniqueId());
        if (flags == null) return true;
        for (String flag : flags.keySet()) {
            if (flag.equalsIgnoreCase(flagName) && flags.get(flag))
                return true;
        }
        return false;
    }
}
