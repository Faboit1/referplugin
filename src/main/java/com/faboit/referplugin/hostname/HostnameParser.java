package com.faboit.referplugin.hostname;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts a referrer username from the virtual hostname the player connected with.
 *
 * <p>Example: {@code faboit.cheesesmp.top} → {@code faboit}
 *
 * <p>Velocity / BungeeCord forward the original hostname in the login packet so
 * Paper's {@code PlayerLoginEvent#getHostname()} reflects the subdomain.
 *
 * <p>All options are driven by {@code hostname.*} in config.yml.
 */
public class HostnameParser {

    private final String            baseDomain;
    private final List<String>      allDomains       = new ArrayList<>();
    private final Map<String, String> manualOverrides = new HashMap<>();
    private final boolean           stripWww;
    private final int               minLength;
    private final int               maxLength;
    private final Pattern           subdomainPattern; // may be null if regex is empty
    /** Compiled glob patterns for subdomains that should be silently ignored. */
    private final List<Pattern>     blockedSubdomainPatterns = new ArrayList<>();

    public HostnameParser(FileConfiguration config) {
        this.baseDomain = config.getString("hostname.base-domain", "").toLowerCase().trim();
        this.stripWww   = config.getBoolean("hostname.strip-www", true);
        this.minLength  = config.getInt("hostname.min-subdomain-length", 3);
        this.maxLength  = config.getInt("hostname.max-subdomain-length", 16);

        // Compile subdomain regex
        String regex = config.getString("hostname.subdomain-regex", "").trim();
        this.subdomainPattern = regex.isBlank() ? null : Pattern.compile(regex);

        // Build full list of base domains to check
        if (!baseDomain.isBlank()) allDomains.add(baseDomain);
        for (String extra : config.getStringList("hostname.extra-domains")) {
            String d = extra.toLowerCase().trim();
            if (!d.isBlank()) allDomains.add(d);
        }

        // Load manual overrides: <subdomain> → <username>
        if (config.isConfigurationSection("hostname.manual-overrides")) {
            for (String key : config.getConfigurationSection("hostname.manual-overrides")
                    .getKeys(false)) {
                String value = config.getString("hostname.manual-overrides." + key, "");
                if (!value.isBlank()) manualOverrides.put(key.toLowerCase(), value);
            }
        }

        // Compile blocked-subdomain glob patterns (*, ? wildcards)
        for (String glob : config.getStringList("hostname.blocked-subdomains")) {
            String g = glob.toLowerCase().trim();
            if (!g.isBlank()) {
                blockedSubdomainPatterns.add(globToPattern(g));
            }
        }
    }

    /**
     * Converts a simple glob pattern (using {@code *} and {@code ?} wildcards)
     * into a compiled {@link Pattern} for case-insensitive matching.
     */
    private static Pattern globToPattern(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' ->
                    sb.append('\\').append(c);
                default  -> sb.append(c);
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Returns {@code true} if the given subdomain matches any configured blocked pattern.
     */
    private boolean isBlockedSubdomain(String subdomain) {
        for (Pattern p : blockedSubdomainPatterns) {
            if (p.matcher(subdomain).matches()) return true;
        }
        return false;
    }

    /**
     * Attempt to extract a referrer username from the given hostname string.
     *
     * @param hostname The raw hostname from {@code PlayerLoginEvent#getHostname()}.
     *                 May include a trailing port (e.g. {@code faboit.cheesesmp.top:25565}).
     * @return The inferred referrer username, or {@code null} if no valid subdomain detected.
     */
    public String parse(String hostname) {
        if (hostname == null || hostname.isBlank()) return null;

        // Strip port if present
        String host = hostname.toLowerCase().trim();
        int colonIdx = host.lastIndexOf(':');
        if (colonIdx > 0) host = host.substring(0, colonIdx);

        // Strip leading www. if configured
        if (stripWww && host.startsWith("www.")) host = host.substring(4);

        // Check full-hostname manual overrides first
        if (manualOverrides.containsKey(host)) return manualOverrides.get(host);

        // Try each configured base domain
        for (String domain : allDomains) {
            String suffix = "." + domain;
            if (host.endsWith(suffix)) {
                String subdomain = host.substring(0, host.length() - suffix.length());
                if (subdomain.isBlank() || subdomain.contains(".")) continue; // nested subdomain – skip

                // Silently ignore subdomains that match a blocked pattern
                if (isBlockedSubdomain(subdomain)) return null;

                // Check manual override for just the subdomain part
                if (manualOverrides.containsKey(subdomain)) return manualOverrides.get(subdomain);

                // Validate length
                if (subdomain.length() < minLength || subdomain.length() > maxLength) return null;

                // Validate against regex if configured
                if (subdomainPattern != null && !subdomainPattern.matcher(subdomain).matches()) return null;

                return subdomain; // Use subdomain directly as username (case-insensitive lookup happens upstream)
            }
            // Player joined on the bare base domain – no referral
            if (host.equals(domain)) return null;
        }

        return null;
    }

    public String getBaseDomain() { return baseDomain; }
    public List<String> getAllDomains() { return List.copyOf(allDomains); }
}
