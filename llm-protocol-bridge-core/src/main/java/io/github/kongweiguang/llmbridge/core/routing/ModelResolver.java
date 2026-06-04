package io.github.kongweiguang.llmbridge.core.routing;

import io.github.kongweiguang.llmbridge.core.config.*;
import io.github.kongweiguang.llmbridge.core.error.BridgeException;
import io.github.kongweiguang.llmbridge.core.format.ApiProtocol;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes requested model names to their resolved configuration.
 */
@Slf4j
public class ModelResolver {

    private final Map<String, ModelAliasDefinition> modelAliases;
    private final Map<String, RouteDefinition> routes;
    private final Map<String, ProviderDefinition> providers;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    public ModelResolver(LlmBridgeProperties properties) {
        this.modelAliases = properties.getModelAliases() != null
                ? properties.getModelAliases() : new LinkedHashMap<>();
        this.routes = properties.getRoutes() != null
                ? properties.getRoutes() : new LinkedHashMap<>();
        this.providers = properties.getProviders() != null
                ? properties.getProviders() : new LinkedHashMap<>();
    }

    /**
     * Resolves a requested model name to a route result.
     */
    public ModelResolutionResult resolve(String requestedModel) {
        if (requestedModel == null || requestedModel.isEmpty()) {
            throw new BridgeException(400, "invalid_request_error", "model is required");
        }

        // 1. Direct model-alias match
        ModelAliasDefinition alias = modelAliases.get(requestedModel);
        if (alias != null) {
            ModelConfig config = resolveAlias(requestedModel, alias);
            log.info("model resolved: requested={}, type=alias, provider={}, upstreamModel={}",
                    requestedModel, config.getProvider(), config.getModel());
            return new ModelResolutionResult(requestedModel, config);
        }

        // 2. Route match
        RouteDefinition route = routes.get(requestedModel);
        if (route != null && route.getCandidates() != null && !route.getCandidates().isEmpty()) {
            List<ModelConfig> candidates = new ArrayList<>();
            for (RouteCandidateDefinition candidate : route.getCandidates()) {
                ModelAliasDefinition candidateAlias = modelAliases.get(candidate.getModelRef());
                if (candidateAlias == null) {
                    throw new BridgeException(500, "config_error",
                            "Route '" + requestedModel + "' references unknown model-alias: "
                                    + candidate.getModelRef());
                }
                candidates.add(resolveAlias(candidate.getModelRef(), candidateAlias));
            }

            RoutingStrategy strategy = RoutingStrategy.parse(route.getStrategy());
            List<ModelConfig> ordered = applyRoutingStrategy(strategy, candidates);
            log.info("model resolved: requested={}, type=route, strategy={}, candidate={}, candidates={}",
                    requestedModel, strategy, ordered.get(0).getName(), candidates.size());
            return new ModelResolutionResult(requestedModel, ordered.get(0), ordered);
        }

        log.warn("model not found: requested={}, available={}", requestedModel, getAllModelNames());
        throw new BridgeException(404, "model_not_found",
                "Model '" + requestedModel + "' not found. Available models: " + getAllModelNames());
    }

    /**
     * Resolves a model-alias definition into a concrete ModelConfig
     * by merging provider settings with alias-specific overrides.
     */
    private ModelConfig resolveAlias(String aliasName, ModelAliasDefinition alias) {
        ProviderDefinition provider = providers.get(alias.getProviderRef());
        if (provider == null) {
            throw new BridgeException(500, "config_error",
                    "Model-alias '" + aliasName + "' references unknown provider: " + alias.getProviderRef());
        }

        ModelConfig mc = new ModelConfig();
        mc.setName(aliasName);
        mc.setProviderKind(provider.getKind());
        if (provider.getEndpoint() != null) {
            mc.setBaseUrl(provider.getEndpoint().getBaseUrl());
        }
        if (provider.getAuthentication() != null) {
            mc.setApiKey(provider.getAuthentication().getToken());
        }
        mc.setModel(alias.getUpstreamModel());
        if (provider.getDefaultHeaders() != null) {
            mc.setHeaders(provider.getDefaultHeaders());
        }
        if (alias.getRequestDefaults() != null) {
            mc.setRequestDefaults(alias.getRequestDefaults());
        } else if (provider.getRequestDefaults() != null) {
            mc.setRequestDefaults(provider.getRequestDefaults());
        }
        if (alias.getRequestOverrides() != null) {
            mc.setRequestOverrides(alias.getRequestOverrides());
        } else if (provider.getRequestOverrides() != null) {
            mc.setRequestOverrides(provider.getRequestOverrides());
        }
        return mc;
    }

    private List<ModelConfig> applyRoutingStrategy(RoutingStrategy strategy, List<ModelConfig> candidates) {
        return switch (strategy) {
            case FAILOVER, PRIORITY -> candidates;
            case WEIGHTED -> applyWeightedStrategy(candidates);
            case ROUND_ROBIN -> applyRoundRobinStrategy(candidates);
        };
    }

    private List<ModelConfig> applyWeightedStrategy(List<ModelConfig> candidates) {
        if (candidates.size() <= 1) return candidates;

        int totalWeight = 0;
        for (ModelConfig c : candidates) {
            totalWeight += Math.max(1, c.getWeight());
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        ModelConfig selected = candidates.get(0);
        for (ModelConfig c : candidates) {
            cumulative += Math.max(1, c.getWeight());
            if (random < cumulative) {
                selected = c;
                break;
            }
        }

        final ModelConfig primary = selected;
        List<ModelConfig> ordered = new ArrayList<>();
        ordered.add(primary);
        candidates.stream()
                .filter(c -> c != primary)
                .sorted((a, b) -> Integer.compare(b.getWeight(), a.getWeight()))
                .forEach(ordered::add);
        return ordered;
    }

    private List<ModelConfig> applyRoundRobinStrategy(List<ModelConfig> candidates) {
        if (candidates.size() <= 1) return candidates;

        int index = roundRobinIndex.getAndIncrement() % candidates.size();
        if (index < 0) index += candidates.size();

        List<ModelConfig> ordered = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            ordered.add(candidates.get((index + i) % candidates.size()));
        }
        return ordered;
    }

    /**
     * Returns all available model names (aliases + route names).
     */
    public Set<String> getAllModelNames() {
        Set<String> names = new LinkedHashSet<>(modelAliases.keySet());
        names.addAll(routes.keySet());
        return names;
    }
}
