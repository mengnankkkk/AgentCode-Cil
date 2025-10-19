# Suggest 和 Refactor 命令使用指南

## 概述

`/suggest` 和 `/refactor` 命令现在已经完全实现，基于 `/analyze` 生成的报告数据提供真实的 AI 安全修复建议。

## 工作流程

### 1. 生成分析报告

首先运行分析生成报告（同时生成 HTML 和 JSON 格式）：

```bash
# 分析 C/C++ 项目
java -jar target/harmony-agent.jar analyze /path/to/bzip2 -o bzip2-analysis-report.html

# 输出：
# - bzip2-analysis-report.html（人类可读的HTML报告）
# - bzip2-analysis-report.json（机器可读的JSON数据）
```

### 2. 使用 Suggest 命令生成修复建议

基于报告数据生成 AI 修复建议：

```bash
# 基本用法 - 为前5个问题生成建议
java -jar target/harmony-agent.jar suggest bzip2-analysis-report.json

# 为特定问题生成建议
java -jar target/harmony-agent.jar suggest bzip2-analysis-report.json -n 0

# 只为严重问题生成建议
java -jar target/harmony-agent.jar suggest bzip2-analysis-report.json --severity critical

# 按类别筛选
java -jar target/harmony-agent.jar suggest bzip2-analysis-report.json --category buffer

# 生成更多建议
java -jar target/harmony-agent.jar suggest bzip2-analysis-report.json --max 10
```

### 3. 使用 Refactor 命令

#### 3.1 代码修复重构（基于报告）

```bash
# 基本用法 - 为前5个问题生成重构建议
java -jar target/harmony-agent.jar refactor bzip2-analysis-report.json -t fix

# 为特定问题生成重构
java -jar target/harmony-agent.jar refactor bzip2-analysis-report.json -t fix -n 2

# 生成更多重构建议
java -jar target/harmony-agent.jar refactor bzip2-analysis-report.json -t fix --max 10
```

#### 3.2 Rust 迁移建议（独立使用）

```bash
# 为特定 C 函数生成 Rust 迁移建议
java -jar target/harmony-agent.jar refactor /path/to/bzip2 \
  --type rust-migration \
  -f bzlib.c \
  -l 234
```

## 命令参数详解

### Suggest 命令

```
harmony-agent suggest <report.json> [OPTIONS]

参数：
  <report.json>          JSON 报告文件路径（必需）

选项：
  -s, --severity <level> 按严重级别筛选: critical | high | medium | low
  -c, --category <type>  按类别筛选: memory | buffer | null | leak
  -n, --number <n>       只处理特定问题编号（0-based）
  --max <n>              最多生成 N 个建议（默认: 5）
  -v, --verbose          显示详细输出
```

### Refactor 命令

```
harmony-agent refactor <path> [OPTIONS]

参数：
  <path>                 JSON 报告文件路径 或 源代码目录

选项：
  -t, --type <type>      重构类型: fix | rust-migration（默认: fix）
  -n, --number <n>       只处理特定问题编号（fix 类型使用）
  --max <n>              最多生成 N 个重构（默认: 5）

  # Rust 迁移专用选项：
  -f, --file <file>      源文件名（rust-migration 必需）
  -l, --line <number>    行号（rust-migration 必需）
  -o, --output <dir>     输出目录（未来功能）
```

## 输出示例

### Suggest 命令输出

```markdown
═════════════════════════════════════════════════════════════
AI Security Suggestions
═════════════════════════════════════════════════════════════
Provider: OpenAI
Model: gpt-4
Generating suggestions for 3 issues...

─────────────────────────────────────────────────────────────
Issue #0: Buffer overflow in strcpy
─────────────────────────────────────────────────────────────
  Location: bzlib.c:234:12
  Severity: CRITICAL
  Category: BUFFER_OVERFLOW

🔍 Issue Analysis
The use of strcpy() without bounds checking can lead to buffer overflow...

💡 Fix Recommendation
1. Replace strcpy with strncpy
2. Add size validation
3. Consider using safer alternatives

🔧 Code Fix
```c
// Original (unsafe):
strcpy(dest, src);

// Fixed (safe):
size_t destSize = sizeof(dest);
strncpy(dest, src, destSize - 1);
dest[destSize - 1] = '\0';  // Ensure null termination
```

✅ Validation
- Test with various input sizes
- Verify null termination
- Check for truncation warnings

📚 Best Practices
- Always use sized string operations
- Validate input lengths
- Consider using std::string in C++
```

