# 快速参考指南 - AI 系统工具能力实现

**最后更新**：2025-10-27 14:55:00

---

## 📌 核心问题回顾

**用户的问题**：
> "现在能不能去实现文件的读写/搜索/关键词的搜索/过滤/文件的更新和新建新的文件/打印到控制台/等等系统工具的能力，不能的话就去列入待办去实现！并且确保我的AI可以调用这些工具，比如读文件，可以读完之后放入我们AI的记忆缓存上下文之中，等等！"

**回答**：
- 🟥 **现在不能**：系统中没有这些工具和能力
- ✅ **已列入待办**：详细规划和任务清单已生成，准备开始实现

---

## 🎯 核心答案

### 问题 1：现在能做什么？

**现有能力**：
- ✅ Maven 编译、JUnit 测试（Java 项目）
- ✅ C++ 编译和测试
- ✅ Rust 编译和测试
- ✅ Bash 脚本执行

**不能做**：
- ❌ 读取文件并自动存入记忆
- ❌ 搜索代码和关键词
- ❌ AI 通过工具调用读写文件
- ❌ 用记忆信息增强 AI 的决策

### 问题 2：规划了什么？

**9 个优先级阶段**：

1. **FileTools** (2 天) - 文件读写搜索工具
2. **ToolRegistry** (1 天) - 工具定义和注册
3. **AIMemoryStore** (1.5 天) - 记忆存储系统
4. **LLM 扩展** (0.5 天) - Function Calling 支持
5. **工具调用处理** (1.5 天) - LLMOrchestrator 集成
6. **Prompt 注入** (1 天) - 记忆自动注入
7. **ConsolePrinter** (0.5 天) - 格式化输出
8. **测试** (2 天) - 单元和集成测试
9. **文档** (1 天) - API 文档和示例

**总工作量**：8-9 天

### 问题 3：如何使用这些工具？

**AI 读取文件的完整流程**：

```
1️⃣ AI 需要读取文件
   → AI: "我需要读取 src/main/java/App.java"

2️⃣ AI 调用 read_file 工具
   → AI: { tool: "read_file", path: "src/main/java/App.java" }

3️⃣ System 执行工具
   → FileTools.readFile() 返回文件内容

4️⃣ System 存入记忆
   → AIMemoryStore.storeMemory(fileContent, FILE_CONTENT)

5️⃣ System 反馈给 AI
   → { result: "文件内容: ...", lines: 250 }

6️⃣ AI 分析文件
   → AI: "我看到了文件中的 X 问题，建议..."

7️⃣ 后续任务复用记忆
   → 自动注入：[相关背景信息] App.java 的内容是...
```

---

## 📂 已生成的文档

| 文档 | 描述 | 阅读时间 |
|------|------|---------|
| `ai-system-tools-plan.md` | 详细设计文档：架构、使用示例、集成点 | 20 分钟 |
| `ai-system-tools-tasks.md` | 44 个具体任务的清单和依赖关系 | 15 分钟 |
| `ai-system-tools-summary.md` | 执行总结和决策指南 | 10 分钟 |
| `ai-system-tools-quick-ref.md` | 本文档（快速查询） | 5 分钟 |

**推荐阅读顺序**：
1. 本快速参考 (5 min)
2. 执行总结 (10 min)
3. 详细规划的核心部分 (15 min)
4. 任务清单第 1 优先级 (5 min)

---

## 🚀 立即行动清单

### ✅ 已完成的工作

- [x] 分析现有系统架构
- [x] 识别缺失的工具和能力
- [x] 制定详细实现规划
- [x] 分解成 44 个具体任务
- [x] 估算工作量和时间表
- [x] 生成详细文档和指南

### 📋 待执行的工作

**立即执行**（Today）：

- [ ] 审查并批准规划
  - 路径：`.claude/ai-system-tools-summary.md`

- [ ] 确认实现优先级
  - 是否按照 Phase 1 → 9 的顺序进行？
  - 是否有特殊的工具需要优先实现？

**本周执行**：

- [ ] 启动 Phase 1：FileTools 实现
  - 创建 `FileTools.java`
  - 实现 8 个核心方法
  - 单元测试

**下周执行**：

- [ ] 继续 Phase 2-6：工具系统和 AI 集成

**第 3 周**：

- [ ] 完成 Phase 7-9：测试、文档、上线

---

## 🔧 快速技术参考

### FileTools 的核心方法

