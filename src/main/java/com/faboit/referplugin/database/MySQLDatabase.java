package com.faboit.referplugin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/**
 * MySQL / MariaDB database backend.
 */
public class MySQLDatabase extends AbstractDatabase {

    private final FileConfiguration config;

    public MySQLDatabase(Logger log, FileConfiguration config) {
        super(log);
        this.config = config;
    }

    @Override
    protected HikariDataSource createDataSource() {
        String host     = config.getString("database.mysql.host", "localhost");
        int    port     = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "referplugin");
        String user     = config.getString("database.mysql.username", "root");
        String password = config.getString("database.mysql.password", "");
        int    poolSize = config.getInt("database.mysql.pool-size", 10);
        long   timeout  = config.getLong("database.mysql.connection-timeout", 30_000);

        HikariConfig hc = new HikariConfig();
        hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hc.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8");
        hc.setUsername(user);
        hc.setPassword(password);
        hc.setMaximumPoolSize(poolSize);
        hc.setConnectionTimeout(timeout);
        hc.setPoolName("ReferPlugin-MySQL");
        return new HikariDataSource(hc);
    }

    @Override
    protected String autoIncrementKeyword() {
        return "AUTO_INCREMENT";
    }

    /**
     * MySQL uses {@code INSERT ... ON DUPLICATE KEY UPDATE} instead of
     * SQLite's {@code ON CONFLICT ... DO UPDATE SET}.
     */
    @Override
    protected String upsertSql(String baseSql) {
        // Replace SQLite-specific upsert with MySQL equivalent
        return """
            INSERT INTO referral_players
              (uuid, username, reward_profile, total_referrals, successful_referrals,
               blocked_referrals, total_rewards, last_referral)
            VALUES (?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
              username             = VALUES(username),
              reward_profile       = VALUES(reward_profile),
              total_referrals      = VALUES(total_referrals),
              successful_referrals = VALUES(successful_referrals),
              blocked_referrals    = VALUES(blocked_referrals),
              total_rewards        = VALUES(total_rewards),
              last_referral        = VALUES(last_referral)
            """;
    }

    @Override
    protected String ensurePlayerSql(String baseSql) {
        return "INSERT IGNORE INTO referral_players (uuid, username) VALUES (?, ?)";
    }
}
