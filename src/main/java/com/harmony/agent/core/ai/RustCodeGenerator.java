package com.harmony.agent.core.ai;

import com.harmony.agent.llm.model.LLMRequest;
import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.provider.LLMProvider;
import com.harmony.agent.core.model.SecurityIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Rust Code Generator - 将完整的 C/C++ 文件转换为 Rust 代码
 * 生成可以直接编译的完整 Rust 代码，而不只是建议文本
 */
public class RustCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(RustCodeGenerator.class);

    // Rust 代码生成需要更高的温度以支持创造性转换
    private static final double RUST_GENERATION_TEMPERATURE = 0.3;
    private static final int RUST_GENERATION_MAX_TOKENS = 8000;

    private final LLMProvider llmProvider;
    private final String model;

    /**
     * 构造函数
     *
     * @param llmProvider LLM提供者（OpenAI或Claude）
     * @param model 模型名称（如"gpt-4"）
     */
    public RustCodeGenerator(LLMProvider llmProvider, String model) {
        this.llmProvider = llmProvider;
        this.model = model;

        logger.info("RustCodeGenerator initialized with provider: {}, model: {}",
            llmProvider.getProviderName(), model);
    }

    /**
     * 将完整的 C/C++ 文件转换为 Rust 代码
     *
     * @param cFile C/C++ 源文件路径
     * @return 生成的 Rust 代码（完整的 .rs 文件内容）
     */
    public String generateRustCode(Path cFile) {
        return generateRustCode(cFile, null);
    }

    /**
     * 将完整的 C/C++ 文件转换为 Rust 代码，考虑已知的安全问题
     *
     * @param cFile C/C++ 源文件路径
     * @param knownIssues 该文件中的已知安全问题列表（可为 null）
     * @return 生成的 Rust 代码（完整的 .rs 文件内容）
     */
    public String generateRustCode(Path cFile, List<SecurityIssue> knownIssues) {
        logger.info("Generating Rust code for entire file: {}", cFile);

        try {
            // 1. 读取 C/C++ 文件内容
            String cCode = Files.readString(cFile);

            if (cCode.trim().isEmpty()) {
                logger.error("File is empty: {}", cFile);
                return "// ERROR: Source file is empty";
            }

            logger.debug("Read {} characters from {}", cCode.length(), cFile.getFileName());

            // 2. 构建提示词（包括安全约束）
            String prompt = buildFullFileConversionPrompt(cFile.getFileName().toString(), cCode, knownIssues);

            // 3. 构建 LLM 请求
            LLMRequest request = LLMRequest.builder()
                .model(model)
                .temperature(RUST_GENERATION_TEMPERATURE)
                .maxTokens(RUST_GENERATION_MAX_TOKENS)
                .addSystemMessage(
                    "You are an expert Rust developer specialized in converting C/C++ code to idiomatic Rust. " +
                    "Your task is to convert the ENTIRE C/C++ file to a complete, compilable Rust file. " +
                    "Follow these rules:\n" +
                    "1. Generate ONLY the Rust code - no explanations, no markdown formatting\n" +
                    "2. Preserve all functionality from the original C/C++ code\n" +
                    "3. Use idiomatic Rust patterns (Result, Option, etc.)\n" +
                    "4. Add appropriate error handling with Result<T, E>\n" +
                    "5. Use safe Rust whenever possible; mark unsafe blocks only when necessary\n" +
                    "6. Add comments explaining complex conversions\n" +
                    "7. Include all necessary use statements at the top\n" +
                    "8. For C FFI types, use std::os::raw or libc crate\n" +
                    "9. Generate a complete, self-contained .rs file that can be compiled"
                )
                .addUserMessage(prompt)
                .build();

            // 4. 发送请求
            logger.debug("Sending LLM request with max {} tokens", RUST_GENERATION_MAX_TOKENS);
            LLMResponse response = llmProvider.sendRequest(request);

            // 5. 检查响应
            if (!response.isSuccess()) {
                logger.error("LLM request failed: {}", response.getErrorMessage());
                return "// ERROR: Failed to generate Rust code - " + response.getErrorMessage();
            }

            String rustCode = response.getContent();
            if (rustCode == null || rustCode.trim().isEmpty()) {
                logger.error("LLM returned empty response");
                return "// ERROR: LLM returned empty response";
            }

            // 6. 清理生成的代码（移除可能的 markdown 标记）
            rustCode = cleanGeneratedCode(rustCode);

            logger.info("Rust code generated successfully ({} tokens used, {} completion tokens)",
                response.getTotalTokens(), response.getCompletionTokens());

            return rustCode;

        } catch (IOException e) {
            logger.error("Failed to read source file: {}", cFile, e);
            return "// ERROR: Failed to read source file - " + e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to generate Rust code", e);
            return "// ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    /**
     * 构建完整文件转换的提示词
     */
    private String buildFullFileConversionPrompt(String fileName, String cCode) {
        return buildFullFileConversionPrompt(fileName, cCode, null);
    }

    /**
     * 构建完整文件转换的提示词，包含安全约束
     */
    private String buildFullFileConversionPrompt(String fileName, String cCode, List<SecurityIssue> knownIssues) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Convert the following C/C++ file to Rust.\n\n")
              .append("Source file: ").append(fileName).append("\n\n")
              .append("```c\n")
              .append(cCode)
              .append("\n```\n\n");

        // 添加安全问题约束
        if (knownIssues != null && !knownIssues.isEmpty()) {
            prompt.append("⚠️ 已知安全问题（在迁移时必须修复）：\n\n");
            for (int i = 0; i < knownIssues.size(); i++) {
                SecurityIssue issue = knownIssues.get(i);
                prompt.append(String.format(
                    "[%d] %s（%s）\n    描述：%s\n\n",
                    i + 1,
                    issue.getTitle(),
                    issue.getSeverity().getDisplayName(),
                    issue.getDescription()
                ));
            }

            prompt.append("重要提示：\n")
                  .append("- 在 Rust 代码中修复上述所有已知安全问题\n")
                  .append("- 利用 Rust 的安全特性（借用检查、内存安全等）来解决这些问题\n")
                  .append("- 在代码中添加注释说明如何处理这些问题\n\n");
        }

        prompt.append("Generate a complete Rust file (.rs) with:\n")
              .append("- All necessary use statements\n")
              .append("- Type-safe conversions\n")
              .append("- Proper error handling\n")
              .append("- Idiomatic Rust code\n")
              .append("- Comments for complex conversions\n\n")
              .append("Output ONLY the Rust code (no markdown, no explanations):");

        return prompt.toString();
    }

    /**
     * 清理生成的代码，移除可能的 markdown 标记
     */
    private String cleanGeneratedCode(String code) {
        // 移除 markdown 代码块标记
        code = code.replaceAll("^```rust\\s*\n", "");
        code = code.replaceAll("^```\\s*\n", "");
        code = code.replaceAll("\n```\\s*$", "");

        // 移除开头的空行
        code = code.replaceAll("^\\s+", "");

        return code.trim() + "\n"; // 确保文件以换行符结尾
    }

    /**
     * 修复编译错误 - 根据编译器反馈自动修正代码
     *
     * @param originalRustCode 原始的 Rust 代码
     * @param compilationErrors 编译错误信息
     * @param clippyWarnings Clippy 警告信息（可选）
     * @return 修复后的 Rust 代码
     */
    public String fixCompilationErrors(String originalRustCode, String compilationErrors, String clippyWarnings) {
        logger.info("Attempting to fix compilation errors");

        try {
            // 构建修复提示词
            String prompt = buildFixPrompt(originalRustCode, compilationErrors, clippyWarnings);

            // 构建 LLM 请求
            LLMRequest request = LLMRequest.builder()
                .model(model)
                .temperature(RUST_GENERATION_TEMPERATURE)
                .maxTokens(RUST_GENERATION_MAX_TOKENS)
                .addSystemMessage(
                    "You are an expert Rust compiler debugger. " +
                    "Your task is to fix compilation errors in Rust code. " +
                    "Follow these rules:\n" +
                    "1. Generate ONLY the fixed Rust code - no explanations, no markdown\n" +
                    "2. Fix ALL compilation errors reported by rustc\n" +
                    "3. Address Clippy warnings where applicable\n" +
                    "4. Maintain the original functionality\n" +
                    "5. Use idiomatic Rust patterns\n" +
                    "6. Minimize use of unsafe code; if unsafe is necessary, add detailed # SAFETY comments\n" +
                    "7. Prefer safe abstractions over raw pointers\n" +
                    "8. Return complete, compilable code"
                )
                .addUserMessage(prompt)
                .build();

            // 发送请求
            logger.debug("Sending fix request to LLM");
            LLMResponse response = llmProvider.sendRequest(request);

            // 检查响应
            if (!response.isSuccess()) {
                logger.error("LLM fix request failed: {}", response.getErrorMessage());
                return "// ERROR: Failed to fix code - " + response.getErrorMessage();
            }

            String fixedCode = response.getContent();
            if (fixedCode == null || fixedCode.trim().isEmpty()) {
                logger.error("LLM returned empty response for fix");
                return "// ERROR: LLM returned empty response for fix";
            }

            // 清理生成的代码
            fixedCode = cleanGeneratedCode(fixedCode);

            logger.info("Code fix generated successfully ({} tokens used)",
                response.getTotalTokens());

            return fixedCode;

        } catch (Exception e) {
            logger.error("Failed to fix compilation errors", e);
            return "// ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    /**
     * 构建修复提示词
     */
    private String buildFixPrompt(String originalCode, String compilationErrors, String clippyWarnings) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("The following Rust code has compilation errors:\n\n");
        prompt.append("```rust\n");
        prompt.append(originalCode);
        prompt.append("\n```\n\n");

        prompt.append("Compiler errors:\n");
        prompt.append("```\n");
        prompt.append(compilationErrors);
        prompt.append("\n```\n\n");

        if (clippyWarnings != null && !clippyWarnings.trim().isEmpty()) {
            prompt.append("Clippy warnings:\n");
            prompt.append("```\n");
            prompt.append(clippyWarnings);
            prompt.append("\n```\n\n");
        }

        prompt.append("Please fix these errors and return the complete, corrected Rust code. ");
        prompt.append("Focus on:\n");
        prompt.append("1. Fixing all compilation errors\n");
        prompt.append("2. Addressing lifetime and borrowing issues\n");
        prompt.append("3. Ensuring type safety\n");
        prompt.append("4. Minimizing unsafe code\n");
        prompt.append("5. Using idiomatic Rust patterns\n\n");
        prompt.append("Return ONLY the fixed Rust code (no explanations, no markdown):");

        return prompt.toString();
    }

    /**
     * 检查 LLM 提供者是否可用
     *
     * @return true 如果提供者可用
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
