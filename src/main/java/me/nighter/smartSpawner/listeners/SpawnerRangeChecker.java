package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.SpawnerManager;
import me.nighter.smartSpawner.utils.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerRangeChecker implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final SpawnerManager spawnerManager;
    private final Map<String, BukkitTask> spawnerTasks;
    private final Map<String, Set<UUID>> playersInRange;

    public SpawnerRangeChecker(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerTasks = new ConcurrentHashMap<>();
        this.playersInRange = new ConcurrentHashMap<>();

        // Start the range checking task
        startRangeCheckTask();
    }

    private void startRangeCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (SpawnerData spawner : spawnerManager.getAllSpawners()) {
                updateSpawnerStatus(spawner);
            }
        }, 20L, 20L); // Check every second
    }

    private void updateSpawnerStatus(SpawnerData spawner) {
        // 1. Khởi tạo các thông số cần thiết
        Location spawnerLoc = spawner.getSpawnerLocation();
        World world = spawnerLoc.getWorld();
        if (world == null) return;

        int range = spawner.getSpawnerRange();
        double rangeSquared = range * range; // Sử dụng bình phương khoảng cách để tối ưu (không cần tính căn)
        boolean playerFound = false;

        // 2. Tính toán radius theo chunk
        // range >> 4 tương đương với range / 16 (vì 1 chunk = 16 blocks)
        // +1 để đảm bảo không bỏ sót chunk biên
        int chunkRadius = (range >> 4) + 1;

        // 3. Lấy chunk coordinates của spawner
        // spawnerLoc.getBlockX() >> 4 tương đương với chia cho 16 để lấy chunk coordinate
        int baseX = spawnerLoc.getBlockX() >> 4;
        int baseZ = spawnerLoc.getBlockZ() >> 4;

        // 4. Kiểm tra từng chunk trong vùng radius
        chunkCheck: // Label để break khi tìm thấy player
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                // Chỉ kiểm tra chunk đã được load
                if (world.isChunkLoaded(baseX + dx, baseZ + dz)) {
                    // Lấy tất cả entity trong vùng radius
                    // Sử dụng getNearbyEntities thay vì getEntities sẽ trả về các entity trong vùng radius (tính theo block)
                    // Cái này sẽ giúp giảm bớt các phép toán không cần thiết
                    Collection<Entity> nearbyEntities = world.getNearbyEntities(spawnerLoc, range, range, range);
                    for (Entity entity : nearbyEntities) {
                        // Kiểm tra nếu là player và trong range
                        if (entity instanceof Player && entity.getLocation().distanceSquared(spawnerLoc) <= rangeSquared) {
                            playerFound = true;
                            break chunkCheck; // Thoát khỏi tất cả vòng lặp khi tìm thấy player
                        }
                    }
                }
            }
        }

        // 5. Cập nhật trạng thái spawner
        boolean shouldStop = !playerFound;
        if (spawner.getSpawnerStop() != shouldStop) {
            spawner.setSpawnerStop(shouldStop);

            if (!shouldStop) {
                startSpawnerTask(spawner);
                updateGuiForSpawner(spawner);
                configManager.debug("Spawner " + spawner.getSpawnerId() + " activated - Player in range");

            } else {
                stopSpawnerTask(spawner);
                updateGuiForSpawner(spawner);
                configManager.debug("Spawner " + spawner.getSpawnerId() + " deactivated - No players in range");
            }
        }
    }

    private void startSpawnerTask(SpawnerData spawner) {
        // Cancel existing task if any
        stopSpawnerTask(spawner);

        // Start new task
        spawner.setLastSpawnTime(System.currentTimeMillis() + spawner.getSpawnDelay());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!spawner.getSpawnerStop()) {
                spawnerManager.spawnLoot(spawner);
            }
        }, 0L, spawner.getSpawnDelay());

        spawnerTasks.put(spawner.getSpawnerId(), task);
    }

    public void stopSpawnerTask(SpawnerData spawner) {
        BukkitTask task = spawnerTasks.remove(spawner.getSpawnerId());
        if (task != null) {
            task.cancel();
        }
    }

    public Set<UUID> getPlayersInRange(String spawnerId) {
        return playersInRange.getOrDefault(spawnerId, Collections.emptySet());
    }

    public void cleanup() {
        // Cancel all tasks on plugin disable
        spawnerTasks.values().forEach(BukkitTask::cancel);
        spawnerTasks.clear();
        playersInRange.clear();
    }

    private void updateGuiForSpawner(SpawnerData spawner) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Map.Entry<UUID, SpawnerData> entry : spawnerManager.getOpenSpawnerGuis().entrySet()) {
                if (entry.getValue().getSpawnerId().equals(spawner.getSpawnerId())) {
                    Player viewer = Bukkit.getPlayer(entry.getKey());
                    if (viewer != null && viewer.isOnline()) {
                        spawnerManager.updateSpawnerGui(viewer, spawner, true);
                    }
                }
            }
        });
    }
}
