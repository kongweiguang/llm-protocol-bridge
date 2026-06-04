package io.github.kongweiguang.llmbridge.json;

import io.github.kongweiguang.llmbridge.core.json.JsonMerge;
import io.github.kongweiguang.llmbridge.core.json.JacksonUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonMerge}.
 */
class JsonMergeTest {

    @Test
    void deepMerge_scalarOverride() {
        ObjectNode base = JacksonUtil.objectNode();
        base.put("model", "gpt-4");
        base.put("temperature", 0.7);

        ObjectNode override = JacksonUtil.objectNode();
        override.put("temperature", 0.9);

        ObjectNode result = JsonMerge.deepMerge(base, override);

        assertThat(result.get("model").asText()).isEqualTo("gpt-4");
        assertThat(result.get("temperature").asDouble()).isEqualTo(0.9);
    }

    @Test
    void deepMerge_addNewField() {
        ObjectNode base = JacksonUtil.objectNode();
        base.put("model", "gpt-4");

        ObjectNode override = JacksonUtil.objectNode();
        override.put("store", false);

        ObjectNode result = JsonMerge.deepMerge(base, override);

        assertThat(result.get("model").asText()).isEqualTo("gpt-4");
        assertThat(result.get("store").asBoolean()).isFalse();
    }

    @Test
    void deepMerge_objectRecursion() {
        ObjectNode base = JacksonUtil.objectNode();
        base.put("model", "gpt-4");
        ObjectNode textNode = base.putObject("text");
        textNode.put("verbosity", "medium");
        textNode.put("style", "formal");

        ObjectNode override = JacksonUtil.objectNode();
        ObjectNode overrideText = override.putObject("text");
        overrideText.put("verbosity", "high");

        ObjectNode result = JsonMerge.deepMerge(base, override);

        assertThat(result.get("text").get("verbosity").asText()).isEqualTo("high");
        assertThat(result.get("text").get("style").asText()).isEqualTo("formal");
    }

    @Test
    void deepMerge_arrayOverride() {
        ObjectNode base = JacksonUtil.objectNode();
        var stopArr = base.putArray("stop");
        stopArr.add("stop1");
        stopArr.add("stop2");

        ObjectNode override = JacksonUtil.objectNode();
        var overrideArr = override.putArray("stop");
        overrideArr.add("new_stop");

        ObjectNode result = JsonMerge.deepMerge(base, override);

        assertThat(result.get("stop")).hasSize(1);
        assertThat(result.get("stop").get(0).asText()).isEqualTo("new_stop");
    }

    @Test
    void deepMerge_nullOverride() {
        ObjectNode base = JacksonUtil.objectNode();
        base.put("model", "gpt-4");
        base.put("temperature", 0.7);

        ObjectNode override = JacksonUtil.objectNode();
        override.putNull("temperature");

        ObjectNode result = JsonMerge.deepMerge(base, override);

        assertThat(result.get("model").asText()).isEqualTo("gpt-4");
        assertThat(result.get("temperature").isNull()).isTrue();
    }

    @Test
    void deepMerge_nullOverrideReturnsBase() {
        ObjectNode base = JacksonUtil.objectNode();
        base.put("model", "gpt-4");

        ObjectNode result = JsonMerge.deepMerge(base, null);

        assertThat(result.get("model").asText()).isEqualTo("gpt-4");
    }

    @Test
    void deepMerge_exampleFromSpec() {
        // From the plan: base is codec output, override is YAML body
        ObjectNode base = JacksonUtil.objectNode();
        base.put("model", "gpt-5.5");
        base.put("temperature", 0.7);
        ObjectNode textNode = base.putObject("text");
        textNode.put("verbosity", "medium");

        ObjectNode override = JacksonUtil.objectNode();
        override.put("store", false);
        ObjectNode overrideText = override.putObject("text");
        overrideText.put("verbosity", "high");

        ObjectNode result = JsonMerge.deepMerge(base, override);

        assertThat(result.get("model").asText()).isEqualTo("gpt-5.5");
        assertThat(result.get("temperature").asDouble()).isEqualTo(0.7);
        assertThat(result.get("store").asBoolean()).isFalse();
        assertThat(result.get("text").get("verbosity").asText()).isEqualTo("high");
    }
}
