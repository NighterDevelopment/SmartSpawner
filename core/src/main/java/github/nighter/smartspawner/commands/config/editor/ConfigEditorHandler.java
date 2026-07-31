package github.nighter.smartspawner.commands.config.editor;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.MessageService;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Click and close handling for every config editor screen.
 *
 * <p>Screens are told apart by their {@link org.bukkit.inventory.InventoryHolder}, never by title,
 * so a renamed or translated title cannot break routing.</p>
 */
public class ConfigEditorHandler implements Listener {

    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final ConfigEditorService service;
    private final ConfigEditorUI ui;
    private final ConfigEditorDialogs dialogs;

    public ConfigEditorHandler(SmartSpawner plugin, ConfigEditorService service,
                               ConfigEditorUI ui, ConfigEditorDialogs dialogs) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.service = service;
        this.ui = ui;
        this.dialogs = dialogs;
    }

    // ============== Entry list ==============

    @EventHandler
    public void onEntryListClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof EntryListHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        ConfigEditorTarget target = holder.getTarget();

        if (slot == ConfigEditorUI.LIST_PREVIOUS) {
            click(player);
            ui.openEntryList(player, target, holder.getPage() - 1);
            return;
        }
        if (slot == ConfigEditorUI.LIST_NEXT) {
            click(player);
            ui.openEntryList(player, target, holder.getPage() + 1);
            return;
        }
        if (slot == ConfigEditorUI.LIST_NEW_ENTRY) {
            click(player);
            ui.openItemCapture(player, new ItemCaptureHolder(target,
                    ItemCaptureHolder.Purpose.NEW_ENTRY, null, null, holder.getPage()));
            return;
        }
        if (slot == ConfigEditorUI.LIST_SWITCH) {
            click(player);
            ui.openEntryList(player, target.other(), 1);
            return;
        }
        if (slot >= ConfigEditorUI.LIST_CONTENT) return;

        List<String> entries = service.listEntries(target);
        int index = (holder.getPage() - 1) * ConfigEditorUI.LIST_CONTENT + slot;
        if (index < 0 || index >= entries.size()) return;

        click(player);
        dialogs.openEntryOptions(player, target, entries.get(index), holder.getPage());
    }

    // ============== Loot list ==============

    @EventHandler
    public void onLootListClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof LootListHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return;

        ConfigEditorTarget target = holder.getTarget();
        String entryKey = holder.getEntryKey();
        int listPage = holder.getListPage();
        int slot = event.getSlot();

        if (!service.hasEntry(target, entryKey)) {
            messageService.sendMessage(player, "config_editor.entry_missing");
            ui.openEntryList(player, target, listPage);
            return;
        }

        if (slot == ConfigEditorUI.LOOT_BACK) {
            click(player);
            dialogs.openEntryOptions(player, target, entryKey, listPage);
            return;
        }

        if (slot == ConfigEditorUI.LOOT_ADD) {
            click(player);
            ui.openItemCapture(player, new ItemCaptureHolder(target,
                    ItemCaptureHolder.Purpose.ADD_LOOT, entryKey, null, listPage));
            return;
        }

        if (slot < ConfigEditorUI.LOOT_START || slot > ConfigEditorUI.LOOT_END) return;

        List<String> lootKeys = service.listLootKeys(target, entryKey);
        int index = slot - ConfigEditorUI.LOOT_START;
        if (index < 0 || index >= lootKeys.size()) return;
        String lootKey = lootKeys.get(index);

        if (event.getClick() == ClickType.RIGHT) {
            click(player);
            ui.openItemCapture(player, new ItemCaptureHolder(target,
                    ItemCaptureHolder.Purpose.REPLACE_LOOT, entryKey, lootKey, listPage));
            return;
        }

        // Everything else about a loot row, including removing it, lives in its dialog form.
        click(player);
        dialogs.openLootEditor(player, target, entryKey, lootKey, listPage);
    }

    // ============== Item capture ==============

    @EventHandler
    public void onCaptureClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ItemCaptureHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean ownInventory = event.getClickedInventory() != event.getInventory();

        // Shift-clicking would push the item past the drop slot into the filler panes, so it is the
        // one action that has to be blocked on both sides of the window.
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        if (ownInventory) {
            return; // Rearranging their own inventory is fine.
        }

        int slot = event.getSlot();
        if (slot == ConfigEditorUI.CAPTURE_SLOT) {
            return; // The whole point of this screen: let the item in and out freely.
        }

        event.setCancelled(true);

        if (slot == ConfigEditorUI.CAPTURE_CANCEL) {
            click(player);
            returnCapturedItem(player, event.getInventory());
            reopenFrom(player, holder);
            return;
        }

        if (slot == ConfigEditorUI.CAPTURE_CONFIRM) {
            ItemStack captured = event.getInventory().getItem(ConfigEditorUI.CAPTURE_SLOT);
            if (captured == null || captured.getType() == Material.AIR) {
                messageService.sendMessage(player, "config_editor.capture_empty");
                return;
            }

            // Taken out of the inventory before reopening so the close handler cannot hand it back
            // to the player as well.
            event.getInventory().setItem(ConfigEditorUI.CAPTURE_SLOT, null);
            applyCapture(player, holder, captured);
        }
    }

    @EventHandler
    public void onCaptureClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ItemCaptureHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        returnCapturedItem(player, event.getInventory());
    }

    /** Never keep the admin's item: whatever is still in the drop slot goes back to them. */
    private void returnCapturedItem(Player player, Inventory inventory) {
        ItemStack captured = inventory.getItem(ConfigEditorUI.CAPTURE_SLOT);
        if (captured == null || captured.getType() == Material.AIR) {
            return;
        }

        inventory.setItem(ConfigEditorUI.CAPTURE_SLOT, null);
        for (ItemStack leftover : player.getInventory().addItem(captured).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void applyCapture(Player player, ItemCaptureHolder holder, ItemStack captured) {
        ConfigEditorTarget target = holder.getTarget();

        switch (holder.getPurpose()) {
            case ADD_LOOT -> {
                String label = service.addLoot(target, holder.getEntryKey(), captured);
                messageService.sendMessage(player, "config_editor.loot_added", Map.of("label", label));
                ui.openLootList(player, target, holder.getEntryKey(), holder.getListPage());
            }
            case REPLACE_LOOT -> {
                service.setLootItem(target, holder.getEntryKey(), holder.getLootKey(), captured);
                messageService.sendMessage(player, "config_editor.loot_replaced",
                        Map.of("label", holder.getLootKey()));
                ui.openLootList(player, target, holder.getEntryKey(), holder.getListPage());
            }
            case NEW_ENTRY -> createEntryFrom(player, holder, captured);
        }
    }

    private void createEntryFrom(Player player, ItemCaptureHolder holder, ItemStack captured) {
        ConfigEditorTarget target = holder.getTarget();
        String key = target == ConfigEditorTarget.SMART_SPAWNER
                ? entityKeyFromSpawnEgg(captured.getType())
                : captured.getType().name();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("item", captured.getType().name());

        if (key == null || !target.isValidKey(key)) {
            messageService.sendMessage(player, target == ConfigEditorTarget.SMART_SPAWNER
                    ? "config_editor.new_entry_needs_egg"
                    : "config_editor.new_entry_invalid", placeholders);
            returnItem(player, captured);
            ui.openEntryList(player, target, holder.getListPage());
            return;
        }

        placeholders.put("entry", key);
        returnItem(player, captured);

        if (!service.createEntry(target, key)) {
            messageService.sendMessage(player, "config_editor.new_entry_exists", placeholders);
            ui.openEntryList(player, target, holder.getListPage());
            return;
        }

        messageService.sendMessage(player, "config_editor.new_entry_created", placeholders);
        dialogs.openEntryOptions(player, target, key, holder.getListPage());
    }

    /**
     * A spawn egg is the only item that names a mob, so it is what the editor asks for when creating
     * a mob entry. Every vanilla egg is {@code <TYPE>_SPAWN_EGG}.
     */
    private String entityKeyFromSpawnEgg(Material material) {
        String name = material.name();
        if (!name.endsWith("_SPAWN_EGG")) {
            return null;
        }

        String candidate = name.substring(0, name.length() - "_SPAWN_EGG".length());
        try {
            return EntityType.valueOf(candidate).name();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void returnItem(Player player, ItemStack item) {
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void reopenFrom(Player player, ItemCaptureHolder holder) {
        if (holder.getPurpose() == ItemCaptureHolder.Purpose.NEW_ENTRY) {
            ui.openEntryList(player, holder.getTarget(), holder.getListPage());
        } else {
            ui.openLootList(player, holder.getTarget(), holder.getEntryKey(), holder.getListPage());
        }
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }
}
