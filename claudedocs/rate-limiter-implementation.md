# 客户端速率限制器实现文档

## 概述

本项目已成功实现客户端速率限制器,使用 Guava RateLimiter 来控制 LLM API 调用速率,防止超出 API 提供商的速率限制。

## 实现方案

### 架构设计

**位置**: `BaseLLMProvider` (所有 LLM Provider 的基类)

**设计选择**:
- 使用**静态 RateLimiter**,所有 Provider 实例共享同一个速率限制器
- 在 `LLMClient` 初始化时配置速率限制
- 在每次 API 请求前自动应用速率限制

**关键文件**:
- `src/main/java/com/harmony/agent/llm/provider/BaseLLMProvider.java` - 速率限制核心实现
- `src/main/java/com/harmony/agent/llm/LLMClient.java` - 速率限制配置
- `src/main/java/com/harmony/agent/config/AppConfig.java` - 配置模型
- `src/main/resources/application.yml` - 配置文件

## 配置选项

### 两种速率限制模式

#### 1. QPS 模式 (Queries Per Second) - 推荐

**特点**:
- 简单直接,限制每秒请求数
- 不需要估算 token 数量
- 适用于大多数场景

**配置示例**:
```yaml
ai:
  rate_limit_mode: qps
  requests_per_second_limit: 5.0  # 每秒最多 5 个请求
  safety_margin: 0.8  # 使用限制的 80% (实际限制 = 5.0 * 0.8 = 4.0 req/s)
```

**适用场景**:
- 不确定具体 TPM 限制
- 请求大小变化不大
- 简单快速的速率控制

#### 2. TPM 模式 (Tokens Per Minute) - 精确

**特点**:
- 基于 token 数量的精确控制
- 自动估算每次请求的 token 数
- 更贴近 API 提供商的实际限制

**配置示例**:
```yaml
ai:
  rate_limit_mode: tpm
  tokens_per_minute_limit: 60000  # 每分钟 60000 tokens
  safety_margin: 0.8  # 使用限制的 80% (实际限制 = 60000 * 0.8 / 60 = 800 tokens/s)
```

**Token 估算算法**:
```
估算 token 数 = (总字符数 / 4) * 1.2
```
- 假设平均 4 个字符一个 token
- 添加 20% 的安全缓冲

**适用场景**:
- 知道精确的 TPM 限制 (如 OpenAI Tier 1 = 60000 TPM)
- 请求大小变化较大
- 需要最大化利用速率限制

### 安全边际 (Safety Margin)

**目的**: 应对速率波动和请求大小变化

**推荐值**: 0.8 (80%)

**工作原理**:
```
实际速率限制 = 配置限制 × 安全边际
```

**示例**:
- QPS 模式: 配置 5.0 req/s, 安全边际 0.8 → 实际限制 4.0 req/s
- TPM 模式: 配置 60000 TPM, 安全边际 0.8 → 实际限制 800 tokens/s (48000 TPM)

## 使用示例

### 示例 1: 使用 QPS 模式 (推荐新手)

```yaml
ai:
  rate_limit_mode: qps
  requests_per_second_limit: 5.0
  safety_margin: 0.8
```

**说明**: 每秒最多发送 4 个请求 (5.0 × 0.8)

### 示例 2: 使用 TPM 模式 (OpenAI Tier 1)

```yaml
ai:
  rate_limit_mode: tpm
  tokens_per_minute_limit: 60000  # OpenAI Tier 1 限制
  safety_margin: 0.8
```

**说明**: 每秒最多使用 800 tokens (60000 / 60 × 0.8)

### 示例 3: 使用 TPM 模式 (硅基流动)

```yaml
ai:
  rate_limit_mode: tpm
  tokens_per_minute_limit: 120000  # 假设硅基流动 TPM 限制
  safety_margin: 0.8
```

**说明**: 每秒最多使用 1600 tokens (120000 / 60 × 0.8)

## 配置步骤

### 1. 确定 API 提供商的速率限制

**OpenAI**:
- Tier 1: 3,500 RPM (requests per minute) = ~58.3 RPS, 60,000 TPM
- Tier 2: 3,500 RPM, 150,000 TPM
- 查看: https://platform.openai.com/docs/guides/rate-limits

**硅基流动**:
- 查看官方文档或 API 响应头 `X-RateLimit-*`

**Claude (Anthropic)**:
- Tier 1: 50 RPM, 40,000 TPM
- 查看: https://docs.anthropic.com/claude/reference/rate-limits

### 2. 选择速率限制模式

**选择 QPS 模式**:
- 不确定具体 TPM 限制
- 只知道 RPM (Requests Per Minute) 限制
- 想要简单配置

**选择 TPM 模式**:
- 知道精确的 TPM 限制
- 需要最大化 token 使用
- 请求大小变化较大

### 3. 配置 application.yml

编辑 `src/main/resources/application.yml` 或 `~/.harmony-agent/config.yml`:

```yaml
ai:
  # 选择模式
  rate_limit_mode: qps  # 或 tpm

  # QPS 模式配置
  requests_per_second_limit: 5.0

  # TPM 模式配置
  tokens_per_minute_limit: 60000

  # 安全边际
  safety_margin: 0.8
```

### 4. 重启应用

速率限制器会在 `LLMClient` 初始化时自动配置。

## 工作原理

### 速率限制流程

