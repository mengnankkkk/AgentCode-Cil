# AI 系统工具能力规划和实现方案

**文档生成时间**：2025-10-27 14:35:00
**作者**：Claude Code
**状态**：PLANNING - 待实现

---

## 概述

本文档规划 HarmonySafeAgent 项目中 AI 系统（LLM）的工具能力扩展，包括：
1. 文件操作工具（读写、搜索、过滤）
2. 系统工具集成（编译、测试、执行）
3. AI 记忆缓存机制（知识库、上下文管理）
4. 工具调用接口（Function Calling）

---

## 1. 现状分析

### 1.1 已有工具系统

**ToolExecutor 类** (`src/main/java/com/harmony/agent/tools/ToolExecutor.java`)
- ✅ Maven 编译支持
- ✅ JUnit 测试支持
- ✅ SpotBugs 静态分析
- ✅ C++ 编译和测试
- ✅ Rust 编译和测试
- ✅ Bash/Shell 脚本执行

**工具结果类型**
- `CompileResult.java` - 编译结果
- `TestResult.java` - 测试结果
- `AnalysisResult.java` - 分析结果
- `ScriptResult.java` - 脚本执行结果

### 1.2 已有 LLM 系统

**LLM 模型**
- `LLMRequest.java` - LLM 请求（包含消息、模型、参数）
- `LLMResponse.java` - LLM 响应
- `Message.java` - 消息模型
- 多个 Provider：OpenAI、Claude、SiliconFlow、NHH 等

**角色系统**
- `BaseLLMRole.java` - 基类
- `AnalyzerRole.java` - 分析器
- `PlannerRole.java` - 规划器
- `CoderRole.java` - 编码者
- `ReviewerRole.java` - 审查者

**上下文管理**
- `ConversationContext.java` - 对话上下文（管理需求、设计文档、生成代码、审查注释）

### 1.3 已有缓存系统

- `PersistentCacheManager.java` - 持久化缓存管理
- `CachedLLMProvider.java` - LLM 缓存提供者
- `CachedAiValidationClient.java` - AI 验证缓存

### 1.4 缺失的能力

**❌ 文件操作工具**
- 文件读取
- 文件写入
- 文件搜索和过滤
- 关键词搜索
- 文件列表和目录浏览

**❌ AI 工具调用接口**
- Function Calling 定义
- 工具声明和参数定义
- 工具选择和执行

**❌ AI 记忆缓存机制**
- 知识库存储（读文件后放入记忆）
- 关键信息提取和摘要
- 长期记忆vs短期记忆
- 记忆检索和相关性匹配

**❌ 控制台输出工具**
- 结构化输出
- 日志管理
- 错误处理和报告

---

## 2. 详细实现规划

### 2.1 文件操作工具模块（FileTools）

**新文件**：`src/main/java/com/harmony/agent/tools/FileTools.java`

```java
// 核心能力
public class FileTools {
    // 文件读取
    public FileReadResult readFile(String filePath, int maxLines = -1)
    public FileReadResult readFileWithLineNumbers(String filePath)
    public FileReadResult readFileRange(String filePath, int startLine, int endLine)

    // 文件写入
    public FileWriteResult writeFile(String filePath, String content, boolean backup = true)
    public FileWriteResult appendFile(String filePath, String content)
    public FileWriteResult updateFileBlock(String filePath, String oldText, String newText)

    // 文件搜索
    public FileSearchResult searchFiles(String pattern, String directory)
    public FileSearchResult searchFilesRecursive(String pattern, String directory)

    // 内容搜索
    public ContentSearchResult searchContent(String keyword, String directory, boolean regex = false)
    public ContentSearchResult searchContentInFile(String keyword, String filePath, boolean regex = false)

    // 目录操作
    public DirectoryListResult listDirectory(String path)
    public DirectoryListResult findFilesMatching(String pattern, String rootPath)

    // 文件过滤
    public FilterResult filterFiles(List<String> files, String type, String keyword)
}
```

**返回类型**
- `FileReadResult` - 包含：文件内容、行数、编码、时间戳
- `FileWriteResult` - 包含：是否成功、原因、备份路径
- `FileSearchResult` - 包含：匹配的文件列表、总数
- `ContentSearchResult` - 包含：匹配行、行号、上下文
- `DirectoryListResult` - 包含：文件列表、目录结构

### 2.2 AI 工具调用接口（ToolCallInterface）

**新文件**：`src/main/java/com/harmony/agent/tools/ToolDefinition.java`

