package com.harmony.agent.llm.orchestrator;

import com.harmony.agent.llm.model.LLMResponse;
import com.harmony.agent.llm.model.ToolDefinition;
import com.harmony.agent.llm.model.ToolCall;
import com.harmony.agent.llm.provider.LLMProvider;
import com.harmony.agent.llm.provider.ProviderFactory;
import com.harmony.agent.llm.role.LLMRole;
import com.harmony.agent.llm.role.RoleFactory;
import com.harmony.agent.mcp.MCPClientManager;
import com.harmony.agent.task.TodoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates workflow between different LLM roles
 * Manages Provider-Role mapping and execution flow
 * 支持本地工具和通过 MCP 协议的远程工具调用
 */
public class LLMOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(LLMOrchestrator.class);

    private final ProviderFactory providerFactory;
    private final RoleFactory roleFactory;
    private final Map<String, RoleConfig> roleConfigs;
    private final AIMemoryManager aiMemoryManager;
    private final List<ToolDefinition> toolDefinitions;
    private final MCPClientManager mcpClientManager;
    private ToolConfirmationCallback confirmationCallback;  // 工具执行确认回调

    public LLMOrchestrator(ProviderFactory providerFactory, RoleFactory roleFactory) {
        this.providerFactory = providerFactory;
        this.roleFactory = roleFactory;
        this.roleConfigs = new HashMap<>();
        this.aiMemoryManager = new AIMemoryManager();
        this.toolDefinitions = new ArrayList<>();
        this.mcpClientManager = new MCPClientManager();

        // 初始化工具定义
        initializeToolDefinitions();

        // 尝试加载 MCP 配置
        initializeMCPClients();

        int totalTools = toolDefinitions.size() + mcpClientManager.getAllMcpTools().size();
        logger.info("LLMOrchestrator initialized: {} 本地工具 + {} MCP 工具 = {} 总工具",
            toolDefinitions.size(),
            mcpClientManager.getAllMcpTools().size(),
            totalTools);
    }

    /**
     * Configure a role with specific provider and model
     */
    public void configureRole(String roleName, String providerName, String model) {
        roleConfigs.put(roleName, new RoleConfig(providerName, model));
        logger.info("Configured role '{}' to use provider '{}' with model '{}'",
            roleName, providerName, model);
    }

    /**
     * 初始化 MCP 客户端
     * 尝试从 mcp-config.json 加载配置并连接到 MCP 服务
     * 如果配置文件不存在或连接失败，记录警告但继续运行
     */
    private void initializeMCPClients() {
        String configPath = "mcp-config.json";
        try {
            mcpClientManager.loadFromConfig(configPath);
            logger.info("✅ MCP 客户端已初始化");
            logger.info(mcpClientManager.getStatistics());
        } catch (java.io.FileNotFoundException e) {
            logger.info("ℹ️ MCP 配置文件不存在: {} (将仅使用本地工具)", configPath);
        } catch (Exception e) {
            logger.warn("⚠️ 初始化 MCP 客户端失败: {} (将仅使用本地工具)", e.getMessage());
        }
    }

    /**
     * Execute a role with automatic provider/model configuration
     */
    public LLMResponse executeRole(String roleName, String input, ConversationContext context) {
        logger.info("Executing role: {}", roleName);

        // Get role
        LLMRole role = roleFactory.getRole(roleName);

        // Get role configuration
        RoleConfig config = roleConfigs.get(roleName);
        if (config == null) {
            return LLMResponse.builder()
                .errorMessage("Role not configured: " + roleName)
                .build();
        }

        // Get provider
        LLMProvider provider = providerFactory.getProvider(config.providerName);

        // Configure role
        role.setProvider(provider);
        role.setModel(config.model);

        // Build context string
        String contextString = context != null ? context.buildContextString() : "";

        // Execute role
        return role.execute(input, contextString);
    }

    /**
     * Analyze requirement and create todo list
     * 现在使用 PlannerRole 而不是 AnalyzerRole，以获得更结构化的分析结果
     */
    public TodoList analyzeRequirement(String requirement) {
        logger.info("Analyzing requirement using PlannerRole...");

        ConversationContext context = new ConversationContext(requirement);
        // 使用 planner 角色进行需求分析和规划，而不是 analyzer
        LLMResponse response = executeRole("planner", requirement, context);

        if (!response.isSuccess()) {
            logger.error("Failed to analyze requirement: {}", response.getErrorMessage());
            return null;
        }

        // 解析响应中的任务列表
        List<String> tasks = parseTasksFromResponse(response.getContent());
        TodoList todoList = new TodoList(requirement, tasks);

        // 将完整的分析结果存储到 TodoList 的元数据中
        todoList.setAnalysisResult(response.getContent());

        context.setTodoList(todoList);
        return todoList;
    }

    /**
     * Create design document
     */
    public String createDesign(ConversationContext context, String specificTask) {
        logger.info("Creating design document...");

        LLMResponse response = executeRole("planner", specificTask, context);

        if (!response.isSuccess()) {
            logger.error("Failed to create design: {}", response.getErrorMessage());
            return null;
        }

        String design = response.getContent();
        context.setDesignDocument(design);
        return design;
    }

    /**
     * Generate code
     */
    public String generateCode(ConversationContext context, String taskDescription) {
        logger.info("Generating code...");

        LLMResponse response = executeRole("coder", taskDescription, context);

        if (!response.isSuccess()) {
            logger.error("Failed to generate code: {}", response.getErrorMessage());
            return null;
        }

        return response.getContent();
    }

    /**
     * Review code
     */
    public String reviewCode(ConversationContext context, String code) {
        logger.info("Reviewing code...");

        String input = "Please review the following code:\n\n" + code;
        LLMResponse response = executeRole("reviewer", input, context);

        if (!response.isSuccess()) {
            logger.error("Failed to review code: {}", response.getErrorMessage());
            return null;
        }

        return response.getContent();
    }

    /**
     * Parse tasks from LLM response
     */
    private List<String> parseTasksFromResponse(String response) {
        // Simple parsing: split by newlines and extract numbered items
        return response.lines()
            .map(String::trim)
            .filter(line -> line.matches("^\\d+\\..*"))
            .map(line -> line.replaceFirst("^\\d+\\.\\s*", ""))
            .toList();
    }

    /**
     * 初始化工具定义
     * 定义 AI 可以调用的所有工具
     */
    private void initializeToolDefinitions() {
        // 工具 1: 读取文件
        Map<String, Object> readFileParams = new HashMap<>();
        readFileParams.put("type", "object");
        Map<String, Object> readFileProperties = new HashMap<>();
        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径");
        readFileProperties.put("path", pathProp);
        readFileParams.put("properties", readFileProperties);
        readFileParams.put("required", List.of("path"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("read_file")
            .description("读取文件内容，用于获取代码、文档等文件的内容进行分析")
            .parameters(readFileParams)
            .build());

        // 工具 2: 搜索文件
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("type", "object");
        Map<String, Object> searchProperties = new HashMap<>();
        Map<String, Object> keywordProp = new HashMap<>();
        keywordProp.put("type", "string");
        keywordProp.put("description", "搜索关键词");
        searchProperties.put("keyword", keywordProp);
        Map<String, Object> dirProp = new HashMap<>();
        dirProp.put("type", "string");
        dirProp.put("description", "搜索目录，例如 'src/'");
        searchProperties.put("directory", dirProp);
        searchParams.put("properties", searchProperties);
        searchParams.put("required", List.of("keyword", "directory"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("search")
            .description("在指定目录中搜索包含关键词的文件，用于找到相关代码")
            .parameters(searchParams)
            .build());

        // 工具 3: 写入文件
        Map<String, Object> writeParams = new HashMap<>();
        writeParams.put("type", "object");
        Map<String, Object> writeProperties = new HashMap<>();
        Map<String, Object> writePathProp = new HashMap<>();
        writePathProp.put("type", "string");
        writePathProp.put("description", "目标文件路径");
        writeProperties.put("path", writePathProp);
        Map<String, Object> contentProp = new HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "要写入的内容");
        writeProperties.put("content", contentProp);
        writeParams.put("properties", writeProperties);
        writeParams.put("required", List.of("path", "content"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("write_file")
            .description("将内容写入文件，用于创建或修改代码文件")
            .parameters(writeParams)
            .build());

        // 工具 4: 追加文件
        Map<String, Object> appendParams = new HashMap<>();
        appendParams.put("type", "object");
        Map<String, Object> appendProperties = new HashMap<>();
        Map<String, Object> appendPathProp = new HashMap<>();
        appendPathProp.put("type", "string");
        appendPathProp.put("description", "目标文件路径");
        appendProperties.put("path", appendPathProp);
        Map<String, Object> appendContentProp = new HashMap<>();
        appendContentProp.put("type", "string");
        appendContentProp.put("description", "要追加的内容");
        appendProperties.put("content", appendContentProp);
        appendParams.put("properties", appendProperties);
        appendParams.put("required", List.of("path", "content"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("append_file")
            .description("向文件末尾追加内容，用于修改日志、配置等文件")
            .parameters(appendParams)
            .build());

        // 工具 5: grep 搜索
        Map<String, Object> grepParams = new HashMap<>();
        grepParams.put("type", "object");
        Map<String, Object> grepProperties = new HashMap<>();
        Map<String, Object> grepPatternProp = new HashMap<>();
        grepPatternProp.put("type", "string");
        grepPatternProp.put("description", "要搜索的模式或关键词");
        grepProperties.put("pattern", grepPatternProp);
        Map<String, Object> grepFileProp = new HashMap<>();
        grepFileProp.put("type", "string");
        grepFileProp.put("description", "目标文件路径");
        grepProperties.put("filepath", grepFileProp);
        grepParams.put("properties", grepProperties);
        grepParams.put("required", List.of("pattern", "filepath"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("grep")
            .description("在指定文件中搜索匹配的行，并返回行号和内容")
            .parameters(grepParams)
            .build());

        // 工具 6: pwd - 获取当前工作目录
        Map<String, Object> pwdParams = new HashMap<>();
        pwdParams.put("type", "object");
        pwdParams.put("properties", new HashMap<>());
        pwdParams.put("required", new ArrayList<>());

        toolDefinitions.add(ToolDefinition.builder()
            .name("pwd")
            .description("获取当前工作目录的完整路径")
            .parameters(pwdParams)
            .build());

        // 工具 7: cd - 改变工作目录
        Map<String, Object> cdParams = new HashMap<>();
        cdParams.put("type", "object");
        Map<String, Object> cdProps = new HashMap<>();
        Map<String, Object> cdPathProp = new HashMap<>();
        cdPathProp.put("type", "string");
        cdPathProp.put("description", "要切换到的目录路径，可以是绝对路径或相对路径");
        cdProps.put("path", cdPathProp);
        cdParams.put("properties", cdProps);
        cdParams.put("required", List.of("path"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("cd")
            .description("改变当前工作目录，影响后续操作的工作目录")
            .parameters(cdParams)
            .build());

        // 工具 8: ls - 列出目录内容
        Map<String, Object> lsParams = new HashMap<>();
        lsParams.put("type", "object");
        Map<String, Object> lsProps = new HashMap<>();
        Map<String, Object> lsPathProp = new HashMap<>();
        lsPathProp.put("type", "string");
        lsPathProp.put("description", "要列出的目录路径，默认为当前目录");
        lsProps.put("path", lsPathProp);
        lsParams.put("properties", lsProps);
        lsParams.put("required", new ArrayList<>());

        toolDefinitions.add(ToolDefinition.builder()
            .name("ls")
            .description("列出指定目录中的文件和子目录，不包括隐藏文件")
            .parameters(lsParams)
            .build());

        // 工具 9: cat - 查看文件内容
        Map<String, Object> catParams = new HashMap<>();
        catParams.put("type", "object");
        Map<String, Object> catProps = new HashMap<>();
        Map<String, Object> catPathProp = new HashMap<>();
        catPathProp.put("type", "string");
        catPathProp.put("description", "文件路径");
        catProps.put("path", catPathProp);
        Map<String, Object> catLimitProp = new HashMap<>();
        catLimitProp.put("type", "integer");
        catLimitProp.put("description", "最多显示的行数，默认显示全部");
        catProps.put("limit_lines", catLimitProp);
        catParams.put("properties", catProps);
        catParams.put("required", List.of("path"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("cat")
            .description("查看文件内容，可以指定最多显示的行数")
            .parameters(catParams)
            .build());

        // 工具 10: mkdir - 创建目录
        Map<String, Object> mkdirParams = new HashMap<>();
        mkdirParams.put("type", "object");
        Map<String, Object> mkdirProps = new HashMap<>();
        Map<String, Object> mkdirPathProp = new HashMap<>();
        mkdirPathProp.put("type", "string");
        mkdirPathProp.put("description", "要创建的目录路径");
        mkdirProps.put("path", mkdirPathProp);
        Map<String, Object> mkdirRecursiveProp = new HashMap<>();
        mkdirRecursiveProp.put("type", "boolean");
        mkdirRecursiveProp.put("description", "是否递归创建父目录，默认 true");
        mkdirProps.put("recursive", mkdirRecursiveProp);
        mkdirParams.put("properties", mkdirProps);
        mkdirParams.put("required", List.of("path"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("mkdir")
            .description("创建新目录，支持递归创建父目录")
            .parameters(mkdirParams)
            .build());

        // 工具 11: rm - 删除文件或目录
        Map<String, Object> rmParams = new HashMap<>();
        rmParams.put("type", "object");
        Map<String, Object> rmProps = new HashMap<>();
        Map<String, Object> rmPathProp = new HashMap<>();
        rmPathProp.put("type", "string");
        rmPathProp.put("description", "要删除的文件或目录路径");
        rmProps.put("path", rmPathProp);
        Map<String, Object> rmRecursiveProp = new HashMap<>();
        rmRecursiveProp.put("type", "boolean");
        rmRecursiveProp.put("description", "是否递归删除目录，默认 false");
        rmProps.put("recursive", rmRecursiveProp);
        rmParams.put("properties", rmProps);
        rmParams.put("required", List.of("path"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("rm")
            .description("删除文件或目录，删除目录时需要指定 recursive=true")
            .parameters(rmParams)
            .build());

        // 工具 12: shell_exec - 执行任意 shell 命令
        Map<String, Object> shellParams = new HashMap<>();
        shellParams.put("type", "object");
        Map<String, Object> shellProps = new HashMap<>();
        Map<String, Object> shellCmdProp = new HashMap<>();
        shellCmdProp.put("type", "string");
        shellCmdProp.put("description", "要执行的 shell 命令，例如 'ls -la', 'java -version' 等");
        shellProps.put("command", shellCmdProp);
        Map<String, Object> shellTimeoutProp = new HashMap<>();
        shellTimeoutProp.put("type", "integer");
        shellTimeoutProp.put("description", "命令执行超时时间（秒），默认 30 秒");
        shellProps.put("timeout_seconds", shellTimeoutProp);
        shellParams.put("properties", shellProps);
        shellParams.put("required", List.of("command"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("shell_exec")
            .description("执行任意 shell/bash 命令，支持管道和重定向，适合运行编译、测试、构建命令等")
            .parameters(shellParams)
            .build());

        // 工具 13: cargo - Rust 包管理和构建
        Map<String, Object> cargoParams = new HashMap<>();
        cargoParams.put("type", "object");
        Map<String, Object> cargoProps = new HashMap<>();
        Map<String, Object> cargoSubcmdProp = new HashMap<>();
        cargoSubcmdProp.put("type", "string");
        cargoSubcmdProp.put("description", "Cargo 子命令，例如 'build', 'test', 'check', 'clippy' 等");
        cargoProps.put("subcommand", cargoSubcmdProp);
        Map<String, Object> cargoArgsProp = new HashMap<>();
        cargoArgsProp.put("type", "string");
        cargoArgsProp.put("description", "额外的 cargo 参数，例如 '--release', '--all-features'");
        cargoProps.put("args", cargoArgsProp);
        cargoParams.put("properties", cargoProps);
        cargoParams.put("required", List.of("subcommand"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("cargo")
            .description("运行 Rust cargo 命令，支持 build, test, check, clippy 等子命令")
            .parameters(cargoParams)
            .build());

        // 工具 14: gcc/clang - C/C++ 编译
        Map<String, Object> gccParams = new HashMap<>();
        gccParams.put("type", "object");
        Map<String, Object> gccProps = new HashMap<>();
        Map<String, Object> gccFileProp = new HashMap<>();
        gccFileProp.put("type", "string");
        gccFileProp.put("description", "要编译的源文件，例如 'main.c' 或 'lib.cpp'");
        gccProps.put("source_file", gccFileProp);
        Map<String, Object> gccOutputProp = new HashMap<>();
        gccOutputProp.put("type", "string");
        gccOutputProp.put("description", "输出文件名，例如 'main' 或 'lib.o'");
        gccProps.put("output_file", gccOutputProp);
        Map<String, Object> gccFlagsProp = new HashMap<>();
        gccFlagsProp.put("type", "string");
        gccFlagsProp.put("description", "编译标志，例如 '-Wall -O2 -std=c++17'");
        gccProps.put("flags", gccFlagsProp);
        gccParams.put("properties", gccProps);
        gccParams.put("required", List.of("source_file", "output_file"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("gcc")
            .description("编译 C/C++ 源文件，使用 gcc/g++ 或 clang")
            .parameters(gccParams)
            .build());

        // 工具 15: git_status - 查看 Git 状态
        Map<String, Object> gitStatusParams = new HashMap<>();
        gitStatusParams.put("type", "object");
        gitStatusParams.put("properties", new HashMap<>());
        gitStatusParams.put("required", new ArrayList<>());

        toolDefinitions.add(ToolDefinition.builder()
            .name("git_status")
            .description("显示工作目录的 Git 状态，包括已修改、已暂存、未追踪文件等")
            .parameters(gitStatusParams)
            .build());

        // 工具 16: git_diff - 查看文件变更
        Map<String, Object> gitDiffParams = new HashMap<>();
        gitDiffParams.put("type", "object");
        Map<String, Object> gitDiffProps = new HashMap<>();
        Map<String, Object> gitDiffFileProp = new HashMap<>();
        gitDiffFileProp.put("type", "string");
        gitDiffFileProp.put("description", "要查看变更的文件路径，如果不指定则显示所有变更");
        gitDiffProps.put("file", gitDiffFileProp);
        Map<String, Object> gitDiffStagedProp = new HashMap<>();
        gitDiffStagedProp.put("type", "boolean");
        gitDiffStagedProp.put("description", "是否显示已暂存的变更，默认 false");
        gitDiffProps.put("staged", gitDiffStagedProp);
        gitDiffParams.put("properties", gitDiffProps);
        gitDiffParams.put("required", new ArrayList<>());

        toolDefinitions.add(ToolDefinition.builder()
            .name("git_diff")
            .description("显示文件在工作目录中的变更，或已暂存的变更")
            .parameters(gitDiffParams)
            .build());

        // 工具 17: git_apply - 应用 patch
        Map<String, Object> gitApplyParams = new HashMap<>();
        gitApplyParams.put("type", "object");
        Map<String, Object> gitApplyProps = new HashMap<>();
        Map<String, Object> gitApplyPatchProp = new HashMap<>();
        gitApplyPatchProp.put("type", "string");
        gitApplyPatchProp.put("description", "Patch 文件的路径，包含要应用的代码变更");
        gitApplyProps.put("patch_file", gitApplyPatchProp);
        gitApplyParams.put("properties", gitApplyProps);
        gitApplyParams.put("required", List.of("patch_file"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("git_apply")
            .description("应用 patch 文件到工作目录，用于应用 AI 生成的代码补丁")
            .parameters(gitApplyParams)
            .build());

        // 工具 18: git_commit - 提交代码
        Map<String, Object> gitCommitParams = new HashMap<>();
        gitCommitParams.put("type", "object");
        Map<String, Object> gitCommitProps = new HashMap<>();
        Map<String, Object> gitCommitMsgProp = new HashMap<>();
        gitCommitMsgProp.put("type", "string");
        gitCommitMsgProp.put("description", "提交消息");
        gitCommitProps.put("message", gitCommitMsgProp);
        Map<String, Object> gitCommitFileProp = new HashMap<>();
        gitCommitFileProp.put("type", "string");
        gitCommitFileProp.put("description", "要提交的文件列表，逗号分隔，如果为空则提交所有已暂存文件");
        gitCommitProps.put("files", gitCommitFileProp);
        gitCommitParams.put("properties", gitCommitProps);
        gitCommitParams.put("required", List.of("message"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("git_commit")
            .description("提交代码到 Git 仓库，需要指定提交消息")
            .parameters(gitCommitParams)
            .build());

        // 工具 19: verify_code - 代码验证（用于 GVIU/FVI 流程）
        Map<String, Object> verifyParams = new HashMap<>();
        verifyParams.put("type", "object");
        Map<String, Object> verifyProps = new HashMap<>();
        Map<String, Object> verifyLangProp = new HashMap<>();
        verifyLangProp.put("type", "string");
        verifyLangProp.put("description", "编程语言，例如 'rust', 'cpp', 'c', 'java'");
        verifyProps.put("language", verifyLangProp);
        Map<String, Object> verifyFileProp = new HashMap<>();
        verifyFileProp.put("type", "string");
        verifyFileProp.put("description", "要验证的文件或目录");
        verifyProps.put("file_or_dir", verifyFileProp);
        Map<String, Object> verifyTypeProp = new HashMap<>();
        verifyTypeProp.put("type", "string");
        verifyTypeProp.put("description", "验证类型：'compile' 编译检查，'test' 运行测试，'check' 代码检查");
        verifyProps.put("verify_type", verifyTypeProp);
        verifyParams.put("properties", verifyProps);
        verifyParams.put("required", List.of("language", "file_or_dir", "verify_type"));

        toolDefinitions.add(ToolDefinition.builder()
            .name("verify_code")
            .description("自动验证代码，用于 GVIU/FVI 流程中的 [V] 步骤，支持编译检查、测试运行、代码分析等")
            .parameters(verifyParams)
            .build());

        logger.info("Initialized {} tools for AI", toolDefinitions.size());
    }

    /**
     * 获取所有工具定义
     * @return 工具定义列表，用于声明给 LLM
     */
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> allTools = new ArrayList<>(toolDefinitions);
        // 添加 MCP 工具定义
        allTools.addAll(mcpClientManager.getAllMcpTools().values());
        return allTools;
    }

    /**
     * 处理工具调用
     * 当 AI 返回工具调用时，执行相应的工具并返回结果
     *
     * @param toolCalls 工具调用列表
     * @return 工具执行结果（格式化的字符串）
     */
    public String handleToolCalls(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "";
        }

        StringBuilder results = new StringBuilder();

        for (ToolCall call : toolCalls) {
            logger.info("🔧 执行工具调用: {} with arguments: {}", call.getName(), call.getArguments());

            String toolResult;
            try {
                // 检查是否需要人类确认（有副作用的操作）
                if (needsConfirmation(call) && !isConfirmed(call)) {
                    toolResult = "⚠️ 工具调用已取消：用户未确认此操作（" + call.getName() + "）";
                    logger.warn("工具调用被用户取消: {}", call.getName());
                } else {
                    toolResult = executeToolCall(call);
                    logger.info("✅ 工具执行成功: {}", call.getName());
                }
            } catch (Exception e) {
                logger.error("❌ 工具执行失败: {}", call.getName(), e);
                toolResult = "❌ 错误：工具执行失败 - " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }

            // 将结果存入 AI 记忆
            aiMemoryManager.rememberToolResult(call.getName(), toolResult);

            // 添加到结果集
            if (results.length() > 0) {
                results.append("\n\n");
            }
            results.append("【").append(call.getName()).append(" 执行结果】\n");
            results.append(toolResult);
        }

        return results.toString();
    }

    /**
     * 检查工具调用是否需要人类确认
     * 有副作用的操作需要确认：write_file, append_file, cargo, gcc, git_apply, git_commit
     * @param call 工具调用
     * @return true 如果需要确认，false 否则
     */
    private boolean needsConfirmation(ToolCall call) {
        String toolName = call.getName();
        return "write_file".equals(toolName) ||
               "append_file".equals(toolName) ||
               "cargo".equals(toolName) ||
               "gcc".equals(toolName) ||
               "git_apply".equals(toolName) ||
               "git_commit".equals(toolName);
    }

    /**
     * 询问用户是否确认执行此工具调用
     * @param call 工具调用
     * @return true 如果用户确认，false 否则
     */
    private boolean isConfirmed(ToolCall call) {
        // 如果未设置确认回调，则默认允许执行
        if (confirmationCallback == null) {
            logger.info("未设置确认回调，默认允许执行");
            return true;
        }

        // 调用回调获取用户确认
        String message = buildConfirmationMessage(call);
        return confirmationCallback.requestConfirmation(call.getName(), message);
    }

    /**
     * 构建人类确认的提示消息
     * @param call 工具调用
     * @return 确认提示消息
     */
    private String buildConfirmationMessage(ToolCall call) {
        StringBuilder msg = new StringBuilder();
        msg.append("\n⚠️  AI 即将执行有副作用的操作：").append(call.getName()).append("\n");
        msg.append("参数：\n");
        call.getArguments().forEach((key, value) -> {
            msg.append("  - ").append(key).append(": ");
            String valueStr = String.valueOf(value);
            if (valueStr.length() > 100) {
                msg.append(valueStr, 0, 100).append("...");
            } else {
                msg.append(valueStr);
            }
            msg.append("\n");
        });
        msg.append("\n请确认是否继续执行？(yes/no): ");
        return msg.toString();
    }

    /**
     * 设置工具执行的确认回调
     * 当工具需要人类确认时，会调用此回调
     * @param callback 确认回调接口
     */
    public void setConfirmationCallback(ToolConfirmationCallback callback) {
        this.confirmationCallback = callback;
        logger.info("设置工具执行确认回调");
    }

    /**
     * 执行单个工具调用
     * @param call 工具调用对象
     * @return 工具执行结果
     */
    private String executeToolCall(ToolCall call) {
        String toolName = call.getName();

        // 首先检查是否是 MCP 工具
        if (mcpClientManager.isMcpTool(toolName)) {
            try {
                logger.info("🔌 通过 MCP 客户端调用工具: {}", toolName);
                return mcpClientManager.callMcpTool(toolName, call.getArguments());
            } catch (Exception e) {
                logger.error("❌ MCP 工具调用失败: {}", toolName, e);
                return "❌ 错误：MCP 工具执行失败 - " + e.getMessage();
            }
        }

        // 执行本地工具
        switch (toolName) {
            case "read_file":
                return executeReadFileTool(call);
            case "write_file":
                return executeWriteFileTool(call);
            case "append_file":
                return executeAppendFileTool(call);
            case "search":
                return executeSearchTool(call);
            case "grep":
                return executeGrepTool(call);
            case "pwd":
                return executePwdTool(call);
            case "cd":
                return executeCdTool(call);
            case "ls":
                return executeLsTool(call);
            case "cat":
                return executeCatTool(call);
            case "mkdir":
                return executeMkdirTool(call);
            case "rm":
                return executeRmTool(call);
            case "shell_exec":
                return executeShellExecTool(call);
            case "cargo":
                return executeCargoTool(call);
            case "gcc":
                return executeGccTool(call);
            case "git_status":
                return executeGitStatusTool(call);
            case "git_diff":
                return executeGitDiffTool(call);
            case "git_apply":
                return executeGitApplyTool(call);
            case "git_commit":
                return executeGitCommitTool(call);
            case "verify_code":
                return executeVerifyCodeTool(call);
            default:
                return "未知工具: " + toolName;
        }
    }

    /**
     * 执行 read_file 工具
     */
    private String executeReadFileTool(ToolCall call) {
        String path = call.getStringArgument("path");
        if (path == null || path.isEmpty()) {
            return "错误：缺少必要参数 'path'";
        }

        try {
            // 使用 Java NIO 读取文件
            String content = java.nio.file.Files.readString(java.nio.file.Paths.get(path));
            // 存储文件内容到记忆，用于后续 Prompt 注入
            aiMemoryManager.rememberFile(path, content);
            logger.info("✅ 成功读取文件: {}", path);
            return content;
        } catch (java.nio.file.NoSuchFileException e) {
            return "错误：文件不存在 - " + path;
        } catch (Exception e) {
            return "错误：无法读取文件 - " + e.getMessage();
        }
    }

    /**
     * 执行 write_file 工具
     */
    private String executeWriteFileTool(ToolCall call) {
        String path = call.getStringArgument("path");
        String content = call.getStringArgument("content");

        if (path == null || path.isEmpty()) {
            return "错误：缺少必要参数 'path'";
        }
        if (content == null) {
            return "错误：缺少必要参数 'content'";
        }

        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(path);
            // 确保父目录存在
            java.nio.file.Files.createDirectories(filePath.getParent());
            // 写入文件
            java.nio.file.Files.writeString(filePath, content);
            logger.info("✅ 成功写入文件: {}", path);
            return "成功写入文件：" + path + " (" + content.length() + " 字符)";
        } catch (Exception e) {
            return "错误：无法写入文件 - " + e.getMessage();
        }
    }

    /**
     * 执行 search 工具
     */
    private String executeSearchTool(ToolCall call) {
        String keyword = call.getStringArgument("keyword");
        String directory = call.getStringArgument("directory");

        if (keyword == null || keyword.isEmpty()) {
            return "错误：缺少必要参数 'keyword'";
        }
        if (directory == null || directory.isEmpty()) {
            return "错误：缺少必要参数 'directory'";
        }

        try {
            java.nio.file.Path dirPath = java.nio.file.Paths.get(directory);
            if (!java.nio.file.Files.isDirectory(dirPath)) {
                return "错误：目录不存在 - " + directory;
            }

            StringBuilder results = new StringBuilder();
            results.append("搜索关键词: ").append(keyword).append("\n");
            results.append("搜索目录: ").append(directory).append("\n\n");
            results.append("搜索结果:\n");

            // 递归搜索文件中的关键词
            java.nio.file.Files.walk(dirPath)
                .filter(java.nio.file.Files::isRegularFile)
                .limit(100)  // 限制搜索数量，防止过多
                .forEach(path -> {
                    try {
                        String content = java.nio.file.Files.readString(path);
                        if (content.contains(keyword)) {
                            results.append("- ").append(dirPath.relativize(path)).append("\n");
                        }
                    } catch (Exception ignored) {
                        // 跳过无法读取的文件
                    }
                });

            // 存储搜索结果到记忆
            aiMemoryManager.rememberSearchResult(keyword, results.toString());
            logger.info("✅ 搜索完成: {} 在 {}", keyword, directory);
            return results.toString();
        } catch (Exception e) {
            return "错误：搜索失败 - " + e.getMessage();
        }
    }

    /**
     * 执行 append_file 工具
     */
    private String executeAppendFileTool(ToolCall call) {
        String path = call.getStringArgument("path");
        String content = call.getStringArgument("content");

        if (path == null || path.isEmpty()) {
            return "错误：缺少必要参数 'path'";
        }
        if (content == null) {
            return "错误：缺少必要参数 'content'";
        }

        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(path);
            // 确保父目录存在
            java.nio.file.Files.createDirectories(filePath.getParent());
            // 追加内容（带换行符）
            String toAppend = content + "\n";
            java.nio.file.Files.writeString(
                filePath,
                toAppend,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            );
            logger.info("✅ 成功追加文件: {}", path);
            return "成功追加内容到文件：" + path + " (" + content.length() + " 字符)";
        } catch (Exception e) {
            return "错误：无法追加文件 - " + e.getMessage();
        }
    }

    /**
     * 执行 grep 工具
     */
    private String executeGrepTool(ToolCall call) {
        String pattern = call.getStringArgument("pattern");
        String filepath = call.getStringArgument("filepath");

        if (pattern == null || pattern.isEmpty()) {
            return "错误：缺少必要参数 'pattern'";
        }
        if (filepath == null || filepath.isEmpty()) {
            return "错误：缺少必要参数 'filepath'";
        }

        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(filepath);
            if (!java.nio.file.Files.exists(filePath)) {
                return "错误：文件不存在 - " + filepath;
            }
            if (!java.nio.file.Files.isRegularFile(filePath)) {
                return "错误：不是一个文件 - " + filepath;
            }

            java.util.List<String> lines = java.nio.file.Files.readAllLines(filePath);
            java.util.List<String> matches = new java.util.ArrayList<>();

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains(pattern)) {
                    matches.add(String.format("%4d | %s", i + 1, line));
                }
            }

            // 构建结果
            StringBuilder result = new StringBuilder();
            result.append("搜索模式: ").append(pattern).append("\n");
            result.append("文件: ").append(filepath).append("\n");
            result.append("匹配行数: ").append(matches.size()).append("\n\n");

            if (matches.isEmpty()) {
                result.append("未找到匹配项");
            } else {
                result.append("匹配结果:\n");
                matches.forEach(match -> result.append(match).append("\n"));
            }

            // 存储 grep 结果到记忆
            aiMemoryManager.rememberSearchResult(pattern + ":" + filepath, result.toString());
            logger.info("✅ Grep 搜索完成: {} 在 {}", pattern, filepath);
            return result.toString();
        } catch (Exception e) {
            return "错误：grep 搜索失败 - " + e.getMessage();
        }
    }

    /**
     * 执行 pwd 工具 - 获取当前工作目录
     */
    private String executePwdTool(ToolCall call) {
        try {
            String cwd = System.getProperty("user.dir");
            logger.info("✅ PWD: {}", cwd);
            return cwd;
        } catch (Exception e) {
            return "错误：无法获取当前目录 - " + e.getMessage();
        }
    }

    /**
     * 执行 cd 工具 - 改变工作目录（在 Java 中无法真正改变进程工作目录，但记录目标）
     */
    private String executeCdTool(ToolCall call) {
        String path = call.getStringArgument("path");
        if (path == null || path.isEmpty()) {
            return "错误：缺少必要参数 'path'";
        }

        try {
            java.nio.file.Path targetPath = java.nio.file.Paths.get(path);
            if (!java.nio.file.Files.exists(targetPath)) {
                return "错误：目录不存在 - " + path;
            }
            if (!java.nio.file.Files.isDirectory(targetPath)) {
                return "错误：不是目录 - " + path;
            }

            // 注：Java 无法改变进程的工作目录，这里返回目标目录的绝对路径
            String absolutePath = targetPath.toAbsolutePath().toString();
            logger.info("✅ 目标目录: {}", absolutePath);
            return "已定位到目录：" + absolutePath + "\n(注：在 Java 中无法改变进程工作目录，但 AI 应该记住这个路径用于后续操作)";
        } catch (Exception e) {
            return "错误：无法访问目录 - " + e.getMessage();
        }
    }

    /**
     * 执行 ls 工具 - 列出目录内容
     */
    private String executeLsTool(ToolCall call) {
        String path = call.getStringArgument("path");
        if (path == null || path.isEmpty()) {
            path = ".";
        }

        try {
            java.nio.file.Path dirPath = java.nio.file.Paths.get(path);
            if (!java.nio.file.Files.exists(dirPath)) {
                return "错误：路径不存在 - " + path;
            }
            if (!java.nio.file.Files.isDirectory(dirPath)) {
                return "错误：不是目录 - " + path;
            }

            StringBuilder result = new StringBuilder();
            result.append("目录: ").append(dirPath.toAbsolutePath()).append("\n\n");

            java.util.List<java.nio.file.Path> entries = new java.util.ArrayList<>();
            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(dirPath)) {
                stream.sorted().forEach(entries::add);
            }

            for (java.nio.file.Path entry : entries) {
                String name = entry.getFileName().toString();
                if (java.nio.file.Files.isDirectory(entry)) {
                    result.append("[DIR]  ").append(name).append("/\n");
                } else {
                    long size = java.nio.file.Files.size(entry);
                    result.append("[FILE] ").append(name).append(" (").append(formatFileSize(size)).append(")\n");
                }
            }

            if (entries.isEmpty()) {
                result.append("(空目录)");
            }

            logger.info("✅ 列出目录: {}", path);
            return result.toString();
        } catch (Exception e) {
            return "错误：无法列出目录 - " + e.getMessage();
        }
    }

    /**
     * 执行 cat 工具 - 查看文件内容
     */
    private String executeCatTool(ToolCall call) {
        String path = call.getStringArgument("path");
        Object limitObj = call.getArgument("limit_lines");
        int limit = (limitObj instanceof Number) ? ((Number) limitObj).intValue() : Integer.MAX_VALUE;

        if (path == null || path.isEmpty()) {
            return "错误：缺少必要参数 'path'";
        }

        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(path);
            if (!java.nio.file.Files.exists(filePath)) {
                return "错误：文件不存在 - " + path;
            }
            if (!java.nio.file.Files.isRegularFile(filePath)) {
                return "错误：不是文件 - " + path;
            }

            java.util.List<String> lines = java.nio.file.Files.readAllLines(filePath);
            StringBuilder result = new StringBuilder();
            result.append("文件: ").append(filePath.toAbsolutePath()).append("\n");
            result.append("行数: ").append(lines.size()).append("\n\n");

            int displayLines = Math.min(limit, lines.size());
            for (int i = 0; i < displayLines; i++) {
                result.append(String.format("%4d | %s\n", i + 1, lines.get(i)));
            }

            if (displayLines < lines.size()) {
                result.append("\n... (省略 ").append(lines.size() - displayLines).append(" 行)");
            }

            logger.info("✅ 查看文件: {}", path);
            return result.toString();
        } catch (Exception e) {
            return "错误：无法读取文件 - " + e.getMessage();
        }
    }

    /**
     * 执行 mkdir 工具 - 创建目录
     */
    private String executeMkdirTool(ToolCall call) {
        String path = call.getStringArgument("path");
        Object recursiveObj = call.getArgument("recursive");
        boolean recursive = (recursiveObj instanceof Boolean) ? (Boolean) recursiveObj : true;

        if (path == null || path.isEmpty()) {
            return "错误：缺少必要参数 'path'";
        }

        try {
            java.nio.file.Path dirPath = java.nio.file.Paths.get(path);
            if (java.nio.file.Files.exists(dirPath)) {
                return "错误：目录已存在 - " + path;
            }

            if (recursive) {
                java.nio.file.Files.createDirectories(dirPath);
            } else {
                java.nio.file.Files.createDirectory(dirPath);
            }

            logger.info("✅ 创建目录: {}", path);
            return "成功创建目录：" + dirPath.toAbsolutePath();
        } catch (Exception e) {
            return "错误：无法创建目录 - " + e.getMessage();
        }
    }

    /**
     * 执行 rm 工具 - 删除文件或目录
     */
    private String executeRmTool(ToolCall call) {
        String path = call.getStringArgument("path");
        Object recursiveObj = call.getArgument("recursive");
        boolean recursive = (recursiveObj instanceof Boolean) ? (Boolean) recursiveObj : false;

        if (path == null || path.isEmpty()) {
            return "错误：缺少必要参数 'path'";
        }

        try {
            java.nio.file.Path targetPath = java.nio.file.Paths.get(path);
            if (!java.nio.file.Files.exists(targetPath)) {
                return "错误：路径不存在 - " + path;
            }

            if (java.nio.file.Files.isRegularFile(targetPath)) {
                java.nio.file.Files.delete(targetPath);
                logger.info("✅ 删除文件: {}", path);
                return "成功删除文件：" + targetPath.toAbsolutePath();
            } else if (java.nio.file.Files.isDirectory(targetPath)) {
                if (!recursive) {
                    return "错误：删除目录需要指定 recursive=true";
                }
                deleteDirectoryRecursively(targetPath);
                logger.info("✅ 删除目录: {}", path);
                return "成功递归删除目录：" + targetPath.toAbsolutePath();
            } else {
                return "错误：不支持的文件类型";
            }
        } catch (Exception e) {
            return "错误：无法删除文件 - " + e.getMessage();
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectoryRecursively(java.nio.file.Path path) throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> {
                      try {
                          java.nio.file.Files.delete(p);
                      } catch (java.io.IOException e) {
                          throw new RuntimeException(e);
                      }
                  });
        }
    }

    /**
     * 执行 shell_exec 工具 - 执行任意 shell 命令
     */
    private String executeShellExecTool(ToolCall call) {
        String command = call.getStringArgument("command");
        Object timeoutObj = call.getArgument("timeout_seconds");
        int timeout = (timeoutObj instanceof Number) ? ((Number) timeoutObj).intValue() : 30;

        if (command == null || command.isEmpty()) {
            return "错误：缺少必要参数 'command'";
        }

        try {
            // 构建平台特定的命令
            String[] shellCommand;
            if (isWindows()) {
                shellCommand = new String[]{"cmd", "/c", command};
            } else {
                shellCommand = new String[]{"/bin/bash", "-c", command};
            }

            ProcessBuilder pb = new ProcessBuilder(shellCommand);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 设置超时
            boolean completed = process.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "错误：命令执行超时（" + timeout + " 秒）";
            }

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (result.isEmpty() && exitCode == 0) {
                result = "(命令执行成功，无输出)";
            }

            logger.info("✅ Shell 命令执行完成: exit_code={}", exitCode);
            return result;
        } catch (Exception e) {
            return "错误：命令执行失败 - " + e.getMessage();
        }
    }

    /**
     * 执行 cargo 工具 - Rust 包管理和构建
     */
    private String executeCargoTool(ToolCall call) {
        String subcommand = call.getStringArgument("subcommand");
        String args = call.getStringArgument("args");

        if (subcommand == null || subcommand.isEmpty()) {
            return "错误：缺少必要参数 'subcommand'";
        }

        try {
            // 构建 cargo 命令
            StringBuilder cmd = new StringBuilder("cargo ").append(subcommand);
            if (args != null && !args.isEmpty()) {
                cmd.append(" ").append(args);
            }

            ProcessBuilder pb = new ProcessBuilder(getShellCommand(cmd.toString()));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 等待完成（cargo 命令可能很耗时）
            boolean completed = process.waitFor(300, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "错误：cargo 命令执行超时（300 秒）";
            }

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (result.isEmpty() && exitCode == 0) {
                result = "(cargo 命令执行成功，无输出)";
            }

            logger.info("✅ Cargo 命令执行完成: exit_code={}", exitCode);
            return result;
        } catch (Exception e) {
            return "错误：cargo 命令执行失败 - " + e.getMessage();
        }
    }

    /**
     * 执行 gcc 工具 - C/C++ 编译
     */
    private String executeGccTool(ToolCall call) {
        String sourceFile = call.getStringArgument("source_file");
        String outputFile = call.getStringArgument("output_file");
        String flags = call.getStringArgument("flags");

        if (sourceFile == null || sourceFile.isEmpty()) {
            return "错误：缺少必要参数 'source_file'";
        }
        if (outputFile == null || outputFile.isEmpty()) {
            return "错误：缺少必要参数 'output_file'";
        }

        try {
            // 确定编译器
            String compiler = "gcc";
            if (sourceFile.endsWith(".cpp") || sourceFile.endsWith(".cc") ||
                sourceFile.endsWith(".cxx") || sourceFile.endsWith(".c++")) {
                compiler = "g++";
            }

            // 构建编译命令
            StringBuilder cmd = new StringBuilder(compiler)
                .append(" -o ").append(outputFile)
                .append(" ").append(sourceFile);

            if (flags != null && !flags.isEmpty()) {
                cmd.append(" ").append(flags);
            }

            ProcessBuilder pb = new ProcessBuilder(getShellCommand(cmd.toString()));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean completed = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "错误：编译超时（60 秒）";
            }

            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (result.isEmpty() && exitCode == 0) {
                result = "✓ 编译成功：输出文件 " + outputFile;
            }

            logger.info("✅ C/C++ 编译完成: exit_code={}", exitCode);
            return result;
        } catch (Exception e) {
            return "错误：编译失败 - " + e.getMessage();
        }
    }

    /**
     * 执行 git_status 工具 - 查看 Git 状态
     */
    private String executeGitStatusTool(ToolCall call) {
        try {
            ProcessBuilder pb = new ProcessBuilder(getShellCommand("git status"));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean completed = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "错误：git status 超时";
            }

            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return "错误：git status 执行失败";
            }

            logger.info("✅ Git 状态查询完成");
            return output.toString().trim();
        } catch (Exception e) {
            return "错误：git status 执行失败 - " + e.getMessage();
        }
    }

    /**
     * 执行 git_diff 工具 - 查看文件变更
     */
    private String executeGitDiffTool(ToolCall call) {
        String file = call.getStringArgument("file");
        Object stagedObj = call.getArgument("staged");
        boolean staged = (stagedObj instanceof Boolean) ? (Boolean) stagedObj : false;

        try {
            StringBuilder cmd = new StringBuilder("git diff");
            if (staged) {
                cmd.append(" --staged");
            }
            if (file != null && !file.isEmpty()) {
                cmd.append(" ").append(file);
            }

            ProcessBuilder pb = new ProcessBuilder(getShellCommand(cmd.toString()));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean completed = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "错误：git diff 超时";
            }

            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (result.isEmpty()) {
                result = "(没有变更)";
            }

            logger.info("✅ Git diff 完成");
            return result;
        } catch (Exception e) {
            return "错误：git diff 执行失败 - " + e.getMessage();
        }
    }

    /**
     * 执行 git_apply 工具 - 应用 patch
     */
    private String executeGitApplyTool(ToolCall call) {
        String patchFile = call.getStringArgument("patch_file");

        if (patchFile == null || patchFile.isEmpty()) {
            return "错误：缺少必要参数 'patch_file'";
        }

        try {
            // 验证 patch 文件存在
            java.nio.file.Path patchPath = java.nio.file.Paths.get(patchFile);
            if (!java.nio.file.Files.exists(patchPath)) {
                return "错误：patch 文件不存在 - " + patchFile;
            }

            ProcessBuilder pb = new ProcessBuilder(getShellCommand("git apply " + patchFile));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean completed = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "错误：git apply 超时";
            }

            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                logger.info("✅ Patch 应用成功");
                return "✓ Patch 已应用：" + patchFile;
            } else {
                return "错误：patch 应用失败\n" + output.toString().trim();
            }
        } catch (Exception e) {
            return "错误：git apply 执行失败 - " + e.getMessage();
        }
    }

    /**
     * 执行 git_commit 工具 - 提交代码
     */
    private String executeGitCommitTool(ToolCall call) {
        String message = call.getStringArgument("message");
        String files = call.getStringArgument("files");

        if (message == null || message.isEmpty()) {
            return "错误：缺少必要参数 'message'";
        }

        try {
            // 如果指定了文件，先 git add
            if (files != null && !files.isEmpty()) {
                String[] fileList = files.split(",");
                for (String file : fileList) {
                    ProcessBuilder addPb = new ProcessBuilder(
                        getShellCommand("git add " + file.trim()));
                    addPb.redirectErrorStream(true);
                    Process addProcess = addPb.start();
                    addProcess.waitFor();
                }
            }

            // 执行 git commit
            String cmd = "git commit -m \"" + message.replace("\"", "\\\"") + "\"";
            ProcessBuilder pb = new ProcessBuilder(getShellCommand(cmd));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean completed = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "错误：git commit 超时";
            }

            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                logger.info("✅ 代码已提交");
                return "✓ 提交成功\n" + output.toString().trim();
            } else {
                // 检查是否是"nothing to commit"
                String result = output.toString();
                if (result.contains("nothing to commit")) {
                    return "✓ 没有变更需要提交";
                }
                return "错误：提交失败\n" + result;
            }
        } catch (Exception e) {
            return "错误：git commit 执行失败 - " + e.getMessage();
        }
    }

    /**
     * 执行 verify_code 工具 - 代码验证（用于 GVIU/FVI 流程）
     * 支持编译检查、测试运行、代码分析
     */
    private String executeVerifyCodeTool(ToolCall call) {
        String language = call.getStringArgument("language");
        String fileOrDir = call.getStringArgument("file_or_dir");
        String verifyType = call.getStringArgument("verify_type");

        if (language == null || language.isEmpty()) {
            return "错误：缺少必要参数 'language'";
        }
        if (fileOrDir == null || fileOrDir.isEmpty()) {
            return "错误：缺少必要参数 'file_or_dir'";
        }
        if (verifyType == null || verifyType.isEmpty()) {
            return "错误：缺少必要参数 'verify_type'";
        }

        try {
            String cmd = null;
            String description = null;

            // 根据语言和验证类型构建命令
            switch (language.toLowerCase()) {
                case "rust":
                    switch (verifyType.toLowerCase()) {
                        case "compile":
                            cmd = "cargo check --manifest-path " + fileOrDir;
                            description = "Rust 编译检查";
                            break;
                        case "test":
                            cmd = "cargo test --manifest-path " + fileOrDir;
                            description = "Rust 单元测试";
                            break;
                        case "check":
                            cmd = "cargo clippy --manifest-path " + fileOrDir + " -- -W clippy::all";
                            description = "Rust 代码分析 (Clippy)";
                            break;
                        default:
                            return "错误：未知的验证类型 - " + verifyType;
                    }
                    break;

                case "cpp":
                case "c++":
                case "c":
                    switch (verifyType.toLowerCase()) {
                        case "compile":
                            cmd = "gcc -c " + fileOrDir + " -o /dev/null";
                            description = "C/C++ 编译检查";
                            break;
                        case "test":
                            // 假设有 CMake 配置
                            cmd = "cd " + fileOrDir + " && cmake . && make test";
                            description = "C/C++ 单元测试";
                            break;
                        case "check":
                            cmd = "cppcheck " + fileOrDir;
                            description = "C/C++ 代码分析";
                            break;
                        default:
                            return "错误：未知的验证类型 - " + verifyType;
                    }
                    break;

                case "java":
                    switch (verifyType.toLowerCase()) {
                        case "compile":
                            cmd = "mvn clean compile -DskipTests";
                            description = "Java 编译检查";
                            break;
                        case "test":
                            cmd = "mvn test";
                            description = "Java 单元测试";
                            break;
                        case "check":
                            cmd = "mvn spotbugs:check";
                            description = "Java 代码分析 (SpotBugs)";
                            break;
                        default:
                            return "错误：未知的验证类型 - " + verifyType;
                    }
                    break;

                default:
                    return "错误：不支持的语言 - " + language;
            }

            if (cmd == null) {
                return "错误：无法构建验证命令";
            }

            logger.info("执行验证命令: {}", cmd);

            // 执行验证命令
            ProcessBuilder pb = new ProcessBuilder(getShellCommand(cmd));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 设置超时（编译/测试可能需要较长时间）
            boolean completed = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "错误：验证超时（120 秒）- " + description;
            }

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                logger.info("✅ 代码验证通过: {}", description);
                return "✅ 验证通过 - " + description + "\n\n" + result;
            } else {
                logger.warn("❌ 代码验证失败: {}", description);
                return "❌ 验证失败 - " + description + "\n\n" + result;
            }

        } catch (Exception e) {
            return "错误：验证执行失败 - " + e.getMessage();
        }
    }

    /**
     * 检查是否在 Windows 系统
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f%s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    /**
     * 将命令字符串转换为 ProcessBuilder 可接受的命令数组
     * 根据操作系统自动选择 shell 包装方式
     *
     * @param command 要执行的命令字符串
     * @return 适合 ProcessBuilder 的命令数组
     */
    private String[] getShellCommand(String command) {
        if (isWindows()) {
            return new String[]{"cmd", "/c", command};
        } else {
            return new String[]{"/bin/bash", "-c", command};
        }
    }

    /**
     * Get AI Memory Manager for storing/retrieving memories
     */
    public AIMemoryManager getAIMemoryManager() {
        return aiMemoryManager;
    }

    /**
     * 获取 MCP 客户端管理器
     */
    public MCPClientManager getMCPClientManager() {
        return mcpClientManager;
    }

    /**
     * Role configuration
     */
    private static class RoleConfig {
        final String providerName;
        final String model;

        RoleConfig(String providerName, String model) {
            this.providerName = providerName;
            this.model = model;
        }
    }

    /**
     * Builder for creating configured orchestrator
     */
    public static class Builder {
        private final ProviderFactory providerFactory;
        private final RoleFactory roleFactory;
        private final Map<String, RoleConfig> configs = new HashMap<>();

        public Builder(ProviderFactory providerFactory, RoleFactory roleFactory) {
            this.providerFactory = providerFactory;
            this.roleFactory = roleFactory;
        }

        public Builder configureRole(String roleName, String providerName, String model) {
            configs.put(roleName, new RoleConfig(providerName, model));
            return this;
        }

        public LLMOrchestrator build() {
            LLMOrchestrator orchestrator = new LLMOrchestrator(providerFactory, roleFactory);
            configs.forEach((role, config) ->
                orchestrator.configureRole(role, config.providerName, config.model)
            );
            return orchestrator;
        }
    }

    public static Builder builder(ProviderFactory providerFactory, RoleFactory roleFactory) {
        return new Builder(providerFactory, roleFactory);
    }

    /**
     * 工具执行确认回调接口
     * 用于在执行有副作用的工具前获取用户确认
     */
    @FunctionalInterface
    public interface ToolConfirmationCallback {
        /**
         * 请求用户确认是否执行某个工具调用
         * @param toolName 工具名称
         * @param message 确认提示消息
         * @return true 如果用户确认，false 如果用户拒绝或超时
         */
        boolean requestConfirmation(String toolName, String message);
    }
}
