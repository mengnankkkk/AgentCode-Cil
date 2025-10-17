# 快速开始 - 交互式模式

## 🎯 3步开始使用

### 1. 构建项目
```bash
mvn clean package
```

### 2. 启动交互模式
```bash
# Windows
bin\agent-safe.bat

# Linux/macOS
bin/agent-safe.sh

# 或使用JAR
java -jar target/harmony-agent.jar
```

### 3. 开始使用
```bash
❯ /help        # 查看命令帮助
❯ /analyze ./test-code   # 分析代码
❯ /exit        # 退出
```

---

## 📋 可用命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `/analyze <path>` | 分析代码安全问题 | `/analyze ./src` |
| `/suggest [file]` | 获取修复建议 (Phase 3) | `/suggest bug.c` |
| `/refactor [file]` | 重构建议 (Phase 4) | `/refactor old.c` |
| `/config` | 显示当前配置 | `/config` |
| `/history` | 查看对话历史 | `/history` |
| `/help` | 显示帮助 | `/help` |
| `/clear` | 清屏 | `/clear` |
| `/exit` | 退出 | `/exit` |

---

## 💡 使用提示

**分析代码：**
```bash
❯ /analyze ./test-code
```

**查看历史：**
```bash
❯ /history
```

**清屏：**
```bash
❯ /clear
```

**自然对话（Phase 3）：**
```bash
❯ What are buffer overflows?
❯ How to prevent memory leaks?
```

---

## 🎨 功能亮点

✅ **交互式REPL** - 持续对话，无需重启
✅ **斜杠命令** - 快速执行特定功能
✅ **彩色输出** - 友好的视觉体验
✅ **命令历史** - 记录所有交互
✅ **配置管理** - 随时查看设置
✅ **优雅退出** - 多种退出方式

🔄 **即将推出** - AI对话（Phase 3）

---

## 📚 更多文档

- [完整使用指南](./INTERACTIVE_MODE.md)
- [分析器安装](./ANALYZER_INSTALLATION.md)
- [Phase 2完成报告](./PHASE2_COMPLETION.md)

---

**开始探索吧！** 🚀
