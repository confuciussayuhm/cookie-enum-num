package com.github.cookieenum;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Intelligent cookie analyzer that efficiently determines required cookies
 * without exhaustive combinatorial testing.
 *
 * Algorithm:
 * 1. Individual testing to identify suspicious cookies
 * 2. Smart greedy elimination to find minimal set
 * 3. Verification and alternative detection
 */
public class IntelligentCookieAnalyzer {
    private final MontoyaApi api;
    private final Consumer<String> logger;
    private final BiConsumer<String, HttpRequestResponse> resultCallback;
    private int requestCount = 0;
    private Map<String, HttpRequestResponse> storedRequestResponses;

    public IntelligentCookieAnalyzer(MontoyaApi api, Consumer<String> logger) {
        this(api, logger, null);
    }

    public IntelligentCookieAnalyzer(MontoyaApi api, Consumer<String> logger,
                                    BiConsumer<String, HttpRequestResponse> resultCallback) {
        this.api = api;
        this.logger = logger;
        this.resultCallback = resultCallback;
    }

    /**
     * Complete analysis result
     */
    public static class AnalysisResult {
        private final List<ParsedHttpParameter> requiredCookies;
        private final List<ParsedHttpParameter> optionalCookies;
        private final Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives;
        private final Map<ParsedHttpParameter, String> details;
        private final int totalRequests;
        private final BaselineResult baseline;
        private final Map<String, HttpRequestResponse> requestResponses;

        public AnalysisResult(List<ParsedHttpParameter> requiredCookies,
                            List<ParsedHttpParameter> optionalCookies,
                            Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives,
                            Map<ParsedHttpParameter, String> details,
                            int totalRequests,
                            BaselineResult baseline,
                            Map<String, HttpRequestResponse> requestResponses) {
            this.requiredCookies = requiredCookies;
            this.optionalCookies = optionalCookies;
            this.alternatives = alternatives;
            this.details = details;
            this.totalRequests = totalRequests;
            this.baseline = baseline;
            this.requestResponses = requestResponses;
        }

        public List<ParsedHttpParameter> getRequiredCookies() { return requiredCookies; }
        public List<ParsedHttpParameter> getOptionalCookies() { return optionalCookies; }
        public Map<ParsedHttpParameter, List<ParsedHttpParameter>> getAlternatives() { return alternatives; }
        public Map<ParsedHttpParameter, String> getDetails() { return details; }
        public int getTotalRequests() { return totalRequests; }
        public BaselineResult getBaseline() { return baseline; }
        public Map<String, HttpRequestResponse> getRequestResponses() { return requestResponses; }

        public boolean isRequired(ParsedHttpParameter cookie) {
            return requiredCookies.contains(cookie);
        }

        public boolean hasAlternatives(ParsedHttpParameter cookie) {
            return alternatives.containsKey(cookie) && !alternatives.get(cookie).isEmpty();
        }

        public HttpRequestResponse getRequestResponse(String cookieName) {
            return requestResponses.get(cookieName);
        }
    }

    /**
     * Baseline response for comparison
     */
    public static class BaselineResult {
        private final int statusCode;
        private final String bodyHash;
        private final int contentLength;

        public BaselineResult(int statusCode, String bodyHash, int contentLength) {
            this.statusCode = statusCode;
            this.bodyHash = bodyHash;
            this.contentLength = contentLength;
        }

        public int getStatusCode() { return statusCode; }
        public String getBodyHash() { return bodyHash; }
        public int getContentLength() { return contentLength; }
    }

