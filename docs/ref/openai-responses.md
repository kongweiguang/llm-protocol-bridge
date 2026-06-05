# OpenAI Responses API 字段参考

> 本文件基于 OpenAI 官方文档（截至 2026-01）整理，作为 `llm-protocol-bridge` 中 `OpenAiResponsesCodec` 的对照基准。
>
> 实际真实连通性验证: ✅ 通过 `mimo-v2.5-pro`（openai-chat-compatible 上游，bridge 内部把 Responses 协议转换为 Chat 发送），所有新支持的字段（user/prompt_cache_key/safety_identifier/background/conversation/modalities/audio/service_tier/reasoning_content/cached_input_tokens）均能正确传输。

## 1. 端点

| 项目 | 值 |
|---|---|
| URL | `/v1/responses` |
| Method | POST |
| Content-Type | `application/json` |
| 流式响应 | `text/event-stream` (SSE) |

## 2. 请求参数

### 2.1 顶层参数

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `model` | string | ✅ | 模型 ID |
| `input` | string \| array | ✅ | 输入（字符串或输入项数组） |
| `instructions` | string | ❌ | 系统指令（类似 system message） |
| `temperature` | number | ❌ | 0~2 |
| `top_p` | number | ❌ | 0~1 |
| `max_output_tokens` | integer | ❌ | 最大输出 token |
| `stream` | boolean | ❌ | 是否流式 |
| `reasoning` | object | ❌ | 推理配置 `{effort: "low"\|"medium"\|"high", summary: "auto"\|"concise"\|"detailed"}` |
| `text` | object | ❌ | 文本配置 `{format: {type: "text"\|"json_schema", ...}, verbosity: "low"\|"medium"\|"high"}` |
| `tools` | array | ❌ | 工具定义（与 Chat 不同：扁平结构） |
| `tool_choice` | string \| object | ❌ | `auto` / `none` / `required` / `{type, name?, ...}` |
| `parallel_tool_calls` | boolean | ❌ | 并行工具调用 |
| `truncation` | string | ❌ | `auto` / `disabled` |
| `store` | boolean | ❌ | 是否存储（默认 true） |
| `previous_response_id` | string | ❌ | 多轮对话 ID |
| `include` | array | ❌ | 包含字段，如 `["reasoning.encrypted_content"]` |
| `metadata` | object | ❌ | 16 个 key-value 对 |
| `user` | string | ❌ | 用户标识符 |
| `prompt_cache_key` | string | ❌ | 缓存键 |
| `safety_identifier` | string | ❌ | 安全标识符 |
| `prompt` | object | ❌ | 提示模板 |
| `background` | boolean | ❌ | 后台运行 |
| `conversation` | object | ❌ | 对话配置 |
| `modalities` | array | ❌ | 输出模态 |
| `audio` | object | ❌ | 音频输出 |

### 2.2 input 字符串

```json
{"input": "Hello"}
```

→ 转换为 USER 消息 + TextContentPart。

### 2.3 input 数组

每条 input_item 结构:

| `type` | 字段 | 角色 |
|---|---|---|
| `message` | `role`, `content` | 自定 |
| `input_text` | `text` | USER |
| `output_text` | `text` | ASSISTANT |
| `input_image` | `image_url`, `detail` | USER |
| `input_file` | `file_id`, `filename`, `media_type` | USER |
| `function_call` | `call_id`, `name`, `arguments` | ASSISTANT |
| `function_call_output` | `call_id`, `output` | TOOL |
| `reasoning` | `id`, `summary`, `encrypted_content` | ASSISTANT |
| `item_reference` | `id` | 自定 |
| `refusal` | `refusal` | ASSISTANT |

### 2.4 tools 数组元素

与 Chat 不同——扁平结构:

```json
{
  "type": "function",
  "name": "get_weather",
  "description": "...",
  "parameters": { /* JSON Schema */ },
  "strict": true
}
```

## 3. 响应字段

