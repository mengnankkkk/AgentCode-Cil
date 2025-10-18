package com.harmony.agent.autofix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Manages pending changes and applied change history
 * Supports /accept and /rollback operations
 */
public class ChangeManager {

    private static final Logger logger = LoggerFactory.getLogger(ChangeManager.class);

    private PendingChange pendingChange;
    private final Deque<AppliedChange> changeHistory;
    private final int maxHistorySize;

    public ChangeManager() {
        this(10);  // Keep last 10 applied changes
    }

    public ChangeManager(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
        this.changeHistory = new ArrayDeque<>(maxHistorySize);
    }

    /**
     * Set a new pending change (replaces any existing pending change)
     */
    public void setPendingChange(PendingChange change) {
        if (pendingChange != null) {
            logger.warn("Replacing existing pending change: {}", pendingChange.getId());
        }
        this.pendingChange = change;
        logger.info("Pending change set: {}", change.getSummary());
    }

    /**
     * Get the current pending change
     */
    public Optional<PendingChange> getPendingChange() {
        return Optional.ofNullable(pendingChange);
    }

    /**
     * Check if there's a pending change
     */
    public boolean hasPendingChange() {
        return pendingChange != null;
    }

    /**
     * Accept the pending change - apply it to disk
     *
     * @return AppliedChange representing the successful application
     * @throws IOException if file operations fail
     */
    public AppliedChange acceptPendingChange() throws IOException {
        if (pendingChange == null) {
            throw new IllegalStateException("No pending change to accept");
        }

        Path filePath = pendingChange.getFilePath();

        // Read original content for rollback
        String originalContent = Files.readString(filePath);

        // Apply the change
        List<String> lines = Files.readAllLines(filePath);

        // Remove old lines
        int startLine = pendingChange.getStartLine();
        int endLine = pendingChange.getEndLine();
        for (int i = endLine - 1; i >= startLine - 1; i--) {
            if (i < lines.size()) {
                lines.remove(i);
            }
        }

        // Insert new lines
        String[] newLines = pendingChange.getNewCode().split("\n");
        for (int i = 0; i < newLines.length; i++) {
            lines.add(startLine - 1 + i, newLines[i]);
        }

        // Write back to file
        Files.write(filePath, lines);

        // Create applied change record
        String changeId = generateChangeId();
        AppliedChange applied = new AppliedChange(
            changeId,
            filePath,
            originalContent,
            pendingChange
        );

        // Add to history
        changeHistory.addFirst(applied);

        // Trim history if needed
        while (changeHistory.size() > maxHistorySize) {
            changeHistory.removeLast();
        }

        logger.info("Change accepted and applied: {}", applied.getSummary());

        // Clear pending change
        pendingChange = null;

        return applied;
    }

    /**
     * Discard the pending change without applying
     */
    public void discardPendingChange() {
        if (pendingChange != null) {
            logger.info("Discarding pending change: {}", pendingChange.getSummary());
            pendingChange = null;
        }
    }

    /**
     * Rollback the last applied change
     *
     * @return The change that was rolled back
     * @throws IOException if file operations fail
     */
    public AppliedChange rollbackLastChange() throws IOException {
        if (changeHistory.isEmpty()) {
            throw new IllegalStateException("No changes to rollback");
        }

        AppliedChange lastChange = changeHistory.removeFirst();
        Path filePath = lastChange.getFilePath();

        // Restore original content
        Files.writeString(filePath, lastChange.getOriginalContent());

        logger.info("Rolled back change: {}", lastChange.getSummary());

        return lastChange;
    }

    /**
     * Get change history (most recent first)
     */
    public List<AppliedChange> getChangeHistory() {
        return new ArrayList<>(changeHistory);
    }

    /**
     * Check if there are any changes to rollback
     */
    public boolean canRollback() {
        return !changeHistory.isEmpty();
    }

    /**
     * Get number of changes in history
     */
    public int getHistorySize() {
        return changeHistory.size();
    }

    /**
     * Clear all history (cannot be undone!)
     */
    public void clearHistory() {
        logger.warn("Clearing {} changes from history", changeHistory.size());
        changeHistory.clear();
    }

    /**
     * Generate a unique change ID
     */
    private String generateChangeId() {
        return "change_" + System.currentTimeMillis();
    }
}
