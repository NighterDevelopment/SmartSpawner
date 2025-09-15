package github.nighter.smartspawner.spawner.gui.stacker;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class SpawnerStackerUI {
    private static final int GUI_SIZE = 27;
    private static final int[] DECREASE_SLOTS = {9, 10, 11};
    private static final int[] INCREASE_SLOTS = {17, 16, 15};
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int[] STACK_AMOUNTS = {64, 10, 1};

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    public SpawnerStackerUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
    }

    public void openStackerGui(Player player, SpawnerData spawner) {
        if (player == null || spawner == null) {
            return;
        }

        // Validate spawner integrity before opening stacker GUI
        if (plugin.getSpawnerManager().isGhostSpawner(spawner)) {
            // Spawner doesn't exist physically, don't open GUI
            return;
        }



        String title = languageManager.getGuiTitle("gui_title_stacker");
        Inventory gui = Bukkit.createInventory(new SpawnerStackerHolder(spawner), GUI_SIZE, title);

        // Fill GUI with modifier buttons and spawner info
        populateStackerGui(gui, spawner);

        player.openInventory(gui);
    }

    private void populateStackerGui(Inventory gui, SpawnerData spawner) {
        // Add decrease buttons
        for (int i = 0; i < STACK_AMOUNTS.length; i++) {
            gui.setItem(DECREASE_SLOTS[i], createActionButton("remove", spawner, STACK_AMOUNTS[i]));
        }

        // Add increase buttons
        for (int i = 0; i < STACK_AMOUNTS.length; i++) {
            gui.setItem(INCREASE_SLOTS[i], createActionButton("add", spawner, STACK_AMOUNTS[i]));
        }

        // Add spawner info button
        gui.setItem(SPAWNER_INFO_SLOT, createSpawnerInfoButton(spawner));
    }

    private ItemStack createActionButton(String action, SpawnerData spawner, int amount) {
        Map<String, String> placeholders = createPlaceholders(spawner, amount);

        String name = languageManager.getGuiItemName("button_" + action + ".name", placeholders);
        String[] lore = languageManager.getGuiItemLore("button_" + action + ".lore", placeholders);

        Material material = action.equals("add") ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;

        ItemStack button = createButton(material, name, lore);
        button.setAmount(Math.max(1, Math.min(amount, 64)));
        return button;
    }

    private ItemStack createSpawnerInfoButton(SpawnerData spawner) {
        Map<String, String> placeholders = createPlaceholders(spawner, 0);

        String name = languageManager.getGuiItemName("button_spawner.name", placeholders);
        String[] lore = languageManager.getGuiItemLore("button_spawner.lore", placeholders);

        return createButton(Material.SPAWNER, name, lore);
    }

    private Map<String, String> createPlaceholders(SpawnerData spawner, int amount) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(amount));
        placeholders.put("plural", amount > 1 ? "s" : "");
        placeholders.put("stack_size", String.valueOf(spawner.getStackSize()));
        placeholders.put("max_stack_size", String.valueOf(spawner.getMaxStackSize()));
        placeholders.put("entity", languageManager.getFormattedMobName(spawner.getEntityType()));
        placeholders.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(placeholders.get("entity")));
        return placeholders;
    }

    private ItemStack createButton(Material material, String name, String[] lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_UNBREAKABLE);
            button.setItemMeta(meta);
        }

        return button;
    }
}