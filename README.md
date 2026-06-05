# LLM Protocol Bridge

LLM Protocol Bridge 是一个 Spring Boot Starter，用于在 Spring Boot 应用中快速提供 LLM 协议桥接能力。它可以把 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages 三种接口互相转换，并通过 YAML 配置多个上游模型、模型别名和路由策略。

## 简介

LLM Protocol Bridge 定位为 Spring Boot 原生的 LLM 协议桥接 Starter。你可以通过 YAML 配置多个上游 LLM provider（OpenAI、Anthropic、DeepSeek 等），然后对外统一暴露 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages 三种协议。客户端可以使用任意一种协议格式发送请求，Bridge 内部会自动完成协议转换，把请求转发到对应的上游 provider。

核心工作流程：

```
客户端请求（任意协议）
  → normalizeRequest   → CanonicalRequest（统一中间模型）
  → ModelResolver 选择上游 model-alias
  → denormalizeRequest → 上游 JSON
  → 上游 HTTP 调用
  → normalizeResponse  → CanonicalResponse
  → denormalizeResponse → 客户端 JSON
```

## 特性

- Spring Boot Starter 开箱即用
- YAML 配置多个上游 LLM provider
- 支持 OpenAI Chat Completions 协议
- 支持 OpenAI Responses 协议
- 支持 Anthropic Messages 协议
- 支持三种协议之间互相转换（9 条非流式 + 9 条流式链路）
- 支持非流式和 SSE 流式调用
- 支持 providers / model-aliases / routes 三层配置
- 支持路由策略：failover、priority、weighted、round-robin
- 支持 request-defaults（缺失填充）和 request-overrides（强制覆盖）
- 支持 headers 自定义
- 支持 tool/function calling 字段转换
- 支持 usage 和 stop reason 映射
- 支持 unknown 字段保留和安全降级
- 支持 Bearer token 鉴权

## 快速开始

### 安装依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>io.github.kongweiguang</groupId>
    <artifactId>llm-protocol-bridge-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

如果你是在本地源码中使用，请先执行：

```bash
mvn clean install
```

### application.yml 配置

推荐使用 providers / model-aliases / routes 三层配置：

```yaml
llm:
  bridge:
    enabled: true

    server:
      auth-token: ${LLM_BRIDGE_TOKEN:local-token}
      ttfb-timeout: 30s
      request-timeout: 120s

    stream:
      enabled: true
      include-usage: true
      heartbeat-enabled: true
      heartbeat-interval: 15s
      buffer-tool-arguments: true
      pass-through-unknown-events: false
      fail-on-malformed-sse: false
      max-event-size: 2097152  # 2MB

    compatibility:
      preserve-unknown-fields: true
      unsupported-media-policy: downgrade
      default-max-output-tokens: 4096   # 可选，不配置则由上游决定
      expose-thinking: false
      ignore-invalid-thinking-signature: true

    providers:
      openai-primary:
        kind: openai-responses-compatible
        endpoint:
          base-url: https://api.openai.com/v1
        authentication:
          type: bearer-token
          token: ${OPENAI_API_KEY}
        request-defaults:
          store: false
        default-headers:
          user-agent: llm-protocol-bridge

      anthropic-primary:
        kind: anthropic-messages-compatible
        endpoint:
          base-url: https://api.anthropic.com
        authentication:
          type: x-api-key
          token: ${ANTHROPIC_API_KEY}
        default-headers:
          anthropic-version: "2023-06-01"

      deepseek-primary:
        kind: openai-chat-compatible
        endpoint:
          base-url: https://api.deepseek.com/v1
        authentication:
          type: bearer-token
          token: ${DEEPSEEK_API_KEY}

    model-aliases:
      gpt-main:
        provider-ref: openai-primary
        upstream-model: gpt-4o
        request-defaults:
          temperature: 0.7

      claude-main:
        provider-ref: anthropic-primary
        upstream-model: claude-sonnet-4-6

      deepseek-chat:
        provider-ref: deepseek-primary
        upstream-model: deepseek-chat

    routes:
      coding:
        strategy: failover
        candidates:
          - model-ref: gpt-main
          - model-ref: claude-main
          - model-ref: deepseek-chat

      cheap-chat:
        strategy: priority
        candidates:
          - model-ref: deepseek-chat
          - model-ref: gpt-main
```

### 启动服务

设置环境变量并启动 Spring Boot 应用：

