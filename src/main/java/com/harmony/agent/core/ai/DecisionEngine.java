package com.harmony.agent.core.ai;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.SecurityIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Decision Engine - AI-powered security issue validation and enhancement
 * Orchestrates CodeSlicer, PromptBuilder, and CachedAiClient
 */
public class DecisionEngine {

    private static final Logger logger = LoggerFactory.getLogger(DecisionEngine.class);

    private final CachedAiValidationClient aiClient;
    private final CodeSlicer codeSlicer;
    private final Gson gson;
    private final ExecutorService executorService; // For parallel AI validation
    private final int validationConcurrency; // Max concurrent validations

    // Baseline confidence scores for each analyzer
    private static final double CLANG_BASELINE_CONFIDENCE = 0.90;
    private static final double SEMGREP_BASELINE_CONFIDENCE = 0.60;
    private static final double REGEX_BASELINE_CONFIDENCE = 0.40;
    private static final double AI_CONFIRMED_CONFIDENCE = 0.95;
    private static final double AI_FAILED_CONFIDENCE_MULTIPLIER = 0.8;

    // Semgrep race condition false positive detection
    private static final String[] RACE_CONDITION_KEYWORDS = {
        "race condition", "data race", "mutex", "concurrent", "thread-safe", "synchronization"
    };
    private static final String[] SINGLE_THREAD_INDICATORS = {
        "main()", "cli", "single-threaded", "event loop", "sequential", "single thread"
    };

    /**
     * Constructor
     */
    public DecisionEngine(ConfigManager configManager, ExecutorService executorService) {
        this.codeSlicer = new CodeSlicer();
        this.aiClient = new CachedAiValidationClient(
            new AiValidationClient(configManager)
        );
        this.gson = new Gson();
        this.executorService = executorService;
        this.validationConcurrency = configManager.getConfig().getAi().getValidationConcurrency();

        logger.info("Decision Engine initialized with AI provider: {}, concurrency: {}",
            aiClient.getProviderName(), validationConcurrency);
    }

    /**
     * Constructor with custom client (for testing)
     */
    public DecisionEngine(CachedAiValidationClient aiClient, CodeSlicer codeSlicer,
                         ExecutorService executorService, int validationConcurrency) {
        this.aiClient = aiClient;
        this.codeSlicer = codeSlicer;
        this.gson = new Gson();
        this.executorService = executorService;
        this.validationConcurrency = validationConcurrency;
    }

