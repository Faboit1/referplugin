package com.faboit.referplugin;

import com.faboit.referplugin.antiabuse.AbuseDetector;
import com.faboit.referplugin.command.ReferralCommand;
import com.faboit.referplugin.config.ConfigManager;
import com.faboit.referplugin.database.Database;
import com.faboit.referplugin.database.DatabaseManager;
import com.faboit.referplugin.gui.AdminGUI;
import com.faboit.referplugin.gui.PlayerGUI;
import com.faboit.referplugin.hostname.HostnameParser;
import com.faboit.referplugin.listener.LoginListener;
import com.faboit.referplugin.placeholder.ReferralExpansion;
import com.faboit.referplugin.reward.RewardManager;
import com.faboit.referplugin.velocity.VelocityBridge;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for ReferPlugin.
 *
 * <p>Initialisation order:
 * <ol>
 *   <li>Config</li>
 *   <li>Database</li>
 *   <li>Vault Economy hook</li>
 *   <li>Core components (hostname parser, abuse detector, reward manager)</li>
 *   <li>Listeners, commands, PlaceholderAPI expansion</li>
 * </ol>
 */
public class ReferPlugin extends JavaPlugin {

    private ConfigManager   configManager;
    private DatabaseManager databaseManager;
    private HostnameParser  hostnameParser;
    private AbuseDetector   abuseDetector;
    private RewardManager   rewardManager;
    private PlayerGUI       playerGUI;
    private AdminGUI        adminGUI;
    private Economy         economy;
    private VelocityBridge  velocityBridge;
    private ReferralLogger  referralLogger;

    @Override
    public void onEnable() {
        // ── 1. Config ────────────────────────────────────────────────────────
        configManager = new ConfigManager(this);
        configManager.load();

        // ── 1b. Referral logger ──────────────────────────────────────────────
        referralLogger = new ReferralLogger(getDataFolder(), getLogger());

        // ── 2. Database ──────────────────────────────────────────────────────
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.init();
        } catch (Exception e) {
            getLogger().severe("Failed to initialise database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ── 3. Vault hook ────────────────────────────────────────────────────
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<Economy> rsp =
                    getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                getLogger().info("Vault economy hooked: " + economy.getName());
            } else {
                getLogger().warning("Vault found but no economy provider registered.");
            }
        } else {
            getLogger().warning("Vault not found – money rewards disabled.");
        }

        // ── 4. Core components ───────────────────────────────────────────────
        reloadComponents();

        // ── 5. Velocity bridge (plugin messaging) ────────────────────────────
        velocityBridge = new VelocityBridge();
        getServer().getMessenger().registerIncomingPluginChannel(
                this, VelocityBridge.CHANNEL, velocityBridge);
        getServer().getMessenger().registerOutgoingPluginChannel(
                this, VelocityBridge.CHANNEL);
        getLogger().info("Velocity bridge registered on channel: " + VelocityBridge.CHANNEL);

        // ── 6. Listeners (registered once – NOT inside reloadComponents) ─────
        LoginListener loginListener = new LoginListener(this);
        Bukkit.getPluginManager().registerEvents(loginListener, this);
        Bukkit.getPluginManager().registerEvents(playerGUI, this);
        Bukkit.getPluginManager().registerEvents(adminGUI, this);

        // ── 7. PlaceholderAPI expansion ──────────────────────────────────────
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ReferralExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        // ── 8. Commands ──────────────────────────────────────────────────────
        ReferralCommand cmd = new ReferralCommand(this);
        var cmdObj = getCommand("referral");
        if (cmdObj != null) {
            cmdObj.setExecutor(cmd);
            cmdObj.setTabCompleter(cmd);
        }

        getLogger().info("ReferPlugin enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getLogger().info("ReferPlugin disabled.");
    }

    /**
     * Rebuilds all stateless components from the current config.
     * Called on enable and on /referral reload.
     * Does NOT re-register event listeners (they are registered once at enable time).
     */
    public void reloadComponents() {
        hostnameParser = new HostnameParser(configManager.getConfig());
        abuseDetector  = new AbuseDetector(configManager, getDb(), getLogger());
        rewardManager  = new RewardManager(this, configManager, getLogger());
        rewardManager.setEconomy(economy);

        playerGUI = new PlayerGUI(this);
        adminGUI  = new AdminGUI(this);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public ConfigManager   getConfigManager()   { return configManager; }
    public Database        getDb()              { return databaseManager != null ? databaseManager.getDatabase() : null; }
    public HostnameParser  getHostnameParser()  { return hostnameParser; }
    public AbuseDetector   getAbuseDetector()   { return abuseDetector; }
    public RewardManager   getRewardManager()   { return rewardManager; }
    public PlayerGUI       getPlayerGUI()       { return playerGUI; }
    public AdminGUI        getAdminGUI()        { return adminGUI; }
    public Economy         getEconomy()         { return economy; }
    public VelocityBridge  getVelocityBridge()  { return velocityBridge; }
    public ReferralLogger  getReferralLogger()  { return referralLogger; }
}
