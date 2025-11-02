# 🛡️ HarmonySafeAgent

> OpenHarmony Security Analysis Tool - AI-powered code safety analyzer with interactive REPL

一个专为OpenHarmony系统设计的智能安全分析工具，结合静态分析与多LLM能力，提供交互式分析、自动修复、代码审查和Rust迁移建议。

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Java Version](https://img.shields.io/badge/Java-17+-blue)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-green)]()

## 🔒 CI/CD 说明

本项目采用**手动触发**的 CI/CD 流程，确保测试在受控环境下运行：

- ✅ 所有 CI 测试需要**手动批准**后才会执行
- 🎯 支持选择性运行 E2E 测试和性能基准测试
- 🔐 避免不必要的资源消耗和 API 调用

### 如何手动触发 CI

1. 进入 GitHub 仓库的 **Actions** 标签页
2. 选择左侧的 **🚀 HarmonySafeAgent Tests** 工作流
3. 点击右上角的 **Run workflow** 按钮
4. 选择运行选项：
   - **运行 E2E 测试**：选择 `true` 或 `false`
   - **运行性能基准测试**：选择 `true` 或 `false`
5. 点击 **Run workflow** 开始执行

📖 **详细说明**：查看 [CI 快速启动](.github/CI_QUICKSTART.md) | [完整手册](.github/CI_MANUAL.md)

## ✨ 核心特性

### 🎯 交互式分析环境
- 💬 **交互式REPL模式**：类似Claude Code的持续对话界面，支持自然语言交互
- ⌨️ **系统命令集成**：在交互模式中执行系统命令（`$` 前缀），支持智能补全和跨平台
- 📜 **命令历史与自动补全**：基于JLine3的专业终端体验
- 🎨 **彩色输出**：优雅的ANSI彩色输出，友好的用户体验

### 🔍 多层次安全分析
- 🔬 **静态分析引擎**：集成Clang-Tidy和Semgrep，识别内存安全、并发、资源泄漏等问题
- 🤖 **AI增强验证**：使用LLM对静态分析结果进行智能过滤和优先级排序
- 📈 **战略分析**：提供问题严重性评分、修复难度评估和优先级分类
- ⚡ **增量分析**：基于哈希的变更检测，仅分析修改的文件

### 🛠️ AI驱动的代码改进
- 🔧 **自动修复**：LLM驱动的代码修复建议，支持变更预览、验证和应用
- 👁️ **代码审查**：AI审查代码质量、安全性、性能和最佳实践
- 💡 **智能建议**：针对安全问题提供上下文相关的修复方案
- 🦀 **Rust迁移**：C/C++代码到Rust的迁移建议和FFI安全指导

### 🌐 多LLM提供商支持
- 🔗 **灵活架构**：支持OpenAI (GPT-4/3.5)、Anthropic Claude、SiliconFlow等多个提供商
- 🎭 **角色系统**：Analyzer、Planner、Coder、Reviewer等专业角色
- 💾 **智能缓存**：基于Guava的AI响应缓存，减少API调用成本
- 🚦 **速率限制**：内置速率限制器，避免API配额超限

### 📊 专业报告生成
- 📄 **多格式输出**：HTML、JSON、Markdown等格式的安全分析报告
- 🎨 **响应式设计**：基于Freemarker的专业HTML模板，支持移动端
- 📈 **统计可视化**：问题分布、严重性分级、趋势分析
- 🔖 **详细上下文**：包含代码片段、修复建议、相关文件

### 🔌 扩展性与集成
- 🔧 **MCP集成**：支持Model Context Protocol，可与Claude Desktop等工具集成
- 🐳 **容器化部署**：提供Docker镜像和docker-compose配置
- 📦 **模块化架构**：清晰的分层设计，易于扩展和维护

## 🚀 快速开始

### 前置要求

- Java 17或更高版本
- Maven 3.6+
- （可选）Clang、Semgrep工具用于静态分析
- （可选）OpenAI/Claude/SiliconFlow API密钥用于AI功能

### 安装与构建

```bash
# 克隆仓库
git clone https://github.com/your-username/HarmonySafeAgent.git
cd HarmonySafeAgent

# 构建项目
mvn clean package

# （可选）配置API密钥
export OPENAI_API_KEY=sk-xxxxx
export CLAUDE_API_KEY=sk-ant-xxxxx
export SILICONFLOW_API_KEY=sk-xxxxx
```

### 启动交互式模式（推荐）

交互式REPL模式是使用HarmonySafeAgent的最佳方式：

```bash
# 方式1：使用启动脚本（推荐）
bin/agent-safe.sh      # Linux/macOS
bin\agent-safe.bat     # Windows

# 方式2：直接运行JAR（无参数默认进入交互模式）
java -jar target/harmony-agent.jar

# 方式3：显式启动交互模式
java -jar target/harmony-agent.jar interactive
```

**交互模式示例：**
```bash
❯ /help                          # 查看帮助
❯ /analyze ./bzip2               # 分析代码
❯ /suggest --severity high       # 获取修复建议
❯ /review src/main.c             # AI代码审查
❯ $ ls                           # 执行系统命令
❯ $ pwd                          # 查看当前目录
❯ What is buffer overflow?       # 自然语言提问
❯ /exit                          # 退出
```

### 命令行模式

也可以直接执行单次命令：

```bash
# 1. 安全分析
java -jar target/harmony-agent.jar analyze ./your-project

# 2. 战略分析（带优先级分类）
java -jar target/harmony-agent.jar strategic-analysis ./your-project

# 3. AI代码审查
java -jar target/harmony-agent.jar review ./src/main.c

# 4. 获取修复建议
java -jar target/harmony-agent.jar suggest --severity high

# 5. 生成报告
java -jar target/harmony-agent.jar report ./your-project -f html -o report.html

# 6. Rust迁移建议
java -jar target/harmony-agent.jar refactor --type rust-migration

# 7. 查看缓存统计
java -jar target/harmony-agent.jar cache-stats

# 8. 配置管理
java -jar target/harmony-agent.jar config list
java -jar target/harmony-agent.jar config set ai.provider openai
```

## 📖 命令详解

### `interactive` (或 `i`) - 交互式模式

启动交互式REPL环境，支持持续对话、命令执行和系统命令。

```bash
java -jar harmony-agent.jar interactive
# 或简写
java -jar harmony-agent.jar i
# 或直接运行（默认进入交互模式）
java -jar harmony-agent.jar
```

**交互模式命令：**
- `/analyze <path>` - 分析代码
- `/strategic-analysis <path>` - 战略分析（带评分）
- `/review <file>` - AI代码审查
- `/suggest [options]` - 获取修复建议
- `/refactor [options]` - 重构建议
- `/plan <task>` - 创建任务计划
- `/fix <issue-id>` - 应用自动修复
- `/config` - 显示配置
- `/history` - 查看历史
- `/cache-stats` - 缓存统计
- `/help` - 帮助信息
- `/clear` - 清屏
- `/exit` - 退出

**系统命令（`$` 前缀）：**
- `$ ls`, `$ pwd`, `$ cd` - 目录操作
- `$ cat <file>` - 查看文件
- `$ mvn clean install` - Maven构建
- `$ git status` - Git命令
- 支持Tab键自动补全

📖 详见：[交互模式完整指南](claudedocs/INTERACTIVE_MODE.md) | [系统命令使用](claudedocs/系统命令集成使用指南.md)

### `analyze` - 安全分析

执行代码安全分析，识别潜在的安全问题。

```bash
java -jar harmony-agent.jar analyze [源码路径] [选项]

选项：
  -l, --level       分析级别：quick | standard | deep（默认：standard）
  --incremental     启用增量分析（仅分析变更文件）
  --no-ai           禁用AI增强分析（仅使用静态分析）
  -o, --output      输出报告文件路径
```

**示例：**
```bash
# 标准分析
java -jar harmony-agent.jar analyze ./bzip2

# 深度分析
java -jar harmony-agent.jar analyze ./bzip2 --level deep

# 增量分析
java -jar harmony-agent.jar analyze ./bzip2 --incremental
```

### `strategic-analysis` - 战略分析

提供更深入的分析，包括严重性评分、修复难度评估和优先级分类。

```bash
java -jar harmony-agent.jar strategic-analysis [源码路径] [选项]

选项：
  -o, --output      输出报告文件路径（JSON格式）
  --threshold       最低严重性分数阈值（0-100，默认：50）
```

**输出包含：**
- 每个问题的严重性评分（0-100）
- 修复难度评估（简单/中等/困难）
- 优先级分类（Critical/High/Medium/Low）
- 业务影响评估

### `review` - AI代码审查

使用AI进行全面的代码审查，包括质量、安全性、性能和最佳实践检查。

```bash
java -jar harmony-agent.jar review [文件路径] [选项]

选项：
  --focus           审查重点：security | performance | quality | all（默认：all）
  -o, --output      输出审查报告文件路径
```

### `suggest` - AI建议

获取AI生成的改进建议和代码修复方案。

```bash
java -jar harmony-agent.jar suggest [选项]

选项：
  -s, --severity    按严重性过滤：critical | high | medium | low
  -c, --category    按类别过滤：memory | buffer | null | leak
  --code-fix        包含代码修复示例
  -f, --file        指定文件获取针对性建议
```

### `refactor` - 重构建议

生成代码重构建议或Rust迁移方案。

```bash
java -jar harmony-agent.jar refactor [源码路径] [选项]

选项：
  -t, --type        重构类型：fix | rust-migration（默认：fix）
  -o, --output      输出目录
  --interactive     交互式应用重构建议
```

### `report` - 生成报告

生成多种格式的安全分析报告。

```bash
java -jar harmony-agent.jar report [源码路径] -o [输出文件] [选项]

选项：
  -f, --format          报告格式：html | markdown | json（默认：html）
  --include-code        包含代码片段
  --include-fixes       包含修复建议
  --template            自定义Freemarker模板路径
```

### `cache-stats` - 缓存统计

查看AI响应缓存的统计信息，用于监控API使用和成本优化。

```bash
java -jar harmony-agent.jar cache-stats

选项：
  --clear           清空缓存
  --export          导出缓存数据
```

### `config` - 配置管理

管理工具配置。

```bash
# 查看所有配置
java -jar harmony-agent.jar config list

# 设置配置项
java -jar harmony-agent.jar config set [键] [值]

# 获取配置项
java -jar harmony-agent.jar config get [键]
```

**主要配置项：**
- `ai.provider` - AI提供商：openai | claude | siliconflow
- `ai.model` - 模型名称：gpt-4-turbo | claude-3-sonnet-20240229
- `ai.api_key` - API密钥（加密存储）
- `ai.max_tokens` - 最大Token数
- `analysis.level` - 分析级别：quick | standard | deep
- `analysis.parallel` - 并行分析：true | false
- `tools.clang_path` - Clang工具路径
- `tools.semgrep_path` - Semgrep工具路径
- `cache.enabled` - 启用缓存：true | false
- `cache.ttl` - 缓存过期时间（小时）

## 🏗️ 项目结构

```
HarmonySafeAgent/
├── src/
│   ├── main/
│   │   ├── java/com/harmony/agent/
│   │   │   ├── Main.java                 # 程序入口
│   │   │   ├── cli/                      # CLI命令模块
│   │   │   │   ├── HarmonyAgentCLI.java  # 主CLI类
│   │   │   │   ├── InteractiveCommand.java  # 交互式REPL
│   │   │   │   ├── AnalyzeCommand.java
│   │   │   │   ├── StrategicAnalysisCommand.java
│   │   │   │   ├── ReviewCommand.java
│   │   │   │   ├── SuggestCommand.java
│   │   │   │   ├── RefactorCommand.java
│   │   │   │   ├── ReportCommand.java
│   │   │   │   ├── ConfigCommand.java
│   │   │   │   ├── CacheStatsCommand.java
│   │   │   │   └── ConsolePrinter.java   # 控制台输出
│   │   │   ├── config/                   # 配置管理
│   │   │   │   ├── ConfigManager.java
│   │   │   │   ├── SecureConfigManager.java
│   │   │   │   └── AppConfig.java
│   │   │   ├── core/                     # 核心分析引擎
│   │   │   │   ├── AnalysisEngine.java   # 分析引擎
│   │   │   │   ├── CodeScanner.java      # 代码扫描
│   │   │   │   ├── analyzers/            # 分析器
│   │   │   │   │   ├── ClangAnalyzer.java
│   │   │   │   │   ├── SemgrepAnalyzer.java
│   │   │   │   │   └── RegexAnalyzer.java
│   │   │   │   ├── ai/                   # AI模块
│   │   │   │   │   ├── DecisionEngine.java
│   │   │   │   │   ├── PromptBuilder.java
│   │   │   │   │   ├── ValidationCache.java
│   │   │   │   │   └── RustMigrationAdvisor.java
│   │   │   │   └── report/               # 报告生成
│   │   │   │       ├── ReportGenerator.java
│   │   │   │       └── JsonReportWriter.java
│   │   │   ├── llm/                      # LLM架构
│   │   │   │   ├── orchestrator/         # 编排器
│   │   │   │   │   ├── LLMOrchestrator.java
│   │   │   │   │   ├── ConversationContext.java
│   │   │   │   │   └── TodoList.java
│   │   │   │   ├── provider/             # 提供商客户端
│   │   │   │   │   ├── ProviderFactory.java
│   │   │   │   │   ├── OpenAIClient.java
│   │   │   │   │   ├── ClaudeClient.java
│   │   │   │   │   └── SiliconFlowClient.java
│   │   │   │   └── role/                 # 角色系统
│   │   │   │       ├── RoleFactory.java
│   │   │   │       ├── AnalyzerRole.java
│   │   │   │       ├── PlannerRole.java
│   │   │   │       ├── CoderRole.java
│   │   │   │       └── ReviewerRole.java
│   │   │   ├── autofix/                  # 自动修复
│   │   │   │   ├── AutoFixOrchestrator.java
│   │   │   │   ├── ChangeManager.java
│   │   │   │   ├── CodeValidator.java
│   │   │   │   ├── DiffDisplay.java
│   │   │   │   ├── PendingChange.java
│   │   │   │   └── AppliedChange.java
│   │   │   ├── strategic/                # 战略分析
│   │   │   │   └── StrategicAnalyzer.java
│   │   │   ├── task/                     # 任务管理
│   │   │   │   └── TaskManager.java
│   │   │   ├── tools/                    # 工具执行
│   │   │   │   └── SystemCommandExecutor.java
│   │   │   ├── mcp/                      # MCP集成
│   │   │   │   ├── MCPClient.java
│   │   │   │   ├── MCPClientManager.java
│   │   │   │   ├── MCPRequest.java
│   │   │   │   └── MCPResponse.java
│   │   │   └── utils/                    # 工具类
│   │   └── resources/
│   │       ├── application.yml           # 默认配置
│   │       ├── logback.xml               # 日志配置
│   │       ├── templates/                # Freemarker模板
│   │       │   └── report-template.ftl
│   │       └── rules/                    # Semgrep规则
│   │           └── openharmony-rules.yml
│   └── test/                             # 测试代码
│       ├── java/                         # 单元测试
│       └── resources/e2e/                # E2E测试
├── claudedocs/                           # 开发文档
│   ├── INTERACTIVE_MODE.md              # 交互模式指南
│   ├── LLM_ARCHITECTURE.md              # LLM架构文档
│   ├── LLM_QUICKSTART.md                # LLM快速开始
│   ├── SILICONFLOW_SETUP.md             # SiliconFlow配置
│   ├── 系统命令集成使用指南.md
│   └── Agent增强建议可行性分析.md
├── bin/                                  # 启动脚本
│   ├── agent-safe.sh                     # Linux/macOS
│   └── agent-safe.bat                    # Windows
├── docker/                               # Docker配置
│   ├── Dockerfile
│   └── docker-compose.yml
├── pom.xml                               # Maven配置
└── README.md                             # 项目说明
```

## 🔧 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 17 | 主要开发语言 |
| 构建 | Maven | 3.9+ | 依赖管理与构建 |
| CLI | Picocli | 4.7.5 | 命令行框架 |
| 终端 | JLine | 3.25.1 | 交互式终端，命令补全 |
| HTTP | OkHttp | 4.12.0 | AI API调用 |
| JSON | Gson | 2.10.1 | JSON处理 |
| YAML | SnakeYAML | 2.2 | 配置文件 |
| 模板 | Freemarker | 2.3.32 | HTML报告生成 |
| 缓存 | Guava | 33.0.0 | AI响应缓存 |
| 日志 | Logback | 1.4.14 | 日志框架 |
| 控制台 | Jansi | 2.4.1 | ANSI彩色输出 |
| 工具 | Apache Commons | 3.14.0 | 通用工具库 |
| 测试 | JUnit 5 | 5.10.1 | 单元测试 |
| 测试 | Mockito | 5.8.0 | Mock框架 |

## 📝 开发阶段

### ✅ 阶段1：CLI框架（已完成）

- [x] Maven项目结构搭建
- [x] Picocli命令框架实现
- [x] 核心命令（analyze, suggest, refactor, report, config）
- [x] 配置管理（YAML + AES-256加密存储）
- [x] 控制台输出优化（彩色、进度条、格式化）
- [x] 构建与验证

### ✅ 阶段2：静态分析引擎（已完成）

- [x] 代码文件扫描器
- [x] Clang静态分析集成
- [x] Semgrep规则引擎集成
- [x] 正则表达式模式匹配
- [x] 安全问题分类与评级
- [x] 并行分析实现
- [x] 增量分析（基于哈希）

### ✅ 阶段3：AI增强分析（已完成）

- [x] LLM客户端（OpenAI/Claude/SiliconFlow）
- [x] Prompt工程（验证提示词、Rust迁移提示词）
- [x] AI结果解析与结构化
- [x] 混合分析决策引擎
- [x] 缓存机制优化（Guava Cache）
- [x] 速率限制器

### ✅ 阶段4：Rust迁移建议（已完成）

- [x] 代码切片提取器
- [x] Rust FFI安全建议生成
- [x] CLI集成（refactor命令）
- [x] Markdown格式输出

### ✅ 阶段5：可视化报告（已完成）

- [x] Freemarker模板引擎集成
- [x] 专业HTML报告模板（响应式设计）
- [x] AI增强信息展示
- [x] 统计数据可视化
- [x] 严重性分级展示

### ✅ 阶段6：质量保证与性能验证（已完成）

- [x] E2E集成测试（analyze, rust-migration）
- [x] bzip2基准测试项目
- [x] OpenHarmony分级测试框架（7个难度级别）
- [x] 性能基准测试（速度、内存、报告生成）
- [x] 已知局限性文档

### ✅ 阶段7：交互式REPL与系统集成（已完成）

- [x] 交互式REPL模式
- [x] JLine3集成（命令历史、自动补全）
- [x] 系统命令执行（$ 前缀）
- [x] 智能路径补全（文件/目录）
- [x] 危险命令拦截
- [x] 跨平台支持（Windows/Linux/macOS）

### ✅ 阶段8：LLM架构与多角色系统（已完成）

- [x] 灵活的Provider架构
- [x] 多LLM提供商支持（OpenAI, Claude, SiliconFlow）
- [x] 角色系统（Analyzer, Planner, Coder, Reviewer）
- [x] LLM编排器（Orchestrator）
- [x] 对话上下文管理
- [x] 任务计划系统（TodoList）

### ✅ 阶段9：自动修复与代码审查（已完成）

- [x] AutoFix编排器
- [x] 变更管理系统
- [x] 代码验证器
- [x] Diff展示
- [x] AI代码审查命令
- [x] 战略分析与评分

### ✅ 阶段10：MCP集成与扩展（已完成）

- [x] Model Context Protocol支持
- [x] Stdio传输模式
- [x] HTTP传输模式
- [x] MCP客户端管理器
- [x] Claude Desktop集成
- [x] 缓存统计命令

### 🔄 阶段11：容器化与部署（进行中）

- [x] Docker镜像
- [x] docker-compose配置
- [x] 启动脚本（Linux/Windows）
- [x] MCP服务器启动脚本
- [ ] 部署文档
- [ ] 用户手册
- [ ] Demo准备

## ⚠️ 已知局限性

### 增量分析

增量分析（`--incremental`）目前基于源文件（`.c`/`.cpp`）的哈希值进行变更检测。

**限制**：
- ✅ 支持：源文件修改检测
- ❌ 不支持：头文件（`.h`）修改检测

**解决方案**：
```bash
# 如果修改了头文件，请执行一次全量分析
java -jar harmony-agent.jar analyze ./project  # 不使用 --incremental 标志
```

**规划**：未来版本将支持基于依赖图的智能增量分析。

### AI增强分析

**API依赖**：
- 需要有效的OpenAI/Claude/SiliconFlow API密钥
- API调用可能受网络延迟影响
- 大型项目AI分析时间较长（可能5-10分钟）

**成本考虑**：
- GPT-4调用费用较高，建议使用`gpt-4-turbo`或SiliconFlow
- Claude提供性价比较好的选择
- 可使用`--no-ai`标志禁用AI功能
- 启用缓存可显著降低重复分析成本

### 静态分析工具

**外部依赖**：
- Clang-Tidy: 需要系统安装Clang工具链
- Semgrep: 需要Python环境和Semgrep安装

**编译数据库**：
- 需要`compile_commands.json`文件用于准确分析
- 可使用Bear或CMake生成

📖 参见：[分析器安装指南](claudedocs/ANALYZER_INSTALLATION.md)

### 性能特性

**分析速度**：
- 小型项目（<50文件）：< 1分钟
- 中型项目（50-200文件）：1-5分钟
- 大型项目（>200文件）：> 5分钟

**内存使用**：
- 预期峰值：512MB - 1GB
- 大型项目可能需要增加JVM堆内存：`java -Xmx2g -jar harmony-agent.jar`

## 🌟 高级功能

### MCP集成

HarmonySafeAgent支持Model Context Protocol，可与Claude Desktop等工具集成。

**Stdio模式（用于Claude Desktop）：**
```bash
# 启动MCP服务器
./start-mcp-stdio.sh        # Linux/macOS
start-mcp-stdio.bat         # Windows
```

**HTTP模式（用于远程集成）：**
```bash
# 启动HTTP服务器
./start-mcp-http.sh         # Linux/macOS
start-mcp-http.bat          # Windows
```

**配置Claude Desktop：**
在Claude Desktop的设置中添加：
```json
{
  "mcpServers": {
    "harmony-agent": {
      "command": "java",
      "args": ["-jar", "/path/to/harmony-agent.jar", "mcp-server", "--mode=stdio"]
    }
  }
}
```

### Docker部署

**使用Docker运行：**
```bash
# 构建镜像
docker build -t harmony-agent:latest .

# 运行容器
docker run -it --rm \
  -e OPENAI_API_KEY=sk-xxxxx \
  -v $(pwd)/project:/workspace \
  harmony-agent:latest analyze /workspace
```

**使用docker-compose：**
```bash
# 启动服务
docker-compose up -d

# 执行分析
docker-compose exec harmony-agent analyze /workspace
```

### LLM提供商配置

**使用不同的LLM提供商：**

```bash
# OpenAI (默认)
export OPENAI_API_KEY=sk-xxxxx
java -jar harmony-agent.jar config set ai.provider openai
java -jar harmony-agent.jar config set ai.model gpt-4-turbo

# Anthropic Claude
export CLAUDE_API_KEY=sk-ant-xxxxx
java -jar harmony-agent.jar config set ai.provider claude
java -jar harmony-agent.jar config set ai.model claude-3-sonnet-20240229

# SiliconFlow（性价比高）
export SILICONFLOW_API_KEY=sk-xxxxx
java -jar harmony-agent.jar config set ai.provider siliconflow
java -jar harmony-agent.jar config set ai.model deepseek-chat
```

📖 详见：[LLM架构文档](claudedocs/LLM_ARCHITECTURE.md) | [LLM快速开始](claudedocs/LLM_QUICKSTART.md) | [SiliconFlow配置](claudedocs/SILICONFLOW_SETUP.md)

## 🤝 贡献指南

欢迎贡献！请遵循以下步骤：

1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

### CI 测试说明

- 本项目的 CI 工作流采用**手动触发**模式
- 提交 PR 后，CI 测试不会自动运行
- 项目维护者会在审查代码后手动触发 CI 测试
- 如需在自己的 Fork 仓库中测试，可以手动触发工作流（参见上方 CI/CD 说明）

### 开发规范

- 遵循Java代码规范
- 编写单元测试（目标覆盖率 > 80%）
- 更新相关文档
- 提交前运行 `mvn clean verify`

## 📄 许可证

本项目采用 Apache License 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

## 📧 联系方式

- 项目地址：[https://github.com/your-username/HarmonySafeAgent](https://github.com/your-username/HarmonySafeAgent)
- 问题反馈：[GitHub Issues](https://github.com/your-username/HarmonySafeAgent/issues)

## 🙏 致谢

感谢以下开源项目：

- [Picocli](https://picocli.info/) - 强大的CLI框架
- [JLine](https://github.com/jline/jline3) - 交互式终端
- [Jansi](https://github.com/fusesource/jansi) - ANSI颜色支持
- [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) - YAML解析
- [OkHttp](https://square.github.io/okhttp/) - HTTP客户端
- [Freemarker](https://freemarker.apache.org/) - 模板引擎
- [Guava](https://github.com/google/guava) - Google核心库

---

⭐ 如果这个项目对你有帮助，请给个Star吧！