    /**
     * Main analysis method - unified intelligent approach
     */
    public AnalysisResult analyze(HttpRequest originalRequest, List<ParsedHttpParameter> allCookies) {
        requestCount = 0;
        storedRequestResponses = new HashMap<>();
        log("╔════════════════════════════════════════════════════════════╗");
        log("║      INTELLIGENT COOKIE REQUIREMENT ANALYSIS              ║");
        log("╚════════════════════════════════════════════════════════════╝");
        log("");
        log("Total cookies to analyze: " + allCookies.size());
        log("");

        // PHASE 1: Establish baseline
        log("━━━ Phase 1: Establishing Baseline ━━━");
        BaselineResult baseline = establishBaseline(originalRequest, allCookies);
        if (baseline == null) {
            log("❌ CRITICAL: Baseline request failed! Cannot proceed.");
            return createFailedResult(allCookies);
        }
        log("✓ Baseline established (Status: " + baseline.getStatusCode() +
            ", Length: " + baseline.getContentLength() + " bytes)");
        log("");

        // PHASE 2: Individual cookie testing
        log("━━━ Phase 2: Individual Cookie Testing ━━━");
        log("Testing each cookie removal to identify suspicious ones...");
        Map<ParsedHttpParameter, Boolean> individualResults = testIndividualCookies(
            originalRequest, allCookies, baseline);

        List<ParsedHttpParameter> suspicious = new ArrayList<>();
        List<ParsedHttpParameter> clearlyOptional = new ArrayList<>();

        for (ParsedHttpParameter cookie : allCookies) {
            if (individualResults.get(cookie)) {
                clearlyOptional.add(cookie);
                log("  ✓ " + cookie.name() + ": Clearly optional (no deviation when removed)");
            } else {
                suspicious.add(cookie);
                log("  ⚠ " + cookie.name() + ": Suspicious (deviation detected - needs further analysis)");
            }
        }
        log("");
        log("Summary: " + suspicious.size() + " suspicious, " +
            clearlyOptional.size() + " clearly optional");
        log("");

        if (suspicious.isEmpty()) {
            log("🎉 No required cookies found! All cookies are optional.");
            return createAllOptionalResult(allCookies, requestCount, baseline);
        }

        // PHASE 3: Verify suspicious set works
        log("━━━ Phase 3: Verification Test ━━━");
        log("Testing with ONLY suspicious cookies: " + cookieNames(suspicious));
        boolean suspiciousSetWorks = testCookieSet(originalRequest, suspicious, baseline,
            "Suspicious-only test");

        List<ParsedHttpParameter> workingSet;
        if (suspiciousSetWorks) {
            log("✓ Suspicious set alone works! Proceeding to minimize...");
            workingSet = new ArrayList<>(suspicious);
        } else {
            log("✗ Suspicious set alone fails! Some 'optional' cookies are actually needed.");
            log("   This indicates complex dependencies. Testing with all cookies...");
            // Add back some optional cookies using smart search
            workingSet = findWorkingSet(originalRequest, suspicious, clearlyOptional, baseline);
        }
        log("");

        // PHASE 4: Greedy minimization
        log("━━━ Phase 4: Greedy Minimization ━━━");
        log("Removing cookies one-by-one to find minimal required set...");
        List<ParsedHttpParameter> minimal = greedyMinimize(originalRequest, workingSet, baseline);
        log("✓ Minimal set identified: " + cookieNames(minimal));
        log("");

        // PHASE 5: Double-check verification (smart validation from original request)
        log("━━━ Phase 5: Smart Verification ━━━");
        minimal = smartVerify(originalRequest, minimal, baseline);
        log("✓ Verified minimal set: " + cookieNames(minimal));
        log("");

        // PHASE 6: Alternative detection
        log("━━━ Phase 6: Alternative Cookie Detection ━━━");
        Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives =
            detectAlternatives(originalRequest, minimal, clearlyOptional, suspicious, baseline);
        log("");

        // FINAL VERIFICATION: Send one last request with ONLY the minimal set
        log("━━━ Final Confirmation ━━━");
        log("Sending final request with ONLY required cookies: " + cookieNames(minimal));
        HttpRequest minimalRequest = buildRequestWithCookies(originalRequest, minimal);
        HttpRequestResponse finalVerification = sendRequest(minimalRequest, "MINIMAL SET (final verification)");

        if (finalVerification != null && finalVerification.response() != null) {
            int finalStatus = finalVerification.response().statusCode();
            boolean finalSuccess = compareToBaseline(finalVerification, baseline);
            if (finalSuccess) {
                log("✓ Final verification successful (Status: " + finalStatus + ")");
                log("✓ Confirmed: Only these " + minimal.size() + " cookie(s) are needed");
            } else {
                log("⚠ Warning: Final verification differs from baseline");
            }
        }
        log("");

        // Build final result
        List<ParsedHttpParameter> finalOptional = new ArrayList<>(allCookies);
        finalOptional.removeAll(minimal);

        Map<ParsedHttpParameter, String> details = buildDetails(allCookies, minimal, alternatives);

        log("╔════════════════════════════════════════════════════════════╗");
        log("║                    ANALYSIS COMPLETE                       ║");
        log("╠════════════════════════════════════════════════════════════╣");
        log("║  Required cookies: " + String.format("%-40d", minimal.size()) + "║");
        log("║  Optional cookies: " + String.format("%-40d", finalOptional.size()) + "║");
        log("║  Total requests:   " + String.format("%-40d", requestCount) + "║");
        log("╚════════════════════════════════════════════════════════════╝");
        log("");

        return new AnalysisResult(minimal, finalOptional, alternatives, details,
            requestCount, baseline, storedRequestResponses);
    }

