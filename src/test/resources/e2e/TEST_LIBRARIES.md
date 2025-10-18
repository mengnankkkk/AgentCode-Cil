# OpenHarmony 分级测试库配置

用于HarmonySafe Agent的分级能力测试和性能评估。

## 📚 必选基准库

### bzip2 (C语言，全程贯穿)

**仓库**: https://sourceware.org/git/bzip2.git

**用途**:
- 能力评估基准
- 对标目标：bzip2-rs实现

**测试阶段**:
1. **初赛**：安全问题识别
   - 检测buffer overflow
   - 检测memory leak
   - 检测null pointer dereference

2. **复赛**：核心模块重写
   - 压缩/解压核心算法
   - 内存管理模块
   - 错误处理机制

3. **决赛**：完整迁移优化
   - 全量C-to-Rust迁移
   - FFI接口设计
   - 性能对比分析

**本地路径**: `src/test/resources/e2e/bzip2/`

---

## 🎯 分级测试库

### ★★☆ 难度：基础级

#### commonlibrary_c_utils
- **仓库**: https://github.com/openharmony/commonlibrary_c_utils
- **测试重点**: 基础内存安全
- **典型问题**:
  - Buffer overflow in string operations
  - Memory leak in allocation paths
  - Use-after-free in cleanup code
- **推荐起始库**: ✅ 适合快速验证基本功能

---

### ★★★ 难度：中等级

#### 1. hiviewdfx_hilog
- **仓库**: https://github.com/openharmony/hiviewdfx_hilog
- **测试重点**: 并发安全
- **典型问题**:
  - Race condition in log buffer
  - Thread-unsafe global variables
  - Deadlock in logging paths

#### 2. request_request
- **仓库**: https://github.com/openharmony/request_request
- **测试重点**: Rust unsafe优化
- **典型问题**:
  - Unsafe FFI boundaries
  - Raw pointer misuse
  - Memory aliasing violations
- **特殊性**: 已有Rust代码，重点是unsafe块优化

#### 3. isysevent
- **仓库**: https://gitee.com/openharmony/hiviewdfx_hisysevent
- **测试重点**: 跨语言优化
- **典型问题**:
  - FFI data race
  - Cross-language memory ownership
  - ABI compatibility issues

---

### ★★★★ 难度：困难级

#### 1. communication_ipc
- **仓库**: https://github.com/openharmony/communication_ipc
- **测试重点**: 生命周期管理
- **典型问题**:
  - Object lifetime violations
  - Reference counting bugs
  - Resource leak in IPC channels
- **复杂度**: 涉及进程间通信，状态机复杂

#### 2. ylong_runtime
- **仓库**: https://gitee.com/openharmony/commonlibrary_rust_ylong_runtime
- **测试重点**: 异步安全
- **典型问题**:
  - Async task cancellation safety
  - Future drop safety
  - Runtime shutdown race conditions
- **特殊性**: 纯Rust异步运行时，重点是异步安全模式

---

### ★★★★★ 难度：极难级

#### security_asset
- **仓库**: https://github.com/openharmony/security_asset
- **测试重点**: 混合架构设计
- **典型问题**:
  - C/Rust hybrid memory model
  - Security-critical code paths
  - TEE interaction safety
- **复杂度**: 最高，涉及安全敏感操作和混合语言架构
- **推荐用于**: 最终能力展示

---

## 📊 测试策略

### 阶段1: 基础验证（bzip2 + commonlibrary_c_utils）
- 目标：验证基本安全检测能力
- 预期：发现50+安全问题
- 时间：1-2天

### 阶段2: 中等难度（hiviewdfx_hilog + request_request）
- 目标：并发安全和FFI问题检测
- 预期：发现30+中等复杂度问题
- 时间：2-3天

### 阶段3: 高难度（communication_ipc + ylong_runtime）
- 目标：生命周期和异步安全分析
- 预期：发现20+复杂问题
- 时间：3-5天

### 阶段4: 极限挑战（security_asset）
- 目标：混合架构安全分析
- 预期：发现10+高价值安全问题
- 时间：5-7天

---

## 🎯 成功标准

### 基础能力（必须达成）
- ✅ bzip2分析成功，生成HTML报告
- ✅ 检测出主要内存安全问题（≥10个）
- ✅ AI增强功能正常工作（置信度≥0.8）
- ✅ Rust迁移建议生成成功

### 进阶能力（优先达成）
- 🎯 commonlibrary_c_utils完整分析
- 🎯 并发问题检测（hiviewdfx_hilog）
- 🎯 FFI安全分析（request_request）

### 高级能力（尽力达成）
- 🚀 异步安全分析（ylong_runtime）
- 🚀 混合架构分析（security_asset）
- 🚀 完整Rust迁移路线图生成

---

## 📝 测试记录模板

```yaml
test_session:
  library: "bzip2"
  date: "2025-10-18"
  version: "1.0.6"

  metrics:
    files_analyzed: 15
    total_issues: 42
    critical: 8
    high: 15
    medium: 12
    low: 7

  ai_enhancement:
    validated: 35
    filtered_fp: 7
    confidence_avg: 0.87

  performance:
    analysis_time: "12.5s"
    report_generation: "1.2s"
    memory_peak: "512MB"

  notes:
    - "Buffer overflow in bzlib.c:234 detected correctly"
    - "AI suggested Rust rewrite for compression core"
    - "HTML report visual quality excellent"
```

---

## 🔗 相关资源

- **bzip2-rs参考**: https://github.com/trifectatechfoundation/bzip2-rs
- **OpenHarmony文档**: https://docs.openharmony.cn/
- **Rust FFI指南**: https://doc.rust-lang.org/nomicon/ffi.html
- **安全编码标准**: https://wiki.sei.cmu.edu/confluence/display/c/SEI+CERT+C+Coding+Standard
