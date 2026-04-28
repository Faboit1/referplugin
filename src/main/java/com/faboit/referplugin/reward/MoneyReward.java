package com.faboit.referplugin.reward;

import com.faboit.referplugin.ReferPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Deposits money into the player's Vault economy account.
 */
public class MoneyReward implements Reward {

    private final Economy economy;
    private final double amount;
    private final Logger log;

    public MoneyReward(Economy economy, double amount, Logger log) {
        this.economy = economy;
        this.amount = amount;
        this.log = log;
    }

    @Override
    public void apply(Player player, double multiplier) {
        if (economy == null) {
            log.warning("Vault Economy not available — skipping money reward.");
            return;
        }
        double finalAmount = amount * multiplier;
        economy.depositPlayer(player, finalAmount);
    }

    public double getAmount() { return amount; }
}
