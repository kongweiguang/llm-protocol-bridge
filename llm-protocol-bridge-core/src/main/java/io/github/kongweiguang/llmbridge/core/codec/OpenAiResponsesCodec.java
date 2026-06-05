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
 * Codec for the OpenAI Responses API format.
 * Handles conversion between raw OpenAI Responses JSON and the normalized model.
 * Unknown fields are preserved in rawExtra to prevent silent field dropping.
 */
@Slf4j
public class OpenAiResponsesCodec implements ProtocolCodec {

    @Override
    public ApiProtocol apiProtocol() {
        return ApiProtocol.OPENAI_RESPONSES;
    }

    @Override
    public CanonicalRequest normalizeRequest(JsonNode rawRequest, BridgeContext context) {
        log.debug("normalizing OpenAI Responses request: model={}", JacksonUtil.getString(rawRequest, "model"));
        CanonicalRequest req = new CanonicalRequest();
        req.setModel(JacksonUtil.getString(rawRequest, "model"));
        req.setTemperature(JacksonUtil.getDouble(rawRequest, "temperature"));
        req.setTopP(JacksonUtil.getDouble(rawRequest, "top_p"));
        req.setStream(JacksonUtil.getBoolean(rawRequest, "stream"));
        req.setMaxOutputTokens(JacksonUtil.getInt(rawRequest, "max_output_tokens"));

        // reasoning
        JsonNode reasoningNode = rawRequest.get("reasoning");
        if (reasoningNode != null && reasoningNode.isObject()) {
            req.setReasoning((ObjectNode) reasoningNode);
        }

        // text
        JsonNode textNode = rawRequest.get("text");
        if (textNode != null && textNode.isObject()) {
            req.setText((ObjectNode) textNode);
        }

        // metadata
        JsonNode metadataNode = rawRequest.get("metadata");
        if (metadataNode != null && metadataNode.isObject()) {
            req.setMetadata((ObjectNode) metadataNode);
        }

        // parallel_tool_calls
        req.setParallelToolCalls(JacksonUtil.getBoolean(rawRequest, "parallel_tool_calls"));

        // store
        req.setStore(JacksonUtil.getBoolean(rawRequest, "store"));

        // previous_response_id
        req.setPreviousResponseId(JacksonUtil.getString(rawRequest, "previous_response_id"));

        // response_format
        JsonNode rf = rawRequest.get("response_format");
        if (rf != null && rf.isObject()) {
            req.setResponseFormat((ObjectNode) rf);
        }

        // user
        req.setUser(JacksonUtil.getString(rawRequest, "user"));

        // prompt_cache_key
        req.setPromptCacheKey(JacksonUtil.getString(rawRequest, "prompt_cache_key"));

        // safety_identifier
        req.setSafetyIdentifier(JacksonUtil.getString(rawRequest, "safety_identifier"));

        // background
        req.setBackground(JacksonUtil.getBoolean(rawRequest, "background"));

        // conversation
        JsonNode conv = rawRequest.get("conversation");
        if (conv != null && conv.isObject()) {
            req.setConversation((ObjectNode) conv);
        }

        // prompt
        JsonNode prompt = rawRequest.get("prompt");
        if (prompt != null && prompt.isObject()) {
            req.setPrompt((ObjectNode) prompt);
        }

        // modalities
        JsonNode mods = rawRequest.get("modalities");
        if (mods != null && mods.isArray()) {
            List<String> modalityList = new ArrayList<>();
            mods.forEach(m -> modalityList.add(m.asText()));
            req.setModalities(modalityList);
        }

        // audio
        JsonNode audio = rawRequest.get("audio");
        if (audio != null && audio.isObject()) {
            req.setAudio((ObjectNode) audio);
        }

        // service_tier
        req.setServiceTier(JacksonUtil.getString(rawRequest, "service_tier"));

        List<CanonicalMessage> messages = new ArrayList<>();

        // instructions -> SYSTEM message
        String instructions = JacksonUtil.getString(rawRequest, "instructions");
        if (instructions != null && !instructions.isEmpty()) {
            messages.add(new CanonicalMessage(CanonicalRole.SYSTEM,
                    List.of(new TextContentPart(instructions))));
        }

        // input
        JsonNode inputNode = rawRequest.get("input");
        if (inputNode != null) {
            if (inputNode.isTextual()) {
                // Simple string input -> USER message
                messages.add(new CanonicalMessage(CanonicalRole.USER,
                        List.of(new TextContentPart(inputNode.asText()))));
            } else if (inputNode.isArray()) {
                for (JsonNode item : inputNode) {
                    CanonicalMessage nm = normalizeInputItem(item);
                    if (nm != null) {
                        messages.add(nm);
                    }
                }
            }
        }
        req.setMessages(messages);

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

        // Preserve unknown fields
        req.setRawExtra(JacksonUtil.extractExtra((ObjectNode) rawRequest,
                "model", "input", "instructions", "tools", "tool_choice",
                "temperature", "top_p", "max_output_tokens", "stream",
                "reasoning", "text", "metadata", "parallel_tool_calls",
                "store", "previous_response_id", "response_format",
                "include", "truncation",
                "user", "prompt_cache_key", "safety_identifier",
                "background", "conversation", "prompt", "modalities", "audio",
                "service_tier"));

        return req;
    }

