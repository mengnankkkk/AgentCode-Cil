# LLM Architecture - Quick Start Guide

## 🚀 快速上手

### 前置条件

```bash
# 设置API密钥（环境变量）
export OPENAI_API_KEY="your-openai-api-key"
export CLAUDE_API_KEY="your-claude-api-key"
```

---

## 基础使用

### 1. 创建Provider和Role工厂

```java
// Provider工厂 - 管理LLM提供商
ProviderFactory providerFactory = ProviderFactory.createDefault(
    System.getenv("OPENAI_API_KEY"),
    System.getenv("CLAUDE_API_KEY")
);

// Role工厂 - 管理AI角色
RoleFactory roleFactory = RoleFactory.createDefault();
```

### 2. 创建并配置Orchestrator

```java
LLMOrchestrator orchestrator = LLMOrchestrator.builder(providerFactory, roleFactory)
    // Analyzer: 使用OpenAI快速模型分析需求
    .configureRole("analyzer", "openai", "gpt-3.5-turbo")

    // Planner: 使用Claude标准模型规划设计
    .configureRole("planner", "claude", "claude-3-sonnet-20240229")

    // Coder: 使用Claude标准模型生成代码
    .configureRole("coder", "claude", "claude-3-sonnet-20240229")

    // Reviewer: 使用Claude高级模型审查代码
    .configureRole("reviewer", "claude", "claude-3-opus-20240229")

    .build();
```

### 3. 执行工作流

```java
// 分析需求
String requirement = "实现用户登录功能";
TodoList tasks = orchestrator.analyzeRequirement(requirement);

// 打印任务列表
tasks.getAllTasks().forEach(task ->
    System.out.println(task.getId() + ". " + task.getDescription())
);
```

---

## 完整工作流示例

```java
import com.harmony.agent.llm.orchestrator.*;
import com.harmony.agent.llm.provider.*;
import com.harmony.agent.llm.role.*;

public class LLMWorkflowExample {

    public static void main(String[] args) {
        // 1. 初始化
        ProviderFactory providerFactory = ProviderFactory.createDefault(
            System.getenv("OPENAI_API_KEY"),
            System.getenv("CLAUDE_API_KEY")
        );

        RoleFactory roleFactory = RoleFactory.createDefault();

        LLMOrchestrator orchestrator = LLMOrchestrator.builder(providerFactory, roleFactory)
            .configureRole("analyzer", "openai", "gpt-3.5-turbo")
            .configureRole("planner", "claude", "claude-3-sonnet-20240229")
            .configureRole("coder", "claude", "claude-3-sonnet-20240229")
            .configureRole("reviewer", "claude", "claude-3-opus-20240229")
            .build();

        // 2. 创建上下文
        String requirement = "实现JWT token的生成和验证";
        ConversationContext context = new ConversationContext(requirement);

        // 3. 分析需求 (Analyzer)
        System.out.println("=== Step 1: Analyzing Requirement ===");
        TodoList tasks = orchestrator.analyzeRequirement(requirement);
        context.setTodoList(tasks);

        tasks.getAllTasks().forEach(task ->
            System.out.println("  " + task.getId() + ". " + task.getDescription())
        );

        // 4. 创建设计 (Planner)
        System.out.println("\n=== Step 2: Creating Design ===");
        String design = orchestrator.createDesign(context, "Design JWT authentication system");
        context.setDesignDocument(design);
        System.out.println(design);

        // 5. 生成代码 (Coder)
        System.out.println("\n=== Step 3: Generating Code ===");
        String code = orchestrator.generateCode(context, "Implement JWT token generation");
        context.addGeneratedCode("JWTService.java", code);
        System.out.println(code);

        // 6. 审查代码 (Reviewer)
        System.out.println("\n=== Step 4: Reviewing Code ===");
        String review = orchestrator.reviewCode(context, code);
        System.out.println(review);

        // 7. 根据反馈改进 (如果需要)
        if (review.contains("⚠️") || review.contains("❌")) {
            System.out.println("\n=== Step 5: Improving Code Based on Review ===");
            String improvedCode = orchestrator.generateCode(context,
                "Improve the code based on review feedback:\n" + review);
            context.addGeneratedCode("JWTService.java", improvedCode);
            System.out.println(improvedCode);
        }

        System.out.println("\n✅ Workflow completed!");
    }
}
```

