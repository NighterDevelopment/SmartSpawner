package github.nighter.smartspawner.hooks.economy.shops.providers.shopguiplus;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import net.brcdev.shopgui.spawner.external.provider.ExternalSpawnerProvider;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;


public class SpawnerProvider implements ExternalSpawnerProvider {
    private final SpawnerItemFactory spawnerItemFactory;

    public SpawnerProvider(SmartSpawner plugin) {
        this.spawnerItemFactory = plugin.getSpawnerItemFactory();
    }

    @Override
    public String getName() {
        return "SmartSpawner";
    }

    @Override
    public ItemStack getSpawnerItem(EntityType entityType) {
        return spawnerItemFactory.createSmartSpawnerItem(entityType);
    }

    @Override
    public EntityType getSpawnerEntityType(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.SPAWNER) {
            return null;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return null;
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        CreatureSpawner spawner = (CreatureSpawner) blockMeta.getBlockState();

        EntityType entityType = spawner.getSpawnedType();
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return null;
        }

        return entityType;
    }
}
