package me.nighter.smartSpawner.hooks;

import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.api.prices.AdvancedSellPrice;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.util.EcoType;
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

    public EconomyShopGUI(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public boolean sellAllItems(Player player, SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> items = virtualInv.getAllItems();

        if (items.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-items");
            return false;
        }

        int totalAmount = 0;
        boolean foundSellableItem = false;
        Map<ShopItem, Integer> soldItems = new HashMap<>();
        Map<EcoType, Double> prices = new HashMap<>();

        // First pass: Calculate amounts and prices
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            ItemStack item = entry.getValue();
            if (item != null && item.getType() != Material.AIR) {
                ShopItem shopItem = EconomyShopGUIHook.getShopItem(player, item);
                if (shopItem == null) continue;

                if (EconomyShopGUIHook.isSellAble(shopItem)) {
                    foundSellableItem = true;

                    // Check sell limit
                    int limit = getSellLimit(shopItem, player.getUniqueId(), item.getAmount());
                    if (limit == -1) continue;

                    // Check max sell per transaction
                    limit = getMaxSell(shopItem, limit, soldItems.getOrDefault(shopItem, 0));
                    if (limit == -1) continue;

                    calculateSellPrice(prices, shopItem, player, item, limit, totalAmount);
                    totalAmount += limit;
                    soldItems.put(shopItem, soldItems.getOrDefault(shopItem, 0) + limit);
                }
            }
        }

        if (!foundSellableItem) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
            return false;
        }

        if (totalAmount > 0) {
            // Synchronize access to prevent concurrent modifications
            synchronized (virtualInv) {
                // Second pass: Actually remove items and update inventory
                for (Map.Entry<ShopItem, Integer> soldEntry : soldItems.entrySet()) {
                    ShopItem shopItem = soldEntry.getKey();
                    int remainingToRemove = soldEntry.getValue();

                    // Iterate through inventory slots
                    for (Map.Entry<Integer, ItemStack> invEntry : new HashMap<>(items).entrySet()) {
                        if (remainingToRemove <= 0) break;

                        int slot = invEntry.getKey();
                        ItemStack item = invEntry.getValue();

                        if (item != null && item.getType() != Material.AIR) {
                            ShopItem currentShopItem = EconomyShopGUIHook.getShopItem(player, item);
                            if (currentShopItem != null && currentShopItem.equals(shopItem)) {
                                int amountToRemove = Math.min(remainingToRemove, item.getAmount());
                                remainingToRemove -= amountToRemove;

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

            // Update stock limits and dynamic pricing
            updateShopStats(soldItems, player.getUniqueId());

            // Give money to player
            for (Map.Entry<EcoType, Double> entry : prices.entrySet()) {
                if (isClaimableCurrency(entry.getKey())) {
                    EconomyShopGUIHook.getEcon(entry.getKey()).depositBalance(player, entry.getValue());
                }
            }

            // Send success message
            StringBuilder sb = new StringBuilder();
            prices.forEach((type, value) -> {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(formatMonetaryValue(value, type));
            });
            plugin.getLanguageManager().sendMessage(player, "messages.sell-all",
                    "%amount%", String.valueOf(totalAmount),
                    "%price%", sb.toString());
            return true;
        }

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
                    formattedAmount = df.format(value);
                    return formattedAmount;

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