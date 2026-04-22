---
date: 2026-04-22
status: superseded
superseded-by: 2026-04-22-endpoint-grpccallscope-and-map-injection.md
author: claude (Architect agent)
tickets: KT-006, KT-007
---

# ADR: BalanceServiceEndpoint depends on BracketPairSource, not the repository or a raw map

> **Superseded (2026-04-21):** This ADR's core decision (inject `BracketPairSource`) has been
> reversed. `BalanceServiceEndpoint` is now `@GrpcCallScope` and receives a plain `Map<Char, Char>`
> injected by `GrpcCallScopeGraphModule`. `BracketPairSource` does not exist in the design.
> See `2026-04-22-endpoint-grpccallscope-and-map-injection.md` for the current decision.

## Status

Superseded

## Context

The bracket configuration feature (KT-007) requires `BalanceServiceEndpoint` to use a
dynamically configured set of bracket pairs loaded from the database, rather than the
current hardcoded `closedParentheses` map.

Three architectural options were considered for how the endpoint receives this configuration:

**Option A:** Inject the repository interface into `BalanceServiceEndpoint`. The endpoint
calls `repository.loadEnabledPairs()` on each request.

**Option B:** Inject `Map<Char, Char>` directly into `BalanceServiceEndpoint`. The pair map
is loaded once at application startup by `BracketConfigModule` (application-scoped) and
injected as an immutable value. DB changes require a service restart.

**Option C:** Inject a narrow `BracketPairSource` interface into `BalanceServiceEndpoint`.
The endpoint calls `bracketPairSource.get()` once per request. The production implementation
is application-scoped (stable reference to the repository) but queries the DB on each call.

**Option D (considered and rejected):** Inject `Provider<Map<Char, Char>>` into the endpoint.
`Provider<T>` is a Dagger interface; using it at the call site exposes the DI framework and is
less expressive than a domain-named interface. Rejected in favor of Option C.

## Decision (superseded)

~~Use **Option C** — inject `BracketPairSource`.~~

This decision has been superseded. See `2026-04-22-endpoint-grpccallscope-and-map-injection.md`.

## Why this decision was superseded

The `BracketPairSource` interface was introduced because `BalanceServiceEndpoint` was
`@ApplicationScope`. An application-scoped object cannot receive per-request data directly, so a
per-call interface method was the workaround.

The root issue was that `BalanceServiceEndpoint` should have been `@GrpcCallScope` from the start
— this is what the dagger-grpc framework intends for service handlers. With the scope corrected,
per-request data (the bracket pair map) is simply a `@GrpcCallScope` binding provided by
`GrpcCallScopeGraphModule`. No `BracketPairSource` wrapper is needed.
