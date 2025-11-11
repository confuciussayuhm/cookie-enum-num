package com.github.cookieenum.database;

import burp.api.montoya.MontoyaApi;
import com.github.cookieenum.models.CookieCategory;
import com.github.cookieenum.models.CookieInfo;
import com.github.cookieenum.models.PrivacyImpact;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches and parses the Open Cookie Database from GitHub
 * https://github.com/jkwakman/Open-Cookie-Database
 */
public class OpenCookieDatabase {
    private static final String JSON_URL =
        "https://raw.githubusercontent.com/jkwakman/Open-Cookie-Database/master/open-cookie-database.json";

    private final MontoyaApi api;
    private final HttpClient httpClient;

    public OpenCookieDatabase(MontoyaApi api) {
        this.api = api;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(java.net.ProxySelector.of(null)) // NO PROXY
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Fetch and parse the Open Cookie Database JSON
     */
    public List<CookieInfo> fetchDatabase() throws Exception {
        api.logging().logToOutput("[Open Cookie DB] Fetching from GitHub...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(JSON_URL))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Failed to fetch Open Cookie Database: HTTP " +
                response.statusCode());
        }

        String jsonContent = response.body();
        api.logging().logToOutput("[Open Cookie DB] Downloaded " +
            jsonContent.length() + " bytes");

        return parseJson(jsonContent);
    }

    /**
     * Parse the JSON object with nested arrays of cookie entries
     * Format: { "VendorName": [ {...}, {...} ], "AnotherVendor": [ {...} ] }
     */
    private List<CookieInfo> parseJson(String json) {
        List<CookieInfo> cookies = new ArrayList<>();

        try {
            json = json.trim();

            // Remove outer braces
            if (json.startsWith("{")) {
                json = json.substring(1);
            }
            if (json.endsWith("}")) {
                json = json.substring(0, json.length() - 1);
            }

            // Split by vendor sections: "VendorName": [...]
            // Pattern: finds "],\s*"NextVendor":
            String[] vendorSections = json.split("\\],\\s*\"[^\"]+\"\\s*:\\s*\\[");

            for (String section : vendorSections) {
                // Extract cookies from this vendor's array
                // Find all cookie objects: {...}
                int startPos = 0;
                while (true) {
                    int objStart = section.indexOf("{", startPos);
                    if (objStart == -1) break;

                    int objEnd = findMatchingBrace(section, objStart);
                    if (objEnd == -1) break;

                    String cookieObj = section.substring(objStart, objEnd + 1);
                    CookieInfo cookie = parseCookieObject(cookieObj);
                    if (cookie != null) {
                        cookies.add(cookie);
                    }

                    startPos = objEnd + 1;
                }
            }

            api.logging().logToOutput("[Open Cookie DB] Parsed " +
                cookies.size() + " cookie entries");

        } catch (Exception e) {
            api.logging().logToError("[Open Cookie DB] Parse error: " +
                e.getMessage());
            e.printStackTrace();
        }

        return cookies;
    }

    /**
     * Find the matching closing brace for an opening brace
     */
    private int findMatchingBrace(String str, int openPos) {
        int depth = 0;
        for (int i = openPos; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * Parse a single cookie JSON object
     */
    private CookieInfo parseCookieObject(String json) {
        try {
            String cookieName = extractJsonString(json, "cookie");
            String category = extractJsonString(json, "category");
            String description = extractJsonString(json, "description");
            String domain = extractJsonString(json, "domain");
            String dataController = extractJsonString(json, "dataController");
            String retentionPeriod = extractJsonString(json, "retentionPeriod");
            String privacyLink = extractJsonString(json, "privacyLink");
            String wildcardMatch = extractJsonString(json, "wildcardMatch");

            if (cookieName == null || cookieName.isEmpty()) {
                return null;
            }

            // Map Open Cookie DB category to our CookieCategory enum
            CookieCategory mappedCategory = mapCategory(category);

            // Create CookieInfo
            CookieInfo info = new CookieInfo(
                cookieName,
                dataController != null ? dataController : "Unknown",
                mappedCategory,
                description != null ? description : ""
            );

            // Set additional fields
            info.setTypicalExpiration(retentionPeriod != null ? retentionPeriod : "Unknown");
            info.setPrivacyImpact(determinePrivacyImpact(category, domain));
            info.setThirdParty(isThirdParty(domain));
            info.setSource("open-cookie-db");
            info.setConfidenceScore(0.9); // High confidence from curated database

            // Store wildcard match info in notes
            if ("1".equals(wildcardMatch)) {
                info.setNotes("Wildcard pattern. Privacy policy: " +
                    (privacyLink != null ? privacyLink : "N/A"));
            } else {
                info.setNotes("Privacy policy: " +
                    (privacyLink != null ? privacyLink : "N/A"));
            }

            return info;

        } catch (Exception e) {
            api.logging().logToError("[Open Cookie DB] Error parsing cookie: " +
                e.getMessage());
            return null;
        }
    }

    /**
     * Extract a string value from JSON
     */
    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Map Open Cookie DB category to our CookieCategory enum
     */
    private CookieCategory mapCategory(String openDbCategory) {
        if (openDbCategory == null) {
            return CookieCategory.UNKNOWN;
        }

        switch (openDbCategory) {
            case "Functional":
                return CookieCategory.ESSENTIAL;
            case "Analytics":
                return CookieCategory.ANALYTICS;
            case "Marketing":
                return CookieCategory.ADVERTISING;
            case "Security":
                return CookieCategory.SECURITY;
            case "Personalization":
                return CookieCategory.PERSONALIZATION;
            default:
                return CookieCategory.UNKNOWN;
        }
    }

    /**
     * Determine privacy impact based on category and domain
     */
    private PrivacyImpact determinePrivacyImpact(String category, String domain) {
        boolean isThirdParty = isThirdParty(domain);

        if (category == null) {
            return PrivacyImpact.MEDIUM;
        }

        switch (category) {
            case "Functional":
            case "Security":
                return PrivacyImpact.LOW;
            case "Personalization":
                return isThirdParty ? PrivacyImpact.MEDIUM : PrivacyImpact.LOW;
            case "Analytics":
                return isThirdParty ? PrivacyImpact.HIGH : PrivacyImpact.MEDIUM;
            case "Marketing":
                return PrivacyImpact.CRITICAL;
            default:
                return PrivacyImpact.MEDIUM;
        }
    }

    /**
     * Check if cookie is third-party based on domain field
     */
    private boolean isThirdParty(String domain) {
        if (domain == null) {
            return false;
        }
        return domain.toLowerCase().contains("3rd party") ||
               domain.toLowerCase().contains("third party") ||
               domain.toLowerCase().contains("third-party");
    }
}
