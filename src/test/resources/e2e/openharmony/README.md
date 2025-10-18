# OpenHarmony 分级测试库使用指南

本目录用于存放OpenHarmony库的分级能力测试。

## 📁 目录结构

```
openharmony/
├── commonlibrary_c_utils/     (★★☆ 基础级)
├── hiviewdfx_hilog/           (★★★ 中等级)
├── request_request/           (★★★ 中等级)
├── hisysevent/                (★★★ 中等级)
├── communication_ipc/         (★★★★ 困难级)
├── ylong_runtime/             (★★★★ 困难级)
└── security_asset/            (★★★★★ 极难级)
```

## 🚀 快速开始

### 1. 克隆测试库

```bash
# 进入OpenHarmony库目录
cd src/test/resources/e2e/openharmony

# ★★☆ 基础级 - 推荐首选
git clone https://github.com/openharmony/commonlibrary_c_utils

# ★★★ 中等级
git clone https://github.com/openharmony/hiviewdfx_hilog
git clone https://github.com/openharmony/request_request
git clone https://gitee.com/openharmony/hiviewdfx_hisysevent hisysevent

# ★★★★ 困难级
git clone https://github.com/openharmony/communication_ipc
git clone https://gitee.com/openharmony/commonlibrary_rust_ylong_runtime ylong_runtime

# ★★★★★ 极难级
git clone https://github.com/openharmony/security_asset
```

### 2. 生成编译数据库

每个库都需要`compile_commands.json`文件用于Clang-Tidy分析。

**方法1: 使用Bear（推荐）**
```bash
cd <library_directory>
bear -- make
```

**方法2: 使用CMake**
```bash
cd <library_directory>
mkdir build && cd build
cmake -DCMAKE_EXPORT_COMPILE_COMMANDS=ON ..
cp compile_commands.json ..
```

**方法3: 手动创建（小型项目）**
```bash
# 参考 bzip2/compile_commands.json 的格式
```

### 3. 运行分级测试

```bash
# 回到项目根目录
cd E:/github/HarmonySafeAgent

# 运行特定难度级别的测试
mvn test -Dtest=GradedLibraryTest#testBasicLevel_CUtils
mvn test -Dtest=GradedLibraryTest#testMediumLevel_Hilog
mvn test -Dtest=GradedLibraryTest#testHardLevel_IPC

# 运行所有分级测试
mvn test -Dtest=GradedLibraryTest
```

## 📊 测试库详情

### ★★☆ 基础级

#### commonlibrary_c_utils
- **仓库**: https://github.com/openharmony/commonlibrary_c_utils
- **测试重点**: 基础内存安全
- **典型问题**:
  - Buffer overflow in string operations
  - Memory leak in allocation paths
  - Use-after-free in cleanup code
- **预期检测**: ≥10个安全问题
- **推荐用途**: ✅ 快速验证基本功能

---

### ★★★ 中等级

#### 1. hiviewdfx_hilog
- **仓库**: https://github.com/openharmony/hiviewdfx_hilog
- **测试重点**: 并发安全
- **典型问题**:
  - Race condition in log buffer
  - Thread-unsafe global variables
  - Deadlock in logging paths
- **预期检测**: ≥15个安全问题

#### 2. request_request
- **仓库**: https://github.com/openharmony/request_request
- **测试重点**: Rust unsafe优化
- **典型问题**:
  - Unsafe FFI boundaries
  - Raw pointer misuse
  - Memory aliasing violations
- **特殊性**: 已有Rust代码，重点是unsafe块优化
- **预期检测**: ≥10个安全问题

#### 3. hisysevent
- **仓库**: https://gitee.com/openharmony/hiviewdfx_hisysevent
- **测试重点**: 跨语言优化
- **典型问题**:
  - FFI data race
  - Cross-language memory ownership
  - ABI compatibility issues
- **预期检测**: ≥12个安全问题

---

### ★★★★ 困难级

