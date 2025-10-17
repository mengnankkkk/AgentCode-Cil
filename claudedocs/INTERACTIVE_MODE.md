# 交互式模式使用指南

## 概述

HarmonySafeAgent现在支持**交互式REPL模式**，类似Claude Code，提供更友好的命令行交互体验。

## 启动方式

### 方式1：使用脚本（推荐）

**Windows:**
```bash
bin\agent-safe.bat
```

**Linux/macOS:**
```bash
bin/agent-safe.sh
```

### 方式2：直接使用JAR
```bash
java -jar target/harmony-agent.jar
```

### 方式3：显式启动交互模式
```bash
java -jar target/harmony-agent.jar interactive
# 或简写
java -jar target/harmony-agent.jar i
```

---

## 交互式界面

启动后，你会看到欢迎界面：

```
╔═══════════════════════════════════════════════════════╗
║                                                       ║
║   🛡️  HarmonySafeAgent v1.0.0                        ║
║   OpenHarmony Security Analysis Tool                 ║
║                                                       ║
║   AI-Powered Code Safety Analyzer                    ║
║                                                       ║
╚═══════════════════════════════════════════════════════╝

Starting interactive mode...

============================================================
🎯 HarmonySafeAgent Interactive Mode
============================================================

Welcome! You can:
  • Use slash commands: /analyze, /suggest, /help, /exit
  • Chat naturally: Ask questions about security, code analysis, etc.

AI Model: gpt-4-turbo
Mode: Interactive REPL


❯
```

---

## 使用方式

### 1. 斜杠命令（Slash Commands）

以 `/` 开头的命令用于执行特定功能：

#### 核心分析命令

```bash
# 分析代码安全问题
❯ /analyze ./src

# 获取AI修复建议（Phase 3）
❯ /suggest path/to/file.c

# 获取重构建议（Phase 4）
❯ /refactor path/to/file.c
```

#### 系统命令

```bash
# 显示帮助信息
❯ /help
❯ /h

# 显示当前配置
❯ /config

# 显示对话历史
❯ /history

# 清屏
❯ /clear
❯ /cls

# 退出交互模式
❯ /exit
❯ /quit
❯ /q
```

---

### 2. 自然语言对话

直接输入问题，不需要斜杠：

```bash
❯ What are common buffer overflow vulnerabilities in C?

AI: AI chat functionality will be available in Phase 3.
    Currently, you can use slash commands like /analyze, /help, etc.

❯ How can I prevent SQL injection?

AI: [AI response about SQL injection prevention]

❯ Explain use-after-free vulnerabilities

AI: [AI explanation about UAF]
```

**注意：** 完整的AI对话功能将在Phase 3实现。当前版本返回提示信息。

---

## 功能特性

### ✅ 已实现（Current）

1. **交互式REPL循环**
   - 持续输入/输出循环
   - 友好的命令提示符
   - 彩色输出支持

2. **斜杠命令系统**
   - `/analyze` - 安全分析（基于Phase 2）
   - `/help` - 帮助信息
   - `/config` - 配置显示
   - `/history` - 历史记录
   - `/clear` - 清屏
   - `/exit` - 退出

3. **对话历史管理**
   - 自动记录所有交互
   - `/history` 查看历史

4. **配置管理**
   - 显示当前LLM模型
   - 显示分析配置
   - 显示API提供商

### 🔄 计划中（Upcoming）

1. **Phase 3 - AI对话集成**
   - 完整的LLM对话功能
   - 上下文感知回答
   - 代码问题智能分析

2. **Phase 4 - 高级功能**
   - 交互式重构建议
   - 代码补全
   - 实时分析反馈

---

## 命令行模式 vs 交互模式

### 命令行模式（一次性执行）

```bash
# 直接执行分析，查看结果后退出
java -jar target/harmony-agent.jar analyze ./src

# 显示版本信息
java -jar target/harmony-agent.jar --version

# 显示帮助
java -jar target/harmony-agent.jar --help
```

### 交互模式（持续对话）

