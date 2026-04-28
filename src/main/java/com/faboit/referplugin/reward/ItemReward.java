package com.faboit.referplugin.reward;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Gives a preconfigured item to the player.
 * Items that do not fit in the player's inventory are dropped at their feet.
 */
public class ItemReward implements Reward {

    private final ItemStack item;

    public ItemReward(ItemStack item) {
        this.item = item;
    }

    @Override
    public void apply(Player player, double multiplier) {
        if (item == null) return;
        ItemStack toGive = item.clone();
        // Drop overflow items at the player's location
        player.getInventory().addItem(toGive).values()
              .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    public ItemStack getItem() { return item; }
}
