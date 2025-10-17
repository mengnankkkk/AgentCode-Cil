package com.harmony.agent.llm.provider;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;

/**
 * OpenAI provider implementation
 * Supports GPT-3.5, GPT-4, and other OpenAI models
 */
public class OpenAIProvider extends BaseLLMProvider {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private static final String[] AVAILABLE_MODELS = {
        "gpt-3.5-turbo",
        "gpt-3.5-turbo-16k",
        "gpt-4",
        "gpt-4-turbo-preview",
        "gpt-4-32k"
    };

    public OpenAIProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public OpenAIProvider(String apiKey, String baseUrl) {
        super(apiKey, baseUrl);
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String[] getAvailableModels() {
        return AVAILABLE_MODELS;
    }

    @Override
    protected LLMResponse sendHttpRequest(LLMRequest request) {
        // TODO: Phase 3 - Implement actual HTTP request using HttpClient
        logger.warn("OpenAI HTTP request not yet implemented (Phase 3)");

        // Placeholder response for Phase 2
        return LLMResponse.builder()
            .content("OpenAI response placeholder. Real API integration coming in Phase 3.")
            .model(request.getModel())
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .success(true)
            .build();
    }
}
