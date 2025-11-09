package com.github.cookieenum;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.*;
import java.util.function.Consumer;

/**
 * Advanced cookie analyzer that discovers the minimal set of required cookies
 * and handles complex dependencies like OR relationships and threshold requirements.
 */
public class MinimalSetAnalyzer {
    private final MontoyaApi api;
    private final Consumer<String> logger;
    private int requestCount = 0;

    public MinimalSetAnalyzer(MontoyaApi api, Consumer<String> logger) {
        this.api = api;
        this.logger = logger;
    }

    /**
     * Result of minimal set analysis
     */
    public static class AnalysisResult {
        private final List<ParsedHttpParameter> minimalSet;
        private final Map<ParsedHttpParameter, CookieStatus> cookieStatuses;
        private final Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives;
        private final int totalRequests;

        public AnalysisResult(List<ParsedHttpParameter> minimalSet,
                            Map<ParsedHttpParameter, CookieStatus> cookieStatuses,
                            Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives,
                            int totalRequests) {
            this.minimalSet = minimalSet;
            this.cookieStatuses = cookieStatuses;
            this.alternatives = alternatives;
            this.totalRequests = totalRequests;
        }

        public List<ParsedHttpParameter> getMinimalSet() { return minimalSet; }
        public Map<ParsedHttpParameter, CookieStatus> getCookieStatuses() { return cookieStatuses; }
        public Map<ParsedHttpParameter, List<ParsedHttpParameter>> getAlternatives() { return alternatives; }
        public int getTotalRequests() { return totalRequests; }

        public boolean isRequired(ParsedHttpParameter cookie) {
            return minimalSet.contains(cookie);
        }

        public boolean hasAlternatives(ParsedHttpParameter cookie) {
            return alternatives.containsKey(cookie) && !alternatives.get(cookie).isEmpty();
        }
    }

    public enum CookieStatus {
        REQUIRED,           // Must be present
        OPTIONAL,           // Not needed at all
        ALTERNATIVE,        // Can substitute for another cookie
        UNKNOWN            // Status unclear
    }

    /**
     * Perform deep analysis to find minimal cookie set
     */
    public AnalysisResult analyzeMinimalSet(HttpRequest originalRequest,
                                           List<ParsedHttpParameter> allCookies) {
        requestCount = 0;
        log("\n╔═══════════════════════════════════════════════════════╗");
        log("║        DEEP ANALYSIS: Minimal Set Discovery        ║");
        log("╚═══════════════════════════════════════════════════════╝\n");

        // Phase 1: Establish baseline
        log("Phase 1: Establishing Baseline");
        log("─────────────────────────────────────");
        CookieAnalyzer.TestResult baseline = testRequest(originalRequest, allCookies, "BASELINE");
        if (!baseline.isSuccess()) {
            log("❌ Baseline request failed! Cannot proceed with analysis.");
            return createEmptyResult(allCookies);
        }
        log("✓ Baseline successful (Status: " + baseline.getStatusCode() + ")\n");

        // Phase 2: Individual testing
        log("Phase 2: Individual Cookie Testing");
        log("─────────────────────────────────────");
        Map<ParsedHttpParameter, Boolean> individualResults = testIndividualCookies(
                originalRequest, allCookies, baseline);

        List<ParsedHttpParameter> suspicious = new ArrayList<>();
        List<ParsedHttpParameter> possiblyOptional = new ArrayList<>();

        for (ParsedHttpParameter cookie : allCookies) {
            if (individualResults.get(cookie)) {
                possiblyOptional.add(cookie);
                log("  → " + cookie.name() + ": Possibly optional");
            } else {
                suspicious.add(cookie);
                log("  → " + cookie.name() + ": Suspicious (failure when removed)");
            }
        }
        log("");

        // Phase 3: Test minimal set hypothesis
        log("Phase 3: Testing Minimal Set Hypothesis");
        log("─────────────────────────────────────");
        log("Testing with only 'suspicious' cookies: " +
            suspicious.stream().map(ParsedHttpParameter::name)
                .reduce((a, b) -> a + ", " + b).orElse("none"));

        CookieAnalyzer.TestResult minimalTest = testRequest(originalRequest, suspicious, "Minimal Set");
        List<ParsedHttpParameter> workingSet = new ArrayList<>(suspicious);

        if (minimalTest.isSuccess()) {
            log("✓ Minimal set works! These cookies are sufficient.\n");
        } else {
            log("✗ Minimal set failed. Some 'possibly optional' cookies are needed.");

            // Phase 4: Find missing required cookies
            log("\nPhase 4: Finding Missing Required Cookies");
            log("─────────────────────────────────────");
            workingSet = findMissingCookies(originalRequest, suspicious, possiblyOptional, baseline);
        }

        // Phase 5: Verify minimality
        log("\nPhase 5: Verifying Minimality");
        log("─────────────────────────────────────");
        List<ParsedHttpParameter> trueMinimal = verifyMinimality(originalRequest, workingSet, baseline);
        log("✓ True minimal set: " +
            trueMinimal.stream().map(ParsedHttpParameter::name)
                .reduce((a, b) -> a + ", " + b).orElse("none"));

        // Phase 6: Detect alternatives (OR relationships)
        log("\nPhase 6: Detecting Alternative Cookies (OR relationships)");
        log("─────────────────────────────────────");
        Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives =
            detectAlternatives(originalRequest, trueMinimal, possiblyOptional, baseline);

        // Build final result
        Map<ParsedHttpParameter, CookieStatus> statuses = new HashMap<>();
        for (ParsedHttpParameter cookie : allCookies) {
            if (trueMinimal.contains(cookie)) {
                statuses.put(cookie, CookieStatus.REQUIRED);
            } else if (isAlternative(cookie, alternatives)) {
                statuses.put(cookie, CookieStatus.ALTERNATIVE);
            } else {
                statuses.put(cookie, CookieStatus.OPTIONAL);
            }
        }

        log("\n╔═══════════════════════════════════════════════════════╗");
        log("║              Analysis Complete!                     ║");
        log("║  Total requests sent: " + String.format("%-31d", requestCount) + "║");
        log("╚═══════════════════════════════════════════════════════╝\n");

        return new AnalysisResult(trueMinimal, statuses, alternatives, requestCount);
    }

