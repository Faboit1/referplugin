package com.faboit.referplugin.database;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Factory that creates and initialises the correct {@link Database} implementation
 * based on the plugin configuration.
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private Database database;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() throws Exception {
        FileConfiguration config = plugin.getConfig();
        Logger log = plugin.getLogger();
        String type = config.getString("database.type", "SQLITE").toUpperCase();

        if ("MYSQL".equals(type)) {
            database = new MySQLDatabase(log, config);
        } else {
            database = new SQLiteDatabase(log, plugin.getDataFolder());
        }

        database.init();
        log.info("Database initialised (" + type + ").");
    }

    public Database getDatabase() {
        return database;
    }

    public void close() {
        if (database != null) {
            database.close();
        }
    }
}
