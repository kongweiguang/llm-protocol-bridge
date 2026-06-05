# AGENTS.md
完成前用中文总结改动文件、验证结果和剩余风险。
未被明确要求使用其他语言时，默认使用简体中文回复。

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

LLM Protocol Bridge — a Spring Boot Starter that proxies requests to multiple upstream LLM providers (OpenAI, Anthropic, etc.) through a unified API. Any client protocol can reach any upstream protocol via a normalized intermediate model, avoiding an N×N codec matrix.

## Build & Test Commands

```bash
# Build entire project
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run all tests
mvn test

# Run a single test class (tests live in llm-protocol-bridge-tests)
mvn test -pl llm-protocol-bridge-tests -Dtest=OpenAiChatCodecTest

# Run a single test method
mvn test -pl llm-protocol-bridge-tests -Dtest=OpenAiChatCodecTest#testMethodName

# Run integration tests
mvn test -pl llm-protocol-bridge-tests -Dtest=LlmBridgeIntegrationTest

# Run the bridge locally
mvn spring-boot:run -pl llm-protocol-bridge-tests
```

## Tech Stack

- Java 17, Spring Boot 3.4.5, Spring WebFlux (reactive), Jackson, JUnit 5, Reactor, MockWebServer
- Maven multi-module project

## Module Structure

| Module | Purpose | Key Packages |
|---|---|---|
| `llm-protocol-bridge-core` | Domain models, codecs, routing, and upstream HTTP client (Spring WebClient). Depends on Spring WebFlux. | `canonical`, `codec`, `config`, `routing`, `stream`, `error`, `json`, `format`, `http` |
| `llm-protocol-bridge-spring-boot-starter` | Auto-configuration, REST controller, auth web filter | `autoconfigure` |
| `llm-protocol-bridge-tests` | Test runtime with example `application.yml` and all unit/integration tests | `test`, `codec`, `integration`, `json`, `route` |

Dependency direction: `core` ← `spring-boot-starter` ← `tests`. Dependency versions are managed directly in the parent POM (no separate BOM module).

## Architecture: Unified Intermediate Model

Every request passes through a two-stage codec pipeline:

```
Client request (any protocol)
  → source ProtocolCodec.normalizeRequest()     → CanonicalRequest
  → ModelResolver selects upstream model
  → target ProtocolCodec.denormalizeRequest()   → upstream JSON
  → UpstreamHttpClient.post()
  → target ProtocolCodec.normalizeResponse()    → CanonicalResponse
  → source ProtocolCodec.denormalizeResponse()  → client JSON
```

Streaming follows the same pattern with `normalizeStream` / `denormalizeStream` operating on `Flux<SseFrame>` ↔ `Flux<CanonicalStreamEvent>`.

### Key Interfaces & Classes

- **`ProtocolCodec`** (`core.codec`) — the central interface. Each implementation handles one API format. Methods: `normalizeRequest`, `denormalizeRequest`, `normalizeResponse`, `denormalizeResponse`, `normalizeStream`, `denormalizeStream`. Carries a `BridgeContext` record (sourceFormat, targetFormat, requestedModel).
- **`ProtocolCodecRegistry`** — maps `ApiProtocol` enum values to codec instances.
- **`CanonicalRequest` / `CanonicalResponse` / `CanonicalMessage`** (`core.canonical`) — the normalized domain model. Unknown fields from source protocol are preserved in `rawExtra` (an `ObjectNode`).
- **`ModelResolver`** (`core.routing`) — resolves requested model name to `ModelConfig`; supports direct lookup and fallback groups with four routing strategies: `failover` (default), `priority`, `weighted` (random by weight), `round-robin` (cycles through candidates).
- **`UpstreamEndpointResolver`** (`core.http`) — resolves full upstream URL from `ModelConfig` (handles `/v1` suffix normalization).
- **`UpstreamHeaderFactory`** (`core.http`) — constructs HTTP headers per provider (Bearer for OpenAI, x-api-key for Anthropic, plus custom headers).
- **`UpstreamHttpClient`** (`core.http`) — interface for sending requests upstream; `WebClientUpstreamHttpClient` is the reactive Spring WebClient implementation.
- **`LlmBridgeService`** (`autoconfigure`) — orchestrates the full proxy flow: extract model → resolve route → codec pipeline → upstream call → fallback on error (non-streaming only).
- **`LlmBridgeController`** — exposes `/v1/chat/completions`, `/v1/responses`, `/v1/messages`, `/v1/models`, `/health`.

