package com.github.cookieenum.async;

/**
 * Represents a task to discover cookie information
 */
public class CookieDiscoveryTask implements Comparable<CookieDiscoveryTask> {
    private final String cookieName;
    private final String domain;
    private final Priority priority;
    private final long requestNumber;
    private final long submittedAt;
    private final boolean forceReanalysis;

    public enum Priority {
        MANUAL(1),    // User explicitly requested analysis
        AUTO(2);      // Auto-discovered from traffic

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public CookieDiscoveryTask(String cookieName, String domain, Priority priority, long requestNumber) {
        this(cookieName, domain, priority, requestNumber, false);
    }

    public CookieDiscoveryTask(String cookieName, String domain, Priority priority, long requestNumber, boolean forceReanalysis) {
        this.cookieName = cookieName;
        this.domain = domain;
        this.priority = priority;
        this.requestNumber = requestNumber;
        this.submittedAt = System.currentTimeMillis();
        this.forceReanalysis = forceReanalysis;
    }

    /**
     * Get unique task ID for de-duplication
     */
    public String getTaskId() {
        return cookieName + "|" + (domain != null ? domain : "");
    }

    public String getCookieName() {
        return cookieName;
    }

    public String getDomain() {
        return domain;
    }

    public Priority getPriority() {
        return priority;
    }

    public long getRequestNumber() {
        return requestNumber;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public boolean isForceReanalysis() {
        return forceReanalysis;
    }

    @Override
    public int compareTo(CookieDiscoveryTask other) {
        // Higher priority (lower value) first
        int priorityCompare = Integer.compare(this.priority.value, other.priority.value);
        if (priorityCompare != 0) {
            return priorityCompare;
        }

        // Then FIFO order
        return Long.compare(this.submittedAt, other.submittedAt);
    }

    @Override
    public String toString() {
        return String.format("CookieDiscoveryTask{name='%s', domain='%s', priority=%s}",
                cookieName, domain, priority);
    }
}
