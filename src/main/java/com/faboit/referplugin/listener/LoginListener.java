package com.faboit.referplugin.listener;

import com.faboit.referplugin.ReferPlugin;
import com.faboit.referplugin.antiabuse.AbuseDetector;
import com.faboit.referplugin.antiabuse.AbuseResult;
import com.faboit.referplugin.config.ConfigManager;
import com.faboit.referplugin.database.Database;
import com.faboit.referplugin.hostname.HostnameParser;
import com.faboit.referplugin.model.ReferralRecord;
import com.faboit.referplugin.reward.RewardManager;
import com.faboit.referplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * Intercepts player logins to detect and process referral subdomains.
 *
 * <p>Using {@link PlayerLoginEvent} at {@code MONITOR} priority ensures we
 * observe the hostname <em>after</em> all other plugins (including Velocity /
 * BungeeCord IP-forwarding) have set it, but before the player fully joins.
 */
public class LoginListener implements Listener {

    private final ReferPlugin plugin;
    private final HostnameParser hostnameParser;
    private final AbuseDetector  abuseDetector;
    private final RewardManager  rewardManager;
    private final Database       database;
    private final ConfigManager  configManager;
    private final Logger         log;

    public LoginListener(ReferPlugin plugin) {
        this.plugin        = plugin;
        this.hostnameParser = plugin.getHostnameParser();
        this.abuseDetector  = plugin.getAbuseDetector();
        this.rewardManager  = plugin.getRewardManager();
        this.database       = plugin.getDb();
        this.configManager  = plugin.getConfigManager();
        this.log            = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;

        Player joiner = event.getPlayer();

        // 1. Extract referrer subdomain from the connecting hostname
        String rawHost = event.getHostname();
        String referrerName = hostnameParser.parse(rawHost);

        if (referrerName == null || referrerName.isBlank()) return;

        // 2. Sanitise joiner IP
        InetAddress addr = event.getAddress();
        String joinerIp  = addr != null ? AbuseDetector.sanitiseIp(addr.getHostAddress()) : null;

        // 3. Ensure the joiner has a database record
        database.ensurePlayer(joiner.getUniqueId(), joiner.getName());
        if (joinerIp != null) {
            database.logIp(joiner.getUniqueId(), joinerIp, System.currentTimeMillis());
        }

        // 4. Evaluate anti-abuse rules
        AbuseResult result = abuseDetector.evaluate(referrerName, joiner, joinerIp);

        if (result.isBlocked()) {
            // Log and notify but do not penalise the joiner
            database.insertReferralRecord(
                    findOrCreateReferrerUuid(referrerName), joiner.getUniqueId(),
                    joinerIp, rawHost, System.currentTimeMillis(),
                    result.getStatus(), false, false);
            database.incrementBlockedReferrals(findOrCreateReferrerUuid(referrerName));

            sendBlockedMessage(joiner, result);
            return;
        }

        // 5. Look up the referrer player (online or offline)
        Player referrerOnline = Bukkit.getPlayerExact(referrerName);

        // 6. Schedule reward delivery on the main thread (PlayerLoginEvent is async-safe)
        final AbuseResult finalResult = result;
        final String      finalHost   = rawHost;
        final String      finalIp     = joinerIp;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player joinerNow = Bukkit.getPlayerExact(joiner.getName());
            if (joinerNow == null) return; // Player disconnected immediately

            double multiplier = finalResult.isRelaxed()
                    ? configManager.getConfig().getDouble(
                            "anti-abuse.relaxed-reward-multiplier", 0.5)
                    : 1.0;

            double joinerRewardValue  = 0;
            double referrerRewardValue = 0;

            // Give joiner reward
            joinerRewardValue = rewardManager.applyJoinerReward(joinerNow, referrerOnline);
            MessageUtil.send(joinerNow, configManager.getMessage(
                    "referral.success-joiner",
                    "%referrer%", referrerName,
                    "%joiner%",   joinerNow.getName()));

            // Give referrer reward (if online)
            if (referrerOnline != null && referrerOnline.isOnline()) {
                referrerRewardValue = rewardManager.applyReferrerReward(referrerOnline, joinerNow);
                MessageUtil.send(referrerOnline, configManager.getMessage(
                        "referral.success-referrer",
                        "%referrer%", referrerOnline.getName(),
                        "%joiner%",   joinerNow.getName()));

                database.incrementSuccessfulReferrals(referrerOnline.getUniqueId(), referrerRewardValue);
                checkMilestones(referrerOnline);
            }

            // Record the event in the database
            database.insertReferralRecord(
                    findOrCreateReferrerUuid(referrerName), joinerNow.getUniqueId(),
                    finalIp, finalHost, System.currentTimeMillis(),
                    finalResult.getStatus(),
                    referrerOnline != null,
                    true);

            log.info(String.format("Referral processed: %s → %s (host=%s, status=%s)",
                    referrerName, joinerNow.getName(), finalHost, finalResult.getStatus()));
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private java.util.UUID findOrCreateReferrerUuid(String username) {
        // Use online player UUID if available
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) return online.getUniqueId();

        // Try the database by name (linear scan acceptable for rare case)
        // We fall back to a name-based UUID which is stable per server (offline mode)
        return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void sendBlockedMessage(Player joiner, AbuseResult result) {
        String msgKey = switch (result.getStatus()) {
            case BLOCKED_SELF             -> "referral.blocked-self";
            case BLOCKED_SAME_IP          -> "referral.blocked-same-ip";
            case BLOCKED_COOLDOWN         -> "referral.blocked-cooldown";
            case BLOCKED_DAILY_CAP        -> "referral.blocked-daily-cap";
            case BLOCKED_WEEKLY_CAP       -> "referral.blocked-weekly-cap";
            case BLOCKED_ALREADY_REFERRED -> "referral.blocked-already-referred";
            default                       -> null;
        };
        if (msgKey != null) {
            // Schedule message delivery after login completes
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayerExact(joiner.getName());
                if (p != null) MessageUtil.send(p, configManager.getMessage(msgKey));
            });
        }
    }

    private void checkMilestones(Player referrer) {
        var stats = database.getPlayerStats(referrer.getUniqueId());
        if (stats == null) return;
        int successful = stats.getSuccessfulReferrals();

        var milestones = configManager.getConfig().getList("milestones");
        if (milestones == null) return;

        for (Object raw : milestones) {
            if (!(raw instanceof java.util.Map<?,?> m)) continue;
            int target = Integer.parseInt(String.valueOf(m.getOrDefault("referrals", -1)));
            if (target != successful) continue;

            // Give milestone rewards
            Object rewardsRaw = m.get("rewards");
            if (!(rewardsRaw instanceof java.util.Map<?,?> rm)) continue;

            double money = Double.parseDouble(String.valueOf(rm.getOrDefault("money", 0)));
            if (money > 0 && plugin.getEconomy() != null) {
                plugin.getEconomy().depositPlayer(referrer, money);
            }

            Object cmdsRaw = rm.get("commands");
            if (cmdsRaw instanceof java.util.List<?> cmds) {
                for (Object c : cmds) {
                    String cmd = String.valueOf(c)
                            .replace("%referrer%", referrer.getName())
                            .replace("%referral_count%", String.valueOf(successful));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }

            MessageUtil.send(referrer, configManager.getMessage(
                    "milestone.reached",
                    "%referrals%", String.valueOf(target)));
            log.info("Milestone reached: " + referrer.getName() + " at " + target + " referrals.");
        }
    }
}
