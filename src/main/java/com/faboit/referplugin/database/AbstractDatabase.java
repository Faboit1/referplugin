package com.faboit.referplugin.database;

import com.faboit.referplugin.model.PendingReward;
import com.faboit.referplugin.model.PlayerStats;
import com.faboit.referplugin.model.ReferralRecord;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Shared SQL implementation for both SQLite and MySQL.
 * Sub-classes provide the configured {@link HikariDataSource}.
 */
public abstract class AbstractDatabase implements Database {

    protected final Logger log;
    protected HikariDataSource dataSource;

    protected AbstractDatabase(Logger log) {
        this.log = log;
    }

    // ── Schema ──────────────────────────────────────────────────────────────

    @Override
    public void init() throws Exception {
        dataSource = createDataSource();
        createTables();
    }

    protected abstract HikariDataSource createDataSource() throws Exception;

    private void createTables() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS referral_players (
                    uuid                VARCHAR(36)  PRIMARY KEY,
                    username            VARCHAR(16)  NOT NULL,
                    reward_profile      VARCHAR(64)  NOT NULL DEFAULT 'default',
                    total_referrals     INT          NOT NULL DEFAULT 0,
                    successful_referrals INT         NOT NULL DEFAULT 0,
                    blocked_referrals   INT          NOT NULL DEFAULT 0,
                    total_rewards       DOUBLE       NOT NULL DEFAULT 0,
                    last_referral       BIGINT       NOT NULL DEFAULT 0
                )
            """);

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS referral_records (
                    id                    INTEGER PRIMARY KEY %s,
                    referrer_uuid         VARCHAR(36) NOT NULL,
                    joiner_uuid           VARCHAR(36) NOT NULL,
                    joiner_ip             VARCHAR(64),
                    referral_host         VARCHAR(256),
                    timestamp             BIGINT NOT NULL,
                    status                VARCHAR(32) NOT NULL,
                    referrer_reward_given BOOLEAN NOT NULL DEFAULT FALSE,
                    joiner_reward_given   BOOLEAN NOT NULL DEFAULT FALSE
                )
            """.formatted(autoIncrementKeyword()));

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS referral_ip_log (
                    id        INTEGER PRIMARY KEY %s,
                    uuid      VARCHAR(36) NOT NULL,
                    ip        VARCHAR(64) NOT NULL,
                    timestamp BIGINT NOT NULL
                )
            """.formatted(autoIncrementKeyword()));

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS referral_pending_rewards (
                    id           INTEGER PRIMARY KEY %s,
                    player_uuid  VARCHAR(36)  NOT NULL,
                    joiner_name  VARCHAR(16)  NOT NULL,
                    profile_path VARCHAR(128) NOT NULL,
                    multiplier   DOUBLE       NOT NULL DEFAULT 1.0,
                    created_at   BIGINT       NOT NULL
                )
            """.formatted(autoIncrementKeyword()));

            // Indices for performance
            safeExecute(s, "CREATE INDEX IF NOT EXISTS idx_records_referrer ON referral_records(referrer_uuid)");
            safeExecute(s, "CREATE INDEX IF NOT EXISTS idx_records_joiner   ON referral_records(joiner_uuid)");
            safeExecute(s, "CREATE INDEX IF NOT EXISTS idx_ip_uuid          ON referral_ip_log(uuid)");
            safeExecute(s, "CREATE INDEX IF NOT EXISTS idx_ip_ip            ON referral_ip_log(ip)");
            safeExecute(s, "CREATE INDEX IF NOT EXISTS idx_pending_uuid     ON referral_pending_rewards(player_uuid)");
        }
    }

    /** Returns the AUTO_INCREMENT keyword for this engine (overridden by subclasses). */
    protected String autoIncrementKeyword() {
        return "AUTOINCREMENT";
    }

    private void safeExecute(Statement s, String sql) {
        try { s.executeUpdate(sql); } catch (SQLException ignored) {}
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ── Player stats ────────────────────────────────────────────────────────

    @Override
    public PlayerStats getPlayerStats(UUID uuid) {
        String sql = "SELECT * FROM referral_players WHERE uuid = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapStats(rs);
            }
        } catch (SQLException e) {
            log.severe("Error fetching player stats: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void savePlayerStats(PlayerStats stats) {
        String sql = """
            INSERT INTO referral_players
              (uuid, username, reward_profile, total_referrals, successful_referrals,
               blocked_referrals, total_rewards, last_referral)
            VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT(uuid) DO UPDATE SET
              username             = excluded.username,
              reward_profile       = excluded.reward_profile,
              total_referrals      = excluded.total_referrals,
              successful_referrals = excluded.successful_referrals,
              blocked_referrals    = excluded.blocked_referrals,
              total_rewards        = excluded.total_rewards,
              last_referral        = excluded.last_referral
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(upsertSql(sql))) {
            ps.setString(1, stats.getUuid().toString());
            ps.setString(2, stats.getUsername());
            ps.setString(3, stats.getRewardProfile());
            ps.setInt(4, stats.getTotalReferrals());
            ps.setInt(5, stats.getSuccessfulReferrals());
            ps.setInt(6, stats.getBlockedReferrals());
            ps.setDouble(7, stats.getTotalRewards());
            ps.setLong(8, stats.getLastReferralTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Error saving player stats: " + e.getMessage());
        }
    }

    /** Dialects differ on UPSERT syntax; override in MySQL subclass if needed. */
    protected String upsertSql(String baseSql) {
        return baseSql; // SQLite dialect by default
    }

    @Override
    public void ensurePlayer(UUID uuid, String username) {
        String sql = "INSERT OR IGNORE INTO referral_players (uuid, username) VALUES (?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(ensurePlayerSql(sql))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Error ensuring player: " + e.getMessage());
        }
    }

    protected String ensurePlayerSql(String baseSql) {
        return baseSql;
    }

    @Override
    public void incrementSuccessfulReferrals(UUID uuid, double rewardAmount) {
        String sql = """
            UPDATE referral_players
            SET successful_referrals = successful_referrals + 1,
                total_referrals      = total_referrals + 1,
                total_rewards        = total_rewards + ?,
                last_referral        = ?
            WHERE uuid = ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, rewardAmount);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Error incrementing successful referrals: " + e.getMessage());
        }
    }

    @Override
    public void incrementBlockedReferrals(UUID uuid) {
        String sql = """
            UPDATE referral_players
            SET blocked_referrals = blocked_referrals + 1,
                total_referrals   = total_referrals + 1
            WHERE uuid = ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Error incrementing blocked referrals: " + e.getMessage());
        }
    }

    @Override
    public void setRewardProfile(UUID uuid, String profileName) {
        String sql = "UPDATE referral_players SET reward_profile = ? WHERE uuid = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, profileName);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Error setting reward profile: " + e.getMessage());
        }
    }

    @Override
    public List<PlayerStats> getTopReferrers(int limit) {
        List<PlayerStats> list = new ArrayList<>();
        String sql = "SELECT * FROM referral_players ORDER BY successful_referrals DESC LIMIT ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapStats(rs));
            }
        } catch (SQLException e) {
            log.severe("Error fetching top referrers: " + e.getMessage());
        }
        return list;
    }

    // ── Referral records ────────────────────────────────────────────────────

    @Override
    public void insertReferralRecord(UUID referrerUuid, UUID joinerUuid,
                                      String joinerIp, String referralHost,
                                      long timestamp, ReferralRecord.Status status,
                                      boolean referrerRewardGiven, boolean joinerRewardGiven) {
        String sql = """
            INSERT INTO referral_records
              (referrer_uuid, joiner_uuid, joiner_ip, referral_host,
               timestamp, status, referrer_reward_given, joiner_reward_given)
            VALUES (?,?,?,?,?,?,?,?)
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, referrerUuid.toString());
            ps.setString(2, joinerUuid.toString());
            ps.setString(3, joinerIp);
            ps.setString(4, referralHost);
            ps.setLong(5, timestamp);
            ps.setString(6, status.name());
            ps.setBoolean(7, referrerRewardGiven);
            ps.setBoolean(8, joinerRewardGiven);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Error inserting referral record: " + e.getMessage());
        }
    }

    @Override
    public List<ReferralRecord> getRecentRecords(UUID referrerUuid, int limit) {
        List<ReferralRecord> list = new ArrayList<>();
        String sql = """
            SELECT * FROM referral_records
            WHERE referrer_uuid = ?
            ORDER BY timestamp DESC LIMIT ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, referrerUuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRecord(rs));
            }
        } catch (SQLException e) {
            log.severe("Error fetching recent records: " + e.getMessage());
        }
        return list;
    }

    @Override
    public List<ReferralRecord> getSuspiciousRecords(int limit) {
        List<ReferralRecord> list = new ArrayList<>();
        String sql = """
            SELECT * FROM referral_records
            WHERE status != 'SUCCESS'
            ORDER BY timestamp DESC LIMIT ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRecord(rs));
            }
        } catch (SQLException e) {
            log.severe("Error fetching suspicious records: " + e.getMessage());
        }
        return list;
    }

    @Override
    public boolean hasBeenReferred(UUID joinerUuid) {
        // Both SUCCESS and RELAXED_IP count as "already referred" – prevent being referred twice
        String sql = "SELECT 1 FROM referral_records WHERE joiner_uuid = ? AND status IN ('SUCCESS','RELAXED_IP') LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, joinerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.severe("Error checking hasBeenReferred: " + e.getMessage());
        }
        return false;
    }

    @Override
    public int countSuccessfulReferralsToday(UUID referrerUuid) {
        long dayStart = getDayStart();
        String sql = """
            SELECT COUNT(*) FROM referral_records
            WHERE referrer_uuid = ? AND status = 'SUCCESS' AND timestamp >= ?
            """;
        return countQuery(sql, referrerUuid.toString(), dayStart);
    }

    @Override
    public int countSuccessfulReferralsThisWeek(UUID referrerUuid) {
        long weekStart = getWeekStart();
        String sql = """
            SELECT COUNT(*) FROM referral_records
            WHERE referrer_uuid = ? AND status = 'SUCCESS' AND timestamp >= ?
            """;
        return countQuery(sql, referrerUuid.toString(), weekStart);
    }

    // ── IP tracking ─────────────────────────────────────────────────────────

    @Override
    public void logIp(UUID uuid, String ip, long timestamp) {
        String sql = "INSERT INTO referral_ip_log (uuid, ip, timestamp) VALUES (?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            ps.setLong(3, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Error logging IP: " + e.getMessage());
        }
    }

    @Override
    public boolean ipUsedByOtherPlayer(String ip, UUID excludeUuid) {
        String sql = "SELECT 1 FROM referral_ip_log WHERE ip = ? AND uuid != ? LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setString(2, excludeUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.severe("Error checking IP: " + e.getMessage());
        }
        return false;
    }

    @Override
    public List<String> getRecentIps(UUID uuid, int limit) {
        List<String> ips = new ArrayList<>();
        String sql = "SELECT ip FROM referral_ip_log WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ips.add(rs.getString("ip"));
            }
        } catch (SQLException e) {
            log.severe("Error fetching recent IPs: " + e.getMessage());
        }
        return ips;
    }

    // ── Offline player lookup ────────────────────────────────────────────────

    @Override
    public UUID getPlayerUuid(String username) {
        String sql = "SELECT uuid FROM referral_players WHERE username = ? LIMIT 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            log.severe("Error looking up player UUID by name: " + e.getMessage());
        }
        return null;
    }

    // ── Pending rewards ──────────────────────────────────────────────────────

    @Override
    public void addPendingReward(UUID playerUuid, String joinerName,
                                  String profilePath, double multiplier, long createdAt) {
        String sql = """
            INSERT INTO referral_pending_rewards
              (player_uuid, joiner_name, profile_path, multiplier, created_at)
            VALUES (?,?,?,?,?)
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, joinerName);
            ps.setString(3, profilePath);
            ps.setDouble(4, multiplier);
            ps.setLong(5, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Error adding pending reward: " + e.getMessage());
        }
    }

    @Override
    public List<PendingReward> getPendingRewards(UUID playerUuid) {
        List<PendingReward> list = new ArrayList<>();
        String sql = "SELECT * FROM referral_pending_rewards WHERE player_uuid = ? ORDER BY created_at ASC";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new PendingReward(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("joiner_name"),
                            rs.getString("profile_path"),
                            rs.getDouble("multiplier"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            log.severe("Error fetching pending rewards: " + e.getMessage());
        }
        return list;
    }

    @Override
    public void removePendingReward(long id) {
        String sql = "DELETE FROM referral_pending_rewards WHERE id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Error removing pending reward: " + e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private PlayerStats mapStats(ResultSet rs) throws SQLException {
        return new PlayerStats(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("reward_profile"),
                rs.getInt("total_referrals"),
                rs.getInt("successful_referrals"),
                rs.getInt("blocked_referrals"),
                rs.getDouble("total_rewards"),
                rs.getLong("last_referral")
        );
    }

    private ReferralRecord mapRecord(ResultSet rs) throws SQLException {
        return new ReferralRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("referrer_uuid")),
                UUID.fromString(rs.getString("joiner_uuid")),
                rs.getString("joiner_ip"),
                rs.getString("referral_host"),
                rs.getLong("timestamp"),
                ReferralRecord.Status.valueOf(rs.getString("status")),
                rs.getBoolean("referrer_reward_given"),
                rs.getBoolean("joiner_reward_given")
        );
    }

    private int countQuery(String sql, String uuidStr, long since) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuidStr);
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.severe("Error in count query: " + e.getMessage());
        }
        return 0;
    }

    private long getDayStart() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        return today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private long getWeekStart() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        java.time.LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        return weekStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
