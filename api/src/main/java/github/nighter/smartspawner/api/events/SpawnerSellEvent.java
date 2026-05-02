package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Called when items are sold from a spawner's storage.
 */
@Getter
@Setter
public class SpawnerSellEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final Location location;
    private final List<ItemStack> items;
    private final EntityType entityType;
    private double moneyAmount;
    private boolean cancelled = false;

    /**
     * Creates a new spawner sell event.
     *
     * @param player the player selling the items
     * @param location the location of the spawner
     * @param items the items being sold
     * @param moneyAmount the amount of money to be given
     */
    public SpawnerSellEvent(Player player, Location location, List<ItemStack> items, double moneyAmount) {
        this(player, location, items, moneyAmount, null);
    }

    /**
     * Creates a new spawner sell event.
     *
     * @param player the player selling the items
     * @param location the location of the spawner
     * @param items the items being sold
     * @param moneyAmount the amount of money to be given
     * @param entityType the spawned entity type of the selling spawner
     */
    public SpawnerSellEvent(Player player, Location location, List<ItemStack> items, double moneyAmount,
                            @Nullable EntityType entityType) {
        this.player = player;
        this.location = location;
        this.items = items;
        this.moneyAmount = moneyAmount;
        this.entityType = entityType;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