```bash
export LLM_BRIDGE_TOKEN=local-token
export OPENAI_API_KEY=your-openai-key
export ANTHROPIC_API_KEY=your-anthropic-key

mvn spring-boot:run
```

如果使用项目自带的测试运行时：

```bash
mvn spring-boot:run -pl llm-protocol-bridge-tests
```

服务默认监听 `8080` 端口，启动后可以通过 `/health` 端点确认服务状态：

```bash
curl http://localhost:8080/v1/health
```

## 调用示例

### OpenAI Chat Completions

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer local-token" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "coding",
    "messages": [{"role": "user", "content": "你好"}],
    "temperature": 0.7
  }'
```

### OpenAI Responses

```bash
curl http://localhost:8080/v1/responses \
  -H "Authorization: Bearer local-token" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "coding",
    "input": "用三句话介绍 Spring Boot"
  }'
```

### Anthropic Messages

```bash
curl http://localhost:8080/v1/messages \
  -H "Authorization: Bearer local-token" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "coding",
    "max_tokens": 1024,
    "messages": [{"role": "user", "content": "什么是 LLM 协议桥接"}]
  }'
```

### 流式调用

三种协议均支持 `stream: true` 进行 SSE 流式响应：

```bash
curl -N http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer local-token" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "coding",
    "stream": true,
    "messages": [{"role": "user", "content": "写一段项目介绍"}]
  }'
```

## 配置说明

所有配置均位于 `llm.bridge.*` 下。下面按配置块逐一说明每个字段的含义和实际业务用途。

---

### server — 服务端认证与超时

```yaml
llm:
  bridge:
    server:
      auth-token: ${LLM_BRIDGE_TOKEN:local-token}
      ttfb-timeout: 30s
      request-timeout: 120s
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `auth-token` | String | 空（不鉴权） | 客户端调用时需要携带的 Bearer Token。支持 `${ENV_VAR:default}` 语法注入环境变量。不配置或设为空则关闭鉴权。 |
| `ttfb-timeout` | Duration | `30s` | 首字节超时：等待上游返回第一个字节的最大时间。超过此时间未收到任何响应则中断请求并报错。 |
| `request-timeout` | Duration | `120s` | 请求总超时：整个请求的最大耗时，包含流式传输全过程。 |

**业务场景**：
- 生产部署时通过环境变量设置真实的 `auth-token`，防止未授权访问。
- `ttfb-timeout` 用于应对上游服务无响应的情况，避免请求长时间挂起。
- `request-timeout` 防止流式生成过长导致资源耗尽，根据业务需要调整（如长文生成可设为 `300s`）。

---

### stream — 流式响应配置

```yaml
llm:
  bridge:
    stream:
      enabled: true
      include-usage: true
      heartbeat-enabled: true
      heartbeat-interval: 15s
      buffer-tool-arguments: true
      pass-through-unknown-events: false
      fail-on-malformed-sse: false
      max-event-size: 2097152  # 2MB
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 是否启用流式（SSE）响应。设为 `false` 时所有 `stream: true` 请求会被转为非流式处理。 |
| `include-usage` | boolean | `true` | 流式结束时是否输出 token 用量统计（`usage` 字段），用于成本监控和日志记录。 |
| `heartbeat-enabled` | boolean | `true` | 是否在空闲时定期发送心跳 keep-alive，防止连接被中间代理（如 Nginx、Cloudflare）因超时而断开。 |
| `heartbeat-interval` | Duration | `15s` | 心跳发送间隔。生产环境通常设为 `10s`~`30s`，取决于网关的超时配置。 |
| `buffer-tool-arguments` | boolean | `true` | 是否缓冲 tool call 的增量 arguments 片段，在流式结束时组装完整 JSON 后再输出。设为 `false` 时逐片段输出。 |
| `pass-through-unknown-events` | boolean | `false` | 是否透传无法识别的 SSE 事件类型。设为 `true` 时，未知事件会原样转发给客户端。 |
| `fail-on-malformed-sse` | boolean | `false` | 遇到格式错误的 SSE 事件时是否直接报错。默认 `false` 会跳过格式错误的事件继续处理。 |
| `max-event-size` | long | `2097152` | 单个 SSE 事件的最大字节数（默认 2MB），超出则截断并报错，防止内存溢出。 |

**业务场景**：
- 心跳机制：Nginx 默认 `proxy_read_timeout 60s`，如果模型推理超过 60s 没有输出，Nginx 会断开连接。启用心跳后每隔 15s 发送一次空行，保持连接活跃。
- `include-usage` 帮助监控每次调用消耗的 token 数量，便于成本统计和配额管理。
- `pass-through-unknown-events` 在对接部分厂商的私有扩展事件时有用。

---

### compatibility — 兼容性与降级策略

```yaml
llm:
  bridge:
    compatibility:
      preserve-unknown-fields: true
      unsupported-media-policy: downgrade
      default-max-output-tokens: 4096
      expose-thinking: false
      ignore-invalid-thinking-signature: true