    private Map<ParsedHttpParameter, Boolean> testIndividualCookies(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> allCookies,
            CookieAnalyzer.TestResult baseline) {

        Map<ParsedHttpParameter, Boolean> results = new HashMap<>();

        for (ParsedHttpParameter cookie : allCookies) {
            List<ParsedHttpParameter> without = new ArrayList<>(allCookies);
            without.remove(cookie);

            CookieAnalyzer.TestResult result = testRequest(originalRequest, without,
                "Without " + cookie.name());

            boolean works = compareResults(baseline, result);
            results.put(cookie, works);
        }

        return results;
    }

    private List<ParsedHttpParameter> findMissingCookies(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> currentSet,
            List<ParsedHttpParameter> candidates,
            CookieAnalyzer.TestResult baseline) {

        List<ParsedHttpParameter> working = new ArrayList<>(currentSet);

        // Try adding candidates one by one
        for (ParsedHttpParameter candidate : candidates) {
            List<ParsedHttpParameter> testSet = new ArrayList<>(working);
            testSet.add(candidate);

            CookieAnalyzer.TestResult result = testRequest(originalRequest, testSet,
                "With " + candidate.name());

            if (compareResults(baseline, result)) {
                log("  ✓ Adding " + candidate.name() + " makes it work!");
                working = testSet;
                return working; // Found a working set
            } else {
                log("  → Adding " + candidate.name() + " doesn't help");
            }
        }

        // If single additions don't work, try combinations (expensive!)
        log("  Single additions didn't work. Trying combinations...");
        return findCombination(originalRequest, currentSet, candidates, baseline);
    }

    private List<ParsedHttpParameter> findCombination(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> base,
            List<ParsedHttpParameter> candidates,
            CookieAnalyzer.TestResult baseline) {

        // Try adding 2 at a time, then 3, etc.
        for (int size = 2; size <= candidates.size(); size++) {
            List<List<ParsedHttpParameter>> combinations = generateCombinations(candidates, size);

            for (List<ParsedHttpParameter> combo : combinations) {
                List<ParsedHttpParameter> testSet = new ArrayList<>(base);
                testSet.addAll(combo);

                CookieAnalyzer.TestResult result = testRequest(originalRequest, testSet,
                    "Combination test");

                if (compareResults(baseline, result)) {
                    log("  ✓ Found working combination: " +
                        combo.stream().map(ParsedHttpParameter::name)
                            .reduce((a, b) -> a + ", " + b).orElse(""));
                    return testSet;
                }
            }
        }

        // Fallback: return all cookies
        List<ParsedHttpParameter> all = new ArrayList<>(base);
        all.addAll(candidates);
        return all;
    }

    private List<ParsedHttpParameter> verifyMinimality(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> workingSet,
            CookieAnalyzer.TestResult baseline) {

        List<ParsedHttpParameter> minimal = new ArrayList<>(workingSet);

        for (ParsedHttpParameter cookie : workingSet) {
            List<ParsedHttpParameter> without = new ArrayList<>(minimal);
            without.remove(cookie);

            if (without.isEmpty()) {
                log("  → " + cookie.name() + " is the only cookie, must be required");
                continue;
            }

            CookieAnalyzer.TestResult result = testRequest(originalRequest, without,
                "Verify: without " + cookie.name());

            if (compareResults(baseline, result)) {
                log("  ✓ " + cookie.name() + " can be removed");
                minimal = without;
            } else {
                log("  → " + cookie.name() + " is required");
            }
        }

        return minimal;
    }

