package me.nighter.smartSpawner.commands;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class GiveSpawnerCommand {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final List<String> supportedMobs;
    private final int MAX_AMOUNT = 64; // Giới hạn số lượng tối đa

    public GiveSpawnerCommand(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();

        // Initialize supported mobs list
        this.supportedMobs = Arrays.asList(
                "BLAZE", "BOGGED", "BREEZE", "CAVE_SPIDER", "CHICKEN", "COW", "CREEPER",
                "DROWNED", "ENDERMAN", "EVOKER", "GHAST", "GLOW_SQUID", "GUARDIAN",
                "HOGLIN", "HUSK", "IRON_GOLEM", "MAGMA_CUBE", "MOOSHROOM", "PIG",
                "PIGLIN", "PIGLIN_BRUTE", "PILLAGER", "PUFFERFISH", "RABBIT", "RAVAGER",
                "SALMON", "SHEEP", "SHULKER", "SKELETON", "SLIME", "SPIDER", "SQUID",
                "STRAY", "STRIDER", "TROPICAL_FISH", "VINDICATOR", "WITCH",
                "WITHER_SKELETON", "ZOGLIN", "ZOMBIE", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN"
        );
    }

    public boolean executeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smartspawner.give")) {
            sender.sendMessage(languageManager.getMessageWithPrefix("no-permission"));
            return true;
        }

        // Kiểm tra số lượng tham số (hiện tại cần 3 hoặc 4 tham số: give, player, mobtype, [amount])
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(languageManager.getMessageWithPrefix("command.give.usage"));
            return true;
        }

        // Get target player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(languageManager.getMessageWithPrefix("command.give.player-not-found"));
            return true;
        }

        String mobType = args[2].toUpperCase();
        if (!supportedMobs.contains(mobType)) {
            sender.sendMessage(languageManager.getMessageWithPrefix("command.give.invalid-mob-type"));
            return true;
        }

        // Xử lý số lượng
        int amount = 1;
        if (args.length == 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0) {
                    sender.sendMessage(languageManager.getMessageWithPrefix("command.give.invalid-amount"));
                    return true;
                }
                if (amount > MAX_AMOUNT) {
                    sender.sendMessage(languageManager.getMessageWithPrefix("command.give.amount-too-large",
                            "%max%", String.valueOf(MAX_AMOUNT)));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(languageManager.getMessageWithPrefix("command.give.invalid-amount"));
                return true;
            }
        }

        try {
            EntityType entityType = EntityType.valueOf(mobType);
            ItemStack spawner = createSpawnerItem(entityType);
            spawner.setAmount(amount);
            String entityName = languageManager.getLocalizedMobName(entityType);

            // Kiểm tra không gian trong inventory
            HashMap<Integer, ItemStack> leftoverItems = target.getInventory().addItem(spawner);

            if (!leftoverItems.isEmpty()) {
                // Nếu inventory đầy, thả những item còn lại xuống đất
                for (ItemStack leftover : leftoverItems.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
                target.sendMessage(languageManager.getMessageWithPrefix("command.give.inventory-full"));
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                sender.sendMessage(languageManager.getMessageWithPrefix("command.give.spawner-given-dropped",
                        "%player%", target.getName(),
                        "%entity%", entityName,
                        "%amount%", String.valueOf(amount)));
            } else {
                target.sendMessage(languageManager.getMessageWithPrefix("command.give.spawner-received",
                        "%amount%", String.valueOf(amount),
                        "%entity%", entityName));
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                sender.sendMessage(languageManager.getMessageWithPrefix("command.give.spawner-given",
                        "%player%", target.getName(),
                        "%entity%", entityName,
                        "%amount%", String.valueOf(amount)));
            }

            return true;
        } catch (IllegalArgumentException e) {
            sender.sendMessage(languageManager.getMessageWithPrefix("command.give.invalid-mob-type"));
            plugin.getLogger().warning("Error creating spawner: " + e.getMessage());
            return true;
        }
    }

    // For console
    public boolean executeCommand(String[] args) {
        if (args.length < 3 || args.length > 4) {
            plugin.getLogger().info(languageManager.getConsoleMessage("command.give.usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.getLogger().info(languageManager.getConsoleMessage("command.give.player-not-found"));
            return true;
        }

        String mobType = args[2].toUpperCase();
        if (!supportedMobs.contains(mobType)) {
            plugin.getLogger().info(languageManager.getConsoleMessage("command.give.invalid-mob-type"));
            return true;
        }

        // Xử lý số lượng
        int amount = 1;
        if (args.length == 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0) {
                    plugin.getLogger().info(languageManager.getConsoleMessage("command.give.invalid-amount"));
                    return true;
                }
                if (amount > MAX_AMOUNT) {
                    plugin.getLogger().info(languageManager.getConsoleMessage("command.give.amount-too-large",
                            "%max%", String.valueOf(MAX_AMOUNT)));
                    return true;
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().info(languageManager.getConsoleMessage("command.give.invalid-amount"));
                return true;
            }
        }

        try {
            EntityType entityType = EntityType.valueOf(mobType);
            ItemStack spawner = createSpawnerItem(entityType);
            spawner.setAmount(amount);
            String entityName = languageManager.getLocalizedMobName(entityType);

            // Kiểm tra không gian trong inventory
            HashMap<Integer, ItemStack> leftoverItems = target.getInventory().addItem(spawner);

            if (!leftoverItems.isEmpty()) {
                // Nếu inventory đầy, thả những item còn lại xuống đất
                for (ItemStack leftover : leftoverItems.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
                target.sendMessage(languageManager.getMessageWithPrefix("command.give.inventory-full"));
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                plugin.getLogger().info(languageManager.getConsoleMessage("command.give.spawner-given-dropped",
                        "%player%", target.getName(),
                        "%entity%", entityName,
                        "%amount%", String.valueOf(amount)));
            } else {
                target.sendMessage(languageManager.getMessageWithPrefix("command.give.spawner-received",
                        "%amount%", String.valueOf(amount),
                        "%entity%", entityName));
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                plugin.getLogger().info(languageManager.getConsoleMessage("command.give.spawner-given",
                        "%player%", target.getName(),
                        "%entity%", entityName,
                        "%amount%", String.valueOf(amount)));
            }

            return true;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().info(languageManager.getConsoleMessage("command.give.invalid-mob-type"));
            plugin.getLogger().warning("Error creating spawner: " + e.getMessage());
            return true;
        }
    }

    private ItemStack createSpawnerItem(EntityType entityType) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null) {
            if (entityType != null && entityType != EntityType.UNKNOWN) {
                // Set display name
                String entityTypeName = languageManager.getFormattedMobName(entityType);
                String displayName = languageManager.getMessage("spawner-name", "%entity%", entityTypeName);
                meta.setDisplayName(displayName);

                // Store entity type in item NBT
                BlockStateMeta blockMeta = (BlockStateMeta) meta;
                CreatureSpawner cs = (CreatureSpawner) blockMeta.getBlockState();
                cs.setSpawnedType(entityType);
                blockMeta.setBlockState(cs);
            }
            spawner.setItemMeta(meta);
        }

        return spawner;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smartspawner.give")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            String input = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            String input = args[2].toLowerCase();
            return supportedMobs.stream()
                    .map(String::toLowerCase)
                    .filter(mob -> mob.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 4) {
            // Gợi ý một số số lượng phổ biến
            String input = args[3].toLowerCase();
            return Arrays.asList("1", "16", "32", "64").stream()
                    .filter(amount -> amount.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