```java
// 工具定义类
public class ToolDefinition {
    private String name;                    // 工具名称，如 "read_file"
    private String description;             // 工具描述
    private ToolParameterSchema inputSchema; // 参数定义
    private List<String> tags;             // 标签

    // 工具类型：FILE_READ, FILE_WRITE, FILE_SEARCH, COMPILE, TEST, ANALYZE
    public enum ToolType {
        FILE_READ, FILE_WRITE, FILE_SEARCH,
        COMPILE, TEST, ANALYZE,
        EXECUTE, SYSTEM
    }
}

// 参数定义
public class ToolParameterSchema {
    private String type;                   // "object"
    private Map<String, ToolParameter> properties;
    private List<String> required;
}

public class ToolParameter {
    private String type;                   // "string", "number", "array", etc.
    private String description;
    private Object defaultValue;
    private List<String> enum;             // 枚举值（可选）
}
```

**新文件**：`src/main/java/com/harmony/agent/tools/ToolRegistry.java`

```java
// 工具注册表
public class ToolRegistry {
    // 注册所有可用工具
    public void registerAll()

    // 获取工具定义
    public ToolDefinition getToolDefinition(String toolName)

    // 执行工具
    public ToolExecutionResult executeTool(String toolName, Map<String, Object> parameters)

    // 列出所有可用工具
    public List<ToolDefinition> listAllTools()

    // 获取特定类型的工具
    public List<ToolDefinition> getToolsByType(ToolType type)
}
```

### 2.3 AI 记忆缓存机制（AIMemorySystem）

**新文件**：`src/main/java/com/harmony/agent/memory/AIMemoryStore.java`

```java
// AI 记忆存储
public class AIMemoryStore {
    // 存储级别：SESSION（本次会话）、PERSISTENT（持久化）
    public enum StorageLevel {
        SESSION, PERSISTENT
    }

    // 记忆类型
    public enum MemoryType {
        FILE_CONTENT,           // 文件内容
        SEARCH_RESULT,          // 搜索结果
        CODE_SNIPPET,           // 代码片段
        DESIGN_PATTERN,         // 设计模式
        ERROR_ANALYSIS,         // 错误分析
        DECISION,               // 决策记录
        KNOWLEDGE_BASE          // 知识库条目
    }

    // 核心方法
    public void storeMemory(String key, String content, MemoryType type, StorageLevel level)
    public Memory retrieveMemory(String key)
    public List<Memory> searchMemory(String keyword, MemoryType type)
    public void summarizeAndCompress(String fileContent) // 文件内容摘要压缩
    public void clearOldMemory(long ageInMinutes)

    // 记忆对象
    public class Memory {
        private String key;
        private String content;
        private MemoryType type;
        private long timestamp;
        private int accessCount;
        private String summary;      // 摘要（用于长内容）
    }
}
```

**新文件**：`src/main/java/com/harmony/agent/memory/AIContextManager.java`

```java
// AI 上下文管理
public class AIContextManager {
    // 将记忆信息注入到 LLM 请求
    public String buildMemoryContext(String currentTask)

    // 从文件读取后自动存储
    public void storeFileInMemory(FileReadResult fileResult)

    // 从搜索结果中提取关键信息
    public void storeSearchResults(ContentSearchResult searchResult)

    // 获取相关记忆（用于 Prompt 注入）
    public String getRelevantMemory(String query, int maxTokens = 2000)
}
```

### 2.4 LLM 集成支持

**修改文件**：`src/main/java/com/harmony/agent/llm/model/LLMRequest.java`

```java
// 添加工具定义支持
public class LLMRequest {
    private final List<ToolDefinition> tools;  // NEW
    private final ToolChoice toolChoice;       // NEW

    // tool_choice: "auto" | "none" | {"type": "function", "function": {"name": "..."}}
    public enum ToolChoice {
        AUTO, NONE, REQUIRED
    }
}
```

**修改文件**：`src/main/java/com/harmony/agent/llm/model/LLMResponse.java`

```java
// 添加工具调用支持
public class LLMResponse {
    private List<ToolCall> toolCalls;  // NEW

    public class ToolCall {
        private String id;
        private String toolName;
        private String arguments;  // JSON string
        private Map<String, Object> parsedArguments;
    }
}
```

**修改文件**：`src/main/java/com/harmony/agent/llm/orchestrator/LLMOrchestrator.java`

```java
// 添加工具调用处理
public class LLMOrchestrator {
    // 处理工具调用的响应
    public void handleToolCalls(LLMResponse response, ConversationContext context)

    // 执行工具并返回结果给 LLM
    public void executeAndCallback(ToolCall call, ConversationContext context)
}
```

### 2.5 控制台输出工具

