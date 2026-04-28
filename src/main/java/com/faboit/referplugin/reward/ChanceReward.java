package com.faboit.referplugin.reward;

import com.faboit.referplugin.model.RewardProfile;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * A wrapper reward that only applies its inner rewards at a configured probability.
 */
public class ChanceReward implements Reward {

    private final List<Reward> innerRewards;
    private final double chance;  // 0.0 – 1.0

    public ChanceReward(double chance, List<Reward> innerRewards) {
        this.chance = Math.max(0, Math.min(1, chance));
        this.innerRewards = innerRewards;
    }

    @Override
    public void apply(Player player, double multiplier) {
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            for (Reward r : innerRewards) {
                r.apply(player, multiplier);
            }
        }
    }

    public double getChance() { return chance; }
}
