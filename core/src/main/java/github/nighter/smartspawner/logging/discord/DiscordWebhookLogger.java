package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerLogEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Handles sending log entries to Discord via webhooks.
 *
 * <p>Design goals:</p>
 * <ul>
 *   <li>All HTTP work is done inside the single async timer task – no per-entry tasks.</li>
 *   <li>Up to {@value #MAX_EMBEDS_PER_REQUEST} log entries are batched into one POST.</li>
 *   <li>On HTTP 429 the {@code Retry-After} header is honoured and the whole batch is
 *       re-queued for the next timer tick.</li>
 *   <li>The queue is capped at {@value #MAX_QUEUE_SIZE} to prevent memory leaks under
 *       sustained bursts; oldest entries are silently dropped when the cap is reached.</li>
 * </ul>
 */
public class DiscordWebhookLogger {

    // ── Constants ────────────────────────────────────────────────────────────

    /** Maximum Discord POST requests per 60-second window (Discord hard limit is ~30). */
    private static final int  MAX_REQUESTS_PER_MINUTE = 25;
    /** Discord allows up to 10 embeds per message; we use that to batch log entries. */
    private static final int  MAX_EMBEDS_PER_REQUEST  = 10;
    /** Hard cap on the in-process queue to prevent unbounded memory growth. */
    private static final int  MAX_QUEUE_SIZE          = 500;
    private static final long MINUTE_IN_MILLIS        = 60_000L;

    // ── State ─────────────────────────────────────────────────────────────────

    private final SmartSpawner plugin;
    private volatile DiscordWebhookConfig       config;
    private volatile DiscordEmbedConfigManager  embedConfigManager;

    private final ConcurrentLinkedQueue<SpawnerLogEntry> webhookQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isShuttingDown           = new AtomicBoolean(false);
    private final AtomicLong    lastMinuteReset          = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong    requestsSentThisMinute   = new AtomicLong(0);
    /** Epoch-ms after which it is safe to send again; 0 = no active rate-limit. */
    private final AtomicLong    rateLimitedUntil         = new AtomicLong(0);

    private Scheduler.Task webhookTask;

    // ── Constructor ───────────────────────────────────────────────────────────

    public DiscordWebhookLogger(SmartSpawner plugin,
                                DiscordWebhookConfig config,
                                DiscordEmbedConfigManager embedConfigManager) {
        this.plugin              = plugin;
        this.config              = config;
        this.embedConfigManager  = embedConfigManager;

        if (config.isEnabled()) startWebhookTask();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Enqueue a log entry for Discord delivery.
     * Safe to call from any thread.
     */
    public void queueWebhook(SpawnerLogEntry entry) {
        if (isShuttingDown.get()) return;
        if (!config.isEnabled() || !config.isEventEnabled(entry.getEventType())) return;
        // Silently drop oldest entry if queue is at capacity
        if (webhookQueue.size() >= MAX_QUEUE_SIZE) {
            webhookQueue.poll();
        }
        webhookQueue.offer(entry);
    }

    /**
     * Hot-reload the configuration without recreating the whole logger.
     * Cancels and restarts the background task as needed.
     */
    public void reload(DiscordWebhookConfig newConfig, DiscordEmbedConfigManager newEmbedManager) {
        this.config             = newConfig;
        this.embedConfigManager = newEmbedManager;

        if (webhookTask != null) {
            webhookTask.cancel();
            webhookTask = null;
        }
        if (newConfig.isEnabled()) {
            startWebhookTask();
        }
    }

    /** Cancel the background task and discard any queued entries. */
    public void shutdown() {
        isShuttingDown.set(true);
        if (webhookTask != null) {
            webhookTask.cancel();
            webhookTask = null;
        }
        int remaining = webhookQueue.size();
        webhookQueue.clear();
        if (remaining > 0) {
            plugin.getLogger().info("Discord webhook: discarded " + remaining
                    + " pending entries at shutdown.");
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void startWebhookTask() {
        // Timer fires every 2 s (40 ticks). All HTTP work is done here – no nested tasks.
        webhookTask = Scheduler.runTaskTimerAsync(() -> {
            if (!isShuttingDown.get()) processWebhookQueue();
        }, 40L, 40L);
    }

    private void processWebhookQueue() {
        if (webhookQueue.isEmpty()) return;

        long now = System.currentTimeMillis();

        // Honour rate-limit backoff signalled by a previous 429 response
        if (now < rateLimitedUntil.get()) return;

        // Reset per-minute counter at the start of each new minute
        if (now - lastMinuteReset.get() >= MINUTE_IN_MILLIS) {
            requestsSentThisMinute.set(0);
            lastMinuteReset.set(now);
        }

        if (requestsSentThisMinute.get() >= MAX_REQUESTS_PER_MINUTE) return;

        String url = config.getWebhookUrl();
        if (url == null || url.isEmpty()) return;

        // Drain up to MAX_EMBEDS_PER_REQUEST entries into one batch
        List<SpawnerLogEntry> batch = new ArrayList<>(MAX_EMBEDS_PER_REQUEST);
        while (batch.size() < MAX_EMBEDS_PER_REQUEST) {
            SpawnerLogEntry entry = webhookQueue.poll();
            if (entry == null) break;
            batch.add(entry);
        }
        if (batch.isEmpty()) return;

        // Build embeds and post
        try {
            List<DiscordEmbed> embeds = new ArrayList<>(batch.size());
            for (SpawnerLogEntry entry : batch) {
                DiscordEventEmbedConfig embedCfg = embedConfigManager.getEmbedConfig(entry.getEventType());
                embeds.add(DiscordEmbedBuilder.buildEmbed(entry, embedCfg, config, plugin));
            }

            int responseCode = sendHttpRequest(url, DiscordEmbed.buildBatchJson(embeds));

            if (responseCode == 429) {
                // Re-queue the batch so it will be retried after the backoff
                for (SpawnerLogEntry entry : batch) {
                    if (webhookQueue.size() < MAX_QUEUE_SIZE) {
                        webhookQueue.offer(entry);
                    }
                }
            } else if (responseCode >= 200 && responseCode < 300) {
                requestsSentThisMinute.incrementAndGet();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error building/sending Discord webhook batch", e);
        }

        if (webhookQueue.size() > 50) {
            plugin.getLogger().warning("Discord webhook queue backing up: "
                    + webhookQueue.size() + " pending entries.");
        }
    }

    /**
     * Performs a blocking HTTP POST and returns the response code.
     * Must only be called from the async timer task thread.
     *
     * @return HTTP response code, or -1 on I/O failure
     */
    @SuppressWarnings("deprecation")
    private int sendHttpRequest(String webhookUrl, String jsonPayload) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "SmartSpawner-Logger/1.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();

            if (code == 429) {
                // Parse Retry-After (can be a float number of seconds)
                long backoffMs = 10_000L;
                String retryAfter = conn.getHeaderField("Retry-After");
                if (retryAfter != null) {
                    try {
                        backoffMs = Math.max(1_000L, (long) (Double.parseDouble(retryAfter) * 1_000));
                    } catch (NumberFormatException ignored) { }
                }
                rateLimitedUntil.set(System.currentTimeMillis() + backoffMs);
                plugin.getLogger().warning("Discord webhook rate limited – retrying in "
                        + (backoffMs / 1_000) + "s (batch will be re-queued).");
            } else if (code < 200 || code >= 300) {
                plugin.getLogger().warning("Discord webhook returned HTTP " + code + ".");
            }

            // Consume the response body to allow connection reuse
            try (InputStream ignored = conn.getInputStream()) { }

            return code;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Discord webhook request failed", e);
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}

