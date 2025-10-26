package com.harmony.agent.core;

import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.ai.DecisionEngine;
import com.harmony.agent.core.analyzer.Analyzer;
import com.harmony.agent.core.analyzer.AnalyzerException;
import com.harmony.agent.core.analyzer.ClangAnalyzer;
import com.harmony.agent.core.analyzer.SemgrepAnalyzer;
import com.harmony.agent.core.analyzer.RegexAnalyzer;
import com.harmony.agent.core.model.ScanResult;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.report.ReportGenerator;
import com.harmony.agent.core.scanner.CodeScanner;
import com.harmony.agent.core.store.UnifiedIssueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Main analysis engine that coordinates scanning and analysis
 */
public class AnalysisEngine {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisEngine.class);

    private final String sourcePath;
    private final AnalysisConfig config;
    private final CodeScanner scanner;
    private final List<Analyzer> analyzers;
    private final ExecutorService executorService;
    private final DecisionEngine decisionEngine;
    private final ReportGenerator reportGenerator;
    private final boolean aiEnhancementEnabled;

    public AnalysisEngine(String sourcePath, AnalysisConfig config) {
        this.sourcePath = sourcePath;
        this.config = config;
        this.scanner = new CodeScanner(sourcePath, true, config.getCompileCommandsPath());
        this.analyzers = initializeAnalyzers();

        // Create thread pool for parallel analysis
        int threadCount = config.isParallel() ? config.getMaxThreads() : 1;
        this.executorService = Executors.newFixedThreadPool(threadCount);

        // Initialize AI decision engine (pass ExecutorService for parallel validation)
        this.decisionEngine = new DecisionEngine(ConfigManager.getInstance(), this.executorService);
        this.aiEnhancementEnabled = config.isAiEnhancementEnabled() &&
                                    decisionEngine.isAvailable();

        // Initialize report generator
        this.reportGenerator = new ReportGenerator();

        if (aiEnhancementEnabled) {
            logger.info("AnalysisEngine initialized with {} threads + AI enhancement",
                threadCount);
        } else {
            logger.info("AnalysisEngine initialized with {} threads (AI enhancement disabled)",
                threadCount);
        }
    }

    /**
     * Initialize available analyzers
     */
    private List<Analyzer> initializeAnalyzers() {
        List<Analyzer> analyzers = new ArrayList<>();

        // Add Clang analyzer if available (with ExecutorService for parallel file analysis)
        ClangAnalyzer clangAnalyzer = new ClangAnalyzer(
            ConfigManager.getInstance().getConfig().getTools().getClangPath(),
            config.getCompileCommandsPath(),
            this.executorService  // Pass ExecutorService for parallel file processing
        );
        if (clangAnalyzer.isAvailable()) {
            analyzers.add(clangAnalyzer);
            logger.info("Clang analyzer enabled (parallel mode): {}", clangAnalyzer.getVersion());
        } else {
            logger.warn("Clang analyzer not available - ensure clang-tidy is installed");
        }

        // Add Semgrep analyzer if available (already optimized for batch processing)
        SemgrepAnalyzer semgrepAnalyzer = new SemgrepAnalyzer();
        if (semgrepAnalyzer.isAvailable()) {
            analyzers.add(semgrepAnalyzer);
            logger.info("Semgrep analyzer enabled (batch mode): {}", semgrepAnalyzer.getVersion());
        } else {
            logger.warn("Semgrep analyzer not available - ensure semgrep is installed");
        }

        // Always add built-in regex analyzer as fallback
        RegexAnalyzer regexAnalyzer = new RegexAnalyzer();
        analyzers.add(regexAnalyzer);
        logger.info("Built-in regex analyzer enabled: {}", regexAnalyzer.getVersion());

        return analyzers;
    }

    /**
     * Run full analysis
     */
    public ScanResult analyze() throws IOException, AnalyzerException {
        logger.info("Starting analysis of: {}", sourcePath);
        Instant startTime = Instant.now();

        // Scan for files
        List<Path> files = config.isIncremental() ?
            scanner.scanIncremental() : scanner.scanAll();

        logger.info("Scanning complete. Found {} files to analyze", files.size());

        if (files.isEmpty()) {
            logger.warn("No files found to analyze");
            return createEmptyResult(startTime);
        }

        // Analyze files
        List<SecurityIssue> allIssues = analyzeFiles(files);

        // Deduplicate issues
        allIssues = deduplicateIssues(allIssues);

        // AI Enhancement: Validate issues and filter false positives
        if (aiEnhancementEnabled) {
            logger.info("Enhancing {} issues with AI validation...", allIssues.size());
            int beforeCount = allIssues.size();
            allIssues = decisionEngine.enhanceIssues(allIssues);
            int afterCount = allIssues.size();
            int filtered = beforeCount - afterCount;

            logger.info("AI enhancement complete: {} issues → {} issues ({} filtered)",
                beforeCount, afterCount, filtered);
        } else {
            logger.debug("AI enhancement disabled, using static analysis results only");
        }

        // Build result
        Instant endTime = Instant.now();
        ScanResult.Builder resultBuilder = new ScanResult.Builder()
            .sourcePath(sourcePath)
            .startTime(startTime)
            .endTime(endTime)
            .addIssues(allIssues);

        // Add analyzers used
        for (Analyzer analyzer : analyzers) {
            resultBuilder.addAnalyzer(analyzer.getName());
        }

        // Add statistics
        resultBuilder.addStatistic("total_files", files.size());
        resultBuilder.addStatistic("total_issues", allIssues.size());
        resultBuilder.addStatistic("analyzers_count", analyzers.size());

        // Add AI filtering statistics
        if (aiEnhancementEnabled) {
            int beforeCount = allIssues.size() + (allIssues.size() > 0 ?
                (int) allIssues.stream()
                    .filter(i -> i.getMetadata().containsKey("ai_validated"))
                    .count() : 0);
            int filtered = beforeCount - allIssues.size();
            resultBuilder.addStatistic("ai_filtered_count", filtered);
        }

        ScanResult result = resultBuilder.build();

        logger.info("Analysis complete. Found {} issues in {} files",
            allIssues.size(), files.size());

        // Generate HTML report if output path specified
        if (config.getOutputPath() != null && !config.getOutputPath().isBlank()) {
            try {
                Path outputPath = Paths.get(config.getOutputPath());
                logger.info("Generating HTML report at: {}", outputPath);
                reportGenerator.generate(result, outputPath);
                logger.info("Report generated successfully: {}", outputPath);

                // Also generate JSON report for machine reading (used by suggest/refactor commands)
                String jsonPath = config.getOutputPath().replaceFirst("\\.html$", ".json");
                if (jsonPath.equals(config.getOutputPath())) {
                    jsonPath = config.getOutputPath() + ".json";
                }
                Path jsonOutputPath = Paths.get(jsonPath);
                logger.info("Generating JSON report at: {}", jsonOutputPath);
                com.harmony.agent.core.report.JsonReportWriter jsonWriter =
                    new com.harmony.agent.core.report.JsonReportWriter();
                jsonWriter.write(result, jsonOutputPath);
                logger.info("JSON report generated successfully: {}", jsonOutputPath);
            } catch (Exception e) {
                logger.error("Failed to generate report", e);
                // Don't fail the entire analysis if report generation fails
            }
        }

        return result;
    }

    /**
     * Run analysis and write results to unified issue store
     *
     * 用途：在交互模式中，integrate analysis results into the unified store
     * for merge with review results and other analyses
     *
     * @param store UnifiedIssueStore to write issues to
     * @return ScanResult (same as analyze())
     */
    public ScanResult analyzeWithStore(UnifiedIssueStore store) throws IOException, AnalyzerException {
        ScanResult result = analyze();

        // 将结果写入 Store
        if (store != null && result != null) {
            store.addIssues(result.getIssues());
            logger.info("Wrote {} issues to UnifiedIssueStore", result.getIssues().size());
        }

        return result;
    }

    /**
     * Analyze files with available analyzers
     */
    private List<SecurityIssue> analyzeFiles(List<Path> files) throws AnalyzerException {
        if (analyzers.isEmpty()) {
            throw new AnalyzerException("No analyzers available");
        }

        // Select analyzers based on analysis level
        List<Analyzer> selectedAnalyzers = selectAnalyzersByLevel();

        if (selectedAnalyzers.isEmpty()) {
            throw new AnalyzerException("No suitable analyzers for level: " + config.getLevel());
        }

        List<SecurityIssue> allIssues = new ArrayList<>();

        if (config.isParallel()) {
            // Parallel analysis
            allIssues = analyzeParallel(files, selectedAnalyzers);
        } else {
            // Sequential analysis
            allIssues = analyzeSequential(files, selectedAnalyzers);
        }

        return allIssues;
    }

    /**
     * Select analyzers based on analysis level
     */
    private List<Analyzer> selectAnalyzersByLevel() {
        List<Analyzer> selected = new ArrayList<>();
        String level = config.getLevel().toLowerCase();

        logger.info("Selecting analyzers for level: {}", level);

        switch (level) {
            case "quick":
                // Quick mode: Use only Semgrep (fast pattern matching)
                for (Analyzer analyzer : analyzers) {
                    if (analyzer.getName().equals("Semgrep")) {
                        selected.add(analyzer);
                        logger.info("Quick mode: Using {}", analyzer.getName());
                    }
                }
                // Fallback to regex if semgrep not available
                if (selected.isEmpty()) {
                    for (Analyzer analyzer : analyzers) {
                        if (analyzer.getName().contains("Regex")) {
                            selected.add(analyzer);
                            logger.info("Quick mode fallback: Using {}", analyzer.getName());
                        }
                    }
                }
                break;

            case "standard":
            case "deep":
                // Standard/Deep mode: Use Semgrep + Clang-Tidy
                for (Analyzer analyzer : analyzers) {
                    if (analyzer.getName().equals("Semgrep") ||
                        analyzer.getName().equals("Clang-Tidy")) {
                        selected.add(analyzer);
                        logger.info("{} mode: Using {}", level, analyzer.getName());
                    }
                }
                // Add regex as additional check in deep mode
                if (level.equals("deep")) {
                    for (Analyzer analyzer : analyzers) {
                        if (analyzer.getName().contains("Regex") &&
                            !selected.contains(analyzer)) {
                            selected.add(analyzer);
                            logger.info("Deep mode: Also using {}", analyzer.getName());
                        }
                    }
                }
                break;

            default:
                // Unknown level: use all available analyzers
                logger.warn("Unknown analysis level '{}', using all analyzers", level);
                selected.addAll(analyzers);
        }

        logger.info("Selected {} analyzer(s) for execution", selected.size());
        return selected;
    }

    /**
     * Sequential file analysis
     */
    private List<SecurityIssue> analyzeSequential(List<Path> files, List<Analyzer> selectedAnalyzers) {
        List<SecurityIssue> allIssues = new ArrayList<>();

        for (Analyzer analyzer : selectedAnalyzers) {
            logger.info("Running analyzer: {} (batch mode)", analyzer.getName());

            try {
                // Use batch analysis method for efficiency
                List<SecurityIssue> issues = analyzer.analyzeAll(files);
                allIssues.addAll(issues);
                logger.info("{} found {} issues", analyzer.getName(), issues.size());
            } catch (AnalyzerException e) {
                logger.error("Batch analysis failed for {}: {}", analyzer.getName(), e.getMessage());
            }
        }

        return allIssues;
    }

    /**
     * Parallel file analysis
     */
    private List<SecurityIssue> analyzeParallel(List<Path> files, List<Analyzer> selectedAnalyzers) {
        List<Future<List<SecurityIssue>>> futures = new ArrayList<>();

        // Submit analysis tasks for each analyzer (each analyzer runs in parallel)
        for (Analyzer analyzer : selectedAnalyzers) {
            logger.info("Running analyzer in parallel: {} (batch mode)", analyzer.getName());

            Future<List<SecurityIssue>> future = executorService.submit(() -> {
                try {
                    // Use batch analysis method for efficiency
                    List<SecurityIssue> issues = analyzer.analyzeAll(files);
                    logger.info("{} found {} issues", analyzer.getName(), issues.size());
                    return issues;
                } catch (AnalyzerException e) {
                    logger.error("Batch analysis failed for {}: {}", analyzer.getName(), e.getMessage());
                    return new ArrayList<>();
                }
            });

            futures.add(future);
        }

        // Collect results
        List<SecurityIssue> allIssues = new ArrayList<>();
        for (Future<List<SecurityIssue>> future : futures) {
            try {
                List<SecurityIssue> issues = future.get(config.getTimeout(), TimeUnit.SECONDS);
                allIssues.addAll(issues);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Failed to get analysis results", e);
            }
        }

        return allIssues;
    }

    /**
     * Deduplicate issues based on hash
     */
    private List<SecurityIssue> deduplicateIssues(List<SecurityIssue> issues) {
        Map<String, SecurityIssue> uniqueIssues = new LinkedHashMap<>();

        for (SecurityIssue issue : issues) {
            String hash = issue.getHash();
            if (!uniqueIssues.containsKey(hash)) {
                uniqueIssues.put(hash, issue);
            }
        }

        int duplicateCount = issues.size() - uniqueIssues.size();
        if (duplicateCount > 0) {
            logger.info("Removed {} duplicate issues", duplicateCount);
        }

        return new ArrayList<>(uniqueIssues.values());
    }

    /**
     * Create empty result (no files found)
     */
    private ScanResult createEmptyResult(Instant startTime) {
        return new ScanResult.Builder()
            .sourcePath(sourcePath)
            .startTime(startTime)
            .endTime(Instant.now())
            .addStatistic("total_files", 0)
            .addStatistic("total_issues", 0)
            .build();
    }

    /**
     * Shutdown executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get available analyzers
     */
    public List<Analyzer> getAnalyzers() {
        return Collections.unmodifiableList(analyzers);
    }

    /**
     * Analysis configuration
     */
    public static class AnalysisConfig {
        private final String level;
        private final boolean incremental;
        private final boolean parallel;
        private final int maxThreads;
        private final int timeout;
        private final String compileCommandsPath;
        private final boolean aiEnhancementEnabled;
        private final String outputPath;

        public AnalysisConfig(String level, boolean incremental, boolean parallel,
                             int maxThreads, int timeout) {
            this(level, incremental, parallel, maxThreads, timeout, null, true, null);
        }

        public AnalysisConfig(String level, boolean incremental, boolean parallel,
                             int maxThreads, int timeout, String compileCommandsPath) {
            this(level, incremental, parallel, maxThreads, timeout, compileCommandsPath, true, null);
        }

        public AnalysisConfig(String level, boolean incremental, boolean parallel,
                             int maxThreads, int timeout, String compileCommandsPath,
                             boolean aiEnhancementEnabled) {
            this(level, incremental, parallel, maxThreads, timeout, compileCommandsPath,
                aiEnhancementEnabled, null);
        }

        public AnalysisConfig(String level, boolean incremental, boolean parallel,
                             int maxThreads, int timeout, String compileCommandsPath,
                             boolean aiEnhancementEnabled, String outputPath) {
            this.level = level;
            this.incremental = incremental;
            this.parallel = parallel;
            this.maxThreads = maxThreads;
            this.timeout = timeout;
            this.compileCommandsPath = compileCommandsPath;
            this.aiEnhancementEnabled = aiEnhancementEnabled;
            this.outputPath = outputPath;
        }

        public static AnalysisConfig fromConfigManager() {
            return fromConfigManager(null);
        }

        public static AnalysisConfig fromConfigManager(String compileCommandsPath) {
            ConfigManager configManager = ConfigManager.getInstance();
            return new AnalysisConfig(
                configManager.getConfig().getAnalysis().getLevel(),
                configManager.getConfig().getAnalysis().isIncremental(),
                configManager.getConfig().getAnalysis().isParallel(),
                configManager.getConfig().getAnalysis().getMaxThreads(),
                configManager.getConfig().getAnalysis().getTimeout(),
                compileCommandsPath
            );
        }

        public String getLevel() {
            return level;
        }

        public boolean isIncremental() {
            return incremental;
        }

        public boolean isParallel() {
            return parallel;
        }

        public int getMaxThreads() {
            return maxThreads;
        }

        public int getTimeout() {
            return timeout;
        }

        public String getCompileCommandsPath() {
            return compileCommandsPath;
        }

        public boolean isAiEnhancementEnabled() {
            return aiEnhancementEnabled;
        }

        public String getOutputPath() {
            return outputPath;
        }
    }
}
