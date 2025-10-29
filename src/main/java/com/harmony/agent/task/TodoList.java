package com.harmony.agent.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a list of tasks with progress tracking
 * 支持存储完整的计划分析结果（包括需求理解、约束、风险等）
 */
public class TodoList {
    private static final Logger logger = LoggerFactory.getLogger(TodoList.class);

    private final String requirement;
    private final List<Task> tasks;
    private int currentTaskIndex;
    private String analysisResult; // 存储完整的需求分析和规划结果

    public TodoList(String requirement, List<String> taskDescriptions) {
        this.requirement = requirement;
        this.tasks = new ArrayList<>();
        this.currentTaskIndex = 0;
        this.analysisResult = null;

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
     * Skip a task and move to the next one
     */
    public void skipTask(int taskId) {
        for (Task task : tasks) {
            if (task.getId() == taskId) {
                task.setStatus(Task.TaskStatus.SKIPPED);
                currentTaskIndex++;
                logger.info("任务 {} 已被跳过", taskId);
                break;
            }
        }
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
     * Set the complete analysis result (from PlannerRole)
     */
    public void setAnalysisResult(String analysisResult) {
        this.analysisResult = analysisResult;
    }

    /**
     * Get the complete analysis result
     */
    public String getAnalysisResult() {
        return analysisResult;
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
     * 支持显示完整的分析结果或仅任务列表
     */
    public String toDisplayString(boolean showAll) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║ 📋 任务列表: %s/%s 已完成 (%d%%)%n",
            getCompletedTaskCount(), getTotalTaskCount(), getProgressPercentage()));
        sb.append("╠════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ 需求: %s%n", requirement));
        sb.append("╠════════════════════════════════════════════════════════════════╣\n");

        if (showAll) {
            // Show all tasks
            for (Task task : tasks) {
                String marker = task.isCompleted() ? "[x]" : task.isSkipped() ? "[⊘]" : "[ ]";
                String arrow = task.isInProgress() ? " ← 当前任务" : "";
                sb.append(String.format("║ %s %d. %s%s%n", marker, task.getId(), task.getDescription(), arrow));
            }
        } else {
            // Show only current task
            Optional<Task> current = getCurrentTask();
            if (current.isPresent()) {
                Task task = current.get();
                sb.append(String.format("║ 当前任务: [%d/%d]%n", currentTaskIndex + 1, tasks.size()));
                sb.append(String.format("║ → %s%n", task.getDescription()));
            } else {
                sb.append("║ ✓ 所有任务已完成！%n");
            }
        }

        sb.append("╚════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }
}
