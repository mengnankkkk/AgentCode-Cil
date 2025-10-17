# 🛡️ HarmonySafeAgent

> OpenHarmony Security Analysis Tool - AI-powered code safety analyzer

一个专为OpenHarmony系统设计的智能安全分析工具，结合静态分析与AI能力，自动检测代码安全问题并提供修复建议。

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Java Version](https://img.shields.io/badge/Java-17+-blue)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-green)]()

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

### 🔄 阶段2：静态分析引擎（计划中）

- [ ] 代码文件扫描器
- [ ] Clang静态分析集成
- [ ] Semgrep规则引擎集成
- [ ] 安全问题分类与评级
- [ ] 并行分析实现

### 🔄 阶段3：AI增强分析（计划中）

- [ ] LLM客户端（OpenAI/DeepSeek）
- [ ] Prompt工程
- [ ] AI结果解析与结构化
- [ ] 混合分析决策引擎

### 🔄 阶段4：代码生成与重构（计划中）

- [ ] 代码修复建议生成
- [ ] Rust迁移建议生成
- [ ] 代码验证

### 🔄 阶段5：报告生成（计划中）

- [ ] HTML报告模板
- [ ] Markdown报告
- [ ] 数据可视化（图表）

### 🔄 阶段6：性能优化与测试（计划中）

- [ ] 并行分析优化
- [ ] 增量分析实现
- [ ] 单元测试（覆盖率 > 80%）
- [ ] 性能基准测试

### 🔄 阶段7：容器化与部署（计划中）

- [ ] Docker镜像
- [ ] 用户文档
- [ ] Demo准备

## 🤝 贡献指南

欢迎贡献！请遵循以下步骤：

1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

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
