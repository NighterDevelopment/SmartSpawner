package github.nighter.smartspawner.commands.near;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages per-player spawner highlight sessions.
 * Scans asynchronously and renders BlockDisplay entities (visible only to
 * the requesting player, with glow outline visible through walls).
 */
public class SpawnerHighlightManager implements Listener {

    public static final int MAX_RADIUS = 200;
    // Hard cap on how many highlights can be shown to avoid client-side lag
    private static final int MAX_HIGHLIGHTS = 200;
    // How many ticks highlights stay visible (30 s)
    private static final long HIGHLIGHT_DURATION_TICKS = 30 * 20L;
    // How many ticks the "result" bossbar stays visible after the scan finishes (5 s)
    private static final long BOSSBAR_RESULT_TICKS = 5 * 20L;

    private final SmartSpawner plugin;
    /** One session per online player UUID. */
    private final Map<UUID, ScanSession> activeSessions = new ConcurrentHashMap<>();

    public SpawnerHighlightManager(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Inner session record
    // ──────────────────────────────────────────────────────────────────────────

    private static final class ScanSession {
        final UUID playerUUID;
        final BossBar bossBar;
        /** Highlight entities spawned for this session. CopyOnWriteArrayList for thread safety
         *  since location tasks (add) and cleanup (iterate/clear) run on different threads. */
        final CopyOnWriteArrayList<BlockDisplay> highlights = new CopyOnWriteArrayList<>();
        /** Set to true to abort the async scan or skip finalisation. */
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        /** Task that removes highlights after the expiry delay. */
        volatile Scheduler.Task expiryTask;

        ScanSession(UUID playerUUID, BossBar bossBar) {
            this.playerUUID = playerUUID;
            this.bossBar = bossBar;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Start a new scan for {@code player} in radius {@code radius}.
     * Any previous session for this player is silently cancelled first.
     * Must be called from the main / region thread.
     */
    public void startScan(Player player, int radius) {
        UUID uuid = player.getUniqueId();

        // Cancel and clean up any existing session first
        ScanSession existing = activeSessions.remove(uuid);
        if (existing != null) {
            existing.cancelled.set(true);
            cleanupSession(existing, player);
        }

        // Snapshot the player location synchronously – async access is unsafe
        final Location playerLoc = player.getLocation().clone();
        final String worldName = playerLoc.getWorld().getName();
        final double radiusSq = (double) radius * radius;
        final int finalRadius = radius;

        BossBar bossBar = BossBar.bossBar(
                Component.text("🔍 ᴀɴᴀʟʏᴢɪɴɢ... 0%", NamedTextColor.AQUA),
                0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );

        ScanSession session = new ScanSession(uuid, bossBar);
        activeSessions.put(uuid, session);
        player.showBossBar(bossBar);

        plugin.getMessageService().sendMessage(player, "command_near_scan_start",
                Map.of("{radius}", String.valueOf(radius)));

        // ── Async scan ───────────────────────────────────────────────────────
        Scheduler.runTaskAsync(() -> {
            if (session.cancelled.get()) return;

            Set<SpawnerData> worldSpawners = plugin.getSpawnerManager().getSpawnersInWorld(worldName);

            if (worldSpawners == null || worldSpawners.isEmpty()) {
                Scheduler.runTask(() -> {
                    if (!session.cancelled.get())
                        finalizeScan(player, session, Collections.emptyList(), finalRadius);
                });
                return;
            }

            // Snapshot to avoid ConcurrentModificationException
            List<SpawnerData> snapshot = new ArrayList<>(worldSpawners);
            int total = snapshot.size();
            List<Location> nearby = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                if (session.cancelled.get()) return;

                SpawnerData spawner = snapshot.get(i);
                Location loc = spawner.getSpawnerLocation();
                if (loc == null || loc.getWorld() == null) continue;

                double dx = loc.getX() - playerLoc.getX();
                double dy = loc.getY() - playerLoc.getY();
                double dz = loc.getZ() - playerLoc.getZ();
                if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                    nearby.add(loc.clone());
                    if (nearby.size() >= MAX_HIGHLIGHTS) break;
                }

                // Update bossbar every 50 spawners to minimise overhead
                if (i % 50 == 0 || i == total - 1) {
                    float progress = (float) (i + 1) / total;
                    int pct = (int) (progress * 100);
                    bossBar.name(Component.text("🔍 ᴀɴᴀʟʏᴢɪɴɢ... " + pct + "%", NamedTextColor.AQUA));
                    bossBar.progress(progress);
                }
            }

            if (session.cancelled.get()) return;

            final List<Location> result = nearby;
            Scheduler.runTask(() -> {
                if (!session.cancelled.get())
                    finalizeScan(player, session, result, finalRadius);
            });
        });
    }

    /**
     * Cancel the active scan for {@code player} and remove all highlights.
     * Must be called from the main / region thread.
     */
    public void cancelScan(Player player) {
        ScanSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            plugin.getMessageService().sendMessage(player, "command_near_no_active_scan");
            return;
        }
        session.cancelled.set(true);
        cleanupSession(session, player);
        plugin.getMessageService().sendMessage(player, "command_near_scan_cancelled");
    }

    public boolean hasActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Internal helpers – all called on main thread unless stated otherwise
    // ──────────────────────────────────────────────────────────────────────────

