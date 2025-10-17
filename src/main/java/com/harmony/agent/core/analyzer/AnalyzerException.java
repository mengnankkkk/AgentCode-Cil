package com.harmony.agent.core.analyzer;

/**
 * Exception thrown by analyzers
 */
public class AnalyzerException extends Exception {

    public AnalyzerException(String message) {
        super(message);
    }

    public AnalyzerException(String message, Throwable cause) {
        super(message, cause);
    }
}
