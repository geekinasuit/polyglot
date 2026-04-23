---
id: KT-002
title: Introduce Dagger DI via dagger-grpc integration
area: kotlin, grpc, di
status: resolved
created: 2026-03-07
resolved_date: 2026-04-22
---

# Kotlin: Introduce Dagger DI via dagger-grpc integration

## Resolution

Dagger 2 with `dagger-grpc` introduced for the Kotlin service. `@ApplicationScope` `ApplicationGraph` `@Component` with `BracketsDbModule`, `DatabaseModule`, `GrpcHandlersModule`, `InterceptorsModule`, `TelemetryModule`, `ServerModule`. `@GrpcCallScope` subcomponent (`GrpcCallScopeGraph`) handles per-call binding. `BalanceServiceEndpoint` annotated with `@Inject` constructor. `FakeTelemetryModule` swaps in no-op OTel for tests. All wiring verified with `bazel test //kotlin/...`.

## Original Summary

The Kotlin service and client currently wire all objects manually (hardcoded construction
in `run()` methods). As the project grows — particularly with the OTel telemetry subsystem,
configuration objects, and potentially auth/interceptor chains — this becomes hard to test
and hard to extend.

Introduce Dagger 2 as the compile-time DI framework, using the `dagger-grpc` integration
library to handle the gRPC/Armeria-specific binding concerns.

## Context

This was explicitly deferred from the OTel instrumentation plan
(`thoughts/shared/plans/2026-03-07-kotlin-otel-instrumentation.md`) to keep that plan
focused. The OTel plan was written without Dagger; it leaves construction in `run()` methods.
When this ticket is implemented, the OTel modules (`TelemetrySetup`, the config data classes,
the gRPC interceptors) are the natural first things to migrate into Dagger modules.

## Work Needed

1. Research `dagger-grpc` and assess compatibility with the Armeria + grpc-java stack in use
   (`armeria 1.26.4`, `grpc-java 1.71.0`, `grpc-kotlin 1.5.0`).
2. Configure Bazel kapt for `rules_kotlin 2.1.9` — the Dagger compiler runs as a kapt
   annotation processor. Verify the `plugins` / `kt_compiler_plugin` wiring works with the
   current build graph.
3. Define `@Component` interfaces for the service and client binaries.
4. Define `@Module` classes for: config, telemetry (OTel SDK instances, interceptors),
   server (Armeria `Server`, `GrpcService`, `BalanceServiceEndpoint`), client (`ManagedChannel`,
   coroutine stub).
5. Annotate injectable classes with `@Inject` constructors (starting with
   `BalanceServiceEndpoint`).
6. Define test modules (e.g. a `FakeTelemetryModule`) that swap in OTel no-op SDK instances
   for unit tests that don't need a live OTLP exporter.

## References

- OTel plan: `thoughts/shared/plans/2026-03-07-kotlin-otel-instrumentation.md`
- dagger-grpc: https://github.com/google/dagger (see grpc extension docs)
- Dagger 2: https://dagger.dev