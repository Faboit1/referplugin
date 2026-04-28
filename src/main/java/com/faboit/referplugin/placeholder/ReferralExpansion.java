package com.faboit.referplugin.placeholder;

import com.faboit.referplugin.ReferPlugin;
import com.faboit.referplugin.model.PlayerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers the following custom PAPI placeholders:
 *
 * <ul>
 *   <li>{@code %referplugin_referral_count%}       – total successful referrals</li>
 *   <li>{@code %referplugin_referral_total%}        – total referrals (incl. blocked)</li>
 *   <li>{@code %referplugin_referral_blocked%}      – blocked referrals</li>
 *   <li>{@code %referplugin_referral_total_rewards%}– total money rewarded</li>
 *   <li>{@code %referplugin_referral_profile%}      – active reward profile name</li>
 * </ul>
 */
public class ReferralExpansion extends PlaceholderExpansion {

    private final ReferPlugin plugin;

    public ReferralExpansion(ReferPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "referplugin"; }
    @Override public @NotNull String getAuthor()     { return "Faboit"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        PlayerStats stats = plugin.getDb().getPlayerStats(player.getUniqueId());

        return switch (params.toLowerCase()) {
            case "referral_count"        -> stats != null ? String.valueOf(stats.getSuccessfulReferrals()) : "0";
            case "referral_total"        -> stats != null ? String.valueOf(stats.getTotalReferrals()) : "0";
            case "referral_blocked"      -> stats != null ? String.valueOf(stats.getBlockedReferrals()) : "0";
            case "referral_total_rewards"-> stats != null ? String.format("%.2f", stats.getTotalRewards()) : "0.00";
            case "referral_profile"      -> stats != null ? stats.getRewardProfile() : "default";
            default                      -> null;
        };
    }
}