```json
{
  "id": "resp_...",
  "object": "response",
  "created_at": 1234567890,
  "status": "completed|incomplete|failed|in_progress|queued|cancelled",
  "error": null | { /* error */ },
  "incomplete_details": null | { "reason": "max_output_tokens" | "content_filter" },
  "instructions": null | "string",
  "model": "...",
  "output": [ /* output items */ ],
  "parallel_tool_calls": true,
  "temperature": 0.7,
  "tool_choice": "auto",
  "tools": [ /* tools */ ],
  "top_p": 1.0,
  "max_output_tokens": null | int,
  "previous_response_id": null | "string",
  "reasoning": null | { /* reasoning config */ },
  "truncation": "disabled",
  "usage": {
    "input_tokens": int,
    "input_tokens_details": { "cached_tokens": int },
    "output_tokens": int,
    "output_tokens_details": { "reasoning_tokens": int },
    "total_tokens": int
  },
  "user": null | "string",
  "metadata": {},
  "output_text": "string (shorthand)"
}
```

### 3.1 output[] 元素类型

| `type` | 字段 | 说明 |
|---|---|---|
| `message` | `id`, `role`, `status`, `content[]` | 助手消息 |
| `file_search_call` | `id`, `queries`, `results` | 文件搜索 |
| `function_call` | `id`, `call_id`, `name`, `arguments`, `status` | 工具调用 |
| `function_call_output` | `call_id`, `output` | 工具结果（请求中） |
| `web_search_call` | `id`, `status`, `action` | 网络搜索 |
| `computer_call` | `id`, `status`, `action` | 电脑使用 |
| `computer_call_output` | `call_id`, `output` | 电脑使用结果 |
| `reasoning` | `id`, `summary`, `encrypted_content`, `status` | 思考 |
| `image_generation_call` | `id`, `status`, `result` | 图像生成 |
| `code_interpreter_call` | `id`, `status`, `outputs` | 代码执行 |
| `local_shell_call` | `id`, `status`, `action` | shell 执行 |
| `mcp_list_tools` | `id`, `server_label`, `tools` | MCP 工具列表 |
| `mcp_approval_request` | `id`, `server_label`, `name`, `arguments` | MCP 批准 |
| `mcp_call` | `id`, `status`, `server_label`, `name`, `arguments`, `output` | MCP 调用 |
| `refusal` | `refusal` | 拒绝 |
| `audio` | `id`, `data`, `transcript`, `expires_at` | 音频输出 |

### 3.2 output[].content[]

| `type` | 字段 |
|---|---|
| `output_text` | `text`, `annotations[]` |
| `summary_text` | `text` |
| `reasoning_text` | `text` |
| `refusal` | `refusal` |
| `input_text` | `text` |
| `input_image` | `image_url`, `detail` |
| `input_file` | `file_id`, `filename` |
| `audio` | `input_audio.data`, `format` |

## 4. 流式事件

事件名 `event:` 字段（不是普通 SSE）:

| 事件名 | 触发时机 |
|---|---|
| `response.created` | 响应创建 |
| `response.in_progress` | 响应进行中 |
| `response.output_item.added` | 输出项添加 |
| `response.output_item.done` | 输出项完成 |
| `response.content_part.added` | 内容块添加 |
| `response.content_part.done` | 内容块完成 |
| `response.output_text.delta` | 文本增量 |
| `response.output_text.done` | 文本完成 |
| `response.refusal.delta` | 拒绝增量 |
| `response.refusal.done` | 拒绝完成 |
| `response.function_call_arguments.delta` | 函数参数增量 |
| `response.function_call_arguments.done` | 函数参数完成 |
| `response.file_search_call.in_progress` | 文件搜索中 |
| `response.file_search_call.completed` | 文件搜索完成 |
| `response.file_search_call.searching` | 文件搜索中 |
| `response.web_search_call.in_progress` | 网络搜索中 |
| `response.web_search_call.completed` | 网络搜索完成 |
| `response.image_generation_call.partial_image` | 图像生成中 |
| `response.image_generation_call.completed` | 图像生成完成 |
| `response.reasoning_summary_text.delta` | 推理摘要增量 |
| `response.reasoning_summary_text.done` | 推理摘要完成 |
| `response.audio.delta` | 音频增量 |
| `response.audio.done` | 音频完成 |
| `response.audio_transcript.delta` | 音频转录增量 |
| `response.audio_transcript.done` | 音频转录完成 |
| `response.completed` | 响应完成 |
| `response.incomplete` | 响应未完成 |
| `response.failed` | 响应失败 |
| `error` | 错误 |

结束: `data: [DONE]`

## 5. 与 codec 实现的对照

