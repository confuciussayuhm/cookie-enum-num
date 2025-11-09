package com.github.cookieenum.async;

import burp.api.montoya.MontoyaApi;
import com.github.cookieenum.models.CookieInfo;
import com.github.cookieenum.models.QueueStats;
import com.github.cookieenum.service.CookieInfoService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async queue for processing cookie discovery tasks
 */
public class CookieProcessingQueue {
    private final MontoyaApi api;
    private final CookieInfoService cookieService;
    private final BlockingQueue<CookieDiscoveryTask> queue;
    private final Set<String> inProgress;
    private final ExecutorService workerPool;
    private final Semaphore rateLimiter;
    private final ScheduledExecutorService rateLimiterScheduler;

    // Statistics
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong aiQueries = new AtomicLong(0);

    private volatile boolean running = true;

    public CookieProcessingQueue(MontoyaApi api, CookieInfoService service) {
        this.api = api;
        this.cookieService = service;

        // Bounded queue to prevent memory issues
        this.queue = new LinkedBlockingQueue<>(1000);

        // Track in-progress tasks for de-duplication
        this.inProgress = Collections.synchronizedSet(new HashSet<>());

        // Worker thread pool
        int numWorkers = getConfiguredWorkerCount();
        this.workerPool = Executors.newFixedThreadPool(numWorkers,
                new WorkerThreadFactory());

        // Rate limiter for AI queries
        int queriesPerMinute = getConfiguredRateLimit();
        this.rateLimiter = new Semaphore(queriesPerMinute);

        // Scheduler to refill rate limiter
        this.rateLimiterScheduler = Executors.newScheduledThreadPool(1);

        // Start workers
        startWorkers(numWorkers);

        // Start rate limiter refiller
        startRateLimiterRefiller(queriesPerMinute);

        api.logging().logToOutput(String.format(
                "Cookie processor started: %d workers, %d queries/min limit",
                numWorkers, queriesPerMinute));
    }

    /**
     * Submit a task to the queue
     */
    public void submit(CookieDiscoveryTask task) {
        if (!running) {
            return;
        }

        String taskId = task.getTaskId();

        // De-duplicate: skip if already queued or processing
        if (inProgress.contains(taskId)) {
            return;
        }

        // Try to add to queue (non-blocking)
        if (queue.offer(task)) {
            inProgress.add(taskId);
        } else {
            // Queue full - log warning
            api.logging().logToError("Cookie queue full - dropping task: " + taskId);
        }
    }

    /**
     * Start worker threads
     */
    private void startWorkers(int numWorkers) {
        for (int i = 0; i < numWorkers; i++) {
            workerPool.submit(this::workerLoop);
        }
    }

    /**
     * Main worker loop
     */
    private void workerLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Blocking take - waits for work
                CookieDiscoveryTask task = queue.poll(1, TimeUnit.SECONDS);

                if (task != null) {
                    try {
                        processCookie(task);
                    } catch (Exception e) {
                        api.logging().logToError("Worker error processing " +
                                task.getCookieName() + ": " + e.getMessage());
                    } finally {
                        // Remove from in-progress
                        inProgress.remove(task.getTaskId());
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        api.logging().logToOutput("Worker thread exiting");
    }

    /**
     * Process a single cookie discovery task
     */
    private void processCookie(CookieDiscoveryTask task) {
        processedCount.incrementAndGet();

        // 1. Check cache first (fast) - unless force re-analysis is requested
        if (!task.isForceReanalysis()) {
            boolean foundInCache = cookieService.getCookieInfoFromCache(
                    task.getCookieName(),
                    task.getDomain()
            ).isPresent();

            if (foundInCache) {
                cacheHits.incrementAndGet();
                api.logging().logToOutput(String.format(
                        "[CACHE HIT] %s (domain: %s)",
                        task.getCookieName(), task.getDomain()));
                return;
            }
        } else {
            api.logging().logToOutput(String.format(
                    "[FORCE RE-ANALYSIS] %s (domain: %s) - bypassing cache",
                    task.getCookieName(), task.getDomain()));
        }

        // 2. Not in cache - need to query AI
        api.logging().logToOutput(String.format(
                "[AI QUERY] %s (domain: %s) - waiting for rate limit...",
                task.getCookieName(), task.getDomain()));

        // Wait for rate limiter
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // 3. Query AI
        try {
            aiQueries.incrementAndGet();

            CookieInfo info = cookieService.queryCookieFromAI(
                    task.getCookieName(),
                    task.getDomain()
            );

            api.logging().logToOutput(String.format(
                    "[AI SUCCESS] %s → %s (%s)",
                    task.getCookieName(), info.getVendor(), info.getCategory()));

        } catch (Exception e) {
            api.logging().logToError(String.format(
                    "[AI FAILED] %s: %s",
                    task.getCookieName(), e.getMessage()));
        }
    }

    /**
     * Start rate limiter refiller
     */
    private void startRateLimiterRefiller(int queriesPerMinute) {
        rateLimiterScheduler.scheduleAtFixedRate(() -> {
            try {
                int current = rateLimiter.availablePermits();
                int toAdd = queriesPerMinute - current;
                if (toAdd > 0) {
                    rateLimiter.release(toAdd);
                    api.logging().logToOutput(String.format(
                            "[RATE LIMITER] Refilled %d permits", toAdd));
                }
            } catch (Exception e) {
                api.logging().logToError("Rate limiter refill error: " + e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Get configured worker count
     */
    private int getConfiguredWorkerCount() {
        Integer workers = api.persistence().preferences()
                .getInteger("cookiedb.workerThreads");
        return workers != null ? workers : 3;
    }

    /**
     * Get configured rate limit
     */
    private int getConfiguredRateLimit() {
        Integer rateLimit = api.persistence().preferences()
                .getInteger("cookiedb.queriesPerMinute");
        return rateLimit != null ? rateLimit : 10;
    }

    /**
     * Get queue statistics
     */
    public QueueStats getStats() {
        return new QueueStats(
                queue.size(),
                processedCount.get(),
                cacheHits.get(),
                aiQueries.get(),
                inProgress.size()
        );
    }

    /**
     * Shutdown the queue
     */
    public void shutdown() {
        running = false;

        api.logging().logToOutput("Shutting down cookie processing queue...");

        // Shutdown workers
        workerPool.shutdownNow();

        // Shutdown rate limiter scheduler
        rateLimiterScheduler.shutdownNow();

        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                api.logging().logToError("Worker pool did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        api.logging().logToOutput("Cookie processing queue shut down. Stats: " + getStats());
    }

    /**
     * Worker thread factory
     */
    private static class WorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Cookie-Processor-" + counter.incrementAndGet());
            t.setDaemon(true); // Don't prevent JVM shutdown
            return t;
        }
    }
}
