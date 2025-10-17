package com.harmony.agent.core.analyzer;

import com.harmony.agent.core.model.SecurityIssue;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for static analyzers
 */
public interface Analyzer {

    /**
     * Get analyzer name
     */
    String getName();

    /**
     * Check if analyzer is available (tool installed)
     */
    boolean isAvailable();

    /**
     * Analyze a single file
     */
    List<SecurityIssue> analyze(Path file) throws AnalyzerException;

    /**
     * Analyze multiple files
     */
    List<SecurityIssue> analyzeAll(List<Path> files) throws AnalyzerException;

    /**
     * Get analyzer version
     */
    String getVersion();
}
