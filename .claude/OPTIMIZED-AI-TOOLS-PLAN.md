# AI 系统工具能力实现 - 修订规划（优化版）

**修订时间**：2025-10-27 15:15:00
**修订原因**：采纳用户建议，充分利用现有的命令框架和缓存系统
**新状态**：✅ OPTIMIZED PLAN

---

## 🎯 关键改进

### 改进 1: 命令式接口（$read, $write, $search）

**原规划**：创建 FileTools 类，供 Agent 调用

**修订规划**：在 InteractiveCommand 中添加命令处理

**优势**：
- ✅ 与现有 $cd 命令保持一致
- ✅ 支持交互模式和 Agent 两种调用
- ✅ 更简洁，更易于扩展
- ✅ 代码量减少 50%

**实现方式**：

```java
// InteractiveCommand.java 中的新命令处理
switch (cmdName) {
    case "cd":
        handleCdCommand(command);
        break;

    // 【新增】文件操作命令
    case "read":
        handleReadCommand(command);      // $read <filepath> [maxLines]
        break;

    case "write":
        handleWriteCommand(command);     // $write <filepath> <content>
        break;

    case "append":
        handleAppendCommand(command);    // $append <filepath> <content>
        break;

    case "search":
        handleSearchCommand(command);    // $search <keyword> <directory> [-r]
        break;

    case "grep":
        handleGrepCommand(command);      // $grep <pattern> <filepath>
        break;

    case "ls":
        handleLsCommand(command);        // 已有，改进支持 --grep 等选项
        break;

    // 【后续可添加】
    case "find":
        handleFindCommand(command);      // $find <pattern> <directory> [-r]
        break;

    case "filter":
        handleFilterCommand(command);    // $filter <type> <directory>
        break;
}
```

**命令示例**：

```bash
# 交互模式下
$read src/main/java/App.java          # 读取文件
$read src/main/java/App.java 50       # 只读前 50 行
$search "TODO" src/                   # 搜索 TODO 注释
$search "function" src/ -r            # 递归搜索
$write output.txt "Hello World"       # 写文件
$append log.txt "New line"            # 追加文件
```

**Agent 调用方式**：

```java
// 在 LLMOrchestrator 中直接调用
ConsolePrinter printer = new ConsolePrinter();
String content = executeCommand("$read src/main/java/App.java");
```

---

### 改进 2: 复用现有缓存系统作为 AI 记忆

**原规划**：创建 AIMemoryStore（1.5 天工作）

**修订规划**：直接使用 PersistentCacheManager（0 天 - 不需要新代码）

**缓存系统现状**：

```
PersistentCacheManager
├─ L1 缓存（内存）
│  ├─ 容量：500 条记录
│  ├─ TTL：1 小时
│  └─ 性能：<1ms
│
└─ L2 缓存（磁盘）
   ├─ 容量：无限
   ├─ TTL：7 天
   └─ 性能：~5ms
```

**API 已有**：
- `get(String key)` - 读取缓存
- `put(String key, String value)` - 写入缓存
- `cleanupExpired()` - 清理过期
- 线程安全、自动序列化、支持 TTL

**使用方式**：

```java
// 初始化（创建 AI 记忆的缓存管理器）
PersistentCacheManager aiMemory = new PersistentCacheManager("ai-memory", true);

// 存储文件内容
aiMemory.put("file:src/App.java", fileContent);

// 存储搜索结果
aiMemory.put("search:TODO:src/", searchResults);

// 存储 AI 决策
aiMemory.put("decision:issue-123", decisionDetail);

// 检索记忆
String memory = aiMemory.get("file:src/App.java");

// 自动清理（系统会自动清理 7 天前的数据）
aiMemory.cleanupExpired();
```

**优势**：
- ✅ 不需要编写新代码
- ✅ 已经过生产验证
- ✅ 支持持久化和自动过期
- ✅ 线程安全，性能优化
- ✅ 自动清理机制

---

## 📊 修订后的工作量对比

