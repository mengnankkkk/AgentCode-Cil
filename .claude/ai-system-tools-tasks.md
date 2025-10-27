# AI 系统工具能力实现待办清单

**创建时间**：2025-10-27 14:45:00
**总工作量**：11 天（176 小时）
**优先级**：高（影响 AI 的能力和效率）

---

## 优先级 1: FileTools 基础实现（2 天）

### Phase 1.1: FileTools 架构和读取操作（1 天）

- [ ] **Task 1.1.1** 创建 FileTools.java 基础结构
  - [ ] FileTools 类定义
  - [ ] 日志和错误处理框架
  - [ ] 文件编码检测和处理
  - [ ] 估算工作量：2小时

- [ ] **Task 1.1.2** 实现文件读取操作
  - [ ] readFile(String filePath) - 完整读取
  - [ ] readFile(String filePath, int maxLines) - 限制行数读取
  - [ ] readFileWithLineNumbers(String filePath) - 带行号读取
  - [ ] readFileRange(String filePath, int startLine, int endLine) - 范围读取
  - [ ] 处理大文件、编码问题
  - [ ] 估算工作量：3小时

- [ ] **Task 1.1.3** 创建 FileReadResult 结果类
  - [ ] 包含内容、行数、编码、时间戳
  - [ ] 提供方便的访问器
  - [ ] 估算工作量：1小时

- [ ] **Task 1.1.4** 单元测试
  - [ ] 测试正常读取
  - [ ] 测试文件不存在
  - [ ] 测试大文件和编码
  - [ ] 估算工作量：2小时

### Phase 1.2: FileTools 写入和搜索操作（1 天）

- [ ] **Task 1.2.1** 实现文件写入操作
  - [ ] writeFile(String filePath, String content) - 完整覆盖
  - [ ] writeFile(..., boolean backup) - 备份选项
  - [ ] appendFile(String filePath, String content) - 追加
  - [ ] updateFileBlock(String filePath, String oldText, String newText) - 块更新
  - [ ] 估算工作量：2.5小时

- [ ] **Task 1.2.2** 实现文件搜索操作
  - [ ] searchFiles(String pattern, String directory) - 单层搜索
  - [ ] searchFilesRecursive(String pattern, String directory) - 递归搜索
  - [ ] listDirectory(String path) - 列出目录
  - [ ] findFilesMatching(String pattern, String rootPath) - 模式匹配
  - [ ] 估算工作量：2.5小时

- [ ] **Task 1.2.3** 实现内容搜索
  - [ ] searchContent(String keyword, String directory) - 目录内容搜索
  - [ ] searchContentInFile(String keyword, String filePath) - 单文件搜索
  - [ ] 支持正则表达式
  - [ ] 返回匹配行和上下文
  - [ ] 估算工作量：2小时

- [ ] **Task 1.2.4** 创建结果类
  - [ ] FileSearchResult
  - [ ] ContentSearchResult
  - [ ] DirectoryListResult
  - [ ] 估算工作量：1小时

- [ ] **Task 1.2.5** 集成测试
  - [ ] 写入和验证
  - [ ] 搜索准确性
  - [ ] 大批量文件搜索
  - [ ] 估算工作量：2小时

---

## 优先级 2: 工具定义和注册系统（1 天）

### Phase 2.1: 工具定义框架

- [ ] **Task 2.1.1** 创建 ToolDefinition 和相关类
  - [ ] ToolDefinition 类
  - [ ] ToolParameterSchema 类
  - [ ] ToolParameter 类
  - [ ] ToolType 枚举
  - [ ] 估算工作量：2小时

- [ ] **Task 2.1.2** 创建 ToolRegistry
  - [ ] ToolRegistry 基础结构
  - [ ] 注册 FileTools 中的所有方法
  - [ ] registerAll() 方法
  - [ ] getToolDefinition() 和 listAllTools()
  - [ ] 估算工作量：2小时

- [ ] **Task 2.1.3** 实现工具执行
  - [ ] executeTool(String toolName, Map<String, Object> parameters)
  - [ ] 参数验证
  - [ ] 动态执行
  - [ ] ToolExecutionResult 类
  - [ ] 估算工作量：2小时

