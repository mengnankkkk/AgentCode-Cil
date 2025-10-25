package com.harmony.agent.cli;

import com.harmony.agent.config.AppConfig;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.ai.CodeSlicer;
import com.harmony.agent.core.ai.RustMigrationAdvisor;
import com.harmony.agent.core.ai.SecuritySuggestionAdvisor;
import com.harmony.agent.core.model.IssueSeverity;
import com.harmony.agent.core.model.IssueCategory;
import com.harmony.agent.core.model.ScanResult;
import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.core.report.JsonReportWriter;
import com.harmony.agent.llm.provider.LLMProvider;
import com.harmony.agent.llm.provider.ProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Refactor command - generates code refactoring suggestions and Rust migration advice
 */
@Command(
    name = "refactor",
    description = "Generate code refactoring suggestions and Rust migration advice based on analysis report",
    mixinStandardHelpOptions = true
)
public class RefactorCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(RefactorCommand.class);

    @ParentCommand
    private HarmonyAgentCLI parent;

    @Parameters(
        index = "0",
        description = "Path to JSON report file or source directory"
    )
    private String sourcePath;

    @Option(
        names = {"-t", "--type"},
        description = "Refactor type: fix | rust-migration (default: fix)"
    )
    private String type = "fix";

    @Option(
        names = {"-o", "--output"},
        description = "Output directory for refactored code"
    )
    private String outputDir;

    @Option(
        names = {"-f", "--file"},
        description = "Source file for Rust migration analysis (required for rust-migration type)"
    )
    private String targetFile;

    @Option(
        names = {"-l", "--line"},
        description = "Line number in the file (required for rust-migration type)"
    )
    private Integer lineNumber;

    @Option(
        names = {"-n", "--number"},
        description = "Issue number to refactor (for fix type)"
    )
    private Integer issueNumber;

    @Option(
        names = {"--max"},
        description = "Maximum number of refactorings to generate (default: 5)"
    )
    private int maxRefactorings = 5;

    @Override
    public Integer call() {
        ConsolePrinter printer = parent.getPrinter();

        try {
            // Validate source path
            if (!Files.exists(Paths.get(sourcePath))) {
                printer.error("Source path does not exist: " + sourcePath);
                return 1;
            }

            printer.header("Code Refactoring Suggestions");
            printer.info("Source: " + sourcePath);
            printer.info("Type: " + type);
            printer.blank();

            if ("rust-migration".equals(type)) {
                return handleRustMigration(printer);
            } else {
                return handleCodeFix(printer);
            }

        } catch (Exception e) {
            printer.error("Refactoring failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private int handleCodeFix(ConsolePrinter printer) {
        try {
            // Validate sourcePath is a JSON report file
            Path reportPath = Paths.get(sourcePath);
            if (!Files.exists(reportPath)) {
                printer.error("Report file does not exist: " + sourcePath);
                printer.blank();
                printer.info("Please run /analyze first with -o option to generate a report:");
                printer.info("  harmony-agent analyze <path> -o report.html");
                printer.info("This will generate both report.html and report.json");
                return 1;
            }

            if (!Files.isRegularFile(reportPath)) {
                printer.error("Not a regular file: " + sourcePath);
                return 1;
            }

            // Load scan result from JSON report
            printer.blank();
            printer.header("Loading Analysis Report");
            printer.info("Report: " + sourcePath);
            printer.blank();

            JsonReportWriter jsonReader = new JsonReportWriter();
            ScanResult scanResult = jsonReader.read(reportPath);

            // Get all issues
            List<SecurityIssue> issues = scanResult.getIssues();

            if (issues.isEmpty()) {
                printer.success("No issues found in the report!");
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
                // Limit to maxRefactorings
                if (issues.size() > maxRefactorings) {
                    printer.warning("Too many issues (" + issues.size() + "). Limiting to " + maxRefactorings);
                    printer.info("Use -n <number> to refactor a specific issue");
                    issues = issues.subList(0, maxRefactorings);
                    printer.blank();
                }
            }

            // Setup LLM provider
            ConfigManager configManager = parent.getConfigManager();
            String providerName = configManager.getConfig().getAi().getProvider();
            String model = configManager.getConfig().getAi().getModel();

            // Check for command-level configuration
            AppConfig.CommandConfig commandConfig = configManager.getConfig().getAi().getCommands().get("refactor");
            if (commandConfig != null) {
                if (commandConfig.getProvider() != null) {
                    providerName = commandConfig.getProvider();
                }
                if (commandConfig.getModel() != null) {
                    model = commandConfig.getModel();
                }
            }

            // 解析模型别名（如 fast, standard, coder 等）
            model = resolveModelAlias(configManager, providerName, model);

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

            printer.header("Code Refactoring Suggestions");
            printer.info("Provider: " + provider.getProviderName());
            printer.info("Model: " + model);
            printer.info("Generating refactorings for " + issues.size() + " issues...");
            printer.blank();

            // Generate refactorings for each issue
            int successCount = 0;
            for (int i = 0; i < issues.size(); i++) {
                SecurityIssue issue = issues.get(i);
                int displayNumber = issueNumber != null ? issueNumber : i;

                printer.subheader("Issue #" + displayNumber + ": " + issue.getTitle());
                printer.keyValue("  Location", issue.getLocation().toString());
                printer.keyValue("  Severity", issue.getSeverity().getDisplayName());
                printer.keyValue("  Category", issue.getCategory().getDisplayName());
                printer.blank();

                // Generate refactoring
                printer.spinner("Generating refactoring suggestion...", false);

                Path sourceFilePath = Paths.get(scanResult.getSourcePath()).resolve(issue.getLocation().getFilePath());
                String suggestion = advisor.getFixSuggestion(issue, sourceFilePath);

                printer.spinner("Generating refactoring suggestion", true);
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

            printer.success("Generated " + successCount + " / " + issues.size() + " refactorings successfully");
            printer.blank();
            printer.info("💡 Tip: Use -n <number> to regenerate a specific refactoring");

            if (outputDir != null) {
                printer.warning("⚠️  Automatic code generation is in development");
                printer.info("Would write refactored code to: " + outputDir);
            }

            return 0;

        } catch (Exception e) {
            printer.error("Refactoring failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private int handleRustMigration(ConsolePrinter printer) {
        try {
            // 验证必需参数
            if (targetFile == null || lineNumber == null) {
                printer.error("Rust migration requires --file and --line options");
                printer.blank();
                printer.info("Usage:");
                printer.info("  harmony-agent refactor <source-path> --type rust-migration -f <file> -l <line>");
                printer.blank();
                printer.info("Example:");
                printer.info("  harmony-agent refactor /path/to/bzip2 --type rust-migration -f bzlib.c -l 234");
                return 1;
            }

            // 解析文件路径
            Path basePath = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path filePath;

            // 判断targetFile是相对路径还是绝对路径
            Path targetPath = Paths.get(targetFile);
            if (targetPath.isAbsolute()) {
                filePath = targetPath;
            } else {
                // 如果sourcePath是文件，使用其父目录；如果是目录，直接使用
                if (Files.isRegularFile(basePath)) {
                    Path parentDir = basePath.getParent();
                    if (parentDir == null) {
                        parentDir = Paths.get(".").toAbsolutePath().normalize();
                    }
                    filePath = parentDir.resolve(targetFile);
                } else {
                    filePath = basePath.resolve(targetFile);
                }
            }

            // 验证文件存在
            if (!Files.exists(filePath)) {
                printer.error("File not found: " + filePath);
                printer.info("Looking in: " + filePath.toAbsolutePath());
                return 1;
            }

            if (!Files.isRegularFile(filePath)) {
                printer.error("Not a regular file: " + filePath);
                return 1;
            }

            // 获取配置管理器
            ConfigManager configManager = parent.getConfigManager();

            // 获取命令配置（优先）或使用默认的 ai.model
            String providerName = configManager.getConfig().getAi().getProvider();
            String model = configManager.getConfig().getAi().getModel();

            // 检查是否有命令级别的配置
            AppConfig.CommandConfig commandConfig = configManager.getConfig().getAi().getCommands().get("refactor");
            if (commandConfig != null) {
                if (commandConfig.getProvider() != null) {
                    providerName = commandConfig.getProvider();
                }
                if (commandConfig.getModel() != null) {
                    model = commandConfig.getModel();
                }
            }

            // 解析模型别名（如 fast, standard, coder 等）
            model = resolveModelAlias(configManager, providerName, model);

            // 创建LLMProvider
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
                printer.info("Please configure API keys:");
                printer.info("  - Set OPENAI_API_KEY environment variable, or");
                printer.info("  - Set CLAUDE_API_KEY environment variable, or");
                printer.info("  - Set SILICONFLOW_API_KEY environment variable, or");
                printer.info("  - Configure in config.yaml");
                return 1;
            }

            if (!provider.isAvailable()) {
                printer.error("LLM provider not available: " + providerName);
                printer.blank();
                printer.info("Please configure API keys:");
                printer.info("  - OpenAI: Set OPENAI_API_KEY environment variable");
                printer.info("  - Claude: Set CLAUDE_API_KEY environment variable");
                printer.info("  - Or configure in config.yaml under ai.api_key");
                return 1;
            }

            // 创建RustMigrationAdvisor
            CodeSlicer codeSlicer = new CodeSlicer();
            RustMigrationAdvisor advisor = new RustMigrationAdvisor(provider, codeSlicer, model);

            // 显示分析开始
            printer.header("Rust Migration Analysis");
            printer.info("File: " + filePath.getFileName());
            printer.info("Line: " + lineNumber);
            printer.info("Provider: " + provider.getProviderName());
            printer.info("Model: " + model);
            printer.blank();

            // 生成建议
            printer.spinner("Analyzing C code and generating Rust migration advice...", false);
            String suggestion = advisor.getMigrationSuggestion(filePath, lineNumber);
            printer.spinner("Analysis complete", true);

            printer.blank();

            // 输出建议
            if (suggestion.startsWith("❌")) {
                printer.error(suggestion);
                return 1;
            } else {
                // 直接输出Markdown内容（不添加额外格式）
                System.out.println(suggestion);
                printer.blank();
                printer.success("Migration suggestion generated successfully");
                printer.blank();
                printer.info("💡 Tip: Copy the Rust code above and adapt it to your project");
                return 0;
            }

        } catch (Exception e) {
            printer.error("Failed to generate Rust migration suggestion: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /**
     * Resolve model alias (fast, standard, premium, coder) to actual model name
     */
    private String resolveModelAlias(ConfigManager configManager, String providerName, String modelAlias) {
        if (modelAlias == null || modelAlias.isEmpty()) {
            return modelAlias;
        }

        // Check if it's already a full model name (contains '/' or '-')
        if (modelAlias.contains("/") || modelAlias.contains("-")) {
            return modelAlias;
        }

        // Try to resolve from provider models configuration
        try {
            var providers = configManager.getConfig().getAi().getProviders();
            if (providers != null && providers.containsKey(providerName)) {
                var providerConfig = providers.get(providerName);
                var models = providerConfig.getModels();
                if (models != null && models.containsKey(modelAlias)) {
                    String resolvedModel = models.get(modelAlias);
                    logger.debug("Resolved model alias '{}' to '{}' for provider '{}'", 
                        modelAlias, resolvedModel, providerName);
                    return resolvedModel;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to resolve model alias '{}': {}", modelAlias, e.getMessage());
            // If resolution fails, return original alias
        }

        return modelAlias;
    }
}
