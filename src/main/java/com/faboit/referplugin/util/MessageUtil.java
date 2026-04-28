package com.faboit.referplugin.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Utility methods for player messaging.
 */
public final class MessageUtil {

    private MessageUtil() {}

    /** Send a colorized message to a player, no-op if player is null. */
    public static void send(Player player, String message) {
        if (player == null || message == null) return;
        player.sendMessage(message);
    }

    /** Broadcast a message to the entire server. */
    public static void broadcast(String message) {
        if (message == null) return;
        Bukkit.broadcastMessage(message);
    }

    /**
     * Replace common referral placeholders in a string before dispatching.
     * PAPI expansions are handled separately in {@link com.faboit.referplugin.placeholder.ReferralExpansion}.
     */
    public static String replacePlaceholders(String text, String referrer, String joiner,
                                              String host, int count, double totalRewards,
                                              String profile) {
        if (text == null) return "";
        return text
                .replace("%referrer%",              safe(referrer))
                .replace("%joiner%",                safe(joiner))
                .replace("%referral_host%",         safe(host))
                .replace("%referral_count%",        String.valueOf(count))
                .replace("%referral_total_rewards%",String.format("%.2f", totalRewards))
                .replace("%referral_profile%",      safe(profile));
    }

    private static String safe(String s) { return s != null ? s : ""; }
}
