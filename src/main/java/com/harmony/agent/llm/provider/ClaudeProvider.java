package com.harmony.agent.llm.provider;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;

/**
 * Claude (Anthropic) provider implementation
 * Supports Claude 3 Haiku, Sonnet, and Opus models
 */
public class ClaudeProvider extends BaseLLMProvider {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";

    private static final String[] AVAILABLE_MODELS = {
        "claude-3-haiku-20240307",
        "claude-3-sonnet-20240229",
        "claude-3-opus-20240229",
        "claude-3-5-sonnet-20241022"
    };

    public ClaudeProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public ClaudeProvider(String apiKey, String baseUrl) {
        super(apiKey, baseUrl);
    }

    @Override
    public String getProviderName() {
        return "claude";
    }

    @Override
    public String[] getAvailableModels() {
        return AVAILABLE_MODELS;
    }

    @Override
    protected LLMResponse sendHttpRequest(LLMRequest request) {
        // TODO: Phase 3 - Implement actual HTTP request using HttpClient
        logger.warn("Claude HTTP request not yet implemented (Phase 3)");

        // Placeholder response for Phase 2
        return LLMResponse.builder()
            .content("Claude response placeholder. Real API integration coming in Phase 3.")
            .model(request.getModel())
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .success(true)
            .build();
    }
}
