package com.harmony.agent.llm.orchestrator;

import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.provider.LLMProvider;
import com.harmony.agent.llm.provider.ProviderFactory;
import com.harmony.agent.llm.role.LLMRole;
import com.harmony.agent.llm.role.RoleFactory;
import com.harmony.agent.task.TodoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates workflow between different LLM roles
 * Manages Provider-Role mapping and execution flow
 */
public class LLMOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(LLMOrchestrator.class);

    private final ProviderFactory providerFactory;
    private final RoleFactory roleFactory;
    private final Map<String, RoleConfig> roleConfigs;
    private final AIMemoryManager aiMemoryManager;

    public LLMOrchestrator(ProviderFactory providerFactory, RoleFactory roleFactory) {
        this.providerFactory = providerFactory;
        this.roleFactory = roleFactory;
        this.roleConfigs = new HashMap<>();
        this.aiMemoryManager = new AIMemoryManager();
        logger.info("LLMOrchestrator initialized with AI memory management");
    }

    /**
     * Configure a role with specific provider and model
     */
    public void configureRole(String roleName, String providerName, String model) {
        roleConfigs.put(roleName, new RoleConfig(providerName, model));
        logger.info("Configured role '{}' to use provider '{}' with model '{}'",
            roleName, providerName, model);
    }

    /**
     * Execute a role with automatic provider/model configuration
     */
    public LLMResponse executeRole(String roleName, String input, ConversationContext context) {
        logger.info("Executing role: {}", roleName);

        // Get role
        LLMRole role = roleFactory.getRole(roleName);

        // Get role configuration
        RoleConfig config = roleConfigs.get(roleName);
        if (config == null) {
            return LLMResponse.builder()
                .errorMessage("Role not configured: " + roleName)
                .build();
        }

        // Get provider
        LLMProvider provider = providerFactory.getProvider(config.providerName);

        // Configure role
        role.setProvider(provider);
        role.setModel(config.model);

        // Build context string
        String contextString = context != null ? context.buildContextString() : "";

        // Execute role
        return role.execute(input, contextString);
    }

    /**
     * Analyze requirement and create todo list
     */
    public TodoList analyzeRequirement(String requirement) {
        logger.info("Analyzing requirement...");

        ConversationContext context = new ConversationContext(requirement);
        LLMResponse response = executeRole("analyzer", requirement, context);

        if (!response.isSuccess()) {
            logger.error("Failed to analyze requirement: {}", response.getErrorMessage());
            return null;
        }

        // Parse response into todo list
        List<String> tasks = parseTasksFromResponse(response.getContent());
        TodoList todoList = new TodoList(requirement, tasks);

        context.setTodoList(todoList);
        return todoList;
    }

    /**
     * Create design document
     */
    public String createDesign(ConversationContext context, String specificTask) {
        logger.info("Creating design document...");

        LLMResponse response = executeRole("planner", specificTask, context);

        if (!response.isSuccess()) {
            logger.error("Failed to create design: {}", response.getErrorMessage());
            return null;
        }

        String design = response.getContent();
        context.setDesignDocument(design);
        return design;
    }

    /**
     * Generate code
     */
    public String generateCode(ConversationContext context, String taskDescription) {
        logger.info("Generating code...");

        LLMResponse response = executeRole("coder", taskDescription, context);

        if (!response.isSuccess()) {
            logger.error("Failed to generate code: {}", response.getErrorMessage());
            return null;
        }

        return response.getContent();
    }

    /**
     * Review code
     */
    public String reviewCode(ConversationContext context, String code) {
        logger.info("Reviewing code...");

        String input = "Please review the following code:\n\n" + code;
        LLMResponse response = executeRole("reviewer", input, context);

        if (!response.isSuccess()) {
            logger.error("Failed to review code: {}", response.getErrorMessage());
            return null;
        }

        return response.getContent();
    }

    /**
     * Parse tasks from LLM response
     */
    private List<String> parseTasksFromResponse(String response) {
        // Simple parsing: split by newlines and extract numbered items
        return response.lines()
            .map(String::trim)
            .filter(line -> line.matches("^\\d+\\..*"))
            .map(line -> line.replaceFirst("^\\d+\\.\\s*", ""))
            .toList();
    }

    /**
     * Get AI Memory Manager for storing/retrieving memories
     */
    public AIMemoryManager getAIMemoryManager() {
        return aiMemoryManager;
    }

    /**
     * Role configuration
     */
    private static class RoleConfig {
        final String providerName;
        final String model;

        RoleConfig(String providerName, String model) {
            this.providerName = providerName;
            this.model = model;
        }
    }

    /**
     * Builder for creating configured orchestrator
     */
    public static class Builder {
        private final ProviderFactory providerFactory;
        private final RoleFactory roleFactory;
        private final Map<String, RoleConfig> configs = new HashMap<>();

        public Builder(ProviderFactory providerFactory, RoleFactory roleFactory) {
            this.providerFactory = providerFactory;
            this.roleFactory = roleFactory;
        }

        public Builder configureRole(String roleName, String providerName, String model) {
            configs.put(roleName, new RoleConfig(providerName, model));
            return this;
        }

        public LLMOrchestrator build() {
            LLMOrchestrator orchestrator = new LLMOrchestrator(providerFactory, roleFactory);
            configs.forEach((role, config) ->
                orchestrator.configureRole(role, config.providerName, config.model)
            );
            return orchestrator;
        }
    }

    public static Builder builder(ProviderFactory providerFactory, RoleFactory roleFactory) {
        return new Builder(providerFactory, roleFactory);
    }
}
