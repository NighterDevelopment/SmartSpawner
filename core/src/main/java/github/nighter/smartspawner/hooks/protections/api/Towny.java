package github.nighter.smartspawner.hooks.protections.api;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;

import github.nighter.smartspawner.hooks.protections.ProtectionHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Towny implements ProtectionHook {

    @Override
    public boolean canBreak(@NotNull Player player, @NotNull Location location) {
        return canPlayerInteractSpawner(player, location);
    }

    @Override
    public boolean canStack(@NotNull Player player, @NotNull Location location) {
        return canPlayerInteractSpawner(player, location);
    }

    @Override
    public boolean canOpenMenu(@NotNull Player player, @NotNull Location location) {
        return canPlayerInteractSpawner(player, location);
    }

    // Check if player has a resident in the location
    private static boolean canPlayerInteractSpawner(@NotNull Player player, @NotNull Location location) {

        Town town = null;
        try {
            town = TownyAPI.getInstance().getTownBlock(location).getTown();
        } catch (NotRegisteredException | NullPointerException e) {
            /* Not in a town so allow break */
            return true;
        }

        try {
            Resident resident = TownyAPI.getInstance().getResident(player.getUniqueId());
            return town.hasResident(resident) || town.hasTrustedResident(resident);
        } catch (Exception e) {
            return true;
        }
    }
}