```bash
# 启动后保持活跃，可以执行多个命令
❯ /analyze ./src
[查看分析结果]

❯ /analyze ./test
[再次分析]

❯ What's a buffer overflow?
[AI回答]

❯ /exit
```

---

## 配置PATH（可选）

为了在任何目录下都能使用 `agent-safe` 命令：

### Windows

1. 将 `E:\github\HarmonySafeAgent\bin` 添加到系统PATH
2. 或创建别名：
   ```powershell
   Set-Alias agent-safe "E:\github\HarmonySafeAgent\bin\agent-safe.bat"
   ```

### Linux/macOS

添加到 `~/.bashrc` 或 `~/.zshrc`：

```bash
export PATH="$PATH:/path/to/HarmonySafeAgent/bin"
alias agent-safe="/path/to/HarmonySafeAgent/bin/agent-safe.sh"
```

然后重新加载配置：
```bash
source ~/.bashrc  # 或 source ~/.zshrc
```

---

## 使用示例

### 示例1：安全分析工作流

```bash
❯ bin\agent-safe.bat

❯ /analyze ./src/main

[分析结果显示]

❯ /history
[查看之前的所有操作]

❯ /config
[确认使用的分析器和配置]

❯ /exit
```

### 示例2：学习安全知识

```bash
❯ agent-safe

❯ What is a race condition?
AI: [解释竞态条件]

❯ How to prevent it?
AI: [防御策略]

❯ /analyze ./concurrent_code.c
[实际分析示例代码]
```

### 示例3：快速检查

```bash
❯ agent-safe

❯ /analyze ./new_feature.c
[快速安全检查]

❯ /exit
```

---

## 快捷键

- **Ctrl+C**: 中断当前命令（保持在交互模式）
- **Ctrl+D** 或 `/exit`: 退出交互模式
- **↑/↓**: 浏览命令历史（终端原生支持）

---

## 故障排除

### 问题1：启动脚本无法执行

**Windows:**
```bash
# 确保JAR文件存在
dir target\harmony-agent.jar

# 如果不存在，重新构建
mvn clean package
```

**Linux/macOS:**
```bash
# 确保脚本有执行权限
chmod +x bin/agent-safe.sh

# 确保JAR文件存在
ls -l target/harmony-agent.jar
```

### 问题2：命令未识别

确保使用正确的斜杠：
- ✅ `/analyze`
- ❌ `analyze` (缺少斜杠)
- ❌ `\analyze` (反斜杠)

### 问题3：无法退出

尝试以下任一命令：
- `/exit`
- `/quit`
- `/q`
- `Ctrl+C` 多次
- `Ctrl+D`

---

## 下一步

1. **配置API Key** (Phase 3准备):
   ```bash
   export HARMONY_AGENT_API_KEY="your-api-key"
   ```

2. **安装外部分析器** (更好的结果):
   - 参考 [ANALYZER_INSTALLATION.md](./ANALYZER_INSTALLATION.md)

3. **探索更多功能**:
   - 使用 `/help` 查看所有命令
   - 尝试不同的分析选项

---

## 技术细节

### 架构

```
HarmonyAgentCLI (Main)
  ↓
InteractiveCommand (REPL)
  ↓
  ├─ CommandRouter (斜杠命令)
  │    ├─ AnalyzeCommand
  │    ├─ SuggestCommand
  │    └─ ConfigCommand
  │
  └─ LLMClient (自然语言)
       └─ Chat API (Phase 3)
```

### 数据流

```
User Input
  ↓
[Starts with /] ?
  ├─ Yes → Slash Command Handler
  │         ↓
  │         Execute Command
  │         ↓
  │         Display Result
  │
  └─ No → Natural Language Handler
            ↓
            LLMClient.chat()
            ↓
            Display AI Response
```

---

## 反馈与支持

遇到问题或有建议？
- 查看 [README.md](../README.md)
- 提交Issue到GitHub
- 查看详细文档：[PHASE2_COMPLETION.md](./PHASE2_COMPLETION.md)

---

**提示：** 交互模式让安全分析变得更简单、更直观！享受使用吧！🚀