    /**
     * Phase 1: Establish baseline with all cookies
     */
    private BaselineResult establishBaseline(HttpRequest request, List<ParsedHttpParameter> cookies) {
        HttpRequestResponse response = sendRequest(request, "Baseline (all cookies)");
        if (response == null || response.response() == null) {
            return null;
        }

        int statusCode = response.response().statusCode();
        byte[] body = response.response().body().getBytes();
        int contentLength = body.length;
        String bodyHash = computeHash(body);

        return new BaselineResult(statusCode, bodyHash, contentLength);
    }

    /**
     * Phase 2: Test each cookie individually
     */
    private Map<ParsedHttpParameter, Boolean> testIndividualCookies(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> allCookies,
            BaselineResult baseline) {

        Map<ParsedHttpParameter, Boolean> results = new HashMap<>();

        for (ParsedHttpParameter cookie : allCookies) {
            // Create request without this cookie
            HttpRequest modified = removeOneCookie(originalRequest, cookie);
            HttpRequestResponse response = sendRequest(modified, "Without: " + cookie.name());

            if (response == null || response.response() == null) {
                results.put(cookie, false);
                continue;
            }

            boolean matches = compareToBaseline(response, baseline);
            results.put(cookie, matches);
        }

        return results;
    }

    /**
     * Phase 3b: Find working set when suspicious alone doesn't work
     */
    private List<ParsedHttpParameter> findWorkingSet(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> suspicious,
            List<ParsedHttpParameter> optional,
            BaselineResult baseline) {

        log("  → Attempting binary search to find working combination...");

        // Try adding optional cookies in groups
        List<ParsedHttpParameter> current = new ArrayList<>(suspicious);

        // Binary search approach
        if (!optional.isEmpty()) {
            int low = 0;
            int high = optional.size();

            while (low < high) {
                int mid = (low + high) / 2;
                List<ParsedHttpParameter> testSet = new ArrayList<>(suspicious);
                testSet.addAll(optional.subList(0, mid + 1));

                boolean works = testCookieSet(originalRequest, testSet, baseline,
                    "Binary search test");

                if (works) {
                    high = mid;
                    current = testSet;
                } else {
                    low = mid + 1;
                }
            }
        }

        // If still doesn't work, fall back to all cookies
        if (!testCookieSet(originalRequest, current, baseline, "Working set verification")) {
            log("  ⚠ Binary search failed, using all cookies as working set");
            current = new ArrayList<>();
            current.addAll(suspicious);
            current.addAll(optional);
        }

        return current;
    }

    /**
     * Phase 4: Greedy minimization - remove cookies one by one
     */
    private List<ParsedHttpParameter> greedyMinimize(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> workingSet,
            BaselineResult baseline) {

        List<ParsedHttpParameter> minimal = new ArrayList<>(workingSet);

        // Try removing each cookie
        for (ParsedHttpParameter cookie : workingSet) {
            List<ParsedHttpParameter> testSet = new ArrayList<>(minimal);
            testSet.remove(cookie);

            if (testSet.isEmpty()) {
                log("  → " + cookie.name() + " is the last cookie, must be required");
                continue;
            }

            boolean worksWithout = testCookieSet(originalRequest, testSet, baseline,
                "Without: " + cookie.name());

            if (worksWithout) {
                log("  ✓ Removed " + cookie.name() + " (not required)");
                minimal = testSet;
            } else {
                log("  ✗ Kept " + cookie.name() + " (required)");
            }
        }

        return minimal;
    }

