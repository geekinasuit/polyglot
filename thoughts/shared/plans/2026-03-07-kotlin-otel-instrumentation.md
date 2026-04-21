# Kotlin OpenTelemetry Instrumentation Plan

## Overview

Add full OpenTelemetry observability (traces, metrics, structured logs) to the Kotlin gRPC
service and client, exporting via OTLP to a SigNoz instance. Includes a
layered configuration system (Hoplite), structured JSON logging (Logback), instance
identification, and environment labeling — all using OTel semantic conventions and W3C
Trace Context propagation.

DI wiring (Dagger) is explicitly out of scope and tracked separately in
`thoughts/shared/tickets/KT-002-kotlin-dagger-grpc-di.md`.

## Current State Analysis

- **Clikt already present in both binaries** — `BracketsService` and `BracketsClient` both
  extend `CliktCommand`. No new CLI library needed; new options are added to the existing
  commands.
- `wrapService()` (`service.kt:34-39`) accepts `vararg interceptors: ServerInterceptor` and
  passes them through to Armeria's `GrpcService.builder().intercept()`. The server interceptor
  hook is fully ready.
- `ManagedChannelBuilder` in `client.kt:36` is built without interceptors — client hook is
  a one-line `.intercept(...)` addition before `.build()`.
- Both binaries use `kotlin-logging` (SLF4J wrapper), with `slf4j-jdk14` as the runtime
  backend. This is replaced with Logback for structured JSON output.
- No configuration system exists — everything is hardcoded constants or minimal Clikt options.
- gRPC-java version in use is **1.71.0**, which includes the stable `io.grpc:grpc-opentelemetry`
  module (introduced in 1.65). No `-alpha`-suffixed library is needed for gRPC tracing.

## Desired End State

After this plan is complete:

1. Both binaries read configuration from a layered system: HOCON config file → environment
   variables → CLI flags.
2. All logging is structured JSON to stdout, and optionally also exported via OTLP to SigNoz.
3. Both binaries emit OTel traces with W3C `traceparent` propagated through gRPC metadata.
4. Both binaries emit OTel metrics (request counts, latency histograms).
5. OTel resources carry `service.name`, `deployment.environment`, and `service.instance.id`
   with smart defaulting for dev vs. container environments.
6. A configured SigNoz instance shows correlated traces across client and server, with logs
   attached to spans via matching `trace_id`. The endpoint is supplied via
   `TELEMETRY_OTLP_ENDPOINT` or `--otlp-endpoint`; no hostname is hardcoded.

### End-to-end verification

```bash
bazel build //kotlin:brackets_service //kotlin:brackets_client

# Start server (SigNoz export enabled)
TELEMETRY_OTLP_ENDPOINT=http://localhost:4317 bazel run //kotlin:brackets_service

# Make a call
echo "(hello [world])" | TELEMETRY_OTLP_ENDPOINT=http://localhost:4317 \
  bazel run //kotlin:brackets_client -- /dev/stdin
```

Expected in SigNoz: three-level span hierarchy, request count metric increments,
structured logs with `trace_id` correlating to the visible trace.

## What We Are NOT Doing

- No DI framework (Dagger) in this plan — see `thoughts/shared/tickets/KT-002-kotlin-dagger-grpc-di.md`.
- No instrumentation of other languages (Go, Java, Python, Rust).
- No distributed context propagation beyond the single client→server hop.
- No OTel sampling configuration — always-on sampling is appropriate at this scale.
- No secrets manager integration — only the extension seam is designed in the config loader.
- No LaunchDarkly / feature flag integration — only the seam.
- No `service.version` attribute — no build version system exists yet.
- No Armeria HTTP client tracing — the client uses grpc-netty-shaded directly.

## Key Discoveries

- `wrapService()` vararg hook (`service.kt:34-39`) is the exact injection point for the
  gRPC server interceptor. Only the call site at `service.kt:26` needs to change.
- `io.grpc:grpc-opentelemetry:1.71.0` (stable) provides `GrpcOpenTelemetry` with both
  `newServerInterceptor()` and `newClientInterceptor()`. W3C TraceContext propagation
  through gRPC metadata is handled automatically.
