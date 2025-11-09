package com.github.cookieenum.models;

/**
 * Categories for cookie classification
 */
public enum CookieCategory {
    ESSENTIAL("Essential/Functional", "Required for basic site functionality"),
    ANALYTICS("Analytics & Tracking", "Tracks user behavior and site usage"),
    ADVERTISING("Advertising & Marketing", "Ad targeting and conversion tracking"),
    FUNCTIONAL("Functional", "Enhances user experience but not essential"),
    PERFORMANCE("Performance", "Monitors site performance and load times"),
    SOCIAL_MEDIA("Social Media", "Social media integration features"),
    SECURITY("Security", "Security and fraud prevention"),
    PERSONALIZATION("Personalization", "Customizes content based on preferences"),
    UNKNOWN("Unknown", "Category not determined");

    private final String displayName;
    private final String description;

    CookieCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static CookieCategory fromString(String category) {
        if (category == null) return UNKNOWN;

        for (CookieCategory c : values()) {
            if (c.name().equalsIgnoreCase(category) ||
                c.displayName.equalsIgnoreCase(category)) {
                return c;
            }
        }
        return UNKNOWN;
    }
}
