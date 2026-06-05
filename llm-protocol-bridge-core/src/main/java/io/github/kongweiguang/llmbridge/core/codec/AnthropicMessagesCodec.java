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
 * Codec for the Anthropic Messages API format.
 * Handles conversion between raw Anthropic JSON and the normalized model.
 * Unknown fields are preserved in rawExtra to prevent silent field dropping.
 */
@Slf4j
public class AnthropicMessagesCodec implements ProtocolCodec {

    @Override
    public ApiProtocol apiProtocol() {
        return ApiProtocol.ANTHROPIC_MESSAGES;
    }

    @Override
    public CanonicalRequest normalizeRequest(JsonNode rawRequest, BridgeContext context) {
        log.debug("normalizing Anthropic Messages request: model={}", JacksonUtil.getString(rawRequest, "model"));
        CanonicalRequest req = new CanonicalRequest();
        req.setModel(JacksonUtil.getString(rawRequest, "model"));
        req.setTemperature(JacksonUtil.getDouble(rawRequest, "temperature"));
        req.setTopP(JacksonUtil.getDouble(rawRequest, "top_p"));
        req.setStream(JacksonUtil.getBoolean(rawRequest, "stream"));
        req.setMaxOutputTokens(JacksonUtil.getInt(rawRequest, "max_tokens"));

        // top_k
        req.setTopK(JacksonUtil.getInt(rawRequest, "top_k"));

        // service_tier
        req.setServiceTier(JacksonUtil.getString(rawRequest, "service_tier"));

        // inference_geo
        req.setInferenceGeo(JacksonUtil.getString(rawRequest, "inference_geo"));

        // speed
        req.setSpeed(JacksonUtil.getString(rawRequest, "speed"));

        // container
        JsonNode container = rawRequest.get("container");
        if (container != null) {
            if (container.isObject()) {
                req.setContainer((ObjectNode) container);
            } else if (container.isTextual()) {
                ObjectNode c = JacksonUtil.objectNode();
                c.put("id", container.asText());
                req.setContainer(c);
            }
        }

        // mcp_servers
        JsonNode mcp = rawRequest.get("mcp_servers");
        if (mcp != null && mcp.isArray()) {
            List<ObjectNode> servers = new ArrayList<>();
            mcp.forEach(n -> {
                if (n != null && n.isObject()) {
                    servers.add((ObjectNode) n);
                }
            });
            req.setMcpServers(servers);
        }

        // context_management
        JsonNode cm = rawRequest.get("context_management");
        if (cm != null && cm.isObject()) {
            req.setContextManagement((ObjectNode) cm);
        }

        // stop_sequences
        JsonNode stopSeq = rawRequest.get("stop_sequences");
        if (stopSeq != null && stopSeq.isArray()) {
            List<String> stops = new ArrayList<>();
            stopSeq.forEach(s -> stops.add(s.asText()));
            req.setStopSequences(stops);
        }

        // thinking config
        JsonNode thinkingNode = rawRequest.get("thinking");
        if (thinkingNode != null && thinkingNode.isObject()) {
            req.setReasoning((ObjectNode) thinkingNode);
        }

        // metadata
        JsonNode metadataNode = rawRequest.get("metadata");
        if (metadataNode != null && metadataNode.isObject()) {
            req.setMetadata((ObjectNode) metadataNode);
        }

        List<CanonicalMessage> messages = new ArrayList<>();

        // system -> SYSTEM message
        JsonNode systemNode = rawRequest.get("system");
        if (systemNode != null) {
            String systemText;
            if (systemNode.isTextual()) {
                systemText = systemNode.asText();
            } else if (systemNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : systemNode) {
                    if ("text".equals(JacksonUtil.getString(block, "type"))) {
                        sb.append(JacksonUtil.getString(block, "text"));
                    }
                }
                systemText = sb.toString();
            } else {
                systemText = systemNode.toString();
            }
            if (!systemText.isEmpty()) {
                messages.add(new CanonicalMessage(CanonicalRole.SYSTEM, List.of(new TextContentPart(systemText))));
            }
        }

