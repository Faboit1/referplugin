package com.faboit.referplugin.config;

import com.faboit.referplugin.ReferPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Manages loading, saving and reloading of {@code config.yml} and
 * {@code messages.yml}.
 */
public class ConfigManager {

    private final ReferPlugin plugin;
    private final Logger log;

    private FileConfiguration config;
    private FileConfiguration messages;

    public ConfigManager(ReferPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    public void load() {
        // config.yml
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // messages.yml
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Apply defaults from the bundled resource so new keys are always present
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            messages.setDefaults(defaults);
        }

        log.info("Configuration loaded.");
    }

    public void reload() {
        load();
        log.info("Configuration reloaded.");
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    /**
     * Returns a formatted message string with the configured prefix prepended.
     * Supports {@code &} colour codes.
     */
    public String getMessage(String path) {
        String prefix = config.getString("message-prefix", "&8[&6Refer&8] &r");
        String msg    = messages.getString(path, "&cMissing message: " + path);
        return colorize(prefix + msg);
    }

    /**
     * Returns a formatted message with replacements applied after PAPI processing.
     */
    public String getMessage(String path, String... replacements) {
        String msg = getMessage(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public static String colorize(String s) {
        if (s == null) return "";
        return s.replace('&', '\u00A7');
    }
}