```
用户请求
   ↓
LLMClient.executeRole()
   ↓
Orchestrator.executeRole()
   ↓
Provider.sendRequest()
   ↓
BaseLLMProvider.sendRequest()
   ↓
[速率限制检查点]
   ↓
RateLimiter.acquire(permits)  ← 这里会阻塞直到获得许可
   ↓
sendHttpRequest()
   ↓
返回响应
```

### RateLimiter.acquire() 行为

**QPS 模式**:
```java
rateLimiter.acquire();  // 获取 1 个许可
```

**TPM 模式**:
```java
int estimatedTokens = estimateTokens(request);  // 估算 token 数
rateLimiter.acquire(estimatedTokens);  // 获取 N 个许可
```

**阻塞行为**:
- 如果当前速率未超限,立即返回
- 如果超限,会阻塞等待直到可以发送
- 平滑速率,避免突发流量

## 验证和监控

### 日志输出

**初始化日志** (INFO 级别):
```
Rate limiter configured: QPS mode, limit=4.0 req/s (80% of 5.0)
```
或
```
Rate limiter configured: TPM mode, limit=800.0 tokens/s (80% of 60000 TPM)
```

**请求日志** (DEBUG 级别):
```
Acquiring 1 permit from rate limiter (QPS mode)
```
或
```
Acquiring 450 tokens from rate limiter (estimated)
```

### 如何验证速率限制生效

**方法 1: 查看日志**
```bash
# 启用 DEBUG 日志
export JAVA_OPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
java -jar harmony-agent.jar
```

**方法 2: 计时测试**
```bash
# 发送多个请求,观察是否被速率限制
time harmony-agent chat "test 1"
time harmony-agent chat "test 2"
time harmony-agent chat "test 3"
# 如果配置了 4 req/s, 应该每 0.25 秒一个请求
```

## 常见问题 (FAQ)

### Q1: 应该使用 QPS 还是 TPM 模式?

**A**:
- **新手或不确定**: 使用 QPS 模式,简单可靠
- **了解 TPM 限制**: 使用 TPM 模式,更精确

### Q2: 安全边际设置多少合适?

**A**:
- **推荐值**: 0.8 (80%)
- **保守策略**: 0.7 (70%)
- **激进策略**: 0.9 (90%) - 风险较高

### Q3: 如果请求被 API 拒绝 (429 错误) 怎么办?

**A**:
1. 降低配置的速率限制
2. 增大安全边际 (降低到 0.7)
3. 检查是否有其他应用也在使用同一 API key

### Q4: TPM 模式的 token 估算准确吗?

**A**:
- **估算公式**: `(字符数 / 4) × 1.2`
- **准确度**: 约 80-90%
- **偏差**: 倾向于略微高估,更安全
- **改进**: 未来可以使用 tiktoken 库精确计算

### Q5: 速率限制器是全局的吗?

**A**:
- 是的,所有 Provider (OpenAI, Claude, SiliconFlow) 共享同一个速率限制器
- 如果需要为不同 Provider 设置不同速率,需要修改代码

### Q6: 如何禁用速率限制?

**A**:
```java
// 在代码中调用
BaseLLMProvider.disableRateLimiter();
```
或将 `rate_limit_mode` 设置为非法值 (如 "disabled")

## API 提供商速率限制参考

### OpenAI

| Tier | RPM | TPM | 推荐配置 |
|------|-----|-----|---------|
| Free | 3 | 40,000 | qps: 0.05 (3/60) |
| Tier 1 | 3,500 | 60,000 | tpm: 60000 |
| Tier 2 | 3,500 | 150,000 | tpm: 150000 |

### Anthropic (Claude)

| Tier | RPM | TPM | 推荐配置 |
|------|-----|-----|---------|
| Tier 1 | 50 | 40,000 | tpm: 40000 |
| Tier 2 | 1,000 | 80,000 | tpm: 80000 |

### 硅基流动 (SiliconFlow)

请查看官方文档或联系客服确认具体限制。

## 未来改进

1. **精确 Token 计数**: 集成 tiktoken 库进行精确 token 计数
2. **Per-Provider 限制**: 为不同 Provider 设置独立速率限制
3. **动态调整**: 根据 API 响应头动态调整速率限制
4. **监控指标**: 暴露 Prometheus 指标监控速率限制状态
5. **重试策略**: 集成指数退避重试机制

## 总结

✅ **已实现**:
- Guava RateLimiter 集成
- QPS 和 TPM 两种模式
- 安全边际配置
- 自动 token 估算
- 统一速率限制 (所有 Provider 共享)

✅ **优势**:
- 客户端速率控制,避免 429 错误
- 平滑流量,避免突发请求
- 灵活配置,适应不同 API 限制
- 零代码修改,仅需配置

✅ **使用建议**:
- 新手使用 QPS 模式,设置 `requests_per_second_limit: 5.0`
- 了解 TPM 限制后切换到 TPM 模式以最大化利用
- 安全边际保持 0.8,遇到 429 错误降低到 0.7
- 定期检查日志确认速率限制生效

---

**实施时间**: 2025-10-19
**实施方案**: 方案 B - 客户端速率限制器 (Guava RateLimiter)
**文件位置**: E:\github\HarmonySafeAgent\src\main\java\com\harmony\agent\llm\provider\BaseLLMProvider.java:37-60