- `armeria-opentelemetry:1.26.4` provides `OpenTelemetryService.newDecorator()` for
  HTTP/2-layer spans, and the `Server.builder()` API has a `serverListener()` hook for
  lifecycle events (`serverStarted`, `serverStopping`, `serverStopped`).
- `opentelemetry-sdk` is an umbrella artifact covering trace, metrics, and logs SDK
  components — one artifact covers all three signals.
- `opentelemetry-exporter-otlp` covers OTLP gRPC export for all three signals.
- `slf4j-jdk14` is in `runtime_deps` of both `service/BUILD.bazel` and `client/BUILD.bazel`
  — straightforward swap for Logback.
- Hoplite's `PropertySource` interface is the extension point for future secrets manager
  and feature flag sources.

## Trace Hierarchy

The intended span structure for each server-side RPC:

```
[Armeria HTTP/2 span]                       ← armeria-opentelemetry, http.* attributes
  └─ [gRPC: BalanceBrackets/Balance]         ← grpc-opentelemetry interceptor, rpc.* attributes
       └─ [balance-brackets]                 ← manual span, statement + result attributes
```

The client emits a root span; W3C `traceparent` links it to the Armeria span on the server.

## Instance Identification

| Context     | `service.instance.id` value                      |
|-------------|--------------------------------------------------|
| Kubernetes  | `$POD_NAME` (inject via Downward API)            |
| Docker      | `$HOSTNAME` when it matches a container ID regex |
| Dev (local) | `$user@$hostname` — disambiguates local replicas |
| Explicit    | `--instance-id` flag or `SERVICE_INSTANCE_ID` env var |

Detection is attempted in order; first match wins.

---

## Phase 1: Configuration System (Hoplite)

### Overview

Introduce a layered config system using Hoplite with YAML format. The source priority is:
YAML file (lowest) → environment variables → CLI flags (highest). The config loader is
built through a factory function that acts as an extension seam — future callers insert
additional `PropertySource` implementations here (secrets manager, feature flags) without
changing any call sites.

Config data classes use a nested structure with a shared `TelemetryConfig` and
binary-specific root configs (`ServiceAppConfig`, `ClientAppConfig`). The service-specific
and client-specific sections each compute smart defaults for `environment` (checking
standard env vars before falling back to `"development"`) and `instanceId` (checking
Kubernetes, Docker, and local signals in order).

Clikt options in both commands become nullable overrides — a `null` value means "use the
config file / env var value". After loading config from Hoplite, the Clikt layer applies
non-null overrides via data class `copy()`.

### New Maven Artifacts

```
"com.sksamuel.hoplite:hoplite-core:2.9.0",
"com.sksamuel.hoplite:hoplite-yaml:2.9.0",
```

### New Files

- **`kotlin/.../brackets/config/Config.kt`** — data classes: `ServiceAppConfig`,
  `ClientAppConfig`, `ServiceConfig`, `ClientConfig`, `TelemetryConfig`. Top-level functions
  for environment and instance ID defaulting.
- **`kotlin/.../brackets/config/ConfigLoader.kt`** — `buildConfigLoader()` factory function
  composing an environment variable source and a YAML resource file source (optional, so
  a missing file is tolerated). The function's docstring calls out where to insert secrets
  and feature flag sources.
- **`kotlin/.../brackets/config/BUILD.bazel`** — `kt_jvm_library` with Hoplite deps.
- **`kotlin/src/main/resources/service/application.yml`** — YAML defaults for the service.
- **`kotlin/src/main/resources/client/application.yml`** — YAML defaults for the client.
- **`kotlin/src/main/resources/BUILD.bazel`** — `java_library` resource targets for each
  binary, using `resource_strip_prefix = "kotlin/src/main/resources"` so `application.yml`
  lands at the classpath root. The `logback.xml` added in Phase 2 is also included here.

### YAML defaults (both files share this telemetry block)

