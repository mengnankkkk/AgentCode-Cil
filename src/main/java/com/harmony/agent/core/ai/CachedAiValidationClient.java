package com.harmony.agent.core.ai;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Cached AI Validation Client - Decorator pattern with Guava cache
 * Caches AI validation responses to reduce API costs and latency
 */
public class CachedAiValidationClient {

    private static final Logger logger = LoggerFactory.getLogger(CachedAiValidationClient.class);

    private final AiValidationClient delegate;
    private final Cache<String, String> cache;

    // Cache statistics
    private long cacheHits = 0;
    private long cacheMisses = 0;

    /**
     * Constructor with default cache configuration
     *
     * @param delegate The underlying AI validation client
     */
    public CachedAiValidationClient(AiValidationClient delegate) {
        this(delegate, 500, 24);
    }

    /**
     * Constructor with custom cache configuration
     *
     * @param delegate The underlying AI validation client
     * @param maxSize Maximum number of cached entries
     * @param ttlHours Time-to-live in hours
     */
    public CachedAiValidationClient(AiValidationClient delegate, int maxSize, int ttlHours) {
        this.delegate = delegate;

        this.cache = CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttlHours, TimeUnit.HOURS)
            .recordStats() // Enable statistics collection
            .build();

        logger.info("Cached AI client initialized (maxSize: {}, TTL: {}h)", maxSize, ttlHours);
    }

    /**
     * Send validation request with caching
     *
     * @param prompt The validation prompt
     * @param expectJson Whether to expect JSON response
     * @return LLM response content (from cache or fresh)
     * @throws AiValidationClient.AiClientException if request fails
     */
    public String sendRequest(String prompt, boolean expectJson)
            throws AiValidationClient.AiClientException {

        // Create cache key that includes expectJson flag
        String cacheKey = createCacheKey(prompt, expectJson);

        try {
            String result = cache.get(cacheKey, () -> {
                logger.debug("Cache MISS - sending request to LLM");
                cacheMisses++;
                return delegate.sendRequest(prompt, expectJson);
            });

            if (cache.getIfPresent(cacheKey) != null) {
                cacheHits++;
                logger.debug("Cache HIT - returning cached response");
            }

            return result;

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AiValidationClient.AiClientException) {
                throw (AiValidationClient.AiClientException) cause;
            } else {
                throw new AiValidationClient.AiClientException(
                    "Failed to retrieve from cache or delegate", cause);
            }
        }
    }

    /**
     * Create cache key from prompt and flags
     * Uses hash to save memory for long prompts
     */
    private String createCacheKey(String prompt, boolean expectJson) {
        // Use hash for memory efficiency (prompts can be large)
        int promptHash = prompt.hashCode();
        return promptHash + "#" + (expectJson ? "json" : "text");
    }

    /**
     * Check if client is available
     */
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    /**
     * Get provider name
     */
    public String getProviderName() {
        return delegate.getProviderName();
    }

    /**
     * Get model name
     */
    public String getModelName() {
        return delegate.getModelName();
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        com.google.common.cache.CacheStats stats = cache.stats();
        return new CacheStats(
            cacheHits,
            cacheMisses,
            cache.size(),
            stats.hitRate()
        );
    }

    /**
     * Clear the cache
     */
    public void clearCache() {
        cache.invalidateAll();
        logger.info("Cache cleared");
    }

    /**
     * Cache statistics holder
     */
    public static class CacheStats {
        private final long hits;
        private final long misses;
        private final long size;
        private final double hitRate;

        public CacheStats(long hits, long misses, long size, double hitRate) {
            this.hits = hits;
            this.misses = misses;
            this.size = size;
            this.hitRate = hitRate;
        }

        public long getHits() {
            return hits;
        }

        public long getMisses() {
            return misses;
        }

        public long getSize() {
            return size;
        }

        public double getHitRate() {
            return hitRate;
        }

        public double getCostSavings() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{hits=%d, misses=%d, size=%d, hitRate=%.2f%%, savings=%.2f%%}",
                hits, misses, size, hitRate * 100, getCostSavings() * 100
            );
        }
    }
}
