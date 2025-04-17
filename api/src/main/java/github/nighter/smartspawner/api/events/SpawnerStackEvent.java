package github.nighter.smartspawner.api.events;

import github.nighter.smartspawner.api.SmartSpawnerAPI;
import github.nighter.smartspawner.api.SmartSpawnerPlugin;
import github.nighter.smartspawner.api.SmartSpawnerProvider;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SpawnerStackEvent extends Event implements Cancellable {
    private final Player player;
    private Location location;
    private int oldQuantity, newQuantity;
    private final StackSource source;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    /**
     * The constructor for the event.
     *
     * @param player The player who added a spawner at the stack
     * @param location The location of the spawner.
     * @param oldQuantity The old quantity of the spawner.
     * @param newQuantity The new quantity of the spawner
     */
    public SpawnerStackEvent(Player player, Location location, int oldQuantity, int newQuantity, StackSource source) {
        this.player = player;
        this.location = location;
        this.oldQuantity = oldQuantity;
        this.newQuantity = newQuantity;
        this.source = source;
    }

    public SpawnerStackEvent(Player player, Location location, int oldQuantity, int newQuantity) {
        this(player, location, oldQuantity, newQuantity, StackSource.PLACE);
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location;
    }

    public int getOldQuantity() {
        return oldQuantity;
    }

    public int getNewQuantity() {
        return newQuantity;
    }

    public StackSource getSource() {
        return source;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public @NotNull HandlerList getHandlerList() {
        return handlers;
    }

    public enum StackSource {
        PLACE, GUI;
    }
}

