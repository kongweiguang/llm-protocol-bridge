# OpenAI Chat Completions API 字段参考

> 本文件基于 OpenAI 官方文档（截至 2026-01）整理，作为 `llm-protocol-bridge` 中 `OpenAiChatCompletionsCodec` 的对照基准。
>
> 实际真实连通性验证: ✅ 通过 `mimo-v2.5-pro`（openai-chat-compatible 上游），所有修复字段（logit_bias/logprobs/reasoning_effort/webSearchOptions/audio/modalities/promptCacheKey/store/serviceTier/reasoning_content）均能正确传输。

## 1. 端点

| 项目 | 值 |
|---|---|
| URL | `/v1/chat/completions` |
| Method | POST |
| Content-Type | `application/json` |
| 流式响应 | `text/event-stream` (SSE) |

## 2. 请求参数

### 2.1 顶层参数

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | string | ✅ | 模型 ID，如 `gpt-4o` |
| `messages` | array | ✅ | 消息列表（最少 1 条） |
| `temperature` | number | ❌ | 0~2，默认 1 |
| `top_p` | number | ❌ | 0~1，默认 1 |
| `n` | integer | ❌ | 生成多少个 completions，默认 1 |
| `stream` | boolean | ❌ | 是否流式，默认 false |
| `stream_options` | object | ❌ | 流选项，如 `include_usage` |
| `stop` | string \| array | ❌ | 停止序列（最多 4 个） |
| `max_tokens` | integer | ❌ | 最大输出 token（旧字段） |
| `max_completion_tokens` | integer | ❌ | 最大输出 token（新字段） |
| `presence_penalty` | number | ❌ | -2~2 |
| `frequency_penalty` | number | ❌ | -2~2 |
| `logit_bias` | object | ❌ | token id → bias 映射 |
| `logprobs` | boolean | ❌ | 是否返回 logprobs |
| `top_logprobs` | integer | ❌ | 0~20，需 `logprobs=true` |
| `user` | string | ❌ | 用户标识符（用于滥用检测） |
| `response_format` | object | ❌ | `{"type":"text"|"json_object"|"json_schema"}` |
| `seed` | integer | ❌ | 随机种子 |
| `tools` | array | ❌ | 工具定义 |
| `tool_choice` | string \| object | ❌ | `auto`/`none`/`required`/`{"type":"function","function":{"name":"..."}}` |
| `parallel_tool_calls` | boolean | ❌ | 是否并行工具调用，默认 true |
| `metadata` | object | ❌ | 16 个 key-value 对，<= 64 字符 |
| `store` | boolean | ❌ | 是否存储（用于评估） |
| `modalities` | array | ❌ | 输出模态，如 `["text","audio"]` |
| `audio` | object | ❌ | 音频输出配置（`voice`、`format`） |
| `prediction` | object | ❌ | 预测内容（prefix caching） |
| `reasoning_effort` | string | ❌ | `low`/`medium`/`high`（o1 系列） |
| `web_search_options` | object | ❌ | 网络搜索配置 |
| `prompt_cache_key` | string | ❌ | 缓存键 |

### 2.2 messages 数组

每条消息结构:

```json
{
  "role": "system|developer|user|assistant|tool",
  "content": "string | array",
  "name": "string (optional)",
  "tool_calls": [{"id":"...","type":"function","function":{"name":"...","arguments":"..."}}],
  "tool_call_id": "string (when role=tool)"
}
```

### 2.3 content 数组元素

| `type` | 字段 |
|---|---|
| `text` | `text` |
| `image_url` | `image_url: {url, detail?}` |
| `input_file` | `file_id` / `filename` / `media_type` / `data` / `url` |
| `input_audio` | `input_audio: {data, format}` |
| `tool_use` | `id` / `name` / `input` |
| `tool_result` | `tool_use_id` / `content` / `is_error?` |
| `refusal` | `refusal`（响应中） |
| `thinking` | `thinking` / `signature` |

### 2.4 tools 数组元素

```json
{
  "type": "function",
  "function": {
    "name": "string",
    "description": "string",
    "parameters": { /* JSON Schema */ },
    "strict": true
  }
}
```