### Protocols

Three `ApiProtocol` values with corresponding `ProviderKind` mappings:

| ApiProtocol | ProviderKind | Endpoint |
|---|---|---|
| `OPENAI_CHAT_COMPLETIONS` | `OPENAI_CHAT_COMPATIBLE` | `/v1/chat/completions` |
| `OPENAI_RESPONSES` | `OPENAI_RESPONSES_COMPATIBLE` | `/v1/responses` |
| `ANTHROPIC_MESSAGES` | `ANTHROPIC_MESSAGES_COMPATIBLE` | `/v1/messages` |

Codec implementations: `OpenAiChatCompletionsCodec`, `OpenAiResponsesCodec`, `AnthropicMessagesCodec`.

### Configuration

All config lives under `llm.bridge.*` in `application.yml`. Key structure:
- `llm.bridge.models[]` — each model has `name` (client-facing), `provider` (ProviderKind enum), `base-url`, `api-key`, `model` (actual upstream name), optional `headers`, `body` (deep-merged), `weight` (for weighted routing), `request-defaults` (fill missing fields), `request-overrides` (force overwrite).
- `llm.bridge.fallback.<groupName>` — ordered list of model names to try on failure.
- `llm.bridge.routing-strategy` — `failover` (default), `priority`, `weighted`, `round-robin`.
- `llm.bridge.server.auth.token` — optional Bearer token for client auth.
- `llm.bridge.stream.*` — streaming toggles (heartbeat, include-usage, buffer-tool-arguments, etc.).
- `llm.bridge.compatibility.*` — `preserve-unknown-fields`, `unsupported-media-policy` (downgrade/error), `default-max-output-tokens`, `expose-thinking`, `ignore-invalid-thinking-signature`.

### Streaming

Streaming is fully reactive (Project Reactor). `SseFrameParser` parses upstream SSE into `SseFrame` objects; `SseFrameWriter` serializes them back. `CanonicalStreamEvent` is the normalized stream event. State trackers (`StreamStateTracker`, `ToolCallStateTracker`, `ContentBlockStateTracker`) manage incremental assembly of content blocks and tool calls across stream events. Fallback is not supported once streaming content has been emitted.

### Error Handling

`BridgeException` carries HTTP status, error type, and message. `ErrorMapper` converts to OpenAI-style error JSON. `StreamErrorMapper` emits error as SSE events for streaming responses. `StopReasonMapper` normalizes finish/stop reasons across protocols.

## Important Patterns

- The `core` module depends on Spring (WebFlux / spring-web) because the upstream HTTP client is colocated here. Domain models, codecs, and routing logic remain Spring-framework-agnostic — they only use Jackson, Reactor, and SLF4J directly. The HTTP client classes live under `io.github.kongweiguang.llmbridge.core.http`.
- Codec implementations work on `JsonNode`/`ObjectNode` (Jackson tree model), not POJOs for raw protocol data.
- `rawExtra` on `CanonicalRequest` preserves unknown fields from the source protocol, ensuring round-trip fidelity.
- Content parts: `TextContentPart`, `ImageContentPart`, `FileContentPart`, `AudioContentPart`, `ToolCallContentPart`, `ToolResultContentPart`, `ThinkingContentPart`, `RefusalContentPart`, `UnknownContentPart` — all codecs must handle these in both normalize and denormalize.
- `request-defaults` (fill missing) and `request-overrides` (force overwrite) are applied in `LlmBridgeService` after `denormalizeRequest()` and `body` merge.
- All tests are in the `tests` module, not in individual modules. Tests use AssertJ assertions.
- Integration tests use MockWebServer (OkHttp) to simulate upstream LLM providers.
- Stream tests use `collectList()` + content assertions rather than strict ordering to avoid flakiness.