- [ ] **Task 2.1.4** 测试
  - [ ] 工具定义加载
  - [ ] 工具执行准确性
  - [ ] 参数验证
  - [ ] 估算工作量：2小时

---

## 优先级 3: AI 记忆系统（1.5 天）

### Phase 3.1: AIMemoryStore 实现

- [ ] **Task 3.1.1** 创建 Memory 对象和 AIMemoryStore
  - [ ] Memory 类（key、content、type、timestamp、accessCount、summary）
  - [ ] AIMemoryStore 类结构
  - [ ] MemoryType 和 StorageLevel 枚举
  - [ ] 估算工作量：1.5小时

- [ ] **Task 3.1.2** 实现核心功能
  - [ ] storeMemory() - 存储
  - [ ] retrieveMemory() - 检索
  - [ ] searchMemory() - 关键词搜索
  - [ ] clearOldMemory() - 清理
  - [ ] 估算工作量：2.5小时

- [ ] **Task 3.1.3** 实现摘要和压缩
  - [ ] summarizeAndCompress() - 内容摘要
  - [ ] 支持不同内容类型的摘要
  - [ ] 压缩策略配置
  - [ ] 估算工作量：2小时

- [ ] **Task 3.1.4** 持久化存储
  - [ ] 集成 PersistentCacheManager
  - [ ] Session 级和 Persistent 级存储
  - [ ] 序列化和反序列化
  - [ ] 估算工作量：2小时

- [ ] **Task 3.1.5** 单元和集成测试
  - [ ] 基本存储和检索
  - [ ] 搜索功能
  - [ ] 持久化加载
  - [ ] 摘要压缩
  - [ ] 估算工作量：2小时

### Phase 3.2: AIContextManager 实现

- [ ] **Task 3.2.1** 创建 AIContextManager
  - [ ] buildMemoryContext() - 构建记忆上下文
  - [ ] storeFileInMemory() - 存储文件内容
  - [ ] storeSearchResults() - 存储搜索结果
  - [ ] getRelevantMemory() - 获取相关记忆
  - [ ] 估算工作量：2.5小时

- [ ] **Task 3.2.2** 记忆检索和优先级排序
  - [ ] 实现相关性评分
  - [ ] Token 计数和限制
  - [ ] 自动压缩和截断
  - [ ] 估算工作量：2小时

- [ ] **Task 3.2.3** 与 ConversationContext 集成
  - [ ] 在 ConversationContext 中添加 memoryStore
  - [ ] 修改 buildContextString() 包含记忆
  - [ ] 估算工作量：1小时

- [ ] **Task 3.2.4** 测试
  - [ ] 记忆注入 Prompt
  - [ ] 相关性排序
  - [ ] Token 限制
  - [ ] 估算工作量：1.5小时

---

## 优先级 4: LLM 模型扩展（0.5 天）

- [ ] **Task 4.1** 修改 LLMRequest
  - [ ] 添加 tools 和 toolChoice 字段
  - [ ] 构建器模式支持
  - [ ] 估算工作量：1小时

- [ ] **Task 4.2** 修改 LLMResponse
  - [ ] 添加 ToolCall 类
  - [ ] toolCalls 字段
  - [ ] JSON 解析支持
  - [ ] 估算工作量：1.5小时

- [ ] **Task 4.3** 适配各个 Provider
  - [ ] OpenAI Function Calling 格式
  - [ ] Claude Tool Use 格式
  - [ ] SiliconFlow 兼容
  - [ ] 估算工作量：2小时

- [ ] **Task 4.4** 测试
  - [ ] 工具定义序列化
  - [ ] 工具调用解析
  - [ ] Provider 兼容性
  - [ ] 估算工作量：1小时

---

## 优先级 5: LLMOrchestrator 工具调用处理（1.5 天）

### Phase 5.1: 工具调用识别和执行

- [ ] **Task 5.1.1** 在 LLMOrchestrator 中添加工具处理
  - [ ] 识别响应中的工具调用
  - [ ] handleToolCalls() 方法
  - [ ] ToolRegistry 集成
  - [ ] 估算工作量：2小时

