package com.harmony.agent.core.ai;

import com.harmony.agent.core.model.SecurityIssue;
import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.provider.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Security Suggestion Advisor - Provides security fix recommendations
 * Analyzes security issues and generates detailed fix suggestions with code examples
 */
public class SecuritySuggestionAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SecuritySuggestionAdvisor.class);

    // Security fix suggestions need moderate creativity
    private static final double SECURITY_FIX_TEMPERATURE = 0.3;
    private static final int SECURITY_FIX_MAX_TOKENS = 2000;

    private final LLMProvider llmProvider;
    private final CodeSlicer codeSlicer;
    private final String model;

    /**
     * Constructor
     *
     * @param llmProvider LLM provider (OpenAI or Claude)
     * @param codeSlicer Code slicer for extracting context
     * @param model Model name (e.g., "gpt-4")
     */
    public SecuritySuggestionAdvisor(LLMProvider llmProvider, CodeSlicer codeSlicer, String model) {
        this.llmProvider = llmProvider;
        this.codeSlicer = codeSlicer;
        this.model = model;

        logger.info("SecuritySuggestionAdvisor initialized with provider: {}, model: {}",
            llmProvider.getProviderName(), model);
    }

    /**
     * Get security fix suggestion for an issue
     *
     * @param issue Security issue to analyze
     * @param filePath Source file path
     * @return Markdown formatted fix suggestion
     */
    public String getFixSuggestion(SecurityIssue issue, Path filePath) {
        logger.info("Generating security fix suggestion for: {} at {}:{}",
            issue.getTitle(), filePath, issue.getLocation().getLineNumber());

        try {
            // 1. Extract code context using CodeSlicer
            String codeSlice = codeSlicer.getContextSlice(filePath, issue.getLocation().getLineNumber());
            if (codeSlice.startsWith("[Error:")) {
                logger.error("CodeSlicer failed: {}", codeSlice);
                return "❌ Error: Unable to extract code context - " + codeSlice;
            }

            // 2. Build prompt using PromptBuilder
            String prompt = PromptBuilder.buildSecurityFixPrompt(issue, codeSlice);

            // 3. Build LLM request
            LLMRequest request = LLMRequest.builder()
                .model(model)
                .temperature(SECURITY_FIX_TEMPERATURE)
                .maxTokens(SECURITY_FIX_MAX_TOKENS)
                .addSystemMessage(
                    "You are a security expert specializing in C/C++ vulnerability remediation. " +
                    "Provide clear, practical, and actionable fix suggestions. " +
                    "Always include concrete code examples with detailed comments. " +
                    "Structure your response using the requested Markdown sections. " +
                    "Follow secure coding standards like CERT C and CWE guidelines."
                )
                .addUserMessage(prompt)
                .build();

            // 4. Send request
            logger.debug("Sending LLM request with {} tokens estimated",
                request.getMaxTokens());
            LLMResponse response = llmProvider.sendRequest(request);

            // 5. Check response
            if (!response.isSuccess()) {
                logger.error("LLM request failed: {}", response.getErrorMessage());
                return "❌ Error: Failed to generate fix suggestion - " +
                    response.getErrorMessage();
            }

            String content = response.getContent();
            if (content == null || content.trim().isEmpty()) {
                logger.error("LLM returned empty response");
                return "❌ Error: LLM returned empty response";
            }

            logger.info("Fix suggestion generated successfully ({} tokens used, {} completion tokens)",
                response.getTotalTokens(), response.getCompletionTokens());

            return content.trim();

        } catch (Exception e) {
            logger.error("Failed to generate security fix suggestion", e);
            return "❌ Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    /**
     * Check if LLM provider is available
     *
     * @return true if provider is available
     */
    public boolean isAvailable() {
        return llmProvider.isAvailable();
    }

    /**
     * Get provider name
     *
     * @return Provider name
     */
    public String getProviderName() {
        return llmProvider.getProviderName();
    }

    /**
     * Get model name
     *
     * @return Model name
     */
    public String getModelName() {
        return model;
    }
}
