package github.nighter.smartspawner.commands.config.editor;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the three inventories of the config editor.
 *
 * <p>Slot numbers are shared with {@link ConfigEditorHandler}, which is why they live here as
 * constants rather than as literals on both sides.</p>
 */
public class ConfigEditorUI {

    static final int LIST_SIZE = 54;
    static final int LIST_CONTENT = 45;
    static final int LIST_PREVIOUS = 45;
    static final int LIST_SWITCH = 47;
    static final int LIST_NEW_ENTRY = 49;
    static final int LIST_NEXT = 53;

    static final int LOOT_SIZE = 54;
    static final int LOOT_START = 0;
    static final int LOOT_END = 44;
    static final int LOOT_BACK = 45;
    static final int LOOT_ADD = 49;

    static final int CAPTURE_SIZE = 27;
    static final int CAPTURE_SLOT = 13;
    static final int CAPTURE_CANCEL = 18;
    static final int CAPTURE_CONFIRM = 26;

    private final SmartSpawner plugin;
    private final ConfigEditorService service;

    public ConfigEditorUI(SmartSpawner plugin, ConfigEditorService service) {
        this.plugin = plugin;
        this.service = service;
    }

    private LanguageManager lang() {
        return plugin.getLanguageManager();
    }

    // ============== Entry list ==============

    public void openEntryList(Player player, ConfigEditorTarget target, int page) {
        ConfigEditorService.EntryPage entryPage = service.readEntryPage(target, page, LIST_CONTENT);
        int totalPages = entryPage.totalPages();
        int shownPage = entryPage.currentPage();

        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("current", String.valueOf(shownPage));
        titlePlaceholders.put("total", String.valueOf(totalPages));

        Inventory inventory = Bukkit.createInventory(
                new EntryListHolder(target, shownPage), LIST_SIZE,
                lang().commandGui().title(target.getTitleKey() + "_title", titlePlaceholders));

        for (int i = 0; i < entryPage.entries().size(); i++) {
            inventory.setItem(i, buildEntryIcon(target, entryPage.entries().get(i)));
        }

        if (shownPage > 1) {
            inventory.setItem(LIST_PREVIOUS, navigationItem("previous_page", shownPage - 1));
        }
        if (shownPage < totalPages) {
            inventory.setItem(LIST_NEXT, navigationItem("next_page", shownPage + 1));
        }
        boolean showingMobs = target == ConfigEditorTarget.SMART_SPAWNER;
        inventory.setItem(LIST_SWITCH, simpleItem(
                showingMobs ? Material.CHEST : Material.ZOMBIE_SPAWN_EGG,
                showingMobs ? "config_editor.switch_to_items" : "config_editor.switch_to_mobs",
                Map.of()));
        inventory.setItem(LIST_NEW_ENTRY, simpleItem(Material.NETHER_STAR, "config_editor.new_entry", Map.of()));

        player.openInventory(inventory);
    }

    private ItemStack buildEntryIcon(ConfigEditorTarget target, ConfigEditorService.EntryView entry) {
        Material icon = iconFor(target, entry.key());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entry", entry.key());
        placeholders.put("experience", String.valueOf(entry.experience()));
        placeholders.put("loot_count", String.valueOf(entry.lootCount()));

        Double dropChance = entry.dropChance();
        placeholders.put("drop_chance", dropChance == null ? "100" : trim(dropChance));

        return simpleItem(icon, "config_editor.entry", placeholders);
    }

    /** Spawn egg for a mob, the item itself for an item spawner, with a safe fallback. */
    private Material iconFor(ConfigEditorTarget target, String key) {
        if (target == ConfigEditorTarget.ITEM_SPAWNER) {
            Material material = Material.matchMaterial(key);
            return material != null && material.isItem() ? material : Material.SPAWNER;
        }

        Material egg = Material.matchMaterial(key.toLowerCase(Locale.ROOT) + "_spawn_egg");
        return egg != null ? egg : Material.SPAWNER;
    }

    // ============== Loot list ==============

