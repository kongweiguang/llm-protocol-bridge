package io.github.kongweiguang.llmbridge.core.routing;

import io.github.kongweiguang.llmbridge.core.config.ModelConfig;
import lombok.Data;

import java.util.List;

/**
 * Result of model routing. Contains the primary model config and optional fallback candidates.
 */
@Data
public class ModelResolutionResult {

    /** The model name requested by the client. */
    private String requestedModel;

    /** The primary model configuration to use. */
    private ModelConfig modelConfig;

    /** Ordered list of fallback candidates (including the primary). */
    private List<ModelConfig> fallbackCandidates;

    public ModelResolutionResult(String requestedModel, ModelConfig modelConfig) {
        this.requestedModel = requestedModel;
        this.modelConfig = modelConfig;
        this.fallbackCandidates = List.of(modelConfig);
    }

    public ModelResolutionResult(String requestedModel, ModelConfig modelConfig, List<ModelConfig> fallbackCandidates) {
        this.requestedModel = requestedModel;
        this.modelConfig = modelConfig;
        this.fallbackCandidates = fallbackCandidates;
    }
}
