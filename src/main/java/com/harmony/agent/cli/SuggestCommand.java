package com.harmony.agent.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Suggest command - provides AI-generated improvement suggestions
 */
@Command(
    name = "suggest",
    description = "Get AI-powered improvement suggestions for security issues",
    mixinStandardHelpOptions = true
)
public class SuggestCommand implements Callable<Integer> {

    @ParentCommand
    private HarmonyAgentCLI parent;

    @Parameters(
        index = "0",
        description = "Source file or directory"
    )
    private String sourcePath;

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
        names = {"--code-fix"},
        description = "Include code fix examples"
    )
    private boolean codeFix = true;

    @Override
    public Integer call() {
        ConsolePrinter printer = parent.getPrinter();

        try {
            // Validate source path
            if (!Files.exists(Paths.get(sourcePath))) {
                printer.error("Source path does not exist: " + sourcePath);
                return 1;
            }

            printer.header("AI-Powered Security Suggestions");
            printer.info("Analyzing: " + sourcePath);
            printer.blank();

            // TODO: Phase 3 - Implement actual AI suggestion logic
            printer.spinner("Generating AI suggestions...", false);
            Thread.sleep(1500); // Simulate AI processing
            printer.spinner("Generating AI suggestions", true);

            printer.blank();

            // Example suggestions
            printSuggestion(printer, 1,
                "Buffer Overflow in strcpy",
                "bzlib.c:234",
                "CRITICAL",
                "The use of strcpy() can lead to buffer overflow vulnerabilities.",
                "Replace strcpy() with strncpy() or use safer alternatives like strlcpy(). " +
                "Always ensure the destination buffer has sufficient space.",
                codeFix ? "strncpy(dest, src, sizeof(dest) - 1);\ndest[sizeof(dest) - 1] = '\\0';" : null
            );

            printer.blank();

            printSuggestion(printer, 2,
                "Memory Leak in error path",
                "bzlib.c:456",
                "HIGH",
                "Memory allocated on line 450 is not freed in the error path.",
                "Add proper cleanup code before returning from error conditions. " +
                "Consider using RAII pattern or goto cleanup pattern.",
                codeFix ? "if (error) {\n    free(buffer);\n    return NULL;\n}" : null
            );

            printer.blank();

            printSuggestion(printer, 3,
                "Null Pointer Dereference",
                "bzlib.c:789",
                "HIGH",
                "Pointer 's' is dereferenced without null check.",
                "Always validate pointers before dereferencing. " +
                "Add null check and handle the error case appropriately.",
                codeFix ? "if (s == NULL) {\n    return ERROR_CODE;\n}\n// Safe to use s here" : null
            );

            printer.blank();
            printer.success("Generated 3 suggestions");
            printer.info("💡 Tip: Use --code-fix to see detailed code examples");

            return 0;

        } catch (Exception e) {
            printer.error("Failed to generate suggestions: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private void printSuggestion(ConsolePrinter printer, int num, String title,
                                   String location, String severity,
                                   String description, String suggestion,
                                   String codeFix) {
        printer.subheader("Suggestion #" + num + ": " + title);
        printer.keyValue("  📁 Location", location);
        printer.keyValue("  🔴 Severity", printer.severityBadge(severity));
        printer.blank();
        System.out.println("  📝 Description:");
        System.out.println("    " + description);
        printer.blank();
        System.out.println("  💡 Suggestion:");
        System.out.println("    " + suggestion);

        if (codeFix != null) {
            printer.blank();
            System.out.println("  🔧 Code Fix:");
            System.out.println("    ```c");
            for (String line : codeFix.split("\n")) {
                System.out.println("    " + line);
            }
            System.out.println("    ```");
        }
    }
}
