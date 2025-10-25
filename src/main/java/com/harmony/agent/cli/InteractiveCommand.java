package com.harmony.agent.cli;

import com.harmony.agent.autofix.AutoFixOrchestrator;
import com.harmony.agent.autofix.ChangeManager;
import com.harmony.agent.autofix.CodeValidator;
import com.harmony.agent.autofix.DiffDisplay;
import com.harmony.agent.autofix.PendingChange;
import com.harmony.agent.autofix.AppliedChange;
import com.harmony.agent.cli.completion.CommandCompleter;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.llm.LLMClient;
import com.harmony.agent.task.TodoListManager;
import com.harmony.agent.tools.ToolExecutor;
import com.harmony.agent.tools.result.AnalysisResult;
import com.harmony.agent.tools.result.CompileResult;
import com.harmony.agent.tools.result.TestResult;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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

    // Dangerous system commands that should be blocked
    private static final Set<String> DANGEROUS_COMMANDS = Set.of(
        "rm -rf", "rm -r", "rm -fr",
        "format",
        "dd",
        "mkfs",
        "fdisk",
        "> /dev/sda", // Disk overwrites
        ":(){ :|:& };:", // Fork bomb
        "chmod -R 777",
        "chown -R"
    );

    // Dangerous command patterns (for Unix)
    private static final Set<String> DANGEROUS_UNIX_PATTERNS = Set.of(
        "sudo rm",
        "sudo dd",
        "sudo mkfs",
        "sudo fdisk",
        "sudo chmod",
        "sudo chown"
    );

    // Dangerous command patterns (for Windows)
    private static final Set<String> DANGEROUS_WINDOWS_PATTERNS = Set.of(
        "format c:",
        "format d:",
        "del /f /s /q",
        "rd /s /q c:",
        "rd /s /q d:"
    );

    @ParentCommand
    private HarmonyAgentCLI parent;

    private ConsolePrinter printer;
    private ConfigManager configManager;
    private Terminal terminal;
    private LineReader lineReader;
    private boolean running = true;
    private List<String> conversationHistory;
    private LLMClient llmClient;
    private TodoListManager todoListManager;
    private ToolExecutor toolExecutor;
    private AutoFixOrchestrator autoFixOrchestrator;  // NEW: Auto-fix orchestrator
    private ChangeManager changeManager;  // NEW: Change manager for /accept and /rollback
    private AnalysisResult lastAnalysisResult;  // NEW: Store last analyze result for /autofix

    // System command execution support
    private File currentWorkingDirectory;

    @Override
    public Integer call() {
        printer = parent.getPrinter();
        configManager = parent.getConfigManager();
        conversationHistory = new ArrayList<>();
        currentWorkingDirectory = new File(System.getProperty("user.dir"));

        try {
            // Initialize terminal and line reader
            terminal = TerminalBuilder.builder()
                .system(true)
                .build();

            // Configure parser to handle our special characters
            DefaultParser parser = new DefaultParser();
            parser.setEofOnUnclosedQuote(true);
            parser.setEofOnEscapedNewLine(true);

            // Configure command history
            DefaultHistory history = new DefaultHistory();

            // Set history file location (in user's home/.harmony-agent directory)
            File historyFile = getHistoryFile();
            history.attach(LineReaderBuilder.builder()
                .terminal(terminal)
                .build());

            // Get history size from configuration (default: 10)
            int historySize = configManager.getConfig().getOutput().getCommandHistorySize();

            lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new CommandCompleter(() -> currentWorkingDirectory))
                .parser(parser)
                .history(history)
                .variable(LineReader.HISTORY_FILE, historyFile.toPath())
                .variable(LineReader.HISTORY_SIZE, historySize)  // Keep last N commands
                .variable(LineReader.HISTORY_FILE_SIZE, historySize)  // Max N entries in file
                .option(LineReader.Option.CASE_INSENSITIVE, false)  // Keep case sensitive for system commands
                .option(LineReader.Option.AUTO_GROUP, false)        // Disable auto grouping
                .option(LineReader.Option.AUTO_MENU_LIST, true)     // Show menu list
                .option(LineReader.Option.INSERT_TAB, false)        // Tab triggers completion
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)  // Disable ! expansion
                .build();

            // Setup Ctrl+T key binding for viewing todolist
            setupKeyBindings();

            // Initialize LLM client
            llmClient = new LLMClient(configManager);

            // Initialize TodoListManager
            todoListManager = new TodoListManager(llmClient, printer);

            // Initialize ToolExecutor
            toolExecutor = new ToolExecutor(currentWorkingDirectory);

            // Initialize AutoFixOrchestrator and ChangeManager
            CodeValidator codeValidator = new CodeValidator(toolExecutor, currentWorkingDirectory);
            autoFixOrchestrator = new AutoFixOrchestrator(llmClient, codeValidator);
            changeManager = new ChangeManager();

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
     * Get history file location
     * Creates ~/.harmony-agent/history if it doesn't exist
     */
    private File getHistoryFile() {
        String userHome = System.getProperty("user.home");
        File harmonyDir = new File(userHome, ".harmony-agent");

        // Create directory if it doesn't exist
        if (!harmonyDir.exists()) {
            harmonyDir.mkdirs();
        }

        return new File(harmonyDir, "history");
    }

    /**
     * Setup custom key bindings
     */
    private void setupKeyBindings() {
        // Create a widget to handle Ctrl+T
        lineReader.getWidgets().put("show-tasks", () -> {
            // Display todolist when Ctrl+T is pressed
            if (todoListManager.hasActiveTodoList()) {
                terminal.writer().println();
                todoListManager.displayFullTodoList();
                lineReader.callWidget(LineReader.REDRAW_LINE);
                lineReader.callWidget(LineReader.REDISPLAY);
            } else {
                terminal.writer().println();
                printer.info("No active task plan. Use /plan <requirement> to create one.");
                lineReader.callWidget(LineReader.REDRAW_LINE);
                lineReader.callWidget(LineReader.REDISPLAY);
            }
            return true;
        });

        // Bind Ctrl+T to the widget
        lineReader.getKeyMaps().get(LineReader.MAIN).bind(
            new Reference("show-tasks"),
            KeyMap.ctrl('T')
        );
    }

    /**
     * Show welcome message
     */
    private void showWelcome() {
        printer.blank();
        printer.header("HarmonySafeAgent Interactive Mode");
        printer.blank();
        printer.info("Welcome! You can:");
        printer.info("  • 🚀 AI Workflow: /start <path> - Complete security workflow (NEW!)");
        printer.info("  • Plan tasks: /plan <requirement> - AI-powered task breakdown");
        printer.info("  • Execute tasks: /next - Intelligent role routing");
        printer.info("  • View tasks: /tasks or Ctrl+T - See all tasks");
        printer.info("  • Build tools: /compile, /test, /spotbugs - Development tools");
        printer.info("  • System commands: $ <command> - Execute shell commands");
        printer.info("  • Use commands: /analyze, /suggest, /help, /exit");
        printer.info("  • Chat naturally: Ask questions about security, code, etc.");
        printer.info("  • Command history: ↑/↓ to navigate previous commands");
        printer.blank();

        // Show LLM architecture status
        printer.keyValue("AI Model", configManager.getConfig().getAi().getModel());
        if (llmClient.isUsingRealLLM()) {
            printer.keyValue("LLM Mode", "✨ Orchestrator (Multi-Role Architecture)");
            printer.keyValue("Status", "\u001B[32mActive\u001B[0m - Using real LLM providers");
        } else {
            printer.keyValue("LLM Mode", "⚠️  Fallback (Rule-based)");
            printer.keyValue("Status", "\u001B[33mFallback\u001B[0m - Set API keys for AI features");
            printer.blank();
            printer.info("💡 Tip: Set OPENAI_API_KEY or CLAUDE_API_KEY for AI-powered features");
        }
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
        } else if (input.startsWith("$")) {
            // System commands (shell commands)
            handleSystemCommand(input.substring(1).trim());
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

            case "plan":
                handlePlanCommand(args);
                break;

            case "next":
            case "execute":
                handleExecuteCommand();
                break;

            case "tasks":
                handleTasksCommand();
                break;

            case "current":
                handleCurrentTaskCommand();
                break;

            case "start":
                handleStartCommand(args);
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

            case "compile":
                handleCompileCommand(args);
                break;

            case "test":
                handleTestCommand(args);
                break;

            case "spotbugs":
                handleSpotBugsCommand(args);
                break;

            case "autofix":
                handleAutoFixCommand(args);
                break;

            case "accept":
                handleAcceptCommand();
                break;

            case "discard":
                handleDiscardCommand();
                break;

            case "rollback":
                handleRollbackCommand();
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
     * Handle system commands ($ prefix commands)
     */
    private void handleSystemCommand(String command) {
        if (command.isEmpty()) {
            printer.warning("Empty system command. Example: $ ls -la");
            return;
        }

        // Extract command name (first word)
        String cmdName = command.split("\\s+")[0].toLowerCase();

        // Handle special commands that need preprocessing
        switch (cmdName) {
            case "cd":
                handleCdCommand(command);
                break;

            case "pwd":
                handlePwdCommand();
                break;

            case "ls":
                // Translate ls to appropriate command for platform
                handleLsCommand(command);
                break;

            case "cat":
            case "head":
            case "tail":
                // These work on both platforms (mostly)
                executeSystemCommand(command);
                break;

            default:
                // Execute generic system command
                executeSystemCommand(command);
                break;
        }
    }

    /**
     * Handle ls command with cross-platform support
     * Windows users can use $ ls and it works like dir
     */
    private void handleLsCommand(String command) {
        if (isWindows()) {
            // On Windows, translate ls to dir
            String dirCommand;
            if (command.trim().equals("ls")) {
                // Simple ls -> dir
                dirCommand = "dir";
            } else if (command.contains(" -")) {
                // ls with flags -> attempt translation
                String args = command.substring(2).trim();

                // Translate common flags
                if (args.contains("-la") || args.contains("-al")) {
                    dirCommand = "dir /a";
                } else if (args.contains("-l")) {
                    dirCommand = "dir";
                } else if (args.contains("-a")) {
                    dirCommand = "dir /a";
                } else {
                    // Keep any path argument
                    String cleanArgs = args.replaceAll("-[a-zA-Z]+", "").trim();
                    dirCommand = cleanArgs.isEmpty() ? "dir" : "dir " + cleanArgs;
                }
            } else {
                // ls with path argument
                String path = command.substring(2).trim();
                dirCommand = "dir " + path;
            }

            executeSystemCommand(dirCommand);
        } else {
            // Unix/Linux/Mac - use ls directly
            executeSystemCommand(command);
        }
    }

    /**
     * Handle /plan command - create a task breakdown from requirement
     */
    private void handlePlanCommand(String args) {
        if (args.isEmpty()) {
            printer.error("Usage: /plan <requirement>");
            printer.info("Example: /plan Analyze src/main for security issues");
            return;
        }

        // Check if there's already an active todo list
        if (todoListManager.hasActiveTodoList()) {
            printer.warning("There's already an active task plan.");
            printer.info("Current progress: " + todoListManager.getProgressSummary());
            printer.info("Use /next to continue, or /tasks to view all tasks");
            return;
        }

        // Create new todo list
        todoListManager.createTodoList(args);
    }

    /**
     * Handle /next or /execute command - execute current task
     */
    private void handleExecuteCommand() {
        if (!todoListManager.hasActiveTodoList()) {
            printer.warning("No active task plan. Use /plan <requirement> to create one.");
            return;
        }

        todoListManager.executeCurrentTask();
    }

    /**
     * Handle /tasks command - show full todo list
     */
    private void handleTasksCommand() {
        if (!todoListManager.hasActiveTodoList()) {
            printer.warning("No active task plan. Use /plan <requirement> to create one.");
            return;
        }

        todoListManager.displayFullTodoList();
    }

    /**
     * Handle /current command - show current task only
     */
    private void handleCurrentTaskCommand() {
        if (!todoListManager.hasActiveTodoList()) {
            printer.warning("No active task plan. Use /plan <requirement> to create one.");
            return;
        }

        todoListManager.displayCurrentTask();
    }

    /**
     * Handle /start command - Complete AI Agent workflow
     * Phase 1: Deep Analysis & Intelligent Evaluation
     * Phase 2: Human-AI Collaborative Decision
     * Phase 3: High-Quality Security Evolution
     * Phase 4: Review, Acceptance & Feedback Loop
     */
    private void handleStartCommand(String args) {
        if (args.isEmpty()) {
            printer.error("Usage: /start <path>");
            printer.info("Example: /start src/main");
            printer.blank();
            printer.info("This command initiates a complete AI-powered security workflow:");
            printer.info("  1. Deep Analysis & Intelligent Evaluation");
            printer.info("  2. Human-AI Collaborative Decision");
            printer.info("  3. High-Quality Security Evolution");
            printer.info("  4. Review, Acceptance & Feedback Loop");
            return;
        }

        try {
            // Create StartWorkflowCommand instance
            StartWorkflowCommand workflowCmd = new StartWorkflowCommand(
                printer,
                configManager,
                llmClient,
                currentWorkingDirectory
            );

            // Execute the workflow
            int exitCode = workflowCmd.execute(args.trim());

            // Show completion message
            printer.blank();
            if (exitCode == 0) {
                printer.success("✅ Workflow completed successfully!");
            } else if (exitCode == 2) {
                printer.warning("⚠️  Workflow completed with critical issues detected");
            } else {
                printer.error("❌ Workflow failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            printer.error("Workflow execution failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle /analyze command - Enhanced with Strategic Analysis
     */
    private void handleAnalyzeCommand(String args) {
        if (args.isEmpty()) {
            printer.error("Usage: /analyze <path> [options]");
            printer.info("Options:");
            printer.info("  -l, --level <level>           Analysis level: quick | standard | deep");
            printer.info("  -o, --output <file>          Output HTML report file");
            printer.info("  --compile-commands <file>    Path to compile_commands.json");
            printer.info("  --incremental                Enable incremental analysis");
            printer.info("  --no-ai                      Disable AI-enhanced analysis");
            printer.info("  --strategic                  Enable strategic analysis (NEW!)");
            printer.blank();
            printer.info("Examples:");
            printer.info("  /analyze src/main -l quick -o report.html");
            printer.info("  /analyze src/main --strategic    # Strategic analysis with scoring & triage");
            return;
        }

        // Check if strategic analysis is requested
        if (args.contains("--strategic")) {
            handleStrategicAnalyzeCommand(args.replace("--strategic", "").trim());
            return;
        }

        try {
            // Create AnalyzeCommand instance
            AnalyzeCommand analyzeCmd = new AnalyzeCommand();

            // Parse arguments using picocli
            String[] argArray = parseCommandLineArgs(args);
            picocli.CommandLine cmd = new picocli.CommandLine(analyzeCmd);

            // Set parent for printer and config access
            java.lang.reflect.Field parentField = AnalyzeCommand.class.getDeclaredField("parent");
            parentField.setAccessible(true);
            parentField.set(analyzeCmd, parent);

            // Parse and execute
            int exitCode = cmd.execute(argArray);

            // Show completion message
            printer.blank();
            if (exitCode == 0) {
                printer.success("✅ Analysis completed successfully - no critical issues found!");
            } else if (exitCode == 2) {
                printer.warning("⚠️  Analysis completed - critical issues detected!");
                printer.info("Review the results above or check the HTML report if generated");
            } else {
                printer.error("❌ Analysis failed with exit code: " + exitCode);
            }

        } catch (picocli.CommandLine.ParameterException e) {
            printer.error("Invalid arguments: " + e.getMessage());
            printer.info("Type /analyze without arguments for usage help");
        } catch (Exception e) {
            printer.error("Analysis failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle strategic analysis command
     */
    private void handleStrategicAnalyzeCommand(String args) {
        if (args.trim().isEmpty()) {
            printer.error("Usage: /analyze <path> --strategic");
            printer.info("Example: /analyze src/main --strategic");
            return;
        }

        try {
            // Create StrategicAnalysisCommand instance
            StrategicAnalysisCommand strategicCmd = new StrategicAnalysisCommand();

            // Parse arguments using picocli
            String[] argArray = parseCommandLineArgs(args);
            picocli.CommandLine cmd = new picocli.CommandLine(strategicCmd);

            // Set parent for printer and config access
            java.lang.reflect.Field parentField = StrategicAnalysisCommand.class.getDeclaredField("parent");
            parentField.setAccessible(true);
            parentField.set(strategicCmd, parent);

            // Parse and execute
            int exitCode = cmd.execute(argArray);

            // Show completion message
            printer.blank();
            if (exitCode == 0) {
                printer.success("✅ Strategic analysis completed successfully!");
            } else {
                printer.error("❌ Strategic analysis failed with exit code: " + exitCode);
            }

        } catch (picocli.CommandLine.ParameterException e) {
            printer.error("Invalid arguments: " + e.getMessage());
            printer.info("Type /analyze <path> --strategic for strategic analysis");
        } catch (Exception e) {
            printer.error("Strategic analysis failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Parse command line arguments from a string
     * Handles quoted arguments properly
     */
    private String[] parseCommandLineArgs(String argsString) {
        List<String> args = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < argsString.length(); i++) {
            char c = argsString.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args.toArray(new String[0]);
    }

    /**
     * Handle /suggest command
     */
    private void handleSuggestCommand(String args) {
        if (args.isEmpty()) {
            printer.error("Usage: /suggest <path> [options]");
            printer.info("Options:");
            printer.info("  -s, --severity <level>   Filter by severity: critical | high | medium | low");
            printer.info("  -c, --category <type>    Filter by category: memory | buffer | null | leak");
            printer.info("  --code-fix               Include code fix examples (default: true)");
            printer.blank();
            printer.info("Example: /suggest src/main/bzlib.c -s critical --code-fix");
            return;
        }

        try {
            // Create SuggestCommand instance
            SuggestCommand suggestCmd = new SuggestCommand();

            // Parse arguments using picocli
            String[] argArray = parseCommandLineArgs(args);
            picocli.CommandLine cmd = new picocli.CommandLine(suggestCmd);

            // Set parent for printer and config access
            java.lang.reflect.Field parentField = SuggestCommand.class.getDeclaredField("parent");
            parentField.setAccessible(true);
            parentField.set(suggestCmd, parent);

            // Parse and execute
            int exitCode = cmd.execute(argArray);

            // Show completion message
            printer.blank();
            if (exitCode == 0) {
                printer.success("✅ Suggestions generated successfully!");
            } else {
                printer.error("❌ Failed to generate suggestions with exit code: " + exitCode);
            }

        } catch (picocli.CommandLine.ParameterException e) {
            printer.error("Invalid arguments: " + e.getMessage());
            printer.info("Type /suggest without arguments for usage help");
        } catch (Exception e) {
            printer.error("Suggestion generation failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle /refactor command
     */
    private void handleRefactorCommand(String args) {
        if (args.isEmpty()) {
            printer.error("Usage: /refactor <path> [options]");
            printer.info("Options:");
            printer.info("  -t, --type <type>        Refactor type: fix | rust-migration (default: fix)");
            printer.info("  -o, --output <dir>       Output directory for refactored code");
            printer.info("  -f, --file <file>        Source file for Rust migration (required for rust-migration)");
            printer.info("  -l, --line <number>      Line number for Rust migration (required for rust-migration)");
            printer.blank();
            printer.info("Examples:");
            printer.info("  /refactor src/main -t fix -o output");
            printer.info("  /refactor src/main -t rust-migration -f bzlib.c -l 234");
            return;
        }

        try {
            // Create RefactorCommand instance
            RefactorCommand refactorCmd = new RefactorCommand();

            // Parse arguments using picocli
            String[] argArray = parseCommandLineArgs(args);
            picocli.CommandLine cmd = new picocli.CommandLine(refactorCmd);

            // Set parent for printer and config access
            java.lang.reflect.Field parentField = RefactorCommand.class.getDeclaredField("parent");
            parentField.setAccessible(true);
            parentField.set(refactorCmd, parent);

            // Parse and execute
            int exitCode = cmd.execute(argArray);

            // Show completion message
            printer.blank();
            if (exitCode == 0) {
                printer.success("✅ Refactoring completed successfully!");
            } else {
                printer.error("❌ Refactoring failed with exit code: " + exitCode);
            }

        } catch (picocli.CommandLine.ParameterException e) {
            printer.error("Invalid arguments: " + e.getMessage());
            printer.info("Type /refactor without arguments for usage help");
        } catch (Exception e) {
            printer.error("Refactoring failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle /compile command - Maven compilation
     */
    private void handleCompileCommand(String args) {
        printer.blank();
        printer.header("Running Maven Compilation");
        printer.info("Project: " + currentWorkingDirectory.getAbsolutePath());

        boolean cleanFirst = args.contains("clean") || args.contains("-c");

        try {
            printer.spinner("Compiling...", false);
            CompileResult result = toolExecutor.compileMaven(cleanFirst);
            printer.spinner("Compiling", true);
            printer.blank();

            if (result.isSuccess()) {
                printer.success("✓ Compilation successful");
                printer.keyValue("  Duration", result.getDurationMs() + "ms");
                printer.keyValue("  Exit Code", String.valueOf(result.getExitCode()));
            } else {
                printer.error("✗ Compilation failed");
                printer.keyValue("  Duration", result.getDurationMs() + "ms");
                printer.keyValue("  Exit Code", String.valueOf(result.getExitCode()));
                printer.keyValue("  Errors Found", String.valueOf(result.getErrorCount()));

                if (result.hasErrors()) {
                    printer.blank();
                    printer.subheader("Compilation Errors:");
                    for (int i = 0; i < Math.min(10, result.getErrors().size()); i++) {
                        CompileResult.CompileError error = result.getErrors().get(i);
                        printer.error("  " + error.toString());
                    }
                    if (result.getErrors().size() > 10) {
                        printer.info("  ... and " + (result.getErrors().size() - 10) + " more errors");
                    }
                }
            }

            if (parent.isVerbose()) {
                printer.blank();
                printer.subheader("Full Output:");
                System.out.println(result.getOutput());
            }

        } catch (Exception e) {
            printer.error("Compilation failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
        printer.blank();
    }

    /**
     * Handle /test command - JUnit tests
     */
    private void handleTestCommand(String args) {
        printer.blank();
        printer.header("Running JUnit Tests");
        printer.info("Project: " + currentWorkingDirectory.getAbsolutePath());

        String testPattern = args.trim().isEmpty() ? null : args.trim();
        if (testPattern != null) {
            printer.info("Test Pattern: " + testPattern);
        }

        try {
            printer.spinner("Running tests...", false);
            TestResult result = toolExecutor.runTests(testPattern);
            printer.spinner("Running tests", true);
            printer.blank();

            if (result.isSuccess()) {
                printer.success("✓ All tests passed");
            } else {
                printer.error("✗ Some tests failed");
            }

            printer.keyValue("  Tests Run", String.valueOf(result.getTestsRun()));
            printer.keyValue("  Passed", String.valueOf(result.getTestsPassed()));
            printer.keyValue("  Failed", String.valueOf(result.getTestsFailed()));
            printer.keyValue("  Skipped", String.valueOf(result.getTestsSkipped()));
            printer.keyValue("  Duration", result.getDurationMs() + "ms");

            if (result.hasFailures()) {
                printer.blank();
                printer.subheader("Test Failures:");
                for (int i = 0; i < Math.min(5, result.getFailures().size()); i++) {
                    TestResult.TestFailure failure = result.getFailures().get(i);
                    printer.error("  " + failure.toString());
                }
                if (result.getFailures().size() > 5) {
                    printer.info("  ... and " + (result.getFailures().size() - 5) + " more failures");
                }
            }

            if (parent.isVerbose()) {
                printer.blank();
                printer.subheader("Full Output:");
                System.out.println(result.getOutput());
            }

        } catch (Exception e) {
            printer.error("Test execution failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
        printer.blank();
    }

    /**
     * Handle /spotbugs command - SpotBugs static analysis
     */
    private void handleSpotBugsCommand(String args) {
        printer.blank();
        printer.header("Running SpotBugs Static Analysis");
        printer.info("Project: " + currentWorkingDirectory.getAbsolutePath());

        try {
            printer.spinner("Analyzing...", false);
            AnalysisResult result = toolExecutor.analyzeWithSpotBugs(null);
            printer.spinner("Analyzing", true);
            printer.blank();

            // Store result for /autofix command
            lastAnalysisResult = result;

            if (result.isSuccess()) {
                printer.success("✓ No bugs found");
            } else {
                printer.warning("⚠ Bugs detected");
            }

            printer.keyValue("  Total Bugs", String.valueOf(result.getBugCount()));
            printer.keyValue("  High Priority", String.valueOf(result.getHighPriorityCount()));
            printer.keyValue("  Medium Priority", String.valueOf(result.getMediumPriorityCount()));
            printer.keyValue("  Low Priority", String.valueOf(result.getLowPriorityCount()));
            printer.keyValue("  Duration", result.getDurationMs() + "ms");

            if (result.hasBugs()) {
                printer.blank();
                printer.subheader("Bugs Found:");
                for (int i = 0; i < Math.min(10, result.getBugs().size()); i++) {
                    AnalysisResult.Bug bug = result.getBugs().get(i);
                    printer.warning(String.format("  [%d] %s", i, bug.toString()));
                }
                if (result.getBugs().size() > 10) {
                    printer.info("  ... and " + (result.getBugs().size() - 10) + " more bugs");
                }
                printer.blank();
                printer.info("💡 Tip: Use /autofix <issue_number> to automatically fix an issue");
            }

            if (parent.isVerbose()) {
                printer.blank();
                printer.subheader("Full Output:");
                System.out.println(result.getOutput());
            }

        } catch (Exception e) {
            printer.error("Static analysis failed: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
        printer.blank();
    }

    /**
     * Handle /autofix command - generate a fix for a security issue
     */
    private void handleAutoFixCommand(String args) {
        if (args.isEmpty()) {
            printer.error("Usage: /autofix <issue_id> or /autofix <file>:<line>");
            printer.info("Example: /autofix issue_123 or /autofix src/main.c:45");
            printer.info("Run /analyze first to discover issues");
            return;
        }

        if (lastAnalysisResult == null || lastAnalysisResult.getBugs().isEmpty()) {
            printer.warning("No issues found. Run /spotbugs first to discover issues.");
            return;
        }

        try {
            // Try to find the issue
            AnalysisResult.Bug targetBug = null;

            // Case 1: Issue ID (like "issue_123" or just the number)
            if (args.matches("\\d+") || args.startsWith("issue_")) {
                int issueIndex = args.startsWith("issue_")
                    ? Integer.parseInt(args.substring(6))
                    : Integer.parseInt(args);

                if (issueIndex >= 0 && issueIndex < lastAnalysisResult.getBugs().size()) {
                    targetBug = lastAnalysisResult.getBugs().get(issueIndex);
                }
            }
            // Case 2: file:line format
            else if (args.contains(":")) {
                String[] parts = args.split(":");
                if (parts.length == 2) {
                    String file = parts[0];
                    int line = Integer.parseInt(parts[1]);

                    for (AnalysisResult.Bug bug : lastAnalysisResult.getBugs()) {
                        if (bug.getFile().endsWith(file) && bug.getLine() == line) {
                            targetBug = bug;
                            break;
                        }
                    }
                }
            }

            if (targetBug == null) {
                printer.error("Issue not found: " + args);
                printer.info("Available issues:");
                for (int i = 0; i < Math.min(5, lastAnalysisResult.getBugs().size()); i++) {
                    AnalysisResult.Bug bug = lastAnalysisResult.getBugs().get(i);
                    printer.info(String.format("  [%d] %s:%d - %s", i, bug.getFile(), bug.getLine(), bug.getMessage()));
                }
                return;
            }

            // Convert Bug to SecurityIssue (placeholder - needs proper conversion)
            printer.info("Found issue: " + targetBug.getMessage());
            printer.warning("Auto-fix integration coming soon!");
            printer.info("Will generate fix for: " + targetBug.getFile() + ":" + targetBug.getLine());

            // TODO: Convert AnalysisResult.Bug to SecurityIssue and call autoFixOrchestrator.generateFix()

        } catch (Exception e) {
            printer.error("Failed to process autofix request: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle /accept command - accept and apply the pending change
     */
    private void handleAcceptCommand() {
        if (!changeManager.hasPendingChange()) {
            printer.warning("No pending change to accept");
            printer.info("Use /autofix to generate a fix first");
            return;
        }

        try {
            PendingChange pending = changeManager.getPendingChange().get();

            printer.blank();
            printer.spinner("Applying change...", false);

            AppliedChange applied = changeManager.acceptPendingChange();

            printer.spinner("Applying change", true);
            printer.blank();

            printer.success("✅ Change accepted and applied!");
            printer.keyValue("  File", applied.getFilePath().toString());
            printer.keyValue("  Change ID", applied.getId());
            printer.blank();

            printer.info("💡 Tip: Use /rollback to undo this change if needed");

        } catch (IOException e) {
            printer.error("Failed to apply change: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle /discard command - discard the pending change
     */
    private void handleDiscardCommand() {
        if (!changeManager.hasPendingChange()) {
            printer.warning("No pending change to discard");
            return;
        }

        PendingChange pending = changeManager.getPendingChange().get();
        changeManager.discardPendingChange();

        printer.blank();
        printer.info("❌ Pending change discarded: " + pending.getSummary());
        printer.blank();
    }

    /**
     * Handle /rollback command - rollback the last accepted change
     */
    private void handleRollbackCommand() {
        if (!changeManager.canRollback()) {
            printer.warning("No changes to rollback");
            printer.info("History is empty - no changes have been accepted yet");
            return;
        }

        try {
            printer.blank();
            printer.spinner("Rolling back last change...", false);

            AppliedChange rolledBack = changeManager.rollbackLastChange();

            printer.spinner("Rolling back last change", true);
            printer.blank();

            printer.success("✅ Change rolled back successfully!");
            printer.keyValue("  File", rolledBack.getFilePath().toString());
            printer.keyValue("  Change ID", rolledBack.getId());
            printer.blank();

            printer.info(String.format("📊 Remaining changes in history: %d", changeManager.getHistorySize()));

        } catch (IOException e) {
            printer.error("Failed to rollback change: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Show available commands
     */
    private void showHelp() {
        printer.blank();
        printer.subheader("Available Commands");
        printer.blank();

        printer.subheader("Task Planning");
        printer.keyValue("  /plan <requirement>", "Break down requirement into tasks");
        printer.keyValue("  /next", "Execute current task");
        printer.keyValue("  /tasks", "Show all tasks (or press Ctrl+T)");
        printer.keyValue("  /current", "Show current task only");
        printer.blank();

        printer.subheader("AI Workflow (NEW!)");
        printer.keyValue("  /start <path>", "Start complete AI-powered security workflow");
        printer.info("  💡 Includes: Deep Analysis → Decision → Evolution → Review");
        printer.blank();

        printer.subheader("Analysis & Tools");
        printer.keyValue("  /analyze <path>", "Analyze code for security issues");
        printer.keyValue("  /analyze <path> --strategic", "Strategic analysis with scoring & triage");
        printer.keyValue("  /suggest [file]", "Get AI suggestions for fixes");
        printer.keyValue("  /refactor [file]", "Get refactoring recommendations");
        printer.info("  💡 Strategic analysis includes T1.1 Security Scoring + T1.2 Triage Advisor");
        printer.blank();

        printer.subheader("Auto-Fix (NEW!)");
        printer.keyValue("  /autofix <issue>", "Generate automatic fix for security issue");
        printer.keyValue("  /accept", "Accept and apply pending change");
        printer.keyValue("  /discard", "Discard pending change");
        printer.keyValue("  /rollback", "Undo last accepted change");
        printer.info("  💡 Changes are staged in memory - review before /accept");
        printer.blank();

        printer.subheader("Build & Test Tools (NEW!)");
        printer.keyValue("  /compile [clean]", "Run Maven compilation");
        printer.keyValue("  /test [pattern]", "Run JUnit tests");
        printer.keyValue("  /spotbugs", "Run SpotBugs static analysis");
        printer.info("  Examples: /compile, /compile clean, /test UserTest");
        printer.blank();

        printer.subheader("System Commands (NEW!)");
        printer.keyValue("  $ <command>", "Execute system/shell commands");
        printer.keyValue("  $ pwd", "Print current working directory");
        printer.keyValue("  $ cd <path>", "Change directory (supports ~, ./, ../)");
        printer.keyValue("  $ ls -la", "List files (Unix) or $ dir (Windows)");
        printer.keyValue("  $ cat file.txt", "View file contents");
        printer.keyValue("  $ echo \"text\"", "Print text to console");
        printer.info("  Examples: $ pwd, $ cd src, $ ls -la, $ cat README.md");
        printer.warning("  ⚠️  Dangerous commands (rm -rf, format, etc.) are blocked");
        printer.blank();

        printer.subheader("General");
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

        printer.subheader("Quick Tips");
        printer.info("  • Press Ctrl+T to view full task list");
        printer.info("  • Press Ctrl+C to cancel current input");
        printer.info("  • Press Ctrl+D to exit");
        printer.info("  • Use $ prefix for system commands (NEW!)");
        printer.info("  • Use ↑ and ↓ arrow keys to navigate command history");
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

        // Basic config
        printer.keyValue("  LLM Model", configManager.getConfig().getAi().getModel());
        printer.keyValue("  API Provider", configManager.getConfig().getAi().getProvider());
        printer.keyValue("  Analysis Level", configManager.getConfig().getAnalysis().getLevel());
        printer.keyValue("  Parallel", String.valueOf(configManager.getConfig().getAnalysis().isParallel()));
        printer.blank();

        // LLM Architecture status
        printer.subheader("LLM Architecture");
        printer.blank();
        if (llmClient.isUsingRealLLM()) {
            printer.keyValue("  Architecture", "Dual-Strategy (Provider + Role)");
            printer.keyValue("  Analyzer Role", "OpenAI GPT-3.5-turbo");
            printer.keyValue("  Planner Role", "Claude 3 Sonnet");
            printer.keyValue("  Coder Role", "Claude 3 Sonnet");
            printer.keyValue("  Reviewer Role", "Claude 3 Opus");
            printer.keyValue("  Status", "\u001B[32mActive\u001B[0m");
        } else {
            printer.keyValue("  Architecture", "Fallback Mode");
            printer.keyValue("  Status", "\u001B[33mNo API Keys\u001B[0m");
            printer.info("  Set OPENAI_API_KEY or CLAUDE_API_KEY to enable");
        }
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

    // ========================================
    // System Command Support Methods
    // ========================================

    /**
     * Handle cd command - change directory
     */
    private void handleCdCommand(String command) {
        // Extract path from command (everything after "cd")
        String path = command.substring(2).trim();

        if (path.isEmpty()) {
            // cd with no arguments - go to home directory
            path = "~";
        }

        try {
            File newDir = resolveDirectory(path);

            // Validate directory exists and is a directory
            if (!newDir.exists()) {
                printer.error("Directory not found: " + path);
                return;
            }

            if (!newDir.isDirectory()) {
                printer.error("Not a directory: " + path);
                return;
            }

            // Update current working directory
            currentWorkingDirectory = newDir.getCanonicalFile();
            printer.success("Changed directory to: " + currentWorkingDirectory.getAbsolutePath());

        } catch (Exception e) {
            printer.error("Failed to change directory: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handle pwd command - print working directory
     */
    private void handlePwdCommand() {
        printer.info(currentWorkingDirectory.getAbsolutePath());
    }

    /**
     * Execute generic system command via ProcessBuilder
     */
    private void executeSystemCommand(String command) {
        // Security check - block dangerous commands
        if (!isSafeCommand(command)) {
            return; // Error already displayed by isSafeCommand()
        }

        try {
            // Build command for platform
            String[] shellCommand = getShellCommand(command);

            // Create process builder
            ProcessBuilder pb = new ProcessBuilder(shellCommand);
            pb.directory(currentWorkingDirectory);
            pb.redirectErrorStream(true); // Merge stderr into stdout

            // Execute command
            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for completion
            int exitCode = process.waitFor();

            // Display output
            if (output.length() > 0) {
                // Remove trailing newline
                String result = output.toString().trim();
                System.out.println(result);
            }

            // Show exit code if non-zero
            if (exitCode != 0 && parent.isVerbose()) {
                printer.warning("Command exited with code: " + exitCode);
            }

        } catch (java.io.IOException e) {
            printer.error("Failed to execute command: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        } catch (InterruptedException e) {
            printer.error("Command execution interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            printer.error("Unexpected error: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get shell command for current platform
     */
    private String[] getShellCommand(String command) {
        if (isWindows()) {
            return new String[]{"cmd", "/c", command};
        } else {
            return new String[]{"sh", "-c", command};
        }
    }

    /**
     * Resolve directory path (supports relative, absolute, and ~ expansion)
     *
     * @param path The path to resolve
     * @return Resolved File object
     */
    private File resolveDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return currentWorkingDirectory;
        }

        path = path.trim();

        // Handle ~ (home directory)
        if (path.startsWith("~")) {
            String home = System.getProperty("user.home");
            path = home + path.substring(1);
        }

        File file = new File(path);

        // If absolute path, use directly
        if (file.isAbsolute()) {
            return file;
        }

        // Relative path - resolve against current working directory
        return new File(currentWorkingDirectory, path);
    }

    /**
     * Check if running on Windows
     *
     * @return true if Windows, false otherwise
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * Check if a command is safe to execute
     *
     * @param command The command to check
     * @return true if safe, false if dangerous
     */
    private boolean isSafeCommand(String command) {
        String cmdLower = command.toLowerCase().trim();

        // Check general dangerous commands
        for (String dangerous : DANGEROUS_COMMANDS) {
            if (cmdLower.contains(dangerous.toLowerCase())) {
                printer.error("⚠️  Dangerous command blocked: " + dangerous);
                printer.warning("This command could cause system damage");
                return false;
            }
        }

        // Check platform-specific dangerous patterns
        Set<String> platformPatterns = isWindows()
            ? DANGEROUS_WINDOWS_PATTERNS
            : DANGEROUS_UNIX_PATTERNS;

        for (String dangerous : platformPatterns) {
            if (cmdLower.contains(dangerous.toLowerCase())) {
                printer.error("⚠️  Dangerous command blocked: " + dangerous);
                printer.warning("This command could cause system damage");
                return false;
            }
        }

        return true;
    }
}
