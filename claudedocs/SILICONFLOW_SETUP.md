# SiliconFlow (硅基流动) Provider 配置指南

## 概述

HarmonySafeAgent 现已支持硅基流动（SiliconFlow）作为 LLM 提供商。硅基流动提供多种开源模型的 API 访问，兼容 OpenAI API 格式。

## 架构集成

### 新增文件

- `src/main/java/com/harmony/agent/llm/provider/SiliconFlowProvider.java`
  - 实现 `BaseLLMProvider` 抽象类
  - 支持多种开源模型（Qwen、DeepSeek、GLM、Yi、Llama、Mistral 等）

### 更新文件

- `ProviderFactory.java` - 添加 SiliconFlow provider 注册
- `LLMClient.java` - 添加 SILICONFLOW_API_KEY 环境变量支持
- `AiValidationClient.java` - 添加 SiliconFlow 集成
- `RefactorCommand.java` - 添加 SiliconFlow 提示信息

## 配置步骤

### 1. 获取 API Key

访问 [硅基流动官网](https://siliconflow.cn/) 注册并获取 API Key。

### 2. 设置环境变量

**Windows (PowerShell):**
```powershell
$env:SILICONFLOW_API_KEY="your-api-key-here"
```

**Windows (CMD):**
```cmd
set SILICONFLOW_API_KEY=your-api-key-here
```

**Linux/Mac:**
```bash
export SILICONFLOW_API_KEY="your-api-key-here"
```

### 3. 配置 config.yaml

在 `config.yaml` 中设置 provider 为 `siliconflow`：

```yaml
ai:
  provider: siliconflow
  model: Qwen/Qwen2.5-7B-Instruct
  temperature: 0.7
  maxTokens: 2000
```

## 支持的模型

### Qwen (通义千问) 系列
- `Qwen/Qwen2.5-7B-Instruct`
- `Qwen/Qwen2.5-14B-Instruct`
- `Qwen/Qwen2.5-32B-Instruct`
- `Qwen/Qwen2.5-72B-Instruct`
- `Qwen/Qwen2.5-Coder-7B-Instruct`

### DeepSeek 系列
- `deepseek-ai/DeepSeek-V2.5`
- `deepseek-ai/DeepSeek-Coder-V2-Instruct`

### GLM (智谱) 系列
- `THUDM/glm-4-9b-chat`

### Yi 系列
- `01-ai/Yi-1.5-9B-Chat`
- `01-ai/Yi-1.5-34B-Chat`

### Llama 系列
- `meta-llama/Meta-Llama-3.1-8B-Instruct`
- `meta-llama/Meta-Llama-3.1-70B-Instruct`

### Mistral 系列
- `mistralai/Mistral-7B-Instruct-v0.3`
- `mistralai/Mixtral-8x7B-Instruct-v0.1`

## 使用示例

### 命令行使用

```bash
# 设置环境变量
export SILICONFLOW_API_KEY="your-api-key"

# 运行分析命令
java -jar target/harmony-safe-agent-1.0.0-SNAPSHOT.jar analyze /path/to/code

# 运行重构命令
java -jar target/harmony-safe-agent-1.0.0-SNAPSHOT.jar refactor /path/to/code
```

### 代码中使用

```java
// 创建 ProviderFactory（自动读取环境变量）
ProviderFactory factory = ProviderFactory.createDefault(
    openaiKey,
    claudeKey,
    siliconflowKey
);

// 获取 SiliconFlow provider
LLMProvider provider = factory.getProvider("siliconflow");

// 发送请求
LLMRequest request = LLMRequest.builder()
    .model("Qwen/Qwen2.5-7B-Instruct")
    .addUserMessage("分析这段代码的安全性")
    .temperature(0.3)
    .maxTokens(2000)
    .build();

LLMResponse response = provider.sendRequest(request);
```

## 模型选择建议

### 代码分析场景
- **推荐**: `Qwen/Qwen2.5-Coder-7B-Instruct` 或 `deepseek-ai/DeepSeek-Coder-V2-Instruct`
- **理由**: 专门针对代码任务优化

### 安全审查场景
- **推荐**: `Qwen/Qwen2.5-14B-Instruct` 或 `deepseek-ai/DeepSeek-V2.5`
- **理由**: 更强的推理能力，适合复杂分析

### 快速响应场景
- **推荐**: `Qwen/Qwen2.5-7B-Instruct` 或 `mistralai/Mistral-7B-Instruct-v0.3`
- **理由**: 参数量较小，响应速度快

### 高质量输出场景
- **推荐**: `Qwen/Qwen2.5-72B-Instruct` 或 `meta-llama/Meta-Llama-3.1-70B-Instruct`
- **理由**: 大参数模型，输出质量高

## 验证配置

运行以下命令验证 SiliconFlow 配置是否正确：

```bash
# 编译项目
mvn clean compile

# 运行测试（可选）
mvn test

# 验证 provider 可用性
java -jar target/harmony-safe-agent-1.0.0-SNAPSHOT.jar --help
```

## 故障排查

### 错误: "Provider not found: siliconflow"

**原因**: API Key 未设置或为空

**解决方案**:
1. 检查环境变量是否设置: `echo $SILICONFLOW_API_KEY`
2. 确保 API Key 有效且未过期
3. 重启终端使环境变量生效

### 错误: "Model not supported"

**原因**: 指定的模型不在支持列表中

**解决方案**:
1. 检查模型名称拼写
2. 使用本文档中列出的支持模型
3. 访问硅基流动官网查看最新模型列表

### 错误: "Provider is not available"

**原因**: API Key 配置问题或网络问题

**解决方案**:
1. 验证 API Key 是否正确
2. 检查网络连接
3. 查看日志文件获取详细错误信息

## 成本优化建议

1. **选择合适的模型**: 小模型通常更便宜，足以应对大部分任务
2. **控制 token 使用**: 设置合理的 `maxTokens` 值
3. **使用缓存**: 相同请求使用缓存结果（参考 `CachedAiValidationClient`）
4. **批量处理**: 合并多个小请求为一个大请求

## 下一步

- Phase 3: 实现实际的 HTTP 请求逻辑（当前为 placeholder）
- 添加流式响应支持
- 添加更多模型配置选项
- 实现自动模型选择策略

## 相关文档

- [LLM 架构文档](LLM_ARCHITECTURE.md)
- [LLM 快速入门](LLM_QUICKSTART.md)
- [阶段4可行性分析](阶段4可行性分析.md)
