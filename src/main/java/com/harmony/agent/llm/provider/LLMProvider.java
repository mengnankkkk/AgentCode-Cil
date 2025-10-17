package com.harmony.agent.llm.provider;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;

/**
 * Interface for LLM providers (OpenAI, Claude, etc.)
 * Strategy pattern for supporting multiple LLM APIs
 */
public interface LLMProvider {

    /**
     * Send request to LLM provider
     * @param request LLM request
     * @return LLM response
     */
    LLMResponse sendRequest(LLMRequest request);

    /**
     * Get provider name
     * @return Provider name (e.g., "openai", "claude")
     */
    String getProviderName();

    /**
     * Check if provider is available (API key configured, etc.)
     * @return true if available
     */
    boolean isAvailable();

    /**
     * Get available models for this provider
     * @return Array of model names
     */
    String[] getAvailableModels();

    /**
     * Validate model name
     * @param model Model name
     * @return true if model is supported by this provider
     */
    boolean supportsModel(String model);
}
