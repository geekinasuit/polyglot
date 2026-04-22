---
date: 2026-04-22
status: accepted
author: claude (Architect agent)
tickets: KT-006, KT-007
supersedes: 2026-04-22-endpoint-depends-on-pair-map-not-repository.md
---

# ADR: BalanceServiceEndpoint corrected to @GrpcCallScope; BracketPairSource removed

## Status

Accepted

## Context

The previous architecture (ADR `2026-04-22-endpoint-depends-on-pair-map-not-repository.md`)
annotated `BalanceServiceEndpoint` with `@ApplicationScope` and introduced `BracketPairSource` —
a narrow single-method interface — as a per-request freshness boundary. The endpoint called
`bracketPairSource.get()` once per `balance()` invocation to obtain the current bracket pair map.

This design worked around a scope misattribution. The dagger-grpc framework (already in use in
this project) provides `@GrpcCallScope`, a `@Subcomponent` scope representing one gRPC request.
The framework's example (`HelloWorldService`) shows that service handlers are `@GrpcCallScope`,
not `@ApplicationScope`. The existing code diverged from this intent.

Once the scope is corrected, the `@GrpcCallScope` mechanism already provides per-request
freshness for any binding declared in that scope. The `BracketPairSource` interface becomes
redundant — it was a workaround for the incorrect scope, not a necessary design element.

## Decision

1. **Correct `BalanceServiceEndpoint` from `@ApplicationScope` to `@GrpcCallScope`.** This is a
   bug fix, not a new design decision. The dagger-grpc framework's `@GrpcCallScope` subcomponent
   is created per request; service handlers must be in that scope to receive per-request data.

2. **Remove `BracketPairSource`.** The interface and its production implementation do not exist
   in the design. `GrpcCallScopeGraphModule` provides a `@GrpcCallScope`-scoped `Map<Char, Char>`
   by calling `repo.loadEnabledPairs()` directly. Per-request freshness is provided by Dagger's
   scope mechanism, not by a domain interface wrapper.

3. **Introduce `BracketsServiceTelemetry` at `@ApplicationScope`.** With the endpoint now
   `@GrpcCallScope`, its constructor runs per-request. The OTel instruments (`Tracer`,
   `LongCounter`, `LongHistogram`) must not be constructed per-request — they belong at
   application scope. A wrapper type (`BracketsServiceTelemetry`) groups these instruments, is
   constructed once at startup, and is injected into the endpoint alongside the pair map.

4. **Remove `BracketConfigModule`.** This module existed solely to provide the `BracketPairSource`
   implementation. With `BracketPairSource` removed, the module has no remaining responsibility.
   The repository is now consumed directly by `GrpcCallScopeGraphModule`.

## Rationale

### `@GrpcCallScope` is the correct freshness mechanism

The `@GrpcCallScope` subcomponent is created once per gRPC request. Any binding in that scope is
resolved fresh for each request. This is precisely what `BracketPairSource.get()` was simulating —
calling the repository on every invocation to ensure freshness. With the correct scope in place,
the `Map<Char, Char>` is simply a scoped binding: computed once per request (when the subcomponent
is created), injected into the endpoint, and discarded with the subcomponent when the request ends.

### Simpler endpoint

The endpoint previously called `bracketPairSource.get()` on every `balance()` invocation. With
the corrected design, the endpoint receives a resolved `Map<Char, Char>` at construction time.
There is no interface method to call at request time. The endpoint is simpler and its dependency
is more direct.

### Better coroutine shielding (compared to prior design)

The `BracketPairSource` design required that if the repository became `suspend`, then
`BracketPairSource.get()` would also become `suspend`, and the endpoint's `balance()` method would
need to adapt. With the corrected design, the endpoint is fully shielded: if the repository becomes
`suspend`, only `GrpcCallScopeGraphModule`'s provider method adapts (calling the repository in a
coroutine context). The endpoint receives the same resolved `Map<Char, Char>` regardless.

### OTel instruments belong at application scope

OTel instruments are identified by name. The OTel SDK caches them — repeated `getTracer` and
`getMeter` calls with the same name return the same underlying object. Constructing instruments in
a per-request constructor is therefore functionally safe. However, it is architecturally incorrect:
infrastructure objects that are logically application-scoped should be in the application scope.
A `BracketsServiceTelemetry` wrapper makes the application-scope intent explicit, groups related
concerns, and removes unnecessary work from the per-request path.

### Qualifier on `Map<Char, Char>` is recommended

Unlike the former `BracketPairSource` (a named type with no collision risk), `Map<Char, Char>` is
generic. Without a qualifier, any future `@GrpcCallScope` binding of the same type would cause a
Dagger duplicate binding error. A qualifier (e.g., `@BracketPairs`) prevents this fragility. The
exact form is deferred to the TDD Designer.

## Consequences

**Positive:**
- `BalanceServiceEndpoint` is in the correct Dagger scope, matching the dagger-grpc framework's
  intended usage
- Per-request freshness is provided by Dagger's scope mechanism, not by a domain interface wrapper
- The endpoint is fully shielded from an async (coroutine) migration of the repository
- Endpoint unit tests are simpler: a `Map<Char, Char>` literal instead of a fake interface
- `BracketConfigModule` is removed — one fewer module in the graph
- OTel instruments are initialized once at startup

**Negative / constraints:**
- `GrpcCallScopeGraphModule` now has a runtime dependency (the repository). This means the
  subcomponent module is no longer a pure wiring module — it performs a DB call per request.
  This is acceptable: the module's role is to provide per-request data, and the DB call is
  explicitly what provides it.
- `TestGrpcCallScopeGraphModule` is a new test module that did not exist in the prior design.
  It replaces `GrpcCallScopeGraphModule` in integration tests that do not exercise DB behavior.

## Alternatives considered

**Keep `BracketPairSource` and fix the scope separately:**
Fixing the scope to `@GrpcCallScope` while keeping `BracketPairSource` would result in a
per-request-scoped endpoint calling an application-scoped interface method per invocation — the
same as before, but with one unnecessary indirection added. The interface exists only because the
scope was wrong; once the scope is correct, the interface adds no value.

**Leave `@ApplicationScope` and keep `BracketPairSource`:**
This was the prior design. It works but diverges from the dagger-grpc framework's intent. The
`GrpcCallScopeGraph.Supplier` and per-call subcomponent machinery already exist in the codebase;
the endpoint should use them, not circumvent them.