```java
// 读取
readFile(String filePath)                              // 完整读取
readFile(String filePath, int maxLines)                // 限制行数
readFileWithLineNumbers(String filePath)               // 带行号
readFileRange(String filePath, int startLine, int end) // 范围读取

// 写入
writeFile(String filePath, String content)             // 完整覆盖
writeFile(String filePath, String content, backup)    // 备份选项
appendFile(String filePath, String content)            // 追加
updateFileBlock(String filePath, oldText, newText)    // 块更新

// 搜索
searchFiles(String pattern, String directory)          // 文件搜索
searchFilesRecursive(String pattern, String dir)       // 递归搜索
searchContent(String keyword, String directory)        // 内容搜索
searchContentInFile(String keyword, String filePath)   // 文件内容搜索
```

### ToolRegistry 的核心方法

```java
// 工具管理
listAllTools()                                  // 列出所有工具
getToolDefinition(String toolName)              // 获取工具定义
getToolsByType(ToolType type)                   // 按类型获取

// 工具执行
executeTool(String toolName, Map params)        // 执行工具
```

### AIMemoryStore 的核心方法

```java
// 记忆操作
storeMemory(String key, String content, MemoryType, StorageLevel)
retrieveMemory(String key)                      // 检索
searchMemory(String keyword, MemoryType type)   // 搜索
summarizeAndCompress(String content)            // 摘要压缩
clearOldMemory(long ageInMinutes)               // 清理
```

---

## 💡 关键设计决策

### 1. 为什么分 9 个优先级？

- **优先级 1-2**：基础工具（2.5 天）- 快速获得核心价值
- **优先级 3-4**：AI 支持（2 天）- 将工具接入 AI
- **优先级 5-6**：AI 集成（2.5 天）- AI 主动使用工具和记忆
- **优先级 7-9**：完整化（3 天）- 测试、文档、上线

**好处**：
- 每 2-3 天可以测试和验证一个完整的功能切片
- 不需要等到所有代码完成才开始测试
- 可以根据测试结果及时调整

### 2. 为什么使用摘要而不是完整存储？

**成本分析**：
- 1 个 1MB 的文件 ≈ 250K tokens
- 存 100 个文件 ≈ 25M tokens ❌ 太贵
- 摘要后 ≈ 5K tokens ❌ 可接受

**摘要策略**：
- 代码文件：保留结构 + 关键函数签名 + 注释
- 文本文件：保留前 20% + 中心思想 + 关键字
- 搜索结果：完整保留（通常已经很短）

### 3. 为什么 Token 限制必须有？

**场景**：
- 用户：分析整个项目（500 个文件）
- AI：想要读取所有文件做分析
- 没有限制：token 超限，LLM 调用失败 ❌

**解决方案**：
- 动态优先级排序：最相关的记忆优先
- 自动压缩：内容长度超过阈值自动摘要
- 用户配置：可配置记忆注入的最大 token 数

---

## 🧪 验收标准预览

### Phase 1 验收标准（FileTools）

```
✅ 功能完整性
  - 8 个方法全部实现和测试通过
  - 支持大文件（>100MB）不溢出
  - 编码自动检测（UTF-8, GBK, etc）

✅ 性能
  - 文件读取：< 100ms（< 1MB）
  - 递归搜索：< 1s（100 个文件）

✅ 错误处理
  - 文件不存在：友好错误信息
  - 权限不足：提示可选的解决方案
  - 编码错误：自动降级处理

✅ 测试覆盖
  - 代码覆盖率 > 90%
  - 所有边界条件都有测试
```

### Phase 5 验收标准（工具调用）

```
✅ AI 可以调用工具
  - LLMResponse 正确解析工具调用
  - ToolRegistry 正确执行工具
  - 结果正确返回给 AI

✅ 完整流程验证
  - AI 请求 → 工具执行 → 结果反馈 → AI 继续
  - 多轮对话中 AI 可以多次调用工具

✅ 错误恢复
  - 工具执行失败时有重试
  - 失败后 AI 可以选择其他方案
```

---

## 🎓 学习资源

### 相关的现有代码

**参考这些文件学习项目风格**：

- `src/main/java/com/harmony/agent/tools/ToolExecutor.java` (800+ 行)
  → 学习工具执行的通用模式

- `src/main/java/com/harmony/agent/llm/orchestrator/ConversationContext.java` (160 行)
  → 学习上下文管理的实现

- `src/main/java/com/harmony/agent/core/ai/PersistentCacheManager.java`
  → 学习持久化存储的实现

### OpenAI Function Calling 参考

