package github.nighter.smartspawner.updates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import github.nighter.smartspawner.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class UpdateChecker implements Listener {
    private final JavaPlugin plugin;
    private final String projectId = "9tQwxSFr";
    private boolean updateAvailable = false;
    private final String currentVersion;
    private String latestVersion = "";
    private String downloadUrl = "";
    private boolean serverVersionSupported = true;
    private JsonArray latestSupportedVersions = null;

    // ANSI codes for console output
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[2m";
    private static final String GREEN  = "\u001B[92m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[96m";
    private static final String RED    = "\u001B[91m";

    private final Map<UUID, LocalDate> notifiedPlayers = new HashMap<>();

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        checkForUpdates().thenAccept(hasUpdate -> {
            if (hasUpdate && serverVersionSupported) {
                displayConsoleUpdateMessage();
            } else if (!serverVersionSupported) {
                displayUnsupportedVersionMessage();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to check for updates: " + ex.getMessage());
            return null;
        });
    }

    private void displayConsoleUpdateMessage() {
        String line = DIM + "-----------------------------------------------------" + RESET;
        plugin.getLogger().info(line);
        plugin.getLogger().info(BOLD + "  SmartSpawner - Update Available" + RESET);
        plugin.getLogger().info(line);
        plugin.getLogger().info("  Current version  :  " + YELLOW + currentVersion + RESET);
        plugin.getLogger().info("  Latest version   :  " + GREEN  + latestVersion  + RESET);
        plugin.getLogger().info("  Download         :  " + CYAN
                + "https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion + RESET);
        plugin.getLogger().info(line);
    }

    private void displayUnsupportedVersionMessage() {
        String serverVersion = Bukkit.getVersion();
        String line = DIM + "-----------------------------------------------------" + RESET;
        plugin.getLogger().warning(line);
        plugin.getLogger().warning(BOLD + RED + "  SmartSpawner - Server Version Not Supported" + RESET);
        plugin.getLogger().warning(line);
        plugin.getLogger().warning("  Server version   :  " + YELLOW + serverVersion + RESET);
        plugin.getLogger().warning("  Latest plugin    :  " + GREEN  + latestVersion  + RESET);
        plugin.getLogger().warning("  Supported MC     :  " + CYAN   + getSupportedVersionsString() + RESET);
        plugin.getLogger().warning("");
        plugin.getLogger().warning("  This server version is no longer supported.");
        plugin.getLogger().warning("  Update notifications have been disabled.");
        plugin.getLogger().warning(line);
    }

    private String getSupportedVersionsString() {
        if (latestSupportedVersions == null || latestSupportedVersions.isEmpty()) {
            return "N/A";
        }
        List<String> versions = new ArrayList<>();
        for (JsonElement element : latestSupportedVersions) {
            versions.add(element.getAsString());
        }
        return String.join(", ", versions);
    }

    private boolean isServerVersionSupported(JsonObject latestVersionObj) {
        try {
            String serverVersion = Bukkit.getVersion();
            JsonArray gameVersions = latestVersionObj.getAsJsonArray("game_versions");
            if (gameVersions == null || gameVersions.isEmpty()) {
                return true;
            }
            String cleanServerVersion = extractMinecraftVersion(serverVersion);
            for (JsonElement versionElement : gameVersions) {
                if (isVersionCompatible(cleanServerVersion, versionElement.getAsString())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking server version compatibility: " + e.getMessage());
            return true;
        }
    }

    private String extractMinecraftVersion(String serverVersion) {
        if (serverVersion.contains("MC: ")) {
            String mcPart = serverVersion.substring(serverVersion.indexOf("MC: ") + 4);
            if (mcPart.contains(")")) {
                mcPart = mcPart.substring(0, mcPart.indexOf(")"));
            }
            return mcPart.trim();
        }
        if (serverVersion.matches(".*\\d+\\.\\d+(\\.\\d+)?.*")) {
            for (String part : serverVersion.split("\\s+")) {
                if (part.matches("\\d+\\.\\d+(\\.\\d+)?")) {
                    return part;
                }
            }
        }
        return serverVersion;
    }

    private boolean isVersionCompatible(String serverVersion, String supportedVersion) {
        try {
            if (serverVersion.equals(supportedVersion)) {
                return true;
            }
            String[] serverParts    = serverVersion.split("\\.");
            String[] supportedParts = supportedVersion.split("\\.");
            if (serverParts.length >= 2 && supportedParts.length >= 2) {
                return Integer.parseInt(serverParts[0]) == Integer.parseInt(supportedParts[0])
                        && Integer.parseInt(serverParts[1]) == Integer.parseInt(supportedParts[1]);
            }
            return false;
        } catch (NumberFormatException e) {
            return serverVersion.equals(supportedVersion);
        }
    }

    public CompletableFuture<Boolean> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI
                        .create("https://api.modrinth.com/v2/project/" + projectId + "/version")
                        .toURL()
                        .openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "SmartSpawner-UpdateChecker/1.0");

                if (connection.getResponseCode() != 200) {
                    plugin.getLogger().warning("Update check failed with HTTP " + connection.getResponseCode());
                    return false;
                }

                String response;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    response = reader.lines().collect(Collectors.joining("\n"));
                }

                JsonArray versions = JsonParser.parseString(response).getAsJsonArray();
                if (versions.isEmpty()) {
                    return false;
                }

                JsonObject latestVersionObj = null;
                for (JsonElement element : versions) {
                    JsonObject version = element.getAsJsonObject();
                    if (!"release".equals(version.get("version_type").getAsString())) {
                        continue;
                    }
                    if (latestVersionObj == null) {
                        latestVersionObj = version;
                    } else {
                        String currentDate = latestVersionObj.get("date_published").getAsString();
                        String newDate     = version.get("date_published").getAsString();
                        if (newDate.compareTo(currentDate) > 0) {
                            latestVersionObj = version;
                        }
                    }
                }

                if (latestVersionObj == null) {
                    return false;
                }

                latestVersion = latestVersionObj.get("version_number").getAsString();
                downloadUrl   = "https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion;

                serverVersionSupported  = isServerVersionSupported(latestVersionObj);
                latestSupportedVersions = latestVersionObj.getAsJsonArray("game_versions");

                updateAvailable = new Version(latestVersion).compareTo(new Version(currentVersion)) > 0;
                return updateAvailable;

            } catch (Exception e) {
                plugin.getLogger().warning("Error checking for updates: " + e.getMessage());
                return false;
            }
        });
    }

    private void sendUpdateNotification(Player player) {
        if (!updateAvailable || !serverVersionSupported || !player.hasPermission("smartspawner.admin")) {
            return;
        }

        TextColor purple = TextColor.fromHexString("#9b72cf");
        TextColor green  = TextColor.fromHexString("#57d98e");
        TextColor yellow = TextColor.fromHexString("#f0c857");
        TextColor white  = TextColor.fromHexString("#e6e6e6");
        TextColor gray   = TextColor.fromHexString("#555577");

        Component line = Component.text("----------------------------------------").color(gray);

        Component header = Component.text("SmartSpawner").color(purple)
                .append(Component.text(" - Update Available").color(white));

        Component versions = Component.text("  Current: ").color(white)
                .append(Component.text(currentVersion).color(yellow))
                .append(Component.text("  ->  Latest: ").color(white))
                .append(Component.text(latestVersion).color(green));

        Component download = Component.text("  [ Click here to download v" + latestVersion + " ]")
                .color(purple)
                .clickEvent(ClickEvent.openUrl(downloadUrl))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Open download page on Modrinth").color(white)));

        player.sendMessage(Component.empty());
        player.sendMessage(line);
        player.sendMessage(header);
        player.sendMessage(versions);
        player.sendMessage(download);
        player.sendMessage(line);
        player.sendMessage(Component.empty());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        LocalDate today = LocalDate.now();

        notifiedPlayers.entrySet().removeIf(entry -> entry.getValue().isBefore(today));

        if (notifiedPlayers.containsKey(playerId)) {
            return;
        }

        if (updateAvailable && serverVersionSupported) {
            Scheduler.runTaskLater(() -> {
                sendUpdateNotification(player);
                notifiedPlayers.put(playerId, today);
            }, 40L);
        } else if (serverVersionSupported) {
            checkForUpdates().thenAccept(hasUpdate -> {
                if (hasUpdate && serverVersionSupported) {
                    Scheduler.runTask(() -> {
                        sendUpdateNotification(player);
                        notifiedPlayers.put(playerId, today);
                    });
                }
            });
        }
        // !serverVersionSupported: skip notifications entirely
    }
}