---
id: KT-003
title: Dagger DI for the gRPC client
area: kotlin, grpc, di
status: open
created: 2026-03-22
---

# Kotlin: Dagger DI for the gRPC client

## Summary

The Kotlin gRPC client (`BracketsClient`) currently wires all objects manually in `run()`:
config, telemetry (`initTelemetry`), `ManagedChannel`, `BalanceBracketsCoroutineStub`. Introduce
Dagger 2 DI for the client to mirror what is done for the service in ticket
`KT-002-kotlin-dagger-grpc-di.md`.

## Context

Deferred from the server DI plan (`thoughts/shared/plans/2026-03-22-kotlin-dagger-grpc-di.md`)
to keep that plan focused. The client has no call-scoped subcomponent need — it is simpler than
the service. The server DI plan should be completed and merged before this is started.

## Work Needed

1. Define a `ClientApplicationGraph` `@Component` for the client binary.
2. Write `ClientConfigModule` — `@BindsInstance ClientAppConfig` (same CLI-first pattern as server; client uses `ClientAppConfig` with `client: ClientConfig`, not `ServiceAppConfig`).
3. Write `ClientTelemetryModule` — wraps `initTelemetry()` for the client resource.
4. Write `ChannelModule` — provides `ManagedChannel` (host/port from config, configured with `grpcOtel`).
5. Write `StubModule` — provides `BalanceBracketsCoroutineStub` from the channel.
6. Migrate `BracketsClient.run()` to build the graph and call through it.
7. Add `FakeClientTelemetryModule` for unit tests.

## References

- Server DI plan: `thoughts/shared/plans/2026-03-22-kotlin-dagger-grpc-di.md`
- Server DI ticket: `thoughts/shared/tickets/KT-002-kotlin-dagger-grpc-di.md`
- Dagger docs: https://dagger.dev
