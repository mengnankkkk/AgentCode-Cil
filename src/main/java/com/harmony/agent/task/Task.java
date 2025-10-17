package com.harmony.agent.task;

import java.time.LocalDateTime;

/**
 * Represents a single task in the todo list
 */
public class Task {
    private final int id;
    private final String description;
    private TaskStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String output;

    public Task(int id, String description) {
        this.id = id;
        this.description = description;
        this.status = TaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
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

    @Override
    public String toString() {
        String statusSymbol = switch (status) {
            case PENDING -> "[ ]";
            case IN_PROGRESS -> "[→]";
            case COMPLETED -> "[✓]";
        };
        return String.format("%s Task %d: %s", statusSymbol, id, description);
    }

    /**
     * Task status enum
     */
    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED
    }
}
