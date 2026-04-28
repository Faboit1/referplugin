package com.faboit.referplugin.velocity;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receives plugin messages from the Velocity proxy and caches per-player data
 * (real IP + virtual host) so the {@link com.faboit.referplugin.listener.LoginListener}
 * can use the real player IP for anti-abuse checks instead of the proxy IP.
 *
 * <p>Channel: {@value CHANNEL}
 * <p>Message format: {@code <uuid>|<realIp>|<hostname>} (UTF-8)
 */
public class VelocityBridge implements PluginMessageListener {

    /** Plugin messaging channel shared with the Velocity companion plugin. */
    public static final String CHANNEL = "referplugin:connect";

    private final ConcurrentHashMap<UUID, VelocityData> cache = new ConcurrentHashMap<>();

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        String msg = new String(message, StandardCharsets.UTF_8);
        String[] parts = msg.split("\\|", 3);
        if (parts.length < 3) return;

        try {
            UUID uuid = UUID.fromString(parts[0]);
            String realIp   = parts[1];
            String hostname  = parts[2];
            cache.put(uuid, new VelocityData(realIp, hostname));
        } catch (IllegalArgumentException ignored) {
            // Malformed UUID from proxy – ignore safely
        }
    }

    /**
     * Returns and removes the cached {@link VelocityData} for the given UUID.
     * Returns {@code null} if no data has arrived yet (direct connection or Velocity not installed).
     */
    public VelocityData getAndRemove(UUID uuid) {
        return cache.remove(uuid);
    }

    /** Evicts any pending data for the given UUID (called on player quit). */
    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }
}
