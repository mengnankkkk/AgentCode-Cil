# 🚀 CI 快速启动指南

## 一分钟快速触发 CI

### 方式 1：GitHub 网页界面

```
1. 点击仓库顶部的 "Actions" 标签
2. 左侧选择 "🚀 HarmonySafeAgent Tests"
3. 点击 "Run workflow" ▼ 下拉按钮
4. 选择测试选项后点击绿色 "Run workflow" 按钮
```

### 方式 2：GitHub CLI（推荐给高级用户）

```bash
# 快速开发测试（仅单元测试）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=false \
  --field run_performance_tests=false

# 完整测试（包含 E2E）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=true \
  --field run_performance_tests=false

# 全面测试（包含性能基准）
gh workflow run "🚀 HarmonySafeAgent Tests" \
  --field run_e2e_tests=true \
  --field run_performance_tests=true
```

## 📋 测试选项说明

| 选项 | 默认值 | 说明 | 运行时间 |
|------|--------|------|----------|
| 运行 E2E 测试 | `true` | bzip2 和 ylong_runtime 端到端测试 | ~10-15 分钟 |
| 运行性能基准测试 | `false` | 性能和内存使用基准测试 | ~5-10 分钟 |

> 💡 **提示**：单元测试和代码质量检查始终会运行，无法跳过。

## 🎯 使用场景推荐

| 场景 | E2E 测试 | 性能测试 | 理由 |
|------|---------|---------|------|
| 日常开发 | ❌ false | ❌ false | 快速反馈，节省时间 |
| PR 审查 | ✅ true | ❌ false | 确保功能正确性 |
| 发布前验证 | ✅ true | ✅ true | 全面验证 |

## ⏱️ 预期运行时间

- **最快**（仅单元测试）：约 5 分钟
- **标准**（含 E2E）：约 20 分钟
- **完整**（含性能测试）：约 30 分钟

## 📥 查看结果

1. **实时查看**：在 Actions 页面点击运行中的工作流
2. **下载报告**：完成后在 Artifacts 部分下载测试结果和覆盖率报告
3. **保留期**：测试报告保留 30 天

## 🆘 需要帮助？

- 📖 详细文档：[CI_MANUAL.md](./CI_MANUAL.md)
- 📝 更改日志：[CHANGELOG_CI.md](./CHANGELOG_CI.md)
- 🐛 问题反馈：[GitHub Issues](https://github.com/your-username/HarmonySafeAgent/issues)

---

最后更新：2024-10-24
