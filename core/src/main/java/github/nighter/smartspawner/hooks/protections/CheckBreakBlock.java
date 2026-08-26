package github.nighter.smartspawner.hooks.protections;

import github.nighter.smartspawner.SmartSpawner;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CheckBreakBlock {
    public static boolean CanPlayerBreakBlock(@NotNull final Player player, @NotNull Location location) {
        if (player.isOp() || player.hasPermission("*")) return true;

        for (ProtectionHook hook : SmartSpawner.getInstance().getIntegrationManager().getProtectionHooks()) {
            if (!hook.canBreak(player, location)) return false;
        }
        return true;
    }
}
