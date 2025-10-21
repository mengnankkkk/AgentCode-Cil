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
 * 123NHH API provider implementation
 * Supports GLM, Gemini and other models
 * API endpoint: https://new.123nhh.xyz
 */
public class NHHProvider extends BaseLLMProvider {

    private static final String DEFAULT_BASE_URL = "https://new.123nhh.xyz";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    private static final String[] AVAILABLE_MODELS = {
        // GLM (智谱) series
        "glm-4.5-flash",
        "glm-4.5",
        "glm-4-9b",
        "glm-4",
        "glm-3-turbo",

        // Gemini series
        "gemini-2.0-flash",
        "gemini-1.5-pro",
        "gemini-1.5-flash",
        "gemini-pro",

        // Others
        "claude-3-opus",
        "claude-3-sonnet",
        "gpt-4-turbo",
        "gpt-4",
        "gpt-3.5-turbo"
    };

    public NHHProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public NHHProvider(String apiKey, String baseUrl) {
        super(apiKey, baseUrl);
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }

    @Override
    public String getProviderName() {
        return "nhh";
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
            requestBody.addProperty("stream", request.isStream());

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
            String url = baseUrl.endsWith("/v1") ? baseUrl + "/chat/completions" : baseUrl + "/v1/chat/completions";
            RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);

            Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

            logger.debug("Sending request to NHH API: {}", url);

            // Execute request
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    logger.error("NHH API error: {} - {}", response.code(), errorBody);
                    return LLMResponse.builder()
                        .errorMessage("NHH API error: " + response.code() + " - " + errorBody)
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

                logger.info("NHH API call successful. Model: {}, Tokens: prompt={}, completion={}, total={}",
                    request.getModel(), promptTokens, completionTokens, totalTokens);

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
            logger.error("Failed to send request to NHH API", e);
            return LLMResponse.builder()
                .errorMessage("Network error: " + e.getMessage())
                .build();
        } catch (Exception e) {
            logger.error("Unexpected error calling NHH API", e);
            return LLMResponse.builder()
                .errorMessage("Unexpected error: " + e.getMessage())
                .build();
        }
    }
}
