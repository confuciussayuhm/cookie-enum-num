package com.github.cookieenum.async;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import com.github.cookieenum.models.QueueStats;
import com.github.cookieenum.util.DomainFilter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP handler that passively monitors requests and queues cookies for analysis
 */
public class CookieAutoProcessor implements HttpHandler {
    private final MontoyaApi api;
    private final CookieProcessingQueue processingQueue;
    private final AtomicLong requestCount = new AtomicLong(0);
    private volatile boolean enabled = true;
    private volatile DomainFilter domainFilter;

    public CookieAutoProcessor(MontoyaApi api, CookieProcessingQueue queue) {
        this.api = api;
        this.processingQueue = queue;

        // Load domain filter settings
        loadDomainFilterSettings();
    }

    /**
     * Load domain filter settings from Burp preferences
     */
    private void loadDomainFilterSettings() {
        String modeStr = api.persistence().preferences()
                .getString("cookiedb.domainFilter.mode");

        DomainFilter.FilterMode mode = DomainFilter.FilterMode.ALL;
        if (modeStr != null) {
            try {
                mode = DomainFilter.FilterMode.valueOf(modeStr);
            } catch (IllegalArgumentException e) {
                // Invalid mode, default to ALL
            }
        }

        String domainsStr = api.persistence().preferences()
                .getString("cookiedb.domainFilter.domains");
        Set<String> domains = DomainFilter.parseDomainsFromString(domainsStr);

        this.domainFilter = new DomainFilter(mode, domains);
        api.logging().logToOutput("Domain filter loaded: " + domainFilter.toString());
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        if (!enabled) {
            return RequestToBeSentAction.continueWith(requestToBeSent);
        }

        // Process asynchronously - NEVER block here!
        CompletableFuture.runAsync(() -> {
            try {
                extractAndQueueCookies(requestToBeSent);
            } catch (Exception e) {
                // Silently log - don't disrupt normal flow
                api.logging().logToError("Error processing cookies: " + e.getMessage());
            }
        });

        // Return immediately - DO NOT BLOCK
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        if (!enabled) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        // Process Set-Cookie headers asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                extractAndQueueSetCookies(responseReceived);
            } catch (Exception e) {
                // Silently log - don't disrupt normal flow
                api.logging().logToError("Error processing Set-Cookie headers: " + e.getMessage());
            }
        });

        return ResponseReceivedAction.continueWith(responseReceived);
    }

    /**
     * Extract cookies from request and queue for processing
     */
    private void extractAndQueueCookies(HttpRequestToBeSent request) {
        long reqNum = requestCount.incrementAndGet();

        String domain = request.httpService().host();

        // Check domain filter
        boolean isInScope = api.scope().isInScope(request.url());
        if (!domainFilter.shouldProcess(domain, isInScope)) {
            // Log occasionally to show filtering is happening
            if (reqNum % 100 == 0) {
                api.logging().logToOutput(String.format(
                        "[AUTO-PROCESSOR] Filtered out domain: %s (mode: %s)",
                        domain, domainFilter.getMode()));
            }
            return;
        }

        // Extract cookies from request
        List<ParsedHttpParameter> cookies = request.parameters(HttpParameterType.COOKIE);

        if (cookies.isEmpty()) {
            return;
        }

        // Queue each cookie for processing
        for (ParsedHttpParameter cookie : cookies) {
            CookieDiscoveryTask task = new CookieDiscoveryTask(
                    cookie.name(),
                    domain,
                    CookieDiscoveryTask.Priority.AUTO,
                    reqNum
            );

            processingQueue.submit(task);
        }

        // Log occasionally to show it's working
        if (reqNum % 100 == 0) {
            api.logging().logToOutput(String.format(
                    "[AUTO-PROCESSOR] Processed %d requests, Queue: %s",
                    reqNum, processingQueue.getStats()));
        }
    }

    /**
     * Extract cookies from Set-Cookie headers in response
     */
    private void extractAndQueueSetCookies(HttpResponseReceived response) {
        String domain = response.initiatingRequest().httpService().host();

        // Check domain filter
        boolean isInScope = api.scope().isInScope(response.initiatingRequest().url());
        if (!domainFilter.shouldProcess(domain, isInScope)) {
            return;
        }

        // Parse Set-Cookie headers from response headers only (not body)
        String headersString = response.headers().toString();
        String[] lines = headersString.split("\\r?\\n");

        for (String line : lines) {
            if (line.toLowerCase().startsWith("set-cookie:")) {
                String setCookieValue = line.substring("set-cookie:".length()).trim();

                // Extract cookie name from "name=value; attributes..."
                int equalsIndex = setCookieValue.indexOf('=');
                if (equalsIndex > 0) {
                    String cookieName = setCookieValue.substring(0, equalsIndex).trim();

                    // Skip empty or invalid cookie names (spaces, semicolons, etc.)
                    if (cookieName.isEmpty() || cookieName.contains(";") || cookieName.contains(" ")) {
                        continue;
                    }

                    CookieDiscoveryTask task = new CookieDiscoveryTask(
                            cookieName,
                            domain,
                            CookieDiscoveryTask.Priority.AUTO,
                            requestCount.get()
                    );

                    processingQueue.submit(task);
                }
            }
        }
    }

    /**
     * Enable or disable auto-processing
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        api.logging().logToOutput("Cookie auto-processor " +
                (enabled ? "enabled" : "disabled"));
    }

    /**
     * Check if auto-processing is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get statistics
     */
    public QueueStats getStats() {
        return processingQueue.getStats();
    }

    /**
     * Get total requests processed
     */
    public long getRequestCount() {
        return requestCount.get();
    }

    /**
     * Update domain filter settings
     */
    public void setDomainFilter(DomainFilter.FilterMode mode, Set<String> domains) {
        this.domainFilter = new DomainFilter(mode, domains);
        api.logging().logToOutput("Domain filter updated: " + domainFilter.toString());

        // Save to preferences
        api.persistence().preferences().setString("cookiedb.domainFilter.mode", mode.name());
        if (mode == DomainFilter.FilterMode.CUSTOM_LIST && domains != null) {
            String domainsStr = String.join(", ", domains);
            api.persistence().preferences().setString("cookiedb.domainFilter.domains", domainsStr);
        }
    }

    /**
     * Get current domain filter
     */
    public DomainFilter getDomainFilter() {
        return domainFilter;
    }

    /**
     * Process existing HTTP history from Burp Proxy
     */
    public void processExistingHistory() {
        processExistingHistory(false);
    }

    /**
     * Process existing HTTP history from Burp Proxy
     * @param forceReanalysis If true, re-analyze all cookies even if they exist in database
     */
    public void processExistingHistory(boolean forceReanalysis) {
        if (forceReanalysis) {
            api.logging().logToOutput("[AUTO-PROCESSOR] Starting FORCED re-analysis of all HTTP history...");
        } else {
            api.logging().logToOutput("[AUTO-PROCESSOR] Starting to process existing HTTP history (skipping known cookies)...");
        }

        CompletableFuture.runAsync(() -> {
            try {
                var history = api.proxy().history();

                int totalEntries = history.size();
                int processedCookies = 0;

                api.logging().logToOutput("[AUTO-PROCESSOR] Found " + totalEntries + " history entries");

                for (var entry : history) {
                    try {
                        String domain = entry.finalRequest().httpService().host();

                        // Check domain filter
                        boolean isInScope = api.scope().isInScope(entry.finalRequest().url());
                        if (!domainFilter.shouldProcess(domain, isInScope)) {
                            continue; // Skip this domain
                        }

                        // Process cookies from request
                        List<ParsedHttpParameter> cookies =
                                entry.finalRequest().parameters(HttpParameterType.COOKIE);

                        for (ParsedHttpParameter cookie : cookies) {
                            CookieDiscoveryTask task = new CookieDiscoveryTask(
                                    cookie.name(),
                                    domain,
                                    CookieDiscoveryTask.Priority.MANUAL,
                                    requestCount.get(),
                                    forceReanalysis
                            );
                            processingQueue.submit(task);
                            processedCookies++;
                        }

                        // Process Set-Cookie headers from response if available
                        if (entry.hasResponse()) {
                            // Parse Set-Cookie headers from response headers only (not body)
                            String headersString = entry.response().headers().toString();
                            String[] lines = headersString.split("\\r?\\n");

                            for (String line : lines) {
                                if (line.toLowerCase().startsWith("set-cookie:")) {
                                    String setCookieValue = line.substring("set-cookie:".length()).trim();

                                    // Extract cookie name from "name=value; attributes..."
                                    int equalsIndex = setCookieValue.indexOf('=');
                                    if (equalsIndex > 0) {
                                        String cookieName = setCookieValue.substring(0, equalsIndex).trim();

                                        // Skip empty or invalid cookie names (spaces, semicolons, etc.)
                                        if (cookieName.isEmpty() || cookieName.contains(";") || cookieName.contains(" ")) {
                                            continue;
                                        }

                                        CookieDiscoveryTask task = new CookieDiscoveryTask(
                                                cookieName,
                                                domain,
                                                CookieDiscoveryTask.Priority.MANUAL,
                                                requestCount.get(),
                                                forceReanalysis
                                        );
                                        processingQueue.submit(task);
                                        processedCookies++;
                                    }
                                }
                            }
                        }

                    } catch (Exception e) {
                        // Skip this entry if there's an error
                    }
                }

                api.logging().logToOutput("[AUTO-PROCESSOR] Finished processing history. " +
                        "Queued " + processedCookies + " cookies for analysis.");

            } catch (Exception e) {
                api.logging().logToError("Error processing HTTP history: " + e.getMessage());
            }
        });
    }
}
