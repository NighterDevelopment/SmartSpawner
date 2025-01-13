package me.nighter.smartSpawner.hooks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class WorldGuardAPI {
    // Check if player can break in a location
    public static boolean canPlayerBreakBlockInRegion(@NotNull UUID pUUID, @NotNull org.bukkit.Location location) {
        Player player = Bukkit.getServer().getPlayer(pUUID);
        if (player != null && (player.isOp() || player.hasPermission("worldguard.region.bypass"))) {
            return true;
        }

        LocalPlayer localPlayer = player != null ? WorldGuardPlugin.inst().wrapPlayer(player) : WorldGuardPlugin.inst().wrapOfflinePlayer(Bukkit.getServer().getOfflinePlayer(pUUID));
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        StateFlag[] condition = new StateFlag[]{Flags.BLOCK_BREAK};
        return query.testBuild(loc, localPlayer, condition);
    }

    // Check if player can place in a location
    public static boolean canPlayerPlaceBlockInRegion(@NotNull UUID pUUID, @NotNull org.bukkit.Location location) {
        Player player = Bukkit.getServer().getPlayer(pUUID);
        if (player != null) {
            if (player.isOp() || player.hasPermission("worldguard.region.bypass")) {
                return true;
            }
        } else {
            player = Bukkit.getServer().getOfflinePlayer(pUUID).getPlayer();
        }

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapOfflinePlayer(player);
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        StateFlag[] condition = new StateFlag[]{Flags.BLOCK_PLACE};
        return query.testBuild(loc, localPlayer, condition);
    }

    // Check if player can build in a location
    public static boolean canPlayerBuildInRegion(@NotNull Player p, org.bukkit.Location location) {
        if (p.isOp() || p.hasPermission("worldguard.region.bypass")) {
            return true;
        }

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(p);
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        StateFlag[] condition = new StateFlag[]{Flags.BLOCK_BREAK};
        return query.testBuild(loc, localPlayer, condition);
    }

    // Check if player can interact in a location
    public static boolean canPlayerInteractInRegion(@NotNull Player p, org.bukkit.Location location) {
        if (p.isOp() || p.hasPermission("worldguard.region.bypass")) {
            return true;
        }
        
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(p);
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        StateFlag[] condition = new StateFlag[]{Flags.INTERACT};
        return query.testBuild(loc, localPlayer, condition);
    }

    public static boolean isPlayerInRegion(Player p, String name) {
        if (p == null || p.getLocation() == null || p.getWorld() == null) {
            return false;
        }
        Location loc = BukkitAdapter.adapt(p.getLocation());
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(p.getWorld()));

        if (regions == null) {
            return false;
        }

        ApplicableRegionSet set = regions.getApplicableRegions(loc.toVector().toBlockPoint());

        for (ProtectedRegion region : set) {
            if (region.getId().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}