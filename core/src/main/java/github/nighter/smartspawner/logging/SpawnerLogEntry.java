package github.nighter.smartspawner.logging;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single log entry for a spawner action.
 * Contains all relevant information about the event.
 */
public class SpawnerLogEntry {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    
    private final long timestamp;
    @Getter
    private final SpawnerEventType eventType;
    private final String playerName;
    private final UUID playerUuid;
    private final Location location;
    private final EntityType entityType;
    private final Map<String, Object> metadata;
    
    private SpawnerLogEntry(Builder builder) {
        this.timestamp = builder.timestamp;
        this.eventType = builder.eventType;
        this.playerName = builder.playerName;
        this.playerUuid = builder.playerUuid;
        this.location = builder.location;
        this.entityType = builder.entityType;
        this.metadata = new HashMap<>(builder.metadata);
    }

    @Nullable
    public String getPlayerName() {
        return playerName;
    }
    
    @Nullable
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    @Nullable
    public Location getLocation() {
        return location;
    }
    
    @Nullable
    public EntityType getEntityType() {
        return entityType;
    }
    
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    /**
     * Converts the log entry to a JSON string for structured logging.
     */
    public String toJson() {
        return toJson(null);
    }
    
    /**
     * Converts the log entry to a JSON string with custom description.
     */
    public String toJson(String customDescription) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"timestamp\":\"").append(FORMATTER.format(Instant.ofEpochMilli(timestamp))).append("\",");
        json.append("\"timestamp_ms\":").append(timestamp).append(",");
        json.append("\"event_type\":\"").append(eventType.name()).append("\",");
        String description = customDescription != null ? customDescription : eventType.getDescription();
        json.append("\"description\":\"").append(description).append("\"");
        
        if (playerName != null) {
            json.append(",\"player\":\"").append(escapeJson(playerName)).append("\"");
        }
        if (playerUuid != null) {
            json.append(",\"player_uuid\":\"").append(playerUuid).append("\"");
        }
        if (location != null) {
            json.append(",\"location\":{");
            json.append("\"world\":\"").append(escapeJson(location.getWorld().getName())).append("\",");
            json.append("\"x\":").append(location.getBlockX()).append(",");
            json.append("\"y\":").append(location.getBlockY()).append(",");
            json.append("\"z\":").append(location.getBlockZ());
            json.append("}");
        }
        if (entityType != null) {
            json.append(",\"entity_type\":\"").append(entityType.name()).append("\"");
        }
        if (!metadata.isEmpty()) {
            json.append(",\"metadata\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escapeJson(entry.getKey())).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(escapeJson(value.toString())).append("\"");
                } else if (value instanceof Number || value instanceof Boolean) {
                    json.append(value);
                } else {
                    json.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
                }
                first = false;
            }
            json.append("}");
        }
        json.append("}");
        return json.toString();
    }
    
    /**
     * Converts the log entry to a human-readable string.
     */
    public String toReadableString() {
        return toReadableString(null);
    }
    
    /**
     * Converts the log entry to a human-readable string with custom description.
     */
    public String toReadableString(String customDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(FORMATTER.format(Instant.ofEpochMilli(timestamp))).append("] ");
        String description = customDescription != null ? customDescription : eventType.getDescription();
        sb.append(description);
        
        if (playerName != null) {
            sb.append(" | Player: ").append(playerName);
        }
        if (location != null) {
            sb.append(" | Location: ").append(location.getWorld().getName())
                    .append(" (").append(location.getBlockX())
                    .append(", ").append(location.getBlockY())
                    .append(", ").append(location.getBlockZ()).append(")");
        }
        if (entityType != null) {
            sb.append(" | Entity: ").append(entityType.name());
        }
        if (!metadata.isEmpty()) {
            sb.append(" | ");
            metadata.forEach((key, value) -> sb.append(key).append("=").append(value).append(" "));
        }
        return sb.toString().trim();
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    public static class Builder {
        private long timestamp = System.currentTimeMillis();
        private SpawnerEventType eventType;
        private String playerName;
        private UUID playerUuid;
        private Location location;
        private EntityType entityType;
        private final Map<String, Object> metadata = new HashMap<>();
        
        public Builder(SpawnerEventType eventType) {
            this.eventType = eventType;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder player(String name, UUID uuid) {
            this.playerName = name;
            this.playerUuid = uuid;
            return this;
        }
        
        public Builder location(Location location) {
            this.location = location;
            return this;
        }
        
        public Builder entityType(EntityType entityType) {
            this.entityType = entityType;
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }
        
        public SpawnerLogEntry build() {
            return new SpawnerLogEntry(this);
        }
    }
}