- [ ] **Task 5.1.2** 执行工具并反馈
  - [ ] executeAndCallback() 方法
  - [ ] 构建 tool_result 消息
  - [ ] 重新发送给 LLM
  - [ ] 估算工作量：2小时

- [ ] **Task 5.1.3** 错误处理和重试
  - [ ] 工具执行失败处理
  - [ ] 重试机制
  - [ ] 超时处理
  - [ ] 用户友好的错误信息
  - [ ] 估算工作量：2小时

- [ ] **Task 5.1.4** 集成测试
  - [ ] 模拟工具调用
  - [ ] 完整的调用-执行-反馈流程
  - [ ] 多轮对话
  - [ ] 估算工作量：2小时

---

## 优先级 6: Prompt 注入和记忆集成（1 天）

- [ ] **Task 6.1** 在 Role 中集成记忆
  - [ ] 修改 BaseLLMRole.buildPrompt()
  - [ ] 调用 AIContextManager.getRelevantMemory()
  - [ ] 注入到 System Prompt
  - [ ] 估算工作量：1.5小时

- [ ] **Task 6.2** 每个 Role 的特定优化
  - [ ] AnalyzerRole - 分析相关记忆
  - [ ] PlannerRole - 设计相关记忆
  - [ ] CoderRole - 代码相关记忆
  - [ ] ReviewerRole - 审查相关记忆
  - [ ] 估算工作量：2小时

- [ ] **Task 6.3** 测试和验证
  - [ ] Prompt 构建准确性
  - [ ] 记忆正确注入
  - [ ] Token 限制
  - [ ] 性能影响
  - [ ] 估算工作量：1.5小时

---

## 优先级 7: ConsolePrinter（0.5 天）

- [ ] **Task 7.1** 创建 ConsolePrinter
  - [ ] 基础打印方法
  - [ ] 日志级别支持
  - [ ] 颜色和格式化
  - [ ] 估算工作量：1.5小时

- [ ] **Task 7.2** 特殊输出格式
  - [ ] printTable() - 表格
  - [ ] printJson() - JSON
  - [ ] printTree() - 树形结构
  - [ ] 估算工作量：1.5小时

- [ ] **Task 7.3** AI 特定输出
  - [ ] printAIThinking() - AI 思考过程
  - [ ] printAIToolCall() - 工具调用
  - [ ] printAIResponse() - AI 响应
  - [ ] 估算工作量：1小时

- [ ] **Task 7.4** 集成和测试
  - [ ] 集成到 LLMOrchestrator
  - [ ] 集成到 ToolRegistry
  - [ ] 视觉效果测试
  - [ ] 估算工作量：1小时

---

## 优先级 8: 单元和集成测试（2 天）

- [ ] **Task 8.1** FileTools 完整测试
  - [ ] 路径：src/test/java/com/harmony/agent/tools/FileToolsTest.java
  - [ ] 测试覆盖 > 90%
  - [ ] 估算工作量：3小时

- [ ] **Task 8.2** ToolRegistry 和定义测试
  - [ ] 路径：src/test/java/com/harmony/agent/tools/ToolRegistryTest.java
  - [ ] 所有工具的执行验证
  - [ ] 估算工作量：2小时

- [ ] **Task 8.3** AIMemoryStore 测试
  - [ ] 路径：src/test/java/com/harmony/agent/memory/AIMemoryStoreTest.java
  - [ ] 存储、检索、搜索、压缩
  - [ ] 估算工作量：2.5小时

- [ ] **Task 8.4** LLMOrchestrator 工具调用测试
  - [ ] 路径：src/test/java/com/harmony/agent/llm/orchestrator/ToolCallHandlerTest.java
  - [ ] 完整的工具调用流程
  - [ ] 估算工作量：2小时

- [ ] **Task 8.5** 端到端集成测试
  - [ ] 路径：src/test/java/com/harmony/agent/test/integration/ToolIntegrationTest.java
  - [ ] 完整的 AI-Tool-Memory 流程
  - [ ] 多轮对话
  - [ ] 估算工作量：3小时