```yaml
telemetry:
  otlp-endpoint: null
  log-endpoint: null
  metrics-export-interval-seconds: 60
  tracing-enabled: true
  metrics-enabled: true
  logging-export-enabled: false
```

### Changes to Existing Files

- `service.kt` — replace the two hardcoded constants and two Clikt options with nullable
  CLI overrides for all `ServiceConfig` and `TelemetryConfig` fields. Load config at the
  top of `run()` and apply CLI overrides via `copy()`.
- `client.kt` — same pattern with `ClientAppConfig`.
- `service/BUILD.bazel` and `client/BUILD.bazel` — add `config_lib` to `deps` and the
  appropriate resource target to `runtime_deps`.

### Success Criteria

- `bazel build //kotlin:brackets_service //kotlin:brackets_client` passes.
- `--host` / `--port` CLI flags override config file values at runtime.
- Setting `SERVICE_HOST=myhost` env var changes the bind host without a code change.
- Missing `application.conf` is tolerated (Hoplite source marked optional).

**Pause here for human confirmation before proceeding to Phase 2.**

---

## Phase 2: Structured Logging (Logback + JSON)

### Overview

Replace the `slf4j-jdk14` JUL backend with Logback Classic and the Logstash JSON encoder.
All log output becomes structured JSON on stdout — the standard format for log aggregators
and OTel log collectors. The OTel logback appender is added and declared in `logback.xml`
but is a no-op until the OTel SDK is installed in Phase 3; it silently drops events until
`OpenTelemetryAppender.install(sdk)` is called.

The logback config captures `trace_id`, `span_id`, and `trace_flags` from MDC — these are
injected by the OTel logback appender when a span is active, enabling log-to-trace
correlation in SigNoz.

The bare `println("brackets service starting...")` and `println("brackets client running...")`
calls are replaced with `log.info {}` calls. The `println` calls used for user-facing output
(the statement display block with `========` separators) are intentional terminal output
and remain unchanged.

### New Maven Artifacts

```
"ch.qos.logback:logback-classic:1.5.16",
"net.logstash.logback:logstash-logback-encoder:8.0",
"io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.14.0-alpha",
```

Note: the `-alpha` suffix on the logback appender artifact reflects API-stability status,
not production readiness. This artifact is widely used in production JVM services.

Remove from `maven.install`:
```
"org.slf4j:slf4j-jdk14:2.0.11",   ← replaced by logback-classic; keep slf4j-api
```

### New Files

- **`kotlin/src/main/resources/logback.xml`** — configures two appenders:
  - `LogstashConsoleAppender` with `LogstashEncoder` (JSON stdout, always active).
  - `OpenTelemetryAppender` (no-op until SDK is installed; captures MDC keys `trace_id`,
    `span_id`, `trace_flags`; `captureExperimentalAttributes` and `captureCodeAttributes`
    enabled). Both appenders are attached to the root logger at INFO level.

### Changes to Existing Files

- `service/BUILD.bazel` and `client/BUILD.bazel` — replace `@maven//:org_slf4j_slf4j_jdk14`
  in `runtime_deps` with `logback-classic`, `logstash-logback-encoder`, and the OTel logback
  appender artifact.
- `src/main/resources/BUILD.bazel` (from Phase 1) — add `logback.xml` to both resource
  targets so it lands at the classpath root for both binaries.
- `service.kt` — replace `println("brackets service starting...")` with `log.info {}`.
- `client.kt` — replace `println("brackets client running...")` with `log.info {}`.

### Success Criteria

- Log output is valid JSON with `@timestamp`, `level`, `logger_name`, `message` fields.
- No JUL-formatted lines in stdout.
- `bazel build //kotlin:brackets_service //kotlin:brackets_client` passes.

**Pause here for human confirmation before proceeding to Phase 3.**

---

## Phase 3: OpenTelemetry SDK Setup

### Overview

