package com.harmony.agent.llm;

import com.harmony.agent.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LLM Client for chat and AI interactions
 * Basic implementation for Phase 2, will be enhanced in Phase 3
 */
public class LLMClient {
    private static final Logger logger = LoggerFactory.getLogger(LLMClient.class);

    private final ConfigManager configManager;

    public LLMClient(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Chat with AI (placeholder for Phase 3)
     * @param input User input
     * @param history Conversation history
     * @return AI response
     */
    public String chat(String input, List<String> history) {
        // Placeholder implementation
        logger.info("LLM chat request: {}", input);

        // For now, return a helpful message
        return "AI chat functionality will be available in Phase 3. " +
               "Currently, you can use slash commands like /analyze, /help, etc.";
    }

    /**
     * Check if LLM is configured and available
     */
    public boolean isAvailable() {
        String apiKey = configManager.getConfig().getAi().getApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }
}