    /**
     * Enhance security issues with AI validation (Parallel version)
     *
     * @param staticIssues Issues from static analyzers
     * @return Enhanced issues list (false positives filtered out)
     */
    public List<SecurityIssue> enhanceIssues(List<SecurityIssue> staticIssues) {
        logger.info("Starting parallel AI enhancement for {} issues (concurrency: {})",
            staticIssues.size(), validationConcurrency);

        List<SecurityIssue> enhancedIssues = new ArrayList<>();
        List<Callable<SecurityIssue>> validationTasks = new ArrayList<>();
        List<SecurityIssue> noValidationNeeded = new ArrayList<>();

        // Separate issues into those needing validation and those that don't
        for (SecurityIssue issue : staticIssues) {
            if (needsAiValidation(issue)) {
                validationTasks.add(new AiValidationTask(issue));
            } else {
                // High-confidence analyzer (Clang-Tidy), skip AI validation
                noValidationNeeded.add(createHighConfidenceIssue(issue));
            }
        }

        logger.info("Submitting {} issues for parallel AI validation, {} skipped",
            validationTasks.size(), noValidationNeeded.size());

        // Process validation tasks in parallel using a bounded thread pool
        if (!validationTasks.isEmpty()) {
            // Create a dedicated validation pool with limited concurrency
            int poolSize = Math.min(validationConcurrency, validationTasks.size());
            ExecutorService validationPool = Executors.newFixedThreadPool(poolSize);

            try {
                // Submit all tasks and get futures
                List<Future<SecurityIssue>> futures = new ArrayList<>();
                for (Callable<SecurityIssue> task : validationTasks) {
                    futures.add(validationPool.submit(task));
                }

                // Collect results
                int validated = 0;
                int filtered = 0;
                int errors = 0;

                for (Future<SecurityIssue> future : futures) {
                    try {
                        SecurityIssue result = future.get(); // May block
                        if (result != null) {
                            // ✅ CRITICAL FIX: Only add validated issues (null means filtered)
                            enhancedIssues.add(result);
                            validated++;
                        } else {
                            // ✅ Result is null - AI filtered it as false positive
                            filtered++;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.error("Interrupted while waiting for AI validation result", e);
                        errors++;
                    } catch (ExecutionException e) {
                        logger.error("AI validation task failed", e.getCause());
                        errors++;
                    }
                }

                logger.info("AI enhancement complete: {} validated, {} filtered, {} errors",
                    validated, filtered, errors);

            } finally {
                // Shutdown validation pool
                validationPool.shutdown();
                try {
                    if (!validationPool.awaitTermination(10, TimeUnit.SECONDS)) {
                        validationPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    validationPool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Add issues that didn't need validation
        enhancedIssues.addAll(noValidationNeeded);

        logger.info("Total output issues: {}", enhancedIssues.size());

        // Log cache statistics
        logCacheStats();

        return enhancedIssues;
    }

    /**
     * Determine if an issue is likely a Semgrep race condition false positive
     * These are extremely common in single-threaded applications
     */
    private boolean isSemgrepRaceConditionFalsePositive(SecurityIssue issue, String codeSlice) {
        String analyzer = issue.getAnalyzer().toLowerCase();
        if (!analyzer.contains("semgrep")) {
            return false;
        }

        String title = issue.getTitle().toLowerCase();
        String description = issue.getDescription().toLowerCase();

        // Check if this is a race condition / mutex related warning
        boolean isRaceConditionWarning = false;
        for (String keyword : RACE_CONDITION_KEYWORDS) {
            if (title.contains(keyword) || description.contains(keyword)) {
                isRaceConditionWarning = true;
                break;
            }
        }

        if (!isRaceConditionWarning) {
            return false;
        }

        // Check for single-threaded indicators in code
        String codeLower = codeSlice.toLowerCase();
        for (String indicator : SINGLE_THREAD_INDICATORS) {
            if (codeLower.contains(indicator)) {
                logger.debug("Detected single-threaded context: {}", indicator);
                return true;
            }
        }

        // Check for threading constructs
        if (codeLower.contains("pthread_create") ||
            codeLower.contains("std::thread") ||
            codeLower.contains("boost::thread") ||
            codeLower.contains("thread pool") ||
            codeLower.contains("concurrent") ||
            codeLower.contains("async")) {
            logger.debug("Detected multi-threaded constructs - not a false positive");
            return false;
        }

        // No threading constructs found - likely single-threaded
        logger.debug("No multi-threaded constructs found - likely Semgrep false positive");
        return true;
    }

    /**
     * Determine if an issue needs AI validation
     */
    private boolean needsAiValidation(SecurityIssue issue) {
        String analyzer = issue.getAnalyzer().toLowerCase();

        // Semgrep: high false positive rate (20-40%)
        if (analyzer.contains("semgrep")) {
            // Pre-filter obvious Semgrep race condition false positives
            // (we'll do a quick check without fetching code yet)
            String title = issue.getTitle().toLowerCase();
            String description = issue.getDescription().toLowerCase();

            for (String keyword : RACE_CONDITION_KEYWORDS) {
                if (title.contains(keyword) || description.contains(keyword)) {
                    // This is a race condition warning - needs AI validation
                    return true;
                }
            }

            // Other Semgrep issues also need validation due to high false positive rate
            return true;
        }

        // Regex: very high false positive rate (50-70%)
        if (analyzer.contains("regex")) {
            return true;
        }

        // Clang-Tidy: low false positive rate (5-10%)
        // Only validate CRITICAL issues to save API costs
        if (analyzer.contains("clang") &&
            issue.getSeverity() == IssueSeverity.CRITICAL) {
            return true;
        }

        return false;
    }

    /**
     * Validate issue with AI
     *
     * @return Enhanced issue if valid, null if false positive
     */
    private SecurityIssue validateWithAi(SecurityIssue issue) throws Exception {
        // Get code context
        Path filePath = Paths.get(issue.getLocation().getFilePath());
        int lineNumber = issue.getLocation().getLineNumber();

        String codeSlice = codeSlicer.getContextSlice(filePath, lineNumber);

        // Build validation prompt
        String prompt = PromptBuilder.buildIssueValidationPrompt(issue, codeSlice);

        // Send to AI
        String jsonResponse = aiClient.sendRequest(prompt, true);

        // Parse response
        AiValidationResponse validation = parseValidationResponse(jsonResponse);

        if (validation.is_vulnerability) {
            // AI confirmed - create enhanced issue
            return createEnhancedIssue(issue, validation);
        } else {
            // AI marked as false positive
            return null;
        }
    }

    /**
     * Parse AI validation response
     */
    private AiValidationResponse parseValidationResponse(String jsonResponse)
            throws JsonSyntaxException {
        try {
            return gson.fromJson(jsonResponse, AiValidationResponse.class);
        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse AI response as JSON: {}", jsonResponse);
            throw e;
        }
    }

    /**
     * Create enhanced issue with AI validation
     */
    private SecurityIssue createEnhancedIssue(SecurityIssue original,
                                              AiValidationResponse validation) {
        // Parse AI suggested severity
        IssueSeverity aiSeverity;
        try {
            aiSeverity = IssueSeverity.valueOf(validation.suggested_severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid severity from AI: {}, using original",
                validation.suggested_severity);
            aiSeverity = original.getSeverity();
        }

        // Build enhanced issue
        return new SecurityIssue.Builder()
            .id(original.getId())
            .title(original.getTitle())
            .description(original.getDescription())
            .severity(aiSeverity)
            .category(original.getCategory())
            .location(original.getLocation())
            .analyzer(original.getAnalyzer() + " + AI")
            .metadata(original.getMetadata())
            .metadata("ai_validated", true)
            .metadata("ai_confidence", AI_CONFIRMED_CONFIDENCE)
            .metadata("ai_explanation", validation.reason)
            .metadata("original_severity", original.getSeverity().name())
            .build();
    }

    /**
     * Create high-confidence issue (no AI validation needed)
     */
    private SecurityIssue createHighConfidenceIssue(SecurityIssue original) {
        double confidence = getBaselineConfidence(original.getAnalyzer());

        return new SecurityIssue.Builder()
            .id(original.getId())
            .title(original.getTitle())
            .description(original.getDescription())
            .severity(original.getSeverity())
            .category(original.getCategory())
            .location(original.getLocation())
            .analyzer(original.getAnalyzer())
            .metadata(original.getMetadata())
            .metadata("ai_validated", false)
            .metadata("ai_confidence", confidence)
            .metadata("validation_skipped", "High confidence analyzer")
            .build();
    }

    /**
     * Create fallback issue (AI validation failed)
     */
    private SecurityIssue createFallbackIssue(SecurityIssue original) {
        double baseConfidence = getBaselineConfidence(original.getAnalyzer());
        double fallbackConfidence = baseConfidence * AI_FAILED_CONFIDENCE_MULTIPLIER;

        return new SecurityIssue.Builder()
            .id(original.getId())
            .title(original.getTitle())
            .description(original.getDescription())
            .severity(original.getSeverity())
            .category(original.getCategory())
            .location(original.getLocation())
            .analyzer(original.getAnalyzer())
            .metadata(original.getMetadata())
            .metadata("ai_validated", false)
            .metadata("ai_confidence", fallbackConfidence)
            .metadata("validation_error", "AI validation failed, using static analysis only")
            .build();
    }

    /**
     * Get baseline confidence for analyzer
     */
    private double getBaselineConfidence(String analyzer) {
        String lower = analyzer.toLowerCase();

        if (lower.contains("clang")) {
            return CLANG_BASELINE_CONFIDENCE;
        } else if (lower.contains("semgrep")) {
            return SEMGREP_BASELINE_CONFIDENCE;
        } else if (lower.contains("regex")) {
            return REGEX_BASELINE_CONFIDENCE;
        }

        return 0.5; // Default
    }

    /**
     * Log cache statistics
     */
    private void logCacheStats() {
        CachedAiValidationClient.CacheStats stats = aiClient.getStats();
        logger.info("AI Cache Statistics: {}", stats);
    }

    /**
     * Check if AI client is available
     */
    public boolean isAvailable() {
        return aiClient.isAvailable();
    }

    /**
     * Get cache statistics
     */
    public CachedAiValidationClient.CacheStats getCacheStats() {
        return aiClient.getStats();
    }

    /**
     * Clear AI response cache
     */
    public void clearCache() {
        aiClient.clearCache();
        codeSlicer.clearCache();
    }

    /**
     * Create issue marked as filtered by AI
     */
    private SecurityIssue markAsFiltered(SecurityIssue original, String reason) {
        return new SecurityIssue.Builder()
            .id(original.getId())
            .title(original.getTitle() + " [AI FILTERED]")
            .description(original.getDescription())
            .severity(IssueSeverity.INFO) // Downgrade severity for filtered issues
            .category(original.getCategory())
            .location(original.getLocation())
            .analyzer(original.getAnalyzer() + " + AI")
            .metadata(original.getMetadata())
            .metadata("ai_validated", true)
            .metadata("ai_filtered", true)
            .metadata("filter_reason", reason)
            .build();
    }

    /**
     * Callable task for parallel AI validation
     * Enhanced with pre-filtering for obvious Semgrep false positives
     */
    private class AiValidationTask implements java.util.concurrent.Callable<SecurityIssue> {
        private final SecurityIssue originalIssue;

        public AiValidationTask(SecurityIssue issue) {
            this.originalIssue = issue;
        }

        @Override
        public SecurityIssue call() {
            try {
                // Get code context
                Path filePath = Paths.get(originalIssue.getLocation().getFilePath());
                int lineNumber = originalIssue.getLocation().getLineNumber();

                String codeSlice = codeSlicer.getContextSlice(filePath, lineNumber);

                // Pre-check: Quick filtering for Semgrep race condition false positives
                if (isSemgrepRaceConditionFalsePositive(originalIssue, codeSlice)) {
                    logger.info("Pre-filtered Semgrep race condition false positive: {} (single-threaded context)",
                        originalIssue.getTitle());
                    return null;  // Quick filter - no need to call AI
                }

                // Build validation prompt
                String prompt = PromptBuilder.buildIssueValidationPrompt(originalIssue, codeSlice);

                // Send to AI (rate-limited)
                String jsonResponse = aiClient.sendRequest(prompt, true);

                // Parse response
                AiValidationResponse validation = parseValidationResponse(jsonResponse);

                if (validation.is_vulnerability) {
                    // AI confirmed - create enhanced issue
                    return createEnhancedIssue(originalIssue, validation);
                } else {
                    // ✅ CRITICAL FIX: AI marked as false positive - return null to filter it out
                    logger.info("AI filtered false positive: {} - Reason: {}",
                        originalIssue.getTitle(), validation.reason);
                    return null;  // Return null to completely remove false positives
                }
            } catch (Exception e) {
                // AI validation failed - return fallback issue
                logger.error("AI validation failed for issue: {}", originalIssue.getId(), e);
                return createFallbackIssue(originalIssue);
            }
        }
    }

    /**
     * AI validation response model
     */
    private static class AiValidationResponse {
        // Public fields for Gson deserialization (snake_case to match JSON)
        public boolean is_vulnerability;
        public String reason;
        public String suggested_severity;
    }
}