    private CanonicalMessage normalizeInputItem(JsonNode item) {
        String type = JacksonUtil.getString(item, "type");
        if (type == null) return null;

        return switch (type) {
            case "message" -> normalizeMessageItem(item);
            case "input_text" -> {
                TextContentPart tp = new TextContentPart();
                tp.setText(JacksonUtil.getString(item, "text"));
                CanonicalMessage nm = new CanonicalMessage(CanonicalRole.USER, List.of(tp));
                nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item, "type", "text"));
                yield nm;
            }
            case "output_text" -> {
                TextContentPart tp = new TextContentPart();
                tp.setText(JacksonUtil.getString(item, "text"));
                CanonicalMessage nm = new CanonicalMessage(CanonicalRole.ASSISTANT, List.of(tp));
                nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item, "type", "text"));
                yield nm;
            }
            case "input_image" -> {
                ImageContentPart ip = new ImageContentPart();
                ip.setUrl(JacksonUtil.getString(item, "image_url"));
                ip.setDetail(JacksonUtil.getString(item, "detail"));
                CanonicalMessage nm = new CanonicalMessage(CanonicalRole.USER, List.of(ip));
                nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item, "type", "image_url", "detail"));
                yield nm;
            }
            case "input_file" -> {
                FileContentPart fp = new FileContentPart();
                fp.setFileId(JacksonUtil.getString(item, "file_id"));
                fp.setUrl(JacksonUtil.getString(item, "url"));
                fp.setFilename(JacksonUtil.getString(item, "filename"));
                fp.setMediaType(JacksonUtil.getString(item, "media_type"));
                CanonicalMessage nm = new CanonicalMessage(CanonicalRole.USER, List.of(fp));
                nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item,
                        "type", "file_id", "url", "filename", "media_type"));
                yield nm;
            }
            case "function_call" -> normalizeFunctionCallItem(item);
            case "function_call_output" -> normalizeFunctionCallOutputItem(item);
            case "reasoning" -> {
                // Preserve reasoning items as rawExtra
                CanonicalMessage nm = new CanonicalMessage(CanonicalRole.ASSISTANT, List.of());
                ObjectNode extra = JacksonUtil.objectNode();
                extra.set("reasoning_item", item);
                nm.setRawExtra(extra);
                yield nm;
            }
            case "refusal" -> {
                RefusalContentPart rp = new RefusalContentPart(JacksonUtil.getString(item, "refusal"));
                CanonicalMessage nm = new CanonicalMessage(CanonicalRole.ASSISTANT, List.of(rp));
                nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item, "type", "refusal"));
                yield nm;
            }
            default -> {
                // Unknown item type - preserve as rawExtra
                CanonicalMessage nm = new CanonicalMessage(CanonicalRole.USER, List.of());
                ObjectNode extra = JacksonUtil.objectNode();
                extra.set("unknown_item", item);
                nm.setRawExtra(extra);
                yield nm;
            }
        };
    }

    private CanonicalMessage normalizeMessageItem(JsonNode item) {
        CanonicalMessage nm = new CanonicalMessage();
        nm.setRole(mapRole(JacksonUtil.getString(item, "role")));

        JsonNode contentNode = item.get("content");
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

        // Preserve unknown fields
        nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item, "type", "role", "content"));

        return nm;
    }

    private CanonicalMessage normalizeFunctionCallItem(JsonNode item) {
        CanonicalMessage nm = new CanonicalMessage();
        nm.setRole(CanonicalRole.ASSISTANT);
        CanonicalToolCall tc = new CanonicalToolCall();
        tc.setId(JacksonUtil.getString(item, "call_id"));
        tc.setName(JacksonUtil.getString(item, "name"));
        tc.setType("function");
        String argsStr = JacksonUtil.getString(item, "arguments");
        tc.setRawArguments(argsStr);
        tc.setArguments(JacksonUtil.tryParse(argsStr));
        if (tc.getArguments() == null && argsStr != null) {
            tc.setArguments(com.fasterxml.jackson.databind.node.TextNode.valueOf(argsStr));
        }
        // Preserve unknown fields
        tc.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item,
                "type", "call_id", "name", "arguments"));
        nm.setToolCalls(List.of(tc));
        nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item, "type"));
        return nm;
    }

    private CanonicalMessage normalizeFunctionCallOutputItem(JsonNode item) {
        CanonicalMessage nm = new CanonicalMessage();
        nm.setRole(CanonicalRole.TOOL);
        nm.setToolCallId(JacksonUtil.getString(item, "call_id"));
        String output = JacksonUtil.getString(item, "output");
        if (output != null) {
            nm.setContent(List.of(new TextContentPart(output)));
        }
        // Preserve unknown fields
        nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item,
                "type", "call_id", "output"));
        return nm;
    }

    private CanonicalContentPart normalizeContentPart(JsonNode part) {
        String type = JacksonUtil.getString(part, "type");
        if (type == null) return null;
        return switch (type) {
            case "input_text", "output_text" -> {
                TextContentPart tp = new TextContentPart();
                tp.setText(JacksonUtil.getString(part, "text"));
                yield tp;
            }
            case "input_image" -> {
                ImageContentPart ip = new ImageContentPart();
                ip.setUrl(JacksonUtil.getString(part, "image_url"));
                ip.setDetail(JacksonUtil.getString(part, "detail"));
                yield ip;
            }
            case "input_file" -> {
                FileContentPart fp = new FileContentPart();
                fp.setFileId(JacksonUtil.getString(part, "file_id"));
                fp.setUrl(JacksonUtil.getString(part, "url"));
                fp.setFilename(JacksonUtil.getString(part, "filename"));
                fp.setMediaType(JacksonUtil.getString(part, "media_type"));
                yield fp;
            }
            default -> {
                // Unknown content part - preserve as UnknownContentPart
                yield new UnknownContentPart(type, part);
            }
        };
    }

    private CanonicalTool normalizeTool(JsonNode tool) {
        CanonicalTool nt = new CanonicalTool();
        nt.setType(JacksonUtil.getString(tool, "type"));
        nt.setName(JacksonUtil.getString(tool, "name"));
        nt.setDescription(JacksonUtil.getString(tool, "description"));
        nt.setInputSchema(tool.get("parameters"));
        nt.setStrict(JacksonUtil.getBoolean(tool, "strict"));
        // Preserve unknown fields
        nt.setRawExtra(JacksonUtil.extractExtra((ObjectNode) tool,
                "type", "name", "description", "parameters", "strict"));
        return nt;
    }

    private CanonicalRole mapRole(String role) {
        if (role == null) return CanonicalRole.USER;
        return switch (role) {
            case "system" -> CanonicalRole.SYSTEM;
            case "developer" -> CanonicalRole.DEVELOPER;
            case "user" -> CanonicalRole.USER;
            case "assistant" -> CanonicalRole.ASSISTANT;
            default -> CanonicalRole.USER;
        };
    }

    @Override
    public ObjectNode denormalizeRequest(CanonicalRequest request, BridgeContext context) {
        log.debug("denormalizing OpenAI Responses request: model={}", request.getModel());
        ObjectNode root = JacksonUtil.objectNode();
        root.put("model", request.getModel());

        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            root.put("top_p", request.getTopP());
        }
        if (request.getMaxOutputTokens() != null) {
            root.put("max_output_tokens", request.getMaxOutputTokens());
        }
        if (request.getStream() != null) {
            root.put("stream", request.getStream());
        }
        if (request.getReasoning() != null) {
            root.set("reasoning", request.getReasoning());
        }
        if (request.getText() != null) {
            root.set("text", request.getText());
        }
        if (request.getMetadata() != null) {
            root.set("metadata", request.getMetadata());
        }
        if (request.getParallelToolCalls() != null) {
            root.put("parallel_tool_calls", request.getParallelToolCalls());
        }
        if (request.getStore() != null) {
            root.put("store", request.getStore());
        }
        if (request.getPreviousResponseId() != null) {
            root.put("previous_response_id", request.getPreviousResponseId());
        }
        if (request.getResponseFormat() != null) {
            root.set("response_format", request.getResponseFormat());
        }
        if (request.getUser() != null) {
            root.put("user", request.getUser());
        }
        if (request.getPromptCacheKey() != null) {
            root.put("prompt_cache_key", request.getPromptCacheKey());
        }
        if (request.getSafetyIdentifier() != null) {
            root.put("safety_identifier", request.getSafetyIdentifier());
        }
        if (request.getBackground() != null) {
            root.put("background", request.getBackground());
        }
        if (request.getConversation() != null) {
            root.set("conversation", request.getConversation());
        }
        if (request.getPrompt() != null) {
            root.set("prompt", request.getPrompt());
        }
        if (request.getModalities() != null && !request.getModalities().isEmpty()) {
            ArrayNode modsArr = root.putArray("modalities");
            request.getModalities().forEach(modsArr::add);
        }
        if (request.getAudio() != null) {
            root.set("audio", request.getAudio());
        }
        if (request.getServiceTier() != null) {
            root.put("service_tier", request.getServiceTier());
        }

        // Build input array from messages
        ArrayNode inputArr = root.putArray("input");
        if (request.getMessages() != null) {
            for (CanonicalMessage msg : request.getMessages()) {
                if (msg.getRole() == CanonicalRole.SYSTEM || msg.getRole() == CanonicalRole.DEVELOPER) {
                    // System/developer -> instructions or message item
                    String systemText = extractText(msg);
                    if (systemText != null) {
                        if (!root.has("instructions")) {
                            root.put("instructions", systemText);
                        } else {
                            inputArr.add(denormalizeToMessageItem(msg));
                        }
                    }
                } else if (msg.getRole() == CanonicalRole.TOOL) {
                    // Tool result -> function_call_output
                    if (msg.getToolCalls() != null) {
                        for (CanonicalToolCall tc : msg.getToolCalls()) {
                            inputArr.add(denormalizeToolCallToFunctionCall(tc));
                        }
                    }
                    if (msg.getToolCallId() != null) {
                        inputArr.add(denormalizeToolResultToFunctionCallOutput(msg));
                    }
                } else if (msg.getRole() == CanonicalRole.ASSISTANT && msg.getToolCalls() != null) {
                    // Assistant with tool calls -> function_call items
                    for (CanonicalToolCall tc : msg.getToolCalls()) {
                        inputArr.add(denormalizeToolCallToFunctionCall(tc));
                    }
                    // Also add text content if present
                    String text = extractText(msg);
                    if (text != null && !text.isEmpty()) {
                        inputArr.add(denormalizeToMessageItem(msg));
                    }
                } else {
                    inputArr.add(denormalizeToMessageItem(msg));
                }
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
                toolNode.put("name", tool.getName());
                if (tool.getDescription() != null) {
                    toolNode.put("description", tool.getDescription());
                }
                if (tool.getInputSchema() != null) {
                    toolNode.set("parameters", tool.getInputSchema());
                }
                if (tool.getStrict() != null) {
                    toolNode.put("strict", tool.getStrict());
                }
                // Merge rawExtra back
                if (tool.getRawExtra() != null) {
                    JacksonUtil.deepMergeInto(toolNode, tool.getRawExtra());
                }
                toolsArr.add(toolNode);
            }
        }

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

    private ObjectNode denormalizeToMessageItem(CanonicalMessage msg) {
        ObjectNode item = JacksonUtil.objectNode();
        item.put("type", "message");
        item.put("role", mapRoleToString(msg.getRole()));

        ArrayNode contentArr = item.putArray("content");
        if (msg.getContent() != null) {
            for (CanonicalContentPart part : msg.getContent()) {
                if (part instanceof TextContentPart tp) {
                    ObjectNode textNode = JacksonUtil.objectNode();
                    textNode.put("type", "input_text");
                    textNode.put("text", tp.getText());
                    contentArr.add(textNode);
                } else if (part instanceof ImageContentPart ip) {
                    ObjectNode imgNode = JacksonUtil.objectNode();
                    imgNode.put("type", "input_image");
                    imgNode.put("image_url", ip.getUrl());
                    contentArr.add(imgNode);
                } else if (part instanceof FileContentPart fp) {
                    ObjectNode fileNode = JacksonUtil.objectNode();
                    fileNode.put("type", "input_file");
                    if (fp.getFileId() != null) fileNode.put("file_id", fp.getFileId());
                    if (fp.getUrl() != null) fileNode.put("url", fp.getUrl());
                    if (fp.getFilename() != null) fileNode.put("filename", fp.getFilename());
                    if (fp.getMediaType() != null) fileNode.put("media_type", fp.getMediaType());
                    contentArr.add(fileNode);
                } else if (part instanceof AudioContentPart ap) {
                    // Responses doesn't have native audio input; preserve as rawExtra
                    ObjectNode extra = JacksonUtil.objectNode();
                    extra.put("type", "input_audio");
                    if (ap.getBase64() != null) extra.put("data", ap.getBase64());
                    if (ap.getFormat() != null) extra.put("format", ap.getFormat());
                    contentArr.add(extra);
                } else if (part instanceof ThinkingContentPart thp) {
                    // Responses uses reasoning items, not thinking content blocks
                    // Preserve as rawExtra for round-trip fidelity
                    ObjectNode extra = JacksonUtil.objectNode();
                    extra.put("type", "thinking");
                    extra.put("thinking", thp.getThinking());
                    if (thp.getSignature() != null) extra.put("signature", thp.getSignature());
                    contentArr.add(extra);
                }
            }
        }

        // Merge rawExtra back
        if (msg.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(item, msg.getRawExtra());
        }

        return item;
    }

    private ObjectNode denormalizeToolCallToFunctionCall(CanonicalToolCall tc) {
        ObjectNode item = JacksonUtil.objectNode();
        item.put("type", "function_call");
        item.put("call_id", tc.getId() != null ? tc.getId() : "call_" + UUID.randomUUID());
        item.put("name", tc.getName());
        String args = tc.getRawArguments() != null ? tc.getRawArguments()
                : (tc.getArguments() != null ? tc.getArguments().toString() : "{}");
        item.put("arguments", args);
        // Merge rawExtra back
        if (tc.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(item, tc.getRawExtra());
        }
        return item;
    }

    private ObjectNode denormalizeToolResultToFunctionCallOutput(CanonicalMessage msg) {
        ObjectNode item = JacksonUtil.objectNode();
        item.put("type", "function_call_output");
        item.put("call_id", msg.getToolCallId());
        String output = extractText(msg);
        item.put("output", output != null ? output : "");
        // Merge rawExtra back
        if (msg.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(item, msg.getRawExtra());
        }
        return item;
    }

    private String mapRoleToString(CanonicalRole role) {
        if (role == null) return "user";
        return switch (role) {
            case SYSTEM -> "system";
            case DEVELOPER -> "developer";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "user"; // tool results are wrapped as function_call_output
        };
    }

    private String extractText(CanonicalMessage msg) {
        if (msg.getContent() == null) return null;
        StringBuilder sb = new StringBuilder();
        for (CanonicalContentPart part : msg.getContent()) {
            if (part instanceof TextContentPart tp) {
                sb.append(tp.getText());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    @Override
    public CanonicalResponse normalizeResponse(JsonNode rawResponse, BridgeContext context) {
        log.debug("normalizing OpenAI Responses response: id={}", JacksonUtil.getString(rawResponse, "id"));
        CanonicalResponse resp = new CanonicalResponse();
        resp.setId(JacksonUtil.getString(rawResponse, "id"));
        resp.setRequestedModel(JacksonUtil.getString(rawResponse, "model"));
        resp.setUpstreamModel(JacksonUtil.getString(rawResponse, "model"));
        resp.setCreated(JacksonUtil.getLong(rawResponse, "created_at"));

        // output
        JsonNode outputNode = rawResponse.get("output");
        if (outputNode != null && outputNode.isArray() && !outputNode.isEmpty()) {
            List<CanonicalMessage> messages = new ArrayList<>();
            List<CanonicalToolCall> responseToolCalls = new ArrayList<>();
            for (JsonNode item : outputNode) {
                String type = JacksonUtil.getString(item, "type");
                if ("message".equals(type)) {
                    CanonicalMessage nm = new CanonicalMessage();
                    nm.setRole(mapRole(JacksonUtil.getString(item, "role")));
                    JsonNode contentNode = item.get("content");
                    if (contentNode != null && contentNode.isArray()) {
                        List<CanonicalContentPart> parts = new ArrayList<>();
                        for (JsonNode part : contentNode) {
                            String partType = JacksonUtil.getString(part, "type");
                            if ("output_text".equals(partType)) {
                                TextContentPart tp = new TextContentPart();
                                tp.setText(JacksonUtil.getString(part, "text"));
                                parts.add(tp);
                            } else if ("refusal".equals(partType)) {
                                RefusalContentPart rp = new RefusalContentPart(JacksonUtil.getString(part, "refusal"));
                                parts.add(rp);
                            } else {
                                // Unknown content part - preserve
                                parts.add(new UnknownContentPart(partType, part));
                            }
                        }
                        nm.setContent(parts);
                    }
                    // reasoning_content on the message item (e.g. mimo)
                    JsonNode rc = item.get("reasoning_content");
                    if (rc != null && !rc.isNull()) {
                        nm.setReasoningContent(rc.asText());
                    }
                    // audio output
                    JsonNode audio = item.get("audio");
                    if (audio != null && audio.isObject()) {
                        nm.setAudio((ObjectNode) audio);
                    }
                    nm.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item,
                            "type", "role", "content", "id", "status",
                            "reasoning_content", "audio"));
                    messages.add(nm);
                } else if ("function_call".equals(type)) {
                    CanonicalToolCall tc = new CanonicalToolCall();
                    tc.setId(JacksonUtil.getString(item, "call_id"));
                    tc.setName(JacksonUtil.getString(item, "name"));
                    tc.setType("function");
                    String argsStr = JacksonUtil.getString(item, "arguments");
                    tc.setRawArguments(argsStr);
                    tc.setArguments(JacksonUtil.tryParse(argsStr));
                    tc.setRawExtra(JacksonUtil.extractExtra((ObjectNode) item,
                            "type", "call_id", "name", "arguments", "id", "status"));
                    responseToolCalls.add(tc);
                } else if ("reasoning".equals(type)) {
                    // Preserve reasoning items
                    CanonicalMessage nm = new CanonicalMessage(CanonicalRole.ASSISTANT, List.of());
                    ObjectNode extra = JacksonUtil.objectNode();
                    extra.set("reasoning_item", item);
                    nm.setRawExtra(extra);
                    messages.add(nm);
                } else {
                    // Unknown output item type - preserve as rawExtra
                    CanonicalMessage nm = new CanonicalMessage(CanonicalRole.ASSISTANT, List.of());
                    ObjectNode extra = JacksonUtil.objectNode();
                    extra.set("unknown_output_item", item);
                    nm.setRawExtra(extra);
                    messages.add(nm);
                }
            }
            resp.setOutputMessages(messages);
            if (!responseToolCalls.isEmpty()) {
                resp.setToolCalls(responseToolCalls);
            }
        }

        // stop_reason / status
        JsonNode statusNode = rawResponse.get("status");
        if (statusNode != null) {
            resp.setStopReason(mapStatus(JacksonUtil.getString(rawResponse, "status")));
        }

        // usage
        JsonNode usageNode = rawResponse.get("usage");
        if (usageNode != null) {
            resp.setUsage(normalizeUsage(usageNode));
        }

        // Preserve unknown response fields
        resp.setRawExtra(JacksonUtil.extractExtra((ObjectNode) rawResponse,
                "id", "object", "created_at", "model", "output", "usage",
                "status", "error", "incomplete_details", "metadata",
                "output_text", "reasoning"));

        return resp;
    }

    private String mapStatus(String status) {
        return StopReasonMapper.toNormalizedFromResponsesStatus(status);
    }

    @Override
    public ObjectNode denormalizeResponse(CanonicalResponse response, BridgeContext context) {
        log.debug("denormalizing OpenAI Responses response: id={}", response.getId());
        ObjectNode root = JacksonUtil.objectNode();
        root.put("id", response.getId() != null ? response.getId() : "resp-" + UUID.randomUUID());
        root.put("object", "response");
        root.put("created_at", response.getCreated() != null ? response.getCreated() : System.currentTimeMillis() / 1000);
        root.put("model", response.getModel());

        // output
        ArrayNode outputArr = root.putArray("output");
        if (response.getToolCalls() != null) {
            for (CanonicalToolCall tc : response.getToolCalls()) {
                ObjectNode fcNode = JacksonUtil.objectNode();
                fcNode.put("type", "function_call");
                fcNode.put("id", "fc_" + UUID.randomUUID());
                fcNode.put("call_id", tc.getId() != null ? tc.getId() : "call_" + UUID.randomUUID());
                fcNode.put("name", tc.getName());
                String args = tc.getRawArguments() != null ? tc.getRawArguments()
                        : (tc.getArguments() != null ? tc.getArguments().toString() : "{}");
                fcNode.put("arguments", args);
                if (tc.getRawExtra() != null) {
                    JacksonUtil.deepMergeInto(fcNode, tc.getRawExtra());
                }
                outputArr.add(fcNode);
            }
        }
        if (response.getOutputMessages() != null) {
            for (CanonicalMessage msg : response.getOutputMessages()) {
                if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                    ObjectNode msgNode = JacksonUtil.objectNode();
                    msgNode.put("type", "message");
                    msgNode.put("id", "msg_" + UUID.randomUUID());
                    msgNode.put("role", "assistant");
                    ArrayNode contentArr = msgNode.putArray("content");
                    for (CanonicalContentPart part : msg.getContent()) {
                        if (part instanceof TextContentPart tp) {
                            ObjectNode textNode = JacksonUtil.objectNode();
                            textNode.put("type", "output_text");
                            textNode.put("text", tp.getText());
                            contentArr.add(textNode);
                        } else if (part instanceof RefusalContentPart rp) {
                            ObjectNode refusalNode = JacksonUtil.objectNode();
                            refusalNode.put("type", "refusal");
                            refusalNode.put("refusal", rp.getRefusal());
                            contentArr.add(refusalNode);
                        } else if (part instanceof ToolCallContentPart tcp) {
                            ObjectNode fcNode = JacksonUtil.objectNode();
                            fcNode.put("type", "function_call");
                            fcNode.put("id", "fc_" + UUID.randomUUID());
                            fcNode.put("call_id", tcp.getId() != null ? tcp.getId() : "call_" + UUID.randomUUID());
                            fcNode.put("name", tcp.getName());
                            fcNode.put("arguments", tcp.getArguments() != null ? tcp.getArguments().toString() : "{}");
                            outputArr.add(fcNode);
                        } else if (part instanceof FileContentPart fp) {
                            ObjectNode fileNode = JacksonUtil.objectNode();
                            fileNode.put("type", "input_file");
                            if (fp.getFileId() != null) fileNode.put("file_id", fp.getFileId());
                            if (fp.getUrl() != null) fileNode.put("url", fp.getUrl());
                            if (fp.getFilename() != null) fileNode.put("filename", fp.getFilename());
                            if (fp.getMediaType() != null) fileNode.put("media_type", fp.getMediaType());
                            contentArr.add(fileNode);
                        } else if (part instanceof ThinkingContentPart thp) {
                            ObjectNode thinkNode = JacksonUtil.objectNode();
                            thinkNode.put("type", "reasoning");
                            thinkNode.put("summary", thp.getThinking());
                            contentArr.add(thinkNode);
                        }
                    }
                    if (contentArr.isEmpty()) {
                        ObjectNode textNode = JacksonUtil.objectNode();
                        textNode.put("type", "output_text");
                        textNode.put("text", "");
                        contentArr.add(textNode);
                    }
                    // reasoning_content on the message
                    if (msg.getReasoningContent() != null) {
                        msgNode.put("reasoning_content", msg.getReasoningContent());
                    }
                    // audio output
                    if (msg.getAudio() != null) {
                        msgNode.set("audio", msg.getAudio());
                    }
                    // Merge rawExtra back
                    if (msg.getRawExtra() != null) {
                        JacksonUtil.deepMergeInto(msgNode, msg.getRawExtra());
                    }
                    outputArr.add(msgNode);
                }
            }
        }

        // status
        root.put("status", StopReasonMapper.toResponsesStatus(response.getStopReason()));

        // usage
        if (response.getUsage() != null) {
            ObjectNode usageNode = JacksonUtil.objectNode();
            usageNode.put("input_tokens", response.getUsage().getInputTokens() != null ? response.getUsage().getInputTokens() : 0);
            usageNode.put("output_tokens", response.getUsage().getOutputTokens() != null ? response.getUsage().getOutputTokens() : 0);
            usageNode.put("total_tokens", response.getUsage().getTotalTokens() != null ? response.getUsage().getTotalTokens() : 0);
            if (response.getUsage().getCachedInputTokens() != null) {
                usageNode.put("cached_input_tokens", response.getUsage().getCachedInputTokens());
            }
            if (response.getUsage().getReasoningTokens() != null) {
                usageNode.put("reasoning_tokens", response.getUsage().getReasoningTokens());
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

    private String mapStopReasonToStatus(String stopReason) {
        if (stopReason == null) return "completed";
        return switch (stopReason) {
            case "end_turn" -> "completed";
            case "max_tokens" -> "incomplete";
            case "refusal" -> "failed";
            default -> "completed";
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
        if (data == null) return null;

        String type = JacksonUtil.getString(data, "type");
        if (type == null) {
            type = event.getEvent();
        }
        if (type == null) return null;

        CanonicalStreamEvent nse = new CanonicalStreamEvent();
        nse.setRaw(data);

        switch (type) {
            case "response.created" -> {
                nse.setType(CanonicalStreamEventType.START);
                JsonNode response = data.get("response");
                if (response != null) {
                    nse.setResponseId(JacksonUtil.getString(response, "id"));
                }
            }
            case "response.output_item.added" -> {
                JsonNode item = data.get("item");
                if (item != null) {
                    String itemType = JacksonUtil.getString(item, "type");
                    if ("message".equals(itemType)) {
                        nse.setType(CanonicalStreamEventType.MESSAGE_START);
                        nse.setRole(CanonicalRole.ASSISTANT);
                    } else if ("function_call".equals(itemType)) {
                        nse.setType(CanonicalStreamEventType.TOOL_CALL_START);
                        nse.setToolCallId(JacksonUtil.getString(item, "call_id"));
                        nse.setToolName(JacksonUtil.getString(item, "name"));
                        nse.setToolIndex(JacksonUtil.getInt(data, "output_index"));
                    }
                }
            }
            case "response.content_part.added" -> {
                nse.setType(CanonicalStreamEventType.CONTENT_BLOCK_START);
                nse.setContentIndex(JacksonUtil.getInt(data, "content_index"));
            }
            case "response.output_text.delta" -> {
                nse.setType(CanonicalStreamEventType.TEXT_DELTA);
                nse.setDeltaText(JacksonUtil.getString(data, "delta"));
                nse.setContentIndex(JacksonUtil.getInt(data, "content_index"));
            }
            case "response.refusal.delta" -> {
                nse.setType(CanonicalStreamEventType.REFUSAL_DELTA);
                nse.setRefusalDelta(JacksonUtil.getString(data, "delta"));
            }
            case "response.function_call_arguments.delta" -> {
                nse.setType(CanonicalStreamEventType.TOOL_ARGUMENTS_DELTA);
                nse.setToolArgumentsDelta(JacksonUtil.getString(data, "delta"));
                nse.setToolIndex(JacksonUtil.getInt(data, "output_index"));
            }
            case "response.function_call_arguments.done" -> {
                nse.setType(CanonicalStreamEventType.TOOL_CALL_DONE);
                nse.setToolIndex(JacksonUtil.getInt(data, "output_index"));
            }
            case "response.completed" -> {
                nse.setType(CanonicalStreamEventType.DONE);
                JsonNode response = data.get("response");
                if (response != null) {
                    JsonNode usage = response.get("usage");
                    if (usage != null) {
                        nse.setUsage(normalizeUsage(usage));
                    }
                }
            }
            case "response.incomplete" -> {
                nse.setType(CanonicalStreamEventType.DONE);
                nse.setStopReason("max_tokens");
                JsonNode response = data.get("response");
                if (response != null) {
                    JsonNode incomplete = response.get("incomplete_details");
                    if (incomplete != null) {
                        nse.setStopReason(JacksonUtil.getString(incomplete, "reason") != null
                                ? JacksonUtil.getString(incomplete, "reason")
                                : "max_tokens");
                    }
                }
            }
            case "response.failed" -> {
                nse.setType(CanonicalStreamEventType.DONE);
                nse.setStopReason("refusal");
            }
            case "response.content_part.done" -> {
                nse.setType(CanonicalStreamEventType.CONTENT_BLOCK_DONE);
                nse.setContentIndex(JacksonUtil.getInt(data, "content_index"));
            }
            case "response.output_item.done" -> {
                nse.setType(CanonicalStreamEventType.UNKNOWN);
            }
            case "response.refusal.done" -> {
                nse.setType(CanonicalStreamEventType.UNKNOWN);
            }
            case "response.output_text.done" -> {
                nse.setType(CanonicalStreamEventType.UNKNOWN);
            }
            case "response.reasoning_summary_text.delta" -> {
                nse.setType(CanonicalStreamEventType.THINKING_DELTA);
                nse.setThinkingDelta(JacksonUtil.getString(data, "delta"));
            }
            case "response.reasoning_summary_text.done" -> {
                nse.setType(CanonicalStreamEventType.UNKNOWN);
            }
            case "response.audio.delta" -> {
                nse.setType(CanonicalStreamEventType.UNKNOWN);
            }
            case "response.audio.done" -> {
                nse.setType(CanonicalStreamEventType.UNKNOWN);
            }
            case "response.audio_transcript.delta" -> {
                nse.setType(CanonicalStreamEventType.TEXT_DELTA);
                nse.setDeltaText(JacksonUtil.getString(data, "delta"));
            }
            case "response.audio_transcript.done" -> {
                nse.setType(CanonicalStreamEventType.UNKNOWN);
            }
            case "response.in_progress" -> {
                // Heartbeat-like event, no payload
                nse.setType(CanonicalStreamEventType.PING);
            }
            case "error" -> {
                nse.setType(CanonicalStreamEventType.ERROR);
                JsonNode error = data.get("error");
                if (error != null) {
                    nse.setDeltaText(JacksonUtil.getString(error, "message"));
                }
            }
            default -> {
                nse.setType(CanonicalStreamEventType.UNKNOWN);
            }
        }

        return nse;
    }

    @Override
    public Flux<SseFrame> denormalizeStream(Flux<CanonicalStreamEvent> events, BridgeContext context) {
        StreamStateTracker state = new StreamStateTracker();
        Map<Integer, String> toolItemIds = new HashMap<>();
        state.setCreated(System.currentTimeMillis() / 1000);
        state.setModel(context.requestedModel());
        state.setResponseId("resp_" + UUID.randomUUID());

        return events.concatMap(event -> {
            List<SseFrame> result = new ArrayList<>();

            state.apply(event);

            switch (event.getType()) {
                case START -> {
                    state.setResponseId(event.getResponseId() != null ? event.getResponseId() : state.getResponseId());
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "response.created");
                    ObjectNode response = root.putObject("response");
                    response.put("id", state.getResponseId());
                    response.put("object", "response");
                    response.put("created_at", state.getCreated());
                    response.put("model", state.getModel());
                    response.put("status", "in_progress");
                    result.add(new SseFrame("response.created", root.toString()));
                }
                case MESSAGE_START -> {
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "response.output_item.added");
                    root.put("output_index", 0);
                    ObjectNode item = root.putObject("item");
                    item.put("id", "msg_" + UUID.randomUUID());
                    item.put("type", "message");
                    item.put("status", "in_progress");
                    item.put("role", "assistant");
                    item.putArray("content");
                    result.add(new SseFrame("response.output_item.added", root.toString()));
                }
                case CONTENT_BLOCK_START -> {
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "response.content_part.added");
                    root.put("item_id", "msg_" + UUID.randomUUID());
                    root.put("output_index", 0);
                    root.put("content_index", event.getContentIndex() != null ? event.getContentIndex() : 0);
                    ObjectNode part = root.putObject("part");
                    part.put("type", "output_text");
                    part.put("text", "");
                    result.add(new SseFrame("response.content_part.added", root.toString()));
                }
                case TEXT_DELTA -> {
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "response.output_text.delta");
                    root.put("item_id", "msg_" + UUID.randomUUID());
                    root.put("output_index", 0);
                    root.put("content_index", event.getContentIndex() != null ? event.getContentIndex() : 0);
                    root.put("delta", event.getDeltaText() != null ? event.getDeltaText() : "");
                    result.add(new SseFrame("response.output_text.delta", root.toString()));
                }
                case REFUSAL_DELTA -> {
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "response.refusal.delta");
                    root.put("delta", event.getRefusalDelta() != null ? event.getRefusalDelta() : "");
                    result.add(new SseFrame("response.refusal.delta", root.toString()));
                }
                case TOOL_CALL_START -> {
                    int outputIndex = event.getToolIndex() != null ? event.getToolIndex() : 0;
                    String itemId = "fc_" + UUID.randomUUID();
                    toolItemIds.put(outputIndex, itemId);
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "response.output_item.added");
                    root.put("output_index", outputIndex);
                    ObjectNode item = root.putObject("item");
                    item.put("id", itemId);
                    item.put("type", "function_call");
                    item.put("status", "in_progress");
                    item.put("call_id", event.getToolCallId() != null ? event.getToolCallId() : "call_" + UUID.randomUUID());
                    item.put("name", event.getToolName() != null ? event.getToolName() : "");
                    item.put("arguments", "");
                    result.add(new SseFrame("response.output_item.added", root.toString()));
                }
                case TOOL_ARGUMENTS_DELTA -> {
                    int outputIndex = event.getToolIndex() != null ? event.getToolIndex() : 0;
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "response.function_call_arguments.delta");
                    root.put("item_id", toolItemIds.computeIfAbsent(outputIndex, ignored -> "fc_" + UUID.randomUUID()));
                    root.put("output_index", outputIndex);
                    root.put("delta", event.getToolArgumentsDelta() != null ? event.getToolArgumentsDelta() : "");
                    result.add(new SseFrame("response.function_call_arguments.delta", root.toString()));
                }
                case TOOL_CALL_DONE -> {
                    int outputIndex = event.getToolIndex() != null ? event.getToolIndex() : 0;
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "response.function_call_arguments.done");
                    root.put("item_id", toolItemIds.computeIfAbsent(outputIndex, ignored -> "fc_" + UUID.randomUUID()));
                    root.put("output_index", outputIndex);
                    result.add(new SseFrame("response.function_call_arguments.done", root.toString()));
                }
                case MESSAGE_DELTA -> {
                    // No direct equivalent in Responses stream
                }
                case USAGE_DELTA -> {
                    // Usage is part of response.completed
                }
                case DONE -> {
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "response.completed");
                    ObjectNode response = root.putObject("response");
                    response.put("id", state.getResponseId());
                    response.put("object", "response");
                    response.put("created_at", state.getCreated());
                    response.put("model", state.getModel());
                    response.put("status", StopReasonMapper.toResponsesStatus(state.getStopReason()));
                    response.putArray("output");
                    CanonicalUsage usage = state.getUsage();
                    if (usage != null) {
                        ObjectNode usageNode = response.putObject("usage");
                        usageNode.put("input_tokens",
                                usage.getInputTokens() != null ? usage.getInputTokens() : 0);
                        usageNode.put("output_tokens",
                                usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
                        usageNode.put("total_tokens",
                                usage.getTotalTokens() != null ? usage.getTotalTokens() : 0);
                    }
                    result.add(new SseFrame("response.completed", root.toString()));
                }
                case ERROR -> {
                    ObjectNode root = JacksonUtil.objectNode();
                    root.put("type", "error");
                    ObjectNode error = root.putObject("error");
                    error.put("message", event.getDeltaText() != null ? event.getDeltaText() : "stream error");
                    error.put("code", "upstream_error");
                    result.add(new SseFrame("error", root.toString()));
                }
                case PING -> {
                    // Ignore pings for Responses
                }
                default -> {
                    // Unknown events - ignore
                }
            }

            return Flux.fromIterable(result);
        });
    }

    private CanonicalUsage normalizeUsage(JsonNode usageNode) {
        CanonicalUsage usage = new CanonicalUsage();
        usage.setInputTokens(firstInt(usageNode, "input_tokens", "prompt_tokens"));
        usage.setOutputTokens(firstInt(usageNode, "output_tokens", "completion_tokens"));
        usage.setTotalTokens(JacksonUtil.getInt(usageNode, "total_tokens"));
        if (usage.getTotalTokens() == null
                && usage.getInputTokens() != null
                && usage.getOutputTokens() != null) {
            usage.setTotalTokens(usage.getInputTokens() + usage.getOutputTokens());
        }

        usage.setCachedInputTokens(JacksonUtil.getInt(usageNode, "cached_input_tokens"));
        usage.setReasoningTokens(JacksonUtil.getInt(usageNode, "reasoning_tokens"));

        JsonNode inputDetails = firstObject(usageNode, "input_tokens_details", "prompt_tokens_details");
        if (inputDetails != null) {
            Integer cached = JacksonUtil.getInt(inputDetails, "cached_tokens");
            if (cached != null && usage.getCachedInputTokens() == null) {
                usage.setCachedInputTokens(cached);
            }
        }

        JsonNode outputDetails = firstObject(usageNode, "output_tokens_details", "completion_tokens_details");
        if (outputDetails != null) {
            Integer reason = JacksonUtil.getInt(outputDetails, "reasoning_tokens");
            if (reason != null && usage.getReasoningTokens() == null) {
                usage.setReasoningTokens(reason);
            }
        }

        Integer cacheCreation = JacksonUtil.getInt(usageNode, "cache_creation_input_tokens");
        Integer cacheRead = JacksonUtil.getInt(usageNode, "cache_read_input_tokens");
        usage.setCacheCreationInputTokens(cacheCreation);
        usage.setCacheReadInputTokens(cacheRead);
        if (usage.getCachedInputTokens() == null && (cacheCreation != null || cacheRead != null)) {
            usage.setCachedInputTokens((cacheCreation != null ? cacheCreation : 0)
                    + (cacheRead != null ? cacheRead : 0));
        }

        if (usageNode instanceof ObjectNode objectNode) {
            usage.setRawExtra(JacksonUtil.extractExtra(objectNode,
                    "input_tokens", "output_tokens", "prompt_tokens", "completion_tokens", "total_tokens",
                    "cached_input_tokens", "reasoning_tokens",
                    "input_tokens_details", "output_tokens_details",
                    "prompt_tokens_details", "completion_tokens_details",
                    "cache_creation_input_tokens", "cache_read_input_tokens"));
        }
        return usage;
    }

    private Integer firstInt(JsonNode node, String... fields) {
        if (node == null) return null;
        for (String field : fields) {
            Integer value = JacksonUtil.getInt(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private JsonNode firstObject(JsonNode node, String... fields) {
        if (node == null) return null;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isObject()) {
                return value;
            }
        }
        return null;
    }
}
