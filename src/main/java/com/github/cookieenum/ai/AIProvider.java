package com.github.cookieenum.ai;

import com.github.cookieenum.models.CookieInfo;

/**
 * Interface for AI providers that can classify cookies
 */
public interface AIProvider {
    /**
     * Query the AI to get information about a cookie
     *
     * @param cookieName Name of the cookie
     * @param domain Domain where the cookie was found
     * @return CookieInfo with AI-provided classification
     * @throws AIException if the query fails
     */
    CookieInfo queryCookie(String cookieName, String domain) throws AIException;

    /**
     * Test if the provider is configured and accessible
     *
     * @return true if connection test succeeds
     */
    boolean testConnection();

    /**
     * Get the name of this provider
     *
     * @return Provider name (e.g., "OpenAI", "Claude")
     */
    String getProviderName();

    /**
     * Check if this provider is properly configured
     *
     * @return true if API key and other settings are configured
     */
    boolean isConfigured();
}