- [OpenAI Function Calling 文档](https://platform.openai.com/docs/guides/function-calling)
- [Claude Tool Use 文档](https://docs.anthropic.com/en/docs/build-a-bot)
- [JSON Schema 规范](https://json-schema.org/)

---

## ❓ 常见问题

### Q1: 为什么不直接用 LangChain 或 LlamaIndex？

**A**:
- 项目已有完整的 LLM 框架（多个 Provider）
- 添加重型框架会增加依赖和复杂度
- 当前方案更轻量级和可控

### Q2: 记忆会不会无限增长？

**A**:
- 自动清理：支持 TTL（可配置为 24 小时）
- 摘要压缩：长内容自动压缩
- 优先级排序：低优先级记忆可手动清理
- 用户控制：可以随时清空记忆

### Q3: AI 会不会滥用工具导致工作流混乱？

**A**:
- 参数验证：工具参数有类型检查
- 执行次数限制：可配置单个对话的工具调用次数上限
- 执行超时：每个工具调用有超时限制（默认 60s）
- 用户审批：关键操作（写文件）可设置用户确认

### Q4: 支持什么编程语言的文件？

**A**: 支持任意文本文件：
- 编程语言：Java, Python, C++, Rust, Go, 等
- 标记语言：JSON, XML, YAML, 等
- 文档：Markdown, HTML, 等
- 配置文件：properties, yml, conf, 等

### Q5: 和现有的 ToolExecutor 的关系是什么？

**A**:
- `ToolExecutor`：编译、测试、分析工具（项目级操作）
- `FileTools`：文件读写工具（代码级操作）
- `ToolRegistry`：统一的工具调用接口（两者都可以注册）

---

## 📊 工作量分解

### Phase 1: FileTools (2 天 = 16 小时)

```
Day 1 (8 小时)
├─ 1.1 结构设计 (1h)
├─ 1.2 readFile 实现 (3h)
├─ 1.3 FileReadResult (1h)
└─ 1.4 单元测试 (2h)

Day 2 (8 小时)
├─ 2.1 writeFile/appendFile (2.5h)
├─ 2.2 searchFiles/searchContent (2.5h)
├─ 2.3 结果类 (1h)
└─ 2.4 集成测试 (2h)
```

### Phase 2: ToolRegistry (1 天 = 8 小时)

```
├─ ToolDefinition 和参数定义 (2h)
├─ ToolRegistry 注册系统 (2h)
├─ executeTool 动态执行 (2h)
└─ 单元测试 (2h)
```

### Phase 3: AIMemoryStore (1.5 天 = 12 小时)

```
Day 1 (8 小时)
├─ Memory 类和 AIMemoryStore (1.5h)
├─ 核心操作实现 (2.5h)
├─ 摘要和压缩 (2h)
└─ 持久化存储 (2h)

Day 2 (4 小时)
├─ AIContextManager (2.5h)
└─ 集成测试 (1.5h)
```

以此类推...

**总计**：176 小时 = 8-9 工作天

---

## 🎯 成功指标

**项目完成后，应该能够**：

1. ✅ AI 自动读取和分析代码文件，不需要用户粘贴
2. ✅ AI 主动搜索相似代码模式和实现示例
3. ✅ AI 的分析基于完整项目信息，而不是片段
4. ✅ AI 可以自动修改文件并验证编译
5. ✅ 用户可以看到 AI 的决策过程（调用了哪些工具）
6. ✅ 后续任务可以复用之前读取的文件知识
7. ✅ 系统性能和 Token 消耗在可控范围内

---

## 🔐 已保障的质量

**在正式实现前已确认**：

- ✅ 架构设计与现有系统兼容
- ✅ 不需要添加新的外部依赖
- ✅ 可以分阶段实现和验证
- ✅ 有明确的集成点和测试计划
- ✅ 风险识别和缓解措施已制定
- ✅ 文档和示例规划完整

---

## 📞 后续沟通

**需要用户反馈的问题**：

1. 是否按规划的 9 个优先级进行？
2. 是否需要提前实现某个特定工具？
3. Token 限制的默认值建议是多少？
4. 是否需要向量数据库支持（现在不需要，后续可加）？
5. 文件权限和安全性的处理策略？

---

## 🎬 准备开始

**当一切都确认无误后，下一步**：

1. 创建 `FileTools.java`（Phase 1）
2. 实现 8 个核心方法
3. 编写单元测试
4. 提交代码审查
5. 继续 Phase 2...

**预计启动时间**：用户确认后立即开始

---

**文档版本**：v1.0 (Final Plan)
**生成工具**：Claude Code
**最后更新**：2025-10-27 14:55:00

