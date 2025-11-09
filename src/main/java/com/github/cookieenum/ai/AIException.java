package com.github.cookieenum.ai;

/**
 * Exception thrown when AI provider fails
 */
public class AIException extends Exception {
    public AIException(String message) {
        super(message);
    }

    public AIException(String message, Throwable cause) {
        super(message, cause);
    }
}
