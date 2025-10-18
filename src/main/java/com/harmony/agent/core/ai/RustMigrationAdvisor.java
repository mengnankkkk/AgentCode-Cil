package com.harmony.agent.core.ai;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.provider.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Rust Migration Advisor - Provides C-to-Rust migration guidance
 * Analyzes C code and generates detailed Rust migration recommendations
 */
public class RustMigrationAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(RustMigrationAdvisor.class);

    // Rust迁移需要更高的温度以支持创造性建议
    private static final double RUST_MIGRATION_TEMPERATURE = 0.5;
    private static final int RUST_MIGRATION_MAX_TOKENS = 3000;

    private final LLMProvider llmProvider;
    private final CodeSlicer codeSlicer;
    private final String model;

    /**
     * 构造函数
     *
     * @param llmProvider LLM提供者（OpenAI或Claude）
     * @param codeSlicer 代码切片器（复用Phase 3）
     * @param model 模型名称（如"gpt-4"）
     */
    public RustMigrationAdvisor(LLMProvider llmProvider, CodeSlicer codeSlicer, String model) {
        this.llmProvider = llmProvider;
        this.codeSlicer = codeSlicer;
        this.model = model;

        logger.info("RustMigrationAdvisor initialized with provider: {}, model: {}",
            llmProvider.getProviderName(), model);
    }

    /**
     * 获取Rust迁移建议
     *
     * @param file C源文件路径
     * @param lineNumber 函数所在行号
     * @return Markdown格式的迁移建议
     */
    public String getMigrationSuggestion(Path file, int lineNumber) {
        logger.info("Generating Rust migration suggestion for: {}:{}", file, lineNumber);

        try {
            // 1. 使用CodeSlicer提取代码上下文
            String codeSlice = codeSlicer.getContextSlice(file, lineNumber);
            if (codeSlice.startsWith("[Error:")) {
                logger.error("CodeSlicer failed: {}", codeSlice);
                return "❌ Error: Unable to extract code context - " + codeSlice;
            }

            // 2. 使用PromptBuilder构建提示词
            String prompt = PromptBuilder.buildRustFFIPrompt(codeSlice);

            // 3. 构建LLM请求
            LLMRequest request = LLMRequest.builder()
                .model(model)
                .temperature(RUST_MIGRATION_TEMPERATURE)
                .maxTokens(RUST_MIGRATION_MAX_TOKENS)
                .addSystemMessage(
                    "You are an expert Rust engineer specializing in C-to-Rust migration and FFI. " +
                    "Provide clear, practical, and actionable advice. " +
                    "Always include concrete Rust code examples with detailed comments. " +
                    "Structure your response using the requested Markdown sections."
                )
                .addUserMessage(prompt)
                .build();

            // 4. 发送请求
            logger.debug("Sending LLM request with {} tokens estimated",
                request.getMaxTokens());
            LLMResponse response = llmProvider.sendRequest(request);

            // 5. 检查响应
            if (!response.isSuccess()) {
                logger.error("LLM request failed: {}", response.getErrorMessage());
                return "❌ Error: Failed to generate migration suggestion - " +
                    response.getErrorMessage();
            }

            String content = response.getContent();
            if (content == null || content.trim().isEmpty()) {
                logger.error("LLM returned empty response");
                return "❌ Error: LLM returned empty response";
            }

            logger.info("Migration suggestion generated successfully ({} tokens used, {} completion tokens)",
                response.getTotalTokens(), response.getCompletionTokens());

            return content.trim();

        } catch (Exception e) {
            logger.error("Failed to generate Rust migration suggestion", e);
            return "❌ Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    /**
     * 检查LLM提供者是否可用
     *
     * @return true如果提供者可用
     */
    public boolean isAvailable() {
        return llmProvider.isAvailable();
    }

    /**
     * 获取提供者名称
     *
     * @return 提供者名称
     */
    public String getProviderName() {
        return llmProvider.getProviderName();
    }

    /**
     * 获取模型名称
     *
     * @return 模型名称
     */
    public String getModelName() {
        return model;
    }
}
