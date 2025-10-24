# 🛡️ HarmonySafeAgent

> OpenHarmony Security Analysis Tool - AI-powered code safety analyzer

一个专为OpenHarmony系统设计的智能安全分析工具，结合静态分析与AI能力，自动检测代码安全问题并提供修复建议。

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

## ✨ 特性

- 🔍 **静态安全分析**：集成Clang和Semgrep，识别内存安全、并发等问题
- 🤖 **AI增强分析**：利用大语言模型（GPT-4/DeepSeek）提供上下文相关的修复建议
- 🦀 **Rust迁移建议**：辅助C/C++代码向Rust安全迁移
- 📊 **可视化报告**：生成专业的HTML/Markdown安全报告
- ⚡ **增量分析**：仅分析变更文件，提升效率
- 🎨 **优雅CLI**：彩色输出、进度条、友好的用户体验

## 🚀 快速开始

### 前置要求

- Java 17或更高版本
- Maven 3.6+
- （可选）Clang、Semgrep工具用于静态分析

### 安装与构建

```bash
# 克隆仓库
git clone https://github.com/your-username/HarmonySafeAgent.git
cd HarmonySafeAgent

# 构建项目
mvn clean package

# 运行CLI
java -jar target/harmony-agent.jar --help
```

### 基本使用

```bash
# 1. 配置API密钥（使用环境变量）
export OPENAI_API_KEY=sk-xxxxx

# 或使用命令配置
java -jar target/harmony-agent.jar config set ai.api_key sk-xxxxx

# 2. 分析源码
java -jar target/harmony-agent.jar analyze ./your-project

# 3. 获取AI建议
java -jar target/harmony-agent.jar suggest ./your-project

# 4. 生成报告
java -jar target/harmony-agent.jar report ./your-project -f html -o report.html

# 5. Rust迁移建议
java -jar target/harmony-agent.jar refactor ./your-project --type rust-migration
```

## 📖 命令详解

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

### `suggest` - AI建议

获取AI生成的改进建议和代码修复方案。

```bash
java -jar harmony-agent.jar suggest [源码路径] [选项]

选项：
  -s, --severity    按严重性过滤：critical | high | medium | low
  -c, --category    按类别过滤：memory | buffer | null | leak
  --code-fix        包含代码修复示例
```

### `refactor` - 重构建议

生成代码重构建议或Rust迁移方案。

```bash
java -jar harmony-agent.jar refactor [源码路径] [选项]

选项：
  -t, --type        重构类型：fix | rust-migration（默认：fix）
  -o, --output      输出目录
```

### `report` - 生成报告

生成多种格式的安全分析报告。