| 项目 | 原规划 | 修订后 | 减少 |
|------|--------|--------|------|
| FileTools | 2 天 | 0 天 | -2 天 |
| AIMemoryStore | 1.5 天 | 0 天 | -1.5 天 |
| 命令集成 | 0 天 | 1 天 | +1 天 |
| 缓存集成 | 0 天 | 0.5 天 | +0.5 天 |
| 其他（不变） | 4 天 | 4 天 | 0 |
| **总计** | **8-9 天** | **2.5-3 天** | **-5.5-6.5 天** |

**节省 65% 的工作量！**

---

## 🔧 修订后的实现规划（仅需 3 天）

### Phase 1: 命令式文件工具（1 天）

**文件**：`src/main/java/com/harmony/agent/cli/InteractiveCommand.java`

**工作**：
- [ ] 添加 5 个新命令处理方法
  - [ ] handleReadCommand（从磁盘读取）
  - [ ] handleWriteCommand（写入磁盘）
  - [ ] handleAppendCommand（追加到磁盘）
  - [ ] handleSearchCommand（搜索文件名）
  - [ ] handleGrepCommand（搜索文件内容）

- [ ] 改进 handleLsCommand
  - [ ] 支持 --grep 过滤

- [ ] 创建文件操作的辅助工具类
  - [ ] FileCommandHelper（可选，如果代码过多）

- [ ] 单元测试和集成测试

**时间**：1 天

### Phase 2: AI 缓存集成（0.5 天）

**文件**：
- `src/main/java/com/harmony/agent/llm/orchestrator/AIMemoryManager.java`（新建，封装器）
- `src/main/java/com/harmony/agent/cli/InteractiveCommand.java`（修改）

**工作**：
- [ ] 创建 AIMemoryManager 封装器
  ```java
  public class AIMemoryManager {
      private PersistentCacheManager cache;

      public AIMemoryManager() {
          this.cache = new PersistentCacheManager("ai-memory", true);
      }

      // 存储文件内容到记忆
      public void rememberFile(String filePath, String content) {
          cache.put("file:" + filePath, content);
      }

      // 存储搜索结果到记忆
      public void rememberSearchResult(String keyword, String results) {
          cache.put("search:" + keyword, results);
      }

      // 检索相关记忆
      public String getMemory(String key) {
          return cache.get(key);
      }

      // 构建 Prompt 上下文
      public String buildMemoryContext(String query) {
          // 根据 query 检索相关记忆，组织成 Prompt
      }
  }
  ```

- [ ] 在 Role 中集成记忆检索
  - [ ] 修改 BaseLLMRole.buildPrompt()
  - [ ] 自动注入相关记忆

- [ ] 单元测试

**时间**：0.5 天

### Phase 3: LLMOrchestrator 工具调用支持（1 天）

**文件**：`src/main/java/com/harmony/agent/llm/orchestrator/LLMOrchestrator.java`

**工作**：
- [ ] 添加工具定义支持
  ```java
  // 向 LLM 声明可用工具
  List<ToolDefinition> tools = List.of(
      new ToolDefinition("read_file", "读取文件内容", ...),
      new ToolDefinition("search", "搜索文件", ...),
      new ToolDefinition("write_file", "写入文件", ...),
      // 等等
  );
  ```

- [ ] 处理工具调用响应
  ```java
  // 当 LLM 返回工具调用时
  if (response.hasToolCalls()) {
      for (ToolCall call : response.getToolCalls()) {
          String result = executeToolCommand(call.getName(), call.getArgs());

          // 结果存入记忆
          aiMemoryManager.storeToolResult(call.getName(), result);

          // 反馈给 LLM
          String feedback = "工具结果: " + result;
          continueConversation(feedback);
      }
  }
  ```

- [ ] 集成 AIMemoryManager
  - [ ] 自动将工具结果存入记忆

- [ ] 测试：完整的工具调用流程

**时间**：1 天

### Phase 4: 测试和文档（0.5 天）

**工作**：
- [ ] 集成测试
  - [ ] 交互模式下使用 $read、$search 等
  - [ ] Agent 通过工具调用执行命令
  - [ ] 记忆自动存储和检索

