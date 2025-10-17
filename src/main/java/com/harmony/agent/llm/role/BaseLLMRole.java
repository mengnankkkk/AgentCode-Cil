package com.harmony.agent.llm.role;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.model.Message;
import com.harmony.agent.llm.provider.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation for LLM roles
 */
public abstract class BaseLLMRole implements LLMRole {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected LLMProvider provider;
    protected String model;

    @Override
    public void setProvider(LLMProvider provider) {
        this.provider = provider;
    }

    @Override
    public void setModel(String model) {
        this.model = model;
    }

    @Override
    public LLMResponse execute(String input, String context) {
        if (provider == null) {
            return LLMResponse.builder()
                .errorMessage("Provider not set for role: " + getRoleName())
                .build();
        }

        // Build request with role-specific parameters
        LLMRequest.Builder requestBuilder = LLMRequest.builder()
            .addSystemMessage(getSystemPrompt())
            .temperature(getRecommendedTemperature())
            .maxTokens(getRecommendedMaxTokens())
            .model(model != null ? model : "gpt-3.5-turbo");

        // Add context if provided
        if (context != null && !context.isEmpty()) {
            requestBuilder.addUserMessage("Context:\n" + context);
        }

        // Add main input
        requestBuilder.addUserMessage(input);

        LLMRequest request = requestBuilder.build();

        logger.info("Executing {} role with provider {}", getRoleName(), provider.getProviderName());
        return provider.sendRequest(request);
    }

    /**
     * Build a formatted prompt with context
     */
    protected String buildPromptWithContext(String input, String context) {
        StringBuilder prompt = new StringBuilder();

        if (context != null && !context.isEmpty()) {
            prompt.append("## Context\n");
            prompt.append(context);
            prompt.append("\n\n");
        }

        prompt.append("## Task\n");
        prompt.append(input);

        return prompt.toString();
    }
}
