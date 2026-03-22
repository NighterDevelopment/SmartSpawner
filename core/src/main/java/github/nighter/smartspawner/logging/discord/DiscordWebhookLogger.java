package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerLogEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Handles sending log entries to Discord via webhooks.
 * Implements rate-limiting and async processing to avoid blocking.
 *
 * <p>Per-event embed appearance is resolved lazily through {@link DiscordEmbedConfigManager}.</p>
 */
public class DiscordWebhookLogger {

    private final SmartSpawner plugin;
    private final DiscordWebhookConfig config;
    private final DiscordEmbedConfigManager embedConfigManager;
    private final ConcurrentLinkedQueue<SpawnerLogEntry> webhookQueue;
    private final AtomicBoolean isShuttingDown;
    private final AtomicLong lastWebhookTime;
    private final AtomicLong webhooksSentThisMinute;
    private Scheduler.Task webhookTask;

    // Discord rate limits: ~30 requests/min per webhook; leave buffer
    private static final int  MAX_REQUESTS_PER_MINUTE = 25;
    private static final long MINUTE_IN_MILLIS         = 60_000L;

    public DiscordWebhookLogger(SmartSpawner plugin,
                                DiscordWebhookConfig config,
                                DiscordEmbedConfigManager embedConfigManager) {
        this.plugin              = plugin;
        this.config              = config;
        this.embedConfigManager  = embedConfigManager;
        this.webhookQueue        = new ConcurrentLinkedQueue<>();
        this.isShuttingDown      = new AtomicBoolean(false);
        this.lastWebhookTime     = new AtomicLong(System.currentTimeMillis());
        this.webhooksSentThisMinute = new AtomicLong(0);

        if (config.isEnabled()) startWebhookTask();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Queue a log entry to be sent to Discord.
     */
    public void queueWebhook(SpawnerLogEntry entry) {
        if (!config.isEnabled() || !config.isEventEnabled(entry.getEventType())) return;
        webhookQueue.offer(entry);
    }

    /**
     * Shutdown the webhook logger.
     */
    public void shutdown() {
        isShuttingDown.set(true);
        if (webhookTask != null) webhookTask.cancel();

        // Attempt a limited flush
        int flushed = 0;
        while (!webhookQueue.isEmpty() && flushed < 10) {
            SpawnerLogEntry entry = webhookQueue.poll();
            if (entry != null) { sendWebhook(entry); flushed++; }
        }
        if (!webhookQueue.isEmpty()) {
            plugin.getLogger().warning("Discord queue had " + webhookQueue.size()
                    + " pending entries at shutdown (flushed " + flushed + ")");
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void startWebhookTask() {
        webhookTask = Scheduler.runTaskTimerAsync(() -> {
            if (!isShuttingDown.get()) processWebhookQueue();
        }, 40L, 40L);
    }

    private void processWebhookQueue() {
        if (webhookQueue.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastWebhookTime.get() >= MINUTE_IN_MILLIS) {
            webhooksSentThisMinute.set(0);
            lastWebhookTime.set(now);
        }

        while (!webhookQueue.isEmpty() && webhooksSentThisMinute.get() < MAX_REQUESTS_PER_MINUTE) {
            SpawnerLogEntry entry = webhookQueue.poll();
            if (entry != null) { sendWebhook(entry); webhooksSentThisMinute.incrementAndGet(); }
        }

        if (webhookQueue.size() > 50) {
            plugin.getLogger().warning("Discord webhook queue backing up: " + webhookQueue.size() + " pending");
        }
    }

    private void sendWebhook(SpawnerLogEntry entry) {
        String url = config.getWebhookUrl();
        if (url == null || url.isEmpty()) return;

        try {
            // Resolve per-event embed config (lazy, cached)
            DiscordEventEmbedConfig embedCfg = embedConfigManager.getEmbedConfig(entry.getEventType());
            String jsonPayload = DiscordEmbedBuilder.buildWebhookPayload(entry, embedCfg, config, plugin);

            Scheduler.runTaskAsync(() -> {
                try {
                    sendHttpRequest(url, jsonPayload);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook", e);
                }
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error building Discord webhook payload", e);
        }
    }

    @SuppressWarnings("deprecation")
    private void sendHttpRequest(String webhookUrl, String jsonPayload) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "SmartSpawner-Logger/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 429) {
                plugin.getLogger().warning("Discord webhook rate limited – entry will be retried.");
            } else if (code < 200 || code >= 300) {
                plugin.getLogger().warning("Discord webhook returned HTTP " + code);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