    /** Called on the main thread once the async scan completes. */
    private void finalizeScan(Player player, ScanSession session,
                               List<Location> locations, int radius) {
        if (!player.isOnline()) {
            cleanupSession(session, null);
            return;
        }

        int count = locations.size();

        // Update bossbar to show the final result
        if (count == 0) {
            session.bossBar.name(Component.text(
                    "✗ ɴᴏ ꜱᴘᴀᴡɴᴇʀꜱ ꜰᴏᴜɴᴅ ᴡɪᴛʜɪɴ " + radius + " ʙʟᴏᴄᴋꜱ", NamedTextColor.RED));
            session.bossBar.color(BossBar.Color.RED);
        } else {
            session.bossBar.name(Component.text(
                    "✓ ꜰᴏᴜɴᴅ " + count + " ꜱᴘᴀᴡɴᴇʀ(ꜱ) ᴡɪᴛʜɪɴ " + radius + " ʙʟᴏᴄᴋꜱ", NamedTextColor.GREEN));
            session.bossBar.color(BossBar.Color.GREEN);
        }
        session.bossBar.progress(1f);

        // Spawn highlight entities (main thread)
        for (Location loc : locations) {
            if (session.cancelled.get()) return;
            spawnHighlight(player, session, loc);
        }

        // Chat result message
        String msgKey = count > 0 ? "command_near_scan_found" : "command_near_scan_none";
        plugin.getMessageService().sendMessage(player, msgKey,
                Map.of("{count}", String.valueOf(count), "{radius}", String.valueOf(radius)));

        // Hide bossbar after a short delay – dispatched to player's entity region (Folia)
        Scheduler.runTaskLater(() -> {
            Player p = plugin.getServer().getPlayer(session.playerUUID);
            if (p != null && p.isOnline()) {
                Scheduler.runEntityTask(p, () -> p.hideBossBar(session.bossBar));
            }
        }, BOSSBAR_RESULT_TICKS);

        // Auto-remove highlights after HIGHLIGHT_DURATION
        session.expiryTask = Scheduler.runTaskLater(() -> {
            ScanSession current = activeSessions.get(session.playerUUID);
            if (current != session) return; // a newer session replaced this one
            activeSessions.remove(session.playerUUID);
            session.cancelled.set(true); // stop any in-flight location tasks from spawning
            Player p = plugin.getServer().getPlayer(session.playerUUID);
            cleanupSession(session, p);
            if (p != null && p.isOnline()) {
                plugin.getMessageService().sendMessage(p, "command_near_highlights_expired");
            }
        }, HIGHLIGHT_DURATION_TICKS);
    }

    /**
     * Schedules BlockDisplay spawning on the chunk's region thread (Folia-safe),
     * then shows it to the player on their entity region thread.
     */
    private void spawnHighlight(Player player, ScanSession session, Location loc) {
        // world.spawn() must run on the region thread that owns this chunk in Folia
        Scheduler.runLocationTask(loc, () -> {
            if (session.cancelled.get() || !player.isOnline()) return;
            World world = loc.getWorld();
            if (world == null) return;

            Location spawnLoc = loc.getBlock().getLocation();

            BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, bd -> {
                bd.setBlock(Material.SPAWNER.createBlockData());
                bd.setGlowing(true);
                bd.setVisibleByDefault(false);
                bd.setPersistent(false);
                bd.setBrightness(new Display.Brightness(15, 15));
            });

            session.highlights.add(display);

            // player.showEntity() must run on the player's entity region in Folia
            Scheduler.runEntityTask(player, () -> {
                if (player.isOnline()) player.showEntity(plugin, display);
            });
        });
    }

    /**
     * Removes bossbar and all highlight entities for {@code session}.
     * {@code player} may be {@code null} if they already disconnected.
     * Entity removal is dispatched to each entity's region thread (Folia-safe).
     */
    private void cleanupSession(ScanSession session, Player player) {
        if (player != null && player.isOnline()) {
            // hideBossBar must run on the player's entity region in Folia
            final Player p = player;
            Scheduler.runEntityTask(player, () -> p.hideBossBar(session.bossBar));
        }
        if (session.expiryTask != null) {
            session.expiryTask.cancel();
            session.expiryTask = null;
        }
        // bd.remove() must run on each entity's region thread in Folia
        List<BlockDisplay> copy = new ArrayList<>(session.highlights);
        session.highlights.clear();
        for (BlockDisplay bd : copy) {
            if (bd.isValid()) {
                Scheduler.runEntityTask(bd, () -> {
                    if (bd.isValid()) bd.remove();
                });
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Event listener
    // ──────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ScanSession session = activeSessions.remove(event.getPlayer().getUniqueId());
        if (session == null) return;
        session.cancelled.set(true);
        // Pass null – bossbar disappears automatically on disconnect
        cleanupSession(session, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Plugin lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    /** Called when the plugin is disabled to tear down all active sessions. */
    public void cleanup() {
        for (ScanSession session : activeSessions.values()) {
            session.cancelled.set(true);
            if (session.expiryTask != null) session.expiryTask.cancel();
            for (BlockDisplay bd : session.highlights) {
                if (bd.isValid()) bd.remove();
            }
            session.highlights.clear();
        }
        activeSessions.clear();
    }
}
