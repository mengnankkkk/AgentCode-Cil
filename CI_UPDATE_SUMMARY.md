# CI/CD 更新摘要

## 🎯 目标
限制 CI 工作流为手动触发模式，只有在项目维护者批准后才能运行测试。

## ✅ 已完成的更改

### 1. 工作流配置修改
**文件**: `.github/workflows/test.yml`

- ✅ 将触发器从自动 (push/pull_request) 改为手动 (workflow_dispatch)
- ✅ 添加了两个可配置参数：
  - `run_e2e_tests`: 控制是否运行 E2E 测试（默认: true）
  - `run_performance_tests`: 控制是否运行性能测试（默认: false）
- ✅ E2E 测试步骤添加了条件判断
- ✅ 性能测试触发条件改为基于用户输入

### 2. 文档更新
**文件**: `README.md`

- ✅ 新增 "🔒 CI/CD 说明" 章节
- ✅ 添加手动触发 CI 的快速指南（5 步操作）
- ✅ 在贡献指南中添加 CI 测试说明
- ✅ 添加指向详细文档的链接

### 3. 新增文档文件

#### `.github/CI_MANUAL.md` - 完整手册
- 📖 详细的手动触发指南
- 🚀 GitHub 界面和 CLI 两种触发方式
- 🧪 测试类型详细说明
- 📊 工作流执行矩阵
- 💡 不同场景的最佳实践
- 🔧 故障排查指南

#### `.github/CI_QUICKSTART.md` - 快速启动
- ⚡ 一分钟快速上手指南
- 📋 测试选项对比表
- 🎯 使用场景推荐
- ⏱️ 预期运行时间

#### `.github/CHANGELOG_CI.md` - 变更日志
- 📝 详细的变更记录
- 📊 影响评估
- 🔄 回滚方案
- 💡 后续建议

## 🎛️ 工作流参数说明

| 参数 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `run_e2e_tests` | choice | `true` | 是否运行 E2E 测试（bzip2 和 ylong_runtime） |
| `run_performance_tests` | choice | `false` | 是否运行性能基准测试 |

## 📊 测试执行逻辑

```
手动触发 CI
    ↓
始终执行：单元测试 + 代码质量检查
    ↓
[run_e2e_tests = true?]
    ├─ Yes → 运行 bzip2 E2E 测试
    ├─ Yes → 运行 ylong_runtime E2E 测试
    └─ No  → 跳过 E2E 测试
    ↓
[run_performance_tests = true?]
    ├─ Yes → 运行性能基准测试
    └─ No  → 跳过性能测试
    ↓
生成测试报告和覆盖率报告
```

## 🚀 如何手动触发

### GitHub 网页界面（推荐给大多数用户）

1. 访问仓库页面
2. 点击 **Actions** 标签
3. 选择 **🚀 HarmonySafeAgent Tests** 工作流
4. 点击 **Run workflow** 按钮
5. 选择测试选项
6. 点击绿色的 **Run workflow** 按钮

### GitHub CLI（推荐给开发者）

```bash
# 安装 GitHub CLI（一次性操作）
brew install gh          # macOS
sudo apt install gh      # Ubuntu
choco install gh         # Windows

# 快速测试（仅单元测试）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=false \
  --field run_performance_tests=false

# 标准测试（含 E2E）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=true \
  --field run_performance_tests=false

# 完整测试（含性能）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=true \
  --field run_performance_tests=true
```

## 💡 使用建议

### 开发阶段
```bash
# 快速验证基本功能
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=false \
  --field run_performance_tests=false
```
⏱️ 预期时间：约 5 分钟

### PR 审查阶段
```bash
# 确保功能正确性
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=true \
  --field run_performance_tests=false
```
⏱️ 预期时间：约 20 分钟

### 发布前验证
```bash
# 全面测试
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=true \
  --field run_performance_tests=true
```
⏱️ 预期时间：约 30 分钟

## 📈 影响分析

### 正面影响 ✅
- 💰 **成本节省**：避免不必要的 AI API 调用
- ⚡ **资源优化**：减少 GitHub Actions 使用时间
- 🎛️ **灵活控制**：可选择性运行测试
- 🔒 **质量把控**：确保代码经审查后才测试

### 潜在影响 ⚠️
- ⏱️ **反馈延迟**：需要等待维护者手动触发
- 👥 **维护负担**：维护者需要主动管理 CI

### 缓解措施 🛡️
- 📖 提供详细的文档和快速指南
- 🔧 Fork 仓库可以自行手动触发测试
- ✅ 保留单元测试始终执行

## 🔄 如何恢复自动触发

如果需要恢复自动触发模式，编辑 `.github/workflows/test.yml`：

```yaml
on:
  workflow_dispatch:
    # ... 保留现有配置 ...
  push:                        # 取消注释这三行
    branches: [ main, develop ]
  pull_request:                # 取消注释这三行
    branches: [ main, develop ]
```

## 📚 相关文档

- 📘 [README.md](./README.md) - 项目主文档
- 🚀 [CI_QUICKSTART.md](.github/CI_QUICKSTART.md) - 快速启动指南
- 📖 [CI_MANUAL.md](.github/CI_MANUAL.md) - 完整手册
- 📝 [CHANGELOG_CI.md](.github/CHANGELOG_CI.md) - 详细变更日志

## 🆘 获取帮助

如有问题，请：
1. 查阅 [CI_MANUAL.md](.github/CI_MANUAL.md) 故障排查部分
2. 提交 [GitHub Issue](https://github.com/your-username/HarmonySafeAgent/issues)
3. 在 Issue 中提供工作流运行链接和错误信息

---

**更新日期**: 2024-10-24  
**分支**: chore-update-readme-restrict-ci-approval  
**状态**: ✅ 已完成
