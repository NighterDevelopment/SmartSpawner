package github.nighter.smartspawner.logging;

import github.nighter.smartspawner.api.events.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Event listener that automatically logs spawner-related events.
 * Listens to all spawner events and delegates to the SpawnerActionLogger.
 */
public class SpawnerAuditListener implements Listener {
    private final SpawnerActionLogger logger;
    
    public SpawnerAuditListener(SpawnerActionLogger logger) {
        this.logger = logger;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerPlace(SpawnerPlaceEvent event) {
        logger.log(new SpawnerLogEntry.Builder(SpawnerEventType.SPAWNER_PLACE)
                .player(event.getPlayer().getName(), event.getPlayer().getUniqueId())
                .location(event.getLocation())
                .entityType(event.getEntityType())
                .metadata("quantity", event.getQuantity())
                .build());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerBreak(SpawnerBreakEvent event) {
        logger.log(new SpawnerLogEntry.Builder(SpawnerEventType.SPAWNER_BREAK)
                .location(event.getLocation())
                .metadata("quantity", event.getQuantity())
                .build());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerPlayerBreak(SpawnerPlayerBreakEvent event) {
        logger.log(new SpawnerLogEntry.Builder(SpawnerEventType.SPAWNER_BREAK)
                .player(event.getPlayer().getName(), event.getPlayer().getUniqueId())
                .location(event.getLocation())
                .metadata("quantity", event.getQuantity())
                .build());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerExplode(SpawnerExplodeEvent event) {
        logger.log(new SpawnerLogEntry.Builder(SpawnerEventType.SPAWNER_EXPLODE)
                .location(event.getLocation())
                .metadata("quantity", event.getQuantity())
                .metadata("cause", event.getClass().getSimpleName())
                .build());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerStack(SpawnerStackEvent event) {
        SpawnerEventType eventType = event.getSource() == SpawnerStackEvent.StackSource.PLACE ? 
                SpawnerEventType.SPAWNER_STACK_HAND : 
                SpawnerEventType.SPAWNER_STACK_GUI;
        
        int amountAdded = event.getNewStackSize() - event.getOldStackSize();
        
        logger.log(new SpawnerLogEntry.Builder(eventType)
                .player(event.getPlayer().getName(), event.getPlayer().getUniqueId())
                .location(event.getLocation())
                .metadata("amount_added", amountAdded)
                .metadata("old_stack_size", event.getOldStackSize())
                .metadata("new_stack_size", event.getNewStackSize())
                .build());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerGUIOpen(SpawnerOpenGUIEvent event) {
        logger.log(new SpawnerLogEntry.Builder(SpawnerEventType.SPAWNER_GUI_OPEN)
                .player(event.getPlayer().getName(), event.getPlayer().getUniqueId())
                .location(event.getLocation())
                .entityType(event.getEntityType())
                .build());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerExpClaim(SpawnerExpClaimEvent event) {
        logger.log(new SpawnerLogEntry.Builder(SpawnerEventType.SPAWNER_EXP_CLAIM)
                .player(event.getPlayer().getName(), event.getPlayer().getUniqueId())
                .location(event.getLocation())
                .metadata("exp_amount", event.getExpAmount())
                .build());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSell(SpawnerSellEvent event) {
        int itemsSold = event.getItems().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
        
        logger.log(new SpawnerLogEntry.Builder(SpawnerEventType.SPAWNER_SELL_ALL)
                .player(event.getPlayer().getName(), event.getPlayer().getUniqueId())
                .location(event.getLocation())
                .metadata("total_value", event.getMoneyAmount())
                .metadata("items_sold", itemsSold)
                .build());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerEggChange(SpawnerEggChangeEvent event) {
        logger.log(new SpawnerLogEntry.Builder(SpawnerEventType.SPAWNER_EGG_CHANGE)
                .player(event.getPlayer().getName(), event.getPlayer().getUniqueId())
                .location(event.getLocation())
                .entityType(event.getNewEntityType())
                .metadata("old_entity", event.getOldEntityType().name())
                .metadata("new_entity", event.getNewEntityType().name())
                .build());
    }
}
