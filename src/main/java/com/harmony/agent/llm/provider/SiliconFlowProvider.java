package com.harmony.agent.llm.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.model.Message;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * SiliconFlow (硅基流动) provider implementation
 * Supports various open-source models via SiliconFlow API
 * Compatible with OpenAI API format
 */
public class SiliconFlowProvider extends BaseLLMProvider {

    private static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

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
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
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
        try {
            // Build JSON request body (OpenAI-compatible format)
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", request.getModel());
            requestBody.addProperty("temperature", request.getTemperature());
            requestBody.addProperty("max_tokens", request.getMaxTokens());
            requestBody.addProperty("stream", false);

            // Add messages
            JsonArray messagesArray = new JsonArray();
            for (Message msg : request.getMessages()) {
                JsonObject messageObj = new JsonObject();
                messageObj.addProperty("role", msg.getRole().name().toLowerCase());
                messageObj.addProperty("content", msg.getContent());
                messagesArray.add(messageObj);
            }
            requestBody.add("messages", messagesArray);

            // Build HTTP request
            String url = baseUrl + "/chat/completions";
            RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);

            Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

            logger.debug("Sending request to SiliconFlow API: {}", url);

            // Execute request
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    logger.error("SiliconFlow API error: {} - {}", response.code(), errorBody);
                    return LLMResponse.builder()
                        .errorMessage("SiliconFlow API error: " + response.code() + " - " + errorBody)
                        .build();
                }

                // Parse response
                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                // Extract content from response
                String content = "";
                int promptTokens = 0;
                int completionTokens = 0;
                int totalTokens = 0;

                if (jsonResponse.has("choices") && jsonResponse.getAsJsonArray("choices").size() > 0) {
                    JsonObject firstChoice = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject();
                    if (firstChoice.has("message")) {
                        JsonObject message = firstChoice.getAsJsonObject("message");
                        content = message.has("content") ? message.get("content").getAsString() : "";
                    }
                }

                if (jsonResponse.has("usage")) {
                    JsonObject usage = jsonResponse.getAsJsonObject("usage");
                    promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsInt() : 0;
                    completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsInt() : 0;
                    totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").getAsInt() : 0;
                }

                logger.info("SiliconFlow API call successful. Tokens: prompt={}, completion={}, total={}",
                    promptTokens, completionTokens, totalTokens);

                return LLMResponse.builder()
                    .content(content)
                    .model(request.getModel())
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .success(true)
                    .build();

            }
        } catch (IOException e) {
            logger.error("Failed to send request to SiliconFlow API", e);
            return LLMResponse.builder()
                .errorMessage("Network error: " + e.getMessage())
                .build();
        } catch (Exception e) {
            logger.error("Unexpected error calling SiliconFlow API", e);
            return LLMResponse.builder()
                .errorMessage("Unexpected error: " + e.getMessage())
                .build();
        }
    }
}
