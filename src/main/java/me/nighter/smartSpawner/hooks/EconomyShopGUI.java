package me.nighter.smartSpawner.hooks;

import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.prices.AdvancedSellPrice;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.EcoType;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.utils.VirtualInventory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import me.nighter.smartSpawner.SmartSpawner;

public class EconomyShopGUI {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;

    public EconomyShopGUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
    }

    private Map<EcoType, Double> applyTax(Map<EcoType, Double> originalPrices) {
        double taxPercentage = plugin.getConfigManager().getTaxPercentage();
        Map<EcoType, Double> taxedPrices = new HashMap<>();
        for (Map.Entry<EcoType, Double> entry : originalPrices.entrySet()) {
            double originalPrice = entry.getValue();
            double taxAmount = originalPrice * (taxPercentage / 100.0);
            double afterTaxPrice = originalPrice - taxAmount;
            taxedPrices.put(entry.getKey(), afterTaxPrice);
        }
        return taxedPrices;
    }

    public boolean sellAllItems(Player player, SpawnerData spawner) {
        // Retrieve the virtual inventory from the spawner
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> items = virtualInv.getAllItems();

        // Check if inventory is empty
        if (items.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-items");
            return false;
        }

        // Initialize variables for tracking sale process
        int totalAmount = 0;
        boolean foundSellableItem = false;
        Map<ShopItem, Integer> soldItems = new HashMap<>();
        Map<EcoType, Double> prices = new HashMap<>();

        // First pass: Scan through inventory to calculate sellable items
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            ItemStack item = entry.getValue();
            if (item != null && item.getType() != Material.AIR) {
                // Get shop item information
                ShopItem shopItem = EconomyShopGUIHook.getShopItem(player, item);
                if (shopItem == null) continue;

                // Check if item is sellable
                if (EconomyShopGUIHook.isSellAble(shopItem)) {
                    foundSellableItem = true;

                    // Check sell limits and restrictions
                    // Verify if player can sell the entire item stack
                    int limit = getSellLimit(shopItem, player.getUniqueId(), item.getAmount());
                    if (limit == -1) continue;

                    // Additional check for max sell per transaction
                    limit = getMaxSell(shopItem, limit, soldItems.getOrDefault(shopItem, 0));
                    if (limit == -1) continue;

                    // Calculate selling price for the item
                    calculateSellPrice(prices, shopItem, player, item, limit, totalAmount);
                    totalAmount += limit;
                    soldItems.put(shopItem, soldItems.getOrDefault(shopItem, 0) + limit);
                }
            }
        }

        // If no sellable items were found
        if (!foundSellableItem) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
            return false;
        }

        // Process selling if items are available
        if (totalAmount > 0) {
            // Check tax configuration
            double taxPercentage = plugin.getConfigManager().getTaxPercentage();

            // Original prices before tax
            Map<EcoType, Double> preTaxPrices = new HashMap<>(prices);
            // Prices after tax calculation
            Map<EcoType, Double> afterTaxPrices = prices;

            // Apply tax if enabled
            if (taxPercentage > 0) {
                afterTaxPrices = applyTax(prices);
            }

            // Thread-safe inventory modification
            synchronized (virtualInv) {
                // Remove sold items from virtual inventory
                for (Map.Entry<ShopItem, Integer> soldEntry : soldItems.entrySet()) {
                    ShopItem shopItem = soldEntry.getKey();
                    int remainingToRemove = soldEntry.getValue();

                    // Iterate through inventory to remove items
                    for (Map.Entry<Integer, ItemStack> invEntry : new HashMap<>(items).entrySet()) {
                        if (remainingToRemove <= 0) break;

                        int slot = invEntry.getKey();
                        ItemStack item = invEntry.getValue();

                        // Verify and remove matching items
                        if (item != null && item.getType() != Material.AIR) {
                            ShopItem currentShopItem = EconomyShopGUIHook.getShopItem(player, item);
                            if (currentShopItem != null && currentShopItem.equals(shopItem)) {
                                // Calculate amount to remove
                                int amountToRemove = Math.min(remainingToRemove, item.getAmount());
                                remainingToRemove -= amountToRemove;

                                // Update inventory slot
                                if (amountToRemove >= item.getAmount()) {
                                    virtualInv.setItem(slot, null);
                                } else {
                                    ItemStack newItem = item.clone();
                                    newItem.setAmount(item.getAmount() - amountToRemove);
                                    virtualInv.setItem(slot, newItem);
                                }
                            }
                        }
                    }
                }
            }

            // Update shop statistics asynchronously
            updateShopStats(soldItems, player.getUniqueId());

            // Deposit money/resources to player
            for (Map.Entry<EcoType, Double> entry : afterTaxPrices.entrySet()) {
                if (isClaimableCurrency(entry.getKey())) {
                    EconomyShopGUIHook.getEcon(entry.getKey()).depositBalance(player, entry.getValue());
                }
            }

            // Prepare price string for messaging
            StringBuilder priceBuilder = new StringBuilder();
            afterTaxPrices.forEach((type, value) -> {
                if (priceBuilder.length() > 0) priceBuilder.append(", ");
                priceBuilder.append(formatMonetaryValue(value, type));
            });

            // Send appropriate sell message based on tax status
            if (taxPercentage > 0) {
                // Message with tax information
                plugin.getLanguageManager().sendMessage(player, "messages.sell-all-tax",
                        "%amount%", String.valueOf(totalAmount),
                        "%price%", priceBuilder.toString(),
                        "%tax%", String.format("%.2f", taxPercentage));
            } else {
                // Standard sell message without tax
                plugin.getLanguageManager().sendMessage(player, "messages.sell-all",
                        "%amount%", String.valueOf(totalAmount),
                        "%price%", priceBuilder.toString());
            }

            return true;
        }

        // Fallback message if no items could be sold
        plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
        return false;
    }

    private int getSellLimit(ShopItem shopItem, UUID playerUUID, int amount) {
        if (shopItem.getLimitedSellMode() != 0) {
            int stock = EconomyShopGUIHook.getSellLimit(shopItem, playerUUID);
            if (stock <= 0) {
                return -1;
            } else if (stock < amount) {
                amount = stock;
            }
        }
        return amount;
    }

    private int getMaxSell(ShopItem shopItem, int qty, int alreadySold) {
        if (shopItem.isMaxSell(alreadySold + qty)) {
            if (alreadySold >= shopItem.getMaxSell())
                return -1;
            qty = shopItem.getMaxSell() - alreadySold;
        }
        return qty;
    }

    private void calculateSellPrice(Map<EcoType, Double> prices, ShopItem shopItem, Player player, ItemStack item, int amount, int sold) {
        if (EconomyShopGUIHook.hasMultipleSellPrices(shopItem)) {
            AdvancedSellPrice sellPrice = EconomyShopGUIHook.getMultipleSellPrices(shopItem);
            sellPrice.getSellPrices(sellPrice.giveAll() ? null : sellPrice.getSellTypes().get(0), player, item, amount, sold)
                    .forEach((type, price) -> prices.put(type, prices.getOrDefault(type, 0d) + price));
        } else {
            double sellPrice = EconomyShopGUIHook.getItemSellPrice(shopItem, item, player, amount, sold);
            prices.put(shopItem.getEcoType(), prices.getOrDefault(shopItem.getEcoType(), 0d) + sellPrice);
        }
    }

    private void updateShopStats(Map<ShopItem, Integer> items, UUID playerUUID) {
        plugin.runTaskAsync(() -> {
            for (Map.Entry<ShopItem, Integer> entry : items.entrySet()) {
                ShopItem item = entry.getKey();
                int amount = entry.getValue();

                if (item.isRefillStock()) {
                    EconomyShopGUIHook.sellItemStock(item, playerUUID, amount);
                }
                if (item.getLimitedSellMode() != 0) {
                    EconomyShopGUIHook.sellItemLimit(item, playerUUID, amount);
                }
                if (item.isDynamicPricing()) {
                    EconomyShopGUIHook.sellItem(item, amount);
                }
            }
        });
    }

    private boolean isClaimableCurrency(EcoType ecoType) {
        return switch (ecoType.getType().name()) {
            case "ITEM", "LEVELS", "EXP", "VAULT", "PLAYER_POINTS"-> true;
            default -> false;
        };
    }

    private String formatMonetaryValue(double value, EcoType ecoType) {
        if (ecoType == null) {
            return String.format("%.2f", value); // Fallback format if ecoType is null
        }

        try {
            DecimalFormat df = new DecimalFormat("#,##0.00");
            // Set the decimal separator and grouping separator based on locale if needed
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            df.setDecimalFormatSymbols(symbols);

            String formattedAmount;
            switch (ecoType.getType()) {
                case VAULT:
                    if(configManager.getFormatedPrice()) {
                        formattedAmount = languageManager.formatNumber((long) value);
                        return formattedAmount;
                    }
                    else {
                        formattedAmount = df.format(value);
                        return formattedAmount;
                    }
                case ITEM:
                    // For items, we might want to show whole numbers if the value is integer
                    if (value == (int)value) {
                        return String.format("%d %s", (int)value, ecoType.getType().name());
                    }
                    return String.format("%.2f %s", value, ecoType.getType().name());

                case EXP, LEVELS:
                    // Always round to integer for levels and exp
                    return String.format("%d %s", Math.round(value), ecoType.getType().name());

                case PLAYER_POINTS:
                    formattedAmount = df.format(value);
                    return formattedAmount;

                default:
                    // Use the decimal format for unknown types
                    return df.format(value);
            }
        } catch (Exception e) {
            // Fallback formatting in case of any formatting errors
            return String.format("%.2f", value);
        }
    }
}