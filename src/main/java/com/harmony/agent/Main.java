package com.harmony.agent;

import com.harmony.agent.cli.HarmonyAgentCLI;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

/**
 * HarmonySafeAgent - OpenHarmony Security Analysis Tool
 *
 * Main entry point for the CLI application.
 */
public class Main {

    public static void main(String[] args) {
        // Install ANSI support for Windows
        AnsiConsole.systemInstall();

        try {
            // Create and execute command
            HarmonyAgentCLI cli = new HarmonyAgentCLI();
            CommandLine cmd = new CommandLine(cli);

            // Configure command line
            cmd.setUsageHelpAutoWidth(true);
            cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                commandLine.getErr().println(commandLine.getColorScheme().errorText("❌ Error: " + ex.getMessage()));
                if (cli.isVerbose()) {
                    ex.printStackTrace(commandLine.getErr());
                }
                return 1;
            });

            // Execute
            int exitCode = cmd.execute(args);
            System.exit(exitCode);

        } catch (Exception e) {
            System.err.println("❌ Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            AnsiConsole.systemUninstall();
        }
    }
}
