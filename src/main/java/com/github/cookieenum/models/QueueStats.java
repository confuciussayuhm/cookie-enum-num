package com.github.cookieenum.models;

/**
 * Statistics for cookie processing queue
 */
public class QueueStats {
    private final int queueSize;
    private final long processedCount;
    private final long cacheHits;
    private final long aiQueries;
    private final int inProgress;

    public QueueStats(int queueSize, long processedCount, long cacheHits,
                      long aiQueries, int inProgress) {
        this.queueSize = queueSize;
        this.processedCount = processedCount;
        this.cacheHits = cacheHits;
        this.aiQueries = aiQueries;
        this.inProgress = inProgress;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public long getCacheHits() {
        return cacheHits;
    }

    public long getAiQueries() {
        return aiQueries;
    }

    public int getInProgress() {
        return inProgress;
    }

    public double getCacheHitRate() {
        if (processedCount == 0) return 0.0;
        return (double) cacheHits / processedCount;
    }

    @Override
    public String toString() {
        return String.format(
            "QueueStats{queue=%d, processed=%d, cacheHits=%d (%.1f%%), aiQueries=%d, inProgress=%d}",
            queueSize, processedCount, cacheHits, getCacheHitRate() * 100, aiQueries, inProgress
        );
    }
}
