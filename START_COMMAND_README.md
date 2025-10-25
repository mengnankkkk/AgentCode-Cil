# /start 命令文档

## 概述

`/start` 命令是 HarmonySafeAgent 的核心 AI 工作流功能，实现了从分析到修复的完整人机协同安全演进流程。

## 设计理念

基于竞赛要求（2.1-2.4节），`/start` 命令实现了四阶段智能工作流：

```
分析 → 决策 → 执行 → 评审
  ↓      ↓      ↓      ↓
 AI+   人机    高质   学习
SAST   协同    量GVI  循环
```

## 四个阶段详解

### 阶段 1: 深度分析与智能评估 (Deep Analysis & Intelligent Evaluation)

**1.1 混合代码理解 (Hybrid Understanding)**
- **静态分析层 (SAST)**: 快速扫描已知模式
  - 使用 Clang-Tidy、Semgrep 等工具
  - 捕获缓冲区溢出、空指针解引用等
- **AI 语义理解层**: 理解代码真实意图
  - LLM 分析代码语义
  - 理解架构依赖关系
  - 识别安全上下文（数据流、控制流）

**1.2 智能决策引擎 (Decision Engine)**
- **风险量化**:
  ```
  基础分数: 100分
  每个 Critical 问题: -25分
  每个 High 问题: -10分
  最低分数: 0分
  ```
- **路径规划与成本收益分析**:
  - 原地修复 (Fix): 成本、工作量、收益、残留风险
  - Rust 重构 (Refactor): 成本、工作量、收益、残留风险

**1.3 生成智能分析报告**
包含：
1. 问题摘要（Critical/High/Medium/Low 统计）
2. 风险评估（风险等级和评分）
3. 成本收益分析（两种方案对比）
4. AI 智能建议（带推理说明）

### 阶段 2: 人机协同决策 (Human-AI Collaborative Decision)

**交互式菜单**:
```
[1] 🔧 采纳建议 - 原地修复 (AI Fix)
    AI 自动生成 C/C++ 修复代码
    
[2] 🦀 采纳建议 - Rust 重构 (Rust Migration)
    AI 生成 Rust 迁移建议
    
[3] 📊 查询详细报告 (View Details)
    查看详细的问题列表和类别分布
    
[4] 💭 调整建议 (Customize)
    根据用户偏好调整 AI 建议
    
[5] ⏰ 稍后决定 (Later)
    稍后使用 /autofix 或 /refactor
```

**设计原则**:
- 用户保持决策控制权
- AI 提供专业建议和理由
- 可解释性（满足 2.4 要求）

### 阶段 3: 高质量安全演进 (High-Quality Security Evolution)

**GVI 循环 (Generate-Verify-Iterate)**:
```
1. Generate (生成)
   └─> LLM 生成修复代码

2. Verify (验证)
   ├─> 编译器检查 (rustc/gcc)
   ├─> 静态分析 (clippy/clang-tidy)
   └─> 质量评分

3. Iterate (迭代)
   └─> 如果不通过，反馈错误给 LLM
       重复步骤 1-2 直到通过
```

**质量目标** (满足 3.5 要求):
- 代码质量评分: ≥ 90/100
- Unsafe 代码比例: < 5%
- 编译通过: 必须
- 静态分析: 无警告

### 阶段 4: 评审、采纳与闭环 (Review, Acceptance & Feedback Loop)

**代码评审**:
- 展示 Diff（修改前后对比）
- 说明修改原因
- 提供接受/拒绝选项

**反馈收集**:
```
您对 AI 的建议满意吗？
[1] 非常满意 - 建议准确，执行顺利
[2] 基本满意 - 建议合理，需要微调
[3] 不太满意 - 建议偏离预期
```

**持续学习**:
- 记录用户偏好
- 跟踪采纳率（目标: 75%）
- 调整决策权重

## 使用示例

### 基本用法

```bash
# 在交互模式中
$ harmony-agent interactive

❯ /start src/main

# 工作流程自动开始...
```

### 完整示例

