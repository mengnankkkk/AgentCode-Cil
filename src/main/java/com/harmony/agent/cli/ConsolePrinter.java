package com.harmony.agent.cli;

import org.fusesource.jansi.Ansi;
import static org.fusesource.jansi.Ansi.*;
import static org.fusesource.jansi.Ansi.Color.*;

/**
 * Console output utility with ANSI colors and formatting
 */
public class ConsolePrinter {

    private final boolean colorEnabled;

    public ConsolePrinter(boolean colorEnabled) {
        this.colorEnabled = colorEnabled;
    }

    public ConsolePrinter() {
        this(true);
    }

    /**
     * Print success message
     */
    public void success(String message) {
        if (colorEnabled) {
            System.out.println(ansi().fg(GREEN).a("✅ " + message).reset());
        } else {
            System.out.println("[SUCCESS] " + message);
        }
    }

    /**
     * Print error message
     */
    public void error(String message) {
        if (colorEnabled) {
            System.err.println(ansi().fg(RED).a("❌ " + message).reset());
        } else {
            System.err.println("[ERROR] " + message);
        }
    }

    /**
     * Print warning message
     */
    public void warning(String message) {
        if (colorEnabled) {
            System.out.println(ansi().fg(YELLOW).a("⚠️  " + message).reset());
        } else {
            System.out.println("[WARNING] " + message);
        }
    }

    /**
     * Print info message
     */
    public void info(String message) {
        if (colorEnabled) {
            System.out.println(ansi().fg(CYAN).a("ℹ️  " + message).reset());
        } else {
            System.out.println("[INFO] " + message);
        }
    }

    /**
     * Print section header
     */
    public void header(String title) {
        if (colorEnabled) {
            System.out.println(ansi()
                .fg(BLUE)
                .bold()
                .a("\n" + "=".repeat(60))
                .a("\n📊 " + title)
                .a("\n" + "=".repeat(60))
                .reset());
        } else {
            System.out.println("\n" + "=".repeat(60));
            System.out.println(title);
            System.out.println("=".repeat(60));
        }
    }

    /**
     * Print subsection header
     */
    public void subheader(String title) {
        if (colorEnabled) {
            System.out.println(ansi()
                .fg(MAGENTA)
                .bold()
                .a("\n## " + title)
                .reset());
        } else {
            System.out.println("\n## " + title);
        }
    }

    /**
     * Print progress bar
     * @param current current progress
     * @param total total items
     * @param task task description
     */
    public void progressBar(int current, int total, String task) {
        int percent = (current * 100) / total;
        int completed = percent / 2; // 50 chars max

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 50; i++) {
            bar.append(i < completed ? "█" : "░");
        }
        bar.append("] ").append(percent).append("% - ").append(task);

        if (colorEnabled) {
            Color color = percent < 50 ? YELLOW : (percent < 100 ? CYAN : GREEN);
            System.out.print("\r" + ansi().fg(color).a(bar).reset());
        } else {
            System.out.print("\r" + bar);
        }

        if (current == total) {
            System.out.println();
        }
    }

    /**
     * Print spinner animation
     */
    public void spinner(String message, boolean done) {
        if (done) {
            System.out.print("\r" + ansi().fg(GREEN).a("✓ " + message).reset() + "\n");
        } else {
            System.out.print("\r" + ansi().fg(CYAN).a("⠋ " + message + "...").reset());
        }
    }

    /**
     * Print statistics
     */
    public void stats(String label, int value, Color color) {
        if (colorEnabled) {
            System.out.println(ansi()
                .a("  • ")
                .fg(color)
                .bold()
                .a(String.format("%-20s", label))
                .a(": ")
                .a(value)
                .reset());
        } else {
            System.out.println(String.format("  • %-20s: %d", label, value));
        }
    }

    /**
     * Print key-value pair
     */
    public void keyValue(String key, String value) {
        if (colorEnabled) {
            System.out.println(ansi()
                .fg(CYAN)
                .a(key)
                .a(": ")
                .reset()
                .a(value));
        } else {
            System.out.println(key + ": " + value);
        }
    }

    /**
     * Print table row
     */
    public void tableRow(String col1, String col2, String col3) {
        System.out.println(String.format("  %-30s %-20s %-20s", col1, col2, col3));
    }

    /**
     * Print blank line
     */
    public void blank() {
        System.out.println();
    }

    /**
     * Clear current line (for progress updates)
     */
    public void clearLine() {
        System.out.print("\r" + " ".repeat(80) + "\r");
    }

    /**
     * Print severity badge
     */
    public String severityBadge(String severity) {
        if (!colorEnabled) {
            return "[" + severity + "]";
        }

        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> ansi().fg(RED).bold().a("🔴 CRITICAL").reset().toString();
            case "HIGH" -> ansi().fg(RED).a("🟠 HIGH").reset().toString();
            case "MEDIUM" -> ansi().fg(YELLOW).a("🟡 MEDIUM").reset().toString();
            case "LOW" -> ansi().fg(GREEN).a("🟢 LOW").reset().toString();
            default -> ansi().a("⚪ " + severity).reset().toString();
        };
    }

    /**
     * Format bytes to human-readable size
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Format duration to human-readable time
     */
    public static String formatDuration(long millis) {
        if (millis < 1000) return millis + "ms";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return String.format("%dm %ds", minutes, seconds);
        long hours = minutes / 60;
        minutes = minutes % 60;
        return String.format("%dh %dm", hours, minutes);
    }
}
