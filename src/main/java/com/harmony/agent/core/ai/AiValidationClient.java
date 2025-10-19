package com.harmony.agent.core.ai;

import com.google.common.util.concurrent.RateLimiter;
import com.harmony.agent.config.ConfigManager;
import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.model.Message;
import com.harmony.agent.llm.provider.LLMProvider;
import com.harmony.agent.llm.provider.ProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * AI Validation Client - Specialized client for security issue validation
 * Built on top of existing LLMProvider architecture
 */
public class AiValidationClient {

    private static final Logger logger = LoggerFactory.getLogger(AiValidationClient.class);

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final int DEFAULT_MAX_TOKENS = 2000;
    private static final double DEFAULT_TEMPERATURE = 0.3; // Lower for more deterministic analysis

    private final LLMProvider provider;
    private final String model;
    private final ConfigManager configManager;
    private final RateLimiter rateLimiter; // Client-side rate limiting

    /**
     * Constructor with default configuration
     */
    public AiValidationClient(ConfigManager configManager) {
        this.configManager = configManager;

        // Get API keys from environment or config
        String openaiKey = System.getenv("OPENAI_API_KEY");
        if (openaiKey == null || openaiKey.isEmpty()) {
            if (configManager.getConfig().getAi().getProviders().containsKey("openai")) {
                openaiKey = configManager.getConfig().getAi().getProviders().get("openai").getApiKey();
            }
        }

        String claudeKey = System.getenv("CLAUDE_API_KEY");
        if (claudeKey == null || claudeKey.isEmpty()) {
            if (configManager.getConfig().getAi().getProviders().containsKey("claude")) {
                claudeKey = configManager.getConfig().getAi().getProviders().get("claude").getApiKey();
            }
        }

        String siliconflowKey = System.getenv("SILICONFLOW_API_KEY");
        if (siliconflowKey == null || siliconflowKey.isEmpty()) {
            if (configManager.getConfig().getAi().getProviders().containsKey("siliconflow")) {
                siliconflowKey = configManager.getConfig().getAi().getProviders().get("siliconflow").getApiKey();
            }
        }

        // Create provider factory
        ProviderFactory factory = ProviderFactory.createDefault(openaiKey, claudeKey, siliconflowKey);

        // Use OpenAI for validation (fast and cost-effective)
        // Can be configured to use Claude for more complex analysis
        String providerName = configManager.getConfig().getAi().getProvider();
        this.provider = factory.getProvider(providerName);
        this.model = configManager.getConfig().getAi().getModel();

        // Initialize rate limiter from configuration
        double requestsPerSecond = configManager.getConfig().getAi().getRequestsPerSecondLimit();
        if (requestsPerSecond <= 0) {
            requestsPerSecond = 5.0; // Fallback default
        }
        this.rateLimiter = RateLimiter.create(requestsPerSecond);
        logger.info("Rate limiter initialized: {} requests/second", requestsPerSecond);

        if (!provider.isAvailable()) {
            logger.warn("LLM provider '{}' is not available - check API keys", providerName);
        } else {
            logger.info("AI Validation Client initialized with provider: {}, model: {}",
                providerName, model);
        }
    }

    /**
     * Constructor with custom provider
     */
    public AiValidationClient(LLMProvider provider, String model, ConfigManager configManager) {
        this.provider = provider;
        this.model = model;
        this.configManager = configManager;

        // Initialize rate limiter from configuration
        double requestsPerSecond = configManager.getConfig().getAi().getRequestsPerSecondLimit();
        if (requestsPerSecond <= 0) {
            requestsPerSecond = 5.0; // Fallback default
        }
        this.rateLimiter = RateLimiter.create(requestsPerSecond);
    }

    /**
     * Send validation request to LLM
     *
     * @param prompt The validation prompt
     * @param expectJson Whether to expect JSON response
     * @return LLM response content
     * @throws AiClientException if request fails after retries
     */
    public String sendRequest(String prompt, boolean expectJson) throws AiClientException {
        if (!provider.isAvailable()) {
            throw new AiClientException("LLM provider is not available - check API keys");
        }

        // Acquire rate limiter permit (blocks until available)
        logger.debug("Acquiring rate limit permit...");
        rateLimiter.acquire();
        logger.debug("Rate limit permit acquired");

        IOException lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return sendRequestInternal(prompt, expectJson);
            } catch (IOException e) {
                lastException = e;
                logger.warn("AI validation request failed (attempt {}/{}): {}",
                    attempt + 1, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AiClientException("Request interrupted during retry", ie);
                    }
                }
            }
        }

        throw new AiClientException("AI validation failed after " + MAX_RETRIES + " retries",
            lastException);
    }

    /**
     * Internal method to send request
     */
    private String sendRequestInternal(String prompt, boolean expectJson) throws IOException {
        // Build request
        LLMRequest.Builder requestBuilder = LLMRequest.builder()
            .model(model)
            .temperature(DEFAULT_TEMPERATURE)
            .maxTokens(DEFAULT_MAX_TOKENS);

        // Add system message for JSON mode
        if (expectJson) {
            requestBuilder.addSystemMessage(
                "You are a security analysis expert. " +
                "Always respond with valid JSON only, no additional text."
            );
        }

        // Add user prompt
        requestBuilder.addUserMessage(prompt);

        LLMRequest request = requestBuilder.build();

        // Send request
        logger.debug("Sending AI validation request (expect JSON: {})", expectJson);
        LLMResponse response = provider.sendRequest(request);

        // Check response
        if (!response.isSuccess()) {
            throw new IOException("LLM request failed: " + response.getErrorMessage());
        }

        String content = response.getContent();
        if (content == null || content.trim().isEmpty()) {
            throw new IOException("LLM returned empty response");
        }

        logger.debug("AI validation response received: {} tokens",
            response.getTotalTokens());

        return content.trim();
    }

    /**
     * Check if client is available
     */
    public boolean isAvailable() {
        return provider.isAvailable();
    }

    /**
     * Get provider name
     */
    public String getProviderName() {
        return provider.getProviderName();
    }

    /**
     * Get model name
     */
    public String getModelName() {
        return model;
    }

    /**
     * Custom exception for AI client errors
     */
    public static class AiClientException extends Exception {
        public AiClientException(String message) {
            super(message);
        }

        public AiClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * IOException wrapper for consistency
     */
    private static class IOException extends Exception {
        public IOException(String message) {
            super(message);
        }
    }
}
