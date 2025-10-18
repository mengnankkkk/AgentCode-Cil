package com.harmony.agent.llm.provider;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;

/**
 * SiliconFlow (硅基流动) provider implementation
 * Supports various open-source models via SiliconFlow API
 * Compatible with OpenAI API format
 */
public class SiliconFlowProvider extends BaseLLMProvider {

    private static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1";

    private static final String[] AVAILABLE_MODELS = {
        // Qwen (通义千问) 系列
        "Qwen/Qwen2.5-7B-Instruct",
        "Qwen/Qwen2.5-14B-Instruct",
        "Qwen/Qwen2.5-32B-Instruct",
        "Qwen/Qwen2.5-72B-Instruct",
        "Qwen/Qwen2.5-Coder-7B-Instruct",

        // DeepSeek 系列
        "deepseek-ai/DeepSeek-V2.5",
        "deepseek-ai/DeepSeek-Coder-V2-Instruct",

        // GLM (智谱) 系列
        "THUDM/glm-4-9b-chat",

        // Yi 系列
        "01-ai/Yi-1.5-9B-Chat",
        "01-ai/Yi-1.5-34B-Chat",

        // Llama 系列
        "meta-llama/Meta-Llama-3.1-8B-Instruct",
        "meta-llama/Meta-Llama-3.1-70B-Instruct",

        // Mistral 系列
        "mistralai/Mistral-7B-Instruct-v0.3",
        "mistralai/Mixtral-8x7B-Instruct-v0.1"
    };

    public SiliconFlowProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public SiliconFlowProvider(String apiKey, String baseUrl) {
        super(apiKey, baseUrl);
    }

    @Override
    public String getProviderName() {
        return "siliconflow";
    }

    @Override
    public String[] getAvailableModels() {
        return AVAILABLE_MODELS;
    }

    @Override
    protected LLMResponse sendHttpRequest(LLMRequest request) {
        // TODO: Phase 3 - Implement actual HTTP request using HttpClient
        // SiliconFlow API is compatible with OpenAI format, so implementation will be similar
        logger.warn("SiliconFlow HTTP request not yet implemented (Phase 3)");

        // Placeholder response for Phase 2
        return LLMResponse.builder()
            .content("SiliconFlow response placeholder. Real API integration coming in Phase 3.")
            .model(request.getModel())
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .success(true)
            .build();
    }
}
