package com.harmony.agent.cli;

import com.harmony.agent.cli.completion.CommandCompleter;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.llm.LLMClient;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Interactive REPL mode - similar to Claude Code
 * Allows both slash commands (/analyze, /suggest) and natural language chat
 */
@Command(
    name = "interactive",
    aliases = {"i", "repl", "chat"},
    description = "Start interactive mode (REPL) for analysis and chat",
    mixinStandardHelpOptions = true
)
public class InteractiveCommand implements Callable<Integer> {

    @ParentCommand
    private HarmonyAgentCLI parent;

    private ConsolePrinter printer;
    private ConfigManager configManager;
    private Terminal terminal;
    private LineReader lineReader;
    private boolean running = true;
    private List<String> conversationHistory;
    private LLMClient llmClient;

    @Override
    public Integer call() {
        printer = parent.getPrinter();
        configManager = parent.getConfigManager();
        conversationHistory = new ArrayList<>();

        try {
            // Initialize terminal and line reader
            terminal = TerminalBuilder.builder()
                .system(true)
                .build();

            lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new CommandCompleter())
                .parser(new DefaultParser())
                .history(new DefaultHistory())
                .option(LineReader.Option.CASE_INSENSITIVE, true)
                .option(LineReader.Option.AUTO_GROUP, true)
                .option(LineReader.Option.AUTO_MENU_LIST, true)
                .option(LineReader.Option.INSERT_TAB, false)
                .build();

            // Initialize LLM client
            llmClient = new LLMClient(configManager);

            // Show welcome message
            showWelcome();

            // Start REPL loop
            while (running) {
                String input = readInput();

                if (input == null) {
                    // Ctrl+D was pressed
                    printer.success("Goodbye!");
                    break;
                }

                if (input.trim().isEmpty()) {
                    continue;
                }

                // Process input
                processInput(input.trim());
            }

            return 0;

        } catch (Exception e) {
            printer.error("Interactive mode failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        } finally {
            // Close terminal
            if (terminal != null) {
                try {
                    terminal.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Show welcome message
     */
    private void showWelcome() {
        printer.blank();
        printer.header("HarmonySafeAgent Interactive Mode");
        printer.blank();
        printer.info("Welcome! You can:");
        printer.info("  • Use slash commands: /analyze, /suggest, /help, /exit");
        printer.info("  • Chat naturally: Ask questions about security, code analysis, etc.");
        printer.blank();
        printer.keyValue("AI Model", configManager.getConfig().getAi().getModel());
        printer.keyValue("Mode", "Interactive REPL");
        printer.blank();
    }

    /**
     * Read user input with prompt and tab completion
     */
    private String readInput() {
        try {
            return lineReader.readLine("\n\u001B[1;36m❯\u001B[0m ");
        } catch (UserInterruptException e) {
            // Ctrl+C pressed
            return "";
        } catch (EndOfFileException e) {
            // Ctrl+D pressed
            return null;
        } catch (Exception e) {
            printer.error("Failed to read input: " + e.getMessage());
            return null;
        }
    }

    /**
     * Process user input - route to command or chat
     */
    private void processInput(String input) {
        // Check for slash commands
        if (input.startsWith("/")) {
            handleSlashCommand(input);
        } else {
            // Natural language chat
            handleChat(input);
        }
    }

    /**
     * Handle slash commands
     */
    private void handleSlashCommand(String input) {
        String[] parts = input.substring(1).split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "exit":
            case "quit":
            case "q":
                printer.success("Goodbye!");
                running = false;
                break;

            case "help":
            case "h":
                showHelp();
                break;

            case "clear":
            case "cls":
                clearScreen();
                break;

            case "history":
                showHistory();
                break;

            case "analyze":
                handleAnalyzeCommand(args);
                break;

            case "suggest":
                handleSuggestCommand(args);
                break;

            case "refactor":
                handleRefactorCommand(args);
                break;

            case "config":
                showConfig();
                break;

            default:
                printer.error("Unknown command: /" + command);
                printer.info("Type /help for available commands");
        }
    }

    /**
     * Handle natural language chat
     */
    private void handleChat(String input) {
        try {
            // Add to conversation history
            conversationHistory.add("User: " + input);

            // Show thinking indicator
            printer.blank();
            printer.spinner("Thinking...", false);

            // Get AI response
            String response = llmClient.chat(input, conversationHistory);

            printer.spinner("Thinking", true);
            printer.blank();

            // Display response
            printer.info("AI: " + response);

            // Add to history
            conversationHistory.add("AI: " + response);

        } catch (Exception e) {
            printer.error("Chat failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle /analyze command
     */
    private void handleAnalyzeCommand(String args) {
        if (args.isEmpty()) {
            printer.error("Usage: /analyze <path>");
            return;
        }

        try {
            // Create and execute AnalyzeCommand
            AnalyzeCommand analyzeCmd = new AnalyzeCommand();
            // Set parent and sourcePath via reflection or directly
            // For now, print message
            printer.info("Running analysis on: " + args);
            printer.info("(Full integration coming in Phase 3)");

        } catch (Exception e) {
            printer.error("Analysis failed: " + e.getMessage());
        }
    }

    /**
     * Handle /suggest command
     */
    private void handleSuggestCommand(String args) {
        printer.info("Suggestion feature coming in Phase 3");
    }

    /**
     * Handle /refactor command
     */
    private void handleRefactorCommand(String args) {
        printer.info("Refactor feature coming in Phase 4");
    }

    /**
     * Show available commands
     */
    private void showHelp() {
        printer.blank();
        printer.subheader("Available Commands");
        printer.blank();

        printer.keyValue("  /analyze <path>", "Analyze code for security issues");
        printer.keyValue("  /suggest [file]", "Get AI suggestions for fixes");
        printer.keyValue("  /refactor [file]", "Get refactoring recommendations");
        printer.keyValue("  /config", "Show current configuration");
        printer.keyValue("  /history", "Show conversation history");
        printer.keyValue("  /clear", "Clear screen");
        printer.keyValue("  /help", "Show this help");
        printer.keyValue("  /exit", "Exit interactive mode");
        printer.blank();

        printer.subheader("Chat Mode");
        printer.info("  Just type naturally to chat with AI about security");
        printer.info("  Example: 'What are common buffer overflow patterns?'");
        printer.blank();
    }

    /**
     * Show conversation history
     */
    private void showHistory() {
        printer.blank();
        printer.subheader("Conversation History");
        printer.blank();

        if (conversationHistory.isEmpty()) {
            printer.info("No conversation history yet");
        } else {
            for (int i = 0; i < conversationHistory.size(); i++) {
                printer.info(String.format("[%d] %s", i + 1, conversationHistory.get(i)));
            }
        }
        printer.blank();
    }

    /**
     * Show current configuration
     */
    private void showConfig() {
        printer.blank();
        printer.subheader("Current Configuration");
        printer.blank();

        printer.keyValue("  LLM Model", configManager.getConfig().getAi().getModel());
        printer.keyValue("  API Provider", configManager.getConfig().getAi().getProvider());
        printer.keyValue("  Analysis Level", configManager.getConfig().getAnalysis().getLevel());
        printer.keyValue("  Parallel", String.valueOf(configManager.getConfig().getAnalysis().isParallel()));
        printer.blank();
    }

    /**
     * Clear screen (platform-independent)
     */
    private void clearScreen() {
        try {
            final String os = System.getProperty("os.name");

            if (os.contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                // ANSI escape code
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }

            showWelcome();
        } catch (Exception e) {
            // Fallback: print blank lines
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
    }
}
