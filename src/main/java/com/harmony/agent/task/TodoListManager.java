package com.harmony.agent.task;

import com.harmony.agent.cli.ConsolePrinter;
import com.harmony.agent.llm.LLMClient;
import com.harmony.agent.llm.ApiKeyValidator;
import com.harmony.agent.llm.orchestrator.AIMemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Enhanced Todo List Manager with parallel execution support
 * Features:
 * - Task dependency management (DAG)
 * - Smart task routing (Roles + Tools + MCP + Commands)
 * - Error classification (Permanent vs Transient)
 * - Interactive failure handling
 * - Context caching and AI memory integration
 */
public class TodoListManager {
    private static final Logger logger = LoggerFactory.getLogger(TodoListManager.class);

    private final LLMClient llmClient;
    private final ConsolePrinter printer;
    private TodoList activeTodoList;
    private TaskRetryPolicy retryPolicy;
    private TaskExecutionContext executionContext;
    private TaskDependencyResolver dependencyResolver;
    private InteractiveTaskExecutor interactiveExecutor;
    private AIMemoryManager aiMemoryManager;  // AI 记忆管理器

    public TodoListManager(LLMClient llmClient, ConsolePrinter printer) {
        this.llmClient = llmClient;
        this.printer = printer;
        this.retryPolicy = new TaskRetryPolicy();
        this.interactiveExecutor = new InteractiveTaskExecutor(printer);
        this.aiMemoryManager = new AIMemoryManager();  // 初始化 AI 记忆管理器
        logger.info("AI 记忆管理器已初始化");
    }

    /**
     * Create a new todo list from a user requirement
     * 执行链路：API Key 检查 → ToDoList 检查 → 上下文处理 → 需求分析 → ToDoList 生成
     */
    public TodoList createTodoList(String requirement) {
        logger.info("开始创建任务列表，需求: {}", requirement);

        // 第一步：API Key 检查
        if (!ApiKeyValidator.hasValidApiKey()) {
            printer.error(ApiKeyValidator.getApiKeyErrorMessage());
            logger.error("未找到有效的 API Key");
            return null;
        }

        // 显示已配置的提供商
        String providers = ApiKeyValidator.getConfiguredProviders();
        if (!providers.isEmpty()) {
            logger.info("已配置的 LLM 提供商:\n{}", providers);
        }

        // 第二步：ToDoList 检查
        if (activeTodoList != null) {
            printer.warning("当前已有活跃的任务计划。");
            printer.info("当前进度: " + getProgressSummary());
            printer.info("使用 /next 继续执行，或 /tasks 查看所有任务");
            printer.blank();
            printer.info("若要创建新计划，请先完成或清除当前计划:");
            printer.info("  /clear - 清除当前任务计划");
            return null;
        }

        // 第三步：初始化执行上下文
        executionContext = new TaskExecutionContext(requirement);
        retryPolicy = new TaskRetryPolicy();

        // 显示分析进度
        printer.blank();
        printer.spinner("分析需求并生成任务计划...", false);

        try {
            // 第四步：调用 PlannerRole 进行需求分析
            List<String> tasks = llmClient.breakdownRequirement(requirement);

            printer.spinner("分析需求并生成任务计划", true);

            if (tasks == null || tasks.isEmpty()) {
                printer.error("需求分析失败：未生成任务");
                logger.error("PlannerRole 返回空任务列表");
                return null;
            }

            // 第五步：创建 TodoList
            activeTodoList = new TodoList(requirement, tasks);
            executionContext.setAnalysisResult(activeTodoList.getAnalysisResult());

            // 缓存任务列表到 executionContext
            executionContext.cacheTaskList(activeTodoList.getAllTasks());

            // 初始化依赖解析器（用于并行执行）
            dependencyResolver = new TaskDependencyResolver(activeTodoList.getAllTasks());

            // 验证依赖关系是否正确（无循环依赖）
            if (!dependencyResolver.isValidDAG()) {
                printer.error("任务间存在循环依赖，无法执行");
                logger.error("Task dependency graph contains cycles");
                activeTodoList = null;
                return null;
            }

            // 显示生成的任务列表
            printer.blank();
            printer.success("✨ 需求分析完成！已生成 " + tasks.size() + " 个任务");
            displayTodoList(true);

            // 显示依赖关系统计
            printer.blank();
            printer.info(dependencyResolver.getExecutionStats());

            // 自动启动第一个任务
            if (activeTodoList.startCurrentTask()) {
                printer.blank();
                printer.info("开始执行第一个任务...");
                displayCurrentTask();
            }

            logger.info("成功创建任务列表，共 {} 个任务", tasks.size());
            return activeTodoList;

        } catch (Exception e) {
            printer.spinner("分析需求", true);
            printer.error("创建任务列表失败: " + e.getMessage());
            logger.error("创建任务列表时发生异常", e);
            activeTodoList = null;
            return null;
        }
    }

