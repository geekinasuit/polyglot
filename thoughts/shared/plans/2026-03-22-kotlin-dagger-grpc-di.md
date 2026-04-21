# Kotlin Dagger DI via dagger-grpc Implementation Plan

## Overview

Introduce Dagger 2 as the compile-time DI framework for the Kotlin `brackets` service, using
`geekinasuit/dagger-grpc` for the Armeria/gRPC binding. This replaces the manual object
construction in `BracketsService.run()` with a generated component graph, adds injectable
interceptors via Dagger multibindings, and annotates `BalanceServiceEndpoint` with
`@Inject` constructor + `@GrpcServiceHandler` so the KSP processor generates its adapter class.

Client DI is explicitly deferred (see ticket `KT-003-kotlin-dagger-client-di.md`).

## Current State Analysis

- **`BracketsService.run()`** manually constructs: `Resource`, `BalanceServiceEndpoint`, Armeria `Server`. It also calls `initTelemetry(resource, config.telemetry)` for its side effect of registering the OTel SDK as the `GlobalOpenTelemetry` singleton — the returned `GrpcOpenTelemetry` is discarded (the service never captures it, unlike the client).
- **`BalanceServiceEndpoint`** has a `// TODO: make @Inject constructor params` comment; uses `GlobalOpenTelemetry.get()` for both `Tracer` and `Meter`.
- **`BalanceServiceEndpoint`** already implements `BalanceBracketsGrpc.AsyncService` and `BindableService` directly — the `BindableService` impl will move to the generated adapter.
- **Interceptors**: `wrapService()` takes `vararg interceptors` but none are wired in; the hook has always been empty.
- **No kapt/KSP** is configured in the build today.

## Desired End State

The Kotlin service binary is assembled by a Dagger `ApplicationGraph` component. All construction
is in `@Module` objects. `BracketsService.run()` only: resolves CLI config, constructs the graph
via `ApplicationGraph.builder().config(config).build()`, then calls `graph.server().start().join()`.

`BalanceServiceEndpoint` has an `@Inject` constructor taking `OpenTelemetry`. A KSP-generated
`BalanceServiceEndpointAdapter` wraps it and is contributed to `Set<BindableService>` via
`GrpcHandlersModule`. Interceptors are declared per-module via `@IntoSet` and passed through
`wrapService()` at `ServerModule`.

The `GrpcHandlersModule` and `GrpcCallScopeGraph` are hand-written in this plan (both are marked
`@Generated("to be generated")` mirroring the convention in the dagger-grpc library). When
`dagger-grpc`'s `module_generator.kt` is completed, those files will be replaced by generated output.

### Key Discoveries

- `geekinasuit/dagger-grpc` is Armeria-native — `wrapService()` returns `GrpcService` (armeria-grpc). No grpc-netty on the server side. Perfect match for this project.
- KSP (not kapt) — dagger-grpc uses `DaggerGrpcSymbolProcessor` via `SymbolProcessorProvider`. The Dagger compiler also runs as a KSP plugin (not a separate annotation processor).
- `@GrpcServiceHandler(BalanceBracketsGrpc::class)` validates that `BalanceBracketsGrpc` has an inner `AsyncService` interface — it does.
- `GrpcCallScopeGraph.Supplier` is a `@Subcomponent.Factory` interface; `ApplicationGraph` extends it, making the root component self-sufficient as the call-scope factory. `ApplicationGraphModule` provides `applicationGraph: ApplicationGraph` back as `GrpcCallScopeGraph.Supplier` to satisfy that binding.
- `Set<@JvmSuppressWildcards BindableService>` — the `@JvmSuppressWildcards` annotation is required on Kotlin multibinding injection points.
- dagger-grpc is not on Maven Central; it must be referenced via `git_override` in `MODULE.bazel`.

## What We're NOT Doing

- Client DI (separate ticket)
- Per-call telemetry injection (OTel bindings live at `@ApplicationScope`; the `GrpcCallContext` pair binding is added for completeness but not used in this endpoint)
- Implementing `module_generator.kt` in dagger-grpc (separate effort in that project)
- Removing `GlobalOpenTelemetry` registration — `initTelemetry()` still sets it as a side effect; `TelemetryModule` captures the return value and exposes `OpenTelemetry` from `GlobalOpenTelemetry.get()` after init
- Kubernetes / Docker changes (separate ticket)

