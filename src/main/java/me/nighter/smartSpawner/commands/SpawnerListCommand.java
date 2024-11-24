package me.nighter.smartSpawner.commands;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.managers.SpawnerHeadManager;
import me.nighter.smartSpawner.managers.SpawnerManager;
import me.nighter.smartSpawner.utils.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpawnerListCommand {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final LanguageManager languageManager;
    private static final int SPAWNERS_PER_PAGE = 45;

    public SpawnerListCommand(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.languageManager = plugin.getLanguageManager();
    }

    public void openWorldSelectionGUI(Player player) {
        if (!player.hasPermission("smartspawner.list")) {
            languageManager.sendMessage(player, "no-permission");
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        Inventory inv = Bukkit.createInventory(new WorldSelectionHolder(),
                27, languageManager.getMessage("spawner-list.gui-title.world-selection"));

        // World selection buttons with custom textures
        ItemStack overworldButton = createWorldButton(Material.GRASS_BLOCK,
                languageManager.getMessage("spawner-list.world-buttons.overworld.name"),
                getWorldDescription("spawner-list.world-buttons.overworld.lore", "world"));

        ItemStack netherButton = createWorldButton(Material.NETHERRACK,
                languageManager.getMessage("spawner-list.world-buttons.nether.name"),
                getWorldDescription("spawner-list.world-buttons.nether.lore", "world_nether"));

        ItemStack endButton = createWorldButton(Material.END_STONE,
                languageManager.getMessage("spawner-list.world-buttons.end.name"),
                getWorldDescription("spawner-list.world-buttons.end.lore", "world_the_end"));


        // Set buttons in a visually appealing layout
        inv.setItem(11, overworldButton);
        inv.setItem(13, netherButton);
        inv.setItem(15, endButton);

        // Add decorative glass panes
//        ItemStack decoration = createDecorationItem();
//        for (int i = 0; i < 27; i++) {
//            if (inv.getItem(i) == null) {
//                inv.setItem(i, decoration);
//            }
//        }

        player.openInventory(inv);
    }

    private List<String> getWorldDescription(String path, String worldName) {
        List<String> description = new ArrayList<>();
        for (String line : languageManager.getMessage(path).split("\n")) {
            description.add(line.replace("{total}", String.valueOf(countSpawnersInWorld(worldName))));
        }
        return description;
    }


    private ItemStack createDecorationItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWorldButton(Material material, String name, List<String> lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        button.setItemMeta(meta);
        return button;
    }

    private int countSpawnersInWorld(String worldName) {
        return (int) spawnerManager.getAllSpawners().stream()
                .filter(spawner -> spawner.getSpawnerLocation().getWorld().getName().equals(worldName))
                .count();
    }

    public void openSpawnerListGUI(Player player, String worldName, int page) {
        if (!player.hasPermission("smartspawner.list")) {
            languageManager.sendMessage(player, "no-permission");
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        List<SpawnerData> worldSpawners = spawnerManager.getAllSpawners().stream()
                .filter(spawner -> spawner.getSpawnerLocation().getWorld().getName().equals(worldName))
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) worldSpawners.size() / SPAWNERS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));

        String worldTitle = switch (worldName) {
            case "world" -> languageManager.getMessage("spawner-list.world-buttons.overworld.name");
            case "world_nether" -> languageManager.getMessage("spawner-list.world-buttons.nether.name");
            case "world_the_end" -> languageManager.getMessage("spawner-list.world-buttons.end.name");
            default -> worldName;
        };

        String title = languageManager.getMessage("spawner-list.gui-title.page-title",
                Map.of(
                        "{world}", worldTitle,
                        "{current}", String.valueOf(page),
                        "{total}", String.valueOf(totalPages)
                ));

        Inventory inv = Bukkit.createInventory(new SpawnerListHolder(page, totalPages, worldName),
                54, title);

        // Calculate start and end indices for current page
        int startIndex = (page - 1) * SPAWNERS_PER_PAGE;
        int endIndex = Math.min(startIndex + SPAWNERS_PER_PAGE, worldSpawners.size());

        // Populate inventory with spawners
        for (int i = startIndex; i < endIndex; i++) {
            SpawnerData spawner = worldSpawners.get(i);
            inv.addItem(createSpawnerInfoItem(spawner));
        }

        // Add navigation buttons and back button
        addNavigationButtons(inv, page, totalPages);
        addBackButton(inv);

        // Add decoration
        ItemStack decoration = createDecorationItem();
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, decoration);
            }
        }

        player.openInventory(inv);
    }

    private ItemStack createSpawnerInfoItem(SpawnerData spawner) {
        ItemStack spawnerItem = SpawnerHeadManager.getCustomHead(spawner.getEntityType());
        ItemMeta meta = spawnerItem.getItemMeta();
        Location loc = spawner.getSpawnerLocation();

        // Set display name with spawner ID
        meta.setDisplayName(languageManager.getMessage("spawner-list.spawner-item.name",
                Map.of("{id}", String.valueOf(spawner.getSpawnerId()))));

        List<String> lore = new ArrayList<>();

        // Add separator
        lore.add(languageManager.getMessage("spawner-list.spawner-item.lore.separator"));

        // Add entity info
        lore.add(languageManager.getMessage("spawner-list.spawner-item.lore.entity",
                Map.of("{entity}", formatEntityName(spawner.getEntityType().name()))));

        // Add stack size
        lore.add(languageManager.getMessage("spawner-list.spawner-item.lore.stack_size",
                Map.of("{size}", String.valueOf(spawner.getStackSize()))));

        // Add status
        String statusPath = spawner.getSpawnerStop() ?
                "spawner-list.spawner-item.lore.status.inactive" :
                "spawner-list.spawner-item.lore.status.active";
        lore.add(languageManager.getMessage(statusPath));

        // Add location info
        lore.add(languageManager.getMessage("spawner-list.spawner-item.lore.location",
                Map.of(
                        "{x}", String.valueOf(loc.getBlockX()),
                        "{y}", String.valueOf(loc.getBlockY()),
                        "{z}", String.valueOf(loc.getBlockZ())
                )));

        // Add bottom separator
        lore.add(languageManager.getMessage("spawner-list.spawner-item.lore.separator"));

        // Add teleport instruction
        lore.add(languageManager.getMessage("spawner-list.spawner-item.lore.teleport"));

        meta.setLore(lore);
        spawnerItem.setItemMeta(meta);
        return spawnerItem;
    }

    private String formatEntityName(String name) {
        return Arrays.stream(name.toLowerCase().split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private void addNavigationButtons(Inventory inv, int currentPage, int totalPages) {
        if (currentPage > 1) {
            ItemStack previousPage = new ItemStack(Material.ARROW);
            ItemMeta previousMeta = previousPage.getItemMeta();
            previousMeta.setDisplayName(languageManager.getMessage("spawner-list.navigation.previous-page"));
            previousPage.setItemMeta(previousMeta);
            inv.setItem(45, previousPage);
        }

        if (currentPage < totalPages) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(languageManager.getMessage("spawner-list.navigation.next-page"));
            nextPage.setItemMeta(nextMeta);
            inv.setItem(53, nextPage);
        }
    }

    private void addBackButton(Inventory inv) {
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta meta = backButton.getItemMeta();
        meta.setDisplayName(languageManager.getMessage("spawner-list.navigation.back"));
        backButton.setItemMeta(meta);
        inv.setItem(49, backButton);
    }

    // Inventory Holders
    public static class WorldSelectionHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static class SpawnerListHolder implements InventoryHolder {
        private final int currentPage;
        private final int totalPages;
        private final String worldName;

        public SpawnerListHolder(int currentPage, int totalPages, String worldName) {
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.worldName = worldName;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public String getWorldName() {
            return worldName;
        }
    }
}
