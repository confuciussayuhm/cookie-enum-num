package com.github.cookieenum;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.List;

public class CookieAnalyzer {
    private final MontoyaApi api;
    private static final int SIMILARITY_THRESHOLD = 95; // Percentage similarity to consider responses "same"

    public CookieAnalyzer(MontoyaApi api) {
        this.api = api;
    }

    /**
     * Test a request with all cookies and return the result
     */
    public TestResult testRequest(HttpRequest request, ParsedHttpParameter excludeCookie, String testName) {
        try {
            HttpRequest modifiedRequest = request;

            // If we need to exclude a cookie, create modified request
            if (excludeCookie != null) {
                modifiedRequest = request.withRemovedParameters(excludeCookie);
            }

            // Send the request
            HttpRequestResponse response = api.http().sendRequest(modifiedRequest);

            if (response == null || response.response() == null) {
                return new TestResult(false, 0, 0, "No response received", false);
            }

            int statusCode = response.response().statusCode();
            int contentLength = response.response().body().length();

            return new TestResult(true, statusCode, contentLength, "Request successful", false);

        } catch (Exception e) {
            api.logging().logToError("Error testing request: " + e.getMessage());
            return new TestResult(false, 0, 0, "Error: " + e.getMessage(), false);
        }
    }

    /**
     * Test a request without a specific cookie and determine if the cookie is required
     */
    public TestResult testRequestWithoutCookie(HttpRequest request,
                                                ParsedHttpParameter cookieToRemove,
                                                TestResult baselineResult) {
        try {
            // Create request without the specified cookie
            HttpRequest modifiedRequest = request.withRemovedParameters(cookieToRemove);

            // Send the request (first test)
            HttpRequestResponse response = api.http().sendRequest(modifiedRequest);

            if (response == null || response.response() == null) {
                return new TestResult(false, 0, 0, "No response received", true, false);
            }

            int statusCode = response.response().statusCode();
            int contentLength = response.response().body().length();

            // Analyze if the cookie is required
            boolean isRequired = analyzeCookieRequirement(baselineResult, statusCode,
                    contentLength, response, baselineResult.getStatusCode());

            boolean doubleChecked = false;

            // SMART VALIDATION: If cookie appears required, validate again to avoid false positives
            // (e.g., from WAF, intermittent errors, rate limiting)
            if (isRequired) {
                api.logging().logToOutput("Cookie '" + cookieToRemove.name() +
                        "' appears required - double-checking to confirm...");

                // Wait a moment to avoid rate limiting
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                // Send the same request again (second test)
                HttpRequestResponse secondResponse = api.http().sendRequest(modifiedRequest);

                if (secondResponse != null && secondResponse.response() != null) {
                    int secondStatusCode = secondResponse.response().statusCode();
                    int secondContentLength = secondResponse.response().body().length();

                    // Check if second test agrees with first test
                    boolean secondTestRequired = analyzeCookieRequirement(baselineResult,
                            secondStatusCode, secondContentLength, secondResponse,
                            baselineResult.getStatusCode());

                    if (!secondTestRequired) {
                        // First test said required, but second test said not required
                        // This indicates a false positive (likely WAF, error, etc.)
                        api.logging().logToOutput("Double-check FAILED - cookie '" +
                                cookieToRemove.name() + "' is NOT required (false positive avoided)");
                        isRequired = false;
                    } else {
                        api.logging().logToOutput("Double-check CONFIRMED - cookie '" +
                                cookieToRemove.name() + "' is REQUIRED");
                    }

                    doubleChecked = true;
                }
            }

            String details = buildDetails(baselineResult, statusCode, contentLength, isRequired, doubleChecked);

            return new TestResult(true, statusCode, contentLength, details, isRequired, doubleChecked);

        } catch (Exception e) {
            api.logging().logToError("Error testing without cookie: " + e.getMessage());
            return new TestResult(false, 0, 0, "Error: " + e.getMessage(), true, false);
        }
    }

    /**
     * Analyze whether a cookie is required based on response comparison
     */
    private boolean analyzeCookieRequirement(TestResult baseline,
                                             int statusCode,
                                             int contentLength,
                                             HttpRequestResponse testResponse,
                                             int baselineStatusCode) {
        // If status codes differ significantly, cookie is likely required
        if (statusCode != baselineStatusCode) {
            // Common patterns indicating cookie is required:
            // - 401/403 (authentication/authorization)
            // - 302/307 (redirect, possibly to login)
            // - 500 (server error due to missing cookie)
            if (statusCode == 401 || statusCode == 403 ||
                    statusCode == 302 || statusCode == 307 ||
                    statusCode == 500) {
                return true;
            }

            // If baseline was 200 OK but now it's an error, cookie is required
            if (baselineStatusCode == 200 && (statusCode >= 400)) {
                return true;
            }
        }

        // If content length changed significantly (more than 20%), likely required
        double lengthDiffPercent = Math.abs(contentLength - baseline.getContentLength()) * 100.0 /
                Math.max(baseline.getContentLength(), 1);

        if (lengthDiffPercent > 20) {
            return true;
        }

        return false;
    }

    /**
     * Build detailed description of the test result
     */
    private String buildDetails(TestResult baseline, int statusCode,
                                 int contentLength, boolean isRequired, boolean doubleChecked) {
        StringBuilder details = new StringBuilder();

        if (statusCode != baseline.getStatusCode()) {
            details.append("Status changed: ")
                    .append(baseline.getStatusCode())
                    .append(" -> ")
                    .append(statusCode)
                    .append(". ");
        } else {
            details.append("Status unchanged (")
                    .append(statusCode)
                    .append("). ");
        }

        int lengthDiff = contentLength - baseline.getContentLength();
        double lengthDiffPercent = Math.abs(lengthDiff) * 100.0 /
                Math.max(baseline.getContentLength(), 1);

        if (Math.abs(lengthDiff) > 0) {
            details.append(String.format("Length: %+d bytes (%.1f%%). ",
                    lengthDiff, lengthDiffPercent));
        } else {
            details.append("Length unchanged. ");
        }

        if (isRequired) {
            details.append("Cookie appears REQUIRED");
            if (doubleChecked) {
                details.append(" (verified)");
            }
            details.append(".");
        } else {
            details.append("Cookie appears optional.");
        }

        return details.toString();
    }

    /**
     * Result of a cookie test
     */
    public static class TestResult {
        private final boolean success;
        private final int statusCode;
        private final int contentLength;
        private final String details;
        private final boolean required;
        private final boolean doubleChecked;

        public TestResult(boolean success, int statusCode, int contentLength,
                          String details, boolean required) {
            this(success, statusCode, contentLength, details, required, false);
        }

        public TestResult(boolean success, int statusCode, int contentLength,
                          String details, boolean required, boolean doubleChecked) {
            this.success = success;
            this.statusCode = statusCode;
            this.contentLength = contentLength;
            this.details = details;
            this.required = required;
            this.doubleChecked = doubleChecked;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public int getContentLength() {
            return contentLength;
        }

        public String getDetails() {
            return details;
        }

        public boolean isRequired() {
            return required;
        }

        public boolean isDoubleChecked() {
            return doubleChecked;
        }
    }
}
