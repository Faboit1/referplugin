package com.faboit.referplugin.util;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Reads a {@code notifications} config sub-section and dispatches titles,
 * action bars, sounds, broadcasts and fireworks to the appropriate players.
 *
 * <p>Expected config layout (all sub-keys are optional):
 * <pre>
 * notifications:
 *   broadcast:
 *     enabled: true
 *     message: "..."
 *   title:
 *     enabled: true
 *     title: "..."
 *     subtitle: "..."
 *     fade-in: 10
 *     stay: 60
 *     fade-out: 10
 *   actionbar:
 *     enabled: true
 *     message: "..."
 *     duration-ticks: 60
 *   sound:
 *     enabled: true
 *     sound: ENTITY_PLAYER_LEVELUP
 *     volume: 1.0
 *     pitch: 1.0
 *   fireworks:
 *     enabled: false
 *     count: 3
 *     power: 1
 * </pre>
 */
public final class NotificationUtil {

    private NotificationUtil() {}

    /**
     * Fire every enabled notification in the given section for {@code target}.
     *
     * @param target     The player to notify.
     * @param section    The {@code notifications} config section (may be null – no-op).
     * @param placeholders Flat key-value pairs to replace in messages (e.g. "%money%", "500").
     */
    public static void send(Player target, ConfigurationSection section,
                             Logger log, String... placeholders) {
        if (target == null || section == null) return;

        sendBroadcast(section.getConfigurationSection("broadcast"), placeholders);
        sendTitle(target, section.getConfigurationSection("title"), placeholders);
        sendActionBar(target, section.getConfigurationSection("actionbar"), placeholders);
        playSound(target, section.getConfigurationSection("sound"), log);
        spawnFireworks(target, section.getConfigurationSection("fireworks"), log);
    }

    // ── Broadcast ────────────────────────────────────────────────────────────

    public static void sendBroadcast(ConfigurationSection sec, String... placeholders) {
        if (sec == null || !sec.getBoolean("enabled", false)) return;
        String msg = sec.getString("message", "");
        if (!msg.isBlank()) {
            org.bukkit.Bukkit.broadcastMessage(
                    com.faboit.referplugin.config.ConfigManager.colorize(
                            applyPlaceholders(msg, placeholders)));
        }
    }

    // ── Title ─────────────────────────────────────────────────────────────────

    public static void sendTitle(Player player, ConfigurationSection sec, String... placeholders) {
        if (player == null || sec == null || !sec.getBoolean("enabled", false)) return;
        String title    = colorize(applyPlaceholders(sec.getString("title",    ""), placeholders));
        String subtitle = colorize(applyPlaceholders(sec.getString("subtitle", ""), placeholders));
        int fadeIn  = sec.getInt("fade-in",  10);
        int stay    = sec.getInt("stay",     60);
        int fadeOut = sec.getInt("fade-out", 10);
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    // ── Action bar ────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    public static void sendActionBar(Player player, ConfigurationSection sec, String... placeholders) {
        if (player == null || sec == null || !sec.getBoolean("enabled", false)) return;
        String msg = colorize(applyPlaceholders(sec.getString("message", ""), placeholders));
        if (!msg.isBlank()) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(msg));
        }
    }

    // ── Sound ─────────────────────────────────────────────────────────────────

    public static void playSound(Player player, ConfigurationSection sec, Logger log) {
        if (player == null || sec == null || !sec.getBoolean("enabled", false)) return;
        String soundName = sec.getString("sound", "ENTITY_PLAYER_LEVELUP");
        float  volume    = (float) sec.getDouble("volume", 1.0);
        float  pitch     = (float) sec.getDouble("pitch",  1.0);
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            if (log != null) log.warning("Unknown sound '" + soundName + "' in config.");
        }
    }

    // ── Fireworks ─────────────────────────────────────────────────────────────

    public static void spawnFireworks(Player player, ConfigurationSection sec, Logger log) {
        if (player == null || sec == null || !sec.getBoolean("enabled", false)) return;
        int count = sec.getInt("count", 1);
        int power = sec.getInt("power", 0);
        Location loc = player.getLocation();

        for (int i = 0; i < count; i++) {
            try {
                Firework fw = loc.getWorld().spawn(loc, Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();
                meta.setPower(power);
                meta.addEffect(FireworkEffect.builder()
                        .with(randomEffectType())
                        .withColor(randomColor())
                        .withFade(randomColor())
                        .trail(ThreadLocalRandom.current().nextBoolean())
                        .flicker(ThreadLocalRandom.current().nextBoolean())
                        .build());
                fw.setFireworkMeta(meta);
            } catch (Exception e) {
                if (log != null) log.warning("Error spawning firework: " + e.getMessage());
                break;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String applyPlaceholders(String text, String... pairs) {
        if (text == null) return "";
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] != null && pairs[i + 1] != null) {
                text = text.replace(pairs[i], pairs[i + 1]);
            }
        }
        return text;
    }

    private static String colorize(String s) {
        return com.faboit.referplugin.config.ConfigManager.colorize(s);
    }

    private static FireworkEffect.Type randomEffectType() {
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }

    private static Color randomColor() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return Color.fromRGB(r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }
}
