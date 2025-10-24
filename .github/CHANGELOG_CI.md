# CI/CD 配置更改日志

## 📅 2024-10-24 - 限制 CI 为手动触发模式

### 🎯 更改目标
将 CI/CD 工作流从自动触发模式改为手动触发模式，确保所有测试需要项目维护者批准后才能执行。

### ✅ 主要更改

#### 1. 工作流配置 (`.github/workflows/test.yml`)

**更改前**：
```yaml
on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]
```

**更改后**：
```yaml
on:
  workflow_dispatch:
    inputs:
      run_e2e_tests:
        description: '运行 E2E 测试'
        required: false
        default: 'true'
        type: choice
        options:
          - 'true'
          - 'false'
      run_performance_tests:
        description: '运行性能基准测试'
        required: false
        default: 'false'
        type: choice
        options:
          - 'true'
          - 'false'
  # push:
  #   branches: [ main, develop ]
  # pull_request:
  #   branches: [ main, develop ]
```

**影响**：
- ❌ 自动触发器已被注释（push/pull_request）
- ✅ 添加了手动触发选项（workflow_dispatch）
- 🎛️ 提供了两个可配置参数来控制测试范围

#### 2. E2E 测试步骤条件化

**添加的条件**：
```yaml
- name: 🔄 Run E2E tests (bzip2)
  if: ${{ github.event.inputs.run_e2e_tests != 'false' }}
  run: mvn test -Dtest=Bzip2E2ETest
  continue-on-error: true

- name: 🦀 Run E2E tests (ylong_runtime)
  if: ${{ github.event.inputs.run_e2e_tests != 'false' }}
  run: mvn test -Dtest=YlongRuntimeE2ETest
  continue-on-error: true
```

**影响**：
- 🎯 E2E 测试现在可以选择性跳过
- ⚡ 快速验证时可以只运行单元测试
- 💰 节省 CI 运行时间和 AI API 调用成本

#### 3. 性能测试触发条件修改

**更改前**：
```yaml
if: github.event_name == 'push' && github.ref == 'refs/heads/main'
```

**更改后**：
```yaml
if: ${{ github.event.inputs.run_performance_tests == 'true' }}
```

**影响**：
- 🎚️ 性能基准测试现在完全由用户控制
- 🔒 不再自动在 main 分支执行
- 💡 可以在任何分支上手动触发性能测试

#### 4. README 更新

新增内容：
- 🔒 **CI/CD 说明**章节，解释手动触发策略
- 📖 **如何手动触发 CI** 快速指南
- 🔗 指向详细 CI 手册的链接
- 💡 **CI 测试说明**（在贡献指南中）

#### 5. 新增文档

创建了 `.github/CI_MANUAL.md`，包含：
- 📋 完整的手动触发指南
- 🚀 GitHub 界面和 CLI 两种触发方式
- 🧪 详细的测试类型说明
- 📊 工作流执行矩阵
- 💡 不同场景的最佳实践
- 🔧 故障排查指南

### 📊 影响评估

#### 正面影响 ✅
1. **成本控制**：避免每次 push/PR 都触发 AI API 调用
2. **资源优化**：减少 GitHub Actions 使用分钟数
3. **灵活性提升**：可以根据需要选择运行哪些测试
4. **质量控制**：确保只有审查过的代码才触发完整测试

#### 潜在影响 ⚠️
1. **开发者体验**：贡献者需要等待维护者手动触发测试
2. **反馈延迟**：不能立即获得 CI 结果
3. **维护负担**：维护者需要手动管理 CI 触发

#### 缓解措施 🛡️
1. 在 README 中清楚说明手动触发流程
2. 提供详细的 CI 手册供参考
3. Fork 仓库的开发者可以在自己的仓库中手动触发测试
4. 保留了单元测试和代码质量检查（总是执行）

### 🔄 回滚方案

如需恢复自动触发模式，取消注释以下行：

```yaml
on:
  workflow_dispatch:
    # ... 保留手动触发配置 ...
  push:                        # 取消注释
    branches: [ main, develop ] # 取消注释
  pull_request:                # 取消注释
    branches: [ main, develop ] # 取消注释
```

### 📝 后续建议

1. **环境保护规则**（可选）：
   - 在 GitHub Settings > Environments 创建 `production` 环境
   - 配置需要审批者列表
   - 将工作流配置为使用该环境

2. **通知机制**（可选）：
   - 配置 Slack/Discord webhook
   - 在 PR 上自动评论提醒维护者触发 CI

3. **定期审查**：
   - 每月检查 CI 使用情况
   - 评估是否需要调整触发策略

### 🔗 相关文档

- [README.md](../README.md) - 项目主文档（已更新）
- [CI_MANUAL.md](./CI_MANUAL.md) - CI/CD 详细指南（新增）
- [GitHub Actions 官方文档](https://docs.github.com/en/actions)

---

**变更人**: AI Agent  
**日期**: 2024-10-24  
**分支**: chore-update-readme-restrict-ci-approval