#### 1. communication_ipc
- **仓库**: https://github.com/openharmony/communication_ipc
- **测试重点**: 生命周期管理
- **典型问题**:
  - Object lifetime violations
  - Reference counting bugs
  - Resource leak in IPC channels
- **复杂度**: 涉及进程间通信，状态机复杂
- **预期检测**: ≥20个安全问题

#### 2. ylong_runtime
- **仓库**: https://gitee.com/openharmony/commonlibrary_rust_ylong_runtime
- **测试重点**: 异步安全
- **典型问题**:
  - Async task cancellation safety
  - Future drop safety
  - Runtime shutdown race conditions
- **特殊性**: 纯Rust异步运行时，重点是异步安全模式
- **预期检测**: ≥15个安全问题

---

### ★★★★★ 极难级

#### security_asset
- **仓库**: https://github.com/openharmony/security_asset
- **测试重点**: 混合架构设计
- **典型问题**:
  - C/Rust hybrid memory model
  - Security-critical code paths
  - TEE interaction safety
- **复杂度**: 最高，涉及安全敏感操作和混合语言架构
- **推荐用于**: 最终能力展示
- **预期检测**: ≥25个安全问题

---

## 🎯 测试策略

### 阶段1: 基础验证
- **目标**: 验证基本安全检测能力
- **库**: bzip2 + commonlibrary_c_utils
- **预期**: 发现50+安全问题
- **时间**: 1-2天

### 阶段2: 中等难度
- **目标**: 并发安全和FFI问题检测
- **库**: hiviewdfx_hilog + request_request
- **预期**: 发现30+中等复杂度问题
- **时间**: 2-3天

### 阶段3: 高难度
- **目标**: 生命周期和异步安全分析
- **库**: communication_ipc + ylong_runtime
- **预期**: 发现20+复杂问题
- **时间**: 3-5天

### 阶段4: 极限挑战
- **目标**: 混合架构安全分析
- **库**: security_asset
- **预期**: 发现10+高价值安全问题
- **时间**: 5-7天

---

## ✅ 成功标准

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
  library: "commonlibrary_c_utils"
  date: "2025-10-18"
  difficulty: "★★☆"

  metrics:
    files_analyzed: 25
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
    analysis_time: "18.5s"
    report_generation: "1.5s"
    memory_peak: "768MB"

  notes:
    - "Buffer overflow detected correctly"
    - "AI suggested memory management improvements"
    - "HTML report quality excellent"
```

---

## 🔧 故障排查

### 问题1: compile_commands.json缺失

**症状**: 测试失败，提示"compile_commands.json not found"

**解决方案**:
```bash
cd src/test/resources/e2e/openharmony/<library_name>

# 方案A: 使用Bear
bear -- make

# 方案B: 使用CMake
mkdir build && cd build
cmake -DCMAKE_EXPORT_COMPILE_COMMANDS=ON ..
cp compile_commands.json ..
```

### 问题2: 库克隆失败

**症状**: git clone报错或网络超时

**解决方案**:
```bash
# 使用gitee镜像（中国境内更快）
git clone https://gitee.com/openharmony/<library_name>

# 或使用浅克隆节省时间和空间
git clone --depth 1 <git_url>
```

### 问题3: 分析耗时过长

**症状**: 测试超过预期时间

**解决方案**:
1. 检查AI API配置（API延迟可能很高）
2. 使用`--no-ai`禁用AI增强进行基础测试
3. 减少并发线程数：`--max-threads 2`

---

## 🔗 相关资源

- **OpenHarmony文档**: https://docs.openharmony.cn/
- **bzip2-rs参考**: https://github.com/trifectatechfoundation/bzip2-rs
- **Rust FFI指南**: https://doc.rust-lang.org/nomicon/ffi.html
- **安全编码标准**: https://wiki.sei.cmu.edu/confluence/display/c/SEI+CERT+C+Coding+Standard
- **Bear工具**: https://github.com/rizsotto/Bear
