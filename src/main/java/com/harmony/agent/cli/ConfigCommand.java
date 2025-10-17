package com.harmony.agent.cli;

import com.harmony.agent.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Configuration management command
 */
@Command(
    name = "config",
    description = "Manage configuration settings",
    mixinStandardHelpOptions = true
)
public class ConfigCommand implements Callable<Integer> {

    @ParentCommand
    private HarmonyAgentCLI parent;

    @Parameters(
        index = "0",
        description = "Action: set | get | list"
    )
    private String action;

    @Parameters(
        index = "1",
        arity = "0..1",
        description = "Configuration key (e.g., ai.api_key, analysis.level)"
    )
    private String key;

    @Parameters(
        index = "2",
        arity = "0..1",
        description = "Configuration value"
    )
    private String value;

    @Override
    public Integer call() {
        ConsolePrinter printer = parent.getPrinter();
        ConfigManager configManager = parent.getConfigManager();

        try {
            switch (action.toLowerCase()) {
                case "set" -> handleSet(printer, configManager);
                case "get" -> handleGet(printer, configManager);
                case "list" -> handleList(printer, configManager);
                default -> {
                    printer.error("Unknown action: " + action);
                    printer.info("Available actions: set, get, list");
                    return 1;
                }
            }
            return 0;
        } catch (Exception e) {
            printer.error("Configuration error: " + e.getMessage());
            if (parent.isVerbose()) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private void handleSet(ConsolePrinter printer, ConfigManager configManager) {
        if (key == null || value == null) {
            printer.error("Usage: config set <key> <value>");
            printer.info("Example: config set ai.api_key sk-xxxxx");
            return;
        }

        configManager.set(key, value);

        // Mask sensitive values in output
        String displayValue = key.contains("key") || key.contains("password")
            ? "****"
            : value;

        printer.success("Configuration saved: " + key + " = " + displayValue);
    }

    private void handleGet(ConsolePrinter printer, ConfigManager configManager) {
        if (key == null) {
            printer.error("Usage: config get <key>");
            printer.info("Example: config get ai.model");
            return;
        }

        String val = configManager.get(key);
        if (val == null) {
            printer.warning("Configuration key not found: " + key);
        } else {
            printer.keyValue(key, val);
        }
    }

    private void handleList(ConsolePrinter printer, ConfigManager configManager) {
        printer.header("Current Configuration");

        Map<String, String> config = configManager.list();

        // Group by section
        String[] sections = {"ai", "analysis", "tools", "output", "cache"};

        for (String section : sections) {
            printer.subheader(section.toUpperCase());

            config.entrySet().stream()
                .filter(e -> e.getKey().startsWith(section + "."))
                .forEach(e -> {
                    String displayKey = e.getKey();
                    String displayValue = e.getValue();

                    // Mask sensitive values
                    if (displayKey.contains("key") || displayKey.contains("password")) {
                        if (displayValue != null && !displayValue.isEmpty()) {
                            displayValue = maskValue(displayValue);
                        }
                    }

                    printer.keyValue("  " + displayKey, displayValue != null ? displayValue : "(not set)");
                });
        }

        printer.blank();
        printer.info("Configuration file: " + configManager.getConfigDir().resolve("config.yml"));
    }

    private String maskValue(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
