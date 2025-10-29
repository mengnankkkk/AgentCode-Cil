package com.harmony.agent.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validate LLM API Key validity
 */
public class ApiKeyValidator {
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyValidator.class);

    /**
     * Check if valid API Key exists
     * @return true if at least one valid API Key exists
     */
    public static boolean hasValidApiKey() {
        boolean hasOpenAiKey = hasValidOpenAiKey();
        boolean hasClaudeKey = hasValidClaudeKey();
        boolean hasOtherKey = hasValidOtherKey();

        logger.debug("API Key check - OpenAI: {}, Claude: {}, Other: {}",
            hasOpenAiKey, hasClaudeKey, hasOtherKey);

        return hasOpenAiKey || hasClaudeKey || hasOtherKey;
    }

    private static boolean hasValidOpenAiKey() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        return isValidKey(apiKey);
    }

    private static boolean hasValidClaudeKey() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        return isValidKey(apiKey);
    }

    private static boolean hasValidOtherKey() {
        String siliconFlowKey = System.getenv("SILICONFLOW_API_KEY");
        String nhhKey = System.getenv("NHH_API_KEY");

        return isValidKey(siliconFlowKey) || isValidKey(nhhKey);
    }

    private static boolean isValidKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }

        String trimmed = apiKey.trim();

        if (trimmed.length() < 10) {
            return false;
        }

        if (trimmed.equalsIgnoreCase("your-api-key") ||
            trimmed.equalsIgnoreCase("your_api_key") ||
            trimmed.equalsIgnoreCase("sk-xxx")) {
            return false;
        }

        return true;
    }

    public static String getApiKeyErrorMessage() {
        return "ERROR: No valid LLM API Key configured\n" +
               "/plan command requires API Key for AI-powered analysis\n" +
               "Configure one of: OPENAI_API_KEY, ANTHROPIC_API_KEY, SILICONFLOW_API_KEY";
    }

    public static String getConfiguredProviders() {
        StringBuilder sb = new StringBuilder();

        if (hasValidOpenAiKey()) {
            sb.append("- OpenAI\n");
        }
        if (hasValidClaudeKey()) {
            sb.append("- Anthropic Claude\n");
        }
        if (isValidKey(System.getenv("SILICONFLOW_API_KEY"))) {
            sb.append("- SiliconFlow\n");
        }
        if (isValidKey(System.getenv("NHH_API_KEY"))) {
            sb.append("- NHH\n");
        }

        return sb.toString();
    }
}
