package com.faboit.referplugin.model;

import java.util.UUID;

/**
 * Represents a referral reward that could not be delivered immediately because
 * the referrer was offline. Delivered the next time the referrer joins.
 */
public class PendingReward {

    private final long id;
    private final UUID playerUuid;
    private final String joinerName;
    /** Config path of the reward profile, e.g. {@code reward-profiles.default_referrer}. */
    private final String profilePath;
    private final double multiplier;
    private final long createdAt;

    public PendingReward(long id, UUID playerUuid, String joinerName,
                         String profilePath, double multiplier, long createdAt) {
        this.id          = id;
        this.playerUuid  = playerUuid;
        this.joinerName  = joinerName;
        this.profilePath = profilePath;
        this.multiplier  = multiplier;
        this.createdAt   = createdAt;
    }

    public long   getId()          { return id; }
    public UUID   getPlayerUuid()  { return playerUuid; }
    public String getJoinerName()  { return joinerName; }
    public String getProfilePath() { return profilePath; }
    public double getMultiplier()  { return multiplier; }
    public long   getCreatedAt()   { return createdAt; }
}