---

## 单独使用角色

### Analyzer - 需求分析

```java
// 1. 获取Analyzer角色
LLMRole analyzer = roleFactory.getRole("analyzer");

// 2. 配置Provider
LLMProvider provider = providerFactory.getProvider("openai");
analyzer.setProvider(provider);
analyzer.setModel("gpt-3.5-turbo");

// 3. 执行分析
String requirement = "添加用户注册功能";
LLMResponse response = analyzer.execute(requirement, "");

// 4. 获取结果
System.out.println(response.getContent());
```

### Planner - 技术规划

```java
LLMRole planner = roleFactory.getRole("planner");
planner.setProvider(providerFactory.getProvider("claude"));
planner.setModel("claude-3-sonnet-20240229");

String task = "设计微服务架构的API网关";
String context = "Current system: monolithic app, 100k users";

LLMResponse response = planner.execute(task, context);
System.out.println(response.getContent());
```

### Coder - 代码生成

```java
LLMRole coder = roleFactory.getRole("coder");
coder.setProvider(providerFactory.getProvider("claude"));
coder.setModel("claude-3-sonnet-20240229");

String task = "实现Redis缓存工具类";
String context = """
    Requirements:
    - Support get/set/delete operations
    - Handle serialization automatically
    - Include TTL support
    """;

LLMResponse response = coder.execute(task, context);
System.out.println(response.getContent());
```

### Reviewer - 代码审查

```java
LLMRole reviewer = roleFactory.getRole("reviewer");
reviewer.setProvider(providerFactory.getProvider("claude"));
reviewer.setModel("claude-3-opus-20240229");

String codeToReview = """
    public class UserService {
        public void deleteUser(String userId) {
            database.execute("DELETE FROM users WHERE id = " + userId);
        }
    }
    """;

String task = "Review this code for security issues";
LLMResponse response = reviewer.execute(task, codeToReview);
System.out.println(response.getContent());
// 输出: ❌ Critical security issue: SQL injection vulnerability
```

---

## 配置管理

### 从配置文件加载

```java
// application.yml中配置角色映射
LLMConfig config = configManager.getConfig();

// 根据配置创建orchestrator
LLMOrchestrator orchestrator = createOrchestratorFromConfig(config);
```

### 动态切换模型

```java
// 运行时切换模型
orchestrator.configureRole("coder", "openai", "gpt-4");

// 再次执行，使用新模型
String code = orchestrator.generateCode(context, task);
```

---

## 成本优化策略

### 策略1: 任务难度匹配

```java
// 简单任务 → 快速模型
orchestrator.configureRole("analyzer", "openai", "gpt-3.5-turbo");

// 中等任务 → 标准模型
orchestrator.configureRole("planner", "claude", "claude-3-sonnet");
orchestrator.configureRole("coder", "claude", "claude-3-sonnet");

// 复杂任务 → 高级模型
orchestrator.configureRole("reviewer", "claude", "claude-3-opus");
```

**成本节省: ~60-70%** 相比全程使用最高级模型

### 策略2: 批量处理

```java
List<String> requirements = Arrays.asList(
    "实现用户登录",
    "添加数据验证",
    "优化查询性能"
);

// 批量分析（使用快速模型）
List<TodoList> allTasks = requirements.stream()
    .map(orchestrator::analyzeRequirement)
    .toList();
```

---

## 错误处理

### 检查Provider可用性

```java
LLMProvider provider = providerFactory.getProvider("openai");

if (!provider.isAvailable()) {
    System.err.println("OpenAI provider not available. Check API key.");
    return;
}
```

