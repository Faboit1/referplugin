package com.faboit.referplugin.antiabuse;

import com.faboit.referplugin.config.ConfigManager;
import com.faboit.referplugin.database.Database;
import com.faboit.referplugin.model.ReferralRecord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Evaluates an incoming referral against all anti-abuse rules and returns
 * an {@link AbuseResult} describing whether the referral is allowed,
 * should be processed with relaxed rewards, or blocked entirely.
 */
public class AbuseDetector {

    private final ConfigManager configManager;
    private final Database database;
    private final Logger log;

    public AbuseDetector(ConfigManager configManager, Database database, Logger log) {
        this.configManager = configManager;
        this.database      = database;
        this.log           = log;
    }

    /**
     * Evaluate a candidate referral.
     *
     * @param referrerName  The username extracted from the hostname.
     * @param joiner        The player who just joined.
     * @param joinerIp      The IP address of the joining player (sanitised, no port).
     * @return An {@link AbuseResult} describing the decision.
     */
    public AbuseResult evaluate(String referrerName, Player joiner, String joinerIp) {
        UUID joinerUuid = joiner.getUniqueId();

        // --- 1. Self-referral check ---
        if (configManager.getConfig().getBoolean("anti-abuse.self-referral-block", true)) {
            if (joiner.getName().equalsIgnoreCase(referrerName)) {
                log("BLOCKED_SELF", joiner.getName(), referrerName, joinerIp, "Self-referral attempt");
                return AbuseResult.blocked(ReferralRecord.Status.BLOCKED_SELF, "Self-referral");
            }
        }

        // --- 2. Already-referred check ---
        if (database.hasBeenReferred(joinerUuid)) {
            log("BLOCKED_ALREADY_REFERRED", joiner.getName(), referrerName, joinerIp, "Already referred");
            return AbuseResult.blocked(
                    ReferralRecord.Status.BLOCKED_ALREADY_REFERRED, "Already referred");
        }

        // Find the referrer's UUID
        Player referrerOnline = Bukkit.getPlayerExact(referrerName);
        UUID referrerUuid = referrerOnline != null ? referrerOnline.getUniqueId() : null;

        // --- 3. IP check ---
        if (configManager.getConfig().getBoolean("anti-abuse.same-ip-block", true)
                && joinerIp != null && !joinerIp.isBlank()) {

            // Check IP whitelist — whitelisted IPs are never blocked
            List<String> whitelist = configManager.getConfig()
                    .getStringList("anti-abuse.ip-whitelist");
            if (!whitelist.contains(joinerIp)) {
                boolean ipConflict = database.ipUsedByOtherPlayer(joinerIp, joinerUuid);
                if (!ipConflict && referrerOnline != null) {
                    // Also check if the joiner's IP matches the referrer's current/stored IP
                    String referrerIp = sanitiseIp(referrerOnline.getAddress() != null
                            ? referrerOnline.getAddress().getAddress().getHostAddress() : null);
                    ipConflict = joinerIp.equals(referrerIp);
                }

                if (ipConflict) {
                    String enforcement = configManager.getConfig()
                            .getString("anti-abuse.enforcement", "strict").toLowerCase();
                    if ("relaxed".equals(enforcement)) {
                        log("RELAXED_IP", joiner.getName(), referrerName, joinerIp, "Same IP – relaxed mode");
                        return AbuseResult.relaxed("Same IP (relaxed)");
                    } else {
                        log("BLOCKED_SAME_IP", joiner.getName(), referrerName, joinerIp, "Same IP – strict");
                        return AbuseResult.blocked(ReferralRecord.Status.BLOCKED_SAME_IP, "Same IP");
                    }
                }
            }
        }

        // --- 4. Cooldown check ---
        if (referrerUuid != null && !joiner.hasPermission("referplugin.bypass.cooldown")) {
            int cooldownHours = resolveCooldown(joiner);
            if (cooldownHours > 0) {
                long lastReferral = getLastReferralTimestamp(referrerUuid);
                long cooldownMs = cooldownHours * 3_600_000L;
                if (System.currentTimeMillis() - lastReferral < cooldownMs) {
                    log("BLOCKED_COOLDOWN", joiner.getName(), referrerName, joinerIp, "Cooldown active");
                    return AbuseResult.blocked(ReferralRecord.Status.BLOCKED_COOLDOWN, "Cooldown");
                }
            }
        }

        // --- 5. Daily cap ---
        if (referrerUuid != null && !joiner.hasPermission("referplugin.bypass.cap")) {
            int dailyCap = resolveDailyCap(joiner);
            if (dailyCap > 0) {
                int todayCount = database.countSuccessfulReferralsToday(referrerUuid);
                if (todayCount >= dailyCap) {
                    log("BLOCKED_DAILY_CAP", joiner.getName(), referrerName, joinerIp, "Daily cap reached");
                    return AbuseResult.blocked(ReferralRecord.Status.BLOCKED_DAILY_CAP, "Daily cap");
                }
            }

            // --- 6. Weekly cap ---
            int weeklyCap = resolveWeeklyCap(joiner);
            if (weeklyCap > 0) {
                int weekCount = database.countSuccessfulReferralsThisWeek(referrerUuid);
                if (weekCount >= weeklyCap) {
                    log("BLOCKED_WEEKLY_CAP", joiner.getName(), referrerName, joinerIp, "Weekly cap reached");
                    return AbuseResult.blocked(ReferralRecord.Status.BLOCKED_WEEKLY_CAP, "Weekly cap");
                }
            }
        }

        return AbuseResult.allow();
    }

