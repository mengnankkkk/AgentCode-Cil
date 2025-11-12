# HarmonySafeAgent 快速启动指南

**版本**: 1.0.0  
**最后更新**: 2024年11月  
**适用于竞赛提交**

---

## 目录

1. [系统要求](#系统要求)
2. [安装步骤](#安装步骤)
3. [快速命令](#快速命令)
4. [完整工作流程](#完整工作流程)
5. [常见问题](#常见问题)
6. [性能优化](#性能优化)

---

## 系统要求

### 必需环境

| 组件 | 版本 | 说明 |
|------|------|------|
| **Java** | 17+ | OpenJDK 17或Oracle Java 17及以上 |
| **Maven** | 3.6+ | 用于构建和依赖管理 |
| **Git** | 2.0+ | 克隆仓库 |
| **操作系统** | Linux/macOS/Windows | 完全跨平台支持 |

### 可选工具（用于完整功能）

```bash
# 静态分析工具
- clang-tools (Clang-Tidy)  # 推荐: v14+
- semgrep                    # 推荐: v1.40+

# 代码转换和编译
- rustc                      # 推荐: v1.70+
- cargo                      # Rust包管理器

# 可视化报告
- 现代浏览器 (Chrome/Firefox)
```

### 依赖项验证

```bash
# 检查Java版本
java -version
# Expected: openjdk version "17" or higher

# 检查Maven版本
mvn -version
# Expected: Maven 3.6 or higher

# 检查Git版本
git --version
# Expected: git version 2.0 or higher

# 检查Clang-Tidy (可选)
clang-tidy --version
# Expected: LLVM version 14 or higher

# 检查Semgrep (可选)
semgrep --version
# Expected: 1.40.0 or higher
```

---

## 安装步骤

### 第1步: 克隆仓库

```bash
# 克隆项目
git clone https://github.com/your-org/HarmonySafeAgent.git
cd HarmonySafeAgent

# 验证分支
git branch -a
# 应该看到: feat-submission-spec-agent-bzip2-rust-design-test-video-prototype-cli
```

### 第2步: 环境变量配置

```bash
# 创建 .env 文件 (复制示例)
cp .env.example .env

# 编辑 .env 文件，配置API密钥 (可选)
# 如果使用AI功能，需要至少配置一个LLM提供商

# 示例配置:
export OPENAI_API_KEY=sk-your-key-here
export CLAUDE_API_KEY=sk-ant-your-key-here
export SILICONFLOW_API_KEY=sk-your-key-here

# 或写入 .env 文件
cat > .env << 'EOF'
# OpenAI Configuration
OPENAI_API_KEY=sk-your-key-here
OPENAI_MODEL=gpt-4
OPENAI_TIMEOUT=30

# Claude Configuration
CLAUDE_API_KEY=sk-ant-your-key-here
CLAUDE_MODEL=claude-3-opus-20240229

# SiliconFlow Configuration
SILICONFLOW_API_KEY=sk-your-key-here
SILICONFLOW_MODEL=Qwen/Qwen-14B-Chat

# Analysis Configuration
ENABLE_CLANG=true
ENABLE_SEMGREP=true
ENABLE_AI_VALIDATION=true
CACHE_SIZE=1000
EOF
```

### 第3步: 编译项目

```bash
# 完整构建 (推荐)
mvn clean package

# 输出信息应该显示:
# [INFO] Building HarmonySafeAgent 1.0.0
# ...
# [INFO] BUILD SUCCESS

# 构建速度: 首次 3-5分钟, 后续增量 <1分钟
```

### 第4步: 验证安装

```bash
# 检查构建产物
ls -la target/
# 应该看到: harmony-safe-agent-1.0.0.jar

# 验证可执行性
java -jar target/harmony-safe-agent-1.0.0.jar --help
# 应该显示帮助信息

# 或使用启动脚本
./bin/agent-safe.sh --help
```

---

## 快速命令

### 启动交互式CLI

```bash
# 方式1: 使用启动脚本 (推荐)
./bin/agent-safe.sh        # Linux/macOS
.\bin\agent-safe.bat       # Windows

# 方式2: 直接运行JAR
java -jar target/harmony-safe-agent-1.0.0.jar

# 预期输出:
# ╔═══════════════════════════════════════╗
# ║  🛡️  HarmonySafeAgent v1.0.0          ║
# ║  AI-Powered OpenHarmony Security      ║
# ║  Analysis & Rust Migration Tool       ║
# ╚═══════════════════════════════════════╝
# 
# Type 'help' for available commands
# >
```

### 交互式命令示例

```bash
# 获取帮助
> help
> help analyze
> help generate-rust

# 分析代码
> analyze /path/to/bzip2

# 查看分析结果
> show-all
> show-critical
> show-issues-by-type

# 获取修复建议
> suggest-fix 1
> suggest-fix 2-5

# 生成Rust代码
> generate-rust /path/to/module.c

# 生成报告
> generate-report html
> generate-report json

# 缓存管理
> cache-stats
> cache-clear

# 退出
> exit
> quit
```

---

## 完整工作流程

### 场景1: 分析bzip2并生成报告

```bash
# 启动工具
./bin/agent-safe.sh

# 分析bzip2源代码
> analyze ./samples/bzip2-1.0.8

# 等待分析完成 (约45-60秒)
[INFO] Scanning: 8000 LOC across 8 files
[INFO] Level 1 Analysis: 12 issues found
[INFO] Level 2 Analysis: 28 issues found
[INFO] Level 3 AI Validation: Running...
[INFO] Analysis Complete: 18 validated issues

# 查看统计
> show-all

# 查看关键问题
> show-critical

# 获取第一个问题的建议
> suggest-fix 1

# 生成完整报告
> generate-report html

# 输出:
# Report saved to: bzip2-analysis-report.html
# Open the report in your browser to view detailed analysis

# 退出
> exit
```

### 场景2: Rust代码迁移

```bash
# 启动工具
./bin/agent-safe.sh

# 分析特定模块
> analyze ./samples/bzip2-1.0.8/huffman.c

# 查看转换可行性
> generate-rust ./samples/bzip2-1.0.8/huffman.c

# 输出:
# Analyzing C code for Rust conversion...
# Complexity: 8.3 (moderate)
# Conversion strategy: DIRECT_CONVERSION
# Unsafe blocks needed: 0
# 
# Generated Rust code: huffman.rs
# Lines: 245
# Compilation status: ✓ Success

# 查看生成的Rust文件
$ cat huffman.rs

# 运行Rust编译检查
$ rustc --crate-type lib huffman.rs

# 生成完整迁移指南
> generate-report markdown

# 退出
> exit
```

### 场景3: 持续集成调用

```bash
# 方式1: 单行命令调用
java -jar target/harmony-safe-agent-1.0.0.jar \
    analyze ./src \
    --format=json \
    --output=./reports/analysis.json

# 方式2: 使用Docker
docker run \
    -v $(pwd):/workspace \
    harmony-safe-agent:latest \
    analyze /workspace/src

# 方式3: 管道调用
./bin/agent-safe.sh << 'EOF'
analyze ./src
generate-report json
generate-report html
exit
EOF
```

---

## 常见问题

### Q1: 如何验证安装是否成功?

```bash
# 运行完整性检查
java -jar target/harmony-safe-agent-1.0.0.jar --health-check

# 预期输出:
# ✓ Java version: 17.0.x
# ✓ Maven environment: OK
# ✓ Core modules: Loaded
# ✓ LLM providers: Configured
# ✓ Static analyzers: Clang-Tidy (found), Semgrep (found)
# ✓ Storage: OK
# 
# Status: READY
```

### Q2: 如何处理内存不足错误?

```bash
# 增加JVM堆内存
export JVM_OPTS="-Xmx4g -Xms2g"

# 或直接在命令行指定
java -Xmx4g -Xms2g -jar target/harmony-safe-agent-1.0.0.jar
```

### Q3: 如何禁用可选工具（如Clang-Tidy)?

编辑 `~/.harmony-safe/config.yaml`:

```yaml
analysis:
  enable_clang: false      # 禁用Clang-Tidy
  enable_semgrep: true     # 保留Semgrep
  enable_ai_validation: true
```

或使用命令行:

```bash
> analyze ./src --disable-clang --disable-semgrep
```

### Q4: 如何查看详细日志?

```bash
# 设置日志级别
export LOG_LEVEL=DEBUG

# 或编辑配置
# src/main/resources/logback.xml

# 运行时查看日志
./bin/agent-safe.sh 2>&1 | tee agent.log
```

### Q5: 报告生成失败怎么办?

```bash
# 检查模板文件
ls -la src/main/resources/templates/

# 检查输出目录权限
chmod 755 ./reports

# 尝试手动生成
> generate-report json --output ./test-report.json
```

---

## 性能优化

### 内存优化

```bash
# 对于大型项目 (>100 KLOC)
export JVM_OPTS="-Xmx8g -Xms4g -XX:+UseG1GC"

# 对于小型项目 (<50 KLOC)
export JVM_OPTS="-Xmx2g -Xms1g"
```

### 分析优化

```bash
# 启用增量分析 (跳过未修改的文件)
> analyze ./src --incremental

# 使用并行分析
> analyze ./src --parallel=4

# 禁用AI验证加快速度 (仅使用静态分析)
> analyze ./src --no-ai-validation

# 仅运行快速扫描
> analyze ./src --fast
```

### 缓存优化

```bash
# 查看缓存统计
> cache-stats

# 预期输出:
# Cache Statistics
# ├─ Total entries: 1,234
# ├─ Memory usage: 234 MB
# ├─ Hit rate: 72.3%
# └─ Most cached: memory_leak pattern

# 清理缓存
> cache-clear --older-than=7d

# 禁用缓存
> analyze ./src --no-cache
```

### CI/CD优化

```bash
# 仅导出关键问题（用于失败构建的门槛）
java -jar target/harmony-safe-agent-1.0.0.jar \
    analyze ./src \
    --min-severity=HIGH \
    --format=json \
    --output=./critical-issues.json

# 检查是否有关键问题
if [ $(jq '.issues | length' critical-issues.json) -gt 0 ]; then
    echo "CRITICAL ISSUES FOUND - Build Failed"
    exit 1
fi
```

---

## 验证竞赛要求

### ✅ 技术设计文档

```bash
# 位置
ls -la TECHNICAL_DESIGN_DOCUMENT.md

# 内容检查
wc -l TECHNICAL_DESIGN_DOCUMENT.md
# Expected: 15-30 page markdown document (800-1500 lines)
```

### ✅ 演示视频脚本

```bash
# 位置
ls -la DEMO_VIDEO_SCRIPT.md

# 包含内容:
# - Agent工作流程演示
# - bzip2代码问题识别功能
# - 生成Rust代码的示例演示
# - 技术创新点讲解
```

### ✅ 可执行原型

```bash
# 命令1: 分析功能
./bin/agent-safe.sh << 'EOF'
analyze ./samples/bzip2-1.0.8
exit
EOF

# 命令2: 生成功能
./bin/agent-safe.sh << 'EOF'
analyze ./samples/bzip2-1.0.8/huffman.c
generate-rust ./samples/bzip2-1.0.8/huffman.c
exit
EOF

# 验证完整性
# ✓ ./agent analyze <file> - 支持
# ✓ ./agent generate <file> - 支持
# ✓ 完整安装脚本 - 已提供
# ✓ 依赖说明 - 已提供
# ✓ bzip2功能实现 - 已完成
```

---

## 下一步

### 生成演示

```bash
# 运行完整演示流程
./bin/demo.sh

# 或按步骤手动运行
./bin/agent-safe.sh

# 在交互式CLI中执行演示命令
> demo-bzip2-analysis
> demo-rust-generation
> demo-report-generation
```

### 阅读文档

- `TECHNICAL_DESIGN_DOCUMENT.md` - 详细的技术设计
- `README.md` - 项目概述
- `claudedocs/` - 开发文档和分析

### 提交竞赛

确保包含以下文件:
- ✅ TECHNICAL_DESIGN_DOCUMENT.md (技术设计文档)
- ✅ DEMO_VIDEO_SCRIPT.md (演示视频脚本)
- ✅ 完整的源代码
- ✅ 可执行的JAR文件
- ✅ 启动脚本 (bin/agent-safe.sh)
- ✅ 完整的pom.xml依赖配置
- ✅ README.md安装说明

---

## 支持与反馈

如有问题:
1. 查看FAQ部分
2. 检查日志: `agent.log`
3. 提交Issue到GitHub仓库
4. 查阅完整文档: `claudedocs/`

祝使用愉快！🛡️

