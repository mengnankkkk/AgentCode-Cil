package com.harmony.agent.llm.role;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.provider.LLMProvider;

/**
 * Interface for LLM roles (Analyzer, Planner, Coder, Reviewer)
 * Strategy pattern for task-specific AI behaviors
 */
public interface LLMRole {

    /**
     * Get role name
     * @return Role name (e.g., "analyzer", "coder")
     */
    String getRoleName();

    /**
     * Get role description
     * @return Human-readable role description
     */
    String getRoleDescription();

    /**
     * Get system prompt for this role
     * @return System prompt that defines role behavior
     */
    String getSystemPrompt();

    /**
     * Get recommended temperature for this role
     * @return Temperature value (0.0 - 1.0)
     */
    double getRecommendedTemperature();

    /**
     * Get recommended max tokens for this role
     * @return Max tokens value
     */
    int getRecommendedMaxTokens();

    /**
     * Execute role-specific task
     * @param input User input/task description
     * @param context Additional context
     * @return LLM response
     */
    LLMResponse execute(String input, String context);

    /**
     * Set the LLM provider for this role
     * @param provider LLM provider
     */
    void setProvider(LLMProvider provider);

    /**
     * Set the model name for this role
     * @param model Model name
     */
    void setModel(String model);
}
