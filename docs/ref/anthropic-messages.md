# Anthropic Messages API 字段参考

> 本文件基于 Anthropic 官方文档（截至 2026-01）整理，作为 `llm-protocol-bridge` 中 `AnthropicMessagesCodec` 的对照基准。
>
> 实际真实连通性验证: ✅ 通过 `mimo-v2.5-pro`（openai-chat-compatible 上游，bridge 内部把 Anthropic 协议转换为 Chat 发送），所有新支持的字段（top_k/service_tier/inference_geo/speed/container/mcp_servers/context_management/cache_creation/cache_read/server_tool_use/reasoning_content）均能正确传输。

## 1. 端点

| 项目 | 值 |
|---|---|
| URL | `/v1/messages` |
| Method | POST |
| 必填 Header | `x-api-key: <KEY>`, `anthropic-version: 2023-06-01` |
| 流式响应 | `text/event-stream` (SSE) |

## 2. 请求参数

### 2.1 顶层参数

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | string | ✅ | 模型 ID，如 `claude-sonnet-4-6` |
| `messages` | array | ✅ | 消息列表（最少 1 条） |
| `max_tokens` | integer | ✅ | **必需** 最大输出 token |
| `system` | string \| array | ❌ | 系统提示 |
| `temperature` | number | ❌ | 0~1 |
| `top_p` | number | ❌ | 0~1（不与 temperature 同时使用） |
| `top_k` | integer | ❌ | 0~500 |
| `stream` | boolean | ❌ | 是否流式 |
| `stop_sequences` | array | ❌ | 停止序列 |
| `tools` | array | ❌ | 工具定义 |
| `tool_choice` | object | ❌ | `{type: "auto"\|"any"\|"tool", name?, disable_parallel_tool_use?}` |
| `metadata` | object | ❌ | `user_id` (UUID) |
| `thinking` | object | ❌ | `{type: "enabled"\|"disabled", budget_tokens?: int}` |
| `context_management` | object | ❌ | 上下文管理（Beta） |
| `container` | object \| string | ❌ | 容器配置 |
| `mcp_servers` | array | ❌ | MCP 服务器配置 |
| `service_tier` | string | ❌ | `auto` / `standard_only` |
| `inference_geo` | string | ❌ | 推理地理区域 |
| `speed` | string | ❌ | `standard` / `fast` |

### 2.2 messages 数组

`role` 必须是 `user` 或 `assistant`。Tool 结果通过 `user` 消息 + `tool_result` 内容块实现。

### 2.3 content 块类型

字符串简写: `"content": "Hello"` → 等价于 `[{"type": "text", "text": "Hello"}]`

数组元素类型:

| `type` | 必需字段 | 可选字段 | 说明 |
|---|---|---|---|
| `text` | `text` | `cache_control?` | 文本 |
| `image` | `source` | `cache_control?` | 图片 |
| `document` | `source` | `title?`, `context?`, `citations?`, `cache_control?` | 文档（PDF） |
| `audio` | `source` | — | 音频 |
| `tool_use` | `id`, `name`, `input` | `cache_control?` | 工具调用 |
| `tool_result` | `tool_use_id`, `content` | `is_error?`, `cache_control?` | 工具结果 |
| `thinking` | `thinking` | `signature?` | 思考过程 |
| `redacted_thinking` | `data` | — | 已编辑的思考 |
| `server_tool_use` | `id`, `name`, `input` | — | 服务端工具（web_search） |
| `web_search_tool_result` | `tool_use_id`, `content` | `cache_control?` | 网络搜索结果 |
| `code_execution_tool_result` | `tool_use_id`, `content` | `cache_control?` | 代码执行结果 |
| `mcp_tool_use` | `id`, `name`, `input`, `server_name` | — | MCP 工具调用 |
| `mcp_tool_result` | `tool_use_id`, `content`, `is_error?` | — | MCP 工具结果 |
| `container_upload` | `file_id` | — | 容器上传 |
| `compaction` | `content` | — | 压缩（Beta） |

### 2.4 source 子对象

```json
{ "type": "base64", "media_type": "image/png", "data": "..." }
// or
{ "type": "url", "url": "https://..." }
// or
{ "type": "file", "file_id": "..." }
// or
{ "type": "text", "text": "...", "media_type": "text/plain" }
```

### 2.5 cache_control

```json
{ "type": "ephemeral", "ttl": "5m" | "1h" }
```

### 2.6 tools 数组元素

```json
{
  "name": "get_weather",
  "description": "...",
  "input_schema": { /* JSON Schema */ },
  "cache_control": { "type": "ephemeral" }
}
```

## 3. 响应字段

