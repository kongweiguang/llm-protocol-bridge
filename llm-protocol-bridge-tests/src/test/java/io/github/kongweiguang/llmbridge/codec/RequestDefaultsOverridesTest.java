package io.github.kongweiguang.llmbridge.codec;

import io.github.kongweiguang.llmbridge.core.json.JacksonUtil;
import io.github.kongweiguang.llmbridge.core.json.JsonMerge;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for request-defaults and request-overrides merge behavior.
 */
class RequestDefaultsOverridesTest {

    @Test
    void applyMissing_fillsOnlyMissingFields() {
        ObjectNode target = JacksonUtil.objectNode();
        target.put("model", "gpt-4");
        target.put("temperature", 0.7);

        ObjectNode defaults = JacksonUtil.objectNode();
        defaults.put("temperature", 1.0);  // should NOT override existing
        defaults.put("top_p", 0.9);         // should fill missing
        defaults.put("max_tokens", 4096);   // should fill missing

        JacksonUtil.applyMissing(target, defaults);

        assertThat(target.get("model").asText()).isEqualTo("gpt-4");
        assertThat(target.get("temperature").asDouble()).isEqualTo(0.7); // not overridden
        assertThat(target.get("top_p").asDouble()).isEqualTo(0.9);       // filled
        assertThat(target.get("max_tokens").asInt()).isEqualTo(4096);    // filled
    }

    @Test
    void applyMissing_recursesIntoNestedObjects() {
        ObjectNode target = JacksonUtil.objectNode();
        ObjectNode text = target.putObject("text");
        text.put("format", "json");

        ObjectNode defaults = JacksonUtil.objectNode();
        ObjectNode defaultText = defaults.putObject("text");
        defaultText.put("format", "text");     // should NOT override
        defaultText.put("verbosity", "high");  // should fill

        JacksonUtil.applyMissing(target, defaults);

        assertThat(target.get("text").get("format").asText()).isEqualTo("json"); // preserved
        assertThat(target.get("text").get("verbosity").asText()).isEqualTo("high"); // filled
    }

    @Test
    void deepMerge_overridesExistingFields() {
        ObjectNode base = JacksonUtil.objectNode();
        base.put("model", "gpt-4");
        base.put("temperature", 0.7);
        ObjectNode text = base.putObject("text");
        text.put("verbosity", "medium");

        ObjectNode override = JacksonUtil.objectNode();
        override.put("store", false);
        ObjectNode overrideText = override.putObject("text");
        overrideText.put("verbosity", "high");

        ObjectNode result = JsonMerge.deepMerge(base, override);

        assertThat(result.get("model").asText()).isEqualTo("gpt-4");  // preserved
        assertThat(result.get("temperature").asDouble()).isEqualTo(0.7); // preserved
        assertThat(result.get("store").asBoolean()).isFalse();          // added
        assertThat(result.get("text").get("verbosity").asText()).isEqualTo("high"); // overridden
    }

    @Test
    void applyMissing_handlesNullDefaults() {
        ObjectNode target = JacksonUtil.objectNode();
        target.put("model", "gpt-4");

        // Should not throw
        JacksonUtil.applyMissing(target, null);

        assertThat(target.get("model").asText()).isEqualTo("gpt-4");
    }

    @Test
    void combined_defaultsThenOverrides() {
        ObjectNode target = JacksonUtil.objectNode();
        target.put("model", "gpt-4");

        ObjectNode defaults = JacksonUtil.objectNode();
        defaults.put("temperature", 0.7);
        defaults.put("top_p", 0.9);

        ObjectNode overrides = JacksonUtil.objectNode();
        overrides.put("store", false);
        overrides.put("temperature", 0.3); // override the default

        // Apply defaults first (fill missing)
        JacksonUtil.applyMissing(target, defaults);
        // Then apply overrides (force overwrite)
        ObjectNode result = JsonMerge.deepMerge(target, overrides);

        assertThat(result.get("model").asText()).isEqualTo("gpt-4");
        assertThat(result.get("temperature").asDouble()).isEqualTo(0.3); // overridden
        assertThat(result.get("top_p").asDouble()).isEqualTo(0.9);       // from defaults
        assertThat(result.get("store").asBoolean()).isFalse();            // from overrides
    }
}