```

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `preserve-unknown-fields` | boolean | `true` | 是否保留源协议中 Bridge 无法识别的未知字段。保留后存入 `rawExtra`（`ObjectNode`），在反向转换时原样输出，确保协议透传时字段不丢失。 |
| `unsupported-media-policy` | String | `downgrade` | 遇到上游不支持的媒体类型（如图片发给纯文本模型）时的策略：`downgrade` 自动降级为文本描述，`error` 直接返回错误。 |
| `default-max-output-tokens` | Integer | 不设置 | 客户端未指定 `max_tokens` 且上游要求该字段时的默认值。不配置则由上游自行决定。避免因缺少必填字段导致上游报错。 |
| `expose-thinking` | boolean | `false` | 是否将模型的"思考过程"（thinking/reasoning block）暴露给不原生支持该字段的协议。设为 `true` 时，thinking 内容会作为普通文本输出。 |
| `ignore-invalid-thinking-signature` | boolean | `true` | 是否忽略 Anthropic thinking block 中无效的签名字段，避免因签名格式异常导致请求失败。 |

**业务场景**：
- `preserve-unknown-fields: true` 是最安全的设置，确保协议转换时不做静默丢弃。适用于对接多方客户端、需要完全透传的场景。
- `downgrade` 策略让不支持图片的模型自动回退为纯文本提示（如"此处为图片内容"），对客户端透明，提升用户体验。
- `expose-thinking` 默认关闭以保护模型内部推理过程。调试或特殊场景下可开启。

---

### providers — 上游提供商定义

`providers` 定义真实上游 LLM 服务，包括协议类型、连接地址、认证方式和默认请求参数。

```yaml
llm:
  bridge:
    providers:
      openai-primary:
        kind: openai-responses-compatible
        endpoint:
          base-url: https://api.openai.com/v1
        authentication:
          type: bearer-token
          token: ${OPENAI_API_KEY}
        request-defaults:
          store: false
        default-headers:
          user-agent: llm-protocol-bridge
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `kind` | Enum | 上游协议类型，决定使用哪个 `ProtocolCodec` 进行编解码。 |
| `endpoint.base-url` | String | 上游服务基础地址，Bridge 会根据 kind 自动拼接路径（如 `/chat/completions`）。 |
| `authentication.type` | String | 认证方式：`bearer-token`（Authorization: Bearer xxx）或 `x-api-key`（Anthropic 的 x-api-key 头）。 |
| `authentication.token` | String | 认证凭证，强烈建议使用 `${ENV_VAR}` 语法从环境变量读取，避免明文写入配置文件。 |
| `request-defaults` | Map | 默认请求参数，仅在客户端请求中**缺失**该字段时填充。provider 级别和 model-alias 级别可叠加。 |
| `request-overrides` | Map | 强制覆盖请求参数，无论客户端是否传入都会被覆盖。 |
| `default-headers` | Map | 每次请求自动附加的 HTTP 头。常用于设置 `user-agent`、`anthropic-version` 等必需头。 |

**`kind` 可选值：**

| kind 值 | 对应协议 | 典型上游 |
|---|---|---|
| `openai-chat-compatible` | OpenAI Chat Completions | DeepSeek、智谱 GLM、Moonshot、任何兼容 OpenAI 格式的国产大模型 |
| `openai-responses-compatible` | OpenAI Responses | OpenAI GPT-4o、GPT-4o-mini |
| `anthropic-messages-compatible` | Anthropic Messages | Claude Sonnet、Claude Opus |

