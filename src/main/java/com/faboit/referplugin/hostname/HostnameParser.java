package com.faboit.referplugin.hostname;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts a referrer username from the virtual hostname the player connected with.
 *
 * <p>Example: {@code faboit.cheesesmp.top} → {@code faboit}
 *
 * <p>Velocity / BungeeCord forward the original hostname in the login packet so
 * Paper's {@code PlayerLoginEvent#getHostname()} reflects the subdomain.
 */
public class HostnameParser {

    private final String baseDomain;
    private final Map<String, String> manualOverrides = new HashMap<>();

    public HostnameParser(FileConfiguration config) {
        this.baseDomain = config.getString("hostname.base-domain", "").toLowerCase().trim();

        // Load manual overrides: <subdomain> → <username>
        if (config.isConfigurationSection("hostname.manual-overrides")) {
            for (String key : config.getConfigurationSection("hostname.manual-overrides").getKeys(false)) {
                String value = config.getString("hostname.manual-overrides." + key, "");
                if (!value.isBlank()) {
                    manualOverrides.put(key.toLowerCase(), value);
                }
            }
        }
    }

    /**
     * Attempt to extract a referrer username from the given hostname string.
     *
     * @param hostname The raw hostname from {@code PlayerLoginEvent#getHostname()}.
     *                 May include a trailing port (e.g. {@code faboit.cheesesmp.top:25565}).
     * @return The inferred referrer username, or {@code null} if no subdomain detected.
     */
    public String parse(String hostname) {
        if (hostname == null || hostname.isBlank()) return null;

        // Strip port if present
        String host = hostname.toLowerCase().trim();
        int colonIdx = host.lastIndexOf(':');
        if (colonIdx > 0) host = host.substring(0, colonIdx);

        // Check manual overrides first
        if (manualOverrides.containsKey(host)) {
            return manualOverrides.get(host);
        }

        // Extract subdomain relative to the configured base domain
        if (!baseDomain.isBlank()) {
            String suffix = "." + baseDomain;
            if (host.endsWith(suffix)) {
                String subdomain = host.substring(0, host.length() - suffix.length());
                if (!subdomain.isBlank() && !subdomain.contains(".")) {
                    // Check manual override for just the subdomain part
                    if (manualOverrides.containsKey(subdomain)) {
                        return manualOverrides.get(subdomain);
                    }
                    return subdomain;
                }
            }
            // Player joined on the bare base domain – no referral
            if (host.equals(baseDomain)) return null;
        }

        return null;
    }

    public String getBaseDomain() { return baseDomain; }
}
