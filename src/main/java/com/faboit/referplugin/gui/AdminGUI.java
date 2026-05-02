package com.faboit.referplugin.gui;

import com.faboit.referplugin.ReferPlugin;
import com.faboit.referplugin.config.ConfigManager;
import com.faboit.referplugin.database.Database;
import com.faboit.referplugin.model.PlayerStats;
import com.faboit.referplugin.model.ReferralRecord;
import com.faboit.referplugin.util.NotificationUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin panel GUI.
 * All slot positions, materials, names, lore and filler are driven entirely
 * from {@code gui.admin.*} in config.yml.
 */
public class AdminGUI implements Listener {

    private final ReferPlugin   plugin;
    private final ConfigManager configManager;
    private final Database      database;

    public AdminGUI(ReferPlugin plugin) {
        this.plugin        = plugin;
        this.configManager = plugin.getConfigManager();
        this.database      = plugin.getDb();
    }

    public void open(Player admin) {
        ConfigurationSection guiCfg = configManager.getConfig()
                .getConfigurationSection("gui.admin");
        if (guiCfg != null && !guiCfg.getBoolean("enabled", true)) {
            admin.sendMessage(configManager.getMessage("feature-disabled"));
            return;
        }

        String title = ConfigManager.colorize(configManager.getConfig()
                .getString("gui.admin.title", "&c&lAdmin Panel"));
        int rows = Math.max(1, Math.min(6, configManager.getConfig()
                .getInt("gui.admin.rows", 6)));
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        ConfigurationSection items = configManager.getConfig()
                .getConfigurationSection("gui.admin.items");

        // Leaderboard header
        placeItemFromSection(inv, items, "leaderboard-header", 0, "NETHER_STAR");

        // Leaderboard entries
        int lbSize = items != null ? items.getInt("leaderboard-size", 8) : 8;
        List<PlayerStats> top = database.getTopReferrers(lbSize);
        for (int i = 0; i < top.size(); i++) {
            PlayerStats s = top.get(i);
            int rank = i + 1;
            List<String> lore = buildLeaderboardLore(s, rank);
            inv.setItem(1 + i, makePlayerHead(s.getUsername(),
                    colorize(configManager.getMessages()
                            .getString("gui.admin.leaderboard-rank", "&e#%rank% &f%player%")
                            .replace("%rank%",  String.valueOf(rank))
                            .replace("%player%", s.getUsername())),
                    lore));
        }
        if (top.isEmpty()) {
            inv.setItem(1, makeBasicItem(Material.GRAY_DYE,
                    colorize(configManager.getMessages()
                            .getString("gui.admin.no-leaderboard", "&7No referral data yet.")),
                    List.of()));
        }

        // Suspicious header
        placeItemFromSection(inv, items, "suspicious-header", 18, "REDSTONE");

        // Suspicious entries
        String dateFmt = configManager.getConfig()
                .getString("gui.admin.history.date-format", "yyyy-MM-dd HH:mm");
        String tz = configManager.getConfig().getString("formatting.timezone", "UTC");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(dateFmt).withZone(ZoneId.of(tz));

        int suspSize = items != null ? items.getInt("suspicious-size", 8) : 8;
        List<ReferralRecord> suspicious = database.getSuspiciousRecords(suspSize);
        for (int i = 0; i < suspicious.size(); i++) {
            ReferralRecord r = suspicious.get(i);
            String when = fmt.format(Instant.ofEpochMilli(r.getTimestamp()));
            List<String> lore = buildSuspiciousLore(r, when);
            inv.setItem(19 + i, makeBasicItem(Material.RED_CONCRETE,
                    colorize(ChatColor.RED + r.getStatus().name()), lore));
        }
        if (suspicious.isEmpty()) {
            inv.setItem(19, makeBasicItem(Material.LIME_DYE,
                    colorize(configManager.getMessages()
                            .getString("gui.admin.no-suspicious", "&7No suspicious activity recorded.")),
                    List.of()));
        }

        // Latest referrals header
        placeItemFromSection(inv, items, "latest-header", 27, "EMERALD");

        // Latest referral entries
        int latestSize = items != null ? items.getInt("latest-size", 8) : 8;
        List<ReferralRecord> latest = database.getLatestSuccessfulRecords(latestSize);
        for (int i = 0; i < latest.size(); i++) {
            ReferralRecord r = latest.get(i);
            String when = fmt.format(Instant.ofEpochMilli(r.getTimestamp()));
            String referrerDisplay = r.getReferrerName() != null ? r.getReferrerName()
                    : r.getReferrerUuid().toString().substring(0, 8) + "…";
            String joinerDisplay   = r.getJoinerName() != null ? r.getJoinerName()
                    : r.getJoinerUuid().toString().substring(0, 8) + "…";
            String itemName = colorize(configManager.getMessages()
                    .getString("gui.admin.latest-entry", "&a%referrer% &7→ &e%joiner%")
                    .replace("%referrer%", referrerDisplay)
                    .replace("%joiner%",   joinerDisplay));
            List<String> lore = buildLatestLore(r, joinerDisplay, when);
            inv.setItem(28 + i, makePlayerHead(referrerDisplay, itemName, lore));
        }
        if (latest.isEmpty()) {
            inv.setItem(28, makeBasicItem(Material.GRAY_DYE,
                    colorize(configManager.getMessages()
                            .getString("gui.admin.no-latest", "&7No successful referrals yet.")),
                    List.of()));
        }

        // Close button
        if (items != null && items.isConfigurationSection("close-button")) {
            int cbSlot = items.getInt("close-button.slot", 49);
            placeItemFromSection(inv, items, "close-button", cbSlot, "BARRIER");
        }

        // Filler
        fillEmpty(inv);

        admin.openInventory(inv);

        // Open sound
        playSoundFromSection(admin,
                configManager.getConfig().getConfigurationSection("gui.admin.open-sound"));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        String expectedTitle = ConfigManager.colorize(configManager.getConfig()
                .getString("gui.admin.title", "&c&lAdmin Panel"));
        if (!event.getView().getTitle().equals(expectedTitle)) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        ConfigurationSection items = configManager.getConfig()
                .getConfigurationSection("gui.admin.items");
        if (items == null) return;
        int closeSlot = items.getInt("close-button.slot", 49);
        if (event.getSlot() == closeSlot) {
            p.closeInventory();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> buildLeaderboardLore(PlayerStats s, int rank) {
        List<String> lore = new ArrayList<>();
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.leaderboard-stats",
                        "&7Successful: &a%referral_count%  &7Blocked: &c%referral_blocked%")
                .replace("%referral_count%",   String.valueOf(s.getSuccessfulReferrals()))
                .replace("%referral_blocked%", String.valueOf(s.getBlockedReferrals()))));
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.leaderboard-rewards",
                        "&7Rewards: &6$%referral_total_rewards%")
                .replace("%referral_total_rewards%", String.format("%.2f", s.getTotalRewards()))));
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.leaderboard-profile",
                        "&7Profile: &e%referral_profile%")
                .replace("%referral_profile%", s.getRewardProfile())));
        return lore;
    }

    private List<String> buildLatestLore(ReferralRecord r, String joinerDisplay, String when) {
        List<String> lore = new ArrayList<>();
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.latest-joiner", "&7Joiner: &e%joiner%")
                .replace("%joiner%", joinerDisplay)));
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.latest-when", "&7When: &f%date%")
                .replace("%date%", when)));
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.latest-host", "&7Host: &f%referral_host%")
                .replace("%referral_host%", safe(r.getReferralHost()))));
        return lore;
    }

    private List<String> buildSuspiciousLore(ReferralRecord r, String when) {
        List<String> lore = new ArrayList<>();
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.suspicious-status", "&cStatus: &f%status%")
                .replace("%status%", r.getStatus().name())));
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.suspicious-joiner", "&7Joiner: &f%uuid%")
                .replace("%uuid%", r.getJoinerUuid().toString().substring(0, 8) + "…")));
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.suspicious-ip", "&7IP: &f%ip%")
                .replace("%ip%", safe(r.getJoinerIp()))));
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.suspicious-host", "&7Host: &f%referral_host%")
                .replace("%referral_host%", safe(r.getReferralHost()))));
        lore.add(colorize(configManager.getMessages()
                .getString("gui.admin.suspicious-when", "&7When: &f%date%")
                .replace("%date%", when)));
        return lore;
    }

    private void placeItemFromSection(Inventory inv, ConfigurationSection items,
                                       String key, int defaultSlot, String defaultMat) {
        if (items == null || !items.isConfigurationSection(key)) return;
        ConfigurationSection sec = items.getConfigurationSection(key);
        assert sec != null;
        int slot = sec.getInt("slot", defaultSlot);
        Material mat = safeMaterial(sec.getString("material", defaultMat));
        String name = colorize(sec.getString("name", ""));
        List<String> lore = new ArrayList<>();
        for (String l : sec.getStringList("lore")) lore.add(colorize(l));
        setIfValid(inv, slot, makeBasicItem(mat, name, lore));
    }

    private void fillEmpty(Inventory inv) {
        ConfigurationSection filler = configManager.getConfig()
                .getConfigurationSection("gui.admin.filler");
        if (filler == null || !filler.getBoolean("enabled", true)) return;
        Material mat = safeMaterial(filler.getString("material", "GRAY_STAINED_GLASS_PANE"));
        String name = colorize(filler.getString("name", " "));
        ItemStack item = makeBasicItem(mat, name, List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, item);
        }
    }

    private ItemStack makeBasicItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack makePlayerHead(String owner, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) skull.getItemMeta();
        if (sm != null) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            sm.setDisplayName(name);
            sm.setLore(lore);
            skull.setItemMeta(sm);
        }
        return skull;
    }

    private void setIfValid(Inventory inv, int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item);
    }

    private Material safeMaterial(String name) {
        if (name == null) return Material.STONE;
        Material mat = Material.matchMaterial(name.toUpperCase());
        return mat != null ? mat : Material.STONE;
    }

    private String colorize(String s) { return ConfigManager.colorize(s); }
    private String safe(String s) { return s != null ? s : "N/A"; }

    private void playSoundFromSection(Player player, ConfigurationSection sec) {
        NotificationUtil.playSound(player, sec, plugin.getLogger());
    }
}
