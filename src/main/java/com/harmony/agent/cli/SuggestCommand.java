package com.harmony.agent.cli;

import com.harmony.agent.config.AppConfig;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.ai.CodeSlicer;
import com.harmony.agent.core.ai.SecuritySuggestionAdvisor;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.ScanResult;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.report.JsonReportWriter;
import com.harmony.agent.llm.provider.LLMProvider;
import com.harmony.agent.llm.provider.ProviderFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Suggest command - provides AI-generated improvement suggestions based on analysis report
 */
@Command(
    name = "suggest",
    description = "Get AI-powered improvement suggestions for security issues from analysis report",
    mixinStandardHelpOptions = true
)
public class SuggestCommand implements Callable<Integer> {

    @ParentCommand
    private HarmonyAgentCLI parent;

    @Parameters(
        index = "0",
        description = "Path to JSON report file (e.g., bzip2-analysis-report.json)"
    )
    private String reportPath;

    @Option(
        names = {"-s", "--severity"},
        description = "Filter by severity: critical | high | medium | low"
    )
    private String severity;

    @Option(
        names = {"-c", "--category"},
        description = "Filter by category: memory | buffer | null | leak"
    )
    private String category;

    @Option(
        names = {"-n", "--number"},
        description = "Issue number to get suggestion for (e.g., 0, 1, 2...)"
    )
    private Integer issueNumber;

    @Option(
        names = {"--max"},
        description = "Maximum number of suggestions to generate (default: 5)"
    )
    private int maxSuggestions = 5;

