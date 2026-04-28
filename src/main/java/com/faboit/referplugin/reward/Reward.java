package com.faboit.referplugin.reward;

import org.bukkit.entity.Player;

/**
 * A single reward unit that can be applied to a player.
 */
public interface Reward {

    /**
     * Apply this reward to the target player.
     *
     * @param player     The player receiving the reward.
     * @param multiplier Multiplier from permission modifiers (1.0 = normal).
     */
    void apply(Player player, double multiplier);
}