    /**
     * Phase 5: Smart verification with double-check
     */
    private List<ParsedHttpParameter> smartVerify(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> candidates,
            BaselineResult baseline) {

        // Verify minimal set works
        boolean works = testCookieSet(originalRequest, candidates, baseline,
            "Final verification");

        if (!works) {
            log("  ⚠ WARNING: Minimal set verification failed! Possible false positive from WAF/rate limiting");
            log("  → Re-testing minimal set...");

            // Wait a moment and retry
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            works = testCookieSet(originalRequest, candidates, baseline, "Retry verification");
            if (!works) {
                log("  ❌ Verification failed again. Results may be unreliable.");
            } else {
                log("  ✓ Verification succeeded on retry");
            }
        } else {
            log("  ✓ Minimal set verified successfully");
        }

        return candidates;
    }

    /**
     * Phase 6: Detect alternative cookies (OR relationships)
     */
    private Map<ParsedHttpParameter, List<ParsedHttpParameter>> detectAlternatives(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> required,
            List<ParsedHttpParameter> optional,
            List<ParsedHttpParameter> suspicious,
            BaselineResult baseline) {

        Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives = new HashMap<>();

        // Check if any optional cookies can substitute for required ones
        for (ParsedHttpParameter req : required) {
            List<ParsedHttpParameter> alts = new ArrayList<>();

            // Only test suspicious cookies that aren't in required set
            List<ParsedHttpParameter> candidates = new ArrayList<>(suspicious);
            candidates.removeAll(required);

            for (ParsedHttpParameter candidate : candidates) {
                // Try replacing required with candidate
                List<ParsedHttpParameter> testSet = new ArrayList<>(required);
                testSet.remove(req);
                testSet.add(candidate);

                boolean works = testCookieSet(originalRequest, testSet, baseline,
                    "Replace " + req.name() + " with " + candidate.name());

                if (works) {
                    log("  ✓ " + candidate.name() + " can substitute for " + req.name() + " (OR relationship)");
                    alts.add(candidate);
                }
            }

            if (!alts.isEmpty()) {
                alternatives.put(req, alts);
            }
        }

        if (alternatives.isEmpty()) {
            log("  No alternative cookies detected");
        }

        return alternatives;
    }

    /**
     * Test a specific set of cookies
     */
    private boolean testCookieSet(HttpRequest originalRequest,
                                  List<ParsedHttpParameter> cookiesToInclude,
                                  BaselineResult baseline,
                                  String testName) {
        HttpRequest modified = buildRequestWithCookies(originalRequest, cookiesToInclude);
        HttpRequestResponse response = sendRequest(modified, testName);

        if (response == null || response.response() == null) {
            return false;
        }

        return compareToBaseline(response, baseline);
    }

    /**
     * Compare response to baseline
     */
    private boolean compareToBaseline(HttpRequestResponse response, BaselineResult baseline) {
        if (response.response().statusCode() != baseline.getStatusCode()) {
            return false;
        }

        byte[] body = response.response().body().getBytes();
        String bodyHash = computeHash(body);

        // Exact body match
        if (bodyHash.equals(baseline.getBodyHash())) {
            return true;
        }

        // Allow small variation (up to 5% for dynamic content)
        double lengthDiff = Math.abs(body.length - baseline.getContentLength()) * 100.0
            / Math.max(baseline.getContentLength(), 1);

        return lengthDiff <= 5;
    }

    /**
     * Build request with specific cookies only
     */
    private HttpRequest buildRequestWithCookies(HttpRequest originalRequest,
                                                List<ParsedHttpParameter> cookies) {
        HttpRequest modified = originalRequest.withRemovedParameters(
            originalRequest.parameters(burp.api.montoya.http.message.params.HttpParameterType.COOKIE));

        for (ParsedHttpParameter cookie : cookies) {
            modified = modified.withAddedParameters(cookie);
        }

        return modified;
    }