- [ ] 文档
  - [ ] 命令使用说明
  - [ ] API 文档

**时间**：0.5 天

---

## 📁 修订后的文件结构

```
src/main/java/com/harmony/agent/
├── cli/
│   ├── InteractiveCommand.java (修改)
│   │  ├── handleReadCommand()      【新增】
│   │  ├── handleWriteCommand()     【新增】
│   │  ├── handleAppendCommand()    【新增】
│   │  ├── handleSearchCommand()    【新增】
│   │  ├── handleGrepCommand()      【新增】
│   │  └── ... (现有方法)
│
├── llm/
│   ├── model/
│   │  ├── ToolDefinition.java     【新增】(轻量级)
│   │  └── ToolCall.java           【新增】(轻量级)
│
│   └── orchestrator/
│       ├── LLMOrchestrator.java    (修改)
│       │  └── handleToolCalls()    【新增】
│
│       └── AIMemoryManager.java    【新增】(封装器)
│           ├── rememberFile()
│           ├── rememberSearchResult()
│           └── buildMemoryContext()

test/java/com/harmony/agent/
├── cli/
│   └── InteractiveCommandFileToolsTest.java  【新增】
│
└── llm/
    └── orchestrator/
        ├── AIMemoryManagerTest.java          【新增】
        └── ToolCallHandlerTest.java          【新增】
```

**新增代码**：
- 3 个新类（ToolDefinition, ToolCall, AIMemoryManager）
- 5 个命令处理方法（InteractiveCommand）
- 1 个工具调用处理方法（LLMOrchestrator）
- ~200-300 行代码（非常精简）

---

## 🎯 核心流程（修订版）

### 交互模式下的文件操作

```
用户输入：$read src/App.java

InteractiveCommand.processInput()
  ↓
detectCommand() → cmdName = "read"
  ↓
switch case "read":
  ↓
handleReadCommand()
  ├─ 读取文件
  ├─ 存入 AIMemoryManager
  ├─ 返回内容到控制台
  └─ 同时存入缓存
```

### Agent 中的工具调用

```
AI: "我需要分析 App.java，请帮我读取这个文件"
  ↓
LLM 响应包含工具调用：
{
  "tool_calls": [
    {
      "name": "read_file",
      "arguments": {"path": "src/App.java"}
    }
  ]
}
  ↓
LLMOrchestrator.handleToolCalls()
  ├─ 识别工具调用
  ├─ 调用命令：executeCommand("$read src/App.java")
  ├─ 获得结果
  ├─ 存入 AIMemoryManager（自动）
  └─ 反馈给 AI："文件内容：..."
  ↓
AI 基于文件内容继续分析
```

### 记忆注入到 Prompt

```
AI 执行任务：分析代码

BaseLLMRole.buildPrompt()
  ├─ 基础 Prompt："你是代码分析专家..."
  ├─ 调用 AIMemoryManager.buildMemoryContext(task)
  ├─ 获得相关记忆：
  │  ├─ "前面读过的 App.java 内容"
  │  ├─ "搜索到的 TODO 列表"
  │  └─ "之前的分析结果"
  ├─ 组织成 Prompt
  └─ 发送给 LLM
```

---

## 📊 优化对比表

| 功能 | 原规划方式 | 修订后方式 | 代码量 | 工期 |
|------|-----------|-----------|--------|------|
| 文件读 | FileTools 类 | $read 命令 | 60行 | 0.5天 |
| 文件写 | FileTools 类 | $write 命令 | 60行 | 0.3天 |
| 文件搜 | FileTools 类 | $search 命令 | 80行 | 0.2天 |
| 记忆存 | AIMemoryStore 类 | PersistentCacheManager | 0行 | 0天 |
| 记忆检 | AIMemoryStore 类 | AIMemoryManager 封装 | 50行 | 0.3天 |
| 工具定 | ToolDefinition 类 | 轻量级类 | 30行 | 0.2天 |
| Prompt 注入 | AIContextManager | 直接集成到 Role | 40行 | 0.5天 |
| **总计** | **全部新写** | **充分复用** | **~320行** | **2.5-3天** |