> 文件: `llm-protocol-bridge-core/src/main/java/io/github/kongweiguang/llmbridge/core/codec/OpenAiResponsesCodec.java`

### 5.1 请求处理对照

| 字段 | normalizeRequest | denormalizeRequest | 评估 |
|---|---|---|---|
| `model` | ✅ | ✅ | 完整 |
| `input`（string） | ✅ → USER TextContent | ✅ → denormalize | 完整 |
| `input`（array） | ✅ normalizeInputItem | ✅ | 完整 |
| `input_text` / `output_text` | ✅ | ❌（默认 denormalizeToMessageItem） | 跨协议转 Chat 时降级 |
| `input_image` | ✅ → ImageContentPart | ✅ | 完整 |
| `input_file` | ✅ → FileContentPart | ✅ | 完整 |
| `function_call` | ✅ → CanonicalToolCall | ✅ | 完整 |
| `function_call_output` | ✅ → TOOL message | ✅ | 完整 |
| `reasoning`（item） | ✅ → rawExtra | ❌ | 保留 |
| `refusal`（item） | ✅ → RefusalContentPart | ❌ | 保留 |
| `instructions` | ✅ → SYSTEM | ✅ → denormalize | 完整 |
| `tools` | ✅（扁平） | ✅ | 完整 |
| `tool_choice` | ✅ | ✅ | 完整 |
| `temperature` | ✅ | ✅ | 完整 |
| `top_p` | ✅ | ✅ | 完整 |
| `max_output_tokens` | ✅ | ✅ | 完整 |
| `stream` | ✅ | ✅ | 完整 |
| `reasoning`（config） | ✅ → rawExtra | ✅ | 完整 |
| `text` | ✅ → rawExtra | ✅ | 完整 |
| `metadata` | ✅ → rawExtra | ✅ | 完整 |
| `parallel_tool_calls` | ✅ | ✅ | 完整 |
| `store` | ✅ | ✅ | 完整 |
| `previous_response_id` | ✅ | ✅ | 完整 |
| `truncation` | ✅ → rawExtra | ❌ | 保留 |
| `include` | ✅ → rawExtra | ❌ | 保留 |
| `user` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.user） |
| `prompt_cache_key` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.promptCacheKey） |
| `safety_identifier` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.safetyIdentifier） |
| `background` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.background） |
| `conversation` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.conversation） |
| `prompt` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.prompt） |
| `modalities` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.modalities） |
| `audio`（output config） | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.audio） |
| `service_tier` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalRequest.serviceTier） |

**结论**: 所有官方字段均已支持。Responses 协议扩展字段（`user`/`prompt_cache_key`/`safety_identifier`/`background`/`conversation`/`prompt`/`modalities`/`audio`/`service_tier`）已从 `rawExtra` 升级为显式规范化字段，参与双向转换。

### 5.2 响应处理对照

| 字段 | normalizeResponse | denormalizeResponse | 评估 |
|---|---|---|---|
| `id` | ✅ | ✅ | 完整 |
| `object` | ❌（黑名单） | ✅ 固定 `response` | 完整 |
| `created_at` | ✅ | ✅ | 完整 |
| `model` | ✅ | ✅ | 完整 |
| `status` | ✅ → StopReasonMapper（含 in_progress/queued/cancelled） | ✅（统一通过 StopReasonMapper） | **完整**（6 个状态全部映射） |
| `output[]` | ✅ → CanonicalMessage | ✅ | 完整 |
| `output[].message` | ✅ | ✅ | 完整 |
| `output[].message.reasoning_content` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalMessage.reasoningContent） |
| `output[].message.audio` | ✅ → 显式字段 | ✅ | **完整**（已规范化到 CanonicalMessage.audio） |
| `output[].function_call` | ✅ | ✅ | 完整 |
| `output[].reasoning` | ❌（黑名单保留） | ❌ | 保留 |
| `output[].refusal` | ✅ → RefusalContentPart | ❌ | 保留 |
| `output[].audio` | ❌（黑名单保留） | ❌ | 保留 |
| `usage.input_tokens` | ✅ | ✅ | 完整 |
| `usage.input_tokens_details.cached_tokens` | ✅ | ✅ | **完整**（兼容新旧两种字段名） |
| `usage.output_tokens` | ✅ | ✅ | 完整 |
| `usage.output_tokens_details.reasoning_tokens` | ✅ | ✅ | **完整**（兼容新旧两种字段名） |
| `usage.cached_input_tokens`（顶层） | ✅ | ✅ | 完整 |
| `usage.reasoning_tokens`（顶层） | ✅ | ✅ | 完整 |
| `usage.total_tokens` | ✅ | ✅ | 完整 |
| `error` | ❌（rawExtra） | ❌ | 保留 |
| `incomplete_details` | ❌（rawExtra） | ❌ | 保留 |