    /**
     * Execute the current task with enhanced error handling and routing
     * Features:
     * - Smart task routing (Roles + Tools + MCP + Commands)
     * - Error classification (Permanent vs Transient)
     * - Interactive failure handling
     */
    public boolean executeCurrentTask() {
        if (activeTodoList == null) {
            printer.warning("没有活跃的任务计划。");
            printer.info("使用 /plan <需求> 创建一个新的任务计划。");
            return false;
        }

        Optional<Task> currentTask = activeTodoList.getCurrentTask();
        if (currentTask.isEmpty()) {
            handleAllTasksCompleted();
            return false;
        }

        Task task = currentTask.get();

        // 检查该任务是否已被跳过
        if (retryPolicy.isTaskSkipped(task.getId())) {
            printer.warning(String.format("任务 %d 已被跳过（失败次数过多）", task.getId()));
            printer.info(String.format("失败原因: %s", retryPolicy.getFailureReason(task.getId())));
            activeTodoList.skipTask(task.getId());
            return executeCurrentTask();
        }

        printer.blank();
        printer.spinner(String.format("执行任务 %d/%d: %s",
            task.getId(), activeTodoList.getTotalTaskCount(), task.getDescription()), false);

        try {
            // 构建包含完整上下文的执行环境
            String context = buildContext();

            // 智能路由（Roles + Tools + MCP + Commands）
            AdvancedTaskRouter.RouteDecision routeDecision = AdvancedTaskRouter.route(
                task.getDescription(), executionContext);

            printer.info(AdvancedTaskRouter.getRoutingExplanation(routeDecision));

            // 获取执行类型
            AdvancedTaskRouter.ExecutionType executionType = AdvancedTaskRouter.getExecutionType(routeDecision);

            // 执行任务
            String output = executeTaskByType(task, executionType, routeDecision, context);

            printer.spinner("执行任务", true);

            // 任务成功，记录结果
            activeTodoList.completeCurrentTask(output);
            task.setFailureCount(0);  // Reset failure count on success
            retryPolicy.recordTaskSuccess(task.getId());
            executionContext.recordTaskResult(task.getId(), task.getDescription(), output, true);

            printer.blank();
            printer.success(String.format("✓ 任务 %d 完成: %s", task.getId(), task.getDescription()));

            // 检查是否还有更多任务
            if (!activeTodoList.isCompleted()) {
                // 获取下一个可以执行的任务（支持并行依赖）
                if (dependencyResolver != null) {
                    List<Task> readyTasks = dependencyResolver.getReadyTasks();
                    if (readyTasks.isEmpty()) {
                        printer.warning("没有可执行的任务（所有任务都在等待依赖）");
                        return false;
                    }
                }

                // 启动下一个任务
                activeTodoList.startCurrentTask();
                printer.blank();
                displayCurrentTask();
                return true;
            } else {
                handleAllTasksCompleted();
                return false;
            }

        } catch (Exception e) {
            printer.spinner("执行任务", true);

            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            task.setLastErrorMessage(errorMsg);
            task.incrementFailureCount();

            logger.error("任务执行失败", e);

            // 对错误进行分类
            ErrorClassifier.ErrorType errorType = ErrorClassifier.classify(errorMsg);
            int maxRetries = ErrorClassifier.getMaxRetries(errorType);

            // 应用重试策略
            boolean shouldRetry = retryPolicy.recordTaskFailure(task.getId(), errorMsg);

            if (shouldRetry && task.getFailureCount() < maxRetries) {
                printer.warning(String.format("⚠️ 任务 %d 执行失败（第 %d 次尝试），将进行重试...",
                    task.getId(), task.getFailureCount()));
                printer.info(String.format("错误类型: %s, 失败原因: %s", errorType, errorMsg));

                // 等待后重试
                retryPolicy.waitBeforeRetry();
                return executeCurrentTask();
            } else {
                // 任务已达到最大重试次数，提示用户选择
                InteractiveTaskExecutor.UserChoice choice =
                    interactiveExecutor.handleTaskFailure(task, errorMsg, task.getFailureCount());

                return handleUserChoice(choice, task);
            }

        }
    }