---

## Implementation Approach

Five phases, each independently buildable and testable:

1. **Build wiring** — add dagger-grpc dep, Maven artifacts, KSP plugins to BUILD files
2. **Annotate `BalanceServiceEndpoint`** — `@GrpcServiceHandler`, `@GrpcCallScope`, `@Inject constructor`
3. **Write Dagger modules** — Config, Telemetry, Server, GrpcCallScope, GrpcHandlers, Interceptors
4. **Write `ApplicationGraph` component; migrate `run()`** — remove manual construction from `BracketsService.run()`
5. **Test module** — `FakeTelemetryModule` for unit tests

---

## Phase 1: Build Wiring

### Overview

Add `dagger-grpc` as a bzlmod dependency, add Dagger + javax.inject Maven artifacts, register
the two KSP plugins (dagger-grpc adapter generator + Dagger component generator) in the service
library's `BUILD.bazel`.

### Changes Required

#### 1. `MODULE.bazel` — add dagger-grpc bzlmod dep

Add after the `grpc_kotlin` dep:

```starlark
bazel_dep(name = "dagger-grpc", version = "0.1")
git_override(
    module_name = "dagger-grpc",
    remote = "https://github.com/geekinasuit/dagger-grpc.git",
    commit = "<pin to HEAD commit at time of implementation>",
)
```

#### 2. `MODULE.bazel` — add Maven artifacts

In `maven.install(artifacts = [...])`, add:

```starlark
"com.google.dagger:dagger:2.55",
"javax.inject:javax.inject:1",
```

Note: `dagger-compiler` runs as a KSP plugin sourced from `@dagger-grpc//third_party/dagger:dagger-compiler` — no Maven dep needed for the compiler.

#### 3. `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BUILD.bazel`

Add `plugins` and additional `deps` to `service_lib`:

```starlark
kt_jvm_library(
    name = "service_lib",
    srcs = glob(["*.kt", "dagger/*.kt"]),
    plugins = [
        "@dagger-grpc//io_grpc/compiler/ksp:plugin",       # generates *Adapter.kt
        "@dagger-grpc//third_party/dagger:dagger-compiler", # generates DaggerApplicationGraph
    ],
    visibility = ["//visibility:public"],
    deps = [
        # existing deps ...
        "@dagger-grpc//api",
        "@dagger-grpc//util/armeria",
        "@maven//:com_google_dagger_dagger",
        "@maven//:javax_inject_javax_inject",
    ],
    # runtime_deps unchanged
)
```

### Success Criteria

#### Automated Verification
- [ ] `bazel build //kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service:service_lib` completes without error
- [ ] The KSP output directory (under `bazel-bin`) contains `BalanceServiceEndpointAdapter.kt` once Phase 2 is done
- [ ] `bazel query @dagger-grpc//...` lists targets (confirms the override resolves)

#### Manual Verification
- [ ] No unexpected transitive dependency conflicts in `bazel deps //kotlin/...`

---

## Phase 2: Annotate `BalanceServiceEndpoint`

### Overview

Add `@GrpcServiceHandler`, `@GrpcCallScope`, and `@Inject constructor(otel: OpenTelemetry)` to
`BalanceServiceEndpoint`. Remove the `BindableService` implementation (the generated adapter
provides `bindService()`). Remove the `GlobalOpenTelemetry.get()` calls; initialize `tracer`
and `meter` from the injected `otel` instance instead.

### Changes Required

**File**: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BalanceServiceEndpoint.kt`

Remove:
- `import io.grpc.BindableService`
- `import io.grpc.ServerServiceDefinition`
- `import io.opentelemetry.api.GlobalOpenTelemetry`
- `BindableService` from the implements clause
- `override fun bindService(): ServerServiceDefinition = BalanceBracketsGrpc.bindService(this)`
- The two `GlobalOpenTelemetry.get()` calls and the `// TODO` comment