    private Map<ParsedHttpParameter, List<ParsedHttpParameter>> detectAlternatives(
            HttpRequest originalRequest,
            List<ParsedHttpParameter> minimalSet,
            List<ParsedHttpParameter> optionalCookies,
            CookieAnalyzer.TestResult baseline) {

        Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives = new HashMap<>();

        for (ParsedHttpParameter required : minimalSet) {
            List<ParsedHttpParameter> alts = new ArrayList<>();

            for (ParsedHttpParameter optional : optionalCookies) {
                // Try replacing 'required' with 'optional'
                List<ParsedHttpParameter> testSet = new ArrayList<>(minimalSet);
                testSet.remove(required);
                testSet.add(optional);

                CookieAnalyzer.TestResult result = testRequest(originalRequest, testSet,
                    "Replace " + required.name() + " with " + optional.name());

                if (compareResults(baseline, result)) {
                    log("  ✓ " + optional.name() + " can replace " + required.name() +
                        " (OR relationship)");
                    alts.add(optional);
                }
            }

            if (!alts.isEmpty()) {
                alternatives.put(required, alts);
            }
        }

        if (alternatives.isEmpty()) {
            log("  No alternative cookies found.");
        }

        return alternatives;
    }

    private CookieAnalyzer.TestResult testRequest(HttpRequest originalRequest,
                                                  List<ParsedHttpParameter> cookiesToInclude,
                                                  String testName) {
        requestCount++;

        try {
            // Build request with only specified cookies
            HttpRequest modified = originalRequest.withRemovedParameters(
                originalRequest.parameters(burp.api.montoya.http.message.params.HttpParameterType.COOKIE));

            for (ParsedHttpParameter cookie : cookiesToInclude) {
                modified = modified.withAddedParameters(cookie);
            }

            HttpRequestResponse response = api.http().sendRequest(modified);

            if (response == null || response.response() == null) {
                return new CookieAnalyzer.TestResult(false, 0, 0, "No response", false);
            }

            int statusCode = response.response().statusCode();
            int contentLength = response.response().body().length();

            return new CookieAnalyzer.TestResult(true, statusCode, contentLength,
                testName, false);

        } catch (Exception e) {
            return new CookieAnalyzer.TestResult(false, 0, 0,
                "Error: " + e.getMessage(), false);
        }
    }

    private boolean compareResults(CookieAnalyzer.TestResult baseline,
                                  CookieAnalyzer.TestResult test) {
        if (!test.isSuccess()) return false;
        if (test.getStatusCode() != baseline.getStatusCode()) return false;

        // Allow up to 20% difference in content length
        double lengthDiff = Math.abs(test.getContentLength() - baseline.getContentLength()) * 100.0
            / Math.max(baseline.getContentLength(), 1);

        return lengthDiff <= 20;
    }

    private boolean isAlternative(ParsedHttpParameter cookie,
                                 Map<ParsedHttpParameter, List<ParsedHttpParameter>> alternatives) {
        for (List<ParsedHttpParameter> alts : alternatives.values()) {
            if (alts.contains(cookie)) {
                return true;
            }
        }
        return false;
    }

    private List<List<ParsedHttpParameter>> generateCombinations(
            List<ParsedHttpParameter> items, int size) {
        List<List<ParsedHttpParameter>> result = new ArrayList<>();
        generateCombinationsHelper(items, size, 0, new ArrayList<>(), result);
        return result;
    }

    private void generateCombinationsHelper(List<ParsedHttpParameter> items, int size,
                                           int start, List<ParsedHttpParameter> current,
                                           List<List<ParsedHttpParameter>> result) {
        if (current.size() == size) {
            result.add(new ArrayList<>(current));
            return;
        }

        for (int i = start; i < items.size(); i++) {
            current.add(items.get(i));
            generateCombinationsHelper(items, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private AnalysisResult createEmptyResult(List<ParsedHttpParameter> allCookies) {
        Map<ParsedHttpParameter, CookieStatus> statuses = new HashMap<>();
        for (ParsedHttpParameter cookie : allCookies) {
            statuses.put(cookie, CookieStatus.UNKNOWN);
        }
        return new AnalysisResult(new ArrayList<>(), statuses, new HashMap<>(), requestCount);
    }

    private void log(String message) {
        // Log to Burp output for debugging
        api.logging().logToOutput("[DEEP ANALYSIS] " + message);

        // Also send to UI callback if provided
        if (logger != null) {
            logger.accept(message);
        }
    }
}
