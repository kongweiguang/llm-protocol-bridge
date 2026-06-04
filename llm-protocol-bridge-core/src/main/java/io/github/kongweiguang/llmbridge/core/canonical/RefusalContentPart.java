package io.github.kongweiguang.llmbridge.core.canonical;

import lombok.Data;

/**
 * A refusal content part in a normalized message.
 * Represents a model's refusal to answer a request.
 */
@Data
public class RefusalContentPart implements CanonicalContentPart {

    /** The refusal message. */
    private String refusal;

    public RefusalContentPart(String refusal) {
        this.refusal = refusal;
    }

    @Override
    public String type() {
        return "refusal";
    }
}