Add:
- `import com.geekinasuit.daggergrpc.api.GrpcCallScope`
- `import com.geekinasuit.daggergrpc.api.GrpcServiceHandler`
- `import io.opentelemetry.api.OpenTelemetry`
- `import javax.inject.Inject`

The class declaration becomes:

```kotlin
@GrpcServiceHandler(BalanceBracketsGrpc::class)
@GrpcCallScope
class BalanceServiceEndpoint @Inject constructor(otel: OpenTelemetry) :
    BalanceBracketsGrpc.AsyncService {

  private val tracer: Tracer = otel.getTracer("brackets-service")
  private val meter = otel.getMeter("brackets-service")
  private val requestCounter: LongCounter =
    meter.counter("brackets.server.request.count") { setDescription("Number of bracket balance requests") }
  private val latencyHistogram: LongHistogram =
    meter.longHistogram("brackets.server.request.duration.ms") { setDescription("Request duration in milliseconds") }

  // balance() override unchanged
}
```

### Success Criteria

#### Automated Verification
- [ ] `bazel build //kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service:service_lib` passes
- [ ] KSP output contains `BalanceServiceEndpointAdapter.kt` with a `() -> BalanceBracketsGrpc.AsyncService` constructor
- [ ] `BalanceServiceEndpointAdapter.bindService()` delegates to `BalanceBracketsGrpc.bindService(this)` in generated output

---

## Phase 3: Write Dagger Modules

### Overview

Create a `dagger/` subpackage under the service package containing the component infrastructure.
All files in this phase are hand-written; `GrpcHandlersModule` and `GrpcCallScopeGraph` are
annotated `@Generated("to be generated by dagger-grpc module_generator")` to signal their
intended future status.

**Package**: `com.geekinasuit.polyglot.brackets.service.dagger`
**Directory**: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/`

### Changes Required

#### 1. `GrpcCallScopeGraph.kt`

```kotlin
package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.GrpcCallScope
import com.geekinasuit.polyglot.brackets.service.BalanceServiceEndpoint
import dagger.Subcomponent
import javax.annotation.Generated

/** Per-call subcomponent. Hand-written; intended to be generated by dagger-grpc module_generator. */
@Generated("to be generated by dagger-grpc module_generator")
@GrpcCallScope
@Subcomponent(modules = [GrpcCallScopeGraphModule::class])
interface GrpcCallScopeGraph {
  fun balance(): BalanceServiceEndpoint

  @Subcomponent.Factory
  interface Supplier {
    fun callScope(): GrpcCallScopeGraph
  }
}
```

#### 2. `GrpcCallScopeGraphModule.kt`

```kotlin
package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.GrpcCallContext
import dagger.Module

@Module(includes = [GrpcCallContext.Module::class])
object GrpcCallScopeGraphModule
```

#### 3. `GrpcHandlersModule.kt`

```kotlin
package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.polyglot.brackets.service.BalanceServiceEndpointAdapter
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import io.grpc.BindableService
import javax.annotation.Generated

/** Contributes gRPC service adapters to the Set<BindableService> multibinding.
 *  Hand-written; intended to be generated by dagger-grpc module_generator. */
@Generated("to be generated by dagger-grpc module_generator")
@Module
object GrpcHandlersModule {
  @Provides @IntoSet
  fun balanceHandler(supplier: GrpcCallScopeGraph.Supplier): BindableService =
    BalanceServiceEndpointAdapter { supplier.callScope().balance() }
}
```

#### 4. `InterceptorsModule.kt`

```kotlin
package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.GrpcCallContext
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import io.grpc.ServerInterceptor

/** Declares server-level gRPC interceptors. Add @Provides @IntoSet methods here for each
 *  interceptor. The Set<ServerInterceptor> is consumed by ServerModule.server(). */
@Module
object InterceptorsModule {
  @Provides @IntoSet
  fun callContextInterceptor(): ServerInterceptor = GrpcCallContext.Interceptor()
}
```

#### 5. `TelemetryModule.kt`

```kotlin
package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import com.geekinasuit.polyglot.brackets.telemetry.initTelemetry
import com.geekinasuit.polyglot.brackets.telemetry.merging
import com.geekinasuit.daggergrpc.api.ApplicationScope
import dagger.Module
import dagger.Provides
import io.grpc.opentelemetry.GrpcOpenTelemetry
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.resources.Resource