```bash
java -jar harmony-agent.jar report [源码路径] -o [输出文件] [选项]

选项：
  -f, --format          报告格式：html | markdown | json（默认：html）
  --include-code        包含代码片段
  --include-fixes       包含修复建议
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

**配置项说明：**
- `ai.api_key` - AI服务API密钥（加密存储）
- `ai.provider` - AI服务提供商：openai | deepseek
- `ai.model` - 使用的模型：gpt-4-turbo | gpt-4 等
- `analysis.level` - 分析级别：quick | standard | deep
- `analysis.parallel` - 是否并行分析：true | false
- `tools.clang_path` - Clang工具路径
- `tools.semgrep_path` - Semgrep工具路径

## 🏗️ 项目结构

```
HarmonySafeAgent/
├── src/
│   ├── main/
│   │   ├── java/com/harmony/agent/
│   │   │   ├── Main.java                 # CLI入口
│   │   │   ├── cli/                      # CLI命令模块
│   │   │   │   ├── HarmonyAgentCLI.java  # 主CLI类
│   │   │   │   ├── ConsolePrinter.java   # 控制台输出
│   │   │   │   ├── AnalyzeCommand.java
│   │   │   │   ├── SuggestCommand.java
│   │   │   │   ├── RefactorCommand.java
│   │   │   │   ├── ReportCommand.java
│   │   │   │   └── ConfigCommand.java
│   │   │   ├── config/                   # 配置管理
│   │   │   │   ├── ConfigManager.java
│   │   │   │   ├── SecureConfigManager.java
│   │   │   │   └── AppConfig.java
│   │   │   ├── core/                     # 核心分析引擎（待实现）
│   │   │   ├── ai/                       # AI模块（待实现）
│   │   │   └── utils/                    # 工具类
│   │   └── resources/
│   │       ├── application.yml           # 默认配置
│   │       ├── logback.xml              # 日志配置
│   │       ├── templates/               # 报告模板
│   │       └── rules/                   # Semgrep规则
│   └── test/                            # 测试代码
├── docs/                                 # 文档
├── claudedocs/                           # 开发文档
│   └── 阶段性开发计划书_v2.0.md
├── pom.xml                              # Maven配置
└── README.md                            # 项目说明
```

## 🔧 技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 17 | 主要开发语言 |
| 构建 | Maven | 3.9+ | 依赖管理与构建 |
| CLI | Picocli | 4.7.5 | 命令行框架 |
| HTTP | OkHttp | 4.12.0 | AI API调用 |
| JSON | Gson | 2.10.1 | JSON处理 |
| YAML | SnakeYAML | 2.2 | 配置文件 |
| 日志 | Logback | 1.4.14 | 日志框架 |
| 控制台 | Jansi | 2.4.1 | ANSI彩色输出 |
| 测试 | JUnit 5 | 5.10.1 | 单元测试 |

## 📝 开发阶段

### ✅ 阶段1：CLI框架（已完成）

- [x] Maven项目结构搭建
- [x] Picocli命令框架实现
- [x] 5个核心命令（analyze, suggest, refactor, report, config）
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

### ✅ 阶段3：AI增强分析（已完成）

- [x] LLM客户端（OpenAI/DeepSeek）
- [x] Prompt工程（验证提示词、Rust迁移提示词）
- [x] AI结果解析与结构化
- [x] 混合分析决策引擎
- [x] 缓存机制优化

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

### 🔄 阶段7：容器化与部署（计划中）

- [ ] Docker镜像
- [ ] 用户文档
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
- 需要有效的OpenAI或DeepSeek API密钥
- API调用可能受网络延迟影响
- 大型项目AI分析时间较长（可能5-10分钟）

**成本考虑**：
- GPT-4调用费用较高，建议使用`gpt-4-turbo`或DeepSeek
- 可使用`--no-ai`标志禁用AI功能

### 静态分析工具

**外部依赖**：
- Clang-Tidy: 需要系统安装Clang工具链
- Semgrep: 需要Python环境和Semgrep安装

**编译数据库**：
- 需要`compile_commands.json`文件用于准确分析
- 可使用Bear或CMake生成

### 性能特性

**分析速度**：
- 小型项目（<50文件）：< 1分钟
- 中型项目（50-200文件）：1-5分钟
- 大型项目（>200文件）：> 5分钟

**内存使用**：
- 预期峰值：512MB - 1GB
- 大型项目可能需要增加JVM堆内存

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

## 📄 许可证

本项目采用 Apache License 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

## 📧 联系方式

- 项目地址：[https://github.com/your-username/HarmonySafeAgent](https://github.com/your-username/HarmonySafeAgent)
- 问题反馈：[GitHub Issues](https://github.com/your-username/HarmonySafeAgent/issues)

## 🙏 致谢

感谢以下开源项目：

- [Picocli](https://picocli.info/) - 强大的CLI框架
- [Jansi](https://github.com/fusesource/jansi) - ANSI颜色支持
- [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) - YAML解析
- [OkHttp](https://square.github.io/okhttp/) - HTTP客户端

---

⭐ 如果这个项目对你有帮助，请给个Star吧！
