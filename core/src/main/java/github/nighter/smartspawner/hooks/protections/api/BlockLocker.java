package github.nighter.smartspawner.hooks.protections.api;

import github.nighter.smartspawner.hooks.protections.ProtectionHook;
import nl.rutgerkok.blocklocker.BlockLockerAPI;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

// BlockLocker (nl.rutgerkok) protection hook
public class BlockLocker implements ProtectionHook {

    @Override
    public boolean canOpenMenu(@NotNull Player player, @NotNull Location location) {
        return hasAccess(player, location);
    }

    @Override
    public boolean canStack(@NotNull Player player, @NotNull Location location) {
        return hasAccess(player, location);
    }

    @Override
    public boolean canBreak(@NotNull Player player, @NotNull Location location) {
        return hasAccess(player, location);
    }

    private static boolean hasAccess(@NotNull Player player, @NotNull Location location) {
        try {
            Block block = location.getBlock();

            // Not protected: leave it to the other checks
            if (!BlockLockerAPI.isProtected(block)) {
                return true;
            }

            // allowBypass = true so the BlockLocker bypass permission is respected
            return BlockLockerAPI.isAllowed(player, block, true);
        } catch (Exception e) {
            // On any API error, don't block the player
            return true;
        }
    }
}