    /**
     * Execute task by type (Role/Tool/MCP/Command)
     */
    private String executeTaskByType(Task task, AdvancedTaskRouter.ExecutionType type,
                                     AdvancedTaskRouter.RouteDecision decision, String context) {
        return switch (type) {
            case ROLE -> {
                String roleName = AdvancedTaskRouter.getLLMRole(decision);
                yield llmClient.executeTask(task.getDescription(), context);
            }
            case LOCAL_TOOL -> {
                String toolName = AdvancedTaskRouter.getLocalTool(decision);
                yield executeLocalTool(toolName, task);
            }
            case MCP_TOOL -> {
                String mcpTool = AdvancedTaskRouter.getMCPTool(decision);
                yield executeMCPTool(mcpTool, task);
            }
            case COMMAND -> {
                String command = AdvancedTaskRouter.getCLICommand(decision);
                yield executeCommand(command, task);
            }
            default -> "Unknown execution type";
        };
    }

    /**
     * Execute local tool
     */
    private String executeLocalTool(String toolName, Task task) {
        // Placeholder: will be integrated with ToolExecutor
        logger.info("Executing local tool: {}", toolName);
        return String.format("Local tool '%s' executed", toolName);
    }

    /**
     * Execute MCP tool
     */
    private String executeMCPTool(String mcpTool, Task task) {
        // Placeholder: will be integrated with MCPClientManager
        logger.info("Executing MCP tool: {}", mcpTool);
        return String.format("MCP tool '%s' executed", mcpTool);
    }

    /**
     * Execute CLI command
     */
    private String executeCommand(String command, Task task) {
        // Placeholder: will be integrated with command execution
        logger.info("Executing command: {}", command);
        return String.format("Command '%s' executed", command);
    }

    /**
     * Handle user choice after task failure
     */
    private boolean handleUserChoice(InteractiveTaskExecutor.UserChoice choice, Task task) {
        return switch (choice) {
            case RETRY_ONCE -> {
                printer.warning("重新尝试任务...");
                task.setFailureCount(task.getFailureCount() - 1);  // Allow one more retry
                yield executeCurrentTask();
            }
            case SKIP_TASK -> {
                printer.error(String.format("❌ 任务 %d 已被跳过", task.getId()));
                activeTodoList.skipTask(task.getId());
                yield executeCurrentTask();
            }
            case ABORT_PLAN -> {
                printer.error("计划已被用户中止");
                activeTodoList = null;
                yield false;
            }
        };
    }

