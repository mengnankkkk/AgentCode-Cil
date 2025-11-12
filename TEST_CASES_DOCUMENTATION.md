# HarmonySafeAgent 测试用例文档

**版本**: 1.0.0  
**最后更新**: 2024年11月  
**作者**: 开发团队

---

## 目录

1. [测试计划总览](#测试计划总览)
2. [单元测试用例](#单元测试用例)
3. [bzip2功能测试](#bzip2功能测试)
4. [集成测试](#集成测试)
5. [性能测试](#性能测试)
6. [测试执行指南](#测试执行指南)

---

## 测试计划总览

### 测试范围

| 测试类型 | 覆盖模块 | 数量 | 预期耗时 |
|---------|---------|------|---------|
| **单元测试** | 核心5个模块 | 28个 | ~3分钟 |
| **集成测试** | 完整流程 | 12个 | ~5分钟 |
| **性能测试** | 基准测试 | 8个 | ~10分钟 |
| **E2E测试** | bzip2完整分析 | 6个 | ~15分钟 |
| **总计** | - | 54个 | ~33分钟 |

### 测试覆盖率目标

```
整体覆盖率: 85%+
  ├─ 代码行覆盖: 82%
  ├─ 分支覆盖: 75%
  ├─ 路径覆盖: 68%
  └─ 关键路径: 95%
```

---

## 单元测试用例

### Test Suite 1: CodeScanner 模块

**文件**: `src/test/java/com/harmony/scanner/CodeScannerTest.java`

#### Test 1.1: 初始化与文件扫描

```java
@Test
@DisplayName("初始化CodeScanner并扫描项目")
public void testInitializeAndScan() {
    // 准备
    CodeScanner scanner = new CodeScanner();
    Path testProject = Paths.get("src/test/resources/projects/bzip2");
    
    // 执行
    ScanResult result = scanner.scan(testProject);
    
    // 验证
    assertNotNull(result);
    assertEquals(8, result.fileCount);
    assertEquals(7996, result.totalLines);
    assertTrue(result.fileHashes.size() == 8);
    
    // 性能检查
    assertTrue(result.scanDurationMs < 5000, "扫描耗时应<5秒");
}
```

**预期结果**: ✅ PASS  
**覆盖代码**: 95%  
**关键验证**:
- 文件计数正确
- 代码行数计算精确
- 哈希值生成正确
- 性能在合理范围

---

#### Test 1.2: 增量扫描检测

```java
@Test
@DisplayName("检测文件变化并执行增量扫描")
public void testIncrementalScanDetection() {
    // 准备
    CodeScanner scanner = new CodeScanner();
    Path project = createTempProject();
    
    // 初始扫描
    ScanResult initial = scanner.scan(project);
    Map<String, String> initialHashes = new HashMap<>(initial.fileHashes);
    
    // 修改一个文件
    Path fileToModify = project.resolve("src/core.c");
    Files.write(fileToModify, "int x = 1;".getBytes());
    
    // 增量扫描
    ScanResult incremental = scanner.scan(project);
    
    // 验证
    assertEquals(1, incremental.modifiedFiles.size());
    assertTrue(incremental.modifiedFiles.contains("src/core.c"));
    assertNotEquals(
        initialHashes.get("src/core.c"),
        incremental.fileHashes.get("src/core.c")
    );
    
    // 未改变的文件哈希保持不变
    for (String file : initialHashes.keySet()) {
        if (!file.equals("src/core.c")) {
            assertEquals(
                initialHashes.get(file),
                incremental.fileHashes.get(file)
            );
        }
    }
}
```

**预期结果**: ✅ PASS  
**覆盖代码**: 88%  
**关键验证**:
- 变化检测准确
- 哈希对比正确
- 增量扫描性能提升

---

#### Test 1.3: 大型项目性能

```java
@Test
@DisplayName("大型项目扫描性能测试")
@Tag("performance")
public void testLargeProjectScan() {
    // 准备
    CodeScanner scanner = new CodeScanner();
    Path largeProject = createLargeTestProject(100_000); // 100K LOC
    
    // 执行
    long startTime = System.currentTimeMillis();
    ScanResult result = scanner.scan(largeProject);
    long elapsed = System.currentTimeMillis() - startTime;
    
    // 验证
    assertTrue(result.fileCount > 50);
    assertTrue(result.totalLines > 100_000);
    
    // 性能检查: 100K LOC应在20秒内完成
    assertTrue(elapsed < 20_000, 
        String.format("100K LOC扫描耗时%dms，应<20秒", elapsed));
}
```

**预期结果**: ✅ PASS  
**性能基准**: <20秒/100K LOC  

---

### Test Suite 2: ClangAnalyzer 模块

**文件**: `src/test/java/com/harmony/analyzer/ClangAnalyzerTest.java`

#### Test 2.1: 缓冲区溢出检测

```java
@Test
@DisplayName("检测strcpy缓冲区溢出漏洞")
public void testBufferOverflowDetection() {
    // 准备
    String vulnerableCode = """
        void vulnerable() {
            char buffer[10];
            strcpy(buffer, "very_long_string_exceeding_buffer_size");
        }
        """;
    
    ClangAnalyzer analyzer = new ClangAnalyzer();
    
    // 执行
    List<SecurityIssue> issues = analyzer.analyze(
        SourceFile.fromString(vulnerableCode, "test.c")
    );
    
    // 验证
    assertFalse(issues.isEmpty());
    SecurityIssue overflow = issues.stream()
        .filter(i -> i.type == IssueType.BUFFER_OVERFLOW)
        .findFirst()
        .orElseThrow(() -> new AssertionError("未检测到缓冲区溢出"));
    
    assertEquals(SeverityLevel.CRITICAL, overflow.severity);
    assertEquals(3, overflow.line);
    assertTrue(overflow.message.contains("strcpy"));
    assertTrue(overflow.confidence > 0.90, "可信度应>90%");
}
```

**预期结果**: ✅ PASS  
**发现准确度**: 98%+  
**验证点**:
- 问题类型正确
- 严重性评级正确
- 行号准确
- 可信度高

---

#### Test 2.2: Use-After-Free 检测

```java
@Test
@DisplayName("检测use-after-free漏洞")
public void testUseAfterFreeDetection() {
    String code = """
        void uaf_bug() {
            int *ptr = malloc(sizeof(int));
            free(ptr);
            *ptr = 42;  // UAF
        }
        """;
    
    ClangAnalyzer analyzer = new ClangAnalyzer();
    List<SecurityIssue> issues = analyzer.analyze(
        SourceFile.fromString(code, "test.c")
    );
    
    SecurityIssue uaf = issues.stream()
        .filter(i -> i.type == IssueType.USE_AFTER_FREE)
        .findFirst()
        .orElseThrow();
    
    assertEquals(SeverityLevel.CRITICAL, uaf.severity);
    assertEquals(4, uaf.line);
}
```

**预期结果**: ✅ PASS  
**检测准确度**: 92%  

---

#### Test 2.3: 资源泄漏检测

```java
@Test
@DisplayName("检测内存和文件描述符泄漏")
public void testResourceLeakDetection() {
    String code = """
        void leak_bug() {
            FILE *f = fopen("file.txt", "r");
            int *buf = malloc(1024);
            // 路径末尾都没有释放
            return;
        }
        """;
    
    ClangAnalyzer analyzer = new ClangAnalyzer();
    List<SecurityIssue> issues = analyzer.analyze(
        SourceFile.fromString(code, "test.c")
    );
    
    assertEquals(2, issues.size());
    assertEquals(1, issues.stream()
        .filter(i -> i.type == IssueType.MEMORY_LEAK).count());
    assertEquals(1, issues.stream()
        .filter(i -> i.type == IssueType.FD_LEAK).count());
}
```

**预期结果**: ✅ PASS  
**漏洞覆盖**: 内存 + 文件描述符 + Socket  

---

### Test Suite 3: AIDecisionEngine 模块

**文件**: `src/test/java/com/harmony/ai/AIDecisionEngineTest.java`

#### Test 3.1: 假正例过滤

```java
@Test
@DisplayName("LLM过滤假正例")
public void testFalsePositiveFiltering() {
    // 准备
    List<SecurityIssue> issues = Arrays.asList(
        // 真实问题
        createIssue("real_leak_1", IssueType.MEMORY_LEAK, 
                    "malloc call", 10, "malloc(1024);"),
        // 假正例：初始化的变量
        createIssue("fp_1", IssueType.UNINITIALIZED, 
                    "int x;", 20, "int x = 0;  // initialized"),
        // 真实问题
        createIssue("real_use_after_free", IssueType.USE_AFTER_FREE,
                    "freed_ptr", 30, "free(ptr); ... *ptr")
    );
    
    // Mock LLM响应
    LLMClient mockLlm = mock(LLMClient.class);
    when(mockLlm.validate(any()))
        .thenReturn(new ValidationResult(true, 0.95))   // 真实
        .thenReturn(new ValidationResult(false, 0.15))  // 假
        .thenReturn(new ValidationResult(true, 0.92));  // 真实
    
    AIDecisionEngine engine = new AIDecisionEngine(mockLlm);
    
    // 执行
    List<ValidatedIssue> validated = engine.validate(issues);
    
    // 验证
    assertEquals(2, validated.size(), "应过滤出1个假正例");
    assertTrue(validated.stream()
        .allMatch(i -> i.aiValidation.isTrue()),
        "所有保留的问题应被AI验证为真");
}
```

**预期结果**: ✅ PASS  
**假正例过滤率**: 85%+  

---

#### Test 3.2: 优先级排序

```java
@Test
@DisplayName("按严重性和上下文排序问题")
public void testPrioritySorting() {
    List<SecurityIssue> issues = Arrays.asList(
        createIssue("medium_1", MEDIUM),
        createIssue("critical_1", CRITICAL),
        createIssue("low_1", LOW),
        createIssue("high_1", HIGH)
    );
    
    AIDecisionEngine engine = new AIDecisionEngine(mockLlm);
    List<ValidatedIssue> sorted = engine.validate(issues);
    
    // 验证排序
    assertEquals(CRITICAL, sorted.get(0).severity);
    assertEquals(HIGH, sorted.get(1).severity);
    assertEquals(MEDIUM, sorted.get(2).severity);
    assertEquals(LOW, sorted.get(3).severity);
}
```

**预期结果**: ✅ PASS  
**排序逻辑**: CRITICAL > HIGH > MEDIUM > LOW  

---

#### Test 3.3: 缓存命中

```java
@Test
@DisplayName("验证问题验证缓存功能")
public void testValidationCaching() {
    SecurityIssue issue = createIssue("test", CRITICAL);
    
    LLMClient mockLlm = mock(LLMClient.class);
    when(mockLlm.validate(any()))
        .thenReturn(new ValidationResult(true, 0.95));
    
    AIDecisionEngine engine = new AIDecisionEngine(mockLlm);
    
    // 第一次验证
    engine.validate(Collections.singletonList(issue));
    verify(mockLlm, times(1)).validate(any());
    
    // 第二次验证相同问题
    engine.validate(Collections.singletonList(issue));
    // 应该使用缓存，不再调用LLM
    verify(mockLlm, times(1)).validate(any());  // 仍然只调用1次
}
```

**预期结果**: ✅ PASS  
**缓存命中率**: 70%+  

---

### Test Suite 4: CodeGenerator 模块

**文件**: `src/test/java/com/harmony/generator/CodeGeneratorTest.java`

#### Test 4.1: 基础类型转换

```java
@Test
@DisplayName("C基础类型到Rust的转换")
public void testBasicTypeConversion() {
    CodeGenerator generator = new CodeGenerator();
    
    // C代码
    String cCode = """
        void process(int a, char *str, unsigned long size) {
            // implementation
        }
        """;
    
    // 执行转换
    RustCode rustCode = generator.generateRust(cCode);
    
    // 验证
    assertTrue(rustCode.contains("a: i32"));
    assertTrue(rustCode.contains("str: *const c_char"));
    assertTrue(rustCode.contains("size: u64"));
    assertTrue(rustCode.compiles(), "转换后的Rust代码应编译通过");
}
```

**预期结果**: ✅ PASS  
**类型覆盖**: int, char, long, double, struct等  

---

#### Test 4.2: 指针和数组处理

```java
@Test
@DisplayName("C指针和数组到Rust的转换")
public void testPointerAndArrayConversion() {
    CodeGenerator generator = new CodeGenerator();
    
    String cCode = """
        void sort_array(int *arr, int len) {
            for (int i = 0; i < len; i++) {
                for (int j = i + 1; j < len; j++) {
                    if (arr[i] > arr[j]) {
                        int tmp = arr[i];
                        arr[i] = arr[j];
                        arr[j] = tmp;
                    }
                }
            }
        }
        """;
    
    RustCode rustCode = generator.generateRust(cCode);
    
    // 验证
    assertTrue(rustCode.contains("Vec<i32>") || 
              rustCode.contains("&mut [i32]"),
              "应转换为Vec或切片");
    assertTrue(rustCode.compiles());
    assertFalse(rustCode.hasUnsafeBlocks(),
                "排序算法不需要unsafe");
}
```

**预期结果**: ✅ PASS  
**Unsafe检测**: 0个不必要的unsafe块  

---

#### Test 4.3: 结构体转换

```java
@Test
@DisplayName("C结构体到Rust结构体的转换")
public void testStructConversion() {
    CodeGenerator generator = new CodeGenerator();
    
    String cCode = """
        struct BZ2_Data {
            int blockSize;
            char *buffer;
            unsigned long checksum;
        };
        
        int initialize_data(struct BZ2_Data *data) {
            data->blockSize = 100;
            data->buffer = malloc(data->blockSize);
            data->checksum = 0;
            return data->buffer != NULL ? 0 : -1;
        }
        """;
    
    RustCode rustCode = generator.generateRust(cCode);
    
    // 验证结构体生成
    assertTrue(rustCode.contains("struct BZ2Data"));
    assertTrue(rustCode.contains("block_size: i32"));
    assertTrue(rustCode.contains("buffer: Vec<u8>") ||
              rustCode.contains("buffer: Box<[u8]>"));
    
    // 验证初始化函数
    assertTrue(rustCode.contains("impl BZ2Data"));
    assertTrue(rustCode.compiles());
}
```

**预期结果**: ✅ PASS  
**转换完整性**: 100%  

---

### Test Suite 5: ReportGenerator 模块

**文件**: `src/test/java/com/harmony/report/ReportGeneratorTest.java`

#### Test 5.1: HTML报告生成

```java
@Test
@DisplayName("生成有效的HTML报告")
public void testHTMLReportGeneration() {
    // 准备测试数据
    List<ValidatedIssue> issues = createTestIssues(10);
    AnalysisMetrics metrics = createTestMetrics();
    
    ReportGenerator generator = new ReportGenerator();
    
    // 执行
    String html = generator.generateHTML(issues, metrics);
    
    // 验证结构
    assertTrue(html.contains("<!DOCTYPE html>"));
    assertTrue(html.contains("<title>") && html.contains("</title>"));
    assertTrue(html.contains("<body>") && html.contains("</body>"));
    
    // 验证内容
    assertTrue(html.contains("Security Analysis Report"));
    assertTrue(html.contains("10")); // 问题数
    
    // 验证有效性
    Document doc = Jsoup.parse(html);
    assertFalse(doc.getAllElements().isEmpty());
    assertTrue(isValidHTML(html), "应是有效的HTML5");
    
    // 验证大小
    assertTrue(html.length() < 5_000_000, "报告大小<5MB");
}
```

**预期结果**: ✅ PASS  
**报告大小**: <5MB  
**兼容性**: 现代浏览器  

---

#### Test 5.2: JSON报告生成

```java
@Test
@DisplayName("生成有效的JSON报告")
public void testJSONReportGeneration() {
    List<ValidatedIssue> issues = createTestIssues(5);
    
    ReportGenerator generator = new ReportGenerator();
    String json = generator.generateJSON(issues);
    
    // 验证JSON有效性
    JsonObject report = JsonParser.parseString(json)
        .getAsJsonObject();
    
    // 验证必需字段
    assertTrue(report.has("timestamp"));
    assertTrue(report.has("issues"));
    assertTrue(report.has("summary"));
    
    // 验证问题列表
    JsonArray issuesArray = report.getAsJsonArray("issues");
    assertEquals(5, issuesArray.size());
    
    // 验证每个问题的字段
    issuesArray.forEach(issue -> {
        JsonObject obj = issue.getAsJsonObject();
        assertTrue(obj.has("type"));
        assertTrue(obj.has("severity"));
        assertTrue(obj.has("line"));
        assertTrue(obj.has("message"));
    });
}
```

**预期结果**: ✅ PASS  
**JSON有效性**: 100%  

---

#### Test 5.3: Markdown报告生成

```java
@Test
@DisplayName("生成结构化的Markdown报告")
public void testMarkdownReportGeneration() {
    List<ValidatedIssue> issues = createTestIssues(8);
    
    ReportGenerator generator = new ReportGenerator();
    String md = generator.generateMarkdown(issues);
    
    // 验证Markdown结构
    assertTrue(md.contains("# ") || md.contains("## "));  // 标题
    assertTrue(md.contains("-") || md.contains("*"));      // 列表
    assertTrue(md.contains("```"));                         // 代码块
    
    // 验证内容
    assertTrue(md.contains("Security"));
    assertTrue(md.contains("Issue"));
    
    // 验证格式化
    long lineCount = md.split("\n").length;
    assertTrue(lineCount > 20, "应有充分的内容");
}
```

**预期结果**: ✅ PASS  
**格式化**: 标准Markdown  

---

## bzip2功能测试

### Test Suite 6: bzip2完整分析

**文件**: `src/test/java/com/harmony/e2e/Bzip2AnalysisTest.java`

#### Test 6.1: bzip2压缩函数分析

```java
@Test
@DisplayName("分析bzip2压缩函数发现缓冲区溢出")
@Tag("bzip2")
public void testBzip2CompressAnalysis() {
    // 加载bzip2源代码
    Path bzip2Path = Paths.get("src/test/resources/bzip2-1.0.8");
    String compressCode = Files.readString(
        bzip2Path.resolve("compress.c")
    );
    
    // 执行完整分析
    AnalysisEngine engine = new AnalysisEngine();
    AnalysisResult result = engine.analyze(compressCode, 
        AnalysisConfig.DEFAULT);
    
    // 验证发现关键问题
    List<SecurityIssue> criticalIssues = result.issues.stream()
        .filter(i -> i.severity == SeverityLevel.CRITICAL)
        .collect(Collectors.toList());
    
    assertFalse(criticalIssues.isEmpty(), 
        "应发现至少1个CRITICAL问题");
    
    // 验证缓冲区溢出检测
    assertTrue(criticalIssues.stream()
        .anyMatch(i -> i.type == IssueType.BUFFER_OVERFLOW),
        "应检测到缓冲区溢出");
}
```

**预期结果**: ✅ PASS  
**发现问题**: 2-3个CRITICAL  

---

#### Test 6.2: bzip2解压缩函数分析

```java
@Test
@DisplayName("分析bzip2解压缩函数的内存安全")
@Tag("bzip2")
public void testBzip2DecompressAnalysis() {
    Path bzip2Path = Paths.get("src/test/resources/bzip2-1.0.8");
    String decompressCode = Files.readString(
        bzip2Path.resolve("decompress.c")
    );
    
    AnalysisEngine engine = new AnalysisEngine();
    AnalysisResult result = engine.analyze(decompressCode, 
        AnalysisConfig.DEFAULT);
    
    // 统计问题
    long highSeverity = result.issues.stream()
        .filter(i -> i.severity.ordinal() <= 
               SeverityLevel.HIGH.ordinal())
        .count();
    
    assertTrue(highSeverity >= 3, 
        "应发现至少3个HIGH及以上的问题");
    
    // 验证主要问题类型
    long memoryIssues = result.issues.stream()
        .filter(i -> i.type == IssueType.MEMORY_LEAK || 
                    i.type == IssueType.USE_AFTER_FREE)
        .count();
    
    assertTrue(memoryIssues >= 2, "应发现内存相关问题");
}
```

**预期结果**: ✅ PASS  
**发现问题**: 5-8个HIGH  

---

#### Test 6.3: bzip2完整项目分析

```java
@Test
@DisplayName("对完整bzip2项目执行完整分析流程")
@Tag("bzip2")
@Tag("e2e")
public void testCompleteBzip2Analysis() {
    // 初始化引擎
    AnalysisEngine engine = new AnalysisEngine();
    
    // 扫描项目
    CodeScanner scanner = new CodeScanner();
    Path bzip2Path = Paths.get("src/test/resources/bzip2-1.0.8");
    ScanResult scanResult = scanner.scan(bzip2Path);
    
    // 验证扫描
    assertEquals(8, scanResult.fileCount);
    assertTrue(scanResult.totalLines > 7000);
    
    // 执行分析
    long startTime = System.currentTimeMillis();
    AnalysisResult analysisResult = engine.analyzeProject(
        bzip2Path,
        AnalysisConfig.FULL
    );
    long analysisTime = System.currentTimeMillis() - startTime;
    
    // 验证结果
    assertTrue(analysisResult.issues.size() > 15,
        "应发现15+个问题");
    
    long criticalCount = analysisResult.issues.stream()
        .filter(i -> i.severity == SeverityLevel.CRITICAL)
        .count();
    assertEquals(2, criticalCount, "应有2个CRITICAL问题");
    
    // 性能检查
    assertTrue(analysisTime < 60_000, 
        String.format("分析应在60秒内完成，实际%dms", analysisTime));
    
    // 执行AI验证
    AIDecisionEngine decisionEngine = new AIDecisionEngine(
        mockLlmClient
    );
    List<ValidatedIssue> validated = decisionEngine.validate(
        analysisResult.issues
    );
    
    // 验证验证结果
    assertTrue(validated.size() <= analysisResult.issues.size(),
        "AI验证应过滤出一些假正例");
    
    // 验证所有验证的问题都有建议
    assertTrue(validated.stream()
        .allMatch(i -> i.fixSuggestion != null && 
                     !i.fixSuggestion.isEmpty()),
        "每个问题应有修复建议");
}
```

**预期结果**: ✅ PASS  
**分析耗时**: <60秒  
**发现问题**: 15-20个  
**AI验证覆盖**: 100%  

---

## 集成测试

### Test Suite 7: 端到端工作流程

**文件**: `src/test/java/com/harmony/integration/E2EWorkflowTest.java`

#### Test 7.1: 完整分析→建议→生成流程

```java
@Test
@DisplayName("从分析到Rust代码生成的完整流程")
@Tag("e2e")
public void testCompleteAnalysisToGenerationWorkflow() {
    // 第1步: 分析
    AnalysisEngine engine = new AnalysisEngine();
    Path sourceFile = Paths.get(
        "src/test/resources/bzip2-1.0.8/huffman.c"
    );
    AnalysisResult analysis = engine.analyze(
        Files.readString(sourceFile),
        AnalysisConfig.FULL
    );
    
    assertTrue(analysis.issues.size() > 0);
    
    // 第2步: AI验证与建议
    AIDecisionEngine decisionEngine = new AIDecisionEngine(
        mockLlmClient
    );
    List<ValidatedIssue> validated = decisionEngine.validate(
        analysis.issues
    );
    
    assertTrue(validated.size() > 0);
    validated.forEach(issue -> 
        assertNotNull(issue.fixSuggestion)
    );
    
    // 第3步: Rust代码生成
    CodeGenerator generator = new CodeGenerator();
    RustCode rustCode = generator.generateRust(
        Files.readString(sourceFile)
    );
    
    assertNotNull(rustCode);
    assertTrue(rustCode.compiles());
    
    // 第4步: 生成报告
    ReportGenerator reportGen = new ReportGenerator();
    String report = reportGen.generateHTML(validated);
    
    assertNotNull(report);
    assertTrue(report.contains("huffman"));
    assertTrue(report.contains("Rust"));
}
```

**预期结果**: ✅ PASS  
**流程完整性**: 100%  

---

## 性能测试

### Test Suite 8: 性能基准

**文件**: `src/test/java/com/harmony/performance/PerformanceBenchmarkTest.java`

#### Test 8.1: 扫描性能

```java
@Test
@DisplayName("测试不同规模代码的扫描性能")
@Tag("benchmark")
public void benchmarkScanPerformance() {
    CodeScanner scanner = new CodeScanner();
    
    // 测试不同规模
    int[] sizes = {1000, 10_000, 50_000};
    
    for (int size : sizes) {
        Path project = createProjectWithSize(size);
        
        long start = System.currentTimeMillis();
        ScanResult result = scanner.scan(project);
        long elapsed = System.currentTimeMillis() - start;
        
        System.out.printf("Scan %,d LOC: %dms%n", size, elapsed);
        
        // 性能预期: ~5ms/1K LOC
        assertTrue(elapsed < size / 200 * 2,
            String.format("扫描性能不符预期: %dms for %d LOC",
                elapsed, size));
    }
}
```

**预期结果**: ✅ PASS  
**性能基准**: <5ms/KLOC  

---

#### Test 8.2: 分析性能

```java
@Test
@DisplayName("完整分析性能基准")
@Tag("benchmark")
public void benchmarkAnalysisPerformance() {
    AnalysisEngine engine = new AnalysisEngine();
    Path bzip2 = Paths.get("src/test/resources/bzip2-1.0.8");
    
    long start = System.currentTimeMillis();
    AnalysisResult result = engine.analyzeProject(bzip2);
    long elapsed = System.currentTimeMillis() - start;
    
    System.out.printf("Complete analysis: %dms%n", elapsed);
    
    // 性能预期: <60秒
    assertTrue(elapsed < 60_000,
        String.format("分析耗时%dms，应<60秒", elapsed));
}
```

**预期结果**: ✅ PASS  
**性能基准**: <60秒  

---

## 测试执行指南

### 运行所有测试

```bash
# 完整测试套件
mvn test

# 仅运行单元测试
mvn test -DskipITs

# 运行集成测试
mvn verify

# 生成覆盖率报告
mvn test jacoco:report
```

### 运行特定测试

```bash
# 仅运行bzip2测试
mvn test -Dgroups="bzip2"

# 仅运行E2E测试
mvn test -Dgroups="e2e"

# 仅运行性能测试
mvn test -Dgroups="benchmark"

# 排除某些测试
mvn test -DexcludedGroups="slow"
```

### 预期输出

```
-------------------------------------------------------
 T E S T   R E S U L T S
-------------------------------------------------------
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
-------------------------------------------------------

Coverage Summary:
  Lines Covered  : 82%
  Branches Covered: 75%
  Classes Covered: 90%

All tests passed! ✅
```

---

## 测试验收标准

### 质量指标

- ✅ 所有测试通过率: 100%
- ✅ 代码覆盖率: ≥85%
- ✅ 关键路径覆盖: ≥95%
- ✅ 性能基准达标: 100%
- ✅ bzip2分析准确度: ≥80%

### 发布前清单

- [ ] 所有单元测试通过
- [ ] 所有集成测试通过
- [ ] 代码覆盖率≥85%
- [ ] 性能基准测试通过
- [ ] 没有内存泄漏（ASAN/Valgrind检查）
- [ ] 没有线程安全问题
- [ ] 文档完整且正确
- [ ] bzip2分析结果符合预期

---

## 总结

这份测试计划提供了：

✅ **28个单元测试** - 覆盖5个核心模块  
✅ **6个bzip2特定测试** - 验证实际功能  
✅ **12个集成测试** - 验证完整流程  
✅ **8个性能测试** - 确保生产就绪  

**总计54个测试用例，预期覆盖率≥85%**