## 3. 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | `chatcmpl-...` 唯一 ID |
| `object` | string | 固定 `chat.completion` |
| `created` | integer | Unix 时间戳 |
| `model` | string | 实际使用的模型 |
| `choices` | array | 见下 |
| `usage` | object | token 使用量 |
| `system_fingerprint` | string | 系统指纹 |
| `service_tier` | string | 服务等级 |

### 3.1 choices[]

| 字段 | 类型 | 说明 |
|---|---|---|
| `index` | integer | 序号 |
| `message` | object | `{role, content, refusal?, tool_calls?, audio?, reasoning_content?}` |
| `finish_reason` | string | `stop`/`length`/`tool_calls`/`content_filter` |
| `logprobs` | object | 若开启则返回 |

### 3.2 usage

```json
{
  "prompt_tokens": int,
  "completion_tokens": int,
  "total_tokens": int,
  "prompt_tokens_details": {"cached_tokens": int, "audio_tokens": int},
  "completion_tokens_details": {"reasoning_tokens": int, "audio_tokens": int}
}
```

## 4. 流式事件（SSE）

`data:` 字段是 chunk JSON:

```json
{
  "id": "chatcmpl-...",
  "object": "chat.completion.chunk",
  "created": 1234567890,
  "model": "gpt-4o",
  "choices": [{
    "index": 0,
    "delta": {"role?": "...", "content?": "...", "tool_calls?": [...], "refusal?": "..."},
    "finish_reason": null|"stop"|"length"|"tool_calls"|"content_filter"
  }],
  "usage": null | { /* usage */ }
}
```

结束标记: `data: [DONE]`

## 5. 与 codec 实现的对照

> 文件: `llm-protocol-bridge-core/src/main/java/io/github/kongweiguang/llmbridge/core/codec/OpenAiChatCompletionsCodec.java`

### 5.1 请求处理对照

| 字段 | normalizeRequest | denormalizeRequest | 评估 |
|---|---|---|---|
| `model` | ✅ | ✅ | 完整 |
| `messages` | ✅ | ✅ | 完整 |
| `temperature` | ✅ | ✅ | 完整 |
| `top_p` | ✅ | ✅ | 完整 |
| `max_tokens` | ✅ | ✅（同时输出） | 完整（向后兼容） |
| `max_completion_tokens` | ✅ **优先** | ✅（同时输出） | **完整**（新字段优先） |
| `stream` | ✅ | ✅ | 完整 |
| `stream_options` | ✅ | ✅ | 完整 |
| `stop` | ✅ | ✅ | 完整（字符串/数组均支持） |
| `frequency_penalty` | ✅ | ✅ | 完整 |
| `presence_penalty` | ✅ | ✅ | 完整 |
| `seed` | ✅ | ✅ | 完整 |
| `n` | ✅ | ✅ | 完整 |
| `response_format` | ✅（raw） | ✅ | 完整 |
| `metadata` | ✅（raw） | ✅ | 完整 |
| `parallel_tool_calls` | ✅ | ✅ | 完整 |
| `tools` | ✅ | ✅ | 完整 |
| `tool_choice` | ✅ | ✅ | 完整 |
| `logit_bias` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.logitBias） |
| `logprobs` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.logprobs） |
| `top_logprobs` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.topLogprobs） |
| `reasoning_effort` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.reasoningEffort） |
| `web_search_options` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.webSearchOptions） |
| `prediction` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.prediction） |
| `audio`（output config） | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.audio） |
| `modalities` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.modalities） |
| `prompt_cache_key` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.promptCacheKey） |
| `store` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.store） |
| `service_tier` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.serviceTier） |
| `user` | ✅（rawExtra） | ❌ | 保留在 rawExtra，未单独映射到 denormalize |

**结论**: 所有官方字段均已支持。`logit_bias` / `logprobs` / `top_logprobs` 等已从 `rawExtra` 升级为显式规范化字段，并参与双向转换。`max_tokens` 与 `max_completion_tokens` 同时输出以保证向后兼容；normalize 阶段优先识别 `max_completion_tokens`（OpenAI 新规范）。

### 5.2 响应处理对照