```json
{
  "id": "msg_...",
  "type": "message",
  "role": "assistant",
  "model": "claude-sonnet-4-6",
  "content": [ /* content blocks */ ],
  "stop_reason": "end_turn|max_tokens|stop_sequence|tool_use|pause_turn|refusal|model_context_window_exceeded",
  "stop_sequence": null | "string",
  "usage": {
    "input_tokens": int,
    "cache_creation_input_tokens": int,
    "cache_read_input_tokens": int,
    "output_tokens": int,
    "server_tool_use": { "web_search_requests": int },
    "service_tier": "standard"
  }
}
```

### 3.1 stop_reason 取值

- `end_turn` — 模型自然结束
- `max_tokens` — 达到 max_tokens
- `stop_sequence` — 遇到 stop_sequences
- `tool_use` — 模型要调用工具
- `pause_turn` — 长对话中暂停
- `refusal` — 模型拒绝
- `model_context_window_exceeded` — 上下文窗口超限

## 4. 流式事件

`event:` 字段是事件名:

| 事件 | 字段 | 说明 |
|---|---|---|
| `message_start` | `message` | 流开始，message 含初始 usage |
| `content_block_start` | `index`, `content_block` | 内容块开始 |
| `content_block_delta` | `index`, `delta` | 内容块增量 |
| `content_block_stop` | `index` | 内容块结束 |
| `message_delta` | `delta` | 消息级更新（stop_reason + 累计 usage） |
| `message_stop` | — | 消息结束 |
| `ping` | — | 心跳 |
| `error` | `error` | 错误 |

### 4.1 delta 子类型

| `delta.type` | 字段 | 触发 |
|---|---|---|
| `text_delta` | `text` | 文本 |
| `input_json_delta` | `partial_json` | 工具参数 |
| `thinking_delta` | `thinking` | 思考 |
| `signature_delta` | `signature` | 思考签名 |
| `citations_delta` | `citation` | 引用 |
| `compaction_delta` | `content` | 压缩 |

## 5. 与 codec 实现的对照

> 文件: `llm-protocol-bridge-core/src/main/java/io/github/kongweiguang/llmbridge/core/codec/AnthropicMessagesCodec.java`

### 5.1 请求处理对照

| 字段 | normalizeRequest | denormalizeRequest | 评估 |
|---|---|---|---|
| `model` | ✅ | ✅ | 完整 |
| `messages` | ✅ | ✅ | 完整 |
| `max_tokens` | ✅ | ✅（默认 4096） | 完整 |
| `system`（string） | ✅ → SYSTEM | ✅ | 完整 |
| `system`（array） | ✅ 拼接 text 块 | ❌（只输出字符串） | 跨协议转换时丢失 cache_control |
| `temperature` | ✅ | ✅ | 完整 |
| `top_p` | ✅ | ✅ | 完整 |
| `top_k` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.topK） |
| `stream` | ✅ | ✅ | 完整 |
| `stop_sequences` | ✅ → stopSequences | ✅ | 完整 |
| `tools` | ✅ | ✅ | 完整 |
| `tool_choice` | ✅ | ✅ | 完整 |
| `metadata` | ✅ | ✅ | 完整 |
| `thinking` | ✅ → reasoning | ✅ | 完整 |
| `container` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.container；支持 string 与 object） |
| `mcp_servers` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.mcpServers） |
| `service_tier` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.serviceTier） |
| `inference_geo` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.inferenceGeo） |
| `speed` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.speed） |
| `context_management` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.contextManagement） |
| `cache_control` (on block) | ❌（仅 rawExtra） | ❌ | 仍为潜在问题：未提取到 CanonicalRequest |

**结论**: 所有顶级请求字段均已支持。`top_k`/`service_tier`/`inference_geo`/`speed`/`container`/`mcp_servers`/`context_management` 已从 `rawExtra` 升级为显式规范化字段，参与双向转换。块级 `cache_control` 仍为已知遗留点。

### 5.2 响应处理对照

