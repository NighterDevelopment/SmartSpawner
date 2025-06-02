package github.nighter.smartspawner.hooks.shops.api.shopguiplus;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.hooks.shops.IShopIntegration;
import github.nighter.smartspawner.hooks.shops.SaleLogger;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyManager;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * ShopGuiPlus integration for selling items from a spawner's virtual inventory asynchronously using Bukkit's scheduler.
 */
public class ShopGuiPlus implements IShopIntegration {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final boolean isLoggingEnabled;
    private final boolean isTaxEnabled;
    private final double taxPercentage;
    private static final long TRANSACTION_TIMEOUT_MS = 5000; // 5 seconds timeout
    private final Set<UUID> pendingSales = ConcurrentHashMap.newKeySet();

    public ShopGuiPlus(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.isLoggingEnabled = plugin.getConfig().getBoolean("log_transactions.enabled", true);
        this.isTaxEnabled = plugin.getConfig().getBoolean("tax.enabled", false);
        this.taxPercentage = plugin.getConfig().getDouble("tax.percentage", 10.0);
    }

    @Override
    public boolean sellAllItems(Player player, SpawnerData spawner) {
        if (!isEnabled()) {
            messageService.sendMessage(player, "shop.transaction_in_progress");
            return false;
        }

        if (pendingSales.contains(player.getUniqueId())) {
            messageService.sendMessage(player, "shop.transaction_in_progress");
            return false;
        }

        ReentrantLock lock = spawner.getLock();
        if (!lock.tryLock()) {
            messageService.sendMessage(player, "shop.transaction_in_progress");
            return false;
        }

        pendingSales.add(player.getUniqueId());
        processSaleAsync(player, spawner, lock);
        return true; // Sale is processing, keep inventory open
    }

    private void processSaleAsync(Player player, SpawnerData spawner, ReentrantLock lock) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();

