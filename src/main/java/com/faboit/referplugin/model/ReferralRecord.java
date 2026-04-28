package com.faboit.referplugin.model;

import java.util.UUID;

/**
 * Represents a single referral event stored in the database.
 */
public class ReferralRecord {

    public enum Status {
        SUCCESS,
        BLOCKED_SELF,
        BLOCKED_SAME_IP,
        BLOCKED_COOLDOWN,
        BLOCKED_DAILY_CAP,
        BLOCKED_WEEKLY_CAP,
        BLOCKED_ALREADY_REFERRED,
        RELAXED_IP  // allowed but with reduced rewards
    }

    private final long id;
    private final UUID referrerUuid;
    private final UUID joinerUuid;
    private final String joinerIp;
    private final String referralHost;
    private final long timestamp;
    private final Status status;
    private final boolean referrerRewardGiven;
    private final boolean joinerRewardGiven;

    public ReferralRecord(long id, UUID referrerUuid, UUID joinerUuid, String joinerIp,
                          String referralHost, long timestamp, Status status,
                          boolean referrerRewardGiven, boolean joinerRewardGiven) {
        this.id = id;
        this.referrerUuid = referrerUuid;
        this.joinerUuid = joinerUuid;
        this.joinerIp = joinerIp;
        this.referralHost = referralHost;
        this.timestamp = timestamp;
        this.status = status;
        this.referrerRewardGiven = referrerRewardGiven;
        this.joinerRewardGiven = joinerRewardGiven;
    }

    public long getId() { return id; }
    public UUID getReferrerUuid() { return referrerUuid; }
    public UUID getJoinerUuid() { return joinerUuid; }
    public String getJoinerIp() { return joinerIp; }
    public String getReferralHost() { return referralHost; }
    public long getTimestamp() { return timestamp; }
    public Status getStatus() { return status; }
    public boolean isReferrerRewardGiven() { return referrerRewardGiven; }
    public boolean isJoinerRewardGiven() { return joinerRewardGiven; }
}
