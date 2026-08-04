package github.nighter.smartspawner.hooks.drops;

import github.nighter.smartspawner.SmartSpawner;
import io.lumine.mythic.bukkit.adapters.item.ItemComponentBukkitItemStack;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import io.lumine.mythic.core.drops.droppables.VanillaItemDrop;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.logging.Level;

public class MythicMobsHook implements Listener {
    private final Random random;

    public MythicMobsHook() {
        random = new Random();
    }

    @EventHandler
    public void onMythicDropLoad(MythicDropLoadEvent e) {
        if (!e.getDropName().equalsIgnoreCase("smartspawner")) return;
        String[] parts = e.getContainer().getLine().split(" ");
        if (parts.length < 2) return;
        String entity = parts[1].toUpperCase();
        int amount = 1;
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entity);
            if (parts.length > 2) {
                String s = parts[2].trim();
                boolean isRange = s.contains("-");
                if (isRange) {
                    String[] amounts = s.split("-");
                    if (amounts.length < 2) throw new NumberFormatException();
                    int i = Integer.parseInt(amounts[0]);
                    int j = Integer.parseInt(amounts[1]);
                    int min = Math.min(i, j);
                    int max = Math.max(i, j);
                    amount = random.nextInt(max - min + 1) + min;
                } else amount = Integer.parseInt(s);
            }
        } catch (NumberFormatException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Amount is not a valid number or range in a MythicMobs drop (%s)", e.getDropName());
            return;
        } catch (IllegalArgumentException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Entity is not valid in a MythicMobs drop (%s)", e.getDropName());
            return;
        }

        ItemStack iS = SmartSpawner.getInstance().getSpawnerItemFactory().createSmartSpawnerItem(entityType, amount);
        if (iS == null) return;

        e.register(new VanillaItemDrop(e.getContainer().getLine(), e.getConfig(), new ItemComponentBukkitItemStack(iS)));
    }
}