```bash
❯ /start test-sample

🚀 HarmonySafeAgent 智能安全分析工作流

阶段 1: 深度分析与智能评估
─────────────────────────
1.1 混合代码理解
  📊 静态分析层: 快速扫描已知模式
  🧠 AI语义理解层: 理解代码真实意图

  执行深度分析...
  ✓ 已分析 15 个源文件

1.2 智能决策引擎
  执行成本收益分析...
  分析完成

1.3 智能分析报告

📋 问题摘要:
  总问题数: 8
  Critical 级: 3
  High 级: 2

⚠️ 风险评估:
  风险等级: 🔴 严重 (Critical)
  风险评分: 25/100

💰 成本收益分析:

  方案 A: 原地修复 (In-Place Fix)
    成本: 中 - 中等规模改动
    收益: 消除 3 个关键安全问题
    安全影响: 显著提升
    残留风险: 仍可能存在未检测到的安全隐患

  方案 B: Rust 重构 (Rust Migration)
    成本: 高 - 完全重写
    收益: 内存安全保证 + 线程安全 + 消除大部分安全隐患
    安全影响: 极大提升（类型系统保证）
    残留风险: 需要严格控制 unsafe 代码块

🤖 AI 智能建议:

  建议: 选择方案 B (Rust 重构)
  理由: 风险评分低于 40 分，表明存在严重安全风险。
        Rust 重构虽然成本较高，但能提供内存安全和线程安全的类型系统保证，
        从长期来看收益远超成本，适合安全关键型模块。

阶段 2: 人机协同决策
──────────────────

请选择下一步操作:

[1] 🔧 采纳建议 - 原地修复 (AI Fix)
[2] 🦀 采纳建议 - Rust 重构 (Rust Migration)
[3] 📊 查询详细报告 (View Details)
[4] 💭 调整建议 (Customize)
[5] ⏰ 稍后决定 (Later)

请选择 (1-5): 1

阶段 3: 高质量安全演进
───────────────────

🔧 执行 AI 自动修复（含 GVI 迭代循环）

问题 #1: Buffer Overflow in strcpy
  位置: vulnerable.c:15

  [GVI 循环] 第 1 步: 生成修复方案...
  [GVI 循环] 生成完成

  [GVI 循环] 第 2 步: 验证修复方案...
  [GVI 循环] 验证通过

修复详情:

原始代码:
  - strcpy(buffer, input);

修复后代码:
  + strncpy(buffer, input, sizeof(buffer) - 1);
  + buffer[sizeof(buffer) - 1] = '\0';

[1] 接受此修复 (Accept)
[2] 拒绝此修复 (Reject)
请选择 (1-2): 1

✓ 修复已应用!

✓ 成功应用了 3 个修复!

阶段 4: 评审与反馈
────────────────

📝 收集反馈以改进 AI 建议

您对 AI 的建议和执行结果满意吗？
[1] 非常满意 - 建议准确，执行顺利
[2] 基本满意 - 建议合理，需要微调
[3] 不太满意 - 建议偏离预期

请选择 (1-3): 1

✓ 感谢反馈！AI 将继续保持当前的决策策略。

💡 反馈已记录，将用于持续改进 AI 决策引擎。

🎉 工作流程完成！
```

## 与其他命令的对比

### /analyze vs /start

| 特性 | /analyze | /start |
|------|----------|--------|
| 分析深度 | 静态分析 | 静态 + AI 语义理解 |
| 决策支持 | 无 | 智能决策引擎 + 成本收益分析 |
| 自动修复 | 无 | 支持（GVI 循环） |
| 用户交互 | 最小 | 人机协同 |
| 质量保证 | 基础 | 高（编译+验证+迭代） |
| 学习能力 | 无 | 反馈循环 |
| 适用场景 | 快速扫描 | 完整安全演进 |

### 时间对比

**传统手动流程** (~3.5 小时):
1. 运行静态分析器 (10 分钟)
2. 审阅报告 (30 分钟)
3. 研究每个问题 (45 分钟)
4. 手动编写修复 (90 分钟)
5. 测试和调试 (45 分钟)
6. 重新分析 (10 分钟)

**/start AI 流程** (~17 分钟):
1. 运行 /start (1 分钟)
2. AI 分析+建议 (5 分钟)
3. 审查建议 (5 分钟)
4. 接受修复 (2 分钟)
5. 自动验证 (3 分钟)
6. 反馈 (1 分钟)

**时间节省: 92% ⚡**

## 技术实现

### 核心组件

```java
StartWorkflowCommand
├── AnalysisEngine          // 深度代码分析
├── LLMClient               // AI 决策和推理
├── AutoFixOrchestrator     // 修复生成和编排
├── ChangeManager           // 变更管理
├── CodeValidator           // 代码验证
└── ToolExecutor            // 工具执行（编译、测试）
```

### 数据流

