package com.harmony.agent.task;

import com.harmony.agent.cli.ConsolePrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Interactive Task Executor
 * Pauses on critical failures and provides user options
 */
public class InteractiveTaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(InteractiveTaskExecutor.class);

    private final ConsolePrinter printer;

    public enum UserChoice {
        RETRY_ONCE,      // Retry the task one more time
        SKIP_TASK,       // Skip this task and continue
        ABORT_PLAN       // Abort entire plan
    }

    public InteractiveTaskExecutor(ConsolePrinter printer) {
        this.printer = printer;
    }

    /**
     * Handle task failure with user interaction
     * Returns user's choice: retry, skip, or abort
     */
    public UserChoice handleTaskFailure(Task task, String errorMessage, int failureCount) {
        printer.blank();
        printer.error(String.format("TASK FAILURE - Task %d: %s", task.getId(), task.getDescription()));
        printer.blank();

        // Classify the error
        ErrorClassifier.ErrorType errorType = ErrorClassifier.classify(errorMessage);
        int maxRetries = ErrorClassifier.getMaxRetries(errorType);

        printer.warning(String.format("Failure Count: %d/%d", failureCount, maxRetries));
        printer.info(String.format("Error Type: %s", errorType));
        printer.info(String.format("Error Message: %s", ErrorClassifier.getErrorDescription(errorMessage)));
        printer.blank();

        // If it's a permanent error and we've tried once, skip immediately
        if (errorType == ErrorClassifier.ErrorType.PERMANENT && failureCount == 1) {
            printer.warning("This is a permanent error. Skipping task...");
            return UserChoice.SKIP_TASK;
        }

        // If max retries reached for transient error, ask user
        if (failureCount >= maxRetries) {
            return askUserForDecision(task);
        }

        // For transient errors with retries remaining, auto-retry
        printer.info(String.format("Retrying... (attempt %d of %d)", failureCount + 1, maxRetries));
        return UserChoice.RETRY_ONCE;
    }

    /**
     * Ask user what to do with a failed task
     */
    private UserChoice askUserForDecision(Task task) {
        printer.blank();
        printer.warning("=== Task Failure - Your Options ===");
        printer.info(String.format("Task %d has failed after multiple retries.", task.getId()));
        printer.blank();
        printer.info("[1] Retry once more");
        printer.info("[2] Skip this task and continue");
        printer.info("[3] Abort entire plan");
        printer.blank();

        while (true) {
            printer.info("Enter your choice (1, 2, or 3): ");
            String input = readUserInput();

            switch (input.trim()) {
                case "1":
                    printer.info("Retrying task...");
                    return UserChoice.RETRY_ONCE;
                case "2":
                    printer.warning("Task skipped.");
                    return UserChoice.SKIP_TASK;
                case "3":
                    printer.error("Plan aborted by user.");
                    return UserChoice.ABORT_PLAN;
                default:
                    printer.error("Invalid choice. Please enter 1, 2, or 3.");
            }
        }
    }

    /**
     * Notify user when task succeeds after retry
     */
    public void notifyRecovery(Task task, int retryCount) {
        printer.blank();
        printer.success(String.format("TASK RECOVERED - Task %d succeeded after %d retry attempt(s)",
            task.getId(), retryCount));
    }

    /**
     * Notify user when task is skipped permanently
     */
    public void notifyTaskSkipped(Task task, String reason) {
        printer.blank();
        printer.warning(String.format("TASK SKIPPED - Task %d: %s",
            task.getId(), task.getDescription()));
        printer.info(String.format("Reason: %s", reason));
    }

    /**
     * Show execution summary when some tasks are skipped
     */
    public void showExecutionSummary(TodoList todoList, TaskRetryPolicy retryPolicy) {
        printer.blank();
        printer.info("=== Execution Summary ===");

        int total = todoList.getTotalTaskCount();
        int completed = todoList.getCompletedTaskCount();
        int skipped = (int) retryPolicy.getSkippedTasks().size();

        printer.success(String.format("Completed: %d/%d", completed, total));

        if (skipped > 0) {
            printer.warning(String.format("Skipped: %d", skipped));
            printer.blank();
            printer.warning("Skipped tasks and reasons:");

            retryPolicy.getSkippedTasks().forEach(taskId -> {
                String reason = retryPolicy.getFailureReason(taskId);
                printer.info(String.format("  - Task %d: %s", taskId, reason));
            });
        }

        printer.blank();
    }

    /**
     * Read user input from console
     */
    private String readUserInput() {
        try (Scanner scanner = new Scanner(System.in)) {
            return scanner.nextLine();
        } catch (Exception e) {
            logger.error("Error reading user input", e);
            return "";
        }
    }
}