        if (items.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                messageService.sendMessage(player, "shop.no_items");
                pendingSales.remove(player.getUniqueId());
                lock.unlock();
            });
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SaleCalculationResult calculation = calculateSalePrices(player, items);
            if (!calculation.isValid()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageService.sendMessage(player, "shop.no_sellable_items");
                    pendingSales.remove(player.getUniqueId());
                    lock.unlock();
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                virtualInv.removeItems(calculation.getItemsToRemove());
                Scheduler.runTask(() -> plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner));

                boolean success = processTransactions(player, calculation);
                if (!success) {
                    virtualInv.addItems(calculation.getItemsToRemove());
                    messageService.sendMessage(player, "shop.sale_failed");
                    Scheduler.runTask(() -> plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner));
                    pendingSales.remove(player.getUniqueId());
                    lock.unlock();
                    return;
                }

                if (isLoggingEnabled) {
                    logSalesAsync(calculation, player.getName());
                }

                sendSuccessMessage(player, calculation.getTotalAmount(), calculation.getTotalPrice());
                pendingSales.remove(player.getUniqueId());
                lock.unlock();
            });
        });
    }

    private boolean processTransactions(Player player, SaleCalculationResult calculation) {
        for (Map.Entry<EconomyType, Double> entry : calculation.getPricesByEconomy().entrySet()) {
            EconomyType economyType = entry.getKey();
            double totalPrice = entry.getValue();
            double finalPrice = calculateNetAmount(totalPrice);

            try {
                EconomyProvider economyProvider = ShopGuiPlusApi.getPlugin()
                    .getEconomyManager()
                    .getEconomyProvider(economyType);

                if (economyProvider == null) {
                    plugin.getLogger().severe("No economy provider for type: " + economyType);
                    return false;
                }

                // Economy deposits must be on main thread
                economyProvider.deposit(player, finalPrice);
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing transaction for economy " + economyType + ": " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    private double calculateNetAmount(double grossAmount) {
        return isTaxEnabled ? grossAmount * (1 - taxPercentage / 100.0) : grossAmount;
    }

    private void logSalesAsync(SaleCalculationResult calculation, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Map.Entry<String, SaleInfo> entry : calculation.getItemSales().entrySet()) {
                SaleInfo saleInfo = entry.getValue();
                SaleLogger.getInstance().logSale(
                    playerName,
                    entry.getKey(),
                    saleInfo.getAmount(),
                    saleInfo.getPrice(),
                    saleInfo.getEconomyType().name()
                );
            }
        });
    }

    private void sendSuccessMessage(Player player, int totalAmount, double totalPrice) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(languageManager.formatNumber(totalAmount)));
        placeholders.put("price", formatMonetaryValue(totalPrice));

        if (isTaxEnabled) {
            double grossPrice = totalPrice / (1 - taxPercentage / 100.0);
            placeholders.put("gross", formatMonetaryValue(grossPrice));
            placeholders.put("tax", String.format("%.2f", taxPercentage));
            messageService.sendMessage(player, "shop.sell_all_with_tax", placeholders);
        } else {
            messageService.sendMessage(player, "shop.sell_all", placeholders);
        }
    }

    private String formatMonetaryValue(double value) {
        return String.format("%.2f", value);
    }

    private SaleCalculationResult calculateSalePrices(Player player, Map<VirtualInventory.ItemSignature, Long> items) {
        Map<EconomyType, Double> pricesByEconomy = new HashMap<>();
        Map<String, SaleInfo> itemSales = new HashMap<>();
        List<ItemStack> itemsToRemove = new ArrayList<>();
        int totalAmount = 0;
        boolean foundSellableItem = false;

        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            if (amount <= 0) continue;

            double sellPrice = ShopGuiPlusApi.getItemStackPriceSell(player, template);
            if (sellPrice <= 0) continue;

            EconomyType economyType = getEconomyType(template);
            foundSellableItem = true;

            ItemStack itemToRemove = template.clone();
            int removeAmount = (int) Math.min(amount, Integer.MAX_VALUE);
            itemToRemove.setAmount(removeAmount);
            itemsToRemove.add(itemToRemove);

            double totalItemPrice = sellPrice * amount;
            pricesByEconomy.merge(economyType, totalItemPrice, Double::sum);
            totalAmount += removeAmount;

            String itemName = template.getType().name();
            itemSales.put(itemName, new SaleInfo(removeAmount, totalItemPrice, economyType));
        }

        return new SaleCalculationResult(pricesByEconomy, totalAmount, itemsToRemove, itemSales, foundSellableItem);
    }

    private EconomyType getEconomyType(ItemStack material) {
        return Optional.ofNullable(ShopGuiPlusApi.getItemStackShop(material))
            .map(shop -> Optional.ofNullable(shop.getEconomyType()).orElse(EconomyType.CUSTOM))
            .orElseGet(() -> Optional.ofNullable(ShopGuiPlusApi.getPlugin().getEconomyManager().getDefaultEconomyProvider())
                .map(provider -> {
                    try {
                        return EconomyType.valueOf(provider.getName().toUpperCase(Locale.US));
                    } catch (IllegalArgumentException e) {
                        return EconomyType.CUSTOM;
                    }
                })
                .orElse(EconomyType.CUSTOM));
    }

    @Override
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public boolean isEnabled() {
        return ShopGuiPlusApi.getPlugin().getShopManager().areShopsLoaded();
    }

    private static class SaleCalculationResult {
        private final Map<EconomyType, Double> pricesByEconomy;
        private final int totalAmount;
        private final List<ItemStack> itemsToRemove;
        private final Map<String, SaleInfo> itemSales;
        private final boolean valid;

        public SaleCalculationResult(Map<EconomyType, Double> pricesByEconomy, int totalAmount,
                                    List<ItemStack> itemsToRemove, Map<String, SaleInfo> itemSales, boolean valid) {
            this.pricesByEconomy = pricesByEconomy;
            this.totalAmount = totalAmount;
            this.itemsToRemove = itemsToRemove;
            this.itemSales = itemSales;
            this.valid = valid;
        }

        public Map<EconomyType, Double> getPricesByEconomy() {
            return pricesByEconomy;
        }

        public double getTotalPrice() {
            return pricesByEconomy.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        public int getTotalAmount() {
            return totalAmount;
        }

        public List<ItemStack> getItemsToRemove() {
            return itemsToRemove;
        }

        public Map<String, SaleInfo> getItemSales() {
            return itemSales;
        }

        public boolean isValid() {
            return valid;
        }
    }

    private static class SaleInfo {
        private final int amount;
        private final double price;
        private final EconomyType economyType;

        public SaleInfo(int amount, double price, EconomyType economyType) {
            this.amount = amount;
            this.price = price;
            this.economyType = economyType;
        }

        public int getAmount() {
            return amount;
        }

        public double getPrice() {
            return price;
        }

        public EconomyType getEconomyType() {
            return economyType;
        }
    }
}
