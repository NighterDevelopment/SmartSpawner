package github.nighter.smartspawner.hooks.protections;

import github.nighter.smartspawner.SmartSpawner;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CheckOpenMenu {
    public static boolean CanPlayerOpenMenu(@NotNull final Player player, @NotNull Location location) {
        if (player.isOp() || player.hasPermission("*")) return true;

        for (ProtectionHook hook : SmartSpawner.getInstance().getIntegrationManager().getProtectionHooks()) {
            if (!hook.canOpenMenu(player, location)) return false;
        }
        return true;
    }
}
