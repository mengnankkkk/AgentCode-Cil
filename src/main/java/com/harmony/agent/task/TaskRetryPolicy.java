package com.harmony.agent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Task Retry Policy Manager
 * Support failure retry, skip and delayed retry
 */
public class TaskRetryPolicy {
    private static final Logger logger = LoggerFactory.getLogger(TaskRetryPolicy.class);

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final Map<Integer, Integer> taskRetryCount = new HashMap<>();
    private final Set<Integer> skippedTasks = new HashSet<>();
    private final Map<Integer, String> taskFailureReasons = new HashMap<>();

    /**
     * Record task failure
     * @param taskId Task ID
     * @param reason Failure reason
     * @return true if should retry, false if should skip
     */
    public boolean recordTaskFailure(int taskId, String reason) {
        int currentRetries = taskRetryCount.getOrDefault(taskId, 0);

        logger.warn("Task {} execution failed (attempt {}): {}", taskId, currentRetries + 1, reason);

        taskFailureReasons.put(taskId, reason);

        if (currentRetries < MAX_RETRIES) {
            taskRetryCount.put(taskId, currentRetries + 1);
            logger.info("Will retry task {} after {}ms", taskId, RETRY_DELAY_MS);
            return true;
        } else {
            skippedTasks.add(taskId);
            logger.warn("Task {} reached max retries, will be skipped", taskId);
            return false;
        }
    }

    /**
     * Record task success
     */
    public void recordTaskSuccess(int taskId) {
        taskRetryCount.remove(taskId);
        taskFailureReasons.remove(taskId);
        logger.info("Task {} executed successfully", taskId);
    }

    /**
     * Check if task is skipped
     */
    public boolean isTaskSkipped(int taskId) {
        return skippedTasks.contains(taskId);
    }

    /**
     * Get skipped tasks list
     */
    public Set<Integer> getSkippedTasks() {
        return new HashSet<>(skippedTasks);
    }

    /**
     * Get failure reason
     */
    public String getFailureReason(int taskId) {
        return taskFailureReasons.getOrDefault(taskId, "Unknown reason");
    }

    /**
     * Get retry count
     */
    public int getRetryCount(int taskId) {
        return taskRetryCount.getOrDefault(taskId, 0);
    }

    /**
     * Wait before retry
     */
    public void waitBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Retry wait was interrupted");
        }
    }

    /**
     * Reset all retry state
     */
    public void resetAllRetries() {
        taskRetryCount.clear();
        taskFailureReasons.clear();
        logger.info("Retry count reset");
    }

    /**
     * Get retry statistics summary
     */
    public String getRetryStatistics() {
        if (skippedTasks.isEmpty() && taskFailureReasons.isEmpty()) {
            return "All tasks executed successfully";
        }

        StringBuilder sb = new StringBuilder();

        if (!skippedTasks.isEmpty()) {
            sb.append("Skipped tasks: ");
            skippedTasks.forEach(id -> sb.append(id).append(", "));
            sb.setLength(sb.length() - 2);
            sb.append("\n");
        }

        if (!taskFailureReasons.isEmpty()) {
            sb.append("Failure reasons:\n");
            taskFailureReasons.forEach((taskId, reason) ->
                sb.append(String.format("  - Task %d: %s\n", taskId, reason))
            );
        }

        return sb.toString();
    }
}
