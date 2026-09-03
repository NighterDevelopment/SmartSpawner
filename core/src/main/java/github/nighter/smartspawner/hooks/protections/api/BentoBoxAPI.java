package github.nighter.smartspawner.hooks.protections.api;

import github.nighter.smartspawner.hooks.protections.ProtectionHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.lists.Flags;

public class BentoBoxAPI implements ProtectionHook {

    // No canBreak override: BentoBox does not police spawner breaking (defaults to allow).

    @Override
    public boolean canStack(@NotNull Player player, @NotNull Location location) {
        if(BentoBox.getInstance().getIslandsManager().getIslandAt(location).isEmpty()) return true;
        return BentoBox.getInstance().getIslandsManager().getIslandAt(location).
                map(island -> island.isAllowed(User.getInstance(player.getUniqueId()), Flags.PLACE_BLOCKS)).
                orElse(Flags.PLACE_BLOCKS.isSetForWorld(location.getWorld()));
    }

    @Override
    public boolean canOpenMenu(@NotNull Player player, @NotNull Location location) {
        if(BentoBox.getInstance().getIslandsManager().getIslandAt(location).isEmpty()) return true;
        return BentoBox.getInstance().getIslandsManager().getIslandAt(location).
                map(island -> island.isAllowed(User.getInstance(player.getUniqueId()), Flags.CONTAINER)).
                orElse(Flags.CONTAINER.isSetForWorld(location.getWorld()));
    }
}
