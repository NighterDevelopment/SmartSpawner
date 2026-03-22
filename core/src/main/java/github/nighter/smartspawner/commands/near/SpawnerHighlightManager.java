package github.nighter.smartspawner.commands.near;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
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

    public static final int MAX_RADIUS = 10000;
    // Hard cap on how many highlights can be shown to avoid client-side lag
    private static final int MAX_HIGHLIGHTS = 10000;
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
        /** Snapshot of SpawnerData found during the scan – used by the near-result GUI. */
        volatile List<SpawnerData> scannedSpawners = Collections.emptyList();

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
                Component.text(plugin.getLanguageManager().getCommandConfig(
                        "near.bossbar.analyzing", "\uD83D\uDD0D \u1D00\u0274\u1D00\u029F\u028F\u1D22\u026A\u0274\u0262... {percent}%",
                        Map.of("percent", "0")), NamedTextColor.AQUA),
                0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );

        ScanSession session = new ScanSession(uuid, bossBar);
        activeSessions.put(uuid, session);
        player.showBossBar(bossBar);
        player.updateCommands(); // reveal cancel/gui in tab-completion

        plugin.getMessageService().sendMessage(player, "near.scan_start",
                Map.of("radius", String.valueOf(radius)));

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
            List<SpawnerData> nearby = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                if (session.cancelled.get()) return;

                SpawnerData spawner = snapshot.get(i);
                Location loc = spawner.getSpawnerLocation();
                if (loc == null || loc.getWorld() == null) continue;

                double dx = loc.getX() - playerLoc.getX();
                double dy = loc.getY() - playerLoc.getY();
                double dz = loc.getZ() - playerLoc.getZ();
                if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                    nearby.add(spawner);
                    if (nearby.size() >= MAX_HIGHLIGHTS) break;
                }

                // Update bossbar every 50 spawners to minimise overhead
                if (i % 50 == 0 || i == total - 1) {
                    float progress = (float) (i + 1) / total;
                    int pct = (int) (progress * 100);
                    bossBar.name(Component.text(plugin.getLanguageManager().getCommandConfig(
                            "near.bossbar.analyzing", "\uD83D\uDD0D \u1D00\u0274\u1D00\u029F\u028F\u1D22\u026A\u0274\u0262... {percent}%",
                            Map.of("percent", String.valueOf(pct))), NamedTextColor.AQUA));
                    bossBar.progress(progress);
                }
            }

            if (session.cancelled.get()) return;

            final List<SpawnerData> result = nearby;
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
            plugin.getMessageService().sendMessage(player, "near.no_active_scan");
            return;
        }
        session.cancelled.set(true);
        cleanupSession(session, player);
        player.updateCommands(); // hide cancel/gui from tab-completion
        plugin.getMessageService().sendMessage(player, "near.scan_cancelled");
    }

    public boolean hasActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Internal helpers – all called on main thread unless stated otherwise
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable snapshot of the spawners from the player's active session,
     * or an empty list if no session exists.
     */
    public List<SpawnerData> getSessionSpawners(UUID uuid) {
        ScanSession session = activeSessions.get(uuid);
        return session != null ? Collections.unmodifiableList(session.scannedSpawners) : Collections.emptyList();
    }

    /** Called on the main thread once the async scan completes. */
    private void finalizeScan(Player player, ScanSession session,
                               List<SpawnerData> spawners, int radius) {
        if (!player.isOnline()) {
            cleanupSession(session, null);
            return;
        }

        int count = spawners.size();

        // Store the result in the session so the GUI can access it later
        session.scannedSpawners = new ArrayList<>(spawners);

        // Update bossbar to show the final result
        if (count == 0) {
            session.bossBar.name(Component.text(plugin.getLanguageManager().getCommandConfig(
                    "near.bossbar.not_found", "\u2717 \u0274\u1D0F \u0280\u1D18\u1D00\u1D21\u0274\u1D07\u0280\u0280 \uA730\u1D0F\u1D1C\u0274\u1D05 \u1D21\u026A\u1D1B\u029C\u026A\u0274 {radius} \u0299\u029F\u1D0F\u1D04\u1D0B\u0280",
                    Map.of("radius", String.valueOf(radius))), NamedTextColor.RED));
            session.bossBar.color(BossBar.Color.RED);
        } else {
            session.bossBar.name(Component.text(plugin.getLanguageManager().getCommandConfig(
                    "near.bossbar.found", "\u2713 \uA730\u1D0F\u1D1C\u0274\u1D05 {count} \u0280\u1D18\u1D00\u1D21\u0274\u1D07\u0280(\u0280) \u1D21\u026A\u1D1B\u029C\u026A\u0274 {radius} \u0299\u029F\u1D0F\u1D04\u1D0B\u0280",
                    Map.of("count", String.valueOf(count), "radius", String.valueOf(radius))), NamedTextColor.GREEN));
            session.bossBar.color(BossBar.Color.GREEN);
        }
        session.bossBar.progress(1f);

        // Spawn highlight entities (main thread)
        for (SpawnerData spawner : spawners) {
            if (session.cancelled.get()) return;
            Location loc = spawner.getSpawnerLocation();
            if (loc != null) spawnHighlight(player, session, loc);
        }

        // Chat result message – for the found case, append the view_gui button on the same line
        Map<String, String> resultPlaceholders = Map.of("count", String.valueOf(count), "radius", String.valueOf(radius));
        if (count > 0) {
            // Deserialise scan_found to a Component so we can append inline
            String foundMsg = plugin.getLanguageManager().getMessage("near.scan_found", resultPlaceholders);
            Component textPart = (foundMsg != null && !foundMsg.startsWith("Missing"))
                    ? LegacyComponentSerializer.legacySection().deserialize(foundMsg)
                    : Component.empty();

            Component viewGuiHint = Component.text()
                    .append(Component.text(
                            plugin.getLanguageManager().getCommandConfig("near.view_gui.button", "[\u1D20\u026A\u1D07\u1D21 \u026A\u0274 \u0262\u1D1C\u026A]"),
                            NamedTextColor.GOLD)
                            .clickEvent(ClickEvent.callback(audience -> {
                                if (audience instanceof Player clickPlayer) {
                                    plugin.getNearResultGUI().openNearResultGUI(clickPlayer, 1);
                                }
                            }))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text(plugin.getLanguageManager().getCommandConfig(
                                            "near.view_gui.hover",
                                            "Click to browse the {count} spawner(s) found nearby",
                                            Map.of("count", String.valueOf(count))),
                                            NamedTextColor.GRAY))))
                    .build();

            player.sendMessage(Component.text().append(textPart).append(viewGuiHint).build());

            // Play the scan_found sound (normally done by MessageService)
            String soundKey = plugin.getLanguageManager().getSound("near.scan_found");
            if (soundKey != null) {
                try {
                    player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
        } else {
            plugin.getMessageService().sendMessage(player, "near.scan_none", resultPlaceholders);
        }

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
                p.updateCommands(); // hide cancel/gui from tab-completion
                plugin.getMessageService().sendMessage(p, "near.highlights_expired");
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
                // bd.setBrightness(new Display.Brightness(15, 15));
                bd.setGlowing(true);
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