Add the OTel SDK and create a shared `TelemetrySetup.kt` that initialises all three signals
(traces, metrics, logs) and registers the SDK globally. Resource attributes are set from
the config: `service.name` is passed as a parameter; `deployment.environment` and
`service.instance.id` come from the resolved config. OTLP export is conditional — if no
endpoint is configured, each provider uses a no-op exporter so in-process instrumentation
still functions without an external collector.

After SDK registration, `OpenTelemetryAppender.install(sdk)` is called to activate the
logback appender declared in Phase 2. From this point on, log lines emitted while a span is
active will carry `trace_id` and `span_id` in both the JSON console output (via MDC) and in
OTLP log export (if enabled).

The metrics export interval is read from `TelemetryConfig.metricsExportIntervalSeconds`
rather than hardcoded, so it is tunable per environment via config or env var.

`TelemetryConfig.logEndpoint` falls back to `otlpEndpoint` if not explicitly set — an
extension property (`effectiveLogEndpoint`) encapsulates this logic.

`initTelemetry()` returns the `GrpcOpenTelemetry` handle so callers can obtain gRPC
interceptors from it directly. Future DI refactoring (see the Dagger ticket) will provide
this as an injected type instead.

### New Maven Artifacts

```
"io.opentelemetry:opentelemetry-api:1.47.0",
"io.opentelemetry:opentelemetry-sdk:1.47.0",           # umbrella: trace + metrics + logs SDKs
"io.opentelemetry:opentelemetry-exporter-otlp:1.47.0", # covers all three signals via OTLP gRPC
"io.grpc:grpc-opentelemetry:1.71.0",                   # stable; matches existing grpc-java version
```

### New Files

- **`kotlin/.../brackets/telemetry/TelemetrySetup.kt`** — `initTelemetry(serviceName,
  environment, instanceId, config)` function. Constructs and globally registers an
  `OpenTelemetrySdk` with:
  - `SdkTracerProvider` with optional `OtlpGrpcSpanExporter` + `BatchSpanProcessor`.
  - `SdkMeterProvider` with optional `OtlpGrpcMetricExporter` + `PeriodicMetricReader`
    (interval from config).
  - `SdkLoggerProvider` with optional `OtlpGrpcLogRecordExporter` + `BatchLogRecordProcessor`
    (conditional on `loggingExportEnabled` and `effectiveLogEndpoint`).
  - W3C Trace Context propagation (`W3CTraceContextPropagator`).
  - Resource attributes: `service.name`, `deployment.environment`, `service.instance.id`.
  - Calls `OpenTelemetryAppender.install(sdk)` after registration.
  - Returns `GrpcOpenTelemetry` built from the registered global SDK.
- **`kotlin/.../brackets/telemetry/BUILD.bazel`** — `kt_jvm_library` with OTel and config
  deps.

### Changes to Existing Files

- `service/BUILD.bazel` and `client/BUILD.bazel` — add `telemetry_lib` to `deps`.

### Success Criteria

- `bazel build //kotlin/src/main/kotlin/.../telemetry:telemetry_lib` passes.
- Both binaries start without errors when no OTLP endpoint is configured.
- A structured log line confirming SDK initialisation appears at startup, including the
  resolved otlpEndpoint value (or `(disabled)` if null).

**Pause here for human confirmation before proceeding to Phase 4.**

---

## Phase 4: Server Instrumentation (Traces + Metrics)

### Overview

Wire the OTel gRPC server interceptor and the Armeria HTTP/2 OTel decorator into the server,
add a `ServerListener` for lifecycle logging, add a manual child span inside
`BalanceServiceEndpoint`, and add OTel metrics instruments for request counting and latency.

### Trace layering

`OpenTelemetryService.newDecorator(otel)` wraps the `GrpcService` at the Armeria HTTP/2
layer, producing a span with `http.*` semantic attributes. The gRPC server interceptor
(from `grpcOtel.newServerInterceptor()`) produces a child span with `rpc.*` attributes.
The manual `balance-brackets` span is a child of the gRPC span. Together these form the
three-level hierarchy described in the overview.

### New Maven Artifact

```
"com.linecorp.armeria:armeria-opentelemetry:1.26.4",
```

### Changes to `service.kt`

