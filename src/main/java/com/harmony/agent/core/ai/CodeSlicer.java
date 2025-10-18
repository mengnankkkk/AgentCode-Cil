package com.harmony.agent.core.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Code Slicer - Extract code context for AI analysis
 * Extracts relevant code snippets (function bodies) based on line numbers
 */
public class CodeSlicer {

    private static final Logger logger = LoggerFactory.getLogger(CodeSlicer.class);

    // Cache for file contents to avoid repeated disk reads
    private final Map<Path, List<String>> fileCache = new ConcurrentHashMap<>();

    // Pattern for detecting function signatures in C/C++
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "^[a-zA-Z_][a-zA-Z0-9_*\\s]*\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*\\([^)]*\\)\\s*\\{?$"
    );

    // Maximum fallback context lines
    private static final int FALLBACK_BEFORE_LINES = 10;
    private static final int FALLBACK_AFTER_LINES = 20;
    private static final int MAX_FUNCTION_SEARCH_LINES = 50;

    /**
     * Get code context slice for a specific issue location
     *
     * @param file File path
     * @param lineNumber Line number where issue was detected (1-indexed)
     * @return Code snippet containing the function body
     */
    public String getContextSlice(Path file, int lineNumber) {
        try {
            List<String> lines = getLines(file);

            if (lines.isEmpty()) {
                return "[Error: File is empty or cannot be read]";
            }

            if (lineNumber < 1 || lineNumber > lines.size()) {
                return String.format("[Error: Invalid line number %d (file has %d lines)]",
                    lineNumber, lines.size());
            }

            // Convert to 0-based index
            int issueLineIndex = lineNumber - 1;

            // Find function boundaries
            int functionStart = findFunctionStart(lines, issueLineIndex);
            int functionEnd = findFunctionEnd(lines, functionStart, issueLineIndex);

            // Extract the slice
            List<String> slice = lines.subList(functionStart, Math.min(functionEnd + 1, lines.size()));

            // Add line numbers for context
            StringBuilder result = new StringBuilder();
            result.append(String.format("// File: %s (lines %d-%d)\n",
                file.getFileName(), functionStart + 1, functionEnd + 1));

            for (int i = 0; i < slice.size(); i++) {
                int lineNum = functionStart + i + 1;
                String marker = (lineNum == lineNumber) ? " <<< ISSUE HERE" : "";
                result.append(String.format("%4d: %s%s\n", lineNum, slice.get(i), marker));
            }

            return result.toString();

        } catch (Exception e) {
            logger.error("Failed to slice code context for {}:{}", file, lineNumber, e);
            return String.format("[Error: Could not slice code - %s]", e.getMessage());
        }
    }

    /**
     * Get cached file lines
     */
    private List<String> getLines(Path file) {
        return fileCache.computeIfAbsent(file, f -> {
            try {
                return Files.readAllLines(f);
            } catch (IOException e) {
                logger.error("Failed to read file for slicing: {}", f, e);
                return Collections.emptyList();
            }
        });
    }

    /**
     * Find the start of the function containing the issue line
     */
    private int findFunctionStart(List<String> lines, int issueLineIndex) {
        // Search backwards for function signature
        for (int i = issueLineIndex; i >= Math.max(0, issueLineIndex - MAX_FUNCTION_SEARCH_LINES); i--) {
            String line = lines.get(i).trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*")) {
                continue;
            }

            // Check for function signature pattern
            if (FUNCTION_PATTERN.matcher(line).matches()) {
                logger.debug("Found function start at line {}: {}", i + 1, line);
                return i;
            }

            // Check for multi-line function signature ending
            if (line.endsWith("{") && !line.startsWith("{")) {
                // This might be the end of a multi-line signature
                // Search upward for the beginning
                for (int j = i - 1; j >= Math.max(0, i - 5); j--) {
                    String prevLine = lines.get(j).trim();
                    if (prevLine.matches("^[a-zA-Z_].*")) {
                        logger.debug("Found multi-line function start at line {}", j + 1);
                        return j;
                    }
                }
                // If not found, use current line
                return i;
            }
        }

        // Fallback: use lines before the issue
        int fallbackStart = Math.max(0, issueLineIndex - FALLBACK_BEFORE_LINES);
        logger.debug("Function start not found, using fallback: line {}", fallbackStart + 1);
        return fallbackStart;
    }

    /**
     * Find the end of the function
     */
    private int findFunctionEnd(List<String> lines, int functionStart, int issueLineIndex) {
        int braceCount = 0;
        boolean foundStartBrace = false;

        // Start from function start to find the opening brace
        for (int i = functionStart; i < lines.size(); i++) {
            String line = lines.get(i);

            // Count braces character by character
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    foundStartBrace = true;
                } else if (c == '}') {
                    braceCount--;
                }
            }

            // If we've found the opening brace and closed all braces, we're done
            if (foundStartBrace && braceCount == 0) {
                logger.debug("Found function end at line {}", i + 1);
                return i;
            }
        }

        // Fallback: use lines after the issue
        int fallbackEnd = Math.min(lines.size() - 1, issueLineIndex + FALLBACK_AFTER_LINES);
        logger.debug("Function end not found, using fallback: line {}", fallbackEnd + 1);
        return fallbackEnd;
    }

    /**
     * Clear the file cache (useful for testing or low-memory scenarios)
     */
    public void clearCache() {
        fileCache.clear();
        logger.debug("Code slicer cache cleared");
    }

    /**
     * Get cache statistics
     */
    public int getCacheSize() {
        return fileCache.size();
    }
}
