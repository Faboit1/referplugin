package com.faboit.referplugin.gui;

import com.faboit.referplugin.ReferPlugin;
import com.faboit.referplugin.config.ConfigManager;
import com.faboit.referplugin.model.PlayerStats;
import com.faboit.referplugin.model.ReferralRecord;
import com.faboit.referplugin.util.NotificationUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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
 * Player-facing referral stats GUI.
 * All slot positions, materials, names, lore, sounds and filler are driven
 * entirely from {@code gui.player.*} in config.yml.
 */
public class PlayerGUI implements Listener {

    private final ReferPlugin plugin;
    private final ConfigManager configManager;

    public PlayerGUI(ReferPlugin plugin) {
        this.plugin        = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public void open(Player player) {
        ConfigurationSection guiCfg = configManager.getConfig()
                .getConfigurationSection("gui.player");
        if (guiCfg != null && !guiCfg.getBoolean("enabled", true)) {
            player.sendMessage(configManager.getMessage("feature-disabled"));
            return;
        }

        String title = ConfigManager.colorize(configManager.getConfig()
                .getString("gui.player.title", "&6&lYour Referrals"));
        int rows = Math.max(1, Math.min(6, configManager.getConfig()
                .getInt("gui.player.rows", 6)));
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        database().ensurePlayer(player.getUniqueId(), player.getName());
        PlayerStats stats = database().getPlayerStats(player.getUniqueId());

        String successfulStr  = stats != null ? String.valueOf(stats.getSuccessfulReferrals()) : "0";
        String totalStr       = stats != null ? String.valueOf(stats.getTotalReferrals())       : "0";
        String blockedStr     = stats != null ? String.valueOf(stats.getBlockedReferrals())     : "0";
        String rewardsStr     = stats != null ? String.format("%.2f", stats.getTotalRewards())  : "0.00";
        String profileStr     = stats != null ? stats.getRewardProfile()                        : "default";

        ConfigurationSection items = configManager.getConfig()
                .getConfigurationSection("gui.player.items");

        // Overview skull
        if (items != null && items.isConfigurationSection("overview")) {
            ConfigurationSection ov = items.getConfigurationSection("overview");
            assert ov != null;
            int slot = ov.getInt("slot", 13);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(player);
                sm.setDisplayName(colorize(applyStats(ov.getString("name", "&6&l%player%"),
                        player.getName(), successfulStr, totalStr, blockedStr, rewardsStr, profileStr)));
                sm.setLore(buildLore(ov.getStringList("lore"),
                        player.getName(), successfulStr, totalStr, blockedStr, rewardsStr, profileStr));
                skull.setItemMeta(sm);
            }
            setIfValid(inv, slot, skull);
        }

        // Successful referrals
        placeStatItem(inv, items, "successful", "slot", 20, "LIME_DYE",
                successfulStr, totalStr, blockedStr, rewardsStr, profileStr, player.getName());

        // Total rewards
        placeStatItem(inv, items, "rewards", "slot", 22, "SUNFLOWER",
                successfulStr, totalStr, blockedStr, rewardsStr, profileStr, player.getName());

        // Blocked referrals
        placeStatItem(inv, items, "blocked", "slot", 24, "RED_DYE",
                successfulStr, totalStr, blockedStr, rewardsStr, profileStr, player.getName());

        // Milestone progress
        addMilestoneItem(inv, items, stats != null ? stats.getSuccessfulReferrals() : 0);

        // Close button
        if (items != null && items.isConfigurationSection("close-button")
                && items.getBoolean("close-button.enabled", true)) {
            ConfigurationSection cb = items.getConfigurationSection("close-button");
            assert cb != null;
            int slot = cb.getInt("slot", 49);
            setIfValid(inv, slot, makeConfigItem(cb,
                    player.getName(), successfulStr, totalStr, blockedStr, rewardsStr, profileStr));
        }

        // Recent history
        addHistory(inv, player, items);

        // Filler
        fillEmpty(inv);

        player.openInventory(inv);

        // Open sound
        playSoundFromSection(player,
                configManager.getConfig().getConfigurationSection("gui.player.open-sound"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void placeStatItem(Inventory inv, ConfigurationSection items,
                                String key, String slotKey, int defaultSlot,
                                String defaultMaterial,
                                String successful, String total, String blocked,
                                String rewards, String profile, String player) {
        if (items == null || !items.isConfigurationSection(key)) return;
        ConfigurationSection sec = items.getConfigurationSection(key);
        assert sec != null;
        int slot = sec.getInt("slot", defaultSlot);
        setIfValid(inv, slot, makeConfigItem(sec, player, successful, total, blocked, rewards, profile));
    }

    private ItemStack makeConfigItem(ConfigurationSection sec,
                                      String player, String successful, String total,
                                      String blocked, String rewards, String profile) {
        String matName = sec.getString("material", "PAPER").toUpperCase();
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(applyStats(
                    sec.getString("name", ""), player, successful, total, blocked, rewards, profile)));
            meta.setLore(buildLore(sec.getStringList("lore"),
                    player, successful, total, blocked, rewards, profile));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addMilestoneItem(Inventory inv, ConfigurationSection items, int successful) {
        if (items == null || !items.isConfigurationSection("milestone")) return;
        ConfigurationSection ms = items.getConfigurationSection("milestone");
        assert ms != null;
        int slot = ms.getInt("slot", 31);

        List<?> milestones = configManager.getConfig().getList("milestones");
        String nextTargetStr = null;
        int nextTarget = -1;
        if (milestones != null) {
            for (Object raw : milestones) {
                if (!(raw instanceof java.util.Map<?,?> m)) continue;
                int t = parseInt(m.get("referrals"), -1);
                if (t > successful) { nextTarget = t; break; }
            }
        }

        Material mat = safeMaterial(ms.getString("material", "EXPERIENCE_BOTTLE"));
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) { setIfValid(inv, slot, item); return; }

        List<String> lore = new ArrayList<>();
        List<String> headerLore = ms.getStringList("lore-header");
        for (String l : headerLore) lore.add(colorize(l));

        String barFilled = ms.getString("bar-filled-char",  "█");
        String barEmpty  = ms.getString("bar-empty-char",   "█");
        String colFilled = ms.getString("bar-filled-colour","&a");
        String colEmpty  = ms.getString("bar-empty-colour", "&8");
        int    barWidth  = ms.getInt("bar-width", 20);

        String msMsg = configManager.getConfig().getString("messages.gui.player.milestone-progress",
                "&7Progress: &e%current% &7/ &e%target%");

        if (nextTarget < 0) {
            meta.setDisplayName(colorize(ms.getString("name", "&b&lMilestone Progress")));
            lore.add(colorize(configManager.getMessages()
                    .getString("gui.player.milestone-complete", "&a✔ All milestones completed!")));
        } else {
            meta.setDisplayName(colorize(ms.getString("name", "&b&lMilestone Progress")));
            String progressMsg = colorize(configManager.getMessages()
                    .getString("gui.player.milestone-progress", "&7Progress: &e%current% &7/ &e%target%")
                    .replace("%current%", String.valueOf(successful))
                    .replace("%target%",  String.valueOf(nextTarget)));
            lore.add(progressMsg);

            int filled = Math.min(barWidth, (int) ((double) successful / nextTarget * barWidth));
            StringBuilder bar = new StringBuilder(colorize(colFilled));
            for (int i = 0; i < barWidth; i++) {
                if (i == filled) bar.append(colorize(colEmpty));
                bar.append(i < filled ? barFilled : barEmpty);
            }
            lore.add(bar.toString());
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        setIfValid(inv, slot, item);
    }

    private void addHistory(Inventory inv, Player player, ConfigurationSection items) {
        ConfigurationSection historyCfg = configManager.getConfig()
                .getConfigurationSection("gui.player.history");
        int startSlot = historyCfg != null ? historyCfg.getInt("start-slot", 37) : 37;
        int histSize  = historyCfg != null ? historyCfg.getInt("size", 8) : 8;
        String dateFmt = historyCfg != null ? historyCfg.getString("date-format", "yyyy-MM-dd HH:mm") : "yyyy-MM-dd HH:mm";
        String tz = configManager.getConfig().getString("formatting.timezone", "UTC");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(dateFmt)
                .withZone(ZoneId.of(tz));

        String successMat = historyCfg != null ? historyCfg.getString("success-material", "LIME_CONCRETE") : "LIME_CONCRETE";
        String blockedMat = historyCfg != null ? historyCfg.getString("blocked-material", "RED_CONCRETE") : "RED_CONCRETE";

        List<ReferralRecord> recent = database().getRecentRecords(player.getUniqueId(), histSize);
        if (recent.isEmpty()) {
            String noHistory = colorize(configManager.getMessages()
                    .getString("gui.player.no-history", "&7No referral history yet."));
            ItemStack noItem = makeBasicItem(Material.GRAY_DYE, noHistory, List.of());
            setIfValid(inv, startSlot, noItem);
            return;
        }

        for (int i = 0; i < Math.min(histSize, recent.size()); i++) {
            ReferralRecord r = recent.get(i);
            String when = fmt.format(Instant.ofEpochMilli(r.getTimestamp()));
            boolean success = r.getStatus() == ReferralRecord.Status.SUCCESS;
            Material mat = safeMaterial(success ? successMat : blockedMat);

            String displayStatus = success
                    ? colorize(configManager.getMessages().getString("gui.player.history-entry-success", "&aSuccess"))
                    : colorize(configManager.getMessages().getString("gui.player.history-entry-blocked", "&cBlocked"));

            List<String> lore = new ArrayList<>();
            lore.add(colorize(configManager.getMessages()
                    .getString("gui.player.history-host-label", "&7Host: &f%referral_host%")
                    .replace("%referral_host%", safe(r.getReferralHost()))));
            lore.add(colorize(configManager.getMessages()
                    .getString("gui.player.history-when-label", "&7When: &f%date%")
                    .replace("%date%", when)));
            if (!success) {
                lore.add(ChatColor.GRAY + "Status: " + r.getStatus().name());
            }

            setIfValid(inv, startSlot + i, makeBasicItem(mat, displayStatus, lore));
        }
    }

    private void fillEmpty(Inventory inv) {
        ConfigurationSection filler = configManager.getConfig()
                .getConfigurationSection("gui.player.filler");
        if (filler == null || !filler.getBoolean("enabled", true)) return;
        Material mat = safeMaterial(filler.getString("material", "BLACK_STAINED_GLASS_PANE"));
        String name = colorize(filler.getString("name", " "));
        ItemStack item = makeBasicItem(mat, name, List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, item);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String expectedTitle = ConfigManager.colorize(configManager.getConfig()
                .getString("gui.player.title", "&6&lYour Referrals"));
        if (!event.getView().getTitle().equals(expectedTitle)) return;
        event.setCancelled(true);

        // Close button
        if (event.getCurrentItem() != null) {
            ConfigurationSection cb = configManager.getConfig()
                    .getConfigurationSection("gui.player.items.close-button");
            if (cb != null && event.getSlot() == cb.getInt("slot", 49)) {
                playSoundFromSection(player,
                        configManager.getConfig().getConfigurationSection("gui.player.close-sound"));
                player.closeInventory();
            }
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private com.faboit.referplugin.database.Database database() {
        return plugin.getDb();
    }

    private ItemStack makeBasicItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); }
        return item;
    }

    private void setIfValid(Inventory inv, int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item);
    }

    private List<String> buildLore(List<String> raw, String player,
                                    String successful, String total, String blocked,
                                    String rewards, String profile) {
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            result.add(colorize(applyStats(line, player, successful, total, blocked, rewards, profile)));
        }
        return result;
    }

    private String applyStats(String text, String player, String successful, String total,
                               String blocked, String rewards, String profile) {
        return text
                .replace("%player%",                player)
                .replace("%referral_count%",        successful)
                .replace("%referral_total%",        total)
                .replace("%referral_blocked%",      blocked)
                .replace("%referral_total_rewards%",rewards)
                .replace("%referral_profile%",      profile);
    }

    private String colorize(String s) {
        return ConfigManager.colorize(s);
    }

    private Material safeMaterial(String name) {
        if (name == null) return Material.PAPER;
        Material mat = Material.matchMaterial(name.toUpperCase());
        return mat != null ? mat : Material.PAPER;
    }

    private int parseInt(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }

    private String safe(String s) { return s != null ? s : "unknown"; }

    private void playSoundFromSection(Player player, ConfigurationSection sec) {
        NotificationUtil.playSound(player, sec, plugin.getLogger());
    }
}