**业务场景**：
- 一个 `provider` 可以被多个 `model-alias` 引用。例如同一个 OpenAI 账号下配置 `gpt-4o` 和 `gpt-4o-mini` 两个模型，共享相同的 endpoint 和认证信息。
- 国产大模型（智谱、DeepSeek、Moonshot）通常兼容 OpenAI Chat Completions 格式，`kind` 填 `openai-chat-compatible` 即可。
- `request-defaults` 和 `request-overrides` 可以在 provider 和 model-alias 两个层级配置，model-alias 级别优先级更高。

---

### model-aliases — 模型别名

`model-aliases` 定义对外暴露的模型名与真实上游模型之间的映射关系。

```yaml
llm:
  bridge:
    model-aliases:
      gpt-main:
        provider-ref: openai-primary
        upstream-model: gpt-4o
        request-defaults:
          temperature: 0.7
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `provider-ref` | String | 引用 `providers` 中定义的某个 provider 名称。 |
| `upstream-model` | String | 实际发送给上游的模型名。客户端无需知道真实模型名，实现模型切换对客户端透明。 |
| `request-defaults` | Map | 可选，model 级别的默认请求参数，只在客户端缺失时填充。优先级高于 provider 级别。 |
| `request-overrides` | Map | 可选，model 级别的强制覆盖参数。优先级高于 provider 级别。 |

**业务场景**：
- 客户端统一用 `gpt-main` 调用，后端可以悄悄将 `upstream-model` 从 `gpt-4o` 换成 `gpt-4o-mini`，实现模型升级或降级对客户端透明。
- 不同 model-alias 可以设置不同的 `request-defaults`：如代码生成场景 `temperature: 0`，创意写作场景 `temperature: 1.0`。
- 同一个上游 provider 可以配置多个 model-alias，分别对应不同参数的模型实例。

---

### routes — 路由策略

`routes` 定义虚拟模型，将多个候选模型组合在一起，通过路由策略决定实际使用哪个模型。

```yaml
llm:
  bridge:
    routes:
      coding:
        strategy: failover
        candidates:
          - model-ref: gpt-main
            weight: 70
          - model-ref: claude-main
            weight: 20
          - model-ref: deepseek-chat
            weight: 10
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `strategy` | Enum | 路由策略，见下表。 |
| `candidates` | List | 候选模型列表，每项引用一个 `model-alias`。 |
| `candidates[].model-ref` | String | 引用的 model-alias 名称。 |
| `candidates[].weight` | Integer | 可选，`weighted` 策略使用的权重，默认 1。 |
| `request-defaults` | Map | 可选，route 级别的默认请求参数（当前版本暂未在解析链路中生效，保留字段供后续扩展）。 |
| `request-overrides` | Map | 可选，route 级别的强制覆盖参数（当前版本暂未在解析链路中生效，保留字段供后续扩展）。 |

**路由策略说明：**

| strategy | 行为 | 适用场景 |
|---|---|---|
| `failover` | 按 candidates 顺序逐个尝试，当前一个失败时自动切换到下一个 | **高可用保障**：GPT-4o 挂了自动切到 Claude，再挂切到 DeepSeek |
| `priority` | 始终优先使用第一个可用模型，不尝试后续模型 | **成本优先**：优先使用便宜的 DeepSeek，只有不可用时才用 GPT |
| `weighted` | 按 `candidates[].weight` 进行加权随机选择，未配置时默认权重为 1 | **A/B 测试 / 成本控制**：配合不同候选模型的权重配置控制流量比例 |
| `round-robin` | 按顺序轮流使用每个候选模型 | **负载均衡**：均匀分配请求，避免单个 provider 过载 |

**业务场景**：
- **高可用模式**（`failover`）：适合对可用性要求高的生产环境，确保服务不中断。
- **成本优化模式**（`priority`）：将低成本模型放在第一位，高成本模型仅作为降级方案。
- **灰度发布**（`weighted`）：新模型上线时逐步增加流量比例，观察效果。
- **多账号负载均衡**（`round-robin`）：多个 OpenAI 账号配置为不同 provider，轮询调用以突破单账号速率限制。

---

### request-defaults 和 request-overrides

这两类参数可在 `provider` 和 `model-alias` 两个层级配置，model-alias 级别优先。

- **`request-defaults`**：只在客户端请求中**缺失**该字段时生效，相当于"填空"。
- **`request-overrides`**：**无论客户端传不传都会覆盖**，相当于"强制改写"。

**执行顺序**：`denormalizeRequest()` → `body` 深合并 → `request-defaults`（alias 优先，fallback 到 provider）→ `request-overrides`（alias 优先，fallback 到 provider）

