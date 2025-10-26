# 统一问题存储 - 快速参考

## 🚀 快速开始

### 在交互模式中使用

```bash
# 启动交互模式
$ ./harmony-agent.sh interactive

# 1. 执行分析
> /analyze src/cpp -l standard
✅ 发现 42 个问题，已添加到统一存储

# 2. 执行代码审查
> /review src/cpp
✅ 发现 15 个问题，已添加到统一存储
💡 新问题会自动与分析结果去重和合并

# 3. 生成统一报告
> /report -o analysis.json
✅ 报告已生成（包含52个合并后的问题）

# 4. 生成HTML报告
> /report -o report.html
✅ HTML报告已生成
✅ JSON报告也已生成（report.html.json）
```

---

## 📦 API 快速参考

### 基础操作

```java
// 获取 Store 实例（在交互模式中）
UnifiedIssueStore store = storeSession.getStore();

// 添加单个问题
store.addIssue(issue);

// 批量添加
store.addIssues(List<SecurityIssue> issues);

// 清空 Store
store.clear();
```

### 查询

```java
// 获取所有问题
List<SecurityIssue> all = store.getAllIssues();

// 按严重级别
List<SecurityIssue> critical = store.getIssuesBySeverity(IssueSeverity.CRITICAL);

// 按类别
List<SecurityIssue> buffers = store.getIssuesByCategory(IssueCategory.BUFFER_OVERFLOW);

// 按文件
List<SecurityIssue> fileIssues = store.getIssuesByFile("src/main.c");

// 按行号范围（用于 autofix）
List<SecurityIssue> nearby = store.getIssuesInRange("src/main.c", 40, 50);
```

### 统计

```java
// 按严重级别统计
Map<IssueSeverity, Long> stats = store.countBySeverity();

// 按类别统计
Map<IssueCategory, Long> categoryStats = store.countByCategory();

// 问题总数
int total = store.getTotalIssueCount();

// 是否有严重问题
boolean hasCritical = store.hasCriticalIssues();

// 诊断信息
String info = store.getStatistics();
// 输出: "Store Statistics: Total=52 issues, Files=3, Critical=3, High=8, ..."
```

### 导出

```java
// 转换为 ScanResult（用于报告生成）
ScanResult result = store.toScanResult(
    "src/cpp",  // 源路径
    List.of("Clang-Tidy", "Semgrep", "AI-Review")  // 分析器列表
);

// 使用 JsonReportWriter 导出 JSON
JsonReportWriter writer = new JsonReportWriter();
writer.write(result, Paths.get("report.json"));

// 使用 ReportGenerator 导出 HTML
ReportGenerator generator = new ReportGenerator();
generator.generate(result, Paths.get("report.html"));
```

---

## 🔑 关键特性

### 1. 自动去重

```
同一问题（相同位置和类别）不会被重复存储

示例：
- Clang-Tidy 发现 buffer overflow at src/main.c:42
- Semgrep 也发现 buffer overflow at src/main.c:42
- Store 中只保留 1 份记录
```

### 2. 智能合并

```
当同一问题来自多个分析器时，选择"更丰富"的版本

规则：
1. 如果来自 AI 审查 → 使用 AI 版本（通常有修复建议）
2. 否则 → 保持现有版本
3. 记录合并信息（来源、时间戳）
```

### 3. 上下文感知（待实现）

```
AutoFixOrchestrator 可以查询相邻问题：
- 修复一个 buffer overflow 时
- 自动检查附近是否有相关的 null pointer 问题
- 避免修复不完整或产生新的问题
```

---

## 📝 Store 内部结构

```
UnifiedIssueStore
├── issues: ConcurrentHashMap<String, SecurityIssue>
│   └── Key 格式: "CATEGORY:FILE_PATH:LINE:COLUMN"
│       示例: "BUFFER_OVERFLOW:src/main.c:42:10"
│
└── fileIndex: ConcurrentHashMap<String, List<String>>
    └── Key: 文件路径
        Value: 该文件中所有问题的哈希列表（加速按文件查询）
```

---

## 🔍 故障排查

### Q: 为什么 /report 显示 0 个问题？
**A**: 确保已执行 `/analyze` 或 `/review` 命令，并且分析发现了问题。

```bash
> /analyze src/
> /report -o report.json  # 只有在分析发现问题后才能生成报告
```

### Q: 为什么去重后问题数少了？
**A**: 这是正常的！不同的分析器可能发现相同的问题。Store 自动去重。

```
analyze: 50 issues
review: 20 issues
report: 55 issues  // 自动去重（去除重复的 15 个）
```

### Q: 能否在不同会话间共享数据？
**A**: 目前还不支持（待做）。每个交互会话独立。未来版本将支持会话持久化。

---

## 📈 性能建议

- **查询性能**:
  - `getIssuesByFile()` 使用文件索引，O(1)
  - `getIssuesInRange()` 需要扫描文件中的所有问题，O(m)
  - 总体上支持万级别问题的高效查询

- **内存消耗**:
  - 每个 SecurityIssue 约 1-2KB
  - 万个问题约 10-20MB（可接受）

---

## 🛠️ 开发者指南

### 添加新的分析器并写入 Store

```java
// 1. 执行分析
List<SecurityIssue> issues = yourAnalyzer.analyze(files);

// 2. 写入 Store
UnifiedIssueStore store = storeSession.getStore();
store.addIssues(issues);

// 完成！自动去重和合并
```

### 在命令中使用 Store

```java
public class MyCommand implements Callable<Integer> {
    private StoreSession storeSession;  // 从父命令注入

    public Integer call() {
        UnifiedIssueStore store = storeSession.getStore();

        // 执行操作
        List<SecurityIssue> nearby = store.getIssuesInRange(
            "src/main.c", 40, 50
        );

        // 处理结果
        ...
    }
}
```

### 扩展查询功能

```java
// 在 UnifiedIssueStore 中添加新的查询方法

public List<SecurityIssue> getIssuesByAnalyzer(String analyzer) {
    return issues.values().stream()
        .filter(i -> i.getAnalyzer().equals(analyzer))
        .collect(Collectors.toList());
}
```

---

## 📚 相关文件

| 文件 | 说明 |
|------|------|
| `UnifiedIssueStore.java` | 核心存储类 |
| `StoreSession.java` | 会话管理 |
| `InteractiveCommand.java` | /report 命令实现 |
| `UNIFIED_STORE_ARCHITECTURE.md` | 完整设计文档 |

---

**最后更新**: 2025-10-26
**版本**: 1.0
**状态**: 生产就绪（阶段1-2）
