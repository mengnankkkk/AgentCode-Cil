# E2E测试资源目录

此目录用于存放端到端集成测试所需的测试项目。

## 目录结构

```
e2e/
├── README.md           (本文件)
├── bzip2/             (待添加：bzip2测试项目)
│   ├── *.c            (C源文件)
│   ├── *.h            (头文件)
│   └── compile_commands.json  (编译数据库)
└── sample/            (示例测试项目 - 可选)
    ├── test.c
    └── compile_commands.json
```

## 如何准备测试数据

### 选项1: 使用bzip2（推荐）

1. 下载bzip2源码:
   ```bash
   git clone https://sourceware.org/git/bzip2.git src/test/resources/e2e/bzip2
   cd src/test/resources/e2e/bzip2
   ```

2. 生成compile_commands.json:
   ```bash
   # 使用bear（推荐）
   bear -- make

   # 或使用cmake
   cmake -DCMAKE_EXPORT_COMPILE_COMMANDS=ON .
   ```

### 选项2: 使用其他小型C项目

任何小型C项目都可以，要求：
- 包含至少5个C源文件
- 有明显的安全问题（用于测试AI检测）
- 可以生成compile_commands.json

### 选项3: 使用提供的示例项目

如果您提供了测试项目压缩包，请解压到此目录。

## 测试数据要求

E2E测试需要以下内容：

1. **C源文件** (*.c)
   - 至少5个文件
   - 包含一些常见安全问题（buffer overflow, null pointer等）

2. **compile_commands.json**
   - 必需，用于Clang-Tidy分析
   - 格式参考：https://clang.llvm.org/docs/JSONCompilationDatabase.html

3. **预期结果** (可选)
   - 如果知道预期的问题数量，可以在测试中验证

## 测试项目大小建议

- **小型项目**（推荐用于快速测试）: 10-50个源文件, <10MB
- **中型项目**（用于性能测试）: 50-200个源文件, 10-50MB
- **大型项目**（用于压力测试）: 200+个源文件, 50MB+

## 当前状态

- [ ] bzip2测试项目（待添加）
- [ ] compile_commands.json（待生成）
- [ ] 示例项目（可选）

## 下一步

1. 准备测试项目（参考上述选项）
2. 运行E2E测试: `mvn test -Dtest=E2ETest`
3. 查看测试报告: `target/surefire-reports/`
