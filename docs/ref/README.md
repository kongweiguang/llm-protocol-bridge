# docs/ref — 协议字段参考

本目录存放三个上游 LLM 协议的字段规范与本项目 codec 实现的对照文档。

## 文件清单

| 文件 | 协议 | 适用 codec | 验证状态 |
|---|---|---|---|
| [openai-chat-completions.md](openai-chat-completions.md) | OpenAI Chat Completions | `OpenAiChatCompletionsCodec` | ✅ |
| [openai-responses.md](openai-responses.md) | OpenAI Responses | `OpenAiResponsesCodec` | ✅ |
| [anthropic-messages.md](anthropic-messages.md) | Anthropic Messages | `AnthropicMessagesCodec` | ✅ |

## 验证基线

- **单元测试**: **127 tests, 0 failures, 0 errors**（108 旧 + 19 新增 `ExtendedFieldsTest`）
- **真实连通性**: 通过 `mimo-v2.5-pro`（openai-chat-compatible 上游）真实调用三个端点全部成功
  - `POST /v1/chat/completions` (model=mimo-chat) — 返回 chat.completion + `reasoning_content` + `completion_tokens_details.reasoning_tokens`
  - `POST /v1/responses` (model=mimo-chat) — 返回 response.output + `reasoning_content` + `cached_input_tokens` + `reasoning_tokens` + `status: completed`
  - `POST /v1/messages` (model=mimo-chat) — 返回 message.content + `reasoning_content` + `stop_reason: end_turn` + `cache_creation_input_tokens: 192`

## 整体结论

### 已完全覆盖的字段
- **OpenAI Chat**: `model`/`messages`/`temperature`/`top_p`/`max_tokens`+`max_completion_tokens`(双输出)/`stream`/`stream_options`/`stop`/`frequency_penalty`/`presence_penalty`/`seed`/`n`/`response_format`/`metadata`/`parallel_tool_calls`/`tools`/`tool_choice`/`logit_bias`/`logprobs`/`top_logprobs`/`reasoning_effort`/`web_search_options`/`prediction`/`audio`/`modalities`/`prompt_cache_key`/`store`/`service_tier` 全部支持双向转换。
- **OpenAI Responses**: 同上 + `user`/`prompt_cache_key`/`safety_identifier`/`background`/`conversation`/`prompt`/`modalities`/`audio`/`service_tier`/`instructions`/`previous_response_id`/`truncation`/`include`/`text`/`reasoning`(config)/`input`/`function_call`/`function_call_output` 全部支持。
- **Anthropic Messages**: 全部官方字段 + `top_k`/`service_tier`/`inference_geo`/`speed`/`container`(string|object)/`mcp_servers`/`context_management` 全部支持。

### 响应字段增强
- **`reasoning_content`** 字段在三个协议响应中均已规范化（`CanonicalMessage.reasoningContent`），不再是 rawExtra 兜底。
- **Anthropic usage**: `cache_creation_input_tokens` 与 `cache_read_input_tokens` 独立映射，不再合并。
- **Anthropic usage**: `server_tool_use.web_search_requests` 和 `service_tier` 已规范化。
- **OpenAI Chat usage**: `prompt_tokens_details.cached_tokens` / `audio_tokens` 与 `completion_tokens_details.reasoning_tokens` / `audio_tokens` 全部支持。
- **OpenAI Responses status**: 6 个状态（`completed`/`incomplete`/`failed`/`in_progress`/`queued`/`cancelled`）全部在 StopReasonMapper 中双向映射。

### 流式事件
- **OpenAI Responses**: 完整覆盖 22 个事件类型，包括 `response.created`/`in_progress`/`output_item.added`/`output_item.done`/`content_part.added`/`content_part.done`/`output_text.delta`/`output_text.done`/`refusal.delta`/`refusal.done`/`function_call_arguments.delta`/`function_call_arguments.done`/`reasoning_summary_text.delta`/`reasoning_summary_text.done`/`audio.delta`/`audio.done`/`audio_transcript.delta`/`audio_transcript.done`/`completed`/`incomplete`/`failed`/`error`。
- **Anthropic**: 15 个事件全部识别，`citations_delta`/`compaction_delta` 标记为 UNKNOWN 但保留 raw 数据兜底。
- **OpenAI Chat**: 9 个核心事件完整。

### 已知遗留
1. **`content[].cache_control` 块级缓存控制**: 仍未提取（仅在 rawExtra 保留）。
2. **多 choice 流式 index**: `denormalizeStream` 始终 `index=0`，多 choice 输出未完整支持。
3. **Responses denormalizeStream**: 未实现（Responses 协议是单向客户端消费，bridge 中通常不出现"上游 Responses 流"的场景）。
4. **Anthropic thinking denormalizeStream**: 不输出 thinking 内容（已知设计）。
5. **OpenAI Chat `audio` 输出**: 已映射到 `CanonicalMessage.audio`（ObjectNode），但未拆分为 `AudioContentPart`。

## 不在本对照范围内的项

- 配置结构差异（`models[]` vs `providers/model-aliases/routes`）见 `IMPLEMENTATION_AUDIT.md` §10, §22
- Starter 拆分、命名规范、YAML 字段迁移等架构层面问题见 `IMPLEMENTATION_AUDIT.md`
