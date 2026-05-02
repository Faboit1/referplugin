package com.faboit.referplugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Appends human-readable referral events to flat log files inside the plugin folder.
 *
 * <ul>
 *   <li>{@code referrals.log}         – one line per successful referral</li>
 *   <li>{@code blocked-referrals.log} – one line per blocked/flagged referral</li>
 * </ul>
 */
public class ReferralLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("UTC"));

    private final File    successFile;
    private final File    blockedFile;
    private final Logger  log;

    public ReferralLogger(File dataFolder, Logger log) {
        this.successFile = new File(dataFolder, "referrals.log");
        this.blockedFile = new File(dataFolder, "blocked-referrals.log");
        this.log         = log;
    }

    /**
     * Records a successful referral.
     *
     * @param referrerName  Username of the player who referred.
     * @param joinerName    Username of the player who joined.
     * @param joinerIp      Sanitised IP address of the joiner.
     * @param host          Raw hostname the joiner connected with.
     * @param timestamp     Unix epoch milliseconds of the event.
     */
    public synchronized void logSuccess(String referrerName, String joinerName,
                                        String joinerIp, String host, long timestamp) {
        String line = String.format("[%s] SUCCESS | referrer=%s | joiner=%s | ip=%s | host=%s%n",
                FMT.format(Instant.ofEpochMilli(timestamp)),
                referrerName, joinerName, safe(joinerIp), safe(host));
        append(successFile, line);
    }

    /**
     * Records a blocked referral attempt.
     *
     * @param referrerName  Username of the intended referrer.
     * @param joinerName    Username of the joining player.
     * @param joinerIp      Sanitised IP address of the joiner.
     * @param status        The {@link com.faboit.referplugin.model.ReferralRecord.Status} name.
     * @param timestamp     Unix epoch milliseconds of the event.
     */
    public synchronized void logBlocked(String referrerName, String joinerName,
                                        String joinerIp, String status, long timestamp) {
        String line = String.format("[%s] %s | referrer=%s | joiner=%s | ip=%s%n",
                FMT.format(Instant.ofEpochMilli(timestamp)),
                status, referrerName, joinerName, safe(joinerIp));
        append(blockedFile, line);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void append(File file, String line) {
        try {
            Files.writeString(file.toPath(), line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warning("ReferralLogger: could not write to " + file.getName() + ": " + e.getMessage());
        }
    }

    private static String safe(String s) { return s != null ? s : "N/A"; }
}
