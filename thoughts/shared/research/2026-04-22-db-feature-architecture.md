---
date: 2026-04-22
researcher: claude (Architect agent)
topic: Kotlin DB feature architecture — layer boundaries, Dagger decomposition, test surfaces
tickets: KT-005, KT-006, KT-007
status: complete (revised 2026-04-22 — GrpcCallScope correction, BracketPairSource removed)
---

# DB Feature Architecture: Kotlin Layer Design

This document is the authoritative Architect output for the DB feature (PRs 3–5). The TDD Designer
reads this first and uses it as the primary constraint. Sections explicitly mark what is **fixed**
(TDD Designer must not deviate without Architect sign-off) versus **open** (TDD Designer's domain).

---

## Revision history

| Date | Change |
|------|--------|
| 2026-04-22 (initial) | Three-layer design with `BracketPairSource` interface for per-request freshness; `BalanceServiceEndpoint` at `@ApplicationScope` |
| 2026-04-22 (this revision) | `BalanceServiceEndpoint` corrected to `@GrpcCallScope`; `BracketPairSource` removed; `GrpcCallScopeGraphModule` provides `Map<Char, Char>`; `BracketConfigModule` removed; `BracketsServiceTelemetry` wrapper introduced |

---

## 1. Layer Boundaries

**Decision: fixed**

Three layers exist, each with a single primary responsibility:

### DB Adapter Layer
**Responsibility:** Infrastructure plumbing — DataSource (HikariCP pool), Flyway lifecycle, JOOQ
`DSLContext`. This layer has no business logic. It knows about JDBC URLs, driver classes, connection
pool sizing, and migration file locations. Nothing above this layer needs to know how connections are
pooled, which driver is loaded, or that Flyway ran.

**Collaborates with:** `DatabaseConfig` (from `ServiceAppConfig`); `//db:migrations` SQL files at
runtime.

**Provided by:** `DatabaseModule` Dagger module.

### Repository Layer
**Responsibility:** Data access logic — translate between domain types and database records. The
repository queries the `bracket_pair` table, maps JOOQ-generated records to domain types, and returns
the result. All JOOQ types and `DSLContext` are hidden behind this boundary. Nothing above the
repository layer sees a `BracketPairRecord`, a `DSLContext`, or any JOOQ import.

**Collaborates with:** `DSLContext` (injected from DB adapter layer); JOOQ-generated `Tables` /
`BracketPairRecord` classes (internal only — never exposed upward).

**Interface:** The repository is accessed through an interface (not the concrete class). This is
**fixed** — see section 3 for rationale.

### Service Layer
**Responsibility:** Request handling — receive a gRPC request, invoke `balancedBrackets` with the
configured pair map, return the gRPC response. The service layer knows nothing about databases,
repositories, or JOOQ. It receives a `Map<Char, Char>` injected at construction time by the Dagger
`@GrpcCallScope` subcomponent. The pair map is fresh per request — loaded by
`GrpcCallScopeGraphModule` on each subcomponent creation.

**Collaborates with:** injected `Map<Char, Char>` (pair map, loaded fresh per request);
`BracketsServiceTelemetry` (application-scoped OTel instruments); `balancedBrackets`
(algorithm function, already parameterized in PR 1).

**Key constraint (fixed):** `BalanceServiceEndpoint` is annotated `@GrpcCallScope` (not
`@ApplicationScope`). This matches the dagger-grpc framework's intended usage, where service
handlers are per-request subcomponent members. The prior `@ApplicationScope` annotation was a bug.

**Key constraint (fixed):** The endpoint receives a `Map<Char, Char>` — not the repository
interface and not a `BracketPairSource` wrapper. `GrpcCallScopeGraphModule` is the per-request
freshness boundary: it calls `repo.loadEnabledPairs()` when the `@GrpcCallScope` subcomponent is
created (once per request). DB changes take effect without a service restart.

**Collaboration diagram:**

```
BalanceServiceEndpoint (@GrpcCallScope)
  ← Map<Char, Char>   (injected once at subcomponent creation — per-request)
  ← BracketsServiceTelemetry  (injected from @ApplicationScope)
       ↑
  GrpcCallScopeGraphModule @Provides Map<Char, Char>
    calls repo.loadEnabledPairs() at subcomponent creation
       ↑
  BracketPairRepository (interface, @ApplicationScope impl)
       ↑
  DSLContext
       ↑
  DataSource (+ Flyway has already run)
       ↑
  DatabaseConfig ← ServiceAppConfig
```

---

## 2. Dagger Module Decomposition

**Decision: fixed (topology); open (exact provider method signatures)**

### `DatabaseModule`
**Provides:** `DataSource`, `DSLContext`

**Responsibilities:**
- Extract `DatabaseConfig` from `ServiceAppConfig` via a `@Provides` method (unwrapper pattern)
- Build a HikariCP `DataSource` from the extracted config — and run Flyway inside this provider,
  before returning the pool (see section 4 for lifecycle rationale)
- Build a JOOQ `DSLContext` from the `DataSource`, with dialect inferred from the JDBC URL

**Scope:** Both bindings are `@ApplicationScope`. This guarantees a single pool and a single
`DSLContext` per application lifecycle.

**Installed in:** `ApplicationGraph` (production) and `TestApplicationGraph` (overridden by a
test-specific module for H2 in-memory — see section 6).

**`DatabaseConfig` flow:** `ServiceAppConfig` is a `@BindsInstance` in the `ApplicationGraph`
builder. `DatabaseModule` exposes a `@Provides` method that receives `ServiceAppConfig` and returns
its `.database` field. The TDD Designer defines the exact signature.

### `GrpcCallScopeGraphModule` (extended)
**Previously:** included only `GrpcCallContext.Module`.

**Now also provides:** `Map<Char, Char>` — a `@GrpcCallScope`-scoped binding that calls
`repo.loadEnabledPairs()` on the `@ApplicationScope` `BracketPairRepository`. The map is loaded
once per request (when the subcomponent is created) and injected into `BalanceServiceEndpoint`.

**Qualifier:** `Map<Char, Char>` is a generic type. A Dagger qualifier is **recommended** to prevent
future collisions (any additional per-request map binding would cause a duplicate binding error
without a qualifier). The TDD Designer chooses the exact form (e.g., `@BracketPairs` custom
annotation or `@Named("bracketPairs")`). This is a change from the prior design: the previous
`BracketPairSource` named type needed no qualifier; a raw `Map<Char, Char>` does.

**Test replacement:** `TestGrpcCallScopeGraphModule` replaces this module in integration tests,
providing a fixed `Map<Char, Char>` without querying the DB.

### `TelemetryModule` / `BracketsServiceTelemetry`
**New type:** `BracketsServiceTelemetry` — an `@ApplicationScope` data class or value class that
groups the OTel instruments needed by `BalanceServiceEndpoint`: `Tracer`, `LongCounter`, and
`LongHistogram`. It is constructed from `OpenTelemetry` in `TelemetryModule` (or a new
`BracketsTelemetryModule` — the TDD Designer decides).

**Why a wrapper (Option A):**
- Instruments are initialized once at application startup, not per-request. Even though the OTel SDK
  caches instruments by name, per-request construction is unnecessary churn.
- The wrapper groups related telemetry concerns, matching the pattern of other `@ApplicationScope`
  services in the graph.
- The endpoint constructor becomes `(pairs: Map<Char, Char>, telemetry: BracketsServiceTelemetry)` —
  lean and expressive.

**Why not Option B (individual bindings):** More bindings to manage for no additional clarity.
`Tracer` and `LongCounter` and `LongHistogram` as separate bindings would require qualifiers to
distinguish them from any other OTel bindings; a wrapper type is self-documenting.

**Why not Option C (leave as-is):** With the endpoint now `@GrpcCallScope`, the OTel instrument
construction runs per-request. Functionally safe (OTel caches), but architecturally inconsistent
with the principle that infrastructure objects belong at application scope.

**Exact name:** The Architect names the concept `BracketsServiceTelemetry`; the TDD Designer may
finalize the name (see section 7).

### Removed: `BracketConfigModule`
`BracketConfigModule` existed solely to provide the `BracketPairSource` implementation.
`BracketPairSource` is removed from the design (see section 3). There is no replacement module
because the `Map<Char, Char>` is now provided by `GrpcCallScopeGraphModule`. `BracketConfigModule`
is not installed in either `ApplicationGraph` or `TestApplicationGraph`.

### Relationship to existing modules
- `ApplicationGraphModule` — unchanged (provides `GrpcCallScopeGraph.Supplier`)
- `GrpcHandlersModule` — unchanged
- `TelemetryModule` — extended (or `BracketsTelemetryModule` added; TDD Designer decides placement)
- `ServerModule` — unchanged
- `InterceptorsModule` — unchanged

### Module topology summary

```
ApplicationGraph @Component(modules = [
    ApplicationGraphModule,
    GrpcHandlersModule,
    InterceptorsModule,
    TelemetryModule,       ← extended or supplemented with BracketsServiceTelemetry provider
    ServerModule,
    DatabaseModule,        ← new (PR 4)
])

GrpcCallScopeGraph @Subcomponent(modules = [
    GrpcCallScopeGraphModule,   ← extended: now provides Map<Char, Char> from repo
])
```

```
TestApplicationGraph @Component(modules = [
    TestApplicationGraphModule,
    GrpcHandlersModule,
    InterceptorsModule,
    FakeTelemetryModule,
    ServerModule,
    TestDatabaseModule,    ← new (replaces DatabaseModule in tests)
])

Test GrpcCallScopeGraph @Subcomponent(modules = [
    TestGrpcCallScopeGraphModule,   ← new (replaces GrpcCallScopeGraphModule; provides fixed Map)
])
```

---

## 3. Repository Responsibility

**Decision: fixed (interface requirement); open (interface name and method signatures)**

### Responsibility
The repository is responsible for:
1. Querying the `bracket_pair` table for enabled rows
2. Mapping each row's `open_char` and `close_char` columns to a `Char`
3. Returning the result as a domain type (a `Map<Char, Char>` mapping close → open)

The repository is **not** responsible for:
- Caching or memoization (Dagger scope handles this — the `@GrpcCallScope` subcomponent creation
  calls the provider once, so the map is computed once per request and injected)
- Connection management (that is HikariCP's responsibility)
- Schema validation or migration (that is Flyway's responsibility)
- Business logic (that is `balancedBrackets`'s responsibility)

### Interface requirement (fixed)
The repository must be accessed through an interface. `GrpcCallScopeGraphModule` depends on the
interface type, not the concrete implementation.

### BracketPairSource removed
The previous design introduced `BracketPairSource` as a narrow per-request freshness boundary.
This interface is no longer needed. The `@GrpcCallScope` mechanism already provides per-request
freshness — `GrpcCallScopeGraphModule` calls `repo.loadEnabledPairs()` once per subcomponent
creation (i.e., once per request). A separate interface wrapper adds indirection without benefit.

**Rationale for removal:**
1. **`@GrpcCallScope` is the correct freshness boundary.** The dagger-grpc framework's
   `@GrpcCallScope` subcomponent is created per request. Any binding in that scope is fresh per
   request. There is no need for a domain interface to replicate this behavior.
2. **Simpler endpoint.** The endpoint receives a plain `Map<Char, Char>` — the most direct
   representation of its dependency. No interface method to call per request; no fake interface to
   implement in tests.
3. **`BalanceServiceEndpoint` is `@GrpcCallScope`.** The prior design kept the endpoint at
   `@ApplicationScope` and relied on `BracketPairSource.get()` to inject freshness into a
   long-lived object. Now that the endpoint is scoped correctly, that workaround is unnecessary.

### What crosses the interface boundary
- **In:** nothing (query is internal; the repository decides what to load)
- **Out:** `Map<Char, Char>` — close-to-open direction, matching the existing `closedParentheses`
  map convention established in `brackets.kt`

The TDD Designer defines the interface name and the exact method signature (including whether the
method is `open`, `fun`, or eventually `suspend fun`).

---

## 4. Startup and Lifecycle

**Decision: fixed**

Flyway runs inside the `DataSource` provider in `DatabaseModule`. The sequence is:

1. Dagger builds the `ApplicationGraph` (triggered by `DaggerApplicationGraph.builder().config(config).build()`)
2. Dagger resolves `DataSource` (first time it is needed — either directly or as a transitive dep)
3. Inside the `DataSource` provider:
   a. HikariCP pool is configured with the JDBC URL, driver, and pool size
   b. Flyway is configured with `filesystem:` location pointing to `//db:migrations` runfiles path
   c. `Flyway.migrate()` is called
   d. The initialized pool is returned
4. Dagger resolves `DSLContext` — which depends on `DataSource`, so step 3 is already complete
5. Dagger resolves the `@ApplicationScope` repository implementation — which depends on `DSLContext`
6. At request time, Armeria calls the gRPC adapter, which calls `supplier.callScope()`. Dagger
   creates the `@GrpcCallScope` subcomponent. During subcomponent creation, `GrpcCallScopeGraphModule`
   calls `repo.loadEnabledPairs()` to provide `Map<Char, Char>`. This is when the DB is read —
   once per request. The fully-populated endpoint is returned.

**Why this sequencing is safe:** Dagger's construction DAG enforces that `DataSource` is fully
initialized (including Flyway) before `DSLContext` is constructed, and `DSLContext` is constructed
before the repository is constructed. The `@GrpcCallScope` subcomponent can only be created after
the `ApplicationGraph` is fully built — so the repository is always available before any request
can be handled. No explicit lifecycle hooks or ordering tricks are needed.

**Flyway configuration constraint (from DBA, fixed):**
```kotlin
Flyway.configure()
    .sqlMigrationPrefix("")
    .sqlMigrationSeparator("_")
    .locations("filesystem:$migrationsPath")
    .dataSource(dataSource)
    .load()
    .migrate()
```
The `sqlMigrationPrefix("")` and `sqlMigrationSeparator("_")` settings must match the dbmate
timestamp filename convention. These settings apply in both production and test scope — the DBA doc
confirmed this is required.

**Runfiles path resolution (from plan, fixed):**
```kotlin
val migrationsPath = Paths.get(
    System.getenv("RUNFILES_DIR") ?: ".",
    "_main/db/migrations"
).toAbsolutePath().toString()
```
Bazel targets that run Flyway must declare `data = ["//db:migrations"]`.

---

## 5. Coroutine Readiness

**Decision: fixed (boundary location); open (whether to add `suspend` now or later)**

JOOQ is blocking. The current `BalanceServiceEndpoint` uses `StreamObserver` callbacks, not
coroutines. The `balance()` method runs on a gRPC thread pool thread — blocking there is acceptable
for the current scale.

**Boundary location (fixed):** The repository interface is the coroutine migration boundary.
When (if) the repository becomes async, the interface method signature changes (e.g.,
`fun loadEnabledPairs(): Map<Char, Char>` → `suspend fun loadEnabledPairs(): Map<Char, Char>`).

**Improved shielding vs. prior design:** With `BracketPairSource` removed and the endpoint receiving
a plain `Map<Char, Char>`, the endpoint is now **fully shielded** from an async migration.
If the repository becomes `suspend`, only `GrpcCallScopeGraphModule`'s provider method needs to
adapt (calling the repository in a coroutine context). The endpoint receives the same resolved
`Map<Char, Char>` regardless. This is the same shielding as the original Option B design, regained.

**What a future async migration looks like:**
1. Repository interface: `fun loadEnabledPairs()` → `suspend fun loadEnabledPairs()`
2. Repository implementation: JOOQ calls moved to a coroutine dispatcher (e.g., `Dispatchers.IO`)
3. `GrpcCallScopeGraphModule` provider: becomes a `suspend` provider or uses `runBlocking`/coroutine
   scope to call the repository (one small change, bounded to this module)
4. `BalanceServiceEndpoint`: **unchanged** — receives the same `Map<Char, Char>`

**Constraint (fixed):** No JOOQ types, `DSLContext`, or blocking DB calls may exist above the
repository interface — not in `GrpcCallScopeGraphModule`'s provider (it calls the interface, not
JOOQ directly), not in `BalanceServiceEndpoint`.

The current design defers this migration to a later ticket. The TDD Designer should not introduce
`suspend` into the interface now unless there is a concrete reason.

---

## 6. Test Surfaces

**Decision: fixed (layer-level strategy); open (exact test class names, fake implementations)**

### Repository layer
**What is tested:**
- Unit/integration: repository implementation with a real H2 in-memory `DSLContext`
  - Enabled pairs loaded correctly (migration applied via Flyway on test H2 database)
  - Disabled pairs excluded
  - Empty result (all pairs disabled) → empty map, no crash
  - Mapping direction correct: close → open (matching `closedParentheses` convention)

**Test doubles needed:**
- H2 in-memory `DataSource` with `DB_CLOSE_DELAY=-1` in the JDBC URL (see DBA doc constraint)
- Flyway applied in test setup (same migration files, same `sqlMigrationPrefix`/`sqlMigrationSeparator` config)
- No Dagger — construct the repository implementation directly with an H2 `DSLContext`

**What is NOT tested here:** service business logic, gRPC response shapes

### Service layer (`BalanceServiceEndpoint`)
**What is tested:**
- Unit: endpoint behavior with a known `Map<Char, Char>` and a no-op (or fake)
  `BracketsServiceTelemetry` — no DB, no repository, no Dagger
  - Known pair set → correct balanced/unbalanced result
  - Empty pair set → error response, no crash
  - Unbalanced input → correct error message shape
  - Characters not in pair set treated as plain text

**Test doubles needed:**
- A `Map<Char, Char>` literal (no fake needed — just a map)
- A no-op `BracketsServiceTelemetry` instance (the TDD Designer writes this — likely a few lines
  wrapping a no-op `OpenTelemetry.noop()`)

**What is NOT tested here:** DB access, repository behavior

### Dagger graph (integration)
**What is tested:**
- Graph builds with `TestDatabaseModule` (H2 in-memory `DatabaseConfig`)
- Repository (or its interface) is injectable from the test graph
- Full end-to-end: disabling a pair in the test H2 DB causes that pair's characters to be treated
  as plain text in the service response

**Which modules the test graph replaces:**
- `DatabaseModule` → `TestDatabaseModule` (provides H2 `DataSource`/`DSLContext` + runs Flyway on H2)
- `GrpcCallScopeGraphModule` → `TestGrpcCallScopeGraphModule` (provides a fixed `Map<Char, Char>`
  instead of querying the DB; used in tests that do not exercise the repository)
- `TelemetryModule` → `FakeTelemetryModule` (already in place)
- All other modules reused unchanged

**`TestDatabaseModule` shape (fixed at responsibility level; TDD Designer defines exact signatures):**
- Provides `DataSource` using `jdbc:h2:mem:<unique-name>;DB_CLOSE_DELAY=-1`
- Provides `DatabaseConfig` with the H2 URL (so the repository receives a valid config)
- Runs Flyway on the H2 DataSource in the `DataSource` provider (same Flyway config as production)

**`TestGrpcCallScopeGraphModule` shape (new; fixed at responsibility level):**
- Replaces `GrpcCallScopeGraphModule` in integration test graphs that do not exercise DB behavior
- Provides a fixed `Map<Char, Char>` (defined per-test or per-test-class)
- Must use the same qualifier as the production `GrpcCallScopeGraphModule` (TDD Designer ensures
  consistency — this is why getting the qualifier right matters)

**H2 constraint (from DBA, fixed):**
Each test class that uses an H2 in-memory database must use a unique database name in the JDBC URL
to avoid cross-test contamination. `DB_CLOSE_DELAY=-1` is required in all test H2 URLs used with
a connection pool. Bazel test targets must declare `data = ["//db:migrations"]`.

---

## 7. Explicitly Deferred Decisions

These are the TDD Designer's domain. They must be resolved before coding begins on each PR, but
do not need Architect sign-off unless they conflict with a constraint above.

### Deferred to TDD Designer

1. **Repository interface name and method signatures.** The Architect requires an interface;
   what it is called and what methods it exposes is the TDD Designer's decision.

2. **Qualifier for `Map<Char, Char>` in `GrpcCallScopeGraphModule`.** A qualifier is recommended
   (generic type; future collision risk). The TDD Designer chooses the exact form: a custom
   annotation (e.g., `@BracketPairs`) or `@Named("bracketPairs")`. The same qualifier must be
   used in both the production module and `TestGrpcCallScopeGraphModule`.

3. **`BracketsServiceTelemetry` exact name, shape, and API surface.** The Architect names the
   concept `BracketsServiceTelemetry` and specifies it is `@ApplicationScope` and holds `Tracer`,
   `LongCounter`, and `LongHistogram`. The TDD Designer decides the exact class name, whether it
   is a `data class`, a plain class, or an interface; and whether its provider belongs in
   `TelemetryModule` or a new `BracketsTelemetryModule`.

   **Design hint from coordinator:** Consider whether `BracketsServiceTelemetry` should expose
   the relevant portions of the three instrument APIs directly (delegating to the underlying
   instruments) rather than exposing the instruments as fields. Call sites that read
   `telemetry.recordRequest(attrs)` are cleaner than `telemetry.requestCounter.add(1, attrs)`,
   and a delegating wrapper is easier to fake at test time than a struct of OTel objects. This
   is a judgment call — not a mandate — but clean call-site ergonomics are a design goal. Where
   there is no collision between the three instrument APIs, direct delegation is likely the right
   default.

4. **Repository test class placement and naming.** Which package, which test file, what it is called.

5. **`BracketsServiceTelemetry` fake / no-op implementation details.** For endpoint unit tests —
   the TDD Designer writes this.

6. **Fake repository implementation details.** In-memory state structure, constructor signature,
   which test classes use it.

7. **`TestDatabaseModule` exact provider signatures.** Including whether `DatabaseConfig` is
   provided by `TestDatabaseModule` or extracted from a test `ServiceAppConfig`.

8. **`TestGrpcCallScopeGraphModule` shape.** How the fixed `Map<Char, Char>` is configured
   per-test (constructor parameter, `@BindsInstance`, or a static test map).

9. **Error handling shape when no pairs are enabled.** The Architect requires that the service
   returns an error response rather than crashing — the exact error message and gRPC status code
   are the TDD Designer's decision.

10. **H2 test database name uniqueness strategy.** Each test class needs a distinct name.
    A simple convention (`"test-" + testClassName`) is sufficient; the TDD Designer formalizes it.

### Decisions that must be made now (cannot be deferred)

The following are fixed by this document. If the TDD Designer finds a conflict, escalate to the
Architect before committing to an alternative.

- Three-layer boundary (DB adapter, repository, service)
- `BalanceServiceEndpoint` is `@GrpcCallScope` (not `@ApplicationScope`)
- `BalanceServiceEndpoint` depends on `Map<Char, Char>` (injected per-request by
  `GrpcCallScopeGraphModule`) and `BracketsServiceTelemetry` (application-scoped OTel wrapper)
- `BracketPairSource` interface does not exist — removed from design
- `BracketConfigModule` does not exist — removed from design
- `GrpcCallScopeGraphModule` provides `Map<Char, Char>` by calling `repo.loadEnabledPairs()`
  (once per subcomponent creation = once per request)
- Repository interface is required (concrete class is not directly injected into
  `GrpcCallScopeGraphModule`)
- JOOQ types must not leak past the repository interface
- A qualifier on `Map<Char, Char>` is recommended (TDD Designer confirms and chooses form)
- Flyway runs inside the `DataSource` provider (not a separate lifecycle hook)
- `DB_CLOSE_DELAY=-1` in all test H2 JDBC URLs
- `TestGrpcCallScopeGraphModule` replaces `GrpcCallScopeGraphModule` in integration tests
  that do not exercise DB behavior
- `TestDatabaseModule` replaces `DatabaseModule` in the test graph (not `TestApplicationGraphModule`)
- `BracketsServiceTelemetry` is `@ApplicationScope` and is provided from `TelemetryModule` or
  a dedicated module; it holds `Tracer`, `LongCounter`, and `LongHistogram`
