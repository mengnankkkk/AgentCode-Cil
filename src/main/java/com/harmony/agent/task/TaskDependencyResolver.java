package com.harmony.agent.task;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task Dependency Graph (DAG) Scheduler
 * Supports parallel execution of independent tasks
 */
public class TaskDependencyResolver {
    private static final Logger logger = LoggerFactory.getLogger(TaskDependencyResolver.class);

    private final List<Task> tasks;
    private final Map<Integer, Task> taskMap;

    public TaskDependencyResolver(List<Task> tasks) {
        this.tasks = new ArrayList<>(tasks);
        this.taskMap = new HashMap<>();
        this.tasks.forEach(t -> taskMap.put(t.getId(), t));
    }

    /**
     * Find all tasks that are ready to execute (all dependencies completed)
     */
    public List<Task> getReadyTasks() {
        List<Task> readyTasks = new ArrayList<>();

        for (Task task : tasks) {
            if (task.isPending() && canExecute(task)) {
                readyTasks.add(task);
            }
        }

        return readyTasks;
    }

    /**
     * Check if a task's dependencies are all completed
     */
    public boolean canExecute(Task task) {
        if (!task.hasDependencies()) {
            return true;
        }

        for (int dependencyId : task.getDependencies()) {
            Task dependency = taskMap.get(dependencyId);
            if (dependency == null) {
                logger.warn("Task {} depends on non-existent task {}", task.getId(), dependencyId);
                return false;
            }

            if (!dependency.isCompleted()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get the critical path (longest dependency chain)
     * Useful for estimating total execution time
     */
    public List<Task> getCriticalPath() {
        Map<Integer, Integer> depths = new HashMap<>();

        // Calculate depth for each task
        for (Task task : tasks) {
            depths.put(task.getId(), calculateDepth(task, depths));
        }

        // Find the task with maximum depth
        int maxDepth = depths.values().stream().max(Integer::compareTo).orElse(0);
        List<Task> criticalPath = new ArrayList<>();

        // Reconstruct critical path
        Optional<Task> current = tasks.stream()
            .filter(t -> depths.get(t.getId()) == maxDepth)
            .findFirst();

        while (current.isPresent()) {
            Task task = current.get();
            criticalPath.add(0, task);

            // Find dependent with max depth among dependencies
            Optional<Task> next = task.getDependencies().stream()
                .map(taskMap::get)
                .max(Comparator.comparingInt(t -> depths.getOrDefault(t.getId(), 0)));

            current = next;
        }

        return criticalPath;
    }

    /**
     * Calculate the depth of a task in the dependency graph
     */
    private int calculateDepth(Task task, Map<Integer, Integer> memo) {
        if (memo.containsKey(task.getId())) {
            return memo.get(task.getId());
        }

        if (!task.hasDependencies()) {
            memo.put(task.getId(), 1);
            return 1;
        }

        int maxDependencyDepth = task.getDependencies().stream()
            .map(taskMap::get)
            .mapToInt(dep -> calculateDepth(dep, memo))
            .max()
            .orElse(0);

        int depth = maxDependencyDepth + 1;
        memo.put(task.getId(), depth);
        return depth;
    }

    /**
     * Get all tasks that depend on a given task
     */
    public List<Task> getDependentTasks(int taskId) {
        return tasks.stream()
            .filter(t -> t.getDependencies().contains(taskId))
            .toList();
    }

    /**
     * Validate the dependency graph for cycles
     * Returns true if the graph is acyclic (DAG)
     */
    public boolean isValidDAG() {
        Set<Integer> visiting = new HashSet<>();
        Set<Integer> visited = new HashSet<>();

        for (Task task : tasks) {
            if (hasCycle(task.getId(), visiting, visited)) {
                logger.error("Circular dependency detected in task graph");
                return false;
            }
        }

        return true;
    }

    /**
     * DFS to detect cycles
     */
    private boolean hasCycle(int taskId, Set<Integer> visiting, Set<Integer> visited) {
        if (visited.contains(taskId)) {
            return false;
        }

        if (visiting.contains(taskId)) {
            return true;  // Cycle detected
        }

        visiting.add(taskId);
        Task task = taskMap.get(taskId);

        if (task != null) {
            for (int depId : task.getDependencies()) {
                if (hasCycle(depId, visiting, visited)) {
                    return true;
                }
            }
        }

        visiting.remove(taskId);
        visited.add(taskId);
        return false;
    }

    /**
     * Get execution statistics
     */
    public String getExecutionStats() {
        StringBuilder sb = new StringBuilder();

        sb.append("== Task Dependency Statistics ==\n");
        sb.append(String.format("Total tasks: %d\n", tasks.size()));

        long tasksWithDeps = tasks.stream().filter(Task::hasDependencies).count();
        sb.append(String.format("Tasks with dependencies: %d\n", tasksWithDeps));

        List<Task> criticalPath = getCriticalPath();
        sb.append(String.format("Critical path length: %d\n", criticalPath.size()));
        if (!criticalPath.isEmpty()) {
            sb.append("Critical path: ");
            criticalPath.forEach(t -> sb.append(t.getId()).append(" -> "));
            sb.setLength(sb.length() - 4);
            sb.append("\n");
        }

        return sb.toString();
    }
}
