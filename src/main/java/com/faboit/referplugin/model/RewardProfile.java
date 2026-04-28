package com.faboit.referplugin.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A reward profile defines what a referrer or joiner receives.
 */
public class RewardProfile {

    private final String name;
    private double money;
    private int xp;
    private final List<String> commands;
    private final List<Map<String, Object>> items;
    private final List<ChanceRewardEntry> chanceRewards;

    public RewardProfile(String name) {
        this.name = name;
        this.commands = new ArrayList<>();
        this.items = new ArrayList<>();
        this.chanceRewards = new ArrayList<>();
    }

    public String getName() { return name; }

    public double getMoney() { return money; }
    public void setMoney(double money) { this.money = money; }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public List<String> getCommands() { return commands; }
    public List<Map<String, Object>> getItems() { return items; }
    public List<ChanceRewardEntry> getChanceRewards() { return chanceRewards; }

    /**
     * A chance-based reward: only given with a defined probability.
     */
    public static class ChanceRewardEntry {
        private final double chance;   // 0.0–1.0
        private final double money;
        private final int xp;
        private final List<String> commands;

        public ChanceRewardEntry(double chance, double money, int xp, List<String> commands) {
            this.chance = chance;
            this.money = money;
            this.xp = xp;
            this.commands = commands != null ? commands : new ArrayList<>();
        }

        public double getChance() { return chance; }
        public double getMoney() { return money; }
        public int getXp() { return xp; }
        public List<String> getCommands() { return commands; }
    }
}