    /**
     * The loot rows of one entry, as the items they actually drop.
     *
     * <p>Only the loot list is an inventory. Everything numeric about an entry lives in the dialog
     * form, because a slot can show an item but cannot show a value being dragged.</p>
     */
    public void openLootList(Player player, ConfigEditorTarget target, String entryKey, int listPage) {
        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("entry", entryKey);

        Inventory inventory = Bukkit.createInventory(
                new LootListHolder(target, entryKey, listPage), LOOT_SIZE,
                lang().commandGui().title("config_editor.loot_title", titlePlaceholders));

        int slot = LOOT_START;
        for (ConfigEditorService.LootView loot : service.readLootList(target, entryKey)) {
            if (slot > LOOT_END) {
                break;
            }
            inventory.setItem(slot++, buildLootIcon(loot));
        }

        inventory.setItem(LOOT_ADD, simpleItem(Material.HOPPER, "config_editor.add_loot", Map.of()));
        inventory.setItem(LOOT_BACK, simpleItem(Material.ARROW, "config_editor.back", Map.of()));

        player.openInventory(inventory);
    }

    private ItemStack buildLootIcon(ConfigEditorService.LootView loot) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("label", loot.key());
        placeholders.put("item", loot.rawItem());
        placeholders.put("amount", loot.amountLabel());
        placeholders.put("chance", trim(loot.chance()));
        String durability = loot.durabilityLabel();
        placeholders.put("durability", durability == null ? "-" : durability);

        if (loot.isBroken()) {
            return simpleItem(Material.BARRIER, "config_editor.loot_broken", placeholders);
        }

        // Show the real item so the admin sees exactly what drops, then overwrite its text.
        ItemStack icon = loot.preview().clone();
        applyText(icon, "config_editor.loot_entry", placeholders);
        return icon;
    }

    // ============== Item capture ==============

    public void openItemCapture(Player player, ItemCaptureHolder holder) {
        String titleKey = switch (holder.getPurpose()) {
            case ADD_LOOT -> "config_editor.capture_add_title";
            case REPLACE_LOOT -> "config_editor.capture_replace_title";
            case NEW_ENTRY -> "config_editor.capture_new_entry_title";
        };

        Inventory inventory = Bukkit.createInventory(holder, CAPTURE_SIZE,
                lang().commandGui().title(titleKey, Map.of()));

        String hintKey = holder.getPurpose() == ItemCaptureHolder.Purpose.NEW_ENTRY
                ? "config_editor.capture_hint_entry"
                : "config_editor.capture_hint_loot";

        ItemStack filler = simpleItem(Material.GRAY_STAINED_GLASS_PANE, hintKey, Map.of());
        for (int i = 0; i < CAPTURE_SIZE; i++) {
            inventory.setItem(i, filler.clone());
        }

        inventory.setItem(CAPTURE_SLOT, null);
        inventory.setItem(CAPTURE_CANCEL, simpleItem(Material.RED_STAINED_GLASS_PANE,
                "config_editor.cancel", Map.of()));
        inventory.setItem(CAPTURE_CONFIRM, simpleItem(Material.LIME_STAINED_GLASS_PANE,
                "config_editor.confirm", Map.of()));

        player.openInventory(inventory);
    }

    // ============== Item helpers ==============

    private ItemStack navigationItem(String key, int targetPage) {
        return simpleItem(Material.ARROW, "general_navigation." + key,
                Map.of("target_page", String.valueOf(targetPage)));
    }

    private ItemStack simpleItem(Material material, String key, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material);
        applyText(item, key, placeholders);
        return item;
    }

    /**
     * The language sections take the full path to the value, not to the block holding it, so the
     * {@code .name} and {@code .lore} leaves have to be named here. Passing the block path instead
     * yields the section's {@code toString()} as the item name.
     */
    private void applyText(ItemStack item, String key, Map<String, String> placeholders) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setDisplayName(lang().commandGui().name(key + ".name", placeholders));
        List<String> lore = new ArrayList<>(lang().commandGui().loreList(key + ".lore", placeholders));
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    /** Drops a trailing {@code .0} so whole percentages read as whole numbers. */
    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static String shorten(String texture) {
        return texture.length() <= 16 ? texture : texture.substring(0, 16) + "...";
    }
}
