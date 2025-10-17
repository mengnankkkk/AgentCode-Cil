package com.harmony.agent.cli;

import com.harmony.agent.config.AppConfig;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.AnalysisEngine;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.ScanResult;
import com.harmony.agent.core.model.SecurityIssue;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Analyze command - performs security analysis on source code
 */
@Command(
    name = "analyze",
    description = "Analyze source code for security issues",
    mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {

    @ParentCommand
    private HarmonyAgentCLI parent;

    @Parameters(
        index = "0",
        description = "Source code path to analyze"
    )
    private String sourcePath;

    @Option(
        names = {"-l", "--level"},
        description = "Analysis level: quick | standard | deep (default: standard)"
    )
    private String level;

    @Option(
        names = {"-o", "--output"},
        description = "Output file path for report"
    )
    private String outputFile;

    @Option(
        names = {"--incremental"},
        description = "Enable incremental analysis (only analyze changed files)"
    )
    private boolean incremental;

    @Option(
        names = {"--no-ai"},
        description = "Disable AI-enhanced analysis (use static analysis only)"
    )
    private boolean noAi;

    @Override
    public Integer call() {
        ConsolePrinter printer = parent.getPrinter();
        ConfigManager configManager = parent.getConfigManager();

        try {
            // Validate source path
            Path path = Paths.get(sourcePath);
            if (!Files.exists(path)) {
                printer.error("Source path does not exist: " + sourcePath);
                return 1;
            }

            // Get configuration
            AppConfig config = configManager.getConfig();

            // Override level if specified
            if (level != null) {
                config.getAnalysis().setLevel(level);
            }

            // Override incremental if specified
            if (incremental) {
                config.getAnalysis().setIncremental(true);
            }

            // Show analysis start
            printer.header("Security Analysis");
            printer.info("Analyzing: " + sourcePath);
            printer.info("Level: " + config.getAnalysis().getLevel());
            printer.info("Incremental: " + (incremental ? "enabled" : "disabled"));
            printer.info("Parallel: " + (config.getAnalysis().isParallel() ? "enabled" : "disabled"));
            printer.blank();

            // Create analysis engine
            AnalysisEngine.AnalysisConfig analysisConfig = AnalysisEngine.AnalysisConfig.fromConfigManager();
            AnalysisEngine engine = new AnalysisEngine(sourcePath, analysisConfig);

            try {
                // Show available analyzers
                List<String> analyzerNames = engine.getAnalyzers().stream()
                    .map(analyzer -> analyzer.getName())
                    .collect(Collectors.toList());

                printer.info("Using analyzers: " + String.join(", ", analyzerNames));

                // Show note about external analyzers
                boolean hasExternalAnalyzer = analyzerNames.stream()
                    .anyMatch(name -> !name.equals("RegexAnalyzer"));

                if (!hasExternalAnalyzer) {
                    printer.warning("Using built-in analyzer only");
                    printer.info("For better results, install external analyzers:");
                    printer.info("  - Clang-Tidy: apt-get install clang-tidy (or brew install llvm)");
                    printer.info("  - Semgrep: pip install semgrep");
                }

                printer.blank();

                // Run analysis
                printer.spinner("Scanning source files...", false);
                ScanResult result = engine.analyze();
                printer.spinner("Scanning source files", true);

                int fileCount = (int) result.getStatistics().getOrDefault("total_files", 0);
                printer.info("Analyzed " + fileCount + " source files");
                printer.blank();

                // Show summary
                printer.header("Analysis Summary");
                printer.blank();

                // Show overall statistics
                int totalIssues = result.getTotalIssueCount();
                printer.stats("Total Issues", totalIssues, org.fusesource.jansi.Ansi.Color.CYAN);

                // Show severity breakdown
                Map<IssueSeverity, Long> severityCounts = result.getIssueCountBySeverity();
                printer.stats("Critical", severityCounts.getOrDefault(IssueSeverity.CRITICAL, 0L).intValue(),
                    org.fusesource.jansi.Ansi.Color.RED);
                printer.stats("High", severityCounts.getOrDefault(IssueSeverity.HIGH, 0L).intValue(),
                    org.fusesource.jansi.Ansi.Color.RED);
                printer.stats("Medium", severityCounts.getOrDefault(IssueSeverity.MEDIUM, 0L).intValue(),
                    org.fusesource.jansi.Ansi.Color.YELLOW);
                printer.stats("Low", severityCounts.getOrDefault(IssueSeverity.LOW, 0L).intValue(),
                    org.fusesource.jansi.Ansi.Color.GREEN);
                printer.blank();

                // Show category breakdown
                if (totalIssues > 0) {
                    printer.subheader("Issue Categories");
                    Map<IssueCategory, Long> categoryCounts = result.getIssueCountByCategory();

                    categoryCounts.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .limit(10)
                        .forEach(entry -> {
                            String categoryName = entry.getKey().getDisplayName();
                            long count = entry.getValue();
                            printer.keyValue("  " + categoryName, count + " issues");
                        });
                    printer.blank();

                    // Show sample issues
                    printer.subheader("Critical Issues");
                    List<SecurityIssue> criticalIssues = result.getIssuesBySeverity(IssueSeverity.CRITICAL);

                    if (criticalIssues.isEmpty()) {
                        printer.success("  No critical issues found!");
                    } else {
                        for (int i = 0; i < Math.min(5, criticalIssues.size()); i++) {
                            SecurityIssue issue = criticalIssues.get(i);
                            printer.info("  " + issue.getLocation().toString());
                            printer.keyValue("    " + issue.getTitle(), issue.getCategory().getDisplayName());
                        }

                        if (criticalIssues.size() > 5) {
                            printer.info("  ... and " + (criticalIssues.size() - 5) + " more");
                        }
                    }
                    printer.blank();
                }

                // Show performance stats
                printer.subheader("Performance");
                printer.keyValue("  Analysis Time", String.format("%.2f seconds", result.getDuration().toMillis() / 1000.0));
                printer.keyValue("  Files Analyzed", String.valueOf(fileCount));
                printer.keyValue("  Analyzers Used", String.join(", ", result.getAnalyzersUsed()));
                printer.blank();

                // Success message
                if (result.hasCriticalIssues()) {
                    printer.warning("Analysis complete with critical issues!");
                } else {
                    printer.success("Analysis complete!");
                }

                if (!noAi) {
                    printer.info("Use 'suggest' command for AI-enhanced recommendations (Phase 3)");
                }

                if (outputFile != null) {
                    printer.info("Report will be saved to: " + outputFile + " (Phase 5)");
                }

                return result.hasCriticalIssues() ? 2 : 0;

            } finally {
                engine.shutdown();
            }

        } catch (Exception e) {
            printer.error("Analysis failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
