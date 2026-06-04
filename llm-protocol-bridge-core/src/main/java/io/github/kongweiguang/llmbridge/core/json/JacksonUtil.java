package io.github.kongweiguang.llmbridge.core.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

/**
 * Utility methods for working with Jackson JSON nodes.
 */
public final class JacksonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JacksonUtil() {
    }

    /**
     * Returns the shared ObjectMapper instance.
     *
     * @return the ObjectMapper
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Creates a new empty ObjectNode.
     *
     * @return a new ObjectNode
     */
    public static ObjectNode objectNode() {
        return MAPPER.createObjectNode();
    }

    /**
     * Creates a new empty ArrayNode.
     *
     * @return a new ArrayNode
     */
    public static ArrayNode arrayNode() {
        return MAPPER.createArrayNode();
    }

    /**
     * Extracts a string field from a JSON node, returning null if absent.
     *
     * @param node  the JSON node
     * @param field the field name
     * @return the string value, or null
     */
    public static String getString(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value.isTextual() ? value.asText() : value.toString();
    }

    /**
     * Extracts an integer field from a JSON node, returning null if absent.
     *
     * @param node  the JSON node
     * @param field the field name
     * @return the integer value, or null
     */
    public static Integer getInt(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asInt();
    }

    /**
     * Extracts a double field from a JSON node, returning null if absent.
     *
     * @param node  the JSON node
     * @param field the field name
     * @return the double value, or null
     */
    public static Double getDouble(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asDouble();
    }

    /**
     * Extracts a boolean field from a JSON node, returning null if absent.
     *
     * @param node  the JSON node
     * @param field the field name
     * @return the boolean value, or null
     */
    public static Boolean getBoolean(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asBoolean();
    }

    /**
     * Extracts a long field from a JSON node, returning null if absent.
     *
     * @param node  the JSON node
     * @param field the field name
     * @return the long value, or null
     */
    public static Long getLong(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asLong();
    }

    /**
     * Tries to parse a string as JSON. If parsing fails, returns null.
     *
     * @param text the string to parse
     * @return the parsed JsonNode, or null
     */
    public static JsonNode tryParse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Converts a JsonNode to a string, returning null if the node is null.
     *
     * @param node the JSON node
     * @return the string representation, or null
     */
    public static String toStringOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }

    /**
     * Deep-merges the source ObjectNode's fields into the target.
     * Existing fields in target are NOT overwritten.
     * Used to merge rawExtra back into denormalized output.
     *
     * @param target the target ObjectNode to merge into
     * @param source the source ObjectNode to merge from
     */
    public static void deepMergeInto(ObjectNode target, ObjectNode source) {
        if (source == null || source.isNull()) return;
        if (target == null) return;

        var fields = source.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String key = entry.getKey();
            JsonNode sourceValue = entry.getValue();
            JsonNode targetValue = target.get(key);

            if (targetValue == null) {
                // Field doesn't exist in target, add it
                target.set(key, sourceValue);
            } else if (targetValue.isObject() && sourceValue.isObject()) {
                // Both are objects, recurse
                deepMergeInto((ObjectNode) targetValue, (ObjectNode) sourceValue);
            }
            // Otherwise, keep the target value (don't overwrite)
        }
    }

    /**
     * Collects all fields from a JsonNode into a string-keyed map.
     * Used for extracting known fields and preserving unknown ones.
     *
     * @param node the source ObjectNode
     * @param knownFields field names to exclude
     * @return an ObjectNode containing only the non-known fields
     */
    public static ObjectNode extractExtra(ObjectNode node, String... knownFields) {
        if (node == null) {
            return objectNode();
        }
        ObjectNode extra = objectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            boolean known = false;
            for (String kf : knownFields) {
                if (kf.equals(entry.getKey())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                extra.set(entry.getKey(), entry.getValue());
            }
        }
        return extra;
    }

    /**
     * Applies default values from {@code defaults} into {@code target},
     * only filling fields that are missing in the target (never overwrites existing values).
     * Recurses into nested objects.
     *
     * @param target   the target ObjectNode to fill
     * @param defaults the default values to apply
     */
    public static void applyMissing(ObjectNode target, ObjectNode defaults) {
        if (defaults == null || defaults.isNull() || target == null) return;
        var fields = defaults.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String key = entry.getKey();
            JsonNode defaultValue = entry.getValue();
            JsonNode targetValue = target.get(key);
            if (targetValue == null || targetValue.isNull()) {
                target.set(key, defaultValue);
            } else if (targetValue.isObject() && defaultValue.isObject()) {
                applyMissing((ObjectNode) targetValue, (ObjectNode) defaultValue);
            }
        }
    }
}
