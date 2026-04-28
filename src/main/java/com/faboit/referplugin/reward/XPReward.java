package com.faboit.referplugin.reward;

import org.bukkit.entity.Player;

/**
 * Grants experience points to the player.
 */
public class XPReward implements Reward {

    private final int xpAmount;

    public XPReward(int xpAmount) {
        this.xpAmount = xpAmount;
    }

    @Override
    public void apply(Player player, double multiplier) {
        int finalXp = (int) Math.round(xpAmount * multiplier);
        if (finalXp > 0) {
            player.giveExp(finalXp);
        }
    }

    public int getXpAmount() { return xpAmount; }
}
