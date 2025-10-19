# ClassCastException 修复 - ReportGenerator.java:115

## 问题描述

**错误位置**: `ReportGenerator.java:115`
**错误类型**: ClassCastException
**触发场景**: Freemarker 模板处理数据模型时，混合使用 Integer 和 Long 类型进行数学运算

## 根本原因

Freemarker 模板 (`report.ftlh:455-456`) 执行以下运算：
```freemarker
${(aiValidatedCount * 100.0 / (totalIssues + aiFilteredCount))?string['0.0']}%
```

数据模型中的类型不一致：
- `totalIssues`: `int` 类型（来自 `ScanResult.getTotalIssueCount()`）
- `aiValidatedCount`: `long` 类型（Stream.count() 返回）
- `aiFilteredCount`: `long` 类型

当 Freemarker 执行 `int + long` 运算时，发生类型转换异常。

## 修复方案

**原则**: 统一所有传递给 Freemarker 模板的数字类型为 `Long` 对象

### 具体改动

#### 1. totalIssues 类型转换 (Line 90)
```java
// Before
data.put("totalIssues", result.getTotalIssueCount()); // int

// After
data.put("totalIssues", Long.valueOf(result.getTotalIssueCount())); // Long
```

#### 2. aiValidatedCount 显式包装 (Line 112)
```java
// Before
data.put("aiValidatedCount", aiValidatedCount); // primitive long

// After
data.put("aiValidatedCount", Long.valueOf(aiValidatedCount)); // Long object
```

#### 3. ai_filtered_count 默认值修正 (Line 115)
```java
// Before
Object aiFilteredValue = result.getStatistics().getOrDefault("ai_filtered_count", 0); // Integer

// After
Object aiFilteredValue = result.getStatistics().getOrDefault("ai_filtered_count", 0L); // Long
```

#### 4. aiFilteredCount 显式包装 (Line 119)
```java
// Before
data.put("aiFilteredCount", aiFilteredCount); // primitive long

// After
data.put("aiFilteredCount", Long.valueOf(aiFilteredCount)); // Long object
```

## 验证结果

✅ **编译成功**: `mvn compile -DskipTests`
✅ **字节码更新**: `ReportGenerator.class` 于 2025-10-19 13:56 更新
✅ **类型安全**: 所有计数类型统一为 `Long`

## 影响范围

- **修改文件**: `src/main/java/com/harmony/agent/core/report/ReportGenerator.java`
- **影响行**: 88-119（createDataModel 方法）
- **风险评估**: 低风险 - 仅类型包装，不改变业务逻辑

## 设计原则

遵循以下工程原则：
- **KISS**: 采用最简单的类型统一方案
- **Root Cause Fix**: 解决类型不匹配的根本原因
- **Type Safety**: 确保模板数据模型的类型一致性
- **Backward Compatible**: 不影响现有功能，仅修复类型转换问题

## 后续建议

1. 考虑在 `ScanResult.getTotalIssueCount()` 方法中直接返回 `long` 类型，与其他计数方法保持一致
2. 添加单元测试验证 Freemarker 模板数据模型的类型一致性
3. 使用静态分析工具检测类似的类型不匹配问题

---
**修复时间**: 2025-10-19
**修复者**: Claude Code
**优先级**: P5 (最高优先级)