- Call `initTelemetry()` early in `run()` using the resolved config, passing
  `"brackets-service"` as the service name. Log all resolved config values at this point
  (host, port, environment, instanceId, otlpEndpoint).
- Pass `grpcOtel.newServerInterceptor()` into the existing `wrapService()` call.
- Apply `OpenTelemetryService.newDecorator(otel)` as a service decorator on the
  `Server.builder()`, layered above the `GrpcService`.
- Add a `ServerListenerAdapter` to the server builder with:
  - `serverStarted()` — log confirmed active ports; end the startup OTel span here.
  - `serverStopping()` — log graceful drain start.
  - `serverStopped()` — log shutdown confirmed.
- Wrap the server construction and startup in a manual `server.startup` OTel span. The span
  starts before `Server.builder()` and ends inside `serverStarted()` so it measures actual
  Armeria initialization time including port binding. If startup throws before
  `serverStarted()` fires, the span must be ended in a catch block.
- Remove the existing `server.closeOnJvmShutdown().thenRun { log.info { ... } }` pattern;
  shutdown logging moves into the `ServerListener`.

### Changes to `service/BUILD.bazel`

Add `config_lib`, `telemetry_lib`, `armeria-opentelemetry`, and `opentelemetry-api` to
`deps`. Add resource target from Phase 1 to `runtime_deps`.

### Changes to `BalanceServiceEndpoint.kt`

`BalanceServiceEndpoint` gains `Tracer` and `Meter` fields obtained from `GlobalOpenTelemetry`
at construction time (a TODO comment notes these should become `@Inject` constructor
parameters when the Dagger ticket is implemented).

For each `balance()` call, the endpoint:
- Creates a child span named `balance-brackets`, setting `statement.length` and
  `statement.text` as span attributes before starting the algorithm.
- Runs `balancedBrackets()` inside `span.makeCurrent().use { ... }` so that log lines
  emitted from the algorithm carry the span's trace context in MDC.
- On completion, sets a `result` span attribute: `"balanced"`, `"not_balanced"`, or `"error"`.
  On unexpected exceptions, also calls `span.setStatus(StatusCode.ERROR, message)`.
- Always ends the span in a `finally` block.
- Increments a `brackets.server.request.count` counter with a `result` dimension label.
- Records a `brackets.server.request.duration.ms` histogram observation.

### Success Criteria

- `bazel build //kotlin:brackets_service` passes.
- Startup log contains all config values in JSON format.
- `serverStarted` log line shows confirmed active ports.
- After a client call, SigNoz Traces shows the three-level span hierarchy.
- Span attributes `statement.length`, `statement.text`, and `result` are visible.
- `brackets.server.request.count` metric appears in SigNoz with a `result` label.

**Pause here for human confirmation before proceeding to Phase 5.**

---

## Phase 5: Client Instrumentation (Traces + Metrics)

### Overview

Wire the OTel gRPC client interceptor into `ManagedChannelBuilder` and add startup logging
and a request counter metric. The client interceptor automatically injects the W3C
`traceparent` header into outgoing gRPC metadata, making the client's outbound span the
parent of the Armeria HTTP/2 span on the server. No changes to the gRPC stub or request
construction are needed — context propagation is entirely handled by the interceptor.

### Changes to `client.kt`

- Call `initTelemetry()` early in `run()` using the resolved config, passing
  `"brackets-client"` as the service name. Log all resolved config values at this point.
- Add `grpcOtel.newClientInterceptor()` to `ManagedChannelBuilder` via `.intercept(...)`
  before `.build()`.
- Log the statement length before sending the request.
- Replace `log.info { response }` with a structured log of the individual response fields.
- Increment a `brackets.client.call.count` counter after the call completes (or on
  connection error), with a `result` label using the same value set as the server counter.

### Changes to `client/BUILD.bazel`

Add `config_lib`, `telemetry_lib`, and `opentelemetry-api` to `deps`. Add resource target
from Phase 1 to `runtime_deps`.

### Success Criteria