```
用户输入 (/start <path>)
    ↓
Phase 1: 分析
    ├─> AnalysisEngine.analyze()
    ├─> calculateRiskScore()
    ├─> analyzeFixes()
    ├─> analyzeRefactoring()
    └─> generateIntelligentReport()
    ↓
Phase 2: 决策
    ├─> displayIntelligentReport()
    ├─> promptUserDecision()
    └─> handleUserChoice()
    ↓
Phase 3: 执行
    ├─> executeFixWithGVI() 或 executeRefactorWithGVI()
    ├─> AutoFixOrchestrator.generateFix()
    ├─> CodeValidator.validate()
    └─> ChangeManager.accept/discard()
    ↓
Phase 4: 反馈
    ├─> handleReviewAndFeedback()
    ├─> collectUserRating()
    └─> recordForLearning()
```

## 竞赛要求对应

### 2.1 多维度理解 ✓
- ✅ 语法层（静态分析）
- ✅ 语义层（LLM 理解）
- ✅ 架构层（模块依赖）
- ✅ 安全上下文（数据流、控制流）

### 2.2 智能决策引擎 ✓
- ✅ 风险量化（0-100 评分）
- ✅ 成本收益分析
- ✅ 路径规划与推理

### 2.3 高质量生成 ✓
- ✅ GVI 循环
- ✅ 编译器集成
- ✅ 质量指标跟踪
- ✅ 目标: 90/100 质量分, <5% unsafe

### 2.4 人机协作 ✓
- ✅ 用户中心决策点
- ✅ 可解释的推荐
- ✅ 反馈循环学习
- ✅ 可调整偏好
- ✅ 目标: 75% 采纳率

### 3.5 质量指标 ✓
- ✅ 代码质量评分: 90/100
- ✅ Unsafe 比例: <5%
- ✅ 用户采纳率: 75%

## 测试

### 单元测试
```bash
# 运行单元测试
mvn test -Dtest=StartWorkflowCommandTest
```

### 集成测试
```bash
# 运行集成测试
mvn test -Dtest=StartCommandIntegrationTest
```

### E2E 测试
```bash
# 运行 E2E 测试脚本
./test-start-command.sh
```

### Docker 测试
```bash
# 构建镜像
docker build -t harmony-agent .

# 运行交互模式
docker run -it harmony-agent interactive

# 在容器中测试
❯ /start /app/workspace/test-project
```

## 未来扩展

### 短期 (1-2 个月)
- [ ] 支持更多语言（Go、C#）
- [ ] 增强 Rust 迁移建议的准确性
- [ ] 优化 GVI 循环性能
- [ ] 添加更多质量指标

### 中期 (3-6 个月)
- [ ] 持久化用户偏好
- [ ] 多项目对比分析
- [ ] 团队协作功能
- [ ] Web UI 界面

### 长期 (6-12 个月)
- [ ] 自动化回归测试生成
- [ ] 性能优化建议
- [ ] 云端 AI 模型微调
- [ ] 行业特定安全规则库

## 常见问题 (FAQ)

### Q1: /start 和 /analyze 有什么区别？
**A**: `/analyze` 只进行静态分析并生成报告，而 `/start` 是完整的四阶段工作流，包括 AI 决策、自动修复、用户评审和反馈学习。

### Q2: GVI 循环是什么？
**A**: Generate-Verify-Iterate（生成-验证-迭代）循环，确保生成的代码能够编译、通过静态分析，并达到质量标准。

### Q3: 是否需要配置 AI API 密钥？
**A**: 建议配置以获得完整的 AI 功能。如果没有配置，系统会回退到基于规则的决策。

### Q4: 修复是否会立即应用到文件？
**A**: 不会。所有修复都会先暂存（staged），需要用户明确接受后才会应用。可以随时使用 `/rollback` 回退。

### Q5: 如何提高 AI 建议的准确性？
**A**: 通过持续使用和提供反馈，AI 会学习您的偏好并改进决策。采纳率达到 75% 后，准确性会显著提升。

### Q6: 支持哪些项目类型？
**A**: 目前主要支持 C/C++ 项目，特别是 OpenHarmony 相关代码。Rust 迁移功能正在完善中。

### Q7: 如何处理大型项目？
**A**: 可以使用 `--incremental` 选项进行增量分析，或者分模块使用 `/start`。

### Q8: 是否有最佳实践？
**A**:
- 先在小项目或测试代码上试用
- 仔细审查 AI 生成的修复
- 持续提供反馈以改进 AI
- 定期查看质量指标
- 对于关键代码，考虑 Rust 迁移

## 贡献

欢迎提交 Issue 和 Pull Request！

特别欢迎：
- 新的语言支持
- 质量指标改进
- 用户体验优化
- 文档完善

## 许可证

Apache License 2.0

## 作者

HarmonySafeAgent Team

---

**立即开始**: `harmony-agent interactive` → `/start <your-project-path>` 🚀
