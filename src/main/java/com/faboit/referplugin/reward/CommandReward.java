package com.faboit.referplugin.reward;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Runs one or more console commands (with PAPI placeholder support).
 * Commands may include %referrer% and %joiner% which are pre-substituted
 * by {@link com.faboit.referplugin.reward.RewardManager}.
 */
public class CommandReward implements Reward {

    private final Plugin plugin;
    private final String command;

    public CommandReward(Plugin plugin, String command) {
        this.plugin = plugin;
        this.command = command;
    }

    @Override
    public void apply(Player player, double multiplier) {
        String cmd = command;

        // Apply PlaceholderAPI if available
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            cmd = PlaceholderAPI.setPlaceholders(player, cmd);
        }

        String finalCmd = cmd;
        Bukkit.getScheduler().runTask(plugin,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
    }

    public String getCommand() { return command; }
}