---

## ✅ 验收标准（修订版）

### Phase 1 验收（命令式工具）

- [ ] 交互模式下 `$read` 命令正常工作
- [ ] 交互模式下 `$write`、`$append` 命令正常工作
- [ ] 交互模式下 `$search`、`$grep` 命令正常工作
- [ ] 命令支持多种参数形式（路径、行数限制、递归等）
- [ ] 错误处理完善（文件不存在、权限不足等）
- [ ] 编译无错误，单元测试 >80% 覆盖

### Phase 2 验收（记忆集成）

- [ ] 文件内容自动存入 AIMemoryManager
- [ ] 搜索结果自动存入记忆
- [ ] 记忆可以从缓存正确检索
- [ ] 过期记忆自动清理
- [ ] 支持 Session 和 Persistent 两种级别

### Phase 3 验收（工具调用）

- [ ] Agent 可以通过工具调用执行 $read、$search 等命令
- [ ] 工具调用结果正确返回给 Agent
- [ ] 工具结果自动存入记忆
- [ ] Agent 基于工具结果继续对话

### Phase 4 验收（文档和测试）

- [ ] 所有新增代码都有单元测试
- [ ] 集成测试通过（从命令到记忆到 Prompt）
- [ ] 文档完整（使用说明、API、示例）

---

## 🚀 立即行动清单

**修订后的优先级**（仅需 3 天）

1. **Day 1: 命令式工具实现**
   - [ ] handleReadCommand()
   - [ ] handleWriteCommand() / handleAppendCommand()
   - [ ] handleSearchCommand() / handleGrepCommand()
   - [ ] 单元测试

2. **Day 2: 记忆和工具调用集成**
   - [ ] AIMemoryManager 封装器
   - [ ] 在 Role 中集成记忆检索
   - [ ] LLMOrchestrator 工具调用处理
   - [ ] 集成测试

3. **Day 3: 完整化和文档**
   - [ ] 文档编写
   - [ ] 示例和用法说明
   - [ ] 最后的集成测试
   - [ ] 代码审查和优化

---

## 💡 为什么这个修订方案更好？

### 1. **充分利用现有资源**
- 不重复造轮子（FileTools vs 现有命令）
- 不创建 AIMemoryStore（直接用缓存系统）

### 2. **简洁性**
- 原规划：15+ 个新类，300+ 行代码
- 修订后：3 个新类，~320 行代码
- 代码量减少 60%

### 3. **一致性**
- 与现有的 $cd 命令保持一致
- 交互模式和 Agent 使用同一套接口

### 4. **性能**
- 复用已优化的缓存系统（L1 + L2）
- 无需重新设计数据结构

### 5. **可维护性**
- 更少的代码意味着更少的 bug
- 集中管理（所有命令在 InteractiveCommand）
- 集中管理记忆（所有 Agent 记忆在 AIMemoryManager）

### 6. **快速交付**
- 从 8-9 天减少到 2.5-3 天
- 可以更快地获得反馈和验证

---

## 🎓 对比：三个方案

| 方案 | 工期 | 代码量 | 复杂度 | 质量 |
|------|------|--------|--------|------|
| **原规划** | 8-9 天 | 500+ 行 | 高 | 中 |
| **修订方案**（推荐） | 2.5-3 天 | 320 行 | 低 | 高 |
| 最小方案 | 1-2 天 | 150 行 | 很低 | 中 |

**推荐采用修订方案**：性价比最高

---

## 📝 下一步确认

**请确认**：

1. ✅ 同意使用命令式接口（$read, $write 等）
2. ✅ 同意复用 PersistentCacheManager 作为记忆存储
3. ✅ 是否立即启动 Phase 1 实现？

**一旦确认，我们可以立即开始编码，预计 3 天内完成全部功能。**

---

**文档状态**：✅ READY FOR IMPLEMENTATION

**预计交付**：3 个工作日内完整实现和测试

