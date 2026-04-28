package com.faboit.referplugin.gui;

import com.faboit.referplugin.ReferPlugin;
import com.faboit.referplugin.config.ConfigManager;
import com.faboit.referplugin.database.Database;
import com.faboit.referplugin.model.PlayerStats;
import com.faboit.referplugin.model.ReferralRecord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin panel GUI.
 *
 * <p>Layout (54 slots):
 * <ul>
 *   <li>Slots 0–8   – Top 9 referrers leaderboard</li>
 *   <li>Slots 18–26 – Suspicious activity log (9 recent blocked events)</li>
 *   <li>Slot 49     – Close button</li>
 * </ul>
 */
public class AdminGUI implements Listener {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final ReferPlugin   plugin;
    private final ConfigManager configManager;
    private final Database      database;

    public AdminGUI(ReferPlugin plugin) {
        this.plugin        = plugin;
        this.configManager = plugin.getConfigManager();
        this.database      = plugin.getDb();
    }

    public void open(Player admin) {
        String title = ConfigManager.colorize(
                configManager.getConfig().getString("gui.admin.title", "&c&lAdmin Panel"));
        int rows = configManager.getConfig().getInt("gui.admin.rows", 6);
        rows = Math.max(1, Math.min(6, rows));

        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        // Row 1: Top referrers leaderboard (slots 0–8)
        inv.setItem(0, makeItem(Material.NETHER_STAR, ChatColor.AQUA + "" + ChatColor.BOLD + "Top Referrers", List.of()));
        List<PlayerStats> top = database.getTopReferrers(8);
        for (int i = 0; i < top.size(); i++) {
            PlayerStats s = top.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Successful: " + ChatColor.GREEN + s.getSuccessfulReferrals());
            lore.add(ChatColor.GRAY + "Blocked:    " + ChatColor.RED   + s.getBlockedReferrals());
            lore.add(ChatColor.GRAY + "Rewards:    " + ChatColor.GOLD  + String.format("$%.2f", s.getTotalRewards()));
            lore.add(ChatColor.GRAY + "Profile:    " + ChatColor.YELLOW + s.getRewardProfile());
            inv.setItem(1 + i, makePlayerHead(s.getUsername(),
                    ChatColor.GOLD + "#" + (i + 1) + " " + s.getUsername(), lore));
        }

        // Section header: suspicious activity
        inv.setItem(18, makeItem(Material.REDSTONE, ChatColor.RED + "" + ChatColor.BOLD + "Suspicious Activity", List.of()));

        // Row 3: Suspicious records (slots 19–26)
        List<ReferralRecord> suspicious = database.getSuspiciousRecords(8);
        for (int i = 0; i < suspicious.size(); i++) {
            ReferralRecord r = suspicious.get(i);
            String when = DATE_FMT.format(Instant.ofEpochMilli(r.getTimestamp()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Status:  " + ChatColor.RED   + r.getStatus().name());
            lore.add(ChatColor.GRAY + "Joiner:  " + ChatColor.WHITE + r.getJoinerUuid().toString().substring(0, 8) + "...");
            lore.add(ChatColor.GRAY + "IP:      " + ChatColor.WHITE + safe(r.getJoinerIp()));
            lore.add(ChatColor.GRAY + "Host:    " + ChatColor.WHITE + safe(r.getReferralHost()));
            lore.add(ChatColor.GRAY + "When:    " + ChatColor.WHITE + when);
            inv.setItem(19 + i, makeItem(Material.RED_CONCRETE, ChatColor.RED + r.getStatus().name(), lore));
        }

        // Close button
        inv.setItem(49, makeItem(Material.BARRIER, ChatColor.RED + "Close", List.of()));

        // Filler
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        admin.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        String title = event.getView().getTitle();
        if (!title.equals(ConfigManager.colorize(
                configManager.getConfig().getString("gui.admin.title", "&c&lAdmin Panel")))) return;
        event.setCancelled(true);
        if (event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.BARRIER) {
            p.closeInventory();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
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

    private String safe(String s) { return s != null ? s : "N/A"; }
}
