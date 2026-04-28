package com.faboit.referplugin.reward;

import com.faboit.referplugin.config.ConfigManager;
import com.faboit.referplugin.model.RewardProfile;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * Resolves and applies reward profiles for referrers and joiners.
 *
 * <p>Resolution order (referrer side):
 * <ol>
 *   <li>Per-player custom profile (username match)</li>
 *   <li>Default referrer profile</li>
 * </ol>
 * Permission modifiers are then stacked on top.
 */
public class RewardManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private Economy economy;
    private final Logger log;

    public RewardManager(Plugin plugin, ConfigManager configManager, Logger log) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.log = log;
    }

    public void setEconomy(Economy economy) {
        this.economy = economy;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Resolve and apply the referrer's reward.
     *
     * @param referrer   The player who sent the referral link.
     * @param joiner     The player who joined through the link (used for placeholder replacement).
     * @return The total monetary value of rewards applied (for stats tracking).
     */
    public double applyReferrerReward(Player referrer, Player joiner) {
        RewardProfile profile = resolveReferrerProfile(referrer);
        double multiplier = getPermissionMultiplier(referrer);
        return applyProfile(referrer, joiner, profile, multiplier);
    }

    /**
     * Resolve and apply the joiner's reward.
     *
     * @param joiner    The player who joined.
     * @param referrer  The referrer (used for placeholder replacement).
     * @return The total monetary value of rewards applied.
     */
    public double applyJoinerReward(Player joiner, Player referrer) {
        RewardProfile profile = resolveJoinerProfile(joiner);
        double multiplier = getPermissionMultiplier(joiner);
        return applyProfile(joiner, referrer, profile, multiplier);
    }

    /**
     * Apply a referrer reward by profile config path and multiplier.
     * Used for delivering pending rewards to referrers who were offline.
     *
     * @param referrer    The player receiving the reward.
     * @param joinerName  The joiner's name (for placeholder replacement).
     * @param profilePath Config path of the reward profile.
     * @param multiplier  Reward multiplier to apply on top of any permission modifier.
     */
    public void applyReferrerRewardFromPath(Player referrer, String joinerName,
                                             String profilePath, double multiplier) {
        RewardProfile profile          = loadProfile(profilePath, profilePath);
        double        effectiveMulti   = multiplier * getPermissionMultiplier(referrer);

        if (profile.getMoney() > 0) {
            new MoneyReward(economy, profile.getMoney(), log).apply(referrer, effectiveMulti);
        }
        if (profile.getXp() > 0) {
            new XPReward(profile.getXp()).apply(referrer, effectiveMulti);
        }
        // Commands — %referrer% = referrer's name, %joiner% = joiner's name
        for (String cmd : profile.getCommands()) {
            String resolved = cmd
                    .replace("%referrer%", referrer.getName())
                    .replace("%joiner%",   joinerName);
            new CommandReward(plugin, resolved).apply(referrer, effectiveMulti);
        }
        // Chance rewards
        for (RewardProfile.ChanceRewardEntry entry : profile.getChanceRewards()) {
            List<Reward> inner = new ArrayList<>();
            if (entry.getMoney() > 0)
                inner.add(new MoneyReward(economy, entry.getMoney(), log));
            if (entry.getXp() > 0)
                inner.add(new XPReward(entry.getXp()));
            for (String cmd : entry.getCommands()) {
                String resolved = cmd
                        .replace("%referrer%", referrer.getName())
                        .replace("%joiner%",   joinerName);
                inner.add(new CommandReward(plugin, resolved));
            }
            new ChanceReward(entry.getChance(), inner).apply(referrer, effectiveMulti);
        }
    }

    /**
     * Resolve a referrer's reward profile without applying it (used for preview).
     */
    public RewardProfile resolveReferrerProfile(Player referrer) {
        ConfigurationSection custom = configManager.getConfig()
                .getConfigurationSection("custom-referrer-profiles");
        if (custom != null) {
            for (String key : custom.getKeys(false)) {
                if (key.equalsIgnoreCase(referrer.getName())) {
                    return loadProfile("custom-referrer-profiles." + key, key);
                }
            }
        }
        return loadProfile("reward-profiles.default_referrer", "default_referrer");
    }

    public RewardProfile resolveJoinerProfile(Player joiner) {
        ConfigurationSection custom = configManager.getConfig()
                .getConfigurationSection("custom-joiner-profiles");
        if (custom != null) {
            for (String key : custom.getKeys(false)) {
                if (key.equalsIgnoreCase(joiner.getName())) {
                    return loadProfile("custom-joiner-profiles." + key, key);
                }
            }
        }
        return loadProfile("reward-profiles.default_joiner", "default_joiner");
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private double applyProfile(Player recipient, Player other,
                                 RewardProfile profile, double multiplier) {
        double moneyGiven = 0;

        // Money
        if (profile.getMoney() > 0) {
            new MoneyReward(economy, profile.getMoney(), log).apply(recipient, multiplier);
            moneyGiven += profile.getMoney() * multiplier;
        }

        // XP
        if (profile.getXp() > 0) {
            new XPReward(profile.getXp()).apply(recipient, multiplier);
        }

        // Commands (replace %referrer%/%joiner% before dispatching)
        for (String cmd : profile.getCommands()) {
            String resolved = cmd
                    .replace("%referrer%", other != null ? other.getName() : "")
                    .replace("%joiner%",   recipient.getName());
            new CommandReward(plugin, resolved).apply(recipient, multiplier);
        }

        // Items
        for (Map<String, Object> itemMap : profile.getItems()) {
            ItemStack item = buildItemStack(itemMap);
            if (item != null) new ItemReward(item).apply(recipient, multiplier);
        }

        // Chance-based rewards
        for (RewardProfile.ChanceRewardEntry entry : profile.getChanceRewards()) {
            List<Reward> inner = new ArrayList<>();
            if (entry.getMoney() > 0)
                inner.add(new MoneyReward(economy, entry.getMoney(), log));
            if (entry.getXp() > 0)
                inner.add(new XPReward(entry.getXp()));
            for (String cmd : entry.getCommands()) {
                String resolved = cmd
                        .replace("%referrer%", other != null ? other.getName() : "")
                        .replace("%joiner%",   recipient.getName());
                inner.add(new CommandReward(plugin, resolved));
            }
            new ChanceReward(entry.getChance(), inner).apply(recipient, multiplier);
        }

        return moneyGiven;
    }

    private RewardProfile loadProfile(String path, String name) {
        RewardProfile profile = new RewardProfile(name);
        ConfigurationSection sec = configManager.getConfig().getConfigurationSection(path);
        if (sec == null) return profile;

        profile.setMoney(sec.getDouble("money", 0));
        profile.setXp(sec.getInt("xp", 0));
        profile.getCommands().addAll(sec.getStringList("commands"));

        // Items
        List<?> rawItems = sec.getList("items");
        if (rawItems != null) {
            for (Object raw : rawItems) {
                if (raw instanceof Map<?,?> map) {
                    Map<String, Object> itemMap = new HashMap<>();
                    map.forEach((k, v) -> itemMap.put(String.valueOf(k), v));
                    profile.getItems().add(itemMap);
                }
            }
        }

        // Chance rewards
        List<?> rawChance = sec.getList("chance-rewards");
        if (rawChance != null) {
            for (Object raw : rawChance) {
                if (raw instanceof Map<?,?> map) {
                    double chance = parseDouble(map.get("chance"), 0.1);
                    double money  = parseDouble(map.get("money"), 0);
                    int    xp     = parseInt(map.get("xp"), 0);
                    List<String> cmds = new ArrayList<>();
                    Object cmdsRaw = map.get("commands");
                    if (cmdsRaw instanceof List<?> l) {
                        l.forEach(o -> cmds.add(String.valueOf(o)));
                    }
                    profile.getChanceRewards().add(
                            new RewardProfile.ChanceRewardEntry(chance, money, xp, cmds));
                }
            }
        }

        return profile;
    }

    private double getPermissionMultiplier(Player player) {
        ConfigurationSection mods = configManager.getConfig()
                .getConfigurationSection("permission-modifiers");
        if (mods == null) return 1.0;

        double multiplier = 1.0;
        boolean applied = false;
        for (String perm : mods.getKeys(false)) {
            if (player.hasPermission(perm)) {
                double mod = mods.getDouble(perm + ".money-multiplier", 1.0);
                boolean stack = mods.getBoolean(perm + ".stack", true);
                if (!applied) {
                    multiplier = mod;
                    applied = true;
                } else if (stack) {
                    multiplier *= mod;
                } else {
                    multiplier = Math.max(multiplier, mod);
                }
            }
        }
        return multiplier;
    }

    private ItemStack buildItemStack(Map<String, Object> map) {
        try {
            String matName = String.valueOf(map.getOrDefault("material", "AIR"));
            Material mat = Material.matchMaterial(matName.toUpperCase());
            if (mat == null) {
                log.warning("Unknown material in item reward: " + matName);
                return null;
            }
            int amount = parseInt(map.get("amount"), 1);
            return new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        } catch (Exception e) {
            log.warning("Error building item reward: " + e.getMessage());
            return null;
        }
    }

    private double parseDouble(Object o, double def) {
        if (o == null) return def;
        try { return Double.parseDouble(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }

    private int parseInt(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
