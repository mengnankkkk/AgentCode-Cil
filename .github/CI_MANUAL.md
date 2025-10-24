# CI/CD 手动触发指南

## 📋 概述

HarmonySafeAgent 采用**手动触发**的 CI/CD 流程，所有测试需要项目维护者手动批准后才会执行。

## 🎯 为什么使用手动触发？

1. **成本控制**：避免频繁的 AI API 调用产生不必要的费用
2. **资源优化**：测试包含大量 E2E 和性能基准测试，需要较长运行时间
3. **质量把控**：确保只有经过代码审查的更改才会触发完整测试套件
4. **灵活性**：可以选择性地运行特定类型的测试

## 🚀 如何手动触发 CI

### 方式 1：通过 GitHub Actions 界面

1. 打开项目的 GitHub 仓库
2. 点击顶部导航栏的 **Actions** 标签
3. 在左侧工作流列表中选择 **🚀 HarmonySafeAgent Tests**
4. 点击右上角的 **Run workflow** 下拉按钮
5. 在弹出的对话框中：
   - **Use workflow from**: 选择要测试的分支（默认：main）
   - **运行 E2E 测试**: 选择 `true`（运行）或 `false`（跳过）
   - **运行性能基准测试**: 选择 `true`（运行）或 `false`（跳过）
6. 点击绿色的 **Run workflow** 按钮开始执行

### 方式 2：通过 GitHub CLI

```bash
# 安装 GitHub CLI (如果还没安装)
# macOS: brew install gh
# Ubuntu: sudo apt install gh
# Windows: choco install gh

# 认证
gh auth login

# 触发工作流（运行所有测试）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=true \
  --field run_performance_tests=true

# 仅运行单元测试（跳过 E2E 和性能测试）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=false \
  --field run_performance_tests=false

# 在特定分支上触发
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --ref develop \
  --field run_e2e_tests=true
```

## 🧪 测试类型说明

### 1. 单元测试（必选）
- **自动运行**：无论如何选择，单元测试总是会执行
- **测试范围**：
  - `PersistentCacheManagerTest` - 持久化缓存管理器测试
  - `DecisionEngineIntegrationTest` - AI 决策引擎集成测试
- **运行时间**：约 1-2 分钟

### 2. E2E 测试（可选）
- **触发条件**：`run_e2e_tests = true`
- **测试范围**：
  - `Bzip2E2ETest` - bzip2 压缩工具端到端测试
  - `YlongRuntimeE2ETest` - ylong_runtime OpenHarmony 运行时测试
- **运行时间**：约 10-15 分钟
- **注意**：可能需要 AI API 密钥（如果启用 AI 分析）

### 3. 性能基准测试（可选）
- **触发条件**：`run_performance_tests = true`
- **测试范围**：
  - 分析性能基准测试
  - 报告生成性能测试
  - 内存使用情况测试
- **运行时间**：约 5-10 分钟

### 4. 代码质量检查（必选）
- **自动运行**：总是执行
- **检查项目**：
  - Checkstyle 代码风格检查
  - SpotBugs 静态分析
  - PMD 代码质量检查

## 📊 工作流执行矩阵

| Java 版本 | 单元测试 | E2E 测试 | 性能测试 | 代码质量 |
|----------|---------|---------|---------|---------|
| Java 17  | ✅ 必选 | 🔘 可选 | 🔘 可选 | ✅ 必选 |
| Java 21  | ✅ 必选 | 🔘 可选 | 🔘 可选 | ✅ 必选 |

## 🔍 查看测试结果

### 实时查看
1. 在 Actions 页面点击正在运行的工作流
2. 点击具体的 Job（如 `build-and-test`）
3. 展开每个 Step 查看详细日志

### 下载测试报告
1. 工作流完成后，在页面底部的 **Artifacts** 部分可以下载：
   - `test-results-java-17` - Java 17 测试结果
   - `test-results-java-21` - Java 21 测试结果
   - `coverage-report-java-17` - Java 17 覆盖率报告
   - `coverage-report-java-21` - Java 21 覆盖率报告
2. 报告保留期：30 天

## 💡 最佳实践

### 开发阶段
```bash
# 仅运行单元测试，快速验证基本功能
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=false \
  --field run_performance_tests=false
```

### 合并前检查
```bash
# 运行完整测试套件（包括 E2E，不含性能测试）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=true \
  --field run_performance_tests=false
```

### 发布前验证
```bash
# 运行所有测试（包括性能基准测试）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=true \
  --field run_performance_tests=true
```

## ⚙️ 环境变量配置

CI 工作流可能需要以下环境变量（在 GitHub Settings > Secrets and variables > Actions 中配置）：

- `OPENAI_API_KEY` - OpenAI API 密钥（用于 AI 增强测试）
- `DEEPSEEK_API_KEY` - DeepSeek API 密钥（备选 AI 服务）
- `CODECOV_TOKEN` - Codecov 上传令牌（可选）

## 🔧 故障排查

### 问题：工作流无法触发
**解决方案**：
- 确保你有仓库的 write 权限
- 检查分支是否存在
- 确认工作流文件格式正确

### 问题：E2E 测试失败
**可能原因**：
- AI API 密钥未配置或已失效
- 测试依赖的外部工具（clang, semgrep）安装失败
- 网络问题导致 API 调用超时

**解决方案**：
- 检查 GitHub Secrets 中的 API 密钥配置
- 查看详细日志中的错误信息
- 考虑使用 `--no-ai` 标志跳过 AI 相关测试

### 问题：性能测试结果波动
**说明**：
- GitHub Actions 的运行环境可能存在性能差异
- 性能基准测试设置了 `continue-on-error: true`
- 测试失败不会阻止工作流完成

## 📞 联系支持

如有 CI/CD 相关问题，请：
1. 查看工作流日志获取详细错误信息
2. 在 GitHub Issues 中提交问题
3. 提供工作流运行链接和错误信息

---

**最后更新**：2024-10-24