        // messages
        JsonNode messagesNode = rawRequest.get("messages");
        if (messagesNode != null && messagesNode.isArray()) {
            for (JsonNode msg : messagesNode) {
                CanonicalMessage nm = normalizeMessage(msg);
                if (nm != null) {
                    messages.add(nm);
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
        if (tc != null && tc.isObject()) {
            req.setToolChoice(new CanonicalToolChoice(tc));
        }

        // Preserve unknown fields
        req.setRawExtra(JacksonUtil.extractExtra((ObjectNode) rawRequest,
                "model", "system", "messages", "tools", "tool_choice",
                "temperature", "top_p", "top_k", "max_tokens", "stream",
                "stop_sequences", "thinking", "metadata",
                "container", "mcp_servers", "service_tier",
                "inference_geo", "speed", "context_management"));

        return req;
    }

    private CanonicalMessage normalizeMessage(JsonNode msg) {
        CanonicalMessage nm = new CanonicalMessage();
        String role = JacksonUtil.getString(msg, "role");
        nm.setRole(mapRole(role));

        JsonNode contentNode = msg.get("content");
        if (contentNode != null) {
            List<CanonicalContentPart> parts = new ArrayList<>();
            List<CanonicalToolCall> toolCalls = new ArrayList<>();
            List<ToolResultContentPart> toolResults = new ArrayList<>();
            if (contentNode.isTextual()) {
                parts.add(new TextContentPart(contentNode.asText()));
            } else if (contentNode.isArray()) {
                for (JsonNode block : contentNode) {
                    CanonicalContentPart ncp = normalizeContentBlock(block);
                    if (ncp != null) {
                        if (ncp instanceof ToolCallContentPart tcp) {
                            CanonicalToolCall tc = new CanonicalToolCall();
                            tc.setId(tcp.getId());
                            tc.setName(tcp.getName());
                            tc.setType("function");
                            tc.setArguments(tcp.getArguments());
                            if (tcp.getArguments() != null) {
                                tc.setRawArguments(tcp.getArguments().toString());
                            }
                            tc.setIndex(toolCalls.size());
                            toolCalls.add(tc);
                        } else if (ncp instanceof ToolResultContentPart trp) {
                            toolResults.add(trp);
                        } else {
                            parts.add(ncp);
                        }
                    }
                }
            }
            if (!toolCalls.isEmpty()) {
                nm.setToolCalls(toolCalls);
            }
            if (!toolResults.isEmpty() && parts.isEmpty() && nm.getRole() == CanonicalRole.USER) {
                ToolResultContentPart trp = toolResults.get(0);
                nm.setRole(CanonicalRole.TOOL);
                nm.setToolCallId(trp.getToolCallId());
                if (trp.getContent() != null) {
                    parts.add(new TextContentPart(trp.getContent()));
                }
                if (trp.getIsError() != null) {
                    ObjectNode extra = JacksonUtil.objectNode();
                    extra.put("is_error", trp.getIsError());
                    nm.setRawExtra(extra);
                }
            } else {
                parts.addAll(toolResults);
            }
            nm.setContent(parts);
        }

        // Preserve unknown message fields
        ObjectNode messageExtra = JacksonUtil.extractExtra((ObjectNode) msg, "role", "content");
        if (!messageExtra.isEmpty()) {
            if (nm.getRawExtra() != null) {
                messageExtra.setAll(nm.getRawExtra());
            }
            nm.setRawExtra(messageExtra);
        }

        return nm;
    }

    private CanonicalContentPart normalizeContentBlock(JsonNode block) {
        String type = JacksonUtil.getString(block, "type");
        if (type == null) return null;
        return switch (type) {
            case "text" -> {
                TextContentPart tp = new TextContentPart();
                tp.setText(JacksonUtil.getString(block, "text"));
                yield tp;
            }
            case "image" -> {
                ImageContentPart ip = new ImageContentPart();
                JsonNode source = block.get("source");
                if (source != null) {
                    String srcType = JacksonUtil.getString(source, "type");
                    if ("base64".equals(srcType)) {
                        ip.setBase64(JacksonUtil.getString(source, "data"));
                        ip.setMediaType(JacksonUtil.getString(source, "media_type"));
                    } else if ("url".equals(srcType)) {
                        ip.setUrl(JacksonUtil.getString(source, "url"));
                    }
                }
                yield ip;
            }
            case "document", "file" -> {
                FileContentPart fp = new FileContentPart();
                JsonNode source = block.get("source");
                if (source != null) {
                    String srcType = JacksonUtil.getString(source, "type");
                    if ("base64".equals(srcType)) {
                        fp.setBase64(JacksonUtil.getString(source, "data"));
                        fp.setMediaType(JacksonUtil.getString(source, "media_type"));
                    } else if ("url".equals(srcType)) {
                        fp.setUrl(JacksonUtil.getString(source, "url"));
                    } else if ("file_id".equals(srcType)) {
                        fp.setFileId(JacksonUtil.getString(source, "file_id"));
                    }
                }
                fp.setFilename(JacksonUtil.getString(block, "title"));
                fp.setMediaType(JacksonUtil.getString(block, "media_type"));
                yield fp;
            }
            case "tool_use" -> {
                ToolCallContentPart tcp = new ToolCallContentPart();
                tcp.setId(JacksonUtil.getString(block, "id"));
                tcp.setName(JacksonUtil.getString(block, "name"));
                tcp.setArguments(block.get("input"));
                yield tcp;
            }
            case "tool_result" -> {
                ToolResultContentPart trp = new ToolResultContentPart();
                trp.setToolCallId(JacksonUtil.getString(block, "tool_use_id"));
                JsonNode content = block.get("content");
                if (content != null) {
                    if (content.isTextual()) {
                        trp.setContent(content.asText());
                    } else if (content.isArray()) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode c : content) {
                            if ("text".equals(JacksonUtil.getString(c, "type"))) {
                                sb.append(JacksonUtil.getString(c, "text"));
                            }
                        }
                        trp.setContent(sb.toString());
                    }
                }
                trp.setIsError(JacksonUtil.getBoolean(block, "is_error"));
                yield trp;
            }
            case "thinking" -> {
                ThinkingContentPart thp = new ThinkingContentPart();
                thp.setThinking(JacksonUtil.getString(block, "thinking"));
                thp.setSignature(JacksonUtil.getString(block, "signature"));
                yield thp;
            }
            case "redacted_thinking" -> {
                // Preserve redacted thinking as UnknownContentPart
                yield new UnknownContentPart("redacted_thinking", block);
            }
            case "server_tool_use" -> {
                // Preserve server tool use as UnknownContentPart
                yield new UnknownContentPart("server_tool_use", block);
            }
            case "web_search_tool_result" -> {
                // Preserve web search results as UnknownContentPart
                yield new UnknownContentPart("web_search_tool_result", block);
            }
            case "code_execution_tool_result" -> {
                // Preserve code execution results as UnknownContentPart
                yield new UnknownContentPart("code_execution_tool_result", block);
            }
            default -> {
                // Unknown content block type - preserve as UnknownContentPart
                yield new UnknownContentPart(type, block);
            }
        };
    }

    private CanonicalTool normalizeTool(JsonNode tool) {
        CanonicalTool nt = new CanonicalTool();
        nt.setName(JacksonUtil.getString(tool, "name"));
        nt.setDescription(JacksonUtil.getString(tool, "description"));
        nt.setInputSchema(tool.get("input_schema"));
        // Preserve unknown tool fields
        nt.setRawExtra(JacksonUtil.extractExtra((ObjectNode) tool,
                "name", "description", "input_schema"));
        return nt;
    }

    private CanonicalRole mapRole(String role) {
        if (role == null) return CanonicalRole.USER;
        return switch (role) {
            case "user" -> CanonicalRole.USER;
            case "assistant" -> CanonicalRole.ASSISTANT;
            default -> CanonicalRole.USER;
        };
    }

    @Override
    public ObjectNode denormalizeRequest(CanonicalRequest request, BridgeContext context) {
        log.debug("denormalizing Anthropic Messages request: model={}", request.getModel());
        ObjectNode root = JacksonUtil.objectNode();
        root.put("model", request.getModel());

        if (request.getTemperature() != null) {
            root.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            root.put("top_p", request.getTopP());
        }
        if (request.getTopK() != null) {
            root.put("top_k", request.getTopK());
        }
        if (request.getStream() != null) {
            root.put("stream", request.getStream());
        }
        if (request.getServiceTier() != null) {
            root.put("service_tier", request.getServiceTier());
        }
        if (request.getInferenceGeo() != null) {
            root.put("inference_geo", request.getInferenceGeo());
        }
        if (request.getSpeed() != null) {
            root.put("speed", request.getSpeed());
        }
        if (request.getContainer() != null) {
            root.set("container", request.getContainer());
        }
        if (request.getMcpServers() != null && !request.getMcpServers().isEmpty()) {
            ArrayNode mcpArr = root.putArray("mcp_servers");
            request.getMcpServers().forEach(mcpArr::add);
        }
        if (request.getContextManagement() != null) {
            root.set("context_management", request.getContextManagement());
        }
        // Anthropic requires max_tokens
        root.put("max_tokens", request.getMaxOutputTokens() != null ? request.getMaxOutputTokens() : 4096);

        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            ArrayNode stopArr = root.putArray("stop_sequences");
            request.getStopSequences().forEach(stopArr::add);
        }

