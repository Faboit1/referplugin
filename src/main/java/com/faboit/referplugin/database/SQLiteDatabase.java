package com.faboit.referplugin.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.util.logging.Logger;

/**
 * SQLite database backend.
 */
public class SQLiteDatabase extends AbstractDatabase {

    private final File dataFolder;

    public SQLiteDatabase(Logger log, File dataFolder) {
        super(log);
        this.dataFolder = dataFolder;
    }

    @Override
    protected HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath());
        config.setMaximumPoolSize(1);           // SQLite is single-writer
        config.setConnectionTimeout(30_000);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("foreign_keys", "ON");
        config.setPoolName("ReferPlugin-SQLite");
        return new HikariDataSource(config);
    }

    @Override
    protected String autoIncrementKeyword() {
        return "AUTOINCREMENT";
    }
}
