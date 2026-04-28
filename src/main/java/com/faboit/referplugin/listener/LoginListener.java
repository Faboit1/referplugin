package com.faboit.referplugin.listener;

import com.faboit.referplugin.ReferPlugin;
import com.faboit.referplugin.antiabuse.AbuseDetector;
import com.faboit.referplugin.antiabuse.AbuseResult;
import com.faboit.referplugin.database.Database;
import com.faboit.referplugin.model.PendingReward;
import com.faboit.referplugin.model.ReferralRecord;
import com.faboit.referplugin.reward.RewardManager;
import com.faboit.referplugin.util.MessageUtil;
import com.faboit.referplugin.util.NotificationUtil;
import com.faboit.referplugin.velocity.VelocityData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles referral detection and processing.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@link PlayerLoginEvent} (MONITOR) — captures the hostname the player used
 *       and the IP reported by Paper (may be the Velocity proxy IP). Both are stored
 *       in {@link #pendingLogins} keyed by UUID.</li>
 *   <li>If the Velocity companion plugin is installed, it sends a plugin message on
 *       channel {@code referplugin:connect} shortly after the backend connection
 *       succeeds. The {@link com.faboit.referplugin.velocity.VelocityBridge} caches
 *       this, providing the player's real IP and the original virtual host.</li>
 *   <li>{@link PlayerJoinEvent} — scheduled 10 ticks later to allow the Velocity
 *       plugin message to arrive. Referral processing then runs with the best
 *       available IP + hostname data.</li>
 * </ol>
 */
public class LoginListener implements Listener {

    /** Delay (ticks) after PlayerJoinEvent before processing the referral. */
    private static final long PROCESS_DELAY_TICKS = 10L;

    private final ReferPlugin plugin;
    private final Logger      log;

    /**
     * Stores the hostname (and fallback IP) captured at {@link PlayerLoginEvent} time
     * for retrieval during {@link PlayerJoinEvent}.
     */
    private final ConcurrentHashMap<UUID, PendingLogin> pendingLogins = new ConcurrentHashMap<>();

    private record PendingLogin(String hostname, String fallbackIp, String rawHost) {}

    public LoginListener(ReferPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ── Step 1: capture data at login time ────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;

        Player joiner   = event.getPlayer();
        String rawHost  = event.getHostname();
        String hostname = plugin.getHostnameParser().parse(rawHost);

        if (hostname == null || hostname.isBlank()) return; // No referral subdomain

        InetAddress addr     = event.getAddress();
        String      fallbackIp = addr != null
                ? AbuseDetector.sanitiseIp(addr.getHostAddress()) : null;

        pendingLogins.put(joiner.getUniqueId(), new PendingLogin(hostname, fallbackIp, rawHost));
    }

    // ── Step 2: clean up on disconnect ───────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingLogins.remove(uuid);
        plugin.getVelocityBridge().invalidate(uuid);
    }

    // ── Step 3: process referral after join (Velocity data has time to arrive) ─

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // Always deliver any pending rewards (e.g. referrer was offline when referral happened)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) deliverPendingRewards(p);
        }, PROCESS_DELAY_TICKS);

        if (!pendingLogins.containsKey(uuid)) return; // No referral subdomain captured

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player joiner = Bukkit.getPlayer(uuid);
            if (joiner == null) return; // Disconnected before processing

            PendingLogin pending = pendingLogins.remove(uuid);
            if (pending == null) return;

            // Prefer real data from Velocity; fall back to event-time data
            VelocityData vd = plugin.getVelocityBridge().getAndRemove(uuid);
            String referrerName = (vd != null && vd.getHostname() != null && !vd.getHostname().isBlank())
                    ? plugin.getHostnameParser().parse(vd.getHostname())
                    : pending.hostname();
            String joinerIp = (vd != null && vd.getRealIp() != null && !vd.getRealIp().isBlank())
                    ? vd.getRealIp() : pending.fallbackIp();
            // Use Velocity's virtual host for logging; fall back to the raw host from PlayerLoginEvent
            String rawHost = (vd != null && vd.getHostname() != null && !vd.getHostname().isBlank())
                    ? vd.getHostname() : pending.rawHost();

            if (referrerName == null || referrerName.isBlank()) return;

            processReferral(joiner, referrerName, joinerIp, rawHost);
        }, PROCESS_DELAY_TICKS);
    }

    // ── Core referral logic ──────────────────────────────────────────────────

    private void processReferral(Player joiner, String referrerName, String joinerIp, String rawHost) {
        Database      database      = plugin.getDb();
        AbuseDetector abuseDetector = plugin.getAbuseDetector();
        RewardManager rewardManager = plugin.getRewardManager();

        // 1. Ensure joiner has a database record and log their IP
        database.ensurePlayer(joiner.getUniqueId(), joiner.getName());
        if (joinerIp != null) {
            database.logIp(joiner.getUniqueId(), joinerIp, System.currentTimeMillis());
        }

        // 2. Evaluate anti-abuse rules
        AbuseResult result = abuseDetector.evaluate(referrerName, joiner, joinerIp);

        if (result.isBlocked()) {
            UUID referrerUuid = resolveReferrerUuid(referrerName);
            UUID dbReferrerUuid = referrerUuid != null ? referrerUuid : offlineUuid(referrerName);
            database.insertReferralRecord(
                    dbReferrerUuid, joiner.getUniqueId(),
                    joinerIp, rawHost, System.currentTimeMillis(),
                    result.getStatus(), false, false);
            if (referrerUuid != null) {
                database.incrementBlockedReferrals(referrerUuid);
            }
            sendBlockedMessage(joiner, result);
            return;
        }

        // 3. Resolve referrer (may be offline)
        Player referrerOnline = Bukkit.getPlayerExact(referrerName);
        UUID   referrerUuid   = referrerOnline != null
                ? referrerOnline.getUniqueId()
                : database.getPlayerUuid(referrerName);

        // 4. Joiner reward
        double joinerRewardValue = rewardManager.applyJoinerReward(joiner, referrerOnline);
        MessageUtil.send(joiner, plugin.getConfigManager().getMessage(
                "referral.success-joiner",
                "%referrer%", referrerName,
                "%joiner%",   joiner.getName()));

        String joinerProfilePath = resolveProfilePath(joiner, "joiner");
        ConfigurationSection joinerNotif = plugin.getConfigManager().getConfig()
                .getConfigurationSection(joinerProfilePath + ".notifications");
        NotificationUtil.send(joiner, joinerNotif, log,
                "%referrer%", referrerName,
                "%joiner%",   joiner.getName(),
                "%money%",    String.format("%.2f", joinerRewardValue));

        // 5. Referrer reward
        double referrerRewardValue = 0;
        if (referrerOnline != null && referrerOnline.isOnline()) {
            // Referrer is online — deliver immediately
            referrerRewardValue = rewardManager.applyReferrerReward(referrerOnline, joiner);
            MessageUtil.send(referrerOnline, plugin.getConfigManager().getMessage(
                    "referral.success-referrer",
                    "%referrer%", referrerOnline.getName(),
                    "%joiner%",   joiner.getName()));

            String refProfilePath = resolveProfilePath(referrerOnline, "referrer");
            ConfigurationSection refNotif = plugin.getConfigManager().getConfig()
                    .getConfigurationSection(refProfilePath + ".notifications");
            NotificationUtil.send(referrerOnline, refNotif, log,
                    "%referrer%", referrerOnline.getName(),
                    "%joiner%",   joiner.getName(),
                    "%money%",    String.format("%.2f", referrerRewardValue));

            database.incrementSuccessfulReferrals(referrerOnline.getUniqueId(), referrerRewardValue);
            checkMilestones(referrerOnline);
        } else if (referrerUuid != null) {
            // Referrer is offline — update stats and queue reward for next login
            database.ensurePlayer(referrerUuid,
                    referrerName); // ensure row exists so UPDATE doesn't silently no-op
            database.incrementSuccessfulReferrals(referrerUuid, 0);

            String refProfilePath = "reward-profiles.default_referrer";
            database.addPendingReward(referrerUuid, joiner.getName(),
                    refProfilePath, 1.0, System.currentTimeMillis());
        }

        // 6. Record the event
        database.insertReferralRecord(
                referrerUuid != null ? referrerUuid : offlineUuid(referrerName),
                joiner.getUniqueId(),
                joinerIp, rawHost, System.currentTimeMillis(),
                result.getStatus(),
                referrerOnline != null,
                true);

        log.info(String.format("Referral processed: %s → %s (status=%s)",
                referrerName, joiner.getName(), result.getStatus()));
    }

    // ── Pending reward delivery on join ──────────────────────────────────────

    private void deliverPendingRewards(Player player) {
        List<PendingReward> pending = plugin.getDb().getPendingRewards(player.getUniqueId());
        if (pending.isEmpty()) return;

        RewardManager rewardManager = plugin.getRewardManager();
        for (PendingReward pr : pending) {
            rewardManager.applyReferrerRewardFromPath(player, pr.getJoinerName(), pr.getProfilePath(), pr.getMultiplier());
            plugin.getDb().removePendingReward(pr.getId());
        }

        MessageUtil.send(player, plugin.getConfigManager().getMessage(
                "referral.pending-delivered",
                "%count%", String.valueOf(pending.size())));
    }

    // ── Milestone check ──────────────────────────────────────────────────────

    private void checkMilestones(Player referrer) {
        var stats = plugin.getDb().getPlayerStats(referrer.getUniqueId());
        if (stats == null) return;
        int successful = stats.getSuccessfulReferrals();

        var milestones = plugin.getConfigManager().getConfig().getList("milestones");
        if (milestones == null) return;

        for (Object raw : milestones) {
            if (!(raw instanceof java.util.Map<?,?> m)) continue;
            Object _targetRaw = m.get("referrals");
            int target = _targetRaw != null ? Integer.parseInt(String.valueOf(_targetRaw)) : -1;
            if (target != successful) continue;

            Object rewardsRaw = m.get("rewards");
            double moneyGiven = 0;
            int xpGiven = 0;
            if (rewardsRaw instanceof java.util.Map<?,?> rm) {
                Object _moneyRaw = rm.get("money");
                Object _xpRaw    = rm.get("xp");
                moneyGiven = _moneyRaw != null ? Double.parseDouble(String.valueOf(_moneyRaw)) : 0;
                xpGiven    = _xpRaw    != null ? Integer.parseInt(String.valueOf(_xpRaw))      : 0;
                if (moneyGiven > 0 && plugin.getEconomy() != null) {
                    plugin.getEconomy().depositPlayer(referrer, moneyGiven);
                }
                if (xpGiven > 0) {
                    referrer.giveExp(xpGiven);
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
            }

            MessageUtil.send(referrer, plugin.getConfigManager().getMessage(
                    "milestone.reached",
                    "%referrals%", String.valueOf(target)));

            fireConfiguredMilestoneNotifications(referrer, target, moneyGiven, xpGiven);

            log.info("Milestone reached: " + referrer.getName() + " at " + target + " referrals.");
        }
    }

    private void fireConfiguredMilestoneNotifications(Player referrer, int target,
                                                       double money, int xp) {
        var milestonesList = plugin.getConfigManager().getConfig().getList("milestones");
        if (milestonesList == null) return;

        for (int idx = 0; idx < milestonesList.size(); idx++) {
            var ms = plugin.getConfigManager().getConfig()
                    .getConfigurationSection("milestones." + idx);
            if (ms == null) continue;
            if (ms.getInt("referrals", -1) != target) continue;

            ConfigurationSection notif = ms.getConfigurationSection("notifications");
            if (notif != null) {
                NotificationUtil.send(referrer, notif, log,
                        "%referrer%", referrer.getName(),
                        "%referrals%", String.valueOf(target),
                        "%money%", String.format("%.2f", money),
                        "%xp%", String.valueOf(xp));
            }
            break;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UUID resolveReferrerUuid(String username) {
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) return online.getUniqueId();
        return plugin.getDb().getPlayerUuid(username);
    }

    private static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private String resolveProfilePath(Player player, String side) {
        String custom = "custom-" + side + "-profiles";
        String def    = "reward-profiles.default_" + side;

        ConfigurationSection sec = plugin.getConfigManager().getConfig()
                .getConfigurationSection(custom);
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                if (k.equalsIgnoreCase(player.getName())) return custom + "." + k;
            }
        }
        return def;
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
            MessageUtil.send(joiner, plugin.getConfigManager().getMessage(msgKey));
        }
    }
}
