package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.LoggingMessageService;
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
 * Implements rate limiting and async processing to prevent blocking.
 */
public class DiscordWebhookLogger {
    private final SmartSpawner plugin;
    private final DiscordWebhookConfig config;
    private final ConcurrentLinkedQueue<SpawnerLogEntry> webhookQueue;
    private final AtomicBoolean isShuttingDown;
    private final AtomicLong lastWebhookTime;
    private final AtomicLong webhooksSentThisMinute;
    private Scheduler.Task webhookTask;
    private final LoggingMessageService loggingMessageService;
    
    // Discord rate limits: 30 requests per minute per webhook
    private static final int MAX_REQUESTS_PER_MINUTE = 25; // Leave some buffer
    private static final long MINUTE_IN_MILLIS = 60000;
    
    public DiscordWebhookLogger(SmartSpawner plugin, DiscordWebhookConfig config, LoggingMessageService loggingMessageService) {
        this.plugin = plugin;
        this.config = config;
        this.webhookQueue = new ConcurrentLinkedQueue<>();
        this.isShuttingDown = new AtomicBoolean(false);
        this.lastWebhookTime = new AtomicLong(System.currentTimeMillis());
        this.webhooksSentThisMinute = new AtomicLong(0);
        this.loggingMessageService = loggingMessageService;
        
        if (config.isEnabled()) {
            startWebhookTask();
        }
    }
    
    /**
     * Queue a log entry to be sent to Discord.
     */
    public void queueWebhook(SpawnerLogEntry entry) {
        if (!config.isEnabled() || !config.isEventEnabled(entry.getEventType())) {
            return;
        }
        
        webhookQueue.offer(entry);
    }
    
    private void startWebhookTask() {
        // Process webhook queue every 2 seconds (always async)
        webhookTask = Scheduler.runTaskTimerAsync(() -> {
            if (isShuttingDown.get()) {
                return;
            }
            processWebhookQueue();
        }, 40L, 40L);
    }
    
    private void processWebhookQueue() {
        if (webhookQueue.isEmpty()) {
            return;
        }
        
        // Check rate limiting
        long currentTime = System.currentTimeMillis();
        long timeSinceLastCheck = currentTime - lastWebhookTime.get();
        
        // Reset counter every minute
        if (timeSinceLastCheck >= MINUTE_IN_MILLIS) {
            webhooksSentThisMinute.set(0);
            lastWebhookTime.set(currentTime);
        }
        
        // Process entries within rate limit
        while (!webhookQueue.isEmpty() && webhooksSentThisMinute.get() < MAX_REQUESTS_PER_MINUTE) {
            SpawnerLogEntry entry = webhookQueue.poll();
            if (entry != null) {
                sendWebhook(entry);
                webhooksSentThisMinute.incrementAndGet();
            }
        }
        
        // Log warning if queue is backing up
        if (webhookQueue.size() > 50) {
            plugin.getLogger().warning("Discord webhook queue is backing up: " + webhookQueue.size() + " entries pending");
        }
    }
    
    private void sendWebhook(SpawnerLogEntry entry) {
        String webhookUrl = config.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }
        
        try {
            // Build the embed
            DiscordEmbed embed = DiscordEmbedBuilder.buildEmbed(entry, config, plugin, loggingMessageService);
            String jsonPayload = embed.toJson();
            
            // Send async HTTP request
            Scheduler.runTaskAsync(() -> {
                try {
                    sendHttpRequest(webhookUrl, jsonPayload);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook", e);
                }
            });
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error building Discord embed", e);
        }
    }
    
    private void sendHttpRequest(String webhookUrl, String jsonPayload) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(webhookUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "SmartSpawner-Logger/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 429) {
                // Rate limited - will retry from queue
                plugin.getLogger().warning("Discord webhook rate limited. Entries will retry.");
            } else if (responseCode < 200 || responseCode >= 300) {
                plugin.getLogger().warning("Discord webhook returned error code: " + responseCode);
            }
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Shutdown the webhook logger.
     */
    public void shutdown() {
        isShuttingDown.set(true);
        
        if (webhookTask != null) {
            webhookTask.cancel();
        }
        
        // Attempt to flush remaining entries (with limit to prevent blocking)
        int flushed = 0;
        while (!webhookQueue.isEmpty() && flushed < 10) {
            SpawnerLogEntry entry = webhookQueue.poll();
            if (entry != null) {
                sendWebhook(entry);
                flushed++;
            }
        }
        
        if (!webhookQueue.isEmpty()) {
            plugin.getLogger().warning("Discord webhook queue had " + webhookQueue.size() + 
                    " pending entries at shutdown (flushed " + flushed + ")");
        }
    }
}
