package com.faboit.referplugin.gui;

import com.faboit.referplugin.ReferPlugin;
import com.faboit.referplugin.config.ConfigManager;
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
 * Player-facing referral stats GUI.
 *
 * <p>Layout (54 slots):
 * <ul>
 *   <li>Slot 13 – Player skull / stats overview</li>
 *   <li>Slot 20 – Successful referrals</li>
 *   <li>Slot 22 – Total rewards earned</li>
 *   <li>Slot 24 – Blocked referrals</li>
 *   <li>Slot 31 – Milestone progress (glass pane bar)</li>
 *   <li>Slots 37–44 – Recent referral history (8 entries)</li>
 * </ul>
 */
public class PlayerGUI implements Listener {

    private static final String GUI_ID_PREFIX = "§0REFER_PLAYER_";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final ReferPlugin plugin;
    private final ConfigManager configManager;

    public PlayerGUI(ReferPlugin plugin) {
        this.plugin        = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public void open(Player player) {
        String title = ConfigManager.colorize(
                configManager.getConfig().getString("gui.player.title", "&6&lYour Referrals"));
        int rows = configManager.getConfig().getInt("gui.player.rows", 6);
        rows = Math.max(1, Math.min(6, rows));

        Inventory inv = Bukkit.createInventory(null, rows * 9,
                GUI_ID_PREFIX + player.getUniqueId());

        // Override title with configured value (title must match for guard check)
        Inventory display = Bukkit.createInventory(null, rows * 9, title);

        PlayerStats stats = plugin.getDb().getPlayerStats(player.getUniqueId());
        int total      = stats != null ? stats.getTotalReferrals()      : 0;
        int successful = stats != null ? stats.getSuccessfulReferrals() : 0;
        int blocked    = stats != null ? stats.getBlockedReferrals()    : 0;
        double rewards = stats != null ? stats.getTotalRewards()        : 0;
        String profile = stats != null ? stats.getRewardProfile()       : "default";

        // Slot 13 – Overview skull
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) skull.getItemMeta();
        if (sm != null) {
            sm.setOwningPlayer(player);
            sm.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + player.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Profile: " + ChatColor.YELLOW + profile);
            lore.add(ChatColor.GRAY + "Total Referrals: " + ChatColor.WHITE + total);
            sm.setLore(lore);
            skull.setItemMeta(sm);
        }
        display.setItem(13, skull);

        // Slot 20 – Successful referrals
        display.setItem(20, makeItem(Material.LIME_DYE, ChatColor.GREEN + "Successful Referrals",
                List.of(ChatColor.WHITE + String.valueOf(successful))));

        // Slot 22 – Total rewards
        display.setItem(22, makeItem(Material.SUNFLOWER, ChatColor.GOLD + "Total Rewards",
                List.of(ChatColor.WHITE + String.format("$%.2f", rewards))));

        // Slot 24 – Blocked referrals
        display.setItem(24, makeItem(Material.RED_DYE, ChatColor.RED + "Blocked Referrals",
                List.of(ChatColor.WHITE + String.valueOf(blocked))));

        // Slot 31 – Milestone progress
        addMilestoneBar(display, successful);

        // Slots 37–44 – Recent history
        List<ReferralRecord> recent = plugin.getDb().getRecentRecords(player.getUniqueId(), 8);
        for (int i = 0; i < Math.min(8, recent.size()); i++) {
            ReferralRecord r = recent.get(i);
            String when = DATE_FMT.format(Instant.ofEpochMilli(r.getTimestamp()));
            ChatColor statusColor = r.getStatus() == ReferralRecord.Status.SUCCESS
                    ? ChatColor.GREEN : ChatColor.RED;
            display.setItem(37 + i, makeItem(
                    Material.PAPER,
                    statusColor + r.getStatus().name(),
                    List.of(
                            ChatColor.GRAY + "Host: " + ChatColor.WHITE + safe(r.getReferralHost()),
                            ChatColor.GRAY + "When: " + ChatColor.WHITE + when
                    )));
        }

        // Fill empty slots with black glass
        ItemStack filler = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < display.getSize(); i++) {
            if (display.getItem(i) == null) display.setItem(i, filler);
        }

        player.openInventory(display);
    }

    private void addMilestoneBar(Inventory inv, int successful) {
        List<?> milestones = configManager.getConfig().getList("milestones");
        if (milestones == null || milestones.isEmpty()) return;

        // Find next milestone target
        int nextTarget = -1;
        for (Object raw : milestones) {
            if (!(raw instanceof java.util.Map<?,?> m)) continue;
            int t = Integer.parseInt(String.valueOf(m.getOrDefault("referrals", -1)));
            if (t > successful) { nextTarget = t; break; }
        }

        if (nextTarget < 0) {
            inv.setItem(31, makeItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "All milestones complete!", List.of()));
            return;
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Progress: " + ChatColor.YELLOW + successful + " / " + nextTarget);
        double progress = (double) successful / nextTarget;
        int filled = (int) (progress * 7);
        StringBuilder bar = new StringBuilder(ChatColor.GREEN.toString());
        for (int i = 0; i < 7; i++) bar.append(i < filled ? "█" : ChatColor.DARK_GRAY + "█");
        lore.add(bar.toString());
        inv.setItem(31, makeItem(Material.EXPERIENCE_BOTTLE,
                ChatColor.AQUA + "Milestone Progress", lore));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(ConfigManager.colorize(
                configManager.getConfig().getString("gui.player.title", "&6&lYour Referrals")))) return;
        event.setCancelled(true);
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

    private String safe(String s) { return s != null ? s : "unknown"; }
}
