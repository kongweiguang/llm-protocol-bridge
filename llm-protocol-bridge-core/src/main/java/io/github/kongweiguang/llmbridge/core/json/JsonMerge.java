package io.github.kongweiguang.llmbridge.core.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility for deep-merging JSON objects.
 * Used to merge codec-generated request bodies with YAML-configured body overrides.
 */
public final class JsonMerge {

    private JsonMerge() {
    }

    /**
     * Deep-merges the override into the base.
     * Rules:
     * <ul>
     *   <li>Object + Object: recursive merge</li>
     *   <li>Array: override replaces base</li>
     *   <li>Scalar: override replaces base</li>
     *   <li>Null override: removes the field from base</li>
     * </ul>
     *
     * @param base     the base JSON object (will be modified)
     * @param override the override JSON object
     * @return the merged result (same instance as base)
     */
    public static ObjectNode deepMerge(ObjectNode base, ObjectNode override) {
        if (override == null || override.isNull()) {
            return base;
        }
        if (base == null) {
            return override;
        }

        var fields = override.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String key = entry.getKey();
            JsonNode overrideValue = entry.getValue();
            JsonNode baseValue = base.get(key);

            if (overrideValue.isNull()) {
                base.putNull(key);
            } else if (baseValue != null && baseValue.isObject() && overrideValue.isObject()) {
                deepMerge((ObjectNode) baseValue, (ObjectNode) overrideValue);
            } else {
                base.set(key, overrideValue);
            }
        }
        return base;
    }
}