| 字段 | normalizeResponse | denormalizeResponse | 评估 |
|---|---|---|---|
| `id` | ✅ | ✅ | 完整 |
| `type` | ❌（黑名单） | ✅ 固定 `message` | 完整 |
| `role` | ❌（黑名单） | ✅ 固定 `assistant` | 完整 |
| `model` | ✅ | ✅ | 完整 |
| `content[]` | ✅ | ✅ | 完整 |
| `content[].text` | ✅ → TextContentPart | ✅ | 完整 |
| `content[].tool_use` | ✅ → CanonicalToolCall | ✅ | 完整 |
| `content[].thinking` | ✅ → ThinkingContentPart | ✅ | 完整 |
| `content[].redacted_thinking` | ✅ → UnknownContentPart | ❌ | 保留 |
| `content[].server_tool_use` | ✅ → UnknownContentPart | ❌ | 保留 |
| `content[].web_search_tool_result` | ✅ → UnknownContentPart | ❌ | 保留 |
| `content[].code_execution_tool_result` | ✅ → UnknownContentPart | ❌ | 保留 |
| `content[].mcp_tool_use` | ❌（rawExtra 保留） | ❌ | 保留 |
| `content[].mcp_tool_result` | ❌（rawExtra 保留） | ❌ | 保留 |
| `content[].container_upload` | ❌（rawExtra 保留） | ❌ | 保留 |
| `content[].compaction` | ❌（rawExtra 保留） | ❌ | 保留 |
| `content[].audio` | ❌（rawExtra 保留） | ❌ | 保留 |
| `reasoning_content`（响应级） | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalMessage.reasoningContent） |
| `stop_reason` | ✅ | ✅（默认 end_turn） | 完整 |
| `stop_sequence` | ❌（黑名单） | ❌ | 保留 |
| `usage.input_tokens` | ✅ | ✅ | 完整 |
| `usage.output_tokens` | ✅ | ✅ | 完整 |
| `usage.cache_creation_input_tokens` | ✅ → 显式字段 | ✅ | **完整**（独立字段，不再合并） |
| `usage.cache_read_input_tokens` | ✅ → 显式字段 | ✅ | **完整**（独立字段，不再合并） |
| `usage.cachedInputTokens`（合并态） | ✅（由两个 cache 字段合并） | ✅（回写时按独立字段） | 旧字段保留，融合到两个独立字段 |
| `usage.server_tool_use.web_search_requests` | ✅ → 显式字段 | ✅（输出 `server_tool_use: {web_search_requests}`） | **完整**（已规范化到 CanonicalUsage.webSearchRequests） |
| `usage.service_tier` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalUsage.serviceTier） |

### 5.3 流式事件处理

| 事件 | normalizeStream | denormalizeStream | 评估 |
|---|---|---|---|
| `message_start` | ✅ → START（带初始 usage） | ✅ 生成 `message_start` | 完整 |
| `content_block_start`（text） | ✅ → CONTENT_BLOCK_START | ✅ 自动打开/关闭 text 块 | 完整 |
| `content_block_start`（tool_use） | ✅ → TOOL_CALL_START | ✅ | 完整 |
| `content_block_start`（thinking） | ✅ → CONTENT_BLOCK_START | ❌（无 thinking 输出） | **denormalize 不输出 thinking**（已知设计） |
| `content_block_delta.text_delta` | ✅ → TEXT_DELTA | ✅ | 完整 |
| `content_block_delta.input_json_delta` | ✅ → TOOL_ARGUMENTS_DELTA | ✅ | 完整 |
| `content_block_delta.thinking_delta` | ✅ → THINKING_DELTA | ✅ | 完整 |
| `content_block_delta.signature_delta` | ✅ → THINKING_SIGNATURE | ✅ | 完整 |
| `content_block_delta.citations_delta` | ✅ → UNKNOWN（保留 raw） | ❌ | **已识别**（未单独映射事件类型） |
| `content_block_delta.compaction_delta` | ✅ → UNKNOWN（保留 raw） | ❌ | **已识别**（未单独映射事件类型） |
| `content_block_stop` | ✅ → CONTENT_BLOCK_DONE | ✅ | 完整 |
| `message_delta` | ✅ → MESSAGE_DELTA（带 stop_reason + usage） | ✅ | 完整 |
| `message_stop` | ✅ → DONE | ✅ | 完整 |
| `ping` | ✅ → PING | ✅ | 完整 |
| `error` | ✅ → ERROR | ✅ | 完整 |

## 6. 已知问题与改进建议

1. ~~**请求黑名单缺失字段**: `top_k`/`inference_geo`/`speed`/`context_management` 等被静默丢弃~~ ✅ **已修复**：7 个扩展字段已规范化。
2. **`system` 数组格式**: 多个 text 块（含 `cache_control`）拼接为字符串输出到上游，丢失了 `cache_control` 标记。
3. **`content[].cache_control`**: 块级缓存控制未提取（应作为 rawExtra 保留）。
4. **thinking 块在 denormalizeStream 中**: 不输出 thinking 内容（可能是有意设计，但需确认）。
5. ~~**`compaction_delta` / `citations_delta`**: 不处理~~ ✅ **已识别**：标记为 UNKNOWN 但保留 raw 数据。
6. ~~**usage 字段合并**: `cache_read_input_tokens` 被合并到 `cache_creation_input_tokens` 字段，对外不再区分~~ ✅ **已修复**：`cache_creation_input_tokens` 与 `cache_read_input_tokens` 独立映射，denormalize 阶段独立输出。
