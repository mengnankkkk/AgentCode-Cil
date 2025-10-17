package com.harmony.agent.llm.provider;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating LLM providers
 */
public class ProviderFactory {

    private final Map<String, LLMProvider> providers = new HashMap<>();

    /**
     * Register a provider
     */
    public void registerProvider(String name, LLMProvider provider) {
        providers.put(name.toLowerCase(), provider);
    }

    /**
     * Get a provider by name
     */
    public LLMProvider getProvider(String name) {
        LLMProvider provider = providers.get(name.toLowerCase());
        if (provider == null) {
            throw new IllegalArgumentException("Provider not found: " + name);
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
     * Create default factory with common providers
     */
    public static ProviderFactory createDefault(String openaiKey, String claudeKey) {
        ProviderFactory factory = new ProviderFactory();

        if (openaiKey != null && !openaiKey.isEmpty()) {
            factory.registerProvider("openai", new OpenAIProvider(openaiKey));
        }

        if (claudeKey != null && !claudeKey.isEmpty()) {
            factory.registerProvider("claude", new ClaudeProvider(claudeKey));
        }

        return factory;
    }
}
