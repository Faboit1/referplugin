package com.faboit.referplugin.database;

import com.faboit.referplugin.model.PlayerStats;
import com.faboit.referplugin.model.ReferralRecord;

import java.util.List;
import java.util.UUID;

/**
 * Database abstraction layer for ReferPlugin.
 */
public interface Database {

    /** Initialise the schema (create tables if missing). */
    void init() throws Exception;

    /** Close connections / pool. */
    void close();

    // --- Player stats ---

    PlayerStats getPlayerStats(UUID uuid);

    void savePlayerStats(PlayerStats stats);

    /** Ensure a row exists for this player; initialise defaults if new. */
    void ensurePlayer(UUID uuid, String username);

    void incrementSuccessfulReferrals(UUID uuid, double rewardAmount);

    void incrementBlockedReferrals(UUID uuid);

    void setRewardProfile(UUID uuid, String profileName);

    List<PlayerStats> getTopReferrers(int limit);

    // --- Referral records ---

    void insertReferralRecord(UUID referrerUuid, UUID joinerUuid,
                               String joinerIp, String referralHost,
                               long timestamp, ReferralRecord.Status status,
                               boolean referrerRewardGiven, boolean joinerRewardGiven);

    List<ReferralRecord> getRecentRecords(UUID referrerUuid, int limit);

    List<ReferralRecord> getSuspiciousRecords(int limit);

    boolean hasBeenReferred(UUID joinerUuid);

    int countSuccessfulReferralsToday(UUID referrerUuid);

    int countSuccessfulReferralsThisWeek(UUID referrerUuid);

    // --- IP tracking ---

    void logIp(UUID uuid, String ip, long timestamp);

    boolean ipUsedByOtherPlayer(String ip, UUID excludeUuid);

    List<String> getRecentIps(UUID uuid, int limit);
}