@Module
object TelemetryModule {
  @Provides @ApplicationScope
  fun grpcOpenTelemetry(config: ServiceAppConfig): GrpcOpenTelemetry {
    val resource = Resource.getDefault().merging {
      put("service.name", "brackets-service")
      put("deployment.environment", config.service.environment)
      put("service.instance.id", config.service.instanceId)
    }
    return initTelemetry(resource, config.telemetry)
  }

  /**
   * initTelemetry() registers the SDK as the global OpenTelemetry instance as a side effect.
   * This binding depends on grpcOpenTelemetry() to guarantee init has run before the global
   * is read.
   */
  @Provides @ApplicationScope
  @Suppress("UNUSED_PARAMETER")
  fun openTelemetry(grpcOtel: GrpcOpenTelemetry): OpenTelemetry = GlobalOpenTelemetry.get()
}
```

#### 6. `ServerModule.kt`

```kotlin
package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.daggergrpc.armeria.wrapService
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import dagger.Module
import dagger.Provides
import io.grpc.BindableService
import io.grpc.ServerInterceptor
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaServerTelemetry
import com.linecorp.armeria.server.Server
import java.net.InetSocketAddress

@Module
object ServerModule {
  @Provides @ApplicationScope
  fun server(
    services: Set<@JvmSuppressWildcards BindableService>,
    interceptors: Set<@JvmSuppressWildcards ServerInterceptor>,
    otel: OpenTelemetry,
    config: ServiceAppConfig,
  ): Server = Server.builder()
    .http(InetSocketAddress(config.service.host, config.service.port))
    .decorator(ArmeriaServerTelemetry.create(otel).newDecorator())
    .apply { services.forEach { service(wrapService(it, *interceptors.toTypedArray())) } }
    .build()
}
```

### Success Criteria

#### Automated Verification
- [ ] `bazel build //kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service:service_lib` passes with all module files added
- [ ] No Dagger binding errors in the build output (Dagger reports missing bindings at compile time)

---

## Phase 4: Write `ApplicationGraph`; Migrate `BracketsService.run()`

### Overview

Create the root Dagger `@Component`. Migrate `BracketsService.run()` to build the graph and
call `graph.server().start().join()` — all construction leaves `run()`.

### Changes Required

#### 1. `dagger/ApplicationGraph.kt`

```kotlin
package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import com.linecorp.armeria.server.Server
import dagger.BindsInstance
import dagger.Component

@ApplicationScope
@Component(
  modules = [
    ApplicationGraphModule::class,
    GrpcHandlersModule::class,
    InterceptorsModule::class,
    TelemetryModule::class,
    ServerModule::class,
  ],
  subcomponents = [GrpcCallScopeGraph::class],
)
interface ApplicationGraph : GrpcCallScopeGraph.Supplier {
  fun server(): Server

  @Component.Builder
  interface Builder {
    @BindsInstance fun config(config: ServiceAppConfig): Builder
    fun build(): ApplicationGraph
  }

  companion object {
    fun builder(): Builder = DaggerApplicationGraph.builder()
  }
}
```

#### 2. `dagger/ApplicationGraphModule.kt`

```kotlin
package com.geekinasuit.polyglot.brackets.service.dagger

import dagger.Module
import dagger.Provides

/** Satisfies the GrpcCallScopeGraph.Supplier binding by providing the ApplicationGraph itself. */
@Module
object ApplicationGraphModule {
  @Provides
  fun supplier(graph: ApplicationGraph): GrpcCallScopeGraph.Supplier = graph
}
```

#### 3. `service/service.kt` — migrate `run()`

Remove all manual construction. The `run()` body becomes:

```kotlin
override fun run() {
  assertAllOptionsAreBound()
  val config = resolveConfig(loadConfig("/service/application.yml"))
  val graph = ApplicationGraph.builder().config(config).build()
  val server = graph.server()
  server.closeOnJvmShutdown()
  server.start().join()
}
```

