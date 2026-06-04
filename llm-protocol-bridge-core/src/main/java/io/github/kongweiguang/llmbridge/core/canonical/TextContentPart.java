package io.github.kongweiguang.llmbridge.core.canonical;

import lombok.Data;

/**
 * A text content part in a normalized message.
 */
@Data
public class TextContentPart implements CanonicalContentPart {

    private String text;

    public TextContentPart() {
    }

    public TextContentPart(String text) {
        this.text = text;
    }

    @Override
    public String type() {
        return "text";
    }
}