        // thinking config
        if (request.getReasoning() != null) {
            root.set("thinking", request.getReasoning());
        }

        // metadata
        if (request.getMetadata() != null) {
            root.set("metadata", request.getMetadata());
        }

        // Separate system messages from conversation messages
        StringBuilder systemText = new StringBuilder();
        ArrayNode messagesArr = root.putArray("messages");

        if (request.getMessages() != null) {
            for (CanonicalMessage msg : request.getMessages()) {
                if (msg.getRole() == CanonicalRole.SYSTEM || msg.getRole() == CanonicalRole.DEVELOPER) {
                    String text = extractText(msg);
                    if (text != null) {
                        if (!systemText.isEmpty()) {
                            systemText.append("\n\n");
                        }
                        systemText.append(text);
                    }
                } else if (msg.getRole() == CanonicalRole.TOOL) {
                    // Tool result -> user message with tool_result content block
                    ObjectNode toolResultMsg = JacksonUtil.objectNode();
                    toolResultMsg.put("role", "user");
                    ArrayNode contentArr = toolResultMsg.putArray("content");
                    if (msg.getToolCallId() != null) {
                        ObjectNode trBlock = JacksonUtil.objectNode();
                        trBlock.put("type", "tool_result");
                        trBlock.put("tool_use_id", msg.getToolCallId());
                        String text = extractText(msg);
                        if (text != null) {
                            trBlock.put("content", text);
                        }
                        if (msg.getContent() != null) {
                            for (CanonicalContentPart part : msg.getContent()) {
                                if (part instanceof ToolResultContentPart trp) {
                                    trBlock.put("tool_use_id", trp.getToolCallId());
                                    trBlock.put("content", trp.getContent() != null ? trp.getContent() : "");
                                    if (trp.getIsError() != null) {
                                        trBlock.put("is_error", trp.getIsError());
                                    }
                                }
                            }
                        }
                        contentArr.add(trBlock);
                    }
                    // Merge rawExtra back
                    if (msg.getRawExtra() != null) {
                        JacksonUtil.deepMergeInto(toolResultMsg, msg.getRawExtra());
                    }
                    messagesArr.add(toolResultMsg);
                } else {
                    messagesArr.add(denormalizeMessage(msg));
                }
            }
        }

        // Set system field
        if (!systemText.isEmpty()) {
            root.put("system", systemText.toString());
        }

