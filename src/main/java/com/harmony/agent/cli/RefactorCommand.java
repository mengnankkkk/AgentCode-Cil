package com.harmony.agent.cli;

import com.harmony.agent.config.AppConfig;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.ai.CodeSlicer;
import com.harmony.agent.core.ai.RustCodeGenerator;
import com.harmony.agent.core.ai.RustCodeValidator;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            // 解析基础路径（必须是目录）
            Path basePath = Paths.get(sourcePath).toAbsolutePath().normalize();

            if (!Files.exists(basePath)) {
                printer.error("Source path does not exist: " + sourcePath);
                return 1;
            }

            if (!Files.isDirectory(basePath)) {
                printer.error("Source path must be a directory for batch conversion: " + sourcePath);
                printer.info("Usage: harmony-agent refactor <source-directory> --type rust-migration -o <output-dir>");
                return 1;
            }

            // 验证输出目录
            if (outputDir == null) {
                printer.error("Output directory is required for Rust migration");
                printer.blank();
                printer.info("Usage:");
                printer.info("  harmony-agent refactor <source-directory> --type rust-migration -o <output-dir>");
                printer.blank();
                printer.info("Example:");
                printer.info("  harmony-agent refactor /path/to/bzip2 --type rust-migration -o /path/to/bzip2-rust");
                return 1;
            }

            Path outputPath = Paths.get(outputDir).toAbsolutePath().normalize();

            // 创建输出目录（如果不存在）
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
                printer.info("Created output directory: " + outputPath);
            }

            // 1. 扫描所有 C/C++ 文件
            printer.blank();
            printer.header("Scanning for C/C++ Files");
            printer.info("Source directory: " + basePath);
            printer.blank();

            List<Path> cFiles = scanCppFiles(basePath);

            if (cFiles.isEmpty()) {
                printer.warning("No C/C++ files found in: " + basePath);
                return 0;
            }

            printer.success("Found " + cFiles.size() + " C/C++ file(s):");
            for (int i = 0; i < cFiles.size(); i++) {
                printer.info("  [" + (i + 1) + "] " + basePath.relativize(cFiles.get(i)));
            }
            printer.blank();

            // 2. 设置 LLM 提供者
            ConfigManager configManager = parent.getConfigManager();
            String providerName = configManager.getConfig().getAi().getProvider();
            String model = configManager.getConfig().getAi().getModel();

            // 检查命令级别配置
            AppConfig.CommandConfig commandConfig = configManager.getConfig().getAi().getCommands().get("refactor");
            if (commandConfig != null) {
                if (commandConfig.getProvider() != null) {
                    providerName = commandConfig.getProvider();
                }
                if (commandConfig.getModel() != null) {
                    model = commandConfig.getModel();
                }
            }

            // 解析模型别名
            model = resolveModelAlias(configManager, providerName, model);

            // 创建 LLMProvider
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
                printer.info("Please set API keys (OPENAI_API_KEY, CLAUDE_API_KEY, or SILICONFLOW_API_KEY)");
                return 1;
            }

            if (!provider.isAvailable()) {
                printer.error("LLM provider not available: " + providerName);
                printer.info("Please set API key environment variable");
                return 1;
            }

            // 创建 RustCodeGenerator
            RustCodeGenerator rustGenerator = new RustCodeGenerator(provider, model);

            // 创建 RustCodeValidator
            RustCodeValidator rustValidator = new RustCodeValidator();

            // 检查 Rust 工具链是否可用
            boolean rustcAvailable = rustValidator.isRustcAvailable();
            boolean clippyAvailable = rustValidator.isClippyAvailable();

            if (!rustcAvailable) {
                printer.warning("⚠️  rustc not found - code validation will be skipped");
                printer.info("Install Rust: https://rustup.rs/");
                printer.blank();
            }

            if (!clippyAvailable) {
                printer.warning("⚠️  clippy not found - style checking will be skipped");
                printer.info("Install clippy: rustup component add clippy");
                printer.blank();
            }

            printer.header("Rust Code Generation");
            printer.info("Provider: " + provider.getProviderName());
            printer.info("Model: " + model);
            printer.info("Output directory: " + outputPath);
            printer.info("Validation: " + (rustcAvailable ? "✓ rustc" : "✗ rustc") +
                         ", " + (clippyAvailable ? "✓ clippy" : "✗ clippy"));
            printer.blank();

            // 3. 逐文件处理循环
            int successCount = 0;
            int skippedCount = 0;
            List<Path> convertedFiles = new ArrayList<>();

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            for (int i = 0; i < cFiles.size(); i++) {
                Path cFile = cFiles.get(i);
                Path relativePath = basePath.relativize(cFile);

                printer.subheader("File " + (i + 1) + " / " + cFiles.size() + ": " + relativePath);
                printer.blank();

                // ===== [G] Generate: 生成 Rust 代码 =====
                printer.info("🔄 [G] Generating Rust code...");
                String rustCode = rustGenerator.generateRustCode(cFile);

                // 检查是否生成成功
                if (rustCode.startsWith("// ERROR:")) {
                    printer.error("Failed to generate Rust code:");
                    printer.error(rustCode);
                    printer.blank();

                    // 询问是否继续
                    printer.warning("Continue with next file? (y/n): ");
                    String response = reader.readLine().trim().toLowerCase();
                    if (!response.equals("y") && !response.equals("yes")) {
                        printer.info("Conversion aborted by user");
                        break;
                    }
                    continue;
                }

                printer.success("✓ Code generated (" + rustCode.split("\n").length + " lines)");
                printer.blank();

                // ===== [V] + [I] Verify & Iterate: 验证和迭代修复 =====
                int maxIterations = 3;
                int iteration = 0;
                boolean validated = false;

                if (rustcAvailable) {
                    // 创建临时文件用于验证
                    Path tempFile = Files.createTempFile("rust_validation_", ".rs");

                    try {
                        while (iteration < maxIterations && !validated) {
                            iteration++;

                            if (iteration > 1) {
                                printer.info("🔄 [I] Iteration " + iteration + ": Attempting to fix errors...");
                            } else {
                                printer.info("🔍 [V] Validating with rustc...");
                            }

                            // 验证编译
                            RustCodeValidator.ValidationResult rustcResult =
                                rustValidator.validateWithRustc(rustCode, tempFile);

                            if (rustcResult.isSuccess()) {
                                printer.success("✓ rustc validation passed");
                                validated = true;

                                // 如果 clippy 可用，也进行检查
                                if (clippyAvailable) {
                                    printer.info("🔍 Running clippy checks...");
                                    RustCodeValidator.ValidationResult clippyResult =
                                        rustValidator.validateWithClippy(rustCode, tempFile);

                                    if (clippyResult.hasWarnings()) {
                                        printer.warning("⚠️  Clippy found " + clippyResult.getWarningCount() + " warning(s)");

                                        // 显示前 5 个警告
                                        List<String> warnings = clippyResult.getWarnings();
                                        for (int w = 0; w < Math.min(5, warnings.size()); w++) {
                                            printer.warning("  " + warnings.get(w));
                                        }

                                        if (warnings.size() > 5) {
                                            printer.info("  ... and " + (warnings.size() - 5) + " more warnings");
                                        }

                                        // 尝试修复 clippy 警告
                                        if (iteration < maxIterations) {
                                            printer.info("🔄 Attempting to fix clippy warnings...");
                                            String fixedCode = rustGenerator.fixCompilationErrors(
                                                rustCode, "", clippyResult.getFullOutput());

                                            if (!fixedCode.startsWith("// ERROR:")) {
                                                rustCode = fixedCode;
                                                validated = false; // 重新验证
                                                continue;
                                            }
                                        }
                                    } else {
                                        printer.success("✓ Clippy checks passed");
                                    }
                                }

                                break;
                            } else {
                                // 编译失败
                                printer.error("✗ rustc validation failed (" +
                                    rustcResult.getErrorCount() + " error(s))");

                                // 显示前 5 个错误
                                List<String> errors = rustcResult.getErrors();
                                for (int e = 0; e < Math.min(5, errors.size()); e++) {
                                    printer.error("  " + errors.get(e));
                                }

                                if (errors.size() > 5) {
                                    printer.info("  ... and " + (errors.size() - 5) + " more errors");
                                }
                                printer.blank();

                                // 如果还有迭代机会，尝试修复
                                if (iteration < maxIterations) {
                                    String fixedCode = rustGenerator.fixCompilationErrors(
                                        rustCode,
                                        rustcResult.getFullOutput(),
                                        null);

                                    if (fixedCode.startsWith("// ERROR:")) {
                                        printer.error("Failed to fix errors: " + fixedCode);
                                        break;
                                    }

                                    rustCode = fixedCode;
                                    printer.success("✓ Fixed code generated, re-validating...");
                                    printer.blank();
                                } else {
                                    printer.warning("⚠️  Maximum iterations reached, code may have errors");
                                    break;
                                }
                            }
                        }
                    } finally {
                        // 清理临时文件
                        rustValidator.cleanupTempFile(tempFile);
                    }

                    printer.blank();

                    if (validated) {
                        printer.success("✓ Code quality: VALIDATED (iteration " + iteration + ")");
                    } else {
                        printer.warning("⚠️  Code quality: NOT VALIDATED (may have compilation errors)");
                    }
                } else {
                    printer.warning("⚠️  Validation skipped (rustc not available)");
                }

                printer.blank();

                // ===== [U] User Confirmation: 用户确认 =====
                // 显示生成的代码（前30行）
                displayCodePreview(printer, rustCode, 30);
                printer.blank();

                // 用户确认
                boolean accepted = false;
                while (true) {
                    printer.info("Options: [a]ccept / [s]kip / [v]iew full / [q]uit: ");
                    String choice = reader.readLine().trim().toLowerCase();

                    if (choice.equals("a") || choice.equals("accept")) {
                        accepted = true;
                        break;
                    } else if (choice.equals("s") || choice.equals("skip")) {
                        accepted = false;
                        break;
                    } else if (choice.equals("v") || choice.equals("view")) {
                        printer.blank();
                        System.out.println(rustCode);
                        printer.blank();
                        continue;
                    } else if (choice.equals("q") || choice.equals("quit")) {
                        printer.info("Conversion aborted by user");
                        printer.blank();
                        printSummary(printer, successCount, skippedCount, cFiles.size());
                        return 0;
                    } else {
                        printer.warning("Invalid choice. Please enter a, s, v, or q");
                        continue;
                    }
                }

                if (accepted) {
                    // 写入文件
                    Path rustFileName = Paths.get(relativePath.toString().replaceAll("\\.(c|cpp|h|hpp)$", ".rs"));
                    Path rustFilePath = outputPath.resolve(rustFileName);

                    // 创建父目录（如果需要）
                    Files.createDirectories(rustFilePath.getParent());

                    // 写入 Rust 代码
                    Files.writeString(rustFilePath, rustCode, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                    printer.success("✓ Saved: " + rustFilePath);
                    printer.blank();

                    convertedFiles.add(rustFilePath);
                    successCount++;
                } else {
                    printer.info("✗ Skipped: " + relativePath);
                    printer.blank();
                    skippedCount++;
                }
            }

            // 打印总结
            printSummary(printer, successCount, skippedCount, cFiles.size());

            // 生成 Cargo.toml 如果有文件被转换
            if (successCount > 0) {
                printer.blank();
                printer.info("💡 Tip: Generate Cargo.toml to build the Rust project:");
                printer.info("    cd " + outputPath);
                printer.info("    cargo init --name " + basePath.getFileName());
            }

            return 0;

        } catch (IOException e) {
            printer.error("I/O error: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        } catch (Exception e) {
            printer.error("Failed to perform Rust migration: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /**
     * 扫描目录中所有的 C/C++ 文件
     */
    private List<Path> scanCppFiles(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".c") || name.endsWith(".cpp") ||
                           name.endsWith(".h") || name.endsWith(".hpp");
                })
                .sorted()
                .collect(Collectors.toList());
        }
    }

    /**
     * 显示代码预览（前 N 行）
     */
    private void displayCodePreview(ConsolePrinter printer, String code, int maxLines) {
        String[] lines = code.split("\n");
        int displayLines = Math.min(lines.length, maxLines);

        printer.info("Generated Rust code preview (showing " + displayLines + " / " + lines.length + " lines):");
        printer.blank();

        for (int i = 0; i < displayLines; i++) {
            System.out.println(lines[i]);
        }

        if (lines.length > maxLines) {
            printer.blank();
            printer.info("... (" + (lines.length - maxLines) + " more lines)");
        }
    }

    /**
     * 打印转换总结
     */
    private void printSummary(ConsolePrinter printer, int success, int skipped, int total) {
        printer.header("Conversion Summary");
        printer.blank();
        printer.keyValue("  Total files", String.valueOf(total));
        printer.keyValue("  Converted", String.valueOf(success));
        printer.keyValue("  Skipped", String.valueOf(skipped));
        printer.keyValue("  Remaining", String.valueOf(total - success - skipped));
        printer.blank();

        if (success > 0) {
            printer.success("✓ Successfully converted " + success + " file(s) to Rust!");
        } else {
            printer.warning("No files were converted");
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
