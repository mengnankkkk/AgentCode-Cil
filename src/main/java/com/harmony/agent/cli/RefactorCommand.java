package com.harmony.agent.cli;

import com.harmony.agent.config.AppConfig;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.core.ai.CodeSlicer;
import com.harmony.agent.core.ai.RustMigrationAdvisor;
import com.harmony.agent.llm.provider.LLMProvider;
import com.harmony.agent.llm.provider.ProviderFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Refactor command - generates code refactoring suggestions and Rust migration advice
 */
@Command(
    name = "refactor",
    description = "Generate code refactoring suggestions and Rust migration advice",
    mixinStandardHelpOptions = true
)
public class RefactorCommand implements Callable<Integer> {

    @ParentCommand
    private HarmonyAgentCLI parent;

    @Parameters(
        index = "0",
        description = "Source file or directory"
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

    private int handleCodeFix(ConsolePrinter printer) throws InterruptedException {
        // TODO: Phase 4 - Implement actual code fix generation
        printer.spinner("Analyzing code patterns...", false);
        Thread.sleep(1000);
        printer.spinner("Analyzing code patterns", true);

        printer.blank();
        printer.subheader("Suggested Fixes");

        printer.info("1. bzlib.c:234 - Replace strcpy with strncpy");
        printer.info("2. bzlib.c:456 - Add memory cleanup in error path");
        printer.info("3. bzlib.c:789 - Add null pointer check");

        printer.blank();
        printer.warning("⚠️  Automatic code generation is in development");
        printer.info("Currently showing suggestions only");

        if (outputDir != null) {
            printer.info("Would write refactored code to: " + outputDir);
        }

        return 0;
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
            Path basePath = Paths.get(sourcePath);
            Path filePath;

            // 判断targetFile是相对路径还是绝对路径
            Path targetPath = Paths.get(targetFile);
            if (targetPath.isAbsolute()) {
                filePath = targetPath;
            } else {
                // 如果sourcePath是文件，使用其父目录
                if (Files.isRegularFile(basePath)) {
                    filePath = basePath.getParent().resolve(targetFile);
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
}
