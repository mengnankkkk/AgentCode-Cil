package com.harmony.agent.cli;

import com.harmony.agent.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Main CLI command for HarmonySafeAgent
 */
@Command(
    name = "harmony-agent",
    version = "HarmonySafeAgent 1.0.0",
    description = "OpenHarmony Security Analysis Tool - AI-powered code safety analyzer",
    mixinStandardHelpOptions = true,
    subcommands = {
        InteractiveCommand.class,
        AnalyzeCommand.class,
        SuggestCommand.class,
        RefactorCommand.class,
        ReportCommand.class,
        ConfigCommand.class,
        CacheStatsCommand.class  // ✨ P1 Optimization: Cache statistics
    }
)
public class HarmonyAgentCLI implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(HarmonyAgentCLI.class);

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    @Option(names = {"--no-color"}, description = "Disable colored output")
    private boolean noColor;

    private final ConsolePrinter printer;
    private final ConfigManager configManager;

    public HarmonyAgentCLI() {
        this.printer = new ConsolePrinter(!noColor);
        this.configManager = ConfigManager.getInstance();
    }

    @Override
    public Integer call() {
        // Show banner
        printBanner();

        // Start interactive mode by default
        printer.info("Starting interactive mode...");
        printer.info("Use --help for command-line usage, or type /help in interactive mode");
        printer.blank();

        // Create and execute interactive command with proper parent setup
        CommandLine rootCmd = new CommandLine(this);
        InteractiveCommand interactiveCmd = new InteractiveCommand();

        // Manually inject parent
        try {
            java.lang.reflect.Field parentField = InteractiveCommand.class.getDeclaredField("parent");
            parentField.setAccessible(true);
            parentField.set(interactiveCmd, this);
        } catch (Exception e) {
            logger.error("Failed to inject parent", e);
        }

        return interactiveCmd.call();
    }

    private void printBanner() {
        String banner = """

            ╔═══════════════════════════════════════════════════════╗
            ║                                                       ║
            ║   🛡️  HarmonySafeAgent v1.0.0                        ║
            ║   OpenHarmony Security Analysis Tool                 ║
            ║                                                       ║
            ║   AI-Powered Code Safety Analyzer                    ║
            ║                                                       ║
            ╚═══════════════════════════════════════════════════════╝

            """;
        System.out.println(banner);
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isColorEnabled() {
        return !noColor;
    }

    public ConsolePrinter getPrinter() {
        return printer;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
