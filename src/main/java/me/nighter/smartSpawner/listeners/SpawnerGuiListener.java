package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.*;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.managers.SpawnerManager;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.holders.SpawnerMenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

public class SpawnerGuiListener implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerManager spawnerManager;
    private BukkitTask updateTask;
    private boolean isTaskRunning = false;

    public SpawnerGuiListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
        this.spawnerManager = plugin.getSpawnerManager();
        startUpdateTask();
    }

    private void startUpdateTask() {
        if (isTaskRunning) return;
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Map<UUID, SpawnerData> guis = spawnerManager.getOpenSpawnerGuis();
            if (guis.isEmpty()) {
                stopUpdateTask();
                return;
            }

            for (Map.Entry<UUID, SpawnerData> entry : guis.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                SpawnerData spawner = entry.getValue();

                // Kiểm tra null và inventory đang mở
                if (player != null && player.isOnline()) {
                    Inventory openInv = player.getOpenInventory().getTopInventory();
                    if (openInv.getHolder() instanceof SpawnerMenuHolder) {
                        spawnerManager.updateSpawnerGui(player, spawner, false);
                        //configManager.debug("Updated GUI for " + player.getName());
                    } else {
                        // Nếu inventory không còn mở, remove khỏi tracking
                        spawnerManager.untrackOpenGui(entry.getKey());
                    }
                } else {
                    // Nếu player offline, remove khỏi tracking
                    spawnerManager.untrackOpenGui(entry.getKey());
                }
            }
        }, 10L, 10L);

        isTaskRunning = true;
    }

    public void stopUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        isTaskRunning = false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof SpawnerMenuHolder ) {
            SpawnerMenuHolder holder = (SpawnerMenuHolder) event.getInventory().getHolder();
            spawnerManager.trackOpenGui(event.getPlayer().getUniqueId(), holder.getSpawnerData());

            // Chỉ start task khi GUI đầu tiên được mở
            if (!isTaskRunning) {
                startUpdateTask();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SpawnerMenuHolder) {
            spawnerManager.untrackOpenGui(event.getPlayer().getUniqueId());
            // Kiểm tra nếu không còn GUI nào mở thì dừng task
            if (spawnerManager.getOpenSpawnerGuis().isEmpty()) {
                stopUpdateTask();
            }
        }
    }

    public void onDisable() {
        stopUpdateTask();
    }
}