    @Override
    public Integer call() {
        ConsolePrinter printer = parent.getPrinter();
        ConfigManager configManager = parent.getConfigManager();

        try {
            // Validate report path
            Path jsonPath = Paths.get(reportPath);
            if (!Files.exists(jsonPath)) {
                printer.error("Report file does not exist: " + reportPath);
                printer.blank();
                printer.info("Please run /analyze first with -o option to generate a report:");
                printer.info("  harmony-agent analyze <path> -o report.html");
                printer.info("This will generate both report.html and report.json");
                return 1;
            }

            if (!Files.isRegularFile(jsonPath)) {
                printer.error("Not a regular file: " + reportPath);
                return 1;
            }

            // Load scan result from JSON report
            printer.blank();
            printer.header("Loading Analysis Report");
            printer.info("Report: " + reportPath);
            printer.blank();

            JsonReportWriter jsonReader = new JsonReportWriter();
            ScanResult scanResult = jsonReader.read(jsonPath);

            // Filter issues if requested
            List<SecurityIssue> issues = scanResult.getIssues();

            if (severity != null) {
                IssueSeverity severityFilter = IssueSeverity.valueOf(severity.toUpperCase());
                issues = issues.stream()
                    .filter(issue -> issue.getSeverity() == severityFilter)
                    .collect(Collectors.toList());
                printer.info("Filtered by severity: " + severity);
            }

            if (category != null) {
                IssueCategory categoryFilter = IssueCategory.valueOf(category.toUpperCase());
                issues = issues.stream()
                    .filter(issue -> issue.getCategory() == categoryFilter)
                    .collect(Collectors.toList());
                printer.info("Filtered by category: " + category);
            }

            if (issues.isEmpty()) {
                printer.success("No issues found matching the filters!");
                return 0;
            }

            printer.info("Found " + issues.size() + " issues");
            printer.blank();

            // If specific issue number requested, only process that one
            if (issueNumber != null) {
                if (issueNumber < 0 || issueNumber >= issues.size()) {
                    printer.error("Invalid issue number: " + issueNumber);
                    printer.info("Valid range: 0 to " + (issues.size() - 1));
                    return 1;
                }
                issues = List.of(issues.get(issueNumber));
                printer.info("Processing issue #" + issueNumber);
                printer.blank();
            } else {
                // Limit to maxSuggestions
                if (issues.size() > maxSuggestions) {
                    printer.warning("Too many issues (" + issues.size() + "). Limiting to " + maxSuggestions);
                    printer.info("Use -n <number> to get suggestion for a specific issue");
                    issues = issues.subList(0, maxSuggestions);
                    printer.blank();
                }
            }

            // Setup LLM provider
            String providerName = configManager.getConfig().getAi().getProvider();
            String model = configManager.getConfig().getAi().getModel();

            // Check for command-level configuration
            AppConfig.CommandConfig commandConfig = configManager.getConfig().getAi().getCommands().get("suggest");
            if (commandConfig != null) {
                if (commandConfig.getProvider() != null) {
                    providerName = commandConfig.getProvider();
                }
                if (commandConfig.getModel() != null) {
                    model = commandConfig.getModel();
                }
            }

            // Create LLMProvider
            String openaiKey = System.getenv("OPENAI_API_KEY");
            if (openaiKey == null || openaiKey.isEmpty()) {
                openaiKey = configManager.getConfig().getAi().getApiKey();
            }

            String claudeKey = System.getenv("CLAUDE_API_KEY");
            String siliconflowKey = System.getenv("SILICONFLOW_API_KEY");
            ProviderFactory factory = ProviderFactory.createDefault(openaiKey, claudeKey, siliconflowKey);

            LLMProvider provider;
            try {
                provider = factory.getProvider(providerName);
            } catch (IllegalArgumentException e) {
                printer.error("LLM provider not configured: " + providerName);
                printer.blank();
                printer.info("Available providers: openai, claude, siliconflow");
                printer.info("Please configure API keys (set OPENAI_API_KEY, CLAUDE_API_KEY, or SILICONFLOW_API_KEY)");
                return 1;
            }

            if (!provider.isAvailable()) {
                printer.error("LLM provider not available: " + providerName);
                printer.blank();
                printer.info("Please set API key environment variable");
                return 1;
            }

            // Create SecuritySuggestionAdvisor
            CodeSlicer codeSlicer = new CodeSlicer();
            SecuritySuggestionAdvisor advisor = new SecuritySuggestionAdvisor(provider, codeSlicer, model);

            printer.header("AI Security Suggestions");
            printer.info("Provider: " + provider.getProviderName());
            printer.info("Model: " + model);
            printer.info("Generating suggestions for " + issues.size() + " issues...");
            printer.blank();

            // Generate suggestions for each issue
            int successCount = 0;
            for (int i = 0; i < issues.size(); i++) {
                SecurityIssue issue = issues.get(i);
                int displayNumber = issueNumber != null ? issueNumber : i;

                printer.subheader("Issue #" + displayNumber + ": " + issue.getTitle());
                printer.keyValue("  Location", issue.getLocation().toString());
                printer.keyValue("  Severity", issue.getSeverity().getDisplayName());
                printer.keyValue("  Category", issue.getCategory().getDisplayName());
                printer.blank();

                // Generate suggestion
                printer.spinner("Generating AI suggestion...", false);

                Path sourcePath = Paths.get(scanResult.getSourcePath()).resolve(issue.getLocation().getFilePath());
                String suggestion = advisor.getFixSuggestion(issue, sourcePath);

                printer.spinner("Generating AI suggestion", true);
                printer.blank();

                // Output suggestion
                if (suggestion.startsWith("❌")) {
                    printer.error(suggestion);
                } else {
                    System.out.println(suggestion);
                    successCount++;
                }

                printer.blank();
                printer.info("─".repeat(80));
                printer.blank();
            }

            printer.success("Generated " + successCount + " / " + issues.size() + " suggestions successfully");
            printer.blank();
            printer.info("💡 Tip: Use -n <number> to regenerate a specific suggestion");
            printer.info("💡 Tip: Use --severity critical to focus on critical issues");

            return 0;

        } catch (Exception e) {
            printer.error("Failed to generate suggestions: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
