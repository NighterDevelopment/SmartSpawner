package github.nighter.smartspawner.spawner.lootgen;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record LootResult(List<ItemStack> items, long experience) {
}