**新文件**：`src/main/java/com/harmony/agent/tools/ConsolePrinter.java`

```java
// 结构化输出
public class ConsolePrinter {
    // 基础输出
    public void log(String message)
    public void info(String message)
    public void warn(String message)
    public void error(String message)
    public void success(String message)

    // 格式化输出
    public void printTable(String[][] data, String[] headers)
    public void printJson(Object obj)
    public void printTree(String root, List<String> children)

    // 进度输出
    public void printProgress(String task, int current, int total)
    public void printBenchmark(String operation, long durationMs)

    // AI 输出特殊处理
    public void printAIThinking(String thought)
    public void printAIToolCall(String toolName, String arguments)
    public void printAIResponse(String response)
}
```

---

## 3. 工具集成流程

### 3.1 AI 调用文件工具的流程

```
1. AI 生成 Prompt，需要读取文件
   ↓
2. AI 通过 Function Calling 调用 read_file 工具
   LLMResponse.toolCalls = [
     {toolName: "read_file", arguments: {filePath: "src/main.java"}}
   ]
   ↓
3. System 执行工具
   result = FileTools.readFile("src/main.java")
   ↓
4. System 存储到 AI 记忆
   AIMemoryStore.storeMemory(fileContent, MEMORY_TYPE.FILE_CONTENT)
   ↓
5. System 发送结果给 AI
   发送 tool_result 消息，包含文件内容
   ↓
6. AI 基于文件内容继续推理
   生成下一个 response
```

### 3.2 记忆注入到 Prompt 的流程

```
1. AI 执行任务
   ↓
2. 获取相关记忆
   relevantMemory = AIContextManager.getRelevantMemory(currentTask)
   ↓
3. 构建 System Prompt
   systemPrompt = """
   你是安全代码分析专家...

   [相关背景信息]
   """ + relevantMemory
   ↓
4. 发送给 LLM
   request.addSystemMessage(systemPrompt)
   ↓
5. LLM 基于记忆和背景信息做出更好的决策
```

---

## 4. 实现优先级和工作量估计

| 优先级 | 模块 | 工作量 | 依赖 |
|--------|------|--------|------|
| 1 | FileTools 基础（读写搜索） | 2天 | 无 |
| 2 | ToolDefinition 和 ToolRegistry | 1天 | FileTools |
| 3 | AIMemoryStore 基础 | 1.5天 | 无 |
| 4 | LLMRequest/Response 扩展 | 0.5天 | 无 |
| 5 | ToolCallInterface 在 LLMOrchestrator 中 | 1.5天 | 1-4 |
| 6 | AIContextManager 与 Prompt 注入 | 1天 | 3,5 |
| 7 | ConsolePrinter | 0.5天 | 无 |
| 8 | 单元测试和集成测试 | 2天 | 1-7 |
| 9 | 文档和示例 | 1天 | 1-7 |

**总估计**：**11 天**（包含所有测试和文档）

---

## 5. 实现步骤详解

### Step 1: 实现 FileTools（2天）

```
第1天：
- FileTools 基础结构
  - readFile() / readFileWithLineNumbers()
  - readFileRange()
  - 处理编码、行号、截断等

第2天：
- FileTools 写入操作
  - writeFile() / appendFile()
  - updateFileBlock()
  - 文件搜索和目录操作
  - 内容搜索和过滤
```

**验收标准**
- ✅ 可以读取任意文件（含行号、编码处理）
- ✅ 可以搜索文件名和内容
- ✅ 可以写入、更新文件
- ✅ 错误处理完善（文件不存在、权限等）

### Step 2: 工具定义和注册表（1天）

```
- ToolDefinition 类
- ToolRegistry 类，包含所有工具定义
- 注册 FileTools 中的所有方法
- 支持动态工具执行
```

**验收标准**
- ✅ 所有 FileTools 方法都有 ToolDefinition
- ✅ ToolRegistry.listAllTools() 可以列出所有工具
- ✅ ToolRegistry.executeTool() 可以动态执行工具

### Step 3: AI 记忆系统（1.5天）

```
第1天：
- AIMemoryStore 基础
  - Memory 对象
  - 存储和检索
  - Session 级别缓存

第0.5天：
- 持久化集成
- 摘要和压缩
- 清理过期内存
```

**验收标准**
- ✅ 可以存储和检索记忆
- ✅ 支持关键词搜索
- ✅ 自动摘要压缩长内容
- ✅ 持久化存储和加载

### Step 4: LLM 请求/响应扩展（0.5天）

```
- 在 LLMRequest 中添加 tools 和 toolChoice
- 在 LLMResponse 中添加 toolCalls
- 适配各个 Provider（OpenAI、Claude 等）
```

