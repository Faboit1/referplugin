package com.faboit.referplugin.command;

import com.faboit.referplugin.ReferPlugin;
import com.faboit.referplugin.config.ConfigManager;
import com.faboit.referplugin.database.Database;
import com.faboit.referplugin.model.PlayerStats;
import com.faboit.referplugin.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Handles all {@code /referral} sub-commands.
 *
 * <pre>
 * /referral              → help
 * /referral stats        → own stats
 * /referral stats <name> → another player's stats (admin)
 * /referral gui          → open player GUI
 * /referral admin        → open admin GUI (admin)
 * /referral info [name]  → info about a player's profile (admin)
 * /referral setprofile <player> <profile> → set reward profile (admin)
 * /referral reload       → reload config (admin)
 * </pre>
 */
public class ReferralCommand implements CommandExecutor, TabCompleter {

    private final ReferPlugin   plugin;
    private final ConfigManager configManager;
    private final Database      database;

    public ReferralCommand(ReferPlugin plugin) {
        this.plugin        = plugin;
        this.configManager = plugin.getConfigManager();
        this.database      = plugin.getDb();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "stats"      -> handleStats(sender, args);
            case "gui"        -> handleGui(sender);
            case "admin"      -> handleAdmin(sender);
            case "info"       -> handleInfo(sender, args);
            case "setprofile" -> handleSetProfile(sender, args);
            case "reload"     -> handleReload(sender);
            case "help"       -> { sendHelp(sender); yield true; }
            default           -> { MessageUtil.send(sender instanceof Player p ? p : null,
                                       configManager.getMessage("unknown-command"));
                                   yield true; }
        };
    }

    // ── Sub-commands ─────────────────────────────────────────────────────────

    private boolean handleStats(CommandSender sender, String[] args) {
        UUID target;
        String targetName;

        if (args.length >= 2) {
            if (!sender.hasPermission("referplugin.admin")) {
                send(sender, configManager.getMessage("no-permission"));
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            target     = op.getUniqueId();
            targetName = op.getName() != null ? op.getName() : args[1];
        } else {
            if (!(sender instanceof Player p)) {
                send(sender, configManager.getMessage("player-only"));
                return true;
            }
            target     = p.getUniqueId();
            targetName = p.getName();
        }

        PlayerStats stats = database.getPlayerStats(target);
        if (stats == null) {
            send(sender, configManager.getMessage("player-not-found", "%player%", targetName));
            return true;
        }

        send(sender, configManager.getMessage("stats.header"));
        send(sender, configManager.getMessage("stats.total",
                "%referral_count%", String.valueOf(stats.getTotalReferrals())));
        send(sender, configManager.getMessage("stats.successful",
                "%successful%", String.valueOf(stats.getSuccessfulReferrals())));
        send(sender, configManager.getMessage("stats.blocked",
                "%blocked%", String.valueOf(stats.getBlockedReferrals())));
        send(sender, configManager.getMessage("stats.rewards",
                "%referral_total_rewards%", String.format("%.2f", stats.getTotalRewards())));
        send(sender, configManager.getMessage("stats.footer"));
        return true;
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            send(sender, configManager.getMessage("player-only"));
            return true;
        }
        if (!p.hasPermission("referplugin.gui")) {
            send(sender, configManager.getMessage("no-permission"));
            return true;
        }
        database.ensurePlayer(p.getUniqueId(), p.getName());
        plugin.getPlayerGUI().open(p);
        return true;
    }

    private boolean handleAdmin(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            send(sender, configManager.getMessage("player-only"));
            return true;
        }
        if (!p.hasPermission("referplugin.admin")) {
            send(sender, configManager.getMessage("no-permission"));
            return true;
        }
        plugin.getAdminGUI().open(p);
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("referplugin.admin")) {
            send(sender, configManager.getMessage("no-permission"));
            return true;
        }
        String targetName = args.length >= 2 ? args[1] :
                (sender instanceof Player p ? p.getName() : null);
        if (targetName == null) { send(sender, configManager.getMessage("player-only")); return true; }

        OfflinePlayer op  = Bukkit.getOfflinePlayer(targetName);
        PlayerStats stats = database.getPlayerStats(op.getUniqueId());
        if (stats == null) {
            send(sender, configManager.getMessage("player-not-found", "%player%", targetName));
            return true;
        }
        send(sender, ConfigManager.colorize(
                "&6--- " + stats.getUsername() + " ---"));
        send(sender, ConfigManager.colorize(
                "&7Profile: &e" + stats.getRewardProfile()));
        send(sender, ConfigManager.colorize(
                "&7Successful: &a" + stats.getSuccessfulReferrals()
                        + " &7| Blocked: &c" + stats.getBlockedReferrals()));
        send(sender, ConfigManager.colorize(
                "&7Total rewards: &6$" + String.format("%.2f", stats.getTotalRewards())));
        return true;
    }

    private boolean handleSetProfile(CommandSender sender, String[] args) {
        if (!sender.hasPermission("referplugin.admin")) {
            send(sender, configManager.getMessage("no-permission"));
            return true;
        }
        if (args.length < 3) {
            send(sender, ConfigManager.colorize("&cUsage: /referral setprofile <player> <profile>"));
            return true;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
        database.ensurePlayer(op.getUniqueId(), args[1]);
        database.setRewardProfile(op.getUniqueId(), args[2]);
        send(sender, ConfigManager.colorize(
                "&aSet reward profile for &e" + args[1] + " &ato &6" + args[2]));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("referplugin.reload")) {
            send(sender, configManager.getMessage("no-permission"));
            return true;
        }
        configManager.reload();
        plugin.reloadComponents();
        send(sender, configManager.getMessage("plugin-reloaded"));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        send(sender, configManager.getMessage("help.header"));
        send(sender, configManager.getMessage("help.use"));
        send(sender, configManager.getMessage("help.gui"));
        if (sender.hasPermission("referplugin.admin")) {
            send(sender, configManager.getMessage("help.info"));
            send(sender, configManager.getMessage("help.reload"));
        }
        send(sender, configManager.getMessage("help.footer"));
    }

    // ── Tab completion ───────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new java.util.ArrayList<>(Arrays.asList("stats", "gui", "help"));
            if (sender.hasPermission("referplugin.admin")) {
                subs.addAll(Arrays.asList("admin", "info", "setprofile", "reload"));
            }
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stats")
                || args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("setprofile"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private void send(CommandSender sender, String msg) {
        if (msg == null) return;
        sender.sendMessage(msg);
    }
}
