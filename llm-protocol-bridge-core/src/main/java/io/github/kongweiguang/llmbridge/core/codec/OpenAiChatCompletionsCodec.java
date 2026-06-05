package io.github.kongweiguang.llmbridge.core.codec;

import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import io.github.kongweiguang.llmbridge.core.json.JacksonUtil;
import io.github.kongweiguang.llmbridge.core.canonical.*;
import io.github.kongweiguang.llmbridge.core.stream.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * Codec for the OpenAI Chat Completions API format.
 * Handles conversion between raw OpenAI Chat JSON and the normalized model.
 * Unknown fields are preserved in rawExtra to prevent silent field dropping.
 */
@Slf4j
public class OpenAiChatCompletionsCodec implements ProtocolCodec {

    @Override
    public ApiProtocol apiProtocol() {
        return ApiProtocol.OPENAI_CHAT_COMPLETIONS;
    }

    @Override
    public CanonicalRequest normalizeRequest(JsonNode rawRequest, BridgeContext context) {
        log.debug("normalizing OpenAI Chat request: model={}", JacksonUtil.getString(rawRequest, "model"));
        CanonicalRequest req = new CanonicalRequest();
        req.setModel(JacksonUtil.getString(rawRequest, "model"));
        req.setTemperature(JacksonUtil.getDouble(rawRequest, "temperature"));
        req.setTopP(JacksonUtil.getDouble(rawRequest, "top_p"));
        req.setStream(JacksonUtil.getBoolean(rawRequest, "stream"));
        req.setFrequencyPenalty(JacksonUtil.getDouble(rawRequest, "frequency_penalty"));
        req.setPresencePenalty(JacksonUtil.getDouble(rawRequest, "presence_penalty"));
        req.setSeed(JacksonUtil.getLong(rawRequest, "seed"));
        req.setN(JacksonUtil.getInt(rawRequest, "n"));

        // max_tokens or max_completion_tokens (prefer max_completion_tokens when both present)
        Integer maxTokens = JacksonUtil.getInt(rawRequest, "max_completion_tokens");
        if (maxTokens == null) {
            maxTokens = JacksonUtil.getInt(rawRequest, "max_tokens");
        }
        req.setMaxOutputTokens(maxTokens);

        // stop
        JsonNode stopNode = rawRequest.get("stop");
        if (stopNode != null) {
            if (stopNode.isArray()) {
                List<String> stops = new ArrayList<>();
                stopNode.forEach(s -> stops.add(s.asText()));
                req.setStopSequences(stops);
            } else if (stopNode.isTextual()) {
                req.setStopSequences(List.of(stopNode.asText()));
            }
        }

        // response_format
        JsonNode rf = rawRequest.get("response_format");
        if (rf != null && rf.isObject()) {
            req.setResponseFormat((ObjectNode) rf);
        }

        // stream_options
        JsonNode so = rawRequest.get("stream_options");
        if (so != null && so.isObject()) {
            req.setStreamOptions((ObjectNode) so);
        }

        // metadata
        JsonNode meta = rawRequest.get("metadata");
        if (meta != null && meta.isObject()) {
            req.setMetadata((ObjectNode) meta);
        }

        // logit_bias
        JsonNode lb = rawRequest.get("logit_bias");
        if (lb != null && lb.isObject()) {
            req.setLogitBias((ObjectNode) lb);
        }

        // logprobs / top_logprobs
        req.setLogprobs(JacksonUtil.getBoolean(rawRequest, "logprobs"));
        req.setTopLogprobs(JacksonUtil.getInt(rawRequest, "top_logprobs"));

        // reasoning_effort
        req.setReasoningEffort(JacksonUtil.getString(rawRequest, "reasoning_effort"));

        // web_search_options
        JsonNode wso = rawRequest.get("web_search_options");
        if (wso != null && wso.isObject()) {
            req.setWebSearchOptions((ObjectNode) wso);
        }

        // prediction (speculative decoding)
        JsonNode pred = rawRequest.get("prediction");
        if (pred != null && pred.isObject()) {
            req.setPrediction((ObjectNode) pred);
        }

        // audio (output config)
        JsonNode audioCfg = rawRequest.get("audio");
        if (audioCfg != null && audioCfg.isObject()) {
            req.setAudio((ObjectNode) audioCfg);
        }

        // modalities
        JsonNode mods = rawRequest.get("modalities");
        if (mods != null && mods.isArray()) {
            List<String> modalityList = new ArrayList<>();
            mods.forEach(m -> modalityList.add(m.asText()));
            req.setModalities(modalityList);
        }

        // prompt_cache_key
        req.setPromptCacheKey(JacksonUtil.getString(rawRequest, "prompt_cache_key"));

        // store
        req.setStore(JacksonUtil.getBoolean(rawRequest, "store"));

        // parallel_tool_calls
        req.setParallelToolCalls(JacksonUtil.getBoolean(rawRequest, "parallel_tool_calls"));

        // messages
        JsonNode messagesNode = rawRequest.get("messages");
        if (messagesNode != null && messagesNode.isArray()) {
            List<CanonicalMessage> messages = new ArrayList<>();
            for (JsonNode msg : messagesNode) {
                messages.add(normalizeMessage(msg));
            }
            req.setMessages(messages);
        }

        // tools
        JsonNode toolsNode = rawRequest.get("tools");
        if (toolsNode != null && toolsNode.isArray()) {
            List<CanonicalTool> tools = new ArrayList<>();
            for (JsonNode tool : toolsNode) {
                tools.add(normalizeTool(tool));
            }
            req.setTools(tools);
        }

        // tool_choice
        JsonNode tc = rawRequest.get("tool_choice");
        if (tc != null) {
            req.setToolChoice(new CanonicalToolChoice(tc));
        }

        // extra fields - preserve everything we don't explicitly handle
        req.setRawExtra(JacksonUtil.extractExtra((ObjectNode) rawRequest,
                "model", "messages", "tools", "tool_choice", "temperature", "top_p",
                "max_tokens", "max_completion_tokens", "stop", "stream", "response_format",
                "stream_options", "metadata", "parallel_tool_calls", "frequency_penalty",
                "presence_penalty", "seed", "n", "user",
                "logit_bias", "logprobs", "top_logprobs", "reasoning_effort",
                "web_search_options", "prediction", "audio", "modalities",
                "prompt_cache_key", "store", "service_tier"));

        return req;
    }

