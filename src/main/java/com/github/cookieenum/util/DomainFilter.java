package com.github.cookieenum.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility for filtering domains based on user preferences
 */
public class DomainFilter {
    private final FilterMode mode;
    private final Set<String> allowedDomains;

    public enum FilterMode {
        ALL,           // Process all domains
        IN_SCOPE,      // Only process in-scope domains (Burp scope)
        CUSTOM_LIST    // Only process specific domains
    }

    public DomainFilter(FilterMode mode, Set<String> allowedDomains) {
        this.mode = mode;
        this.allowedDomains = allowedDomains != null ? new HashSet<>(allowedDomains) : new HashSet<>();
    }

    /**
     * Check if a domain should be processed based on filter settings
     * @param domain The domain to check
     * @param isInScope Whether the domain is in Burp's scope (for IN_SCOPE mode)
     * @return true if domain should be processed
     */
    public boolean shouldProcess(String domain, boolean isInScope) {
        if (domain == null || domain.isEmpty()) {
            return false;
        }

        switch (mode) {
            case ALL:
                return true;

            case IN_SCOPE:
                return isInScope;

            case CUSTOM_LIST:
                // Check exact match or subdomain match
                if (allowedDomains.contains(domain)) {
                    return true;
                }

                // Check if domain is a subdomain of any allowed domain
                for (String allowed : allowedDomains) {
                    if (domain.endsWith("." + allowed)) {
                        return true;
                    }
                    // Also check if allowed is a subdomain of domain
                    if (allowed.endsWith("." + domain)) {
                        return true;
                    }
                }
                return false;

            default:
                return false;
        }
    }

    public FilterMode getMode() {
        return mode;
    }

    public Set<String> getAllowedDomains() {
        return new HashSet<>(allowedDomains);
    }

    /**
     * Parse domains from comma-separated string
     */
    public static Set<String> parseDomainsFromString(String domainsStr) {
        Set<String> domains = new HashSet<>();
        if (domainsStr != null && !domainsStr.trim().isEmpty()) {
            String[] parts = domainsStr.split("[,;\\s]+");
            for (String part : parts) {
                String domain = part.trim();
                if (!domain.isEmpty()) {
                    domains.add(domain);
                }
            }
        }
        return domains;
    }

    @Override
    public String toString() {
        switch (mode) {
            case ALL:
                return "All domains";
            case IN_SCOPE:
                return "In-scope domains only";
            case CUSTOM_LIST:
                return "Custom list (" + allowedDomains.size() + " domains)";
            default:
                return "Unknown";
        }
    }
}
