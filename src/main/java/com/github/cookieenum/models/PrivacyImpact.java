package com.github.cookieenum.models;

/**
 * Privacy impact levels for cookies
 */
public enum PrivacyImpact {
    LOW("Low", "Minimal or no personal data collection"),
    MEDIUM("Medium", "Some personal data collection, limited tracking"),
    HIGH("High", "Significant tracking, cross-site data collection"),
    CRITICAL("Critical", "Sensitive data, requires explicit consent");

    private final String displayName;
    private final String description;

    PrivacyImpact(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static PrivacyImpact fromString(String impact) {
        if (impact == null) return MEDIUM;

        for (PrivacyImpact p : values()) {
            if (p.name().equalsIgnoreCase(impact) ||
                p.displayName.equalsIgnoreCase(impact)) {
                return p;
            }
        }
        return MEDIUM;
    }
}
