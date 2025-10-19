package com.harmony.agent.llm.provider;

import com.google.common.util.concurrent.RateLimiter;
import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation for LLM providers
 * Provides common functionality for all providers including rate limiting
 */
public abstract class BaseLLMProvider implements LLMProvider {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final String apiKey;
    protected final String baseUrl;

    // Static rate limiter shared across all providers
    private static RateLimiter rateLimiter = null;
    private static String rateLimitMode = "qps";
    private static boolean rateLimiterEnabled = false;

    protected BaseLLMProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    /**
     * Configure rate limiter for all providers
     * @param mode "qps" (queries per second) or "tpm" (tokens per minute)
     * @param qpsLimit QPS limit (for QPS mode)
     * @param tpmLimit TPM limit (for TPM mode)
     * @param safetyMargin Safety margin factor (e.g., 0.8 for 80%)
     */
    public static void configureRateLimiter(String mode, double qpsLimit, int tpmLimit, double safetyMargin) {
        rateLimitMode = mode;

        if ("qps".equalsIgnoreCase(mode)) {
            // QPS mode: limit queries per second
            double effectiveQps = qpsLimit * safetyMargin;
            rateLimiter = RateLimiter.create(effectiveQps);
            rateLimiterEnabled = true;
            LoggerFactory.getLogger(BaseLLMProvider.class)
                .info("Rate limiter configured: QPS mode, limit={} req/s ({}% of {})",
                    effectiveQps, (int)(safetyMargin * 100), qpsLimit);
        } else if ("tpm".equalsIgnoreCase(mode)) {
            // TPM mode: limit tokens per minute, convert to tokens per second
            double tps = (tpmLimit / 60.0) * safetyMargin;
            rateLimiter = RateLimiter.create(tps);
            rateLimiterEnabled = true;
            LoggerFactory.getLogger(BaseLLMProvider.class)
                .info("Rate limiter configured: TPM mode, limit={} tokens/s ({}% of {} TPM)",
                    tps, (int)(safetyMargin * 100), tpmLimit);
        } else {
            rateLimiterEnabled = false;
            LoggerFactory.getLogger(BaseLLMProvider.class)
                .warn("Unknown rate limit mode '{}', rate limiting disabled", mode);
        }
    }

    /**
     * Disable rate limiter
     */
    public static void disableRateLimiter() {
        rateLimiterEnabled = false;
        rateLimiter = null;
        LoggerFactory.getLogger(BaseLLMProvider.class).info("Rate limiter disabled");
    }

    /**
     * Estimate token count for a request
     * Rough estimation: ~4 characters per token, with 1.2x buffer
     */
    private int estimateTokens(LLMRequest request) {
        int totalChars = 0;
        for (Message msg : request.getMessages()) {
            totalChars += msg.getContent().length();
        }
        // Rough estimate: 4 chars per token + 20% buffer
        return (int) Math.ceil((totalChars / 4.0) * 1.2);
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

        // Apply rate limiting
        if (rateLimiterEnabled && rateLimiter != null) {
            if ("tpm".equalsIgnoreCase(rateLimitMode)) {
                // TPM mode: acquire permits based on estimated tokens
                int estimatedTokens = estimateTokens(request);
                logger.debug("Acquiring {} tokens from rate limiter (estimated)", estimatedTokens);
                rateLimiter.acquire(estimatedTokens);
            } else {
                // QPS mode: acquire 1 permit per request
                logger.debug("Acquiring 1 permit from rate limiter (QPS mode)");
                rateLimiter.acquire();
            }
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
