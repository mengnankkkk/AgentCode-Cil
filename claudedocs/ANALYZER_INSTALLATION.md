# 静态分析器安装指南

HarmonySafeAgent支持多种静态分析器来提供更全面的安全分析。本指南介绍如何安装和配置这些分析器。

## 内置分析器

### RegexAnalyzer (默认)

RegexAnalyzer是内置的基于正则表达式的分析器，无需安装任何外部工具即可使用。

**特点：**
- ✅ 无需安装，开箱即用
- ✅ 快速轻量级
- ✅ 支持常见安全问题检测
- ⚠️ 检测能力有限，可能有误报

**支持的安全问题：**
- Buffer Overflow (strcpy, strcat, sprintf, gets)
- Memory Leak (malloc without free)
- Format String Vulnerabilities
- Command Injection (system, popen)
- Weak Cryptography (rand, MD5)
- Path Traversal
- Integer Overflow
- Race Conditions (TOCTOU)

---

## 外部分析器

为了获得更准确和全面的安全分析结果，强烈建议安装以下外部分析器：

### 1. Clang-Tidy

Clang-Tidy是基于Clang/LLVM的强大C/C++静态分析器。

#### Linux (Ubuntu/Debian)
```bash
sudo apt-get update
sudo apt-get install clang-tidy
```

#### macOS
```bash
# 使用Homebrew
brew install llvm

# Clang-Tidy会随LLVM一起安装
# 添加到PATH (可选)
echo 'export PATH="/usr/local/opt/llvm/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

#### Windows
```powershell
# 使用Chocolatey
choco install llvm

# 或者下载LLVM安装包
# https://releases.llvm.org/download.html
```

#### 验证安装
```bash
clang-tidy --version
```

**特点：**
- ✅ 深度代码分析
- ✅ 基于编译器技术
- ✅ 低误报率
- ✅ 支持现代C++标准
- ⚠️ 需要编译配置

---

### 2. Semgrep

Semgrep是快速、可定制的多语言静态分析器。

#### 安装（所有平台）
```bash
# 使用pip安装
pip install semgrep

# 或使用pipx (推荐)
pipx install semgrep
```

#### Linux特定
```bash
# Ubuntu/Debian
sudo apt-get install python3-pip
pip3 install semgrep
```

#### macOS特定
```bash
# 使用Homebrew
brew install semgrep
```

#### 验证安装
```bash
semgrep --version
```

**特点：**
- ✅ 快速扫描
- ✅ 自定义规则
- ✅ 多语言支持
- ✅ 活跃社区规则库
- ✅ 易于使用

---

## 分析器配置

### 配置文件位置

默认配置文件: `~/.harmony-agent/config.yml`

### 启用/禁用分析器

```yaml
analyzers:
  clang:
    enabled: true
    path: /usr/bin/clang-tidy  # 可选：指定路径

  semgrep:
    enabled: true
    rules_path: ./src/main/resources/rules  # 自定义规则路径

  regex:
    enabled: true  # 内置分析器，始终可用
```

### 自定义Semgrep规则

HarmonySafeAgent包含针对OpenHarmony的自定义Semgrep规则：

```
src/main/resources/rules/
├── buffer-overflow.yml
├── memory-safety.yml
├── crypto.yml
├── concurrency.yml
└── injection.yml
```

您可以添加自己的规则文件到此目录。

---

## 使用示例

### 基本分析（仅使用RegexAnalyzer）
```bash
harmony-agent analyze ./src
```

### 使用所有可用分析器
```bash
# 如果已安装Clang-Tidy和Semgrep，它们会自动被使用
harmony-agent analyze ./src
```

### 深度分析
```bash
harmony-agent analyze ./src --level deep
```

---

## 性能对比

| 分析器 | 速度 | 准确性 | 覆盖范围 | 安装难度 |
|--------|------|--------|----------|----------|
| RegexAnalyzer | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ✅ 无需安装 |
| Clang-Tidy | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⚠️ 中等 |
| Semgrep | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ✅ 简单 |

---

## 故障排除

### Clang-Tidy未检测到

**问题：** "Clang analyzer not available"

**解决方案：**
1. 确认Clang-Tidy已安装：`which clang-tidy`
2. 检查PATH环境变量
3. 在配置文件中指定完整路径：
   ```yaml
   analyzers:
     clang:
       path: /usr/local/bin/clang-tidy
   ```

### Semgrep未检测到

**问题：** "Semgrep analyzer not available"

**解决方案：**
1. 确认Semgrep已安装：`which semgrep`
2. 检查Python/pip版本：`python3 --version`
3. 尝试重新安装：`pip3 install --upgrade semgrep`

### 权限问题 (Linux/macOS)

如果遇到权限问题：
```bash
# 使用用户级安装
pip install --user semgrep

# 或使用虚拟环境
python3 -m venv venv
source venv/bin/activate
pip install semgrep
```

---

## 推荐配置

### 开发环境
```yaml
analysis:
  level: standard
  parallel: true
  analyzers:
    - RegexAnalyzer
    - Semgrep
```

### CI/CD环境
```yaml
analysis:
  level: deep
  parallel: true
  analyzers:
    - Clang-Tidy
    - Semgrep
    - RegexAnalyzer
```

### 快速检查
```yaml
analysis:
  level: quick
  parallel: true
  analyzers:
    - RegexAnalyzer  # 仅使用内置分析器
```

---

## 下一步

- 查看 [README.md](../README.md) 了解更多使用方法
- 阅读 [PHASE2_COMPLETION.md](./PHASE2_COMPLETION.md) 了解静态分析引擎实现细节
- 配置CI/CD集成进行自动化安全扫描

---

**注意：** 即使没有安装外部分析器，HarmonySafeAgent的内置RegexAnalyzer也能提供基本的安全分析能力。但为了获得最佳结果，建议安装Clang-Tidy和Semgrep。
