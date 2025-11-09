package com.github.cookieenum.service;

import burp.api.montoya.MontoyaApi;
import com.github.cookieenum.ai.AIException;
import com.github.cookieenum.ai.AIProvider;
import com.github.cookieenum.database.CookieDatabaseManager;
import com.github.cookieenum.models.CookieInfo;

import java.util.Optional;

/**
 * Service layer that coordinates database and AI provider
 */
public class CookieInfoService {
    private final MontoyaApi api;
    private final CookieDatabaseManager dbManager;
    private final AIProvider aiProvider;

    public CookieInfoService(MontoyaApi api,
                            CookieDatabaseManager dbManager,
                            AIProvider aiProvider) {
        this.api = api;
        this.dbManager = dbManager;
        this.aiProvider = aiProvider;
    }

    /**
     * Get cookie info - cache first, then AI if needed
     */
    public CookieInfo getCookieInfo(String name, String domain) {
        // 1. Check database cache
        Optional<CookieInfo> cached = getCookieInfoFromCache(name, domain);
        if (cached.isPresent()) {
            api.logging().logToOutput(String.format(
                    "[CACHE] Found %s in database", name));
            return cached.get();
        }

        // 2. Query AI
        try {
            CookieInfo fromAI = queryCookieFromAI(name, domain);
            return fromAI;

        } catch (AIException e) {
            api.logging().logToError("AI query failed for " + name + ": " + e.getMessage());

            // Return empty CookieInfo as fallback
            return createUnknownCookieInfo(name);
        }
    }

    /**
     * Get cookie info from cache only (no AI query)
     */
    public Optional<CookieInfo> getCookieInfoFromCache(String name, String domain) {
        return dbManager.getCookieInfo(name, domain);
    }

    /**
     * Query AI for cookie information and store in database
     */
    public CookieInfo queryCookieFromAI(String name, String domain) throws AIException {
        // Check if AI provider is configured
        if (!aiProvider.isConfigured()) {
            throw new AIException("AI provider not configured. Please set API key in settings.");
        }

        api.logging().logToOutput(String.format(
                "[AI] Querying %s for %s (domain: %s)",
                aiProvider.getProviderName(), name, domain));

        // Query AI
        CookieInfo info = aiProvider.queryCookie(name, domain);

        // Store in database
        try {
            dbManager.storeCookieInfo(info);
            api.logging().logToOutput(String.format(
                    "[DB] Stored %s: %s - %s",
                    info.getName(), info.getVendor(), info.getCategory()));
        } catch (Exception e) {
            api.logging().logToError("Failed to store in database: " + e.getMessage());
        }

        return info;
    }

    /**
     * Manually store cookie info (user correction)
     */
    public void storeCookieInfo(CookieInfo info) {
        info.setSource("manual");
        dbManager.storeCookieInfo(info);
    }

    /**
     * Add a pattern for automatic matching
     */
    public void addPattern(String cookieName, String pattern) {
        dbManager.addCookiePattern(cookieName, pattern);
    }

    /**
     * Create an unknown cookie info object
     */
    private CookieInfo createUnknownCookieInfo(String name) {
        CookieInfo info = new CookieInfo();
        info.setName(name);
        info.setVendor("Unknown");
        info.setCategory(com.github.cookieenum.models.CookieCategory.UNKNOWN);
        info.setPurpose("Information not available");
        info.setSource("unknown");
        info.setConfidenceScore(0.0);
        return info;
    }

    /**
     * Test AI provider connection
     */
    public boolean testAIConnection() {
        if (!aiProvider.isConfigured()) {
            return false;
        }

        try {
            return aiProvider.testConnection();
        } catch (Exception e) {
            api.logging().logToError("AI connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get database statistics
     */
    public java.util.Map<String, Object> getDatabaseStats() {
        return dbManager.getStatistics();
    }

    /**
     * Get database path
     */
    public String getDatabasePath() {
        return dbManager.getDatabasePathString();
    }

    /**
     * Get all cookies from database
     */
    public java.util.List<CookieInfo> getAllCookies() {
        return dbManager.getAllCookies();
    }

    /**
     * Update an existing cookie in the database
     */
    public void updateCookie(CookieInfo updatedCookie) {
        dbManager.updateCookieInfo(updatedCookie);
        api.logging().logToOutput(String.format(
                "[DB] Updated cookie: %s", updatedCookie.getName()));
    }

    /**
     * Delete a cookie from the database
     */
    public void deleteCookie(String cookieName) {
        dbManager.deleteCookie(cookieName);
        api.logging().logToOutput(String.format(
                "[DB] Deleted cookie: %s", cookieName));
    }
}
