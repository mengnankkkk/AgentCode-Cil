package com.harmony.agent.llm.provider;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation for LLM providers
 * Provides common functionality for all providers
 */
public abstract class BaseLLMProvider implements LLMProvider {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final String apiKey;
    protected final String baseUrl;

    protected BaseLLMProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public boolean supportsModel(String model) {
        String[] models = getAvailableModels();
        for (String m : models) {
            if (m.equals(model)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Send HTTP request to LLM API
     * @param request LLM request
     * @return LLM response
     */
    protected abstract LLMResponse sendHttpRequest(LLMRequest request);

    @Override
    public LLMResponse sendRequest(LLMRequest request) {
        if (!isAvailable()) {
            return LLMResponse.builder()
                .errorMessage("Provider " + getProviderName() + " is not available (API key not configured)")
                .build();
        }

        if (!supportsModel(request.getModel())) {
            return LLMResponse.builder()
                .errorMessage("Model " + request.getModel() + " is not supported by " + getProviderName())
                .build();
        }

        try {
            logger.debug("Sending request to {} with model {}", getProviderName(), request.getModel());
            return sendHttpRequest(request);
        } catch (Exception e) {
            logger.error("Failed to send request to " + getProviderName(), e);
            return LLMResponse.builder()
                .errorMessage("Failed to send request: " + e.getMessage())
                .build();
        }
    }
}
