package com.faboit.referplugin.model;

import java.util.UUID;

/**
 * Immutable snapshot of a player's referral statistics.
 */
public class PlayerStats {

    private final UUID uuid;
    private final String username;
    private final String rewardProfile;
    private final int totalReferrals;
    private final int successfulReferrals;
    private final int blockedReferrals;
    private final double totalRewards;
    private final long lastReferralTimestamp;

    public PlayerStats(UUID uuid, String username, String rewardProfile,
                       int totalReferrals, int successfulReferrals, int blockedReferrals,
                       double totalRewards, long lastReferralTimestamp) {
        this.uuid = uuid;
        this.username = username;
        this.rewardProfile = rewardProfile;
        this.totalReferrals = totalReferrals;
        this.successfulReferrals = successfulReferrals;
        this.blockedReferrals = blockedReferrals;
        this.totalRewards = totalRewards;
        this.lastReferralTimestamp = lastReferralTimestamp;
    }

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public String getRewardProfile() { return rewardProfile; }
    public int getTotalReferrals() { return totalReferrals; }
    public int getSuccessfulReferrals() { return successfulReferrals; }
    public int getBlockedReferrals() { return blockedReferrals; }
    public double getTotalRewards() { return totalRewards; }
    public long getLastReferralTimestamp() { return lastReferralTimestamp; }
}