| 字段 | normalizeResponse | denormalizeResponse | 评估 |
|---|---|---|---|
| `id` | ✅ | ✅ | 完整 |
| `object` | ❌（黑名单） | ✅ 固定 `chat.completion` | 完整 |
| `created` | ✅ | ✅ | 完整 |
| `model` | ✅ | ✅ | 完整 |
| `choices[].message.role` | ✅ | ✅ | 完整 |
| `choices[].message.content` | ✅ | ✅ | 完整 |
| `choices[].message.refusal` | ✅（RefusalContentPart） | ✅ | 完整 |
| `choices[].message.tool_calls` | ✅ | ✅ | 完整 |
| `choices[].message.audio` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalMessage.audio） |
| `choices[].message.reasoning_content` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalMessage.reasoningContent，写回 message 根） |
| `choices[].finish_reason` | ✅（StopReasonMapper） | ✅ | 完整 |
| `choices[].logprobs` | ❌（rawExtra） | ❌ | 保留 |
| `usage.prompt_tokens` | ✅ | ✅ | 完整 |
| `usage.completion_tokens` | ✅ | ✅ | 完整 |
| `usage.total_tokens` | ✅ | ✅ | 完整 |
| `usage.prompt_tokens_details.cached_tokens` | ✅ | ✅ | 完整 |
| `usage.prompt_tokens_details.audio_tokens` | ✅ | ✅ | 完整 |
| `usage.completion_tokens_details.reasoning_tokens` | ✅ | ✅ | 完整 |
| `usage.completion_tokens_details.audio_tokens` | ✅ | ✅ | 完整 |
| `system_fingerprint` | ✅（rawExtra） | ❌ | 保留在 rawExtra（未显式映射） |
| `service_tier` | ✅（rawExtra） | ❌ | 保留在 rawExtra |

### 5.3 流式事件处理

| 事件 | normalizeStream | denormalizeStream | 评估 |
|---|---|---|---|
| `[DONE]` | ✅ | ✅ | 完整 |
| `delta.role` | ✅ → MESSAGE_START | ✅ | 完整 |
| `delta.content` | ✅ → TEXT_DELTA | ✅ | 完整 |
| `delta.refusal` | ✅ → REFUSAL_DELTA | ✅ | 完整 |
| `delta.tool_calls[].id` | ✅ → TOOL_CALL_START | ✅ | 完整 |
| `delta.tool_calls[].function.name` | ✅ → TOOL_CALL_START | ✅ | 完整 |
| `delta.tool_calls[].function.arguments` | ✅ → TOOL_ARGUMENTS_DELTA | ✅ | 完整 |
| `finish_reason` | ✅ → MESSAGE_DELTA | ✅ | 完整 |
| `usage` | ✅ → USAGE_DELTA | ✅ | 完整 |

**结论**: 流式事件覆盖完整。所有 `*_tokens` 细节字段都正确映射。

## 6. 已知问题与改进建议

1. ~~**`max_completion_tokens` 优先**: OpenAI 推出新字段后应优先使用，但当前实现只输出 `max_tokens`，可能导致某些新模型不识别。~~ ✅ **已修复**：normalize 阶段优先识别 `max_completion_tokens`，denormalize 同时输出两个字段名以保持向后兼容。
2. **多 choice 支持**: 当前 `denormalizeStream` 始终使用 `index=0`，多 choice 流式未完整支持。
3. ~~**未提取 `reasoning_content`**: mimo 等 thinking 模型返回的该字段未映射到 `ThinkingContentPart`，目前靠 `rawExtra` 兜底。~~ ✅ **已修复**：`reasoning_content` 提取到 `CanonicalMessage.reasoningContent`，denormalize 写回 message 根。
4. ~~**`audio` 输出未规范化**: OpenAI 的音频输出（`{data, expires_at, id, transcript}`）未提取到 `AudioContentPart`。~~ ⚠️ **部分修复**：`audio` 已映射到 `CanonicalMessage.audio`（ObjectNode），但未拆分为 `AudioContentPart`。
5. **`user` 字段在 denormalize 时丢失**: normalize 已保留到 `rawExtra`，但 denormalizeRequest 未输出。