- [ ] **Task 8.6** 性能和压力测试
  - [ ] 大文件读取性能
  - [ ] 记忆系统在大规模数据下的性能
  - [ ] 并发工具调用
  - [ ] 估算工作量：2小时

---

## 优先级 9: 文档和示例（1 天）

- [ ] **Task 9.1** API 文档
  - [ ] FileTools API 文档
  - [ ] ToolRegistry 和 ToolDefinition 文档
  - [ ] AIMemoryStore 文档
  - [ ] 估算工作量：2小时

- [ ] **Task 9.2** 开发者指南
  - [ ] 如何添加新工具
  - [ ] 如何在 Role 中使用记忆
  - [ ] 工具调用的最佳实践
  - [ ] 估算工作量：1.5小时

- [ ] **Task 9.3** 使用示例
  - [ ] 示例 1：AI 读取和分析文件
  - [ ] 示例 2：AI 搜索代码并提出建议
  - [ ] 示例 3：AI 通过工具完成复杂任务
  - [ ] 路径：docs/examples/
  - [ ] 估算工作量：1.5小时

- [ ] **Task 9.4** 配置文档和故障排除
  - [ ] application.yml 配置说明
  - [ ] 常见问题排查
  - [ ] 性能优化建议
  - [ ] 估算工作量：1小时

---

## 实现顺序和依赖关系

```
Phase 1 (FileTools, 2天)
    ↓
Phase 2 (ToolRegistry, 1天)
    ↓
Phase 3 (AIMemory, 1.5天)  ← 可与 Phase 4 并行
Phase 4 (LLMModel, 0.5天)  ← 可与 Phase 3 并行
    ↓
Phase 5 (LLMOrchestrator, 1.5天)
    ↓
Phase 6 (Prompt注入, 1天)
    ↓
Phase 7 (ConsolePrinter, 0.5天)
    ↓
Phase 8 (测试, 2天)
    ↓
Phase 9 (文档, 1天)
```

**可并行化的任务**
- Phase 3 和 4 可以并行开发（1.5 天 + 0.5 天 = 1.5 天）
- Phase 8 可以在 Phase 5-7 过程中持续进行（降低总工时）
- 写文档可以在实现过程中进行

**优化后总工期**：8-9 天（而非 11 天）

---

## 进度追踪模板

### 每日报告

```
【日期】2025-10-27
【今日完成】
- [ ] Task 1.1.1 FileTools 基础结构
- [ ] Task 1.1.2 文件读取实现
  ✅ readFile() 完成
  ✅ readFileWithLineNumbers() 完成
  🔄 readFileRange() 进行中 (60%)

【遇到的问题】
- 文件编码检测需要使用第三方库还是自己实现？
  → 决议：使用 Java 标准 CharsetDetector

【明日计划】
- [ ] 完成 readFileRange()
- [ ] 实现 FileReadResult
- [ ] 单元测试
```

---

## 质量标准

- **代码覆盖率**：>80%
- **文档完整度**：API 文档、开发者指南、示例
- **性能基准**：
  - 文件读取 <100ms（<1MB）
  - 文件搜索 <1s（100 文件、1MB 平均大小）
  - 记忆检索 <50ms
- **编译和测试**：无错误、无警告
- **代码风格**：遵循项目约定

---

## 交付清单

- [x] ai-system-tools-plan.md (本文档)
- [ ] FileTools.java 及相关类
- [ ] ToolRegistry 和 ToolDefinition
- [ ] AIMemoryStore 和 AIContextManager
- [ ] LLMRequest/Response 扩展
- [ ] LLMOrchestrator 工具调用处理
- [ ] Role 中的记忆注入
- [ ] ConsolePrinter
- [ ] 单元测试 (>80% 覆盖)
- [ ] 集成测试
- [ ] API 文档
- [ ] 开发者指南
- [ ] 使用示例

---

**开始时间**：TBD
**完成目标**：TBD
**当前状态**：Planning

下一步：开始 Phase 1 的实现（FileTools）