- `bazel build //kotlin:brackets_client` passes.
- In SigNoz Traces, a single trace shows the client span as root with Armeria and gRPC
  spans on the server side as descendants — all sharing the same trace ID.
- `brackets.client.call.count` metric appears in SigNoz with a `result` label.
- Startup log shows target host, port, environment, instanceId in JSON format.

**Pause here for human confirmation before proceeding to Phase 6.**

---

## Phase 6: OpenTelemetry Log Export Verification

### Overview

No new code. The OTel logback appender was declared in `logback.xml` (Phase 2) and activated
by `OpenTelemetryAppender.install(sdk)` in `TelemetrySetup.kt` (Phase 3). This phase
validates end-to-end log export and confirms log-to-trace correlation in SigNoz.

Log export is disabled by default (`logging-export-enabled = false`). To enable it, set the
flag in `application.conf` or via the `TELEMETRY_LOGGING_EXPORT_ENABLED` env var. The log
OTLP endpoint defaults to `otlpEndpoint` if `log-endpoint` is not set explicitly.

### Verification

- Start the server with an OTLP endpoint and logging export enabled.
- Make a client call.
- In SigNoz Logs: structured log entries appear with a `trace_id` matching spans visible in
  SigNoz Traces.
- Clicking a log entry in SigNoz navigates to the associated trace span.
- JSON console logs on stdout include `trace_id` and `span_id` in the JSON envelope for log
  lines emitted while a span is active.

---

## Testing Strategy

Automated testing for telemetry uses two tiers. Manual SigNoz verification (Phase 6) is a
third, separate concern and is not a substitute for the automated tiers.

### Tier 1: Unit tests with in-memory exporters

`io.opentelemetry:opentelemetry-sdk-testing` provides `InMemorySpanExporter`,
`InMemoryMetricReader`, and `InMemoryLogRecordExporter`. These are wired into the OTel SDK
in place of the OTLP exporters during tests, making assertions fully in-process with no
network or Docker requirement.

Key assertions to cover in unit tests (in the existing `BracketsTest` target or a new
`TelemetryTest` target under `kotlin/src/test/kotlin/`):

- `BalanceServiceEndpoint.balance()` emits a span named `balance-brackets`.
- The span carries `statement.length`, `statement.text`, and `result` attributes.
- `result` is `"balanced"` for valid balanced input, `"not_balanced"` for imbalanced input,
  and `"error"` for unexpected exceptions.
- On unexpected exceptions, span status is `ERROR`.
- `brackets.server.request.count` counter increments with the correct `result` label.

New maven artifact (test-only):
```
"io.opentelemetry:opentelemetry-sdk-testing:1.47.0",  # testonly
```

### Tier 2: Integration test with OTel Collector via Testcontainers

For end-to-end validation of the full OTLP pipeline without a real SigNoz, use
**Testcontainers** to start an `otel/opentelemetry-collector` container per test run.
The collector is configured with an `otlp` receiver and a `file` exporter writing OTLP
JSON to a temp directory, which the test can then assert against.

The test starts the gRPC service with the collector's exposed port as the OTLP endpoint,
runs a client call, waits briefly for the batch export flush, and asserts that the output
file contains spans with the expected structure.

This test should be tagged `requires-docker` and `local` in its Bazel rule to opt out of
the sandbox (Bazel sandboxing blocks Docker socket access). It runs in CI as a separate
step, not as part of `bazel test //...`.

New maven artifacts (test-only):
```
"org.testcontainers:testcontainers:1.20.4",  # testonly
"org.testcontainers:junit-4:1.20.4",         # testonly
```

A collector config YAML file should be added under `kotlin/src/test/resources/` with an
`otlp` receiver on `0.0.0.0:4317` and a `file` exporter. The Bazel test target includes
this file as a resource.

### Out of scope: cross-language e2e matrix

A scheduled test running all combinations of `(language X client) → (language Y service)`
is tracked separately in `thoughts/shared/tickets/CI-002-cross-language-e2e-matrix.md`. It is
intentionally excluded from CI (too expensive per-PR) and will run on a schedule once
multiple languages have full gRPC client/server implementations.