## 技术实现细节

### 架构变更

1. **JsonReportWriter**: 新增 JSON 报告读写功能
2. **SecuritySuggestionAdvisor**: 新增安全修复建议生成器
3. **PromptBuilder**: 新增 `buildSecurityFixPrompt` 方法
4. **AnalysisEngine**: 自动生成 JSON 报告（与 HTML 同步）

### 数据流

```
┌─────────────┐
│  /analyze   │ → 生成 HTML + JSON 报告
└─────────────┘
       ↓
       ↓ report.json
       ↓
┌─────────────┐
│ /suggest or │ → 读取 JSON → 提取问题 → 调用 LLM → 生成建议
│ /refactor   │
└─────────────┘
```

### LLM 集成

- 支持多个 LLM 提供商（OpenAI, Claude, SiliconFlow）
- 可配置不同命令使用不同模型
- 通过 PromptBuilder 构建专业提示词
- CodeSlicer 提取代码上下文

## 配置说明

在 `config.yaml` 中配置：

```yaml
ai:
  provider: openai
  model: gpt-4

  # 命令级别配置
  commands:
    suggest:
      provider: openai
      model: gpt-3.5-turbo
    refactor:
      provider: claude
      model: claude-3-sonnet-20240229
```

## 环境变量

```bash
# OpenAI
export OPENAI_API_KEY="sk-..."

# Claude
export CLAUDE_API_KEY="sk-ant-..."

# SiliconFlow
export SILICONFLOW_API_KEY="sk-..."
```

## 最佳实践

1. **先分析后建议**: 始终先运行 `/analyze` 生成报告
2. **筛选关键问题**: 使用 `--severity critical` 聚焦严重问题
3. **逐个修复**: 使用 `-n` 参数逐个处理复杂问题
4. **保存建议**: 使用重定向保存建议到文件

```bash
# 保存 critical 问题的修复建议
java -jar target/harmony-agent.jar suggest report.json \
  --severity critical > fixes.md
```

## 限制和未来改进

### 当前限制

- 仅读取问题，不直接修改代码
- `-o/--output` 选项仅显示消息（代码生成功能开发中）
- 依赖 LLM API（需要网络和 API 密钥）

### 未来计划

- [ ] 自动应用修复（P4 Phase 实现）
- [ ] 批量修复多个问题
- [ ] 生成修复的 diff 文件
- [ ] 交互式修复确认
- [ ] 本地 LLM 支持

## 故障排除

### 问题：找不到报告文件

**解决**: 确保先运行 `/analyze` 生成报告，并使用 `.json` 扩展名

```bash
java -jar target/harmony-agent.jar analyze /path -o report.html
java -jar target/harmony-agent.jar suggest report.json  # ✓
```

### 问题：LLM 提供商不可用

**解决**: 检查 API 密钥是否正确配置

```bash
echo $OPENAI_API_KEY  # 应该显示你的密钥
```

### 问题：建议质量不佳

**解决**: 尝试使用更强大的模型

```yaml
ai:
  commands:
    suggest:
      model: gpt-4  # 或 claude-3-opus-20240229
```

## 示例工作流

```bash
# 完整工作流示例
cd /path/to/bzip2

# 1. 分析项目
java -jar /path/to/harmony-agent.jar analyze . \
  -o security-report.html \
  --compile-commands compile_commands.json

# 2. 查看严重问题的修复建议
java -jar /path/to/harmony-agent.jar suggest security-report.json \
  --severity critical

# 3. 为特定问题生成详细建议
java -jar /path/to/harmony-agent.jar suggest security-report.json \
  -n 0 > issue-0-fix.md

# 4. 生成代码重构建议
java -jar /path/to/harmony-agent.jar refactor security-report.json \
  -t fix -n 0

# 5. （可选）Rust 迁移建议
java -jar /path/to/harmony-agent.jar refactor . \
  --type rust-migration -f bzlib.c -l 234
```

## 总结

这两个命令现在已经完全实现了真实的 AI 辅助功能，不再是占位符。它们基于实际的分析报告数据，使用 LLM 生成专业的安全修复建议，大大提升了开发者修复安全问题的效率。
