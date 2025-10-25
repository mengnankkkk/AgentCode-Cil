package com.harmony.agent.llm.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating LLM providers
 *
 * 支持自动缓存包装：
 * - 通过 enableCache() 启用缓存
 * - 所有通过 getProvider() 获取的 provider 都会自动包装 CachedLLMProvider
 * - 缓存类型可通过 setCacheType() 配置
 */
public class ProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger(ProviderFactory.class);

    private final Map<String, LLMProvider> providers = new HashMap<>();
    private boolean cacheEnabled = false;
    private String cacheType = "ai_llm_calls";

    /**
     * Register a provider
     */
    public void registerProvider(String name, LLMProvider provider) {
        providers.put(name.toLowerCase(), provider);
    }

    /**
     * Get a provider by name
     *
     * 如果启用了缓存，会自动包装 CachedLLMProvider
     */
    public LLMProvider getProvider(String name) {
        LLMProvider provider = providers.get(name.toLowerCase());
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + name);
        }

        // 自动应用缓存装饰器
        if (cacheEnabled && !(provider instanceof CachedLLMProvider)) {
            logger.debug("Wrapping provider '{}' with cache (type: {})", name, cacheType);
            return new CachedLLMProvider(provider, cacheType);
        }

        return provider;
    }

    /**
     * Check if provider exists
     */
    public boolean hasProvider(String name) {
        return providers.containsKey(name.toLowerCase());
    }

    /**
     * Enable or disable caching
     *
     * @param enabled true to enable cache, false to disable
     * @return this factory for chaining
     */
    public ProviderFactory enableCache(boolean enabled) {
        this.cacheEnabled = enabled;
        logger.info("LLM Provider cache: {}", enabled ? "ENABLED" : "DISABLED");
        return this;
    }

    /**
     * Set cache type
     *
     * @param cacheType Cache type identifier for PersistentCacheManager
     * @return this factory for chaining
     */
    public ProviderFactory setCacheType(String cacheType) {
        this.cacheType = cacheType;
        logger.info("LLM Provider cache type set to: {}", cacheType);
        return this;
    }

    /**
     * Check if cache is enabled
     *
     * @return true if cache is enabled
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /**
     * Get cache type
     *
     * @return current cache type
     */
    public String getCacheType() {
        return cacheType;
    }

    /**
     * Create default factory with common providers
     */
    public static ProviderFactory createDefault(String openaiKey, String claudeKey) {
        return createDefault(openaiKey, claudeKey, null);
    }

    /**
     * Create default factory with common providers including SiliconFlow
     */
    public static ProviderFactory createDefault(String openaiKey, String claudeKey, String siliconflowKey) {
        return createDefault(openaiKey, claudeKey, siliconflowKey, null);
    }

    /**
     * Create default factory with all providers including NHH
     */
    public static ProviderFactory createDefault(String openaiKey, String claudeKey, String siliconflowKey, String nhhKey) {
        ProviderFactory factory = new ProviderFactory();

        if (openaiKey != null && !openaiKey.isEmpty()) {
            factory.registerProvider("openai", new OpenAIProvider(openaiKey));
        }

        if (claudeKey != null && !claudeKey.isEmpty()) {
            factory.registerProvider("claude", new ClaudeProvider(claudeKey));
        }

        if (siliconflowKey != null && !siliconflowKey.isEmpty()) {
            factory.registerProvider("siliconflow", new SiliconFlowProvider(siliconflowKey));
        }

        if (nhhKey != null && !nhhKey.isEmpty()) {
            factory.registerProvider("nhh", new NHHProvider(nhhKey));
        }

        return factory;
    }
}