**业务场景**：
- **provider 级 defaults**：OpenAI Responses 协议的 `store: false`，确保不意外存储对话历史。
- **model 级 defaults**：给 `coding` 路由下的所有模型设置 `temperature: 0`，确保代码生成结果稳定可复现。
- **model 级 overrides**：强制所有请求的 `max_tokens` 不超过 `2048`，防止输出过长消耗过多 token。

## 字段兼容说明

字段处理分为三类：

1. **精确映射**：三种协议都有等价语义的字段，例如 role、text、tool call、usage
2. **安全降级**：目标协议不支持的字段会降级成文本提示或兼容字段
3. **原样保留**：无法转换的未知字段会保留到 rawExtra，避免静默丢失

## 当前支持能力

- 文本输入输出
- 图片输入字段转换
- 文件输入字段保留和安全降级
- tool/function calling（含流式 arguments delta）
- tool result
- system/developer/user/assistant/tool role 映射
- usage 映射（含 cached/reasoning tokens）
- stop reason 映射
- 9 条非流式协议互转链路
- 9 条流式协议互转链路
- provider headers 自定义
- request-defaults / request-overrides
- providers / model-aliases / routes 三层配置
- failover / priority / weighted / round-robin 路由策略
- 非流式 fallback
- Bearer token 鉴权
- thinking/reasoning rawExtra 保留
- response_format 结构化输出保留
- SSE heartbeat

## 暂不支持或安全降级能力

以下能力根据目标 provider 能力进行安全降级：

- 音频输入输出（AudioContentPart 已定义，转换依赖目标协议支持）
- Anthropic thinking 的完整透出（默认隐藏，expose-thinking=true 可开启）
- 未实现的能力会安全降级或保留到 rawExtra，不会静默丢失

## 常见问题

### 为什么要配置 providers 和 model-aliases？

providers 表示真实上游服务，model-aliases 表示对外暴露的模型名。这样一个 provider 可以复用多个模型，避免重复配置 endpoint 和 authentication。

### routes 和 model-aliases 有什么区别？

model-aliases 是单个模型映射。routes 是虚拟模型，可以包含多个候选模型，并支持 failover、priority、weighted、round-robin 等策略。

### 为什么请求 Chat 接口也可以转发到 Anthropic？

因为项目内部会先把请求转换成 CanonicalRequest，再转换成目标 provider 所需的协议格式。

### stream=true 时 fallback 怎么处理？

流式请求不支持 fallback。流式模式下只会尝试路由选中的首选候选模型，不会自动切换到其他候选。这是为了避免已经输出部分内容后再切换模型导致不一致。非流式请求支持完整的 fallback 机制。

### 如何关闭鉴权？

不配置 `llm.bridge.server.auth-token` 或设置为空即可关闭本地鉴权。

## 开发与测试

```bash
# 运行全部测试
mvn clean test

# 运行单个测试类
mvn test -pl llm-protocol-bridge-testkit -Dtest=OpenAiChatCodecTest

# 运行集成测试
mvn test -pl llm-protocol-bridge-testkit -Dtest=LlmBridgeIntegrationTest

# 运行流式集成测试
mvn test -pl llm-protocol-bridge-testkit -Dtest=StreamingIntegrationTest
```

## 协议转换关系

| 客户端入口 | 可转发到 OpenAI Chat | 可转发到 OpenAI Responses | 可转发到 Anthropic Messages |
|---|---:|---:|---:|
| /v1/chat/completions | ✅ | ✅ | ✅ |
| /v1/responses | ✅ | ✅ | ✅ |
| /v1/messages | ✅ | ✅ | ✅ |

## 测试覆盖

| 测试类 | 测试数 |
|---|---:|
| AnthropicMessagesCodecTest | 8 |
| CrossProtocolConversionTest | 12 |
| OpenAiChatCodecTest | 10 |
| OpenAiResponsesCodecTest | 8 |
| RawExtraPreservationTest | 8 |
| RequestDefaultsOverridesTest | 5 |
| SdkCompatibilityTest | 6 |
| ToolCallConversionTest | 5 |
| UpstreamEndpointHeaderTest | 12 |
| LlmBridgeIntegrationTest | 2 |
| StreamingIntegrationTest | 12 |
| JsonMergeTest | 7 |
| ModelRouterTest | 5 |
| RoutingStrategyTest | 8 |
| **总计** | **108** |
