package com.faboit.referplugin.velocity;

/**
 * Data forwarded from the Velocity proxy for a connecting player.
 * Populated via plugin messaging channel {@code referplugin:connect}.
 */
public class VelocityData {

    private final String realIp;
    private final String hostname;

    public VelocityData(String realIp, String hostname) {
        this.realIp   = realIp;
        this.hostname = hostname;
    }

    /** The player's real IP address as seen by Velocity. */
    public String getRealIp()   { return realIp; }

    /** The virtual host the player used to connect to Velocity. */
    public String getHostname() { return hostname; }
}
