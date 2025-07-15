package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerExpClaimEvent;
import github.nighter.smartspawner.hooks.rpg.AuraSkillsIntegration;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.nms.ParticleWrapper;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.sell.SpawnerSellManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerMenuAction implements Listener {
    private static final Set<Material> SPAWNER_INFO_MATERIALS = EnumSet.of(
            Material.PLAYER_HEAD,
            Material.SPAWNER,
            Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL,
            Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD,
            Material.PIGLIN_HEAD
    );
    private final SmartSpawner plugin;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerStackerUI spawnerStackerUI;
    private final SpawnerStorageUI spawnerStorageUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final SpawnerSellManager spawnerSellManager;
    private AuraSkillsIntegration auraSkills;

    // Anti spam click properties
    private final Map<UUID, Long> lastInfoClickTime = new ConcurrentHashMap<>();

    public SpawnerMenuAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerStackerUI = plugin.getSpawnerStackerUI();
        this.spawnerStorageUI = plugin.getSpawnerStorageUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.spawnerSellManager = plugin.getSpawnerSellManager();
        this.auraSkills = plugin.getIntegrationManager().getAuraSkillsIntegration();
    }

    public void reload() {
        this.auraSkills = plugin.getIntegrationManager().getAuraSkillsIntegration();
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder(false) instanceof SpawnerMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        SpawnerData spawner = holder.getSpawnerData();

        // Verify click was in the actual menu and not player inventory
        if (event.getClickedInventory() == null ||
                !(event.getClickedInventory().getHolder(false) instanceof SpawnerMenuHolder)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        Material itemType = clickedItem.getType();

        if (itemType == Material.CHEST) {
            // Check if storage is disabled before handling chest click
            if (!spawner.getDisableStorage()) {
                handleChestClick(player, spawner);
            }
        } else if (SPAWNER_INFO_MATERIALS.contains(itemType)) {
            handleSpawnerInfoClick(player, spawner, event.getClick());
        } else if (itemType == Material.EXPERIENCE_BOTTLE) {
            handleExpBottleClick(player, spawner, false);
        }
    }

    public void handleChestClick(Player player, SpawnerData spawner) {
        String title = languageManager.getGuiTitle("gui_title_storage");
        Inventory pageInventory = spawnerStorageUI.createInventory(spawner, title, 1, -1);

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        player.closeInventory();
        player.openInventory(pageInventory);
    }

    private void handleSpawnerInfoClick(Player player, SpawnerData spawner, ClickType clickType) {
        if (isClickTooFrequent(player)) {
            return;
        }

        // Determine which mode we're in based on shop integration
        boolean hasShopIntegration = plugin.hasSellIntegration() && player.hasPermission("smartspawner.sellall");

        // Handle clicks based on shop integration mode
        if (hasShopIntegration) {
            // Standard mode: Left click for selling/XP, right click for stacker
            if (clickType == ClickType.LEFT) {
                // Collect EXP and sell items in storage
                handleExpBottleClick(player, spawner, true);
                handleSellAllItems(player, spawner);
            } else if (clickType == ClickType.RIGHT) {
                // Check stacker permission
                if (!player.hasPermission("smartspawner.stack")) {
                    messageService.sendMessage(player, "no_permission");
                    return;
                }

                // Open stacker GUI
                spawnerStackerUI.openStackerGui(player, spawner);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        } else {
            // Check stacker permission
            if (!player.hasPermission("smartspawner.stack")) {
                messageService.sendMessage(player, "no_permission");
                return;
            }

            // Open stacker GUI
            spawnerStackerUI.openStackerGui(player, spawner);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private boolean isClickTooFrequent(Player player) {
        long now = System.currentTimeMillis();
        long last = lastInfoClickTime.getOrDefault(player.getUniqueId(), 0L);
        lastInfoClickTime.put(player.getUniqueId(), now);
        return (now - last) < 300; // 300ms threshold
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastInfoClickTime.remove(event.getPlayer().getUniqueId());
    }

    private void handleSellAllItems(Player player, SpawnerData spawner) {
        if (!plugin.hasSellIntegration()) return;

        // Permission check
        if (!player.hasPermission("smartspawner.sellall")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        spawnerSellManager.sellAllItems(player, spawner);
    }

    public void handleExpBottleClick(Player player, SpawnerData spawner, boolean isSell) {
        if (isClickTooFrequent(player) && !isSell) {
            return;
        }

        int exp = spawner.getSpawnerExp();

        if (exp <= 0 && !isSell) {
            messageService.sendMessage(player, "no_exp");
            return;

        }

        int initialExp = exp;
        int expUsedForMending = 0;

        // Apply mending first if enabled
        if (plugin.getConfig().getBoolean("spawner_properties.default.allow_exp_mending")) {
            expUsedForMending = applyMendingFromExp(player, exp);
            exp -= expUsedForMending;
        }

        // Give AuraSkills XP if integration is enabled
        if (auraSkills != null) {
            giveAuraSkillsXp(player, spawner, initialExp);
        }

        // Give remaining exp to player
        if (exp > 0) {
            if(SpawnerExpClaimEvent.getHandlerList().getRegisteredListeners().length != 0) {
                SpawnerExpClaimEvent expClaimEvent = new SpawnerExpClaimEvent(player, spawner.getSpawnerLocation(), exp);
                Bukkit.getPluginManager().callEvent(expClaimEvent);
                if(expClaimEvent.isCancelled()) return;
                if(exp != expClaimEvent.getExpQuantity()) exp = expClaimEvent.getExpQuantity();
            }
            player.giveExp(exp);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        // Reset spawner exp and update menu
        spawner.setSpawnerExp(0);
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);

        // Update all viewers instead of just current player
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        // Update spawner capacity status
        if (spawner.getSpawnerExp() < spawner.getMaxStoredExp()) {
            if (spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
        }

        // Send appropriate message based on exp distribution
        sendExpCollectionMessage(player, initialExp, expUsedForMending);
    }

    private int applyMendingFromExp(Player player, int availableExp) {
        if (availableExp <= 0) {
            return 0;
        }

        int expUsed = 0;
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> itemsToCheck = Arrays.asList(
                inventory.getItemInMainHand(),
                inventory.getItemInOffHand(),
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        );

        for (ItemStack item : itemsToCheck) {
            if (availableExp <= 0) {
                break;
            }

            if (item == null || item.getType() == Material.AIR ||
                    !item.getEnchantments().containsKey(Enchantment.MENDING)) {
                continue;
            }

            if (!(item.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) {
                continue;
            }

            // Calculate repair amount based on available exp
            int damage = damageable.getDamage();
            int durabilityToRepair = Math.min(damage, availableExp * 2);
            int expNeeded = (durabilityToRepair + 1) / 2; // Round up for partial repairs

            if (expNeeded <= 0) {
                continue;
            }

            // Apply repair and track exp usage
            int actualExpUsed = Math.min(expNeeded, availableExp);
            int actualRepair = actualExpUsed * 2;

            // Ensure damage value does not go negative
            int newDamage = Math.max(0, damage - actualRepair);

            Damageable meta = (Damageable) item.getItemMeta();
            meta.setDamage(newDamage);
            item.setItemMeta(meta);

            availableExp -= actualExpUsed;
            expUsed += actualExpUsed;

            // Visual and sound effects for mending
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.0f);
            player.spawnParticle(ParticleWrapper.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 5);
        }

        return expUsed;
    }

    private void sendExpCollectionMessage(Player player, int totalExp, int mendingExp) {
        Map<String, String> placeholders = new HashMap<>();

        if (mendingExp > 0) {
            int remainingExp = totalExp - mendingExp;
            placeholders.put("exp_mending", languageManager.formatNumber(mendingExp));
            placeholders.put("exp", languageManager.formatNumber(remainingExp));
            messageService.sendMessage(player, "exp_collected_with_mending", placeholders);
        } else {
            if (totalExp > 0) {
                placeholders.put("exp", plugin.getLanguageManager().formatNumber(totalExp));
                messageService.sendMessage(player, "exp_collected", placeholders);
            }
        }
    }

    private void giveAuraSkillsXp(Player player, SpawnerData spawner, int totalExp) {
        try {
            if (auraSkills == null || !auraSkills.isEnabled()) {
                return;
            }

            // Get the entity type from the spawner
            EntityType entityType = spawner.getEntityType();
            if (entityType == null) {
                plugin.debug("Could not determine entity type for spawner at " + spawner.getSpawnerLocation());
                return;
            }

            // Give skill XP based on the entity type and exp amount
            auraSkills.giveSkillXp(player, entityType, totalExp);

        } catch (Exception e) {
            plugin.getLogger().warning("Error giving AuraSkills XP: " + e.getMessage());
            plugin.debug("AuraSkills integration error: " + e.toString());
        }
    }
}