    private CanonicalMessage normalizeMessage(JsonNode msg) {
        CanonicalMessage nm = new CanonicalMessage();
        String role = JacksonUtil.getString(msg, "role");
        nm.setRole(mapRole(role));
        nm.setName(JacksonUtil.getString(msg, "name"));

        // tool_calls (assistant message with function calls)
        JsonNode toolCallsNode = msg.get("tool_calls");
        if (toolCallsNode != null && toolCallsNode.isArray()) {
            List<CanonicalToolCall> toolCalls = new ArrayList<>();
            int idx = 0;
            for (JsonNode tc : toolCallsNode) {
                CanonicalToolCall ntc = new CanonicalToolCall();
                ntc.setId(JacksonUtil.getString(tc, "id"));
                ntc.setType(JacksonUtil.getString(tc, "type"));
                ntc.setIndex(JacksonUtil.getInt(tc, "index") != null ? JacksonUtil.getInt(tc, "index") : idx);
                JsonNode func = tc.get("function");
                if (func != null) {
                    ntc.setName(JacksonUtil.getString(func, "name"));
                    String argsStr = JacksonUtil.getString(func, "arguments");
                    ntc.setRawArguments(argsStr);
                    ntc.setArguments(JacksonUtil.tryParse(argsStr));
                    if (ntc.getArguments() == null && argsStr != null) {
                        ntc.setArguments(com.fasterxml.jackson.databind.node.TextNode.valueOf(argsStr));
                    }
                }
                // Preserve unknown tool_call fields
                ntc.setRawExtra(JacksonUtil.extractExtra((ObjectNode) tc,
                        "id", "type", "function", "index"));
                toolCalls.add(ntc);
                idx++;
            }
            nm.setToolCalls(toolCalls);
        }

        // content
        JsonNode contentNode = msg.get("content");
        if (contentNode != null) {
            List<CanonicalContentPart> parts = new ArrayList<>();
            if (contentNode.isTextual()) {
                parts.add(new TextContentPart(contentNode.asText()));
            } else if (contentNode.isArray()) {
                for (JsonNode part : contentNode) {
                    CanonicalContentPart ncp = normalizeContentPart(part);
                    if (ncp != null) {
                        parts.add(ncp);
                    }
                }
            }
            nm.setContent(parts);
        }

        // tool_call_id (for role=tool)
        nm.setToolCallId(JacksonUtil.getString(msg, "tool_call_id"));

        // Preserve unknown message fields
        nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) msg,
                "role", "name", "tool_calls", "content", "tool_call_id"));

        return nm;
    }

    private CanonicalContentPart normalizeContentPart(JsonNode part) {
        String type = JacksonUtil.getString(part, "type");
        if (type == null) return null;
        return switch (type) {
            case "text" -> {
                TextContentPart tp = new TextContentPart();
                tp.setText(JacksonUtil.getString(part, "text"));
                yield tp;
            }
            case "image_url" -> {
                ImageContentPart ip = new ImageContentPart();
                JsonNode urlNode = part.get("image_url");
                if (urlNode != null) {
                    if (urlNode.isTextual()) {
                        ip.setUrl(urlNode.asText());
                    } else if (urlNode.isObject()) {
                        ip.setUrl(JacksonUtil.getString(urlNode, "url"));
                        ip.setDetail(JacksonUtil.getString(urlNode, "detail"));
                    }
                }
                yield ip;
            }
            case "input_file" -> {
                FileContentPart fp = new FileContentPart();
                fp.setFileId(JacksonUtil.getString(part, "file_id"));
                fp.setUrl(JacksonUtil.getString(part, "url"));
                fp.setFilename(JacksonUtil.getString(part, "filename"));
                fp.setMediaType(JacksonUtil.getString(part, "media_type"));
                fp.setBase64(JacksonUtil.getString(part, "base64"));
                yield fp;
            }
            case "input_audio" -> {
                AudioContentPart ap = new AudioContentPart();
                JsonNode audioNode = part.get("input_audio");
                if (audioNode != null && audioNode.isObject()) {
                    ap.setBase64(JacksonUtil.getString(audioNode, "data"));
                    ap.setFormat(JacksonUtil.getString(audioNode, "format"));
                    ap.setMediaType("audio/" + JacksonUtil.getString(audioNode, "format"));
                }
                yield ap;
            }
            case "tool_use" -> {
                ToolCallContentPart tcp = new ToolCallContentPart();
                tcp.setId(JacksonUtil.getString(part, "id"));
                tcp.setName(JacksonUtil.getString(part, "name"));
                tcp.setArguments(part.get("input"));
                yield tcp;
            }
            case "tool_result" -> {
                ToolResultContentPart trp = new ToolResultContentPart();
                trp.setToolCallId(JacksonUtil.getString(part, "tool_use_id"));
                JsonNode content = part.get("content");
                if (content != null && content.isTextual()) {
                    trp.setContent(content.asText());
                }
                trp.setIsError(JacksonUtil.getBoolean(part, "is_error"));
                yield trp;
            }
            case "thinking" -> {
                ThinkingContentPart thp = new ThinkingContentPart();
                thp.setThinking(JacksonUtil.getString(part, "thinking"));
                thp.setSignature(JacksonUtil.getString(part, "signature"));
                yield thp;
            }
            default -> {
                // Unknown content part type - preserve as UnknownContentPart
                yield new UnknownContentPart(type, part);
            }
        };
    }

    private CanonicalTool normalizeTool(JsonNode tool) {
        CanonicalTool nt = new CanonicalTool();
        nt.setType(JacksonUtil.getString(tool, "type"));
        JsonNode func = tool.get("function");
        if (func != null) {
            nt.setName(JacksonUtil.getString(func, "name"));
            nt.setDescription(JacksonUtil.getString(func, "description"));
            nt.setInputSchema(func.get("parameters"));
            nt.setStrict(JacksonUtil.getBoolean(func, "strict"));
            // Preserve unknown function fields
            ObjectNode funcExtra = JacksonUtil.extractExtra((ObjectNode) func,
                    "name", "description", "parameters", "strict");
            if (!funcExtra.isEmpty()) {
                ObjectNode toolExtra = JacksonUtil.objectNode();
                toolExtra.set("function", funcExtra);
                nt.setRawExtra(toolExtra);
            }
        }
        // Preserve unknown tool fields
        ObjectNode toolExtra = JacksonUtil.extractExtra((ObjectNode) tool,
                "type", "function");
        if (!toolExtra.isEmpty()) {
            if (nt.getRawExtra() != null) {
                toolExtra.setAll(nt.getRawExtra());
            }
            nt.setRawExtra(toolExtra);
        }
        return nt;
    }

    private CanonicalRole mapRole(String role) {
        if (role == null) return CanonicalRole.USER;
        return switch (role) {
            case "system" -> CanonicalRole.SYSTEM;
            case "developer" -> CanonicalRole.DEVELOPER;
            case "user" -> CanonicalRole.USER;
            case "assistant" -> CanonicalRole.ASSISTANT;
            case "tool" -> CanonicalRole.TOOL;
            default -> CanonicalRole.USER;
        };
    }

    @Override
    public ObjectNode denormalizeRequest(CanonicalRequest request, BridgeContext context) {
        log.debug("denormalizing OpenAI Chat request: model={}", request.getModel());
        ObjectNode root = JacksonUtil.objectNode();
        root.put("model", request.getModel());

        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            root.put("top_p", request.getTopP());
        }
        if (request.getFrequencyPenalty() != null) {
            root.put("frequency_penalty", request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            root.put("presence_penalty", request.getPresencePenalty());
        }
        if (request.getMaxOutputTokens() != null) {
            // Output both legacy and current field names for max compatibility
            root.put("max_tokens", request.getMaxOutputTokens());
            root.put("max_completion_tokens", request.getMaxOutputTokens());
        }
        if (request.getStream() != null) {
            root.put("stream", request.getStream());
        }
        if (request.getLogitBias() != null) {
            root.set("logit_bias", request.getLogitBias());
        }
        if (request.getLogprobs() != null) {
            root.put("logprobs", request.getLogprobs());
        }
        if (request.getTopLogprobs() != null) {
            root.put("top_logprobs", request.getTopLogprobs());
        }
        if (request.getReasoningEffort() != null) {
            root.put("reasoning_effort", request.getReasoningEffort());
        }
        if (request.getWebSearchOptions() != null) {
            root.set("web_search_options", request.getWebSearchOptions());
        }
        if (request.getPrediction() != null) {
            root.set("prediction", request.getPrediction());
        }
        if (request.getAudio() != null) {
            root.set("audio", request.getAudio());
        }
        if (request.getModalities() != null && !request.getModalities().isEmpty()) {
            ArrayNode modsArr = root.putArray("modalities");
            request.getModalities().forEach(modsArr::add);
        }
        if (request.getPromptCacheKey() != null) {
            root.put("prompt_cache_key", request.getPromptCacheKey());
        }
        if (request.getStore() != null) {
            root.put("store", request.getStore());
        }
        if (request.getServiceTier() != null) {
            root.put("service_tier", request.getServiceTier());
        }
        if (request.getResponseFormat() != null) {
            root.set("response_format", request.getResponseFormat());
        }
        if (request.getStreamOptions() != null) {
            root.set("stream_options", request.getStreamOptions());
        }
        if (request.getMetadata() != null) {
            root.set("metadata", request.getMetadata());
        }
        if (request.getParallelToolCalls() != null) {
            root.put("parallel_tool_calls", request.getParallelToolCalls());
        }
        if (request.getSeed() != null) {
            root.put("seed", request.getSeed());
        }
        if (request.getN() != null) {
            root.put("n", request.getN());
        }

        // stop sequences
        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            ArrayNode stopArr = root.putArray("stop");
            request.getStopSequences().forEach(stopArr::add);
        }

        // messages
        if (request.getMessages() != null) {
            ArrayNode messagesArr = root.putArray("messages");
            for (CanonicalMessage msg : request.getMessages()) {
                messagesArr.add(denormalizeMessage(msg));
            }
        }

        // tools
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            ArrayNode toolsArr = root.putArray("tools");
            for (CanonicalTool tool : request.getTools()) {
                ObjectNode toolNode = JacksonUtil.objectNode();
                if (tool.getType() != null) {
                    toolNode.put("type", tool.getType());
                } else {
                    toolNode.put("type", "function");
                }
                ObjectNode funcNode = JacksonUtil.objectNode();
                funcNode.put("name", tool.getName());
                if (tool.getDescription() != null) {
                    funcNode.put("description", tool.getDescription());
                }
                if (tool.getInputSchema() != null) {
                    funcNode.set("parameters", tool.getInputSchema());
                }
                if (tool.getStrict() != null) {
                    funcNode.put("strict", tool.getStrict());
                }
                toolNode.set("function", funcNode);
                // Merge rawExtra back if present
                if (tool.getRawExtra() != null) {
                    JacksonUtil.deepMergeInto(toolNode, tool.getRawExtra());
                }
                toolsArr.add(toolNode);
            }
        }

        // tool_choice
        if (request.getToolChoice() != null && request.getToolChoice().getValue() != null) {
            root.set("tool_choice", mapToolChoiceToOpenAi(request.getToolChoice().getValue()));
        }

        // Merge rawExtra back
        if (request.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(root, request.getRawExtra());
        }

        return root;
    }

    private JsonNode mapToolChoiceToOpenAi(JsonNode toolChoice) {
        if (toolChoice == null || !toolChoice.isObject()) {
            return toolChoice;
        }
        String type = JacksonUtil.getString(toolChoice, "type");
        if ("tool".equals(type)) {
            ObjectNode mapped = JacksonUtil.objectNode();
            mapped.put("type", "function");
            ObjectNode function = mapped.putObject("function");
            String name = JacksonUtil.getString(toolChoice, "name");
            if (name != null) {
                function.put("name", name);
            }
            return mapped;
        }
        if ("any".equals(type)) {
            return com.fasterxml.jackson.databind.node.TextNode.valueOf("required");
        }
        if ("auto".equals(type) || "none".equals(type)) {
            return com.fasterxml.jackson.databind.node.TextNode.valueOf(type);
        }
        return toolChoice;
    }

    private ObjectNode denormalizeMessage(CanonicalMessage msg) {
        ObjectNode msgNode = JacksonUtil.objectNode();
        msgNode.put("role", mapRoleToString(msg.getRole()));

        if (msg.getName() != null) {
            msgNode.put("name", msg.getName());
        }
        if (msg.getToolCallId() != null) {
            msgNode.put("tool_call_id", msg.getToolCallId());
        }

        // tool_calls
        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            ArrayNode tcArr = msgNode.putArray("tool_calls");
            for (CanonicalToolCall tc : msg.getToolCalls()) {
                ObjectNode tcNode = JacksonUtil.objectNode();
                tcNode.put("id", tc.getId());
                tcNode.put("type", tc.getType() != null ? tc.getType() : "function");
                if (tc.getIndex() != null) {
                    tcNode.put("index", tc.getIndex());
                }
                ObjectNode funcNode = JacksonUtil.objectNode();
                funcNode.put("name", tc.getName());
                String args = tc.getRawArguments() != null ? tc.getRawArguments()
                        : (tc.getArguments() != null ? tc.getArguments().toString() : "{}");
                funcNode.put("arguments", args);
                tcNode.set("function", funcNode);
                // Merge rawExtra back
                if (tc.getRawExtra() != null) {
                    JacksonUtil.deepMergeInto(tcNode, tc.getRawExtra());
                }
                tcArr.add(tcNode);
            }
        }

        // content
        if (msg.getContent() != null) {
            if (msg.getContent().size() == 1 && msg.getContent().get(0) instanceof TextContentPart tp) {
                msgNode.put("content", tp.getText());
            } else {
                ArrayNode contentArr = msgNode.putArray("content");
                for (CanonicalContentPart part : msg.getContent()) {
                    contentArr.add(denormalizeContentPart(part));
                }
            }
        }

        // Merge rawExtra back
        if (msg.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(msgNode, msg.getRawExtra());
        }

        return msgNode;
    }

    private ObjectNode denormalizeContentPart(CanonicalContentPart part) {
        ObjectNode partNode = JacksonUtil.objectNode();
        if (part instanceof TextContentPart tp) {
            partNode.put("type", "text");
            partNode.put("text", tp.getText());
        } else if (part instanceof ImageContentPart ip) {
            partNode.put("type", "image_url");
            ObjectNode urlNode = JacksonUtil.objectNode();
            if (ip.getUrl() != null) {
                urlNode.put("url", ip.getUrl());
            } else if (ip.getBase64() != null) {
                urlNode.put("url", "data:" + (ip.getMediaType() != null ? ip.getMediaType() : "image/png")
                        + ";base64," + ip.getBase64());
            }
            if (ip.getDetail() != null) {
                urlNode.put("detail", ip.getDetail());
            }
            partNode.set("image_url", urlNode);
        } else if (part instanceof FileContentPart fp) {
            partNode.put("type", "input_file");
            if (fp.getFileId() != null) partNode.put("file_id", fp.getFileId());
            if (fp.getUrl() != null) partNode.put("url", fp.getUrl());
            if (fp.getFilename() != null) partNode.put("filename", fp.getFilename());
            if (fp.getMediaType() != null) partNode.put("media_type", fp.getMediaType());
            if (fp.getBase64() != null) partNode.put("base64", fp.getBase64());
        } else if (part instanceof AudioContentPart ap) {
            partNode.put("type", "input_audio");
            ObjectNode audioNode = JacksonUtil.objectNode();
            if (ap.getBase64() != null) audioNode.put("data", ap.getBase64());
            if (ap.getFormat() != null) audioNode.put("format", ap.getFormat());
            else if (ap.getMediaType() != null) {
                String fmt = ap.getMediaType();
                audioNode.put("format", fmt.contains("/") ? fmt.substring(fmt.indexOf('/') + 1) : fmt);
            }
            partNode.set("input_audio", audioNode);
        } else if (part instanceof ThinkingContentPart thp) {
            partNode.put("type", "thinking");
            partNode.put("thinking", thp.getThinking());
            if (thp.getSignature() != null) {
                partNode.put("signature", thp.getSignature());
            }
        } else if (part instanceof ToolCallContentPart tcp) {
            partNode.put("type", "tool_use");
            partNode.put("id", tcp.getId());
            partNode.put("name", tcp.getName());
            if (tcp.getArguments() != null) {
                partNode.set("input", tcp.getArguments());
            }
        } else if (part instanceof ToolResultContentPart trp) {
            partNode.put("type", "tool_result");
            partNode.put("tool_use_id", trp.getToolCallId());
            if (trp.getContent() != null) {
                partNode.put("content", trp.getContent());
            }
            if (trp.getIsError() != null) {
                partNode.put("is_error", trp.getIsError());
            }
        } else if (part instanceof RefusalContentPart rp) {
            partNode.put("type", "refusal");
            partNode.put("refusal", rp.getRefusal());
        } else if (part instanceof UnknownContentPart up) {
            // Pass through unknown parts as-is
            if (up.getRaw() != null) {
                return (ObjectNode) up.getRaw();
            }
            partNode.put("type", up.getOriginalType() != null ? up.getOriginalType() : "unknown");
        }
        return partNode;
    }

    private String mapRoleToString(CanonicalRole role) {
        if (role == null) return "user";
        return switch (role) {
            case SYSTEM -> "system";
            case DEVELOPER -> "developer";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    @Override
    public CanonicalResponse normalizeResponse(JsonNode rawResponse, BridgeContext context) {
        log.debug("normalizing OpenAI Chat response: id={}", JacksonUtil.getString(rawResponse, "id"));
        CanonicalResponse resp = new CanonicalResponse();
        resp.setId(JacksonUtil.getString(rawResponse, "id"));
        resp.setRequestedModel(JacksonUtil.getString(rawResponse, "model"));
        resp.setUpstreamModel(JacksonUtil.getString(rawResponse, "model"));
        resp.setCreated(JacksonUtil.getLong(rawResponse, "created"));

        // choices
        JsonNode choicesNode = rawResponse.get("choices");
        if (choicesNode != null && choicesNode.isArray() && !choicesNode.isEmpty()) {
            JsonNode choice = choicesNode.get(0);
            JsonNode messageNode = choice.get("message");
            if (messageNode != null) {
                CanonicalMessage nm = normalizeResponseMessage(messageNode);
                resp.setOutputMessages(List.of(nm));
            }
            // finish_reason
            resp.setStopReason(mapFinishReason(JacksonUtil.getString(choice, "finish_reason")));
        }

        // usage
        JsonNode usageNode = rawResponse.get("usage");
        if (usageNode != null) {
            CanonicalUsage usage = new CanonicalUsage();
            usage.setInputTokens(firstInt(usageNode, "prompt_tokens", "input_tokens"));
            usage.setOutputTokens(firstInt(usageNode, "completion_tokens", "output_tokens"));
            usage.setTotalTokens(JacksonUtil.getInt(usageNode, "total_tokens"));
            if (usage.getTotalTokens() == null
                    && usage.getInputTokens() != null
                    && usage.getOutputTokens() != null) {
                usage.setTotalTokens(usage.getInputTokens() + usage.getOutputTokens());
            }

            // Detailed usage breakdowns
            JsonNode promptDetails = usageNode.get("prompt_tokens_details");
            if (promptDetails == null) {
                promptDetails = usageNode.get("input_tokens_details");
            }
            if (promptDetails != null) {
                usage.setCachedInputTokens(JacksonUtil.getInt(promptDetails, "cached_tokens"));
                usage.setAudioInputTokens(JacksonUtil.getInt(promptDetails, "audio_tokens"));
            }
            JsonNode completionDetails = usageNode.get("completion_tokens_details");
            if (completionDetails == null) {
                completionDetails = usageNode.get("output_tokens_details");
            }
            if (completionDetails != null) {
                usage.setReasoningTokens(JacksonUtil.getInt(completionDetails, "reasoning_tokens"));
                usage.setAudioOutputTokens(JacksonUtil.getInt(completionDetails, "audio_tokens"));
            }

            // Preserve unknown usage fields
            usage.setRawExtra(JacksonUtil.extractExtra((ObjectNode) usageNode,
                    "prompt_tokens", "completion_tokens", "input_tokens", "output_tokens", "total_tokens",
                    "prompt_tokens_details", "completion_tokens_details",
                    "input_tokens_details", "output_tokens_details"));

            resp.setUsage(usage);
        }

        // Preserve unknown response fields
        resp.setRawExtra(JacksonUtil.extractExtra((ObjectNode) rawResponse,
                "id", "object", "created", "model", "choices", "usage"));

        return resp;
    }

    private CanonicalMessage normalizeResponseMessage(JsonNode messageNode) {
        CanonicalMessage nm = new CanonicalMessage();
        nm.setRole(mapRole(JacksonUtil.getString(messageNode, "role")));

        // content
        JsonNode contentNode = messageNode.get("content");
        if (contentNode != null && !contentNode.isNull()) {
            List<CanonicalContentPart> parts = new ArrayList<>();
            if (contentNode.isTextual()) {
                parts.add(new TextContentPart(contentNode.asText()));
            }
            nm.setContent(parts);
        }

        // refusal
        JsonNode refusalNode = messageNode.get("refusal");
        if (refusalNode != null && !refusalNode.isNull()) {
            if (nm.getContent() == null) {
                nm.setContent(new ArrayList<>());
            }
            nm.getContent().add(new RefusalContentPart(refusalNode.asText()));
        }

        // reasoning_content (thinking models like o1, mimo, claude)
        JsonNode reasoningNode = messageNode.get("reasoning_content");
        if (reasoningNode != null && !reasoningNode.isNull()) {
            nm.setReasoningContent(reasoningNode.asText());
        }

        // audio output
        JsonNode audioNode = messageNode.get("audio");
        if (audioNode != null && audioNode.isObject()) {
            nm.setAudio((ObjectNode) audioNode);
        }

        // tool_calls
        JsonNode toolCallsNode = messageNode.get("tool_calls");
        if (toolCallsNode != null && toolCallsNode.isArray()) {
            List<CanonicalToolCall> toolCalls = new ArrayList<>();
            int idx = 0;
            for (JsonNode tc : toolCallsNode) {
                CanonicalToolCall ntc = new CanonicalToolCall();
                ntc.setId(JacksonUtil.getString(tc, "id"));
                ntc.setType(JacksonUtil.getString(tc, "type"));
                ntc.setIndex(JacksonUtil.getInt(tc, "index") != null ? JacksonUtil.getInt(tc, "index") : idx);
                JsonNode func = tc.get("function");
                if (func != null) {
                    ntc.setName(JacksonUtil.getString(func, "name"));
                    String argsStr = JacksonUtil.getString(func, "arguments");
                    ntc.setRawArguments(argsStr);
                    ntc.setArguments(JacksonUtil.tryParse(argsStr));
                    if (ntc.getArguments() == null && argsStr != null) {
                        ntc.setArguments(com.fasterxml.jackson.databind.node.TextNode.valueOf(argsStr));
                    }
                }
                ntc.setRawExtra(JacksonUtil.extractExtra((ObjectNode) tc,
                        "id", "type", "function", "index"));
                toolCalls.add(ntc);
                idx++;
            }
            nm.setToolCalls(toolCalls);
        }

        // Preserve unknown message fields
        nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) messageNode,
                "role", "content", "refusal", "tool_calls", "reasoning_content", "audio"));

        return nm;
    }

    private String mapFinishReason(String reason) {
        if (reason == null) return "stop";
        return switch (reason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "tool_calls" -> "tool_use";
            case "content_filter" -> "refusal";
            default -> "end_turn";
        };
    }

    @Override
    public ObjectNode denormalizeResponse(CanonicalResponse response, BridgeContext context) {
        log.debug("denormalizing OpenAI Chat response: id={}", response.getId());
        ObjectNode root = JacksonUtil.objectNode();
        root.put("id", response.getId() != null ? response.getId() : "chatcmpl-" + UUID.randomUUID());
        root.put("object", "chat.completion");
        root.put("created", response.getCreated() != null ? response.getCreated() : System.currentTimeMillis() / 1000);
        root.put("model", response.getModel());

        // choices
        ArrayNode choicesArr = root.putArray("choices");
        if (response.getOutputMessages() != null && !response.getOutputMessages().isEmpty()) {
            CanonicalMessage msg = response.getOutputMessages().get(0);
            ObjectNode choice = JacksonUtil.objectNode();
            choice.put("index", 0);
            choice.set("message", denormalizeResponseMessage(msg));
            choice.put("finish_reason", mapStopReasonToFinishReason(response.getStopReason()));
            choicesArr.add(choice);
        }

        // usage
        if (response.getUsage() != null) {
            ObjectNode usageNode = JacksonUtil.objectNode();
            usageNode.put("prompt_tokens", response.getUsage().getInputTokens() != null ? response.getUsage().getInputTokens() : 0);
            usageNode.put("completion_tokens", response.getUsage().getOutputTokens() != null ? response.getUsage().getOutputTokens() : 0);
            usageNode.put("total_tokens", response.getUsage().getTotalTokens() != null ? response.getUsage().getTotalTokens() : 0);

            // Detailed usage breakdowns
            if (response.getUsage().getCachedInputTokens() != null
                    || response.getUsage().getAudioInputTokens() != null) {
                ObjectNode promptDetails = JacksonUtil.objectNode();
                if (response.getUsage().getCachedInputTokens() != null) {
                    promptDetails.put("cached_tokens", response.getUsage().getCachedInputTokens());
                }
                if (response.getUsage().getAudioInputTokens() != null) {
                    promptDetails.put("audio_tokens", response.getUsage().getAudioInputTokens());
                }
                usageNode.set("prompt_tokens_details", promptDetails);
            }
            if (response.getUsage().getReasoningTokens() != null
                    || response.getUsage().getAudioOutputTokens() != null) {
                ObjectNode completionDetails = JacksonUtil.objectNode();
                if (response.getUsage().getReasoningTokens() != null) {
                    completionDetails.put("reasoning_tokens", response.getUsage().getReasoningTokens());
                }
                if (response.getUsage().getAudioOutputTokens() != null) {
                    completionDetails.put("audio_tokens", response.getUsage().getAudioOutputTokens());
                }
                usageNode.set("completion_tokens_details", completionDetails);
            }

            // Merge rawExtra back
            if (response.getUsage().getRawExtra() != null) {
                JacksonUtil.deepMergeInto(usageNode, response.getUsage().getRawExtra());
            }

            root.set("usage", usageNode);
        }

        // Merge rawExtra back
        if (response.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(root, response.getRawExtra());
        }

        return root;
    }

    private ObjectNode denormalizeResponseMessage(CanonicalMessage msg) {
        ObjectNode msgNode = JacksonUtil.objectNode();
        msgNode.put("role", mapRoleToString(msg.getRole()));

        // content
        if (msg.getContent() != null && !msg.getContent().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            String refusal = null;
            for (CanonicalContentPart part : msg.getContent()) {
                if (part instanceof TextContentPart tp) {
                    sb.append(tp.getText());
                } else if (part instanceof RefusalContentPart rp) {
                    refusal = rp.getRefusal();
                }
            }
            if (sb.length() > 0) {
                msgNode.put("content", sb.toString());
            } else {
                msgNode.putNull("content");
            }
            if (refusal != null) {
                msgNode.put("refusal", refusal);
            }
        } else {
            msgNode.putNull("content");
        }

        // reasoning_content
        if (msg.getReasoningContent() != null) {
            msgNode.put("reasoning_content", msg.getReasoningContent());
        }

        // audio output
        if (msg.getAudio() != null) {
            msgNode.set("audio", msg.getAudio());
        }

        // tool_calls
        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            ArrayNode tcArr = msgNode.putArray("tool_calls");
            for (CanonicalToolCall tc : msg.getToolCalls()) {
                ObjectNode tcNode = JacksonUtil.objectNode();
                tcNode.put("id", tc.getId() != null ? tc.getId() : "call_" + UUID.randomUUID());
                tcNode.put("type", tc.getType() != null ? tc.getType() : "function");
                if (tc.getIndex() != null) {
                    tcNode.put("index", tc.getIndex());
                }
                ObjectNode funcNode = JacksonUtil.objectNode();
                funcNode.put("name", tc.getName());
                String args = tc.getRawArguments() != null ? tc.getRawArguments()
                        : (tc.getArguments() != null ? tc.getArguments().toString() : "{}");
                funcNode.put("arguments", args);
                tcNode.set("function", funcNode);
                if (tc.getRawExtra() != null) {
                    JacksonUtil.deepMergeInto(tcNode, tc.getRawExtra());
                }
                tcArr.add(tcNode);
            }
        }

        // Merge rawExtra back
        if (msg.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(msgNode, msg.getRawExtra());
        }

        return msgNode;
    }

    private String mapStopReasonToFinishReason(String stopReason) {
        if (stopReason == null) return "stop";
        return switch (stopReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            case "refusal" -> "content_filter";
            default -> "stop";
        };
    }

    // ===== Streaming methods =====

    @Override
    public Flux<CanonicalStreamEvent> normalizeStream(Flux<SseFrame> rawEvents, BridgeContext context) {
        return rawEvents.filter(event -> !event.isComment())
                .handle((event, sink) -> {
                    if (event.isDone()) {
                        sink.next(new CanonicalStreamEvent(CanonicalStreamEventType.DONE));
                        return;
                    }
                    CanonicalStreamEvent result = normalizeStreamEvent(event);
                    if (result != null) {
                        sink.next(result);
                    }
                });
    }

    private CanonicalStreamEvent normalizeStreamEvent(SseFrame event) {
        JsonNode data = JacksonUtil.tryParse(event.getData());
        if (data == null) {
            return null;
        }

        CanonicalStreamEvent nse = new CanonicalStreamEvent();
        nse.setRaw(data);

        String id = JacksonUtil.getString(data, "id");
        if (id != null) nse.setResponseId(id);

        JsonNode choices = data.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            Integer index = JacksonUtil.getInt(choice, "index");
            if (index != null) nse.setChoiceIndex(index);

            JsonNode delta = choice.get("delta");
            if (delta != null) {
                // Role delta -> MESSAGE_START
                String role = JacksonUtil.getString(delta, "role");
                if (role != null) {
                    nse.setType(CanonicalStreamEventType.MESSAGE_START);
                    nse.setRole(mapRole(role));
                }

                // Content delta -> TEXT_DELTA
                String content = JacksonUtil.getString(delta, "content");
                if (content != null) {
                    nse.setType(CanonicalStreamEventType.TEXT_DELTA);
                    nse.setDeltaText(content);
                }

                // Refusal delta
                String refusal = JacksonUtil.getString(delta, "refusal");
                if (refusal != null) {
                    nse.setType(CanonicalStreamEventType.REFUSAL_DELTA);
                    nse.setRefusalDelta(refusal);
                }

                // Tool calls delta
                JsonNode toolCalls = delta.get("tool_calls");
                if (toolCalls != null && toolCalls.isArray()) {
                    JsonNode tc = toolCalls.get(0);
                    if (tc != null) {
                        Integer tcIndex = JacksonUtil.getInt(tc, "index");
                        if (tcIndex != null) nse.setToolIndex(tcIndex);

                        String tcId = JacksonUtil.getString(tc, "id");
                        JsonNode func = tc.get("function");
                        String funcName = func != null ? JacksonUtil.getString(func, "name") : null;
                        String funcArgs = func != null ? JacksonUtil.getString(func, "arguments") : null;

                        if (tcId != null || funcName != null) {
                            nse.setType(CanonicalStreamEventType.TOOL_CALL_START);
                            nse.setToolCallId(tcId);
                            nse.setToolName(funcName);
                        } else if (funcArgs != null) {
                            nse.setType(CanonicalStreamEventType.TOOL_ARGUMENTS_DELTA);
                            nse.setToolArgumentsDelta(funcArgs);
                        }
                    }
                }
            }

            // Finish reason
            String finishReason = JacksonUtil.getString(choice, "finish_reason");
            if (finishReason != null) {
                nse.setType(CanonicalStreamEventType.MESSAGE_DELTA);
                nse.setStopReason(StopReasonMapper.toNormalizedFromOpenAi(finishReason));
            }
        }

        // Usage (may appear without choices)
        JsonNode usage = data.get("usage");
        if (usage != null) {
            CanonicalUsage nu = new CanonicalUsage();
            nu.setInputTokens(firstInt(usage, "prompt_tokens", "input_tokens"));
            nu.setOutputTokens(firstInt(usage, "completion_tokens", "output_tokens"));
            nu.setTotalTokens(JacksonUtil.getInt(usage, "total_tokens"));
            if (nu.getTotalTokens() == null && nu.getInputTokens() != null && nu.getOutputTokens() != null) {
                nu.setTotalTokens(nu.getInputTokens() + nu.getOutputTokens());
            }
            if (hasUsage(nu)) {
                nse.setUsage(nu);
                if (nse.getType() == null) {
                    nse.setType(CanonicalStreamEventType.USAGE_DELTA);
                }
            }
        }

        return nse.getType() != null ? nse : null;
    }

    @Override
    public Flux<SseFrame> denormalizeStream(Flux<CanonicalStreamEvent> events, BridgeContext context) {
        StreamStateTracker state = new StreamStateTracker();
        state.setCreated(System.currentTimeMillis() / 1000);
        state.setModel(context.requestedModel());
        state.setResponseId("chatcmpl-" + UUID.randomUUID());

        return events.concatMap(event -> {
            List<SseFrame> result = new ArrayList<>();

            state.apply(event);

            switch (event.getType()) {
                case START, MESSAGE_START -> {
                    state.setResponseId(event.getResponseId() != null ? event.getResponseId() : state.getResponseId());
                    ObjectNode chunk = buildChunk(state);
                    ArrayNode choices = chunk.putArray("choices");
                    ObjectNode choice = choices.addObject();
                    choice.put("index", event.getChoiceIndex() != null ? event.getChoiceIndex() : 0);
                    ObjectNode delta = choice.putObject("delta");
                    delta.put("role", "assistant");
                    choice.putNull("finish_reason");
                    result.add(new SseFrame(null, chunk.toString()));
                }
                case TEXT_DELTA -> {
                    ObjectNode chunk = buildChunk(state);
                    ArrayNode choices = chunk.putArray("choices");
                    ObjectNode choice = choices.addObject();
                    choice.put("index", event.getChoiceIndex() != null ? event.getChoiceIndex() : 0);
                    ObjectNode delta = choice.putObject("delta");
                    delta.put("content", event.getDeltaText());
                    choice.putNull("finish_reason");
                    result.add(new SseFrame(null, chunk.toString()));
                }
                case REFUSAL_DELTA -> {
                    ObjectNode chunk = buildChunk(state);
                    ArrayNode choices = chunk.putArray("choices");
                    ObjectNode choice = choices.addObject();
                    choice.put("index", event.getChoiceIndex() != null ? event.getChoiceIndex() : 0);
                    ObjectNode delta = choice.putObject("delta");
                    delta.put("refusal", event.getRefusalDelta());
                    choice.putNull("finish_reason");
                    result.add(new SseFrame(null, chunk.toString()));
                }
                case TOOL_CALL_START -> {
                    ObjectNode chunk = buildChunk(state);
                    ArrayNode choices = chunk.putArray("choices");
                    ObjectNode choice = choices.addObject();
                    choice.put("index", event.getChoiceIndex() != null ? event.getChoiceIndex() : 0);
                    ObjectNode delta = choice.putObject("delta");
                    ArrayNode toolCalls = delta.putArray("tool_calls");
                    ObjectNode tc = toolCalls.addObject();
                    tc.put("index", event.getToolIndex() != null ? event.getToolIndex() : 0);
                    tc.put("id", event.getToolCallId() != null ? event.getToolCallId() : "call_" + UUID.randomUUID());
                    tc.put("type", "function");
                    ObjectNode func = tc.putObject("function");
                    func.put("name", event.getToolName() != null ? event.getToolName() : "");
                    func.put("arguments", "");
                    choice.putNull("finish_reason");
                    result.add(new SseFrame(null, chunk.toString()));
                }
                case TOOL_ARGUMENTS_DELTA -> {
                    ObjectNode chunk = buildChunk(state);
                    ArrayNode choices = chunk.putArray("choices");
                    ObjectNode choice = choices.addObject();
                    choice.put("index", event.getChoiceIndex() != null ? event.getChoiceIndex() : 0);
                    ObjectNode delta = choice.putObject("delta");
                    ArrayNode toolCalls = delta.putArray("tool_calls");
                    ObjectNode tc = toolCalls.addObject();
                    tc.put("index", event.getToolIndex() != null ? event.getToolIndex() : 0);
                    ObjectNode func = tc.putObject("function");
                    func.put("arguments", event.getToolArgumentsDelta() != null ? event.getToolArgumentsDelta() : "");
                    choice.putNull("finish_reason");
                    result.add(new SseFrame(null, chunk.toString()));
                }
                case MESSAGE_DELTA -> {
                    // Finish chunk
                    ObjectNode chunk = buildChunk(state);
                    ArrayNode choices = chunk.putArray("choices");
                    ObjectNode choice = choices.addObject();
                    choice.put("index", event.getChoiceIndex() != null ? event.getChoiceIndex() : 0);
                    choice.putObject("delta");
                    choice.put("finish_reason",
                            StopReasonMapper.toOpenAiFinishReason(event.getStopReason()));
                    result.add(new SseFrame(null, chunk.toString()));
                }
                case USAGE_DELTA -> {
                    // Usage chunk
                    ObjectNode chunk = buildChunk(state);
                    chunk.putArray("choices");
                    if (event.getUsage() != null) {
                        ObjectNode usageNode = chunk.putObject("usage");
                        usageNode.put("prompt_tokens",
                                event.getUsage().getInputTokens() != null ? event.getUsage().getInputTokens() : 0);
                        usageNode.put("completion_tokens",
                                event.getUsage().getOutputTokens() != null ? event.getUsage().getOutputTokens() : 0);
                        usageNode.put("total_tokens",
                                event.getUsage().getTotalTokens() != null ? event.getUsage().getTotalTokens() : 0);
                    }
                    result.add(new SseFrame(null, chunk.toString()));
                }
                case DONE -> {
                    // Finish chunk
                    ObjectNode chunk = buildChunk(state);
                    ArrayNode choices = chunk.putArray("choices");
                    ObjectNode choice = choices.addObject();
                    choice.put("index", 0);
                    choice.putObject("delta");
                    choice.put("finish_reason",
                            StopReasonMapper.toOpenAiFinishReason(state.getStopReason()));
                    result.add(new SseFrame(null, chunk.toString()));

                    // Usage from state (captured from preceding MESSAGE_DELTA)
                    CanonicalUsage usage = state.getUsage();
                    if (usage != null) {
                        ObjectNode usageChunk = buildChunk(state);
                        usageChunk.putArray("choices");
                        ObjectNode usageNode = usageChunk.putObject("usage");
                        usageNode.put("prompt_tokens",
                                usage.getInputTokens() != null ? usage.getInputTokens() : 0);
                        usageNode.put("completion_tokens",
                                usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
                        usageNode.put("total_tokens",
                                usage.getTotalTokens() != null ? usage.getTotalTokens() : 0);
                        result.add(new SseFrame(null, usageChunk.toString()));
                    }

                    // [DONE]
                    result.add(StreamErrorMapper.doneEvent());
                }
                case ERROR -> {
                    result.add(StreamErrorMapper.toOpenAiChatError(
                            new BridgeException(502, "upstream_error",
                                    event.getDeltaText() != null ? event.getDeltaText() : "stream error")));
                    result.add(StreamErrorMapper.doneEvent());
                }
                case PING -> {
                    // Ignore pings for OpenAI Chat
                }
                default -> {
                    // Unknown events - ignore for OpenAI Chat
                }
            }

            return Flux.fromIterable(result);
        });
    }

    private ObjectNode buildChunk(StreamStateTracker state) {
        ObjectNode chunk = JacksonUtil.objectNode();
        chunk.put("id", state.getResponseId());
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", state.getCreated());
        chunk.put("model", state.getModel());
        return chunk;
    }

    private Integer firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            Integer value = JacksonUtil.getInt(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean hasUsage(CanonicalUsage usage) {
        return usage.getInputTokens() != null
                || usage.getOutputTokens() != null
                || usage.getTotalTokens() != null;
    }
}
