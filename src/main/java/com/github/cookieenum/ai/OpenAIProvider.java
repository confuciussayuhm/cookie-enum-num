package com.github.cookieenum.ai;

import burp.api.montoya.MontoyaApi;
import com.github.cookieenum.models.CookieCategory;
import com.github.cookieenum.models.CookieInfo;
import com.github.cookieenum.models.PrivacyImpact;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI GPT provider for cookie classification
 * Also supports OpenAI-compatible endpoints (LM Studio, LocalAI, etc.)
 */
public class OpenAIProvider implements AIProvider {
    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final int TIMEOUT_SECONDS = 30;

    private final MontoyaApi api;
    private final HttpClient httpClient;
    private String apiKey;
    private String model;
    private String apiEndpoint;
    private String providerName;

    public OpenAIProvider(MontoyaApi api) {
        this.api = api;
        // Use NO_PROXY to avoid routing through Burp's proxy (critical for local models like LM Studio)
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(java.net.ProxySelector.of(null)) // NO PROXY - direct connection
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();

        // Load configuration
        loadConfiguration();
    }

    private void loadConfiguration() {
        String key = api.persistence().preferences()
                .getString("cookiedb.openai.apiKey");
        this.apiKey = key != null ? key : "";

        String mdl = api.persistence().preferences()
                .getString("cookiedb.openai.model");
        this.model = mdl != null ? mdl : "gpt-4";

        // Load provider name from settings
        String provider = api.persistence().preferences()
                .getString("cookiedb.ai.provider");
        this.providerName = provider != null ? provider : "OpenAI";

        String endpoint = api.persistence().preferences()
                .getString("cookiedb.ai.endpoint");
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = DEFAULT_API_URL;
        } else {
            // Determine correct endpoint based on provider
            if (providerName != null && providerName.contains("Anthropic")) {
                // Anthropic uses /v1/messages
                if (!endpoint.endsWith("/messages")) {
                    if (endpoint.endsWith("/")) {
                        endpoint += "messages";
                    } else {
                        endpoint += "/messages";
                    }
                }
            } else {
                // OpenAI and compatible providers use /chat/completions
                if (!endpoint.endsWith("/chat/completions")) {
                    if (endpoint.endsWith("/")) {
                        endpoint += "chat/completions";
                    } else {
                        endpoint += "/chat/completions";
                    }
                }
            }
        }
        this.apiEndpoint = endpoint;
    }

    @Override
    public CookieInfo queryCookie(String cookieName, String domain) throws AIException {
        if (!isConfigured()) {
            throw new AIException("OpenAI provider not configured. Please set API key in settings.");
        }

        try {
            String prompt = buildPrompt(cookieName, domain);
            String jsonResponse = callOpenAI(prompt);

            return parseAIResponse(cookieName, jsonResponse);

        } catch (Exception e) {
            throw new AIException("Failed to query OpenAI: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(String cookieName, String domain) {
        return String.format(
            "Analyze this web cookie and return ONLY valid JSON with these exact fields:\n\n" +
            "Cookie Name: %s\n" +
            "Domain: %s\n\n" +
            "Return JSON with these fields:\n" +
            "{\n" +
            "  \"vendor\": \"company name\",\n" +
            "  \"category\": \"Essential|Analytics|Advertising|Functional|Performance|Social Media|Security|Personalization\",\n" +
            "  \"purpose\": \"1-2 sentence description\",\n" +
            "  \"privacyImpact\": \"Low|Medium|High|Critical\",\n" +
            "  \"isThirdParty\": true or false,\n" +
            "  \"typicalExpiration\": \"e.g., Session, 90 days, 2 years\",\n" +
            "  \"commonDomains\": [\"domain1.com\", \"domain2.com\"],\n" +
            "  \"confidence\": 0.0 to 1.0,\n" +
            "  \"notes\": \"any additional relevant information\"\n" +
            "}\n\n" +
            "Return ONLY the JSON object, no markdown formatting or explanations.",
            cookieName, domain != null ? domain : "unknown"
        );
    }

    private String callOpenAI(String prompt) throws Exception {
        String requestBody = buildRequestBody(prompt);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiEndpoint))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json, */*")
                // Don't request gzip - HttpClient with BodyHandlers.ofString() doesn't auto-decompress
                // .header("Accept-Encoding", "gzip, deflate")
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        // Add authentication headers based on provider
        if (apiKey != null && !apiKey.isEmpty()) {
            if (providerName != null && providerName.contains("Anthropic")) {
                // Anthropic uses x-api-key header and requires anthropic-version
                requestBuilder.header("x-api-key", apiKey);
                requestBuilder.header("anthropic-version", "2023-06-01");
            } else {
                // OpenAI and compatible providers use Authorization: Bearer
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
        }

        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new AIException("OpenAI API returned status " + response.statusCode() +
                    ": " + response.body());
        }

        return extractContentFromResponse(response.body());
    }

    private String buildRequestBody(String prompt) {
        if (providerName != null && providerName.contains("Anthropic")) {
            // Anthropic Messages API format
            return String.format(
                "{\n" +
                "  \"model\": \"%s\",\n" +
                "  \"system\": \"You are a web cookie classification expert. Analyze cookies and return structured JSON data.\",\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"role\": \"user\",\n" +
                "      \"content\": %s\n" +
                "    }\n" +
                "  ],\n" +
                "  \"temperature\": 0.0,\n" +
                "  \"max_tokens\": 1024\n" +
                "}",
                model,
                escapeJson(prompt)
            );
        } else {
            // OpenAI Chat Completions API format
            return String.format(
                "{\n" +
                "  \"model\": \"%s\",\n" +
                "  \"messages\": [\n" +
                "    {\n" +
                "      \"role\": \"system\",\n" +
                "      \"content\": \"You are a web cookie classification expert. Analyze cookies and return structured JSON data.\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"role\": \"user\",\n" +
                "      \"content\": %s\n" +
                "    }\n" +
                "  ],\n" +
                "  \"temperature\": 0.0,\n" +
                "  \"max_tokens\": 500\n" +
                "}",
                model,
                escapeJson(prompt)
            );
        }
    }

    private String extractContentFromResponse(String responseBody) throws AIException {
        // Extract JSON content from OpenAI response
        // Pattern to find the content field
        Pattern pattern = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(responseBody);

        if (matcher.find()) {
            String content = matcher.group(1);
            // Unescape JSON
            content = content.replace("\\n", "\n")
                           .replace("\\\"", "\"")
                           .replace("\\\\", "\\");

            // Remove markdown code blocks if present
            content = content.replaceAll("```json\\s*", "")
                           .replaceAll("```\\s*", "");

            return content.trim();
        }

        throw new AIException("Could not extract content from OpenAI response");
    }

    private CookieInfo parseAIResponse(String cookieName, String jsonResponse) throws AIException {
        try {
            CookieInfo info = new CookieInfo();
            info.setName(cookieName);
            info.setSource("ai");

            // Parse JSON manually (simple parsing for now)
            info.setVendor(extractJsonString(jsonResponse, "vendor"));
            info.setCategory(CookieCategory.fromString(extractJsonString(jsonResponse, "category")));
            info.setPurpose(extractJsonString(jsonResponse, "purpose"));
            info.setPrivacyImpact(PrivacyImpact.fromString(extractJsonString(jsonResponse, "privacyImpact")));
            info.setThirdParty(extractJsonBoolean(jsonResponse, "isThirdParty"));
            info.setTypicalExpiration(extractJsonString(jsonResponse, "typicalExpiration"));
            info.setNotes(extractJsonString(jsonResponse, "notes"));

            // Extract confidence
            String confidenceStr = extractJsonString(jsonResponse, "confidence");
            if (confidenceStr != null && !confidenceStr.isEmpty()) {
                try {
                    info.setConfidenceScore(Double.parseDouble(confidenceStr));
                } catch (NumberFormatException e) {
                    info.setConfidenceScore(0.7); // Default
                }
            }

            // Extract common domains
            String domainsStr = extractJsonArray(jsonResponse, "commonDomains");
            if (domainsStr != null && !domainsStr.isEmpty()) {
                String[] domains = domainsStr.split(",");
                for (String domain : domains) {
                    info.getCommonDomains().add(domain.trim());
                }
            }

            return info;

        } catch (Exception e) {
            throw new AIException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }

    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean extractJsonBoolean(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() && "true".equals(matcher.group(1));
    }

    private String extractJsonArray(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]+)\\]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String arrayContent = matcher.group(1);
            // Remove quotes and clean up
            return arrayContent.replaceAll("\"", "").trim();
        }
        return null;
    }

    private String escapeJson(String str) {
        if (str == null) return "null";

        return "\"" + str.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }

    @Override
    public boolean testConnection() {
        if (!isConfigured()) {
            return false;
        }

        try {
            // Simple test query
            queryCookie("_ga", "google-analytics.com");
            return true;
        } catch (AIException e) {
            api.logging().logToError("OpenAI connection test failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public boolean isConfigured() {
        // For local models (non-default endpoint), API key is not required
        boolean isLocalModel = apiEndpoint != null &&
                               !apiEndpoint.contains("api.openai.com");

        if (isLocalModel) {
            // Local model only needs valid endpoint
            return apiEndpoint != null && !apiEndpoint.isEmpty();
        } else {
            // OpenAI requires API key
            return apiKey != null && !apiKey.isEmpty();
        }
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