    private long getLastReferralTimestamp(UUID referrerUuid) {
        var stats = database.getPlayerStats(referrerUuid);
        return stats != null ? stats.getLastReferralTimestamp() : 0;
    }

    /**
     * Resolves the effective cooldown for a player, checking per-permission
     * overrides first (lowest wins).
     */
    private int resolveCooldown(Player player) {
        var overrides = configManager.getConfig()
                .getConfigurationSection("anti-abuse.cooldown-overrides");
        if (overrides != null) {
            int best = Integer.MAX_VALUE;
            boolean found = false;
            for (String perm : overrides.getKeys(false)) {
                if (player.hasPermission(perm)) {
                    int val = overrides.getInt(perm, 24);
                    if (val < best) { best = val; found = true; }
                }
            }
            if (found) return best;
        }
        return configManager.getConfig().getInt("anti-abuse.cooldown-hours", 24);
    }

    /**
     * Resolves the effective daily cap for a player, checking per-permission
     * overrides first (highest wins; 0 = unlimited).
     */
    private int resolveDailyCap(Player player) {
        var overrides = configManager.getConfig()
                .getConfigurationSection("anti-abuse.cap-overrides");
        if (overrides != null) {
            for (String perm : overrides.getKeys(false)) {
                if (player.hasPermission(perm)) {
                    return overrides.getInt(perm, 10);
                }
            }
        }
        return configManager.getConfig().getInt("anti-abuse.daily-cap", 10);
    }

    /**
     * Resolves the effective weekly cap for a player.
     */
    private int resolveWeeklyCap(Player player) {
        // Weekly cap re-uses the same cap-overrides section (value × 7 heuristic)
        return configManager.getConfig().getInt("anti-abuse.weekly-cap", 50);
    }

    private void log(String type, String joiner, String referrer, String ip, String reason) {
        this.log.warning(String.format(
                "[AbuseDetector] %s | joiner=%s referrer=%s ip=%s reason=%s",
                type, joiner, referrer, ip, reason));
    }

    /**
     * Strip port from an IP string if present, returning null-safe result.
     */
    public static String sanitiseIp(String raw) {
        if (raw == null) return null;
        // IPv6 addresses are wrapped in brackets: [::1]:25565
        if (raw.startsWith("[")) {
            int closeBracket = raw.indexOf(']');
            if (closeBracket > 0) return raw.substring(1, closeBracket);
        }
        int lastColon = raw.lastIndexOf(':');
        // If there's exactly one colon it's likely IPv4:port
        if (lastColon > 0 && raw.indexOf(':') == lastColon) {
            return raw.substring(0, lastColon);
        }
        return raw;
    }
}
