package com.harmony.agent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Error Classifier
 * Categorize errors as Permanent or Transient for different retry strategies
 */
public class ErrorClassifier {
    private static final Logger logger = LoggerFactory.getLogger(ErrorClassifier.class);

    public enum ErrorType {
        TRANSIENT,  // Can be retried (network timeout, temporary service unavailable, etc.)
        PERMANENT   // Should not be retried (invalid input, not found, etc.)
    }

    /**
     * Classify error based on exception message and type
     */
    public static ErrorType classify(Exception exception) {
        if (exception == null) {
            return ErrorType.PERMANENT;
        }

        return classify(exception.getMessage());
    }

    /**
     * Classify error based on error message
     */
    public static ErrorType classify(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return ErrorType.PERMANENT;
        }

        String lower = errorMessage.toLowerCase();

        // Transient errors (can be retried)
        if (lower.contains("timeout") ||
            lower.contains("connection refused") ||
            lower.contains("connection reset") ||
            lower.contains("network unreachable") ||
            lower.contains("service unavailable") ||
            lower.contains("temporarily unavailable") ||
            lower.contains("too many requests") ||
            lower.contains("rate limit") ||
            lower.contains("temporary") ||
            lower.contains("try again")) {
            return ErrorType.TRANSIENT;
        }

        // HTTP error codes
        if (lower.contains("408") ||  // Request Timeout
            lower.contains("429") ||  // Too Many Requests
            lower.contains("500") ||  // Internal Server Error
            lower.contains("502") ||  // Bad Gateway
            lower.contains("503") ||  // Service Unavailable
            lower.contains("504")) {  // Gateway Timeout
            return ErrorType.TRANSIENT;
        }

        // Permanent errors (should not retry)
        if (lower.contains("not found") ||
            lower.contains("404") ||
            lower.contains("400") ||  // Bad Request
            lower.contains("401") ||  // Unauthorized
            lower.contains("403") ||  // Forbidden
            lower.contains("invalid") ||
            lower.contains("malformed") ||
            lower.contains("unsupported") ||
            lower.contains("cannot") ||
            lower.contains("failed to parse")) {
            return ErrorType.PERMANENT;
        }

        // Default: treat as transient if unsure (safer option)
        return ErrorType.TRANSIENT;
    }

    /**
     * Determine max retries based on error type
     */
    public static int getMaxRetries(ErrorType errorType) {
        return switch (errorType) {
            case TRANSIENT -> 3;  // Retry up to 3 times for transient errors
            case PERMANENT -> 0;  // Do not retry for permanent errors
        };
    }

    /**
     * Get error description
     */
    public static String getErrorDescription(String errorMessage) {
        if (errorMessage == null) {
            return "Unknown error";
        }

        // Try to extract the most relevant part
        String[] lines = errorMessage.split("\n");
        return lines[0];
    }

    /**
     * Check if error is likely recoverable
     */
    public static boolean isRecoverable(String errorMessage) {
        return classify(errorMessage) == ErrorType.TRANSIENT;
    }
}
