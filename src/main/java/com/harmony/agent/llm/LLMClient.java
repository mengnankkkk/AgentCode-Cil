package com.harmony.agent.llm;

import com.harmony.agent.config.AppConfig;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.orchestrator.ConversationContext;
import com.harmony.agent.llm.orchestrator.LLMOrchestrator;
import com.harmony.agent.llm.provider.ProviderFactory;
import com.harmony.agent.llm.role.RoleFactory;
import com.harmony.agent.task.TodoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM Client - Facade for LLM operations
 * Now powered by the dual-strategy architecture (Provider + Role)
 */
public class LLMClient {
    private static final Logger logger = LoggerFactory.getLogger(LLMClient.class);

    private final ConfigManager configManager;
    private final LLMOrchestrator orchestrator;
    private final boolean useRealLLM;

    public LLMClient(ConfigManager configManager) {
        this.configManager = configManager;

        // Initialize orchestrator with providers and roles
        try {
            ProviderFactory providerFactory = createProviderFactory();
            RoleFactory roleFactory = RoleFactory.createDefault();

            this.orchestrator = LLMOrchestrator.builder(providerFactory, roleFactory)
                .configureRole("analyzer", getProviderForRole("analyzer"), getModelForRole("analyzer"))
                .configureRole("planner", getProviderForRole("planner"), getModelForRole("planner"))
                .configureRole("coder", getProviderForRole("coder"), getModelForRole("coder"))
                .configureRole("reviewer", getProviderForRole("reviewer"), getModelForRole("reviewer"))
                .build();

            this.useRealLLM = isAvailable();
            if (useRealLLM) {
                logger.info("LLMClient initialized with real LLM providers");

                // Configure rate limiter
                configureRateLimiter();
            } else {
                logger.warn("LLMClient initialized in fallback mode (no API keys configured)");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize LLM orchestrator", e);
            throw new RuntimeException("Failed to initialize LLM client", e);
        }
    }

    /**
     * Configure rate limiter based on application configuration
     */
    private void configureRateLimiter() {
        AppConfig.AiConfig aiConfig = configManager.getConfig().getAi();

        String mode = aiConfig.getRateLimitMode();
        double qpsLimit = aiConfig.getRequestsPerSecondLimit();
        int tpmLimit = aiConfig.getTokensPerMinuteLimit();
        double safetyMargin = aiConfig.getSafetyMargin();

        // Configure the rate limiter in BaseLLMProvider
        com.harmony.agent.llm.provider.BaseLLMProvider.configureRateLimiter(
            mode, qpsLimit, tpmLimit, safetyMargin
        );

        logger.info("Rate limiter configured: mode={}, qpsLimit={}, tpmLimit={}, safetyMargin={}",
            mode, qpsLimit, tpmLimit, safetyMargin);
    }

    /**
     * Create provider factory from configuration
     */
    private ProviderFactory createProviderFactory() {
        String openaiKey = System.getenv("OPENAI_API_KEY");
        String claudeKey = System.getenv("CLAUDE_API_KEY");
        String siliconflowKey = System.getenv("SILICONFLOW_API_KEY");

        // Fallback to config if env vars not set
        if (openaiKey == null || openaiKey.isEmpty()) {
            AppConfig.ProviderConfig openaiConfig = configManager.getConfig().getAi().getProviders().get("openai");
            if (openaiConfig != null) {
                openaiKey = openaiConfig.getApiKey();
            }
        }

        if (claudeKey == null || claudeKey.isEmpty()) {
            AppConfig.ProviderConfig claudeConfig = configManager.getConfig().getAi().getProviders().get("claude");
            if (claudeConfig != null) {
                claudeKey = claudeConfig.getApiKey();
            }
        }

        if (siliconflowKey == null || siliconflowKey.isEmpty()) {
            AppConfig.ProviderConfig siliconflowConfig = configManager.getConfig().getAi().getProviders().get("siliconflow");
            if (siliconflowConfig != null) {
                siliconflowKey = siliconflowConfig.getApiKey();
            }
        }

        return ProviderFactory.createDefault(openaiKey, claudeKey, siliconflowKey);
    }

    /**
     * Get provider name for a role from config
     */
    private String getProviderForRole(String roleName) {
        // Check if role is configured in config file
        AppConfig.RoleConfig roleConfig = configManager.getConfig().getAi().getRoles().get(roleName);
        if (roleConfig != null && roleConfig.getProvider() != null) {
            return roleConfig.getProvider();
        }

        // Default mappings (fallback)
        return switch (roleName) {
            case "analyzer" -> "openai";
            case "planner", "coder", "reviewer" -> "claude";
            // Note: "tester" is not an LLM role - it executes tools
            default -> "openai";
        };
    }

    /**
     * Get model name for a role from config
     */
    private String getModelForRole(String roleName) {
        // Check if role is configured in config file
        AppConfig.RoleConfig roleConfig = configManager.getConfig().getAi().getRoles().get(roleName);
        if (roleConfig != null && roleConfig.getModel() != null) {
            String model = roleConfig.getModel();

            // If model is a reference like "fast", "standard", "premium", resolve it
            if (model.matches("fast|standard|premium")) {
                String provider = getProviderForRole(roleName);
                AppConfig.ProviderConfig providerConfig = configManager.getConfig().getAi().getProviders().get(provider);
                if (providerConfig != null && providerConfig.getModels().containsKey(model)) {
                    return providerConfig.getModels().get(model);
                }
            }

            return model;
        }

        // Default mappings (fallback)
        return switch (roleName) {
            case "analyzer" -> "gpt-3.5-turbo";
            case "planner" -> "claude-3-sonnet-20240229";
            case "coder" -> "claude-3-sonnet-20240229";
            case "reviewer" -> "claude-3-opus-20240229";
            // Note: "tester" is not an LLM role - it executes tools
            default -> configManager.getConfig().getAi().getModel();
        };
    }

    /**
     * Chat with AI
     * @param input User input
     * @param history Conversation history
     * @return AI response
     */
    public String chat(String input, List<String> history) {
        logger.info("LLM chat request: {}", input);

        if (!useRealLLM) {
            return "AI chat functionality requires API keys. " +
                   "Please set OPENAI_API_KEY or CLAUDE_API_KEY environment variable.";
        }

        // Use planner role for general chat (good reasoning ability)
        ConversationContext context = new ConversationContext(input);

        // Add history to context
        if (history != null && !history.isEmpty()) {
            context.setMetadata("chat_history", String.join("\n", history));
        }

        LLMResponse response = orchestrator.executeRole("planner", input, context);

        if (response.isSuccess()) {
            return response.getContent();
        } else {
            return "Error: " + response.getErrorMessage();
        }
    }

    /**
     * Check if LLM is configured and available
     */
    public boolean isAvailable() {
        String openaiKey = System.getenv("OPENAI_API_KEY");
        String claudeKey = System.getenv("CLAUDE_API_KEY");
        String siliconflowKey = System.getenv("SILICONFLOW_API_KEY");
        String configKey = configManager.getConfig().getAi().getApiKey();

        return (openaiKey != null && !openaiKey.isEmpty()) ||
               (claudeKey != null && !claudeKey.isEmpty()) ||
               (siliconflowKey != null && !siliconflowKey.isEmpty()) ||
               (configKey != null && !configKey.isEmpty());
    }

    /**
     * Break down a user requirement into actionable tasks
     * Now uses the Analyzer role from the new architecture
     *
     * @param requirement User's high-level requirement
     * @return List of task descriptions
     */
    public List<String> breakdownRequirement(String requirement) {
        logger.info("Breaking down requirement: {}", requirement);

        if (!useRealLLM) {
            // Fallback to rule-based approach if no API keys
            return breakdownRequirementFallback(requirement);
        }

        try {
            // Use the Analyzer role through orchestrator
            TodoList todoList = orchestrator.analyzeRequirement(requirement);

            if (todoList != null) {
                List<String> tasks = new ArrayList<>();
                todoList.getAllTasks().forEach(task -> tasks.add(task.getDescription()));
                logger.info("Generated {} tasks from requirement using Analyzer role", tasks.size());
                return tasks;
            } else {
                logger.warn("Orchestrator returned null, falling back to rule-based");
                return breakdownRequirementFallback(requirement);
            }
        } catch (Exception e) {
            logger.error("Failed to breakdown requirement using LLM", e);
            return breakdownRequirementFallback(requirement);
        }
    }

    /**
     * Fallback rule-based task breakdown
     */
    private List<String> breakdownRequirementFallback(String requirement) {
        List<String> tasks = new ArrayList<>();

        // Analyze requirement and generate tasks
        if (requirement.toLowerCase().contains("analyze")) {
            tasks.add("Read and parse the target code files");
            tasks.add("Identify potential security vulnerabilities");
            tasks.add("Generate analysis report with findings");
            tasks.add("Provide recommendations for fixes");
        } else if (requirement.toLowerCase().contains("refactor")) {
            tasks.add("Analyze current code structure");
            tasks.add("Identify code smells and improvement areas");
            tasks.add("Design refactoring strategy");
            tasks.add("Apply refactoring changes");
            tasks.add("Verify functionality with tests");
        } else if (requirement.toLowerCase().contains("test")) {
            tasks.add("Analyze code to be tested");
            tasks.add("Design test cases and scenarios");
            tasks.add("Implement unit tests");
            tasks.add("Implement integration tests");
            tasks.add("Run tests and verify coverage");
        } else if (requirement.toLowerCase().contains("implement") || requirement.toLowerCase().contains("add")) {
            tasks.add("Understand requirements and specifications");
            tasks.add("Design implementation approach");
            tasks.add("Write core implementation code");
            tasks.add("Add error handling and validation");
            tasks.add("Write tests for new functionality");
            tasks.add("Document the implementation");
        } else {
            // Generic task breakdown
            tasks.add("Analyze the requirement and gather context");
            tasks.add("Design the solution approach");
            tasks.add("Implement the core functionality");
            tasks.add("Test and validate the implementation");
            tasks.add("Document changes and create summary");
        }

        logger.info("Generated {} tasks from requirement (fallback mode)", tasks.size());
        return tasks;
    }

    /**
     * Execute a single task and return the output
     * Now intelligently routes to appropriate role based on task type
     *
     * @param taskDescription The task to execute
     * @param context Additional context from previous tasks
     * @return Task execution result
     */
    public String executeTask(String taskDescription, String context) {
        logger.info("Executing task: {}", taskDescription);

        if (!useRealLLM) {
            return executeFallback(taskDescription);
        }

        try {
            // Determine which role to use based on task description
            String roleName = determineRoleForTask(taskDescription);

            ConversationContext ctx = new ConversationContext(taskDescription);
            if (context != null && !context.isEmpty()) {
                ctx.setMetadata("previous_context", context);
            }

            LLMResponse response = orchestrator.executeRole(roleName, taskDescription, ctx);

            if (response.isSuccess()) {
                logger.info("Task executed successfully using {} role", roleName);
                return response.getContent();
            } else {
                logger.warn("Task execution failed: {}, falling back", response.getErrorMessage());
                return executeFallback(taskDescription);
            }
        } catch (Exception e) {
            logger.error("Failed to execute task using LLM", e);
            return executeFallback(taskDescription);
        }
    }

    /**
     * Execute a specific role with a prompt and return content
     * This is a convenience method for direct role execution
     *
     * @param roleName The role to execute (planner, coder, reviewer, tester)
     * @param prompt The prompt to send to the role
     * @return The response content as string
     * @throws RuntimeException if execution fails
     */
    public String executeRole(String roleName, String prompt) {
        logger.info("Executing {} role with prompt length: {}", roleName, prompt.length());

        if (!useRealLLM) {
            throw new RuntimeException("LLM is not available. Please configure API keys.");
        }

        try {
            ConversationContext context = new ConversationContext(prompt);
            LLMResponse response = orchestrator.executeRole(roleName, prompt, context);

            if (response.isSuccess()) {
                logger.info("{} role executed successfully", roleName);
                return response.getContent();
            } else {
                throw new RuntimeException("Role execution failed: " + response.getErrorMessage());
            }
        } catch (Exception e) {
            logger.error("Failed to execute {} role", roleName, e);
            throw new RuntimeException("Failed to execute " + roleName + " role: " + e.getMessage(), e);
        }
    }

    /**
     * Determine which role should handle a task
     */
    private String determineRoleForTask(String taskDescription) {
        String lower = taskDescription.toLowerCase();

        // Design, architecture, planning tasks
        if (lower.contains("design") || lower.contains("architect") ||
            lower.contains("plan") || lower.contains("strategy")) {
            return "planner";
        }

        // Code implementation tasks
        if (lower.contains("implement") || lower.contains("code") ||
            lower.contains("write") || lower.contains("create") ||
            lower.contains("develop")) {
            return "coder";
        }

        // Review, analyze, verify tasks
        if (lower.contains("review") || lower.contains("verify") ||
            lower.contains("check") || lower.contains("validate")) {
            return "reviewer";
        }

        // Analysis tasks
        if (lower.contains("analyze") || lower.contains("identify") ||
            lower.contains("find") || lower.contains("detect")) {
            return "analyzer";
        }

        // Default to planner for general tasks
        return "planner";
    }

    /**
     * Fallback execution for when LLM is not available
     */
    private String executeFallback(String taskDescription) {
        return String.format(
            "Task '%s' queued for execution.\n" +
            "Note: Real LLM execution requires API keys. " +
            "Set OPENAI_API_KEY or CLAUDE_API_KEY environment variable.\n" +
            "Current mode: Fallback/Demo mode",
            taskDescription
        );
    }

    /**
     * Get the orchestrator (for advanced usage)
     */
    public LLMOrchestrator getOrchestrator() {
        return orchestrator;
    }

    /**
     * Check if using real LLM or fallback mode
     */
    public boolean isUsingRealLLM() {
        return useRealLLM;
    }
}
