package com.harmony.agent.task;

import com.harmony.agent.cli.ConsolePrinter;
import com.harmony.agent.llm.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Manages todo list lifecycle - creation, execution, and tracking
 */
public class TodoListManager {
    private static final Logger logger = LoggerFactory.getLogger(TodoListManager.class);

    private final LLMClient llmClient;
    private final ConsolePrinter printer;
    private TodoList activeTodoList;

    public TodoListManager(LLMClient llmClient, ConsolePrinter printer) {
        this.llmClient = llmClient;
        this.printer = printer;
    }

    /**
     * Create a new todo list from a user requirement
     */
    public TodoList createTodoList(String requirement) {
        logger.info("Creating todo list for requirement: {}", requirement);

        // Show thinking indicator
        printer.blank();
        printer.spinner("Analyzing requirement and breaking down into tasks...", false);

        try {
            // Break down requirement using LLM
            List<String> tasks = llmClient.breakdownRequirement(requirement);

            // Stop spinner
            printer.spinner("Analyzing requirement and breaking down into tasks", true);

            // Create todo list
            activeTodoList = new TodoList(requirement, tasks);

            // Show the created todo list
            printer.blank();
            printer.success("Task breakdown completed!");
            displayTodoList(true);

            // Auto-start first task
            if (activeTodoList.startCurrentTask()) {
                printer.blank();
                printer.info("Starting first task...");
                displayCurrentTask();
            }

            return activeTodoList;

        } catch (Exception e) {
            printer.spinner("Analyzing requirement", true);
            printer.error("Failed to create todo list: " + e.getMessage());
            logger.error("Failed to create todo list", e);
            return null;
        }
    }

    /**
     * Execute the current task
     */
    public boolean executeCurrentTask() {
        if (activeTodoList == null) {
            printer.warning("No active todo list. Use /plan <requirement> to create one.");
            return false;
        }

        Optional<Task> currentTask = activeTodoList.getCurrentTask();
        if (currentTask.isEmpty()) {
            printer.success("All tasks completed! 🎉");
            return false;
        }

        Task task = currentTask.get();
        printer.blank();
        printer.spinner(String.format("Executing: %s...", task.getDescription()), false);

        try {
            // Execute task using LLM
            String context = buildContext();
            String output = llmClient.executeTask(task.getDescription(), context);

            printer.spinner("Executing task", true);

            // Mark task as completed
            activeTodoList.completeCurrentTask(output);

            printer.blank();
            printer.success(String.format("✓ Task %d completed: %s", task.getId(), task.getDescription()));
            printer.info(String.format("Output: %s", output));

            // Check if there are more tasks
            if (!activeTodoList.isCompleted()) {
                // Start next task
                activeTodoList.startCurrentTask();
                printer.blank();
                displayCurrentTask();
                return true;
            } else {
                printer.blank();
                printer.success("🎉 All tasks completed!");
                displayTodoList(true);
                activeTodoList = null; // Clear active list
                return false;
            }

        } catch (Exception e) {
            printer.spinner("Executing task", true);
            printer.error("Failed to execute task: " + e.getMessage());
            logger.error("Failed to execute task", e);
            return false;
        }
    }

    /**
     * Display the current todo list
     */
    public void displayTodoList(boolean showAll) {
        if (activeTodoList == null) {
            printer.warning("No active todo list.");
            return;
        }

        System.out.println(activeTodoList.toDisplayString(showAll));
    }

    /**
     * Display only the current task
     */
    public void displayCurrentTask() {
        displayTodoList(false);
    }

    /**
     * Display full todo list (all tasks)
     */
    public void displayFullTodoList() {
        displayTodoList(true);
    }

    /**
     * Check if there's an active todo list
     */
    public boolean hasActiveTodoList() {
        return activeTodoList != null;
    }

    /**
     * Get the active todo list
     */
    public TodoList getActiveTodoList() {
        return activeTodoList;
    }

    /**
     * Clear the active todo list
     */
    public void clearTodoList() {
        activeTodoList = null;
        printer.info("Todo list cleared.");
    }

    /**
     * Build context from completed tasks
     */
    private String buildContext() {
        if (activeTodoList == null) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("Requirement: ").append(activeTodoList.getRequirement()).append("\n\n");
        context.append("Completed tasks:\n");

        for (Task task : activeTodoList.getCompletedTasks()) {
            context.append(String.format("- %s: %s\n", task.getDescription(), task.getOutput()));
        }

        return context.toString();
    }

    /**
     * Get progress summary
     */
    public String getProgressSummary() {
        if (activeTodoList == null) {
            return "No active tasks";
        }

        return String.format("%d/%d tasks completed (%d%%)",
            activeTodoList.getCompletedTaskCount(),
            activeTodoList.getTotalTaskCount(),
            activeTodoList.getProgressPercentage());
    }
}
