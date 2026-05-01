package com.faboit.refervelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity companion plugin for ReferPlugin.
 *
 * <p>When a player connects to any backend server this plugin sends a plugin
 * message on channel {@code referplugin:connect} containing:
 * <pre>
 *   &lt;uuid&gt;|&lt;realIp&gt;|&lt;virtualHost&gt;
 * </pre>
 * The Paper-side ReferPlugin reads this message and uses the real IP (instead
 * of the Velocity proxy IP) for anti-abuse checks, and the virtual host (the
 * subdomain the player connected with) for referral detection.
 *
 * <p>No configuration is required – the channel name is hardcoded and matches
 * the Paper plugin automatically.
 */
@Plugin(
    id      = "refervelocity",
    name    = "ReferVelocity",
    version = "1.0.0",
    authors = {"Faboit"}
)
public class ReferVelocityPlugin {

    /** Must match {@code VelocityBridge.CHANNEL} in the Paper plugin. */
    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("referplugin:connect");

    private final ProxyServer server;
    private final Logger      logger;

    /**
     * Stores each player's virtual host string at login time so it is available
     * when {@link ServerConnectedEvent} fires (after the initial backend connection).
     */
    private final Map<UUID, String> virtualHosts = new ConcurrentHashMap<>();

    @Inject
    public ReferVelocityPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        logger.info("ReferVelocity started. Forwarding player data on channel: " + CHANNEL.getId());
    }

    /**
     * Capture the virtual host (subdomain) when the player first logs into Velocity,
     * before they are placed into a backend server.
     */
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        player.getVirtualHost().ifPresentOrElse(
                host -> virtualHosts.put(player.getUniqueId(), host.getHostString()),
                ()   -> virtualHosts.put(player.getUniqueId(), "")
        );
    }

    /**
     * Forward the player's real IP and the virtual host they connected with to
     * the backend Paper server immediately after the backend connection succeeds.
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // Real IP as seen by Velocity
        InetSocketAddress remoteAddress = player.getRemoteAddress();
        String realIp = remoteAddress.getAddress().getHostAddress();

        // Virtual host captured at login time
        String virtualHost = virtualHosts.getOrDefault(uuid, "");

        // Build message: uuid|realIp|virtualHost
        String payload = uuid + "|" + realIp + "|" + virtualHost;
        byte[] data    = payload.getBytes(StandardCharsets.UTF_8);

        event.getServer().sendPluginMessage(CHANNEL, data);

        logger.debug("Forwarded to backend [{}]: uuid={} ip={} host={}",
                event.getServer().getServerInfo().getName(), uuid, realIp, virtualHost);
    }

    /**
     * Remove cached virtual host entry when the player disconnects from Velocity.
     */
    @Subscribe
    public void onDisconnect(com.velocitypowered.api.event.connection.DisconnectEvent event) {
        virtualHosts.remove(event.getPlayer().getUniqueId());
    }
}