    /**
     * Remove one specific cookie
     */
    private HttpRequest removeOneCookie(HttpRequest request, ParsedHttpParameter cookieToRemove) {
        return request.withRemovedParameters(cookieToRemove);
    }

    /**
     * Send HTTP request
     */
    private HttpRequestResponse sendRequest(HttpRequest request, String testName) {
        requestCount++;
        try {
            HttpRequestResponse response = api.http().sendRequest(request);

            // Store the request/response for later retrieval
            if (response != null) {
                storedRequestResponses.put(testName, response);

                // Also call the callback if provided
                if (resultCallback != null) {
                    resultCallback.accept(testName, response);
                }
            }

            return response;
        } catch (Exception e) {
            log("  ❌ Error sending request [" + testName + "]: " + e.getMessage());
            return null;
        }
    }

    /**
     * Compute hash of response body
     */
    private String computeHash(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(data.length);
        }
    }

    /**
     * Build details for each cookie
     */
    private Map<ParsedHttpParameter, String> buildDetails(
            List<ParsedHttpParameter> allCookies,
            List<ParsedHttpParameter> required,
            Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives) {

        Map<ParsedHttpParameter, String> details = new HashMap<>();

        for (ParsedHttpParameter cookie : allCookies) {
            if (required.contains(cookie)) {
                if (alternatives.containsKey(cookie)) {
                    List<ParsedHttpParameter> alts = alternatives.get(cookie);
                    String altNames = cookieNames(alts);
                    details.put(cookie, "Required (OR: " + altNames + ") [View: test WITHOUT this cookie]");
                } else {
                    details.put(cookie, "Required - removal causes failure [View: test WITHOUT this cookie]");
                }
            } else {
                // Check if it's an alternative to something
                boolean isAlternative = false;
                for (List<ParsedHttpParameter> alts : alternatives.values()) {
                    if (alts.contains(cookie)) {
                        isAlternative = true;
                        break;
                    }
                }

                if (isAlternative) {
                    details.put(cookie, "Alternative - can substitute for another [View: test WITHOUT this cookie]");
                } else {
                    details.put(cookie, "Optional - removal has no effect [View: test WITHOUT this cookie]");
                }
            }
        }

        return details;
    }

    /**
     * Helper: Get comma-separated cookie names
     */
    private String cookieNames(List<ParsedHttpParameter> cookies) {
        if (cookies.isEmpty()) return "none";
        return cookies.stream()
            .map(ParsedHttpParameter::name)
            .reduce((a, b) -> a + ", " + b)
            .orElse("none");
    }

    /**
     * Create result for failed baseline
     */
    private AnalysisResult createFailedResult(List<ParsedHttpParameter> allCookies) {
        Map<ParsedHttpParameter, String> details = new HashMap<>();
        for (ParsedHttpParameter cookie : allCookies) {
            details.put(cookie, "Unknown - baseline failed");
        }
        return new AnalysisResult(
            new ArrayList<>(),
            new ArrayList<>(allCookies),
            new HashMap<>(),
            details,
            requestCount,
            null,
            storedRequestResponses
        );
    }

    /**
     * Create result when all cookies are optional
     */
    private AnalysisResult createAllOptionalResult(List<ParsedHttpParameter> allCookies,
                                                   int requests,
                                                   BaselineResult baseline) {
        Map<ParsedHttpParameter, String> details = new HashMap<>();
        for (ParsedHttpParameter cookie : allCookies) {
            details.put(cookie, "Optional - no deviation detected");
        }
        return new AnalysisResult(
            new ArrayList<>(),
            new ArrayList<>(allCookies),
            new HashMap<>(),
            details,
            requests,
            baseline,
            storedRequestResponses
        );
    }

    /**
     * Log message
     */
    private void log(String message) {
        api.logging().logToOutput("[ANALYZER] " + message);
        if (logger != null) {
            logger.accept(message);
        }
    }
}