Remove the following imports from `service.kt` (now handled by modules):
- `initTelemetry`, `merging`, `span` from telemetry
- `GlobalOpenTelemetry`, `ArmeriaServerTelemetry`, `Resource`
- `GrpcService`, `wrapService` (moved to `ServerModule`)
- `InetSocketAddress`
- The startup span logic (consider adding to `ServerModule` or removing — see note below)

**Note on startup span**: The current `run()` wraps server startup in a `server.startup` span.
With DI, the `Server` is already constructed by Dagger before `run()` calls `start()`. Move
the startup span into `ServerModule.server()` or into a `ServerListenerAdapter` inside
`ServerModule`. The latter is idiomatic — add a `ServerListenerAdapter` that starts the span
in `serverStarting` and ends it in `serverStarted`, using a `Tracer` injected into `ServerModule`.

Add `Tracer` to `ServerModule.server(...)` params: `tracer: Tracer`, provided by:

```kotlin
// In TelemetryModule:
@Provides @ApplicationScope
fun tracer(otel: OpenTelemetry): Tracer = otel.getTracer("brackets-service")
```

### Success Criteria

#### Automated Verification
- [ ] `bazel build //kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service:service_bin` passes
- [ ] `bazel run //kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service:service_bin -- --help` prints usage without errors
- [ ] Existing tests pass: `bazel test //kotlin/src/test/...`

#### Manual Verification
- [ ] Service starts: `bazel run :service_bin` binds on expected host/port
- [ ] Client connects and a balance request succeeds end-to-end
- [ ] Telemetry initializes (logs show "OTel SDK initialized" line)

**Implementation Note**: After automated and manual verification, confirm before proceeding to Phase 5.

---

## Phase 5: Test Module

### Overview

Add a `FakeTelemetryModule` usable in unit tests that don't need a live OTLP exporter.
It replaces `TelemetryModule` in test components, binding a no-op `OpenTelemetry` instance
and a no-op `GrpcOpenTelemetry`.

### Changes Required

**File**: `kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/FakeTelemetryModule.kt`

```kotlin
package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import dagger.Module
import dagger.Provides
import io.grpc.opentelemetry.GrpcOpenTelemetry
import io.opentelemetry.api.OpenTelemetry

@Module
object FakeTelemetryModule {
  @Provides @ApplicationScope
  fun grpcOpenTelemetry(): GrpcOpenTelemetry =
    GrpcOpenTelemetry.newBuilder().sdk(io.opentelemetry.sdk.OpenTelemetrySdk.builder().build()).build()

  @Provides @ApplicationScope
  fun openTelemetry(): OpenTelemetry = OpenTelemetry.noop()
}
```

Add a `TestApplicationGraph` in the same test directory that uses `FakeTelemetryModule` in place
of `TelemetryModule`. Wire an `@Component` for it following the same structure as
`ApplicationGraph`.

### Success Criteria

#### Automated Verification
- [ ] A test that instantiates `TestApplicationGraph` without a live OTLP endpoint passes
- [ ] `bazel test //kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/...` all green

---

## Testing Strategy

### Unit Tests
- `FakeTelemetryModule` makes `BalanceServiceEndpoint` testable without OTel infrastructure
- Dagger binding correctness is verified at compile time (missing bindings = build error)

### Integration Tests
- Existing client ↔ service integration test covers end-to-end behavior after Phase 4

### Manual Testing Steps
1. `bazel run //kotlin/.../service:service_bin` — service starts, logs show Dagger-wired startup
2. `echo "((()))" | bazel run //kotlin/.../client:client_bin` — returns "Brackets are balanced."
3. `echo "((" | bazel run //kotlin/.../client:client_bin` — returns "Brackets are NOT balanced"

## References

- Ticket: `thoughts/shared/tickets/KT-002-kotlin-dagger-grpc-di.md`
- Library: `https://github.com/geekinasuit/dagger-grpc`
- Library exemplar: `examples/io_grpc/bazel_build_kt/service/armeria/`
- Client DI ticket: `thoughts/shared/tickets/KT-003-kotlin-dagger-client-di.md`
- OTel plan: `thoughts/shared/plans/2026-03-07-kotlin-otel-instrumentation.md`
