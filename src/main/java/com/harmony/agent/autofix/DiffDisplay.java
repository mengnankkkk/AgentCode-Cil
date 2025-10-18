package com.harmony.agent.autofix;

import org.fusesource.jansi.Ansi;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays code diffs with color coding
 * - Red: Deleted lines
 * - Green: Added lines
 * - White: Context lines
 */
public class DiffDisplay {

    /**
     * Display a diff between old and new code
     */
    public static void displayDiff(String oldCode, String newCode, String filePath, int startLine) {
        System.out.println();
        System.out.println(Ansi.ansi().bold().a("╭─ ").a(filePath).a(" ─").reset());
        System.out.println();

        String[] oldLines = oldCode.split("\n");
        String[] newLines = newCode.split("\n");

        // Simple line-by-line diff
        int maxLines = Math.max(oldLines.length, newLines.length);
        int currentLine = startLine;

        for (int i = 0; i < maxLines; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : null;
            String newLine = i < newLines.length ? newLines[i] : null;

            if (oldLine != null && newLine != null) {
                if (oldLine.equals(newLine)) {
                    // Unchanged line
                    printContextLine(currentLine, oldLine);
                    currentLine++;
                } else {
                    // Changed line
                    printDeletedLine(currentLine, oldLine);
                    printAddedLine(currentLine, newLine);
                    currentLine++;
                }
            } else if (oldLine != null) {
                // Deleted line
                printDeletedLine(currentLine, oldLine);
                currentLine++;
            } else if (newLine != null) {
                // Added line
                printAddedLine(currentLine, newLine);
                currentLine++;
            }
        }

        System.out.println();
        System.out.println(Ansi.ansi().bold().a("╰─").reset());
        System.out.println();
    }

    /**
     * Display a unified diff view
     */
    public static void displayUnifiedDiff(String oldCode, String newCode, String filePath, int startLine) {
        System.out.println();
        System.out.println(Ansi.ansi().bold().fgBrightBlue().a("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").reset());
        System.out.println(Ansi.ansi().bold().a("  File: ").fgBrightCyan().a(filePath).reset());
        System.out.println(Ansi.ansi().bold().a("  Lines: ").a(startLine).a("-").a(startLine + oldCode.split("\n").length - 1).reset());
        System.out.println(Ansi.ansi().bold().fgBrightBlue().a("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").reset());
        System.out.println();

        String[] oldLines = oldCode.split("\n");
        String[] newLines = newCode.split("\n");

        // Show deletions
        if (oldLines.length > 0) {
            for (int i = 0; i < oldLines.length; i++) {
                printDeletedLine(startLine + i, oldLines[i]);
            }
        }

        // Show additions
        if (newLines.length > 0) {
            for (int i = 0; i < newLines.length; i++) {
                printAddedLine(startLine + i, newLines[i]);
            }
        }

        System.out.println();
    }

    /**
     * Print a context line (unchanged)
     */
    private static void printContextLine(int lineNumber, String content) {
        System.out.println(Ansi.ansi()
            .fgBrightBlack().a(String.format("  %4d │ ", lineNumber))
            .reset().a(content)
        );
    }

    /**
     * Print a deleted line (red)
     */
    private static void printDeletedLine(int lineNumber, String content) {
        System.out.println(Ansi.ansi()
            .fgRed().a(String.format("- %4d │ ", lineNumber))
            .fgBrightRed().a(content)
            .reset()
        );
    }

    /**
     * Print an added line (green)
     */
    private static void printAddedLine(int lineNumber, String content) {
        System.out.println(Ansi.ansi()
            .fgGreen().a(String.format("+ %4d │ ", lineNumber))
            .fgBrightGreen().a(content)
            .reset()
        );
    }

    /**
     * Display a summary of changes
     */
    public static void displayChangeSummary(PendingChange change) {
        System.out.println();
        System.out.println(Ansi.ansi().bold().fgBrightYellow().a("📝 Pending Change Summary").reset());
        System.out.println(Ansi.ansi().bold().fgBrightBlue().a("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").reset());

        System.out.println(Ansi.ansi().bold().a("  Issue: ").reset().a(change.getIssue().getTitle()));
        System.out.println(Ansi.ansi().bold().a("  Severity: ").reset().a(change.getIssue().getSeverity().name()));
        System.out.println(Ansi.ansi().bold().a("  File: ").fgBrightCyan().a(change.getFilePath()).reset());
        System.out.println(Ansi.ansi().bold().a("  Lines: ").a(change.getStartLine()).a(" - ").a(change.getEndLine()).reset());

        System.out.println();
        System.out.println(Ansi.ansi().bold().a("  Fix Plan:").reset());
        List<String> fixPlan = change.getFixPlan();
        for (int i = 0; i < fixPlan.size(); i++) {
            System.out.println(Ansi.ansi().a("    ").fgBrightYellow().a((i + 1) + ". ").reset().a(fixPlan.get(i)));
        }

        System.out.println();
        System.out.println(Ansi.ansi().bold().a("  Review: ").reset().a(change.getReviewResult().toString()));

        System.out.println();
        System.out.println(Ansi.ansi().bold().fgBrightBlue().a("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").reset());
    }

    /**
     * Display instructions for next steps
     */
    public static void displayInstructions() {
        System.out.println();
        System.out.println(Ansi.ansi().bold().fgBrightYellow().a("💡 Next Steps:").reset());
        System.out.println(Ansi.ansi().a("  • Type ").fgBrightGreen().bold().a("/accept").reset().a(" to apply this change"));
        System.out.println(Ansi.ansi().a("  • Type ").fgBrightRed().bold().a("/discard").reset().a(" to reject this change"));
        System.out.println(Ansi.ansi().a("  • Type ").fgBrightCyan().bold().a("/rollback").reset().a(" to undo the last accepted change"));
        System.out.println();
    }
}