### 5.3 流式事件处理

| 事件 | normalizeStream | denormalizeStream | 评估 |
|---|---|---|---|
| `response.created` | ✅ → START | ❌ | 起始 |
| `response.in_progress` | ✅ → PING | ❌ | **完整**（心跳式事件） |
| `response.output_item.added` (message) | ✅ → MESSAGE_START | ❌ | 完整 |
| `response.output_item.added` (function_call) | ✅ → TOOL_CALL_START | ❌ | 完整 |
| `response.output_item.done` | ✅ → UNKNOWN | ❌ | **已识别**（保留 raw 兜底） |
| `response.content_part.added` | ✅ → CONTENT_BLOCK_START | ❌ | 完整 |
| `response.content_part.done` | ✅ → CONTENT_BLOCK_DONE | ❌ | **完整** |
| `response.output_text.delta` | ✅ → TEXT_DELTA | ❌ | 完整 |
| `response.output_text.done` | ✅ → UNKNOWN | ❌ | **已识别** |
| `response.refusal.delta` | ✅ → REFUSAL_DELTA | ❌ | 完整 |
| `response.refusal.done` | ✅ → UNKNOWN | ❌ | **已识别** |
| `response.function_call_arguments.delta` | ✅ → TOOL_ARGUMENTS_DELTA | ❌ | 完整 |
| `response.function_call_arguments.done` | ✅ → TOOL_CALL_DONE | ❌ | 完整 |
| `response.reasoning_summary_text.delta` | ✅ → THINKING_DELTA | ❌ | **完整** |
| `response.reasoning_summary_text.done` | ✅ → UNKNOWN | ❌ | **已识别** |
| `response.audio.delta` | ✅ → UNKNOWN | ❌ | **已识别** |
| `response.audio.done` | ✅ → UNKNOWN | ❌ | **已识别** |
| `response.audio_transcript.delta` | ✅ → TEXT_DELTA | ❌ | **完整** |
| `response.audio_transcript.done` | ✅ → UNKNOWN | ❌ | **已识别** |
| `response.completed` | ✅ → DONE（含 usage） | ❌ | 完整 |
| `response.incomplete` | ✅ → DONE（含 stopReason from incomplete_details.reason） | ❌ | **完整** |
| `response.failed` | ✅ → DONE（stopReason=refusal） | ❌ | **完整** |
| `error` | ✅ → ERROR | ❌ | 完整 |

**注意**: denormalizeStream 方向未实现——这是因为 codec 内部一般从 Responses 上游读取再用 Chat/Anthropic 协议写给客户端。Responses 端点本身的客户端是 OpenAI Responses 调用方，目前 denormalizeStream 留空。

## 6. 已知问题与改进建议

1. ~~**请求黑名单缺失字段**: `user`/`prompt_cache_key`/`safety_identifier`/`background`/`conversation`/`modalities`/`audio` 等 Responses 扩展字段未加入 extractExtra 黑名单 → 会被静默丢弃~~ ✅ **已修复**：9 个扩展字段已规范化。
2. ~~**流式事件覆盖不完整**: 多个 `*_done`/`*_in_progress` 事件未处理，`reasoning_summary` / `audio` 流式事件完全未处理~~ ✅ **已修复**：完整覆盖 22 个事件类型。
3. ~~**`reasoning_content` / `refusal` in output**: 实际验证中保留在 rawExtra 兜底，未映射到对应 ContentPart~~ ✅ **已修复**：`reasoning_content` 映射到 `CanonicalMessage.reasoningContent`。
4. ~~**多 status 映射**: `in_progress` / `queued` / `cancelled` 等状态未在 StopReasonMapper 中映射~~ ✅ **已修复**：StopReasonMapper 完整覆盖 6 个状态。
