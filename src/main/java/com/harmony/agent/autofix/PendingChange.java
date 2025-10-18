package com.harmony.agent.autofix;

import com.harmony.agent.core.model.SecurityIssue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Represents a pending code change that hasn't been applied yet
 * Changes are kept in memory until user accepts with /accept
 */
public class PendingChange {

    private final String id;
    private final SecurityIssue issue;
    private final Path filePath;
    private final int startLine;
    private final int endLine;
    private final String oldCode;
    private final String newCode;
    private final List<String> fixPlan;
    private final ReviewResult reviewResult;
    private final CodeValidator.ValidationResult validationResult;
    private final Instant createdAt;

    public PendingChange(String id, SecurityIssue issue, Path filePath,
                        int startLine, int endLine,
                        String oldCode, String newCode,
                        List<String> fixPlan, ReviewResult reviewResult,
                        CodeValidator.ValidationResult validationResult) {
        this.id = id;
        this.issue = issue;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.oldCode = oldCode;
        this.newCode = newCode;
        this.fixPlan = fixPlan;
        this.reviewResult = reviewResult;
        this.validationResult = validationResult;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public SecurityIssue getIssue() { return issue; }
    public Path getFilePath() { return filePath; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }
    public String getOldCode() { return oldCode; }
    public String getNewCode() { return newCode; }
    public List<String> getFixPlan() { return fixPlan; }
    public ReviewResult getReviewResult() { return reviewResult; }
    public CodeValidator.ValidationResult getValidationResult() { return validationResult; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Get number of lines changed
     */
    public int getLinesChanged() {
        int oldLines = oldCode.split("\n").length;
        int newLines = newCode.split("\n").length;
        return Math.max(oldLines, newLines);
    }

    /**
     * Get summary for display
     */
    public String getSummary() {
        return String.format("%s:%d-%d: %s",
            filePath.getFileName(),
            startLine,
            endLine,
            issue.getTitle()
        );
    }
}
