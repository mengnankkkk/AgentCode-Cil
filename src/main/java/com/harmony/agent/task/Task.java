package com.harmony.agent.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single task in the todo list
 * Enhanced with dependency relationships for parallel execution
 */
public class Task {
    private final int id;
    private final String description;
    private TaskStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String output;
    private List<Integer> dependsOn;  // Task IDs that this task depends on
    private int failureCount;         // Number of failures for this task
    private String lastErrorMessage;  // Last error message

    public Task(int id, String description) {
        this.id = id;
        this.description = description;
        this.status = TaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.dependsOn = new ArrayList<>();
        this.failureCount = 0;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        if (status == TaskStatus.COMPLETED) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public boolean isCompleted() {
        return status == TaskStatus.COMPLETED;
    }

    public boolean isInProgress() {
        return status == TaskStatus.IN_PROGRESS;
    }

    public boolean isPending() {
        return status == TaskStatus.PENDING;
    }

    public boolean isSkipped() {
        return status == TaskStatus.SKIPPED;
    }

    // Dependency-related methods
    public void addDependency(int taskId) {
        if (!dependsOn.contains(taskId)) {
            dependsOn.add(taskId);
        }
    }

    public List<Integer> getDependencies() {
        return new ArrayList<>(dependsOn);
    }

    public boolean hasDependencies() {
        return !dependsOn.isEmpty();
    }

    // Error tracking
    public int getFailureCount() {
        return failureCount;
    }

    public void incrementFailureCount() {
        this.failureCount++;
    }

    public void setFailureCount(int count) {
        this.failureCount = count;
    }

    public void setLastErrorMessage(String errorMessage) {
        this.lastErrorMessage = errorMessage;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    /**
     * 获取任务耗时（仅当任务完成时有效）
     * 返回格式：例如 "2m 30s" 或 "15s"
     */
    public String getDuration() {
        if (completedAt == null || createdAt == null) {
            return null;
        }
        long seconds = java.time.temporal.ChronoUnit.SECONDS.between(createdAt, completedAt);
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (remainingSeconds == 0) {
            return minutes + "m";
        }
        return minutes + "m " + remainingSeconds + "s";
    }

    // ANSI 颜色代码
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String GRAY = "\u001B[90m";
    private static final String DIM = "\u001B[2m";

    /**
     * 获取美化后的显示字符串（用于列表显示）
     * 采用Claude Code风格的现代化显示格式，支持ANSI颜色
     */
    public String getDisplayString() {
        String statusIcon;
        String colorCode;

        switch (status) {
            case PENDING:
                statusIcon = "○";
                colorCode = GRAY;
                break;
            case IN_PROGRESS:
                statusIcon = "►";
                colorCode = CYAN;
                break;
            case COMPLETED:
                statusIcon = "✓";
                colorCode = GREEN;
                break;
            case SKIPPED:
                statusIcon = "✗";
                colorCode = RED;
                break;
            default:
                statusIcon = "?";
                colorCode = RESET;
        }

        String displayText = description;
        String durationStr = "";

        if (status == TaskStatus.COMPLETED) {
            // 已完成的任务显示为删除线格式
            displayText = DIM + "~~" + description + "~~" + RESET;
            // 显示耗时信息
            String duration = getDuration();
            if (duration != null) {
                durationStr = " " + GRAY + duration + RESET;
            }
        } else if (status == TaskStatus.SKIPPED) {
            // 已跳过的任务显示为删除线格式
            displayText = DIM + description + RESET;
            String duration = getDuration();
            if (duration != null) {
                durationStr = " " + GRAY + duration + RESET;
            }
        }

        return String.format("  %s%s%s %s%s%s", colorCode, statusIcon, RESET, displayText, durationStr, RESET);
    }

    /**
     * 获取简洁显示字符串（仅用于简洁模式）
     */
    public String getCompactDisplayString() {
        String statusIcon;
        String colorCode;

        switch (status) {
            case PENDING:
                statusIcon = "○";
                colorCode = GRAY;
                break;
            case IN_PROGRESS:
                statusIcon = "►";
                colorCode = CYAN;
                break;
            case COMPLETED:
                statusIcon = "✓";
                colorCode = GREEN;
                break;
            case SKIPPED:
                statusIcon = "✗";
                colorCode = RED;
                break;
            default:
                statusIcon = "?";
                colorCode = RESET;
        }

        String displayText = description;
        if (status == TaskStatus.COMPLETED || status == TaskStatus.SKIPPED) {
            displayText = DIM + description + RESET;
        }

        return String.format("%s%s%s %s", colorCode, statusIcon, RESET, displayText);
    }

    @Override
    public String toString() {
        String statusSymbol = switch (status) {
            case PENDING -> "[ ]";
            case IN_PROGRESS -> "[→]";
            case COMPLETED -> "[✓]";
            case SKIPPED -> "[⊘]";
        };
        return String.format("%s Task %d: %s", statusSymbol, id, description);
    }

    /**
     * Task status enum
     */
    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        SKIPPED
    }
}