### 处理执行失败

```java
LLMResponse response = orchestrator.executeRole("coder", task, context);

if (!response.isSuccess()) {
    System.err.println("Error: " + response.getErrorMessage());
    // 使用备用Provider或重试
    orchestrator.configureRole("coder", "openai", "gpt-4");
    response = orchestrator.executeRole("coder", task, context);
}
```

### 重试机制

```java
int maxRetries = 3;
LLMResponse response = null;

for (int i = 0; i < maxRetries; i++) {
    response = orchestrator.executeRole("coder", task, context);
    if (response.isSuccess()) break;

    System.out.println("Retry " + (i + 1) + "/" + maxRetries);
    Thread.sleep(1000); // 等待1秒
}

if (!response.isSuccess()) {
    throw new RuntimeException("Failed after " + maxRetries + " retries");
}
```

---

## 常见场景

### 场景1: 代码重构

```java
String oldCode = readFile("LegacyService.java");

// 1. Reviewer分析问题
String analysis = orchestrator.reviewCode(context, oldCode);

// 2. Planner设计重构方案
context.addMetadata("code_review", analysis);
String plan = orchestrator.createDesign(context, "Refactor based on review");

// 3. Coder实现重构
context.setDesignDocument(plan);
String newCode = orchestrator.generateCode(context, "Refactor the code");

// 4. Reviewer验证重构
String validation = orchestrator.reviewCode(context, newCode);
```

### 场景2: 新功能开发

```java
// 完整开发流程
String feature = "添加二次验证功能";

ConversationContext ctx = new ConversationContext(feature);

// 分析 → 设计 → 实现 → 审查
TodoList tasks = orchestrator.analyzeRequirement(feature);
String design = orchestrator.createDesign(ctx, tasks.getCurrentTask().getDescription());
String code = orchestrator.generateCode(ctx, tasks.getCurrentTask().getDescription());
String review = orchestrator.reviewCode(ctx, code);

// 如果审查通过
if (review.contains("✅")) {
    System.out.println("Feature completed and approved!");
}
```

### 场景3: 技术调研

```java
// 使用Planner进行技术调研
LLMRole planner = roleFactory.getRole("planner");
planner.setProvider(providerFactory.getProvider("claude"));
planner.setModel("claude-3-opus-20240229"); // 使用最强模型

String question = """
    比较以下三种缓存方案的优劣:
    1. Redis
    2. Memcached
    3. Caffeine (本地缓存)

    考虑: 性能、可扩展性、功能特性、运维成本
    """;

LLMResponse analysis = planner.execute(question, "");
System.out.println(analysis.getContent());
```

---

## 调试技巧

### 启用详细日志

```java
// 在logback.xml中配置
<logger name="com.harmony.agent.llm" level="DEBUG"/>
```

### 查看Context内容

```java
ConversationContext context = new ConversationContext(requirement);
// ... 执行操作 ...

// 打印完整上下文
System.out.println("=== Context ===");
System.out.println(context.buildContextString());
```

### 监控Token使用

```java
LLMResponse response = orchestrator.executeRole("coder", task, context);

System.out.println("Prompt tokens: " + response.getPromptTokens());
System.out.println("Completion tokens: " + response.getCompletionTokens());
System.out.println("Total tokens: " + response.getTotalTokens());

// 估算成本（GPT-4示例）
double cost = (response.getPromptTokens() * 0.00003) +
              (response.getCompletionTokens() * 0.00006);
System.out.printf("Estimated cost: $%.4f\n", cost);
```

---

## 下一步

- 📖 阅读完整的 [LLM_ARCHITECTURE.md](./LLM_ARCHITECTURE.md)
- 🔧 查看 [application.yml](../src/main/resources/application.yml) 配置示例
- 🧪 尝试不同的Provider和Role组合
- 💡 根据你的项目需求定制角色

---

**Happy Coding! 🚀**
