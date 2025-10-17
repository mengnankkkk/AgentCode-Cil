package com.harmony.agent.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages a list of tasks with progress tracking
 */
public class TodoList {
    private final String requirement;
    private final List<Task> tasks;
    private int currentTaskIndex;

    public TodoList(String requirement, List<String> taskDescriptions) {
        this.requirement = requirement;
        this.tasks = new ArrayList<>();
        this.currentTaskIndex = 0;

        // Create tasks from descriptions
        for (int i = 0; i < taskDescriptions.size(); i++) {
            tasks.add(new Task(i + 1, taskDescriptions.get(i)));
        }
    }

    /**
     * Get the current task being worked on
     */
    public Optional<Task> getCurrentTask() {
        if (currentTaskIndex < tasks.size()) {
            return Optional.of(tasks.get(currentTaskIndex));
        }
        return Optional.empty();
    }

    /**
     * Mark current task as completed and move to next
     */
    public boolean completeCurrentTask(String output) {
        Optional<Task> current = getCurrentTask();
        if (current.isPresent()) {
            Task task = current.get();
            task.setStatus(Task.TaskStatus.COMPLETED);
            task.setOutput(output);
            currentTaskIndex++;
            return true;
        }
        return false;
    }

    /**
     * Mark current task as in progress
     */
    public boolean startCurrentTask() {
        Optional<Task> current = getCurrentTask();
        if (current.isPresent()) {
            current.get().setStatus(Task.TaskStatus.IN_PROGRESS);
            return true;
        }
        return false;
    }

    /**
     * Get all tasks
     */
    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks);
    }

    /**
     * Get completed tasks
     */
    public List<Task> getCompletedTasks() {
        return tasks.stream()
            .filter(Task::isCompleted)
            .toList();
    }

    /**
     * Get pending tasks
     */
    public List<Task> getPendingTasks() {
        return tasks.stream()
            .filter(Task::isPending)
            .toList();
    }

    /**
     * Check if all tasks are completed
     */
    public boolean isCompleted() {
        return currentTaskIndex >= tasks.size();
    }

    /**
     * Get progress percentage
     */
    public int getProgressPercentage() {
        if (tasks.isEmpty()) {
            return 100;
        }
        long completed = tasks.stream().filter(Task::isCompleted).count();
        return (int) ((completed * 100) / tasks.size());
    }

    /**
     * Get original requirement
     */
    public String getRequirement() {
        return requirement;
    }

    /**
     * Get total task count
     */
    public int getTotalTaskCount() {
        return tasks.size();
    }

    /**
     * Get completed task count
     */
    public int getCompletedTaskCount() {
        return (int) tasks.stream().filter(Task::isCompleted).count();
    }

    /**
     * Format as display string
     */
    public String toDisplayString(boolean showAll) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║ 📋 Task List: %s/%s completed (%d%%)%n",
            getCompletedTaskCount(), getTotalTaskCount(), getProgressPercentage()));
        sb.append("╠════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Requirement: %s%n", requirement));
        sb.append("╠════════════════════════════════════════════════════════════════╣\n");

        if (showAll) {
            // Show all tasks
            for (Task task : tasks) {
                String marker = task.isCompleted() ? "[x]" : "[ ]";
                String arrow = task.isInProgress() ? " ← Current" : "";
                sb.append(String.format("║ %s %d. %s%s%n", marker, task.getId(), task.getDescription(), arrow));
            }
        } else {
            // Show only current task
            Optional<Task> current = getCurrentTask();
            if (current.isPresent()) {
                Task task = current.get();
                sb.append(String.format("║ Current Task: [%d/%d]%n", currentTaskIndex + 1, tasks.size()));
                sb.append(String.format("║ → %s%n", task.getDescription()));
            } else {
                sb.append("║ ✓ All tasks completed!%n");
            }
        }

        sb.append("╚════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }
}
