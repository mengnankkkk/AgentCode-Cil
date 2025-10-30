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

    // ANSI 颜色代码
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";
    private static final String BRIGHT_BLUE = "\u001B[94m";

    /**
     * 生成进度条字符串 - Claude Code风格
     * 例如：████░░░░░░ 4/10
     */
    private String generateProgressBar(int completed, int total) {
        if (total == 0) return "";
        int barLength = 10;
        int filledLength = (completed * barLength) / total;

        StringBuilder bar = new StringBuilder();
        // 使用ANSI颜色的进度条
        bar.append(CYAN);
        for (int i = 0; i < filledLength; i++) {
            bar.append("█");
        }
        bar.append(RESET);
        for (int i = filledLength; i < barLength; i++) {
            bar.append("░");
        }
        return bar.toString();
    }

    /**
     * Format as display string - Claude Code 现代风格
     * 简洁、美观、清晰的待办列表显示，支持ANSI颜色
     */
    public String toDisplayString(boolean showAll) {
        // 空状态处理
        if (tasks.isEmpty()) {
            return "\n  " + GRAY + "📭 Empty Fast\n" + RESET;
        }

        StringBuilder sb = new StringBuilder();
        int completed = getCompletedTaskCount();
        int total = getTotalTaskCount();
        int progress = getProgressPercentage();

        // 使用简洁的现代化样式
        sb.append("\n");

        // 标题行：任务清单和进度
        String progressBar = generateProgressBar(completed, total);
        sb.append(String.format("  %s📋 Tasks%s %s %2d%%%n", BRIGHT_BLUE, RESET, progressBar, progress));

        if (showAll) {
            // 显示所有任务的详细列表
            sb.append("\n");
            int index = 0;
            for (Task task : tasks) {
                String displayLine = task.getDisplayString();
                if (task.isInProgress()) {
                    sb.append(String.format("  %s◄%s%s%n", CYAN, RESET, displayLine));
                } else {
                    sb.append(String.format("  %s%n", displayLine));
                }
                index++;
            }
        } else {
            // 简洁模式：仅显示当前任务
            Optional<Task> current = getCurrentTask();
            if (current.isPresent()) {
                Task task = current.get();
                String compactDisplay = task.getCompactDisplayString();
                sb.append(String.format("  %s◄%s %s%n", CYAN, RESET, compactDisplay));
            } else {
                sb.append("  ✓ All tasks completed\n");
            }
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * 生成详细的任务清单显示（包含所有任务）
     */
    public String toDetailedDisplayString() {
        return toDisplayString(true);
    }

    /**
     * 生成简洁的任务显示（仅显示当前任务）
     */
    public String toSimpleDisplayString() {
        return toDisplayString(false);
    }
}