**验收标准**
- ✅ LLMRequest 可以声明工具
- ✅ LLMResponse 可以解析工具调用
- ✅ 适配 OpenAI 和 Claude Function Calling 格式

### Step 5: LLMOrchestrator 工具调用处理（1.5天）

```
第1天：
- 识别工具调用
- 执行工具并获得结果
- 构建 tool_result 消息

第0.5天：
- 重新发送给 LLM
- 处理工具调用失败
- 超时和重试机制
```

**验收标准**
- ✅ 识别和执行工具调用
- ✅ 工具结果正确反馈给 LLM
- ✅ AI 可以基于工具结果继续对话

### Step 6: AIContextManager 和 Prompt 注入（1天）

```
- getRelevantMemory() 实现
- 记忆压缩和优先级排序
- 集成到 Role 的 buildPrompt()
- 动态注入记忆到 System Prompt
```

**验收标准**
- ✅ 可以检索相关记忆
- ✅ 记忆正确注入到 Prompt
- ✅ Token 限制内不超过阈值

### Step 7: ConsolePrinter（0.5天）

```
- 基础打印方法
- 格式化和颜色支持
- AI 特定的输出格式
```

### Step 8-9: 测试和文档（3天）

---

## 6. 与现有系统的集成点

### 6.1 与 ConversationContext 的集成

```java
// 在 ConversationContext 中添加
private AIMemoryStore memoryStore;
private Map<String, String> fileCache;

public void cacheFileContent(String filePath, String content)
public String getCachedFile(String filePath)
```

### 6.2 与 BaseLLMRole 的集成

```java
// 修改 buildPrompt() 方法
public String buildPrompt(ConversationContext context) {
    // 1. 获取任务需求
    String requirement = context.getRequirement();

    // 2. 获取相关记忆
    String memory = aiContextManager.getRelevantMemory(requirement);

    // 3. 构建 System Prompt
    String systemPrompt = buildSystemPrompt() + "\n" + memory;

    // 4. 返回 Prompt
    return systemPrompt;
}
```

### 6.3 与 ToolExecutor 的整合

```java
// ToolRegistry 中使用 ToolExecutor
public ToolExecutionResult executeTool(String toolName, Map<String, Object> params) {
    switch(toolName) {
        case "read_file":
            return fileTools.readFile((String) params.get("filePath"));
        case "compile_java":
            return toolExecutor.compileMaven(true);
        case "run_tests":
            return toolExecutor.runTests((String) params.get("testPattern"));
        ...
    }
}
```

---

## 7. 使用示例

### 7.1 AI 读取文件示例

```
用户：分析 src/main/java/com/harmony/agent/tools/ToolExecutor.java

AI 思考：
- 需要读取文件
- 调用 read_file 工具

AI 调用：
tool_call: {
  name: "read_file",
  arguments: {
    filePath: "src/main/java/com/harmony/agent/tools/ToolExecutor.java",
    maxLines: 100
  }
}

System 执行：
result = FileTools.readFile("src/main/java/com/harmony/agent/tools/ToolExecutor.java", 100)
AIMemoryStore.storeMemory("ToolExecutor.java", result.content, FILE_CONTENT)

System 反馈：
tool_result: {
  success: true,
  content: "package com.harmony.agent.tools;\n...",
  lineCount: 100,
  totalLines: 845
}

AI 分析文件并回答...
```

### 7.2 搜索关键词示例

```
用户：在项目中搜索所有 "TODO" 注释

AI 调用：
tool_call: {
  name: "search_content",
  arguments: {
    keyword: "TODO",
    directory: "src/main/java",
    regex: false
  }
}

System 执行：
results = FileTools.searchContent("TODO", "src/main/java")
AIMemoryStore.storeMemory("search_TODO_results", results, SEARCH_RESULT)

System 反馈：
tool_result: {
  matches: [
    {filePath: "ToolExecutor.java", lineNumber: 42, content: "// TODO: implement error handling"},
    {filePath: "LLMOrchestrator.java", lineNumber: 156, content: "// TODO: add memory management"}
  ],
  totalMatches: 42
}

AI 总结搜索结果...
```

### 7.3 写入文件示例

```
用户：在 README.md 中添加一个新的章节

AI 读取现有文件 → 分析结构 → 生成新内容 → 调用写入工具

AI 调用：
tool_call: {
  name: "update_file_block",
  arguments: {
    filePath: "README.md",
    oldText: "## Contributing\n...",
    newText: "## Contributing\n...\n## New Section\n..."
  }
}

System 执行 → 返回成功信息
```

