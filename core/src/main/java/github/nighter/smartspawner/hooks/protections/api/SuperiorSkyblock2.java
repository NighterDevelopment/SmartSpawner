package github.nighter.smartspawner.hooks.protections.api;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SuperiorSkyblock2 implements Listener {

    private static final String SPAWNER_STACK_PERM = "spawner_stack";
    private static final String SPAWNER_OPEN_MENU_PERM = "spawner_open_menu";
    private static IslandPrivilege SPAWNER_STACK, SPAWNER_OPEN_MENU;
    private static boolean registered = false;

    public SuperiorSkyblock2() {
        register();
    }

    public static void register() {
        if (registered)
            return;

        try {
            SPAWNER_STACK = IslandPrivilege.getByName(SPAWNER_STACK_PERM);
            SPAWNER_OPEN_MENU = IslandPrivilege.getByName(SPAWNER_OPEN_MENU_PERM);
        } catch(NullPointerException e) {
            IslandPrivilege.register(SPAWNER_STACK_PERM);
            IslandPrivilege.register(SPAWNER_OPEN_MENU_PERM);
            try {
                SPAWNER_STACK = IslandPrivilege.getByName(SPAWNER_STACK_PERM);
                SPAWNER_OPEN_MENU = IslandPrivilege.getByName(SPAWNER_OPEN_MENU_PERM);
            } catch(Exception ex) {
                SmartSpawner.getInstance().getLogger().severe("Failed to register SuperiorSkyblock Hook - please open a issue on Github or on Discord");
                e.printStackTrace();
                return;
            }
        }
        registered = true;
    }

    public static boolean canPlayerStackBlock(@NotNull Player player, @NotNull Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);
        if (island != null) {
            return !island.hasPermission(SuperiorSkyblockAPI.getPlayer(player.getUniqueId()), SPAWNER_STACK);
        }
        // Player is not in island
        return false;
    }

    public static boolean canPlayerOpenMenu(@NotNull Player player, @NotNull Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);
        if (island != null) {
            return !island.hasPermission(SuperiorSkyblockAPI.getPlayer(player.getUniqueId()), SPAWNER_OPEN_MENU);
        }
        // Player is not in island
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIslandDisband(IslandDisbandEvent event) {
        if(event.isCancelled() || event.getIsland() == null) return;
        Island island = event.getIsland();
        for(Chunk chunk : island.getAllChunks()) {
            for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.SPAWNER, false)) {
                SpawnerData spawner = SmartSpawner.getInstance().getSpawnerManager().getSpawnerByLocation(state.getBlock().getLocation());
                if(spawner == null) continue;
                SmartSpawner.getInstance().getSpawnerManager().removeGhostSpawner(spawner.getSpawnerId());
            }
        }
    }
}