    /**
     * 处理所有任务完成的情况
     */
    private void handleAllTasksCompleted() {
        printer.blank();
        printer.success("🎉 所有任务已完成！");
        displayTodoList(true);

        // 显示被跳过的任务（如果有）
        if (!retryPolicy.getSkippedTasks().isEmpty()) {
            printer.blank();
            printer.warning("⚠️ 有些任务在重试后仍然失败:");
            retryPolicy.getSkippedTasks().forEach(taskId -> {
                printer.info(String.format("  - 任务 %d: %s", taskId,
                    retryPolicy.getFailureReason(taskId)));
            });
            printer.blank();
            printer.info("请检查上述失败任务的错误信息，或手动处理");
        }

        activeTodoList = null;  // 清除活跃列表
        executionContext = null;
        retryPolicy = null;
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
     * Build context from completed tasks and analysis result
     * 包含原始需求、规划分析结果、已完成任务的输出、以及 AI 记忆信息
     */
    private String buildContext() {
        if (activeTodoList == null) {
            return "";
        }

        StringBuilder context = new StringBuilder();

        // 添加原始需求
        context.append("## 原始需求\n");
        context.append(activeTodoList.getRequirement()).append("\n\n");

        // 添加规划分析结果（如果有）
        String analysisResult = activeTodoList.getAnalysisResult();
        if (analysisResult != null && !analysisResult.isEmpty()) {
            context.append("## 规划分析结果\n");
            context.append(analysisResult).append("\n\n");
        }

        // 添加已完成的任务及其输出
        List<Task> completedTasks = activeTodoList.getCompletedTasks();
        if (!completedTasks.isEmpty()) {
            context.append("## 已完成的任务\n");
            for (Task task : completedTasks) {
                context.append(String.format("- 任务 %d: %s\n", task.getId(), task.getDescription()));
                if (task.getOutput() != null && !task.getOutput().isEmpty()) {
                    context.append(String.format("  输出: %s\n", task.getOutput()));
                }
            }
            context.append("\n");
        }

        // 添加会话上下文摘要（来自缓存）
        String sessionContextSummary = executionContext.getSessionContextSummary();
        if (sessionContextSummary != null && !sessionContextSummary.isEmpty()) {
            context.append(sessionContextSummary).append("\n");
        }

        // 添加 AI 记忆信息（已知的代码问题库）
        String issueContext = aiMemoryManager.buildIssueContext();
        if (issueContext != null && !issueContext.isEmpty()) {
            context.append(issueContext).append("\n");
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

    // ==================== 缓存和 AI 记忆查询方法 ====================

    /**
     * 获取当前会话的缓存统计信息
     */
    public String getCacheStats() {
        if (executionContext != null) {
            return executionContext.getCacheStats();
        }
        return "没有活跃的任务上下文缓存";
    }

    /**
     * 获取 AI 记忆统计信息
     */
    public String getAIMemoryStats() {
        return aiMemoryManager.getCacheStats();
    }

    /**
     * 获取完整的上下文和记忆报告
     */
    public String getFullContextReport() {
        StringBuilder report = new StringBuilder();
        report.append("═══════════════════════════════════════\n");
        report.append("📊 任务执行上下文和 AI 记忆报告\n");
        report.append("═══════════════════════════════════════\n\n");

        // 添加任务上下文缓存信息
        report.append(getCacheStats()).append("\n\n");

        // 添加 AI 记忆统计
        report.append(getAIMemoryStats()).append("\n\n");

        // 添加当前会话摘要
        if (executionContext != null) {
            report.append(executionContext.getSessionContextSummary()).append("\n");
        }

        return report.toString();
    }

    /**
     * 获取 AI 记忆管理器（允许外部访问）
     */
    public AIMemoryManager getAIMemoryManager() {
        return aiMemoryManager;
    }

    /**
     * 获取任务执行上下文（允许外部访问）
     */
    public TaskExecutionContext getExecutionContext() {
        return executionContext;
    }
}
