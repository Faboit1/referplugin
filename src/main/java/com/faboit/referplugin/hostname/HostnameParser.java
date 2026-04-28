package com.faboit.referplugin.hostname;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    /**
     * Result of parsing a raw hostname.
     *
     * <ul>
     *   <li>{@link #referrerName()} is non-null when a valid (non-blocked) subdomain was found.</li>
     *   <li>{@link #blocked()} is {@code true} when the hostname matched a configured
     *       {@code hostname.blocked-subdomains} pattern and should be treated as a direct join.</li>
     * </ul>
     */
    public record ParseResult(String referrerName, boolean blocked) {
        public static ParseResult noMatch()            { return new ParseResult(null, false); }
        public static ParseResult blocked()            { return new ParseResult(null, true);  }
        public static ParseResult referrer(String name){ return new ParseResult(name, false); }
    }

    private final String              baseDomain;
    private final List<String>        allDomains              = new ArrayList<>();
    private final Map<String, String> manualOverrides         = new HashMap<>();
    private final boolean             stripWww;
    private final int                 minLength;
    private final int                 maxLength;
    private final Pattern             subdomainPattern;        // may be null if regex is empty
    /** Compiled glob patterns for subdomains that should be silently ignored. */
    private final List<Pattern>       blockedSubdomainPatterns = new ArrayList<>();

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
            if (!g.isBlank()) blockedSubdomainPatterns.add(globToPattern(g));
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attempt to extract a referrer username from the given hostname string.
     *
     * @param hostname The raw hostname from {@code PlayerLoginEvent#getHostname()}.
     *                 May include a trailing port (e.g. {@code faboit.cheesesmp.top:25565}).
     * @return The inferred referrer username, or {@code null} if no valid subdomain detected.
     */
    public String parse(String hostname) {
        return parseResult(hostname).referrerName();
    }

    /**
     * Like {@link #parse} but returns a {@link ParseResult} that also signals whether the
     * hostname was actively blocked by a {@code hostname.blocked-subdomains} pattern.
     */
    public ParseResult parseResult(String hostname) {
        if (hostname == null || hostname.isBlank()) return ParseResult.noMatch();

        // Strip port if present
        String host = hostname.toLowerCase().trim();
        int colonIdx = host.lastIndexOf(':');
        if (colonIdx > 0) host = host.substring(0, colonIdx);

        // Strip leading www. if configured
        if (stripWww && host.startsWith("www.")) host = host.substring(4);

        // Check full-hostname manual overrides first
        if (manualOverrides.containsKey(host)) return ParseResult.referrer(manualOverrides.get(host));

        // Try each configured base domain
        for (String domain : allDomains) {
            String suffix = "." + domain;
            if (host.endsWith(suffix)) {
                String subdomain = host.substring(0, host.length() - suffix.length());
                if (subdomain.isBlank() || subdomain.contains(".")) continue; // nested subdomain – skip

                // Subdomains matching a blocked pattern are silently ignored (treated as direct join)
                if (isBlockedSubdomain(subdomain)) return ParseResult.blocked();

                // Check manual override for just the subdomain part
                if (manualOverrides.containsKey(subdomain))
                    return ParseResult.referrer(manualOverrides.get(subdomain));

                // Validate length
                if (subdomain.length() < minLength || subdomain.length() > maxLength)
                    return ParseResult.noMatch();

                // Validate against regex if configured
                if (subdomainPattern != null && !subdomainPattern.matcher(subdomain).matches())
                    return ParseResult.noMatch();

                return ParseResult.referrer(subdomain);
            }
            // Player joined on the bare base domain – no referral
            if (host.equals(domain)) return ParseResult.noMatch();
        }

        return ParseResult.noMatch();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
                case '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\', '-' ->
                    sb.append('\\').append(c);
                default  -> sb.append(c);
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    public String getBaseDomain() { return baseDomain; }
    public List<String> getAllDomains() { return List.copyOf(allDomains); }
}
