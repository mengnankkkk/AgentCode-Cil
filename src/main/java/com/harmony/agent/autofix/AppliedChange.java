package com.harmony.agent.autofix;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Represents a change that has been applied to disk
 * Used for rollback functionality
 */
public class AppliedChange {

    private final String id;
    private final Path filePath;
    private final String originalContent;  // Full file content before change
    private final PendingChange pendingChange;
    private final Instant appliedAt;

    public AppliedChange(String id, Path filePath, String originalContent,
                        PendingChange pendingChange) {
        this.id = id;
        this.filePath = filePath;
        this.originalContent = originalContent;
        this.pendingChange = pendingChange;
        this.appliedAt = Instant.now();
    }

    public String getId() { return id; }
    public Path getFilePath() { return filePath; }
    public String getOriginalContent() { return originalContent; }
    public PendingChange getPendingChange() { return pendingChange; }
    public Instant getAppliedAt() { return appliedAt; }

    public String getSummary() {
        return String.format("[%s] %s",
            id,
            pendingChange.getSummary()
        );
    }
}
