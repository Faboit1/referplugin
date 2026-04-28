package com.faboit.referplugin.antiabuse;

import com.faboit.referplugin.model.ReferralRecord;

/**
 * The result of an abuse-check evaluation.
 */
public class AbuseResult {

    public enum Outcome {
        /** Referral is clean – apply full rewards. */
        ALLOW,
        /** IP overlap detected but enforcement is RELAXED – apply reduced rewards. */
        RELAXED,
        /** Referral blocked completely. */
        BLOCKED
    }

    private final Outcome outcome;
    private final ReferralRecord.Status status;
    private final String reason;

    private AbuseResult(Outcome outcome, ReferralRecord.Status status, String reason) {
        this.outcome = outcome;
        this.status  = status;
        this.reason  = reason;
    }

    public static AbuseResult allow() {
        return new AbuseResult(Outcome.ALLOW, ReferralRecord.Status.SUCCESS, "OK");
    }

    public static AbuseResult relaxed(String reason) {
        return new AbuseResult(Outcome.RELAXED, ReferralRecord.Status.RELAXED_IP, reason);
    }

    public static AbuseResult blocked(ReferralRecord.Status status, String reason) {
        return new AbuseResult(Outcome.BLOCKED, status, reason);
    }

    public Outcome getOutcome() { return outcome; }
    public ReferralRecord.Status getStatus() { return status; }
    public String getReason() { return reason; }

    public boolean isBlocked() { return outcome == Outcome.BLOCKED; }
    public boolean isRelaxed() { return outcome == Outcome.RELAXED; }
}