---

## Configuration Reference

| Config key | Env var | CLI flag | Default |
|---|---|---|---|
| `service.host` | `SERVICE_HOST` | `--host` | `localhost` |
| `service.port` | `SERVICE_PORT` | `--port` | `8888` |
| `service.environment` | `DEPLOYMENT_ENV` / `ENVIRONMENT` | `--environment` | `development` |
| `service.instance-id` | `SERVICE_INSTANCE_ID` / `POD_NAME` | `--instance-id` | `user@hostname` |
| `telemetry.otlp-endpoint` | `TELEMETRY_OTLP_ENDPOINT` | `--otlp-endpoint` | `null` |
| `telemetry.log-endpoint` | `TELEMETRY_LOG_ENDPOINT` | `--log-endpoint` | `null` (falls back to `otlp-endpoint`) |
| `telemetry.metrics-export-interval-seconds` | `TELEMETRY_METRICS_EXPORT_INTERVAL_SECONDS` | _(config only)_ | `60` |
| `telemetry.logging-export-enabled` | `TELEMETRY_LOGGING_EXPORT_ENABLED` | _(config only)_ | `false` |

## Maven Artifacts Summary

**Add to `MODULE.bazel`:**
```
# Configuration
"com.sksamuel.hoplite:hoplite-core:2.9.0",
"com.sksamuel.hoplite:hoplite-yaml:2.9.0",

# Structured logging (opentelemetry-logback-appender-1.0 carries -alpha API-stability marker)
"ch.qos.logback:logback-classic:1.5.16",
"net.logstash.logback:logstash-logback-encoder:8.0",
"io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.14.0-alpha",

# OpenTelemetry SDK (opentelemetry-sdk covers trace + metrics + logs)
"io.opentelemetry:opentelemetry-api:1.47.0",
"io.opentelemetry:opentelemetry-sdk:1.47.0",
"io.opentelemetry:opentelemetry-exporter-otlp:1.47.0",

# gRPC OTel integration — stable; version matches existing grpc-java 1.71.0
"io.grpc:grpc-opentelemetry:1.71.0",

# Armeria OTel integration — HTTP/2-layer spans and lifecycle listener hooks
"com.linecorp.armeria:armeria-opentelemetry:1.26.4",
```

**Remove from `MODULE.bazel`:**
```
"org.slf4j:slf4j-jdk14:2.0.11",   ← replaced by logback-classic; slf4j-api stays
```

## New File Summary

| File | Purpose |
|------|---------|
| `kotlin/.../brackets/config/Config.kt` | Config data classes; env and instance ID defaulting logic |
| `kotlin/.../brackets/config/ConfigLoader.kt` | Hoplite loader factory (secrets/feature-flag seam) |
| `kotlin/.../brackets/config/BUILD.bazel` | `config_lib` |
| `kotlin/.../brackets/telemetry/TelemetrySetup.kt` | OTel SDK init, all three signals, OTLP exporters |
| `kotlin/.../brackets/telemetry/BUILD.bazel` | `telemetry_lib` |
| `kotlin/src/main/resources/logback.xml` | JSON console appender + OTel logback appender |
| `kotlin/src/main/resources/service/application.yml` | Service YAML defaults |
| `kotlin/src/main/resources/client/application.yml` | Client YAML defaults |
| `kotlin/src/main/resources/BUILD.bazel` | Resource targets with `resource_strip_prefix` |

## References

- Ticket (DI follow-up): `thoughts/shared/tickets/KT-002-kotlin-dagger-grpc-di.md`
- Ticket (interceptors): `thoughts/shared/tickets/KT-001-kotlin-grpc-interceptors-not-implemented.md`
- Codebase overview: `thoughts/shared/research/2026-03-05-polyglot-codebase-overview.compressed.md`
- OTel gRPC Java: https://grpc.io/docs/languages/java/opentelemetry/
- W3C Trace Context: https://www.w3.org/TR/trace-context/
- Hoplite: https://github.com/sksamuel/hoplite