        // tools
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            ArrayNode toolsArr = root.putArray("tools");
            for (CanonicalTool tool : request.getTools()) {
                ObjectNode toolNode = JacksonUtil.objectNode();
                toolNode.put("name", tool.getName());
                if (tool.getDescription() != null) {
                    toolNode.put("description", tool.getDescription());
                }
                if (tool.getInputSchema() != null) {
                    toolNode.set("input_schema", tool.getInputSchema());
                }
                // Merge rawExtra back
                if (tool.getRawExtra() != null) {
                    JacksonUtil.deepMergeInto(toolNode, tool.getRawExtra());
                }
                toolsArr.add(toolNode);
            }
        }

        // tool_choice
        if (request.getToolChoice() != null && request.getToolChoice().getValue() != null) {
            JsonNode tcValue = request.getToolChoice().getValue();
            if (tcValue.isTextual()) {
                String tcStr = tcValue.asText();
                ObjectNode tcNode = JacksonUtil.objectNode();
                if ("auto".equals(tcStr)) {
                    tcNode.put("type", "auto");
                } else if ("none".equals(tcStr)) {
                    tcNode.put("type", "none");
                } else if ("required".equals(tcStr)) {
                    tcNode.put("type", "any");
                } else {
                    tcNode.put("type", "auto");
                }
                root.set("tool_choice", tcNode);
            } else if (tcValue.isObject()) {
                root.set("tool_choice", mapToolChoiceToAnthropic(tcValue));
            }
        }

        // Merge rawExtra back
        if (request.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(root, request.getRawExtra());
        }

        return root;
    }

    private JsonNode mapToolChoiceToAnthropic(JsonNode toolChoice) {
        if (toolChoice == null || !toolChoice.isObject()) {
            return toolChoice;
        }
        String type = JacksonUtil.getString(toolChoice, "type");
        if ("function".equals(type)) {
            JsonNode function = toolChoice.get("function");
            String name = function != null ? JacksonUtil.getString(function, "name") : null;
            ObjectNode mapped = JacksonUtil.objectNode();
            mapped.put("type", "tool");
            if (name != null) {
                mapped.put("name", name);
            }
            return mapped;
        }
        return toolChoice;
    }

    private ObjectNode denormalizeMessage(CanonicalMessage msg) {
        ObjectNode msgNode = JacksonUtil.objectNode();
        msgNode.put("role", mapRoleToString(msg.getRole()));

        ArrayNode contentArr = msgNode.putArray("content");

        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            for (CanonicalToolCall tc : msg.getToolCalls()) {
                ObjectNode toolUseNode = JacksonUtil.objectNode();
                toolUseNode.put("type", "tool_use");
                toolUseNode.put("id", tc.getId() != null ? tc.getId() : "toolu_" + UUID.randomUUID());
                toolUseNode.put("name", tc.getName());
                toolUseNode.set("input", tc.getArguments() != null ? tc.getArguments() : JacksonUtil.objectNode());
                contentArr.add(toolUseNode);
            }
        }

        if (msg.getContent() != null) {
            for (CanonicalContentPart part : msg.getContent()) {
                if (part instanceof TextContentPart tp) {
                    ObjectNode textNode = JacksonUtil.objectNode();
                    textNode.put("type", "text");
                    textNode.put("text", tp.getText());
                    contentArr.add(textNode);
                } else if (part instanceof ImageContentPart ip) {
                    ObjectNode imgNode = JacksonUtil.objectNode();
                    imgNode.put("type", "image");
                    ObjectNode sourceNode = JacksonUtil.objectNode();
                    if (ip.getBase64() != null) {
                        sourceNode.put("type", "base64");
                        sourceNode.put("media_type", ip.getMediaType() != null ? ip.getMediaType() : "image/png");
                        sourceNode.put("data", ip.getBase64());
                    } else if (ip.getUrl() != null) {
                        sourceNode.put("type", "url");
                        sourceNode.put("url", ip.getUrl());
                    }
                    imgNode.set("source", sourceNode);
                    contentArr.add(imgNode);
                } else if (part instanceof ThinkingContentPart thp) {
                    ObjectNode thinkingNode = JacksonUtil.objectNode();
                    thinkingNode.put("type", "thinking");
                    thinkingNode.put("thinking", thp.getThinking());
                    if (thp.getSignature() != null) {
                        thinkingNode.put("signature", thp.getSignature());
                    }
                    contentArr.add(thinkingNode);
                } else if (part instanceof FileContentPart fp) {
                    ObjectNode docNode = JacksonUtil.objectNode();
                    docNode.put("type", "document");
                    ObjectNode sourceNode = JacksonUtil.objectNode();
                    if (fp.getBase64() != null) {
                        sourceNode.put("type", "base64");
                        sourceNode.put("media_type", fp.getMediaType() != null ? fp.getMediaType() : "application/octet-stream");
                        sourceNode.put("data", fp.getBase64());
                    } else if (fp.getUrl() != null) {
                        sourceNode.put("type", "url");
                        sourceNode.put("url", fp.getUrl());
                    } else if (fp.getFileId() != null) {
                        sourceNode.put("type", "file_id");
                        sourceNode.put("file_id", fp.getFileId());
                    }
                    docNode.set("source", sourceNode);
                    if (fp.getFilename() != null) {
                        docNode.put("title", fp.getFilename());
                    }
                    contentArr.add(docNode);
                } else if (part instanceof ToolCallContentPart tcp) {
                    ObjectNode toolUseNode = JacksonUtil.objectNode();
                    toolUseNode.put("type", "tool_use");
                    toolUseNode.put("id", tcp.getId() != null ? tcp.getId() : "toolu_" + UUID.randomUUID());
                    toolUseNode.put("name", tcp.getName());
                    toolUseNode.set("input", tcp.getArguments() != null ? tcp.getArguments() : JacksonUtil.objectNode());
                    contentArr.add(toolUseNode);
                } else if (part instanceof ToolResultContentPart trp) {
                    ObjectNode trNode = JacksonUtil.objectNode();
                    trNode.put("type", "tool_result");
                    trNode.put("tool_use_id", trp.getToolCallId());
                    if (trp.getContent() != null) {
                        trNode.put("content", trp.getContent());
                    }
                    if (trp.getIsError() != null) {
                        trNode.put("is_error", trp.getIsError());
                    }
                    contentArr.add(trNode);
                } else if (part instanceof RefusalContentPart rp) {
                    // Anthropic doesn't have a direct refusal content block
                    // Convert to text with a note
                    ObjectNode textNode = JacksonUtil.objectNode();
                    textNode.put("type", "text");
                    textNode.put("text", "[Refusal: " + rp.getRefusal() + "]");
                    contentArr.add(textNode);
                } else if (part instanceof UnknownContentPart up) {
                    // Pass through unknown parts as-is
                    if (up.getRaw() != null) {
                        contentArr.add(up.getRaw());
                    }
                }
            }
        }

        // Ensure content is not empty
        if (contentArr.isEmpty()) {
            ObjectNode textNode = JacksonUtil.objectNode();
            textNode.put("type", "text");
            textNode.put("text", "");
            contentArr.add(textNode);
        }

        // Merge rawExtra back
        if (msg.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(msgNode, msg.getRawExtra());
        }

        return msgNode;
    }

    private String mapRoleToString(CanonicalRole role) {
        if (role == null) return "user";
        return switch (role) {
            case SYSTEM, DEVELOPER, USER, TOOL -> "user";
            case ASSISTANT -> "assistant";
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
        log.debug("normalizing Anthropic Messages response: id={}", JacksonUtil.getString(rawResponse, "id"));
        CanonicalResponse resp = new CanonicalResponse();
        resp.setId(JacksonUtil.getString(rawResponse, "id"));
        resp.setRequestedModel(JacksonUtil.getString(rawResponse, "model"));
        resp.setUpstreamModel(JacksonUtil.getString(rawResponse, "model"));

        // Anthropic doesn't return created timestamp in the same way
        resp.setCreated(System.currentTimeMillis() / 1000);

        // content blocks
        JsonNode contentNode = rawResponse.get("content");
        if (contentNode != null && contentNode.isArray()) {
            CanonicalMessage nm = new CanonicalMessage();
            nm.setRole(CanonicalRole.ASSISTANT);
            List<CanonicalContentPart> parts = new ArrayList<>();
            List<CanonicalToolCall> toolCalls = new ArrayList<>();

            for (JsonNode block : contentNode) {
                String type = JacksonUtil.getString(block, "type");
                if ("text".equals(type)) {
                    TextContentPart tp = new TextContentPart();
                    tp.setText(JacksonUtil.getString(block, "text"));
                    parts.add(tp);
                } else if ("tool_use".equals(type)) {
                    CanonicalToolCall tc = new CanonicalToolCall();
                    tc.setId(JacksonUtil.getString(block, "id"));
                    tc.setName(JacksonUtil.getString(block, "name"));
                    tc.setType("function");
                    tc.setArguments(block.get("input"));
                    toolCalls.add(tc);
                } else if ("thinking".equals(type)) {
                    ThinkingContentPart thp = new ThinkingContentPart();
                    thp.setThinking(JacksonUtil.getString(block, "thinking"));
                    thp.setSignature(JacksonUtil.getString(block, "signature"));
                    parts.add(thp);
                } else if ("redacted_thinking".equals(type)) {
                    parts.add(new UnknownContentPart("redacted_thinking", block));
                } else if ("server_tool_use".equals(type)) {
                    parts.add(new UnknownContentPart("server_tool_use", block));
                } else if ("web_search_tool_result".equals(type)) {
                    parts.add(new UnknownContentPart("web_search_tool_result", block));
                } else if ("code_execution_tool_result".equals(type)) {
                    parts.add(new UnknownContentPart("code_execution_tool_result", block));
                } else {
                    // Unknown content block - preserve as UnknownContentPart
                    parts.add(new UnknownContentPart(type, block));
                }
            }

            nm.setContent(parts);
            if (!toolCalls.isEmpty()) {
                nm.setToolCalls(toolCalls);
            }
            // reasoning_content on the response (mimo, claude with extended thinking exposed)
            JsonNode rc = rawResponse.get("reasoning_content");
            if (rc != null && !rc.isNull()) {
                nm.setReasoningContent(rc.asText());
            }
            resp.setOutputMessages(List.of(nm));
        }

        // stop_reason
        resp.setStopReason(JacksonUtil.getString(rawResponse, "stop_reason"));

        // usage
        JsonNode usageNode = rawResponse.get("usage");
        if (usageNode != null) {
            CanonicalUsage usage = new CanonicalUsage();
            usage.setInputTokens(JacksonUtil.getInt(usageNode, "input_tokens"));
            usage.setOutputTokens(JacksonUtil.getInt(usageNode, "output_tokens"));
            // cache_creation and cache_read are separate fields
            usage.setCacheCreationInputTokens(JacksonUtil.getInt(usageNode, "cache_creation_input_tokens"));
            usage.setCacheReadInputTokens(JacksonUtil.getInt(usageNode, "cache_read_input_tokens"));
            // Cached total (for OpenAI-style merged field)
            Integer cacheCreation = usage.getCacheCreationInputTokens();
            Integer cacheRead = usage.getCacheReadInputTokens();
            if (cacheCreation != null || cacheRead != null) {
                usage.setCachedInputTokens(
                        (cacheCreation != null ? cacheCreation : 0) + (cacheRead != null ? cacheRead : 0));
            }
            Integer input = usage.getInputTokens();
            Integer output = usage.getOutputTokens();
            if (input != null && output != null) {
                usage.setTotalTokens(input + output);
            }
            // service_tier
            usage.setServiceTier(JacksonUtil.getString(usageNode, "service_tier"));
            // server_tool_use.web_search_requests
            JsonNode stu = usageNode.get("server_tool_use");
            if (stu != null) {
                usage.setWebSearchRequests(JacksonUtil.getInt(stu, "web_search_requests"));
            }
            // Preserve unknown usage fields
            usage.setRawExtra(JacksonUtil.extractExtra((ObjectNode) usageNode,
                    "input_tokens", "output_tokens",
                    "cache_creation_input_tokens", "cache_read_input_tokens",
                    "server_tool_use", "service_tier"));
            resp.setUsage(usage);
        }

        // Preserve unknown response fields
        resp.setRawExtra(JacksonUtil.extractExtra((ObjectNode) rawResponse,
                "id", "type", "role", "model", "content", "stop_reason",
                "stop_sequence", "usage", "reasoning_content"));

        return resp;
    }

    @Override
    public ObjectNode denormalizeResponse(CanonicalResponse response, BridgeContext context) {
        log.debug("denormalizing Anthropic Messages response: id={}", response.getId());
        ObjectNode root = JacksonUtil.objectNode();
        root.put("id", response.getId() != null ? response.getId() : "msg_" + UUID.randomUUID());
        root.put("type", "message");
        root.put("role", "assistant");
        root.put("model", response.getModel());

        // content blocks
        ArrayNode contentArr = root.putArray("content");
        if (response.getOutputMessages() != null && !response.getOutputMessages().isEmpty()) {
            CanonicalMessage msg = response.getOutputMessages().get(0);

            if (msg.getToolCalls() != null) {
                for (CanonicalToolCall tc : msg.getToolCalls()) {
                    ObjectNode toolUseNode = JacksonUtil.objectNode();
                    toolUseNode.put("type", "tool_use");
                    toolUseNode.put("id", tc.getId() != null ? tc.getId() : "toolu_" + UUID.randomUUID());
                    toolUseNode.put("name", tc.getName());
                    toolUseNode.set("input", tc.getArguments() != null ? tc.getArguments() : JacksonUtil.objectNode());
                    contentArr.add(toolUseNode);
                }
            }

            if (msg.getContent() != null) {
                for (CanonicalContentPart part : msg.getContent()) {
                    if (part instanceof TextContentPart tp) {
                        ObjectNode textNode = JacksonUtil.objectNode();
                        textNode.put("type", "text");
                        textNode.put("text", tp.getText());
                        contentArr.add(textNode);
                    } else if (part instanceof ThinkingContentPart thp) {
                        ObjectNode thinkingNode = JacksonUtil.objectNode();
                        thinkingNode.put("type", "thinking");
                        thinkingNode.put("thinking", thp.getThinking());
                        if (thp.getSignature() != null) {
                            thinkingNode.put("signature", thp.getSignature());
                        }
                        contentArr.add(thinkingNode);
                    } else if (part instanceof ToolCallContentPart tcp) {
                        ObjectNode toolUseNode = JacksonUtil.objectNode();
                        toolUseNode.put("type", "tool_use");
                        toolUseNode.put("id", tcp.getId() != null ? tcp.getId() : "toolu_" + UUID.randomUUID());
                        toolUseNode.put("name", tcp.getName());
                        toolUseNode.set("input", tcp.getArguments() != null ? tcp.getArguments() : JacksonUtil.objectNode());
                        contentArr.add(toolUseNode);
                    } else if (part instanceof FileContentPart fp) {
                        ObjectNode docNode = JacksonUtil.objectNode();
                        docNode.put("type", "document");
                        ObjectNode sourceNode = JacksonUtil.objectNode();
                        if (fp.getBase64() != null) {
                            sourceNode.put("type", "base64");
                            sourceNode.put("media_type", fp.getMediaType() != null ? fp.getMediaType() : "application/octet-stream");
                            sourceNode.put("data", fp.getBase64());
                        } else if (fp.getUrl() != null) {
                            sourceNode.put("type", "url");
                            sourceNode.put("url", fp.getUrl());
                        }
                        docNode.set("source", sourceNode);
                        contentArr.add(docNode);
                    } else if (part instanceof UnknownContentPart up) {
                        // Pass through unknown parts
                        if (up.getRaw() != null) {
                            contentArr.add(up.getRaw());
                        }
                    }
                }
            }

            // Merge rawExtra back
            if (msg.getRawExtra() != null) {
                JacksonUtil.deepMergeInto(root, msg.getRawExtra());
            }

            // reasoning_content on the assistant message
            if (msg.getReasoningContent() != null) {
                root.put("reasoning_content", msg.getReasoningContent());
            }
        }

        // Ensure content is not empty
        if (contentArr.isEmpty()) {
            ObjectNode textNode = JacksonUtil.objectNode();
            textNode.put("type", "text");
            textNode.put("text", "");
            contentArr.add(textNode);
        }

        // stop_reason
        root.put("stop_reason", response.getStopReason() != null ? response.getStopReason() : "end_turn");

        // usage
        if (response.getUsage() != null) {
            ObjectNode usageNode = JacksonUtil.objectNode();
            usageNode.put("input_tokens", response.getUsage().getInputTokens() != null ? response.getUsage().getInputTokens() : 0);
            usageNode.put("output_tokens", response.getUsage().getOutputTokens() != null ? response.getUsage().getOutputTokens() : 0);
            // Separate cache_creation vs cache_read
            if (response.getUsage().getCacheCreationInputTokens() != null) {
                usageNode.put("cache_creation_input_tokens", response.getUsage().getCacheCreationInputTokens());
            } else if (response.getUsage().getCachedInputTokens() != null) {
                // Fall back to legacy merged cachedInputTokens
                usageNode.put("cache_creation_input_tokens", response.getUsage().getCachedInputTokens());
            }
            if (response.getUsage().getCacheReadInputTokens() != null) {
                usageNode.put("cache_read_input_tokens", response.getUsage().getCacheReadInputTokens());
            }
            // server_tool_use.web_search_requests
            if (response.getUsage().getWebSearchRequests() != null) {
                ObjectNode stu = JacksonUtil.objectNode();
                stu.put("web_search_requests", response.getUsage().getWebSearchRequests());
                usageNode.set("server_tool_use", stu);
            }
            // service_tier
            if (response.getUsage().getServiceTier() != null) {
                usageNode.put("service_tier", response.getUsage().getServiceTier());
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
            // Comment/ping
            if (event.getComment() != null) {
                return new CanonicalStreamEvent(CanonicalStreamEventType.PING);
            }
            return null;
        }

        String type = JacksonUtil.getString(data, "type");
        if (type == null) {
            type = event.getEvent();
        }
        if (type == null) return null;

        CanonicalStreamEvent nse = new CanonicalStreamEvent();
        nse.setRaw(data);

        switch (type) {
            case "message_start" -> {
                nse.setType(CanonicalStreamEventType.START);
                JsonNode message = data.get("message");
                if (message != null) {
                    nse.setResponseId(JacksonUtil.getString(message, "id"));
                    JsonNode usage = message.get("usage");
                    if (usage != null) {
                        nse.setUsage(normalizeAnthropicUsage(usage));
                    }
                }
            }
            case "content_block_start" -> {
                nse.setType(CanonicalStreamEventType.CONTENT_BLOCK_START);
                nse.setContentIndex(JacksonUtil.getInt(data, "index"));
                JsonNode block = data.get("content_block");
                if (block != null) {
                    String blockType = JacksonUtil.getString(block, "type");
                    if ("tool_use".equals(blockType)) {
                        nse.setType(CanonicalStreamEventType.TOOL_CALL_START);
                        nse.setToolIndex(JacksonUtil.getInt(data, "index"));
                        nse.setToolCallId(JacksonUtil.getString(block, "id"));
                        nse.setToolName(JacksonUtil.getString(block, "name"));
                    } else if ("thinking".equals(blockType)) {
                        // Thinking block start
                    }
                }
            }
            case "content_block_delta" -> {
                nse.setContentIndex(JacksonUtil.getInt(data, "index"));
                JsonNode delta = data.get("delta");
                if (delta != null) {
                    String deltaType = JacksonUtil.getString(delta, "type");
                    if ("text_delta".equals(deltaType)) {
                        nse.setType(CanonicalStreamEventType.TEXT_DELTA);
                        nse.setDeltaText(JacksonUtil.getString(delta, "text"));
                    } else if ("input_json_delta".equals(deltaType)) {
                        nse.setType(CanonicalStreamEventType.TOOL_ARGUMENTS_DELTA);
                        nse.setToolIndex(JacksonUtil.getInt(data, "index"));
                        nse.setToolArgumentsDelta(JacksonUtil.getString(delta, "partial_json"));
                    } else if ("thinking_delta".equals(deltaType)) {
                        nse.setType(CanonicalStreamEventType.THINKING_DELTA);
                        nse.setThinkingDelta(JacksonUtil.getString(delta, "thinking"));
                    } else if ("signature_delta".equals(deltaType)) {
                        nse.setType(CanonicalStreamEventType.THINKING_SIGNATURE);
                        nse.setThinkingSignature(JacksonUtil.getString(delta, "signature"));
                    } else if ("citations_delta".equals(deltaType)) {
                        // Preserve citation in raw for round-trip
                        nse.setType(CanonicalStreamEventType.UNKNOWN);
                    } else if ("compaction_delta".equals(deltaType)) {
                        nse.setType(CanonicalStreamEventType.UNKNOWN);
                    }
                }
            }
            case "content_block_stop" -> {
                nse.setContentIndex(JacksonUtil.getInt(data, "index"));
                nse.setType(CanonicalStreamEventType.CONTENT_BLOCK_DONE);
            }
            case "message_delta" -> {
                nse.setType(CanonicalStreamEventType.MESSAGE_DELTA);
                JsonNode delta = data.get("delta");
                if (delta != null) {
                    String stopReason = JacksonUtil.getString(delta, "stop_reason");
                    if (stopReason != null) {
                        nse.setStopReason(StopReasonMapper.toNormalizedFromAnthropic(stopReason));
                    }
                }
                JsonNode usage = data.get("usage");
                if (usage != null) {
                    nse.setUsage(normalizeAnthropicUsage(usage));
                }
            }
            case "message_stop" -> {
                nse.setType(CanonicalStreamEventType.DONE);
            }
            case "ping" -> {
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
        state.setCreated(System.currentTimeMillis() / 1000);
        state.setModel(context.requestedModel());
        state.setResponseId("msg_" + UUID.randomUUID());

        // Track content blocks for Anthropic's block start/stop model
        final boolean[] textBlockOpen = {false};
        final int[] nextBlockIndex = {0};
        final boolean[] messageStarted = {false};
        final boolean[] messageDeltaEmitted = {false};

        return events.concatMap(event -> {
            List<SseFrame> result = new ArrayList<>();

            state.apply(event);

            switch (event.getType()) {
                case START -> {
                    state.setResponseId(event.getResponseId() != null ? event.getResponseId() : state.getResponseId());
                    result.add(buildMessageStartFrame(state, event.getUsage()));
                    messageStarted[0] = true;
                }
                case TEXT_DELTA -> {
                    if (!messageStarted[0]) {
                        result.add(buildMessageStartFrame(state, state.getUsage()));
                        messageStarted[0] = true;
                    }
                    // Open text block if needed
                    if (!textBlockOpen[0]) {
                        ObjectNode startRoot = JacksonUtil.objectNode();
                        startRoot.put("type", "content_block_start");
                        startRoot.put("index", nextBlockIndex[0]);
                        ObjectNode block = startRoot.putObject("content_block");
                        block.put("type", "text");
                        block.put("text", "");
                        result.add(new SseFrame("content_block_start", startRoot.toString()));
                        textBlockOpen[0] = true;
                    }

                    ObjectNode deltaRoot = JacksonUtil.objectNode();
                    deltaRoot.put("type", "content_block_delta");
                    deltaRoot.put("index", nextBlockIndex[0]);
                    ObjectNode delta = deltaRoot.putObject("delta");
                    delta.put("type", "text_delta");
                    delta.put("text", event.getDeltaText() != null ? event.getDeltaText() : "");
                    result.add(new SseFrame("content_block_delta", deltaRoot.toString()));
                }
                case THINKING_DELTA -> {
                    if (!messageStarted[0]) {
                        result.add(buildMessageStartFrame(state, state.getUsage()));
                        messageStarted[0] = true;
                    }
                    ObjectNode deltaRoot = JacksonUtil.objectNode();
                    deltaRoot.put("type", "content_block_delta");
                    deltaRoot.put("index", nextBlockIndex[0]);
                    ObjectNode delta = deltaRoot.putObject("delta");
                    delta.put("type", "thinking_delta");
                    delta.put("thinking", event.getThinkingDelta() != null ? event.getThinkingDelta() : "");
                    result.add(new SseFrame("content_block_delta", deltaRoot.toString()));
                }
                case THINKING_SIGNATURE -> {
                    if (!messageStarted[0]) {
                        result.add(buildMessageStartFrame(state, state.getUsage()));
                        messageStarted[0] = true;
                    }
                    ObjectNode deltaRoot = JacksonUtil.objectNode();
                    deltaRoot.put("type", "content_block_delta");
                    deltaRoot.put("index", nextBlockIndex[0]);
                    ObjectNode delta = deltaRoot.putObject("delta");
                    delta.put("type", "signature_delta");
                    delta.put("signature", event.getThinkingSignature() != null ? event.getThinkingSignature() : "");
                    result.add(new SseFrame("content_block_delta", deltaRoot.toString()));
                }
                case TOOL_CALL_START -> {
                    if (!messageStarted[0]) {
                        result.add(buildMessageStartFrame(state, state.getUsage()));
                        messageStarted[0] = true;
                    }
                    // Close text block if open
                    if (textBlockOpen[0]) {
                        ObjectNode stopRoot = JacksonUtil.objectNode();
                        stopRoot.put("type", "content_block_stop");
                        stopRoot.put("index", nextBlockIndex[0]);
                        result.add(new SseFrame("content_block_stop", stopRoot.toString()));
                        nextBlockIndex[0]++;
                        textBlockOpen[0] = false;
                    }

                    ObjectNode startRoot = JacksonUtil.objectNode();
                    startRoot.put("type", "content_block_start");
                    startRoot.put("index", nextBlockIndex[0]);
                    ObjectNode block = startRoot.putObject("content_block");
                    block.put("type", "tool_use");
                    block.put("id", event.getToolCallId() != null ? event.getToolCallId() : "toolu_" + UUID.randomUUID());
                    block.put("name", event.getToolName() != null ? event.getToolName() : "");
                    block.putObject("input");
                    result.add(new SseFrame("content_block_start", startRoot.toString()));
                }
                case TOOL_ARGUMENTS_DELTA -> {
                    ObjectNode deltaRoot = JacksonUtil.objectNode();
                    deltaRoot.put("type", "content_block_delta");
                    deltaRoot.put("index", nextBlockIndex[0]);
                    ObjectNode delta = deltaRoot.putObject("delta");
                    delta.put("type", "input_json_delta");
                    delta.put("partial_json", event.getToolArgumentsDelta() != null ? event.getToolArgumentsDelta() : "");
                    result.add(new SseFrame("content_block_delta", deltaRoot.toString()));
                }
                case TOOL_CALL_DONE -> {
                    ObjectNode stopRoot = JacksonUtil.objectNode();
                    stopRoot.put("type", "content_block_stop");
                    stopRoot.put("index", nextBlockIndex[0]);
                    result.add(new SseFrame("content_block_stop", stopRoot.toString()));
                    nextBlockIndex[0]++;
                }
                case CONTENT_BLOCK_DONE -> {
                    if (textBlockOpen[0]) {
                        ObjectNode stopRoot = JacksonUtil.objectNode();
                        stopRoot.put("type", "content_block_stop");
                        stopRoot.put("index", nextBlockIndex[0]);
                        result.add(new SseFrame("content_block_stop", stopRoot.toString()));
                        nextBlockIndex[0]++;
                        textBlockOpen[0] = false;
                    }
                }
                case MESSAGE_DELTA -> {
                    // Close text block if open
                    if (textBlockOpen[0]) {
                        ObjectNode stopRoot = JacksonUtil.objectNode();
                        stopRoot.put("type", "content_block_stop");
                        stopRoot.put("index", nextBlockIndex[0]);
                        result.add(new SseFrame("content_block_stop", stopRoot.toString()));
                        nextBlockIndex[0]++;
                        textBlockOpen[0] = false;
                    }

                    ObjectNode deltaRoot = JacksonUtil.objectNode();
                    deltaRoot.put("type", "message_delta");
                    ObjectNode delta = deltaRoot.putObject("delta");
                    delta.put("stop_reason",
                            StopReasonMapper.toAnthropicStopReason(event.getStopReason()));
                    delta.putNull("stop_sequence");
                    if (event.getUsage() != null) {
                        ObjectNode usage = deltaRoot.putObject("usage");
                        if (event.getUsage().getInputTokens() != null) {
                            usage.put("input_tokens", event.getUsage().getInputTokens());
                        }
                        usage.put("output_tokens",
                                event.getUsage().getOutputTokens() != null ? event.getUsage().getOutputTokens() : 0);
                        addAnthropicUsageDetails(usage, event.getUsage(), true);
                        result.add(new SseFrame("message_delta", deltaRoot.toString()));
                        messageDeltaEmitted[0] = true;
                    }
                }
                case USAGE_DELTA -> {
                    // Anthropic puts usage in message_start and message_delta
                }
                case DONE -> {
                    if (!messageStarted[0]) {
                        result.add(buildMessageStartFrame(state, state.getUsage()));
                        messageStarted[0] = true;
                    }
                    // Close text block if open
                    if (textBlockOpen[0]) {
                        ObjectNode stopRoot = JacksonUtil.objectNode();
                        stopRoot.put("type", "content_block_stop");
                        stopRoot.put("index", nextBlockIndex[0]);
                        result.add(new SseFrame("content_block_stop", stopRoot.toString()));
                        nextBlockIndex[0]++;
                        textBlockOpen[0] = false;
                    }

                    // Emit message_delta only if MESSAGE_DELTA didn't already emit one
                    if (!messageDeltaEmitted[0]) {
                        ObjectNode deltaRoot = JacksonUtil.objectNode();
                        deltaRoot.put("type", "message_delta");
                        ObjectNode delta = deltaRoot.putObject("delta");
                        delta.put("stop_reason",
                                StopReasonMapper.toAnthropicStopReason(state.getStopReason()));
                        delta.putNull("stop_sequence");
                        CanonicalUsage usage = state.getUsage();
                        ObjectNode usageNode = deltaRoot.putObject("usage");
                        usageNode.put("input_tokens",
                                usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0);
                        usageNode.put("output_tokens",
                                usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0);
                        if (usage != null) {
                            addAnthropicUsageDetails(usageNode, usage, true);
                        }
                        result.add(new SseFrame("message_delta", deltaRoot.toString()));
                    }

                    ObjectNode stopRoot = JacksonUtil.objectNode();
                    stopRoot.put("type", "message_stop");
                    result.add(new SseFrame("message_stop", stopRoot.toString()));
                }
                case ERROR -> {
                    ObjectNode errorRoot = JacksonUtil.objectNode();
                    errorRoot.put("type", "error");
                    ObjectNode error = errorRoot.putObject("error");
                    error.put("type", "api_error");
                    error.put("message", event.getDeltaText() != null ? event.getDeltaText() : "stream error");
                    result.add(new SseFrame("error", errorRoot.toString()));
                }
                case PING -> {
                    ObjectNode pingRoot = JacksonUtil.objectNode();
                    pingRoot.put("type", "ping");
                    result.add(new SseFrame("ping", pingRoot.toString()));
                }
                default -> {
                    // Unknown events - ignore
                }
            }

            return Flux.fromIterable(result);
        });
    }

    private CanonicalUsage normalizeAnthropicUsage(JsonNode usageNode) {
        CanonicalUsage usage = new CanonicalUsage();
        usage.setInputTokens(JacksonUtil.getInt(usageNode, "input_tokens"));
        usage.setOutputTokens(JacksonUtil.getInt(usageNode, "output_tokens"));
        usage.setCacheCreationInputTokens(JacksonUtil.getInt(usageNode, "cache_creation_input_tokens"));
        usage.setCacheReadInputTokens(JacksonUtil.getInt(usageNode, "cache_read_input_tokens"));
        Integer input = usage.getInputTokens();
        Integer output = usage.getOutputTokens();
        if (input != null && output != null) {
            usage.setTotalTokens(input + output);
        }
        Integer cacheCreation = usage.getCacheCreationInputTokens();
        Integer cacheRead = usage.getCacheReadInputTokens();
        if (cacheCreation != null || cacheRead != null) {
            usage.setCachedInputTokens((cacheCreation != null ? cacheCreation : 0) + (cacheRead != null ? cacheRead : 0));
        }
        usage.setServiceTier(JacksonUtil.getString(usageNode, "service_tier"));
        JsonNode serverToolUse = usageNode.get("server_tool_use");
        if (serverToolUse != null) {
            usage.setWebSearchRequests(JacksonUtil.getInt(serverToolUse, "web_search_requests"));
        }
        if (usageNode instanceof ObjectNode objectNode) {
            usage.setRawExtra(JacksonUtil.extractExtra(objectNode,
                    "input_tokens", "output_tokens",
                    "cache_creation_input_tokens", "cache_read_input_tokens",
                    "server_tool_use", "service_tier"));
        }
        return usage;
    }

    private SseFrame buildMessageStartFrame(StreamStateTracker state, CanonicalUsage usageState) {
        ObjectNode root = JacksonUtil.objectNode();
        root.put("type", "message_start");
        ObjectNode message = root.putObject("message");
        message.put("id", state.getResponseId());
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("model", state.getModel());
        message.putNull("stop_reason");
        message.putNull("stop_sequence");
        message.putArray("content");
        ObjectNode usage = message.putObject("usage");
        usage.put("input_tokens", usageState != null && usageState.getInputTokens() != null
                ? usageState.getInputTokens() : 0);
        usage.put("output_tokens", usageState != null && usageState.getOutputTokens() != null
                ? usageState.getOutputTokens() : 0);
        addAnthropicUsageDetails(usage, usageState, false);
        return new SseFrame("message_start", root.toString());
    }

    private void addAnthropicUsageDetails(ObjectNode usageNode, CanonicalUsage usage, boolean outputOnlyEvent) {
        if (usage == null) return;
        if (!outputOnlyEvent && usage.getCacheCreationInputTokens() != null) {
            usageNode.put("cache_creation_input_tokens", usage.getCacheCreationInputTokens());
        } else if (!outputOnlyEvent && usage.getCachedInputTokens() != null) {
            usageNode.put("cache_creation_input_tokens", usage.getCachedInputTokens());
        }
        if (!outputOnlyEvent && usage.getCacheReadInputTokens() != null) {
            usageNode.put("cache_read_input_tokens", usage.getCacheReadInputTokens());
        }
        if (usage.getWebSearchRequests() != null) {
            ObjectNode serverToolUse = JacksonUtil.objectNode();
            serverToolUse.put("web_search_requests", usage.getWebSearchRequests());
            usageNode.set("server_tool_use", serverToolUse);
        }
        if (usage.getServiceTier() != null) {
            usageNode.put("service_tier", usage.getServiceTier());
        }
        if (usage.getRawExtra() != null) {
            JacksonUtil.deepMergeInto(usageNode, usage.getRawExtra());
        }
    }
}
