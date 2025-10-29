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