---

## 8. 风险和缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Tool 调用失败 | AI 无法继续 | 失败重试、降级处理、用户提示 |
| 文件权限问题 | 无法读写文件 | 权限检查、友好错误信息 |
| 内存溢出（大文件） | 系统崩溃 | 分块读取、自动摘要压缩 |
| Token 超限 | LLM 调用失败 | 动态优先级排序、记忆压缩 |
| AI 误用工具 | 无限循环或破坏 | 工具参数验证、执行次数限制 |
| 记忆不一致 | AI 决策错误 | 定期清理、冲突检测 |

---

## 9. 配置和环境要求

### 9.1 新增依赖

无特殊外部依赖（使用 Java 标准库）

### 9.2 配置文件

**application.yml** 新增：

```yaml
ai-tools:
  file-tools:
    max-file-size: 10MB          # 单个文件最大读取大小
    default-encoding: UTF-8       # 默认文件编码

  memory:
    store-type: PERSISTENT       # SESSION or PERSISTENT
    max-memory-items: 1000
    memory-ttl-hours: 24
    compression-enabled: true

  tool-execution:
    timeout-seconds: 60
    max-retries: 3

  prompt-injection:
    max-memory-tokens: 2000      # 注入到 prompt 的最大记忆 token 数
    enable-auto-compression: true
```

---

## 10. 验收标准清单

### 功能验收

- [ ] FileTools 实现完整（读写搜索过滤）
- [ ] ToolDefinition 和 ToolRegistry 完整
- [ ] AIMemoryStore 可以存储和检索记忆
- [ ] LLMRequest/Response 支持工具定义和调用
- [ ] LLMOrchestrator 可以识别和执行工具调用
- [ ] AI 可以通过工具读取文件
- [ ] 文件内容自动存储到记忆
- [ ] 记忆可以注入到 Prompt
- [ ] ConsolePrinter 格式化输出完整

### 非功能验收

- [ ] 单元测试覆盖 > 80%
- [ ] 集成测试通过
- [ ] 性能：文件读取 < 100ms（<1MB 文件）
- [ ] 内存：记忆存储 < 500MB
- [ ] 文档完整（API、使用示例、配置）
- [ ] 编译无错误和警告

---

## 11. 后续扩展方向

1. **向量数据库集成**：使用 Pinecone/Weaviate 存储向量化的记忆，实现语义搜索
2. **知识图谱**：构建代码和设计的知识图谱，实现关系查询
3. **多模态支持**：支持图像、图表的分析和理解
4. **外部工具集成**：集成 GitHub、JIRA、Slack 等外部服务
5. **性能监测**：添加工具执行性能监控和优化建议
6. **工具权限管理**：细粒度的工具调用权限控制

---

## 附录 A: 文件结构总览

```
src/main/java/com/harmony/agent/
├── tools/
│   ├── ToolExecutor.java (已有)
│   ├── FileTools.java (NEW)
│   ├── ToolDefinition.java (NEW)
│   ├── ToolRegistry.java (NEW)
│   ├── ConsolePrinter.java (NEW)
│   ├── ToolExecutionResult.java (NEW)
│   └── result/
│       ├── CompileResult.java (已有)
│       ├── TestResult.java (已有)
│       ├── AnalysisResult.java (已有)
│       ├── ScriptResult.java (已有)
│       ├── FileReadResult.java (NEW)
│       ├── FileWriteResult.java (NEW)
│       ├── FileSearchResult.java (NEW)
│       └── ContentSearchResult.java (NEW)
│
├── memory/
│   ├── AIMemoryStore.java (NEW)
│   ├── AIContextManager.java (NEW)
│   ├── Memory.java (NEW)
│   └── MemoryConfig.java (NEW)
│
├── llm/
│   ├── model/
│   │   ├── LLMRequest.java (修改)
│   │   ├── LLMResponse.java (修改)
│   │   ├── ToolDefinition.java (NEW)
│   │   ├── ToolCall.java (NEW)
│   │   └── ToolParameterSchema.java (NEW)
│   │
│   └── orchestrator/
│       └── LLMOrchestrator.java (修改)

test/java/com/harmony/agent/
├── tools/
│   ├── FileToolsTest.java (NEW)
│   ├── ToolRegistryTest.java (NEW)
│   └── ConsolePrinterTest.java (NEW)
│
└── memory/
    ├── AIMemoryStoreTest.java (NEW)
    └── AIContextManagerTest.java (NEW)
```

---

**下一步**：实现优先级 1 的 FileTools 模块。预计完成时间 2 天。

