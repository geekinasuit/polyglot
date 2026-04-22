---
date: 2026-04-21
author: claude
tickets: DB-001, KT-005, KT-006, KT-007
status: ready
---

# Plan: DB-backed Bracket Configuration (Kotlin)

Covers the full DB implementation chain: migration tooling (DB-001), JOOQ codegen (KT-005),
database adapter and Dagger wiring (KT-006), and the bracket configuration feature (KT-007).

---

## Goal

Replace the hardcoded `closedParentheses` map in `brackets.kt` with a database-backed
configuration table, using idiomatic Kotlin + JOOQ + HikariCP + Flyway, wired via Dagger,
all built hermetically with Bazel.

---

## Context

- No DB infrastructure exists today; bracket pairs are module-level `val`s in `brackets.kt`
- Dagger DI is already present (KT-002 resolved); `ApplicationGraph` + `ServiceAppConfig` are the entry points
- Maven deps go in `MODULE.bazel` `maven.install` artifacts list (no `pom.xml`)
- The four tickets have a strict dependency chain: DB-001 → KT-005 and KT-006 (parallel) → KT-007

---

## Approach

Five single-responsibility PRs. KT-007's algorithm parameterization is split out as PR 1
because it has no DB dependency and can land independently. DB-001 is PR 2. KT-005 and
KT-006 can be prepared in parallel once DB-001 merges. KT-007's repository and service
wiring land last as PR 5.

```
PR 1 (KT-007 partial)  ──┐
PR 2 (DB-001)          ──┤─→ PR 3 (KT-005) ──┐
                          └─→ PR 4 (KT-006) ──┴─→ PR 5 (KT-007 remainder)
```

PRs 1 and 2 are simple enough to implement directly. PRs 3–5 use a structured
four-agent workflow described below.

### DDL / schema constraints

All DDL must be compatible with three parsers:
- **SQLite** — runtime (local dev, tests)
- **PostgreSQL** — runtime (production)
- **H2** — build time only (JOOQ DDLDatabase uses H2 to parse DDL during codegen)

`INTEGER PRIMARY KEY`, `CHAR(1)`, `BOOLEAN NOT NULL DEFAULT TRUE` are safe across all three.
Avoid: `RETURNING`, `SERIAL`, stored procedures, `ADD COLUMN ... AFTER`, `GENERATED ALWAYS`.

---

## Agent Workflow (PRs 3–5)

PRs 3–5 are implemented using a four-agent structure: a DBA who designs the schema and
data infrastructure, an Architect who designs the Kotlin API surfaces and Dagger graph on
top of that, a TDD Designer who writes tests against those surfaces (test-first), and a Coder
who implements code to make the tests pass. The coordinating agent (the session running
these subagents) breaks deadlocks.

### Workflow sequence

```
1. DBA agent + Architect agent — collaborative design phase (see note below)
     produces: schema design doc (DBA) + API/Dagger design doc (Architect)
2. For each PR (3, 4, 5), in order:
     a. TDD Designer reads both design docs → designs types/interfaces/APIs → writes tests (red)
     b. Coder reads both design docs + TDD Designer files → implements (green)
     c. Both consult DBA (data questions) or Architect (API questions) for ambiguities
     d. Coordinator breaks any deadlock
```

**Note on Phase 1 sequencing for this project:** In general the Architect drives the need
and the DBA negotiates the schema to serve it — a round-trip, not a handoff. Here the
sequencing is reversed (DBA first, then Architect) because we are retrofitting a DB
requirement onto an existing application where the schema is the primary new driver.
This is the exception; in a greenfield feature the Architect would run first.

### Rules

- **DBA** is the authority on schema, migration tooling, seeding, and DB portability.
  Architect, TDD Designer, and Coder escalate all data/schema questions here first.
- **Architect** is the authority on strategic API shape, Dagger module structure, and
  long-term fit. TDD Designer and Coder escalate decisions that might conflict with
  strategic intent — not every tactical choice, only genuine tensions.
- **TDD Designer** translates the Architect's strategic constraints into concrete types,
  interfaces, and APIs — then expresses those decisions as tests. Has full design authority
  over tactical details the Architect left open. Tests must compile and fail before the
  Coder touches anything.
- **Coder** has full creative latitude in the private implementation space: private types,
  private functions, local names, internal helpers, utility extraction. May propose changes
  to the public contract — implementation insight often reveals things the designer couldn't
  anticipate — but as a collaborative request to the TDD Designer, not a unilateral edit.
  TDD Designer agrees → proceed. Disagree → escalate to Architect.
- **Coordinator** resolves genuine deadlocks only — after DBA and Architect have both
  been consulted and the impasse remains.

---

### DBA Agent Prompt

> **Constraints (apply before doing any other work):**
> - **Compressed format:** Read `/opt/geekinasuit/agents/internal/workflows/compressed-format.compressed.md`
>   for the conventions you must follow when producing `.compressed.md` companion files. This
>   applies to your Markdown output documents only — `.sql` files and other non-prose assets
>   have no compressed form. You must produce both `.md` and `.compressed.md` for your research doc.
> - **Shell safety:** Never pass non-trivial text inline to shell commands — backticks, `---`, and
>   `*` get misinterpreted by shell parsers and security hooks. Write content to a temp file first,
>   then reference by path. Use unique names: `/tmp/polyglot-<purpose>-<context>.txt`.
>
> **Read these files before doing any design work:**
>
> - `thoughts/shared/plans/2026-04-21-db-kotlin-feature.md` — the full implementation plan
> - `thoughts/shared/tickets/DB-001-schema-migrations-setup.md`
> - `thoughts/shared/tickets/KT-006-database-adapter.md`
> - `thoughts/shared/tickets/KT-007-bracket-config-feature.md`
>
> **Your role:** You are the DBA and data architect for the DB feature. Your domain covers schema
> design, migration tooling, seeding, and test data strategy. The Architect agent will read your
> output and build the Kotlin API design on top of it — so be precise about anything that
> constrains the application layer (e.g. column types, nullability, indexing decisions,
> migration sequencing).
>
> **Design and document the following, with rationale for each decision:**
>
> 1. **Schema:** Finalize the `bracket_pair` DDL. Verify each column type and constraint is
>    portable across SQLite (runtime), PostgreSQL (runtime), and H2 (build-time, used by JOOQ
>    DDLDatabase). Document why each choice is safe across all three.
>
> 2. **Migration filename convention:** Validate whether Flyway accepts dbmate-style timestamp
>    filenames (`20260421000000_create_bracket_pair.sql`) when configured with
>    `sqlMigrationPrefix=""` and `sqlMigrationSeparator="_"`. Produce a validated conclusion
>    with evidence. If it fails, document the fallback (separate `flyway/` and `dbmate/`
>    subdirectories, same SQL content, different names) and why.
>
> 3. **Seed data strategy:** Should the three standard pairs be inserted in the migration file
>    itself, or via a separate seed mechanism? Consider: can tests reset or override seed data
>    cleanly with an in-memory SQLite DB? What are the tradeoffs for reproducibility?
>
> 4. **Future schema evolution:** What changes to `bracket_pair` are plausible over time
>    (ordering, categories, locale, per-user config)? Note what the current schema would need
>    to add to support each, and confirm the initial design does not foreclose reasonable paths.
>    Do not over-engineer — just avoid unnecessary dead ends.
>
> 5. **dbmate compatibility:** Confirm the `-- migrate:up` marker (with no `-- migrate:down`
>    section) works correctly for both dbmate and Flyway on the same file.
>
> 6. **Test data strategy:** How do integration tests get a clean, reproducible DB state?
>    Propose the pattern (e.g. in-memory SQLite per test class, schema applied via Flyway,
>    seed data inserted in test setup). No shared mutable state between tests.
>
> 7. **Bazel filegroup:** Confirm `//db:migrations` with `glob(["migrations/*.sql"])` is
>    sufficient. Note any edge cases (e.g. ordering, subdirectory layout).
>
> **Output:** Write your findings to:
> `thoughts/shared/research/2026-04-21-db-schema-design.md`
>
> Also produce a compressed companion:
> `thoughts/shared/research/2026-04-21-db-schema-design.compressed.md`
>
> Mark each decision as **fixed** (Architect and implementers must not deviate) or
> **provisional** (open to refinement by the Architect or implementers with good reason).

---

### Architect Agent Prompt

> **Constraints (apply before doing any other work):**
> - **Compressed format:** Read `/opt/geekinasuit/agents/internal/workflows/compressed-format.compressed.md`
>   for the conventions you must follow when producing `.compressed.md` companion files. You must
>   produce both `.md` and `.compressed.md` forms for your output document.
> - **Shell safety:** Never pass non-trivial text inline to shell commands — backticks, `---`, and
>   `*` get misinterpreted by shell parsers and security hooks. Write content to a temp file first,
>   then reference by path. Use unique names: `/tmp/polyglot-<purpose>-<context>.txt`.
>
> **Read these files before doing any design work:**
>
> - `thoughts/shared/research/2026-04-21-db-schema-design.md` — DBA output; read this first
> - `thoughts/shared/plans/2026-04-21-db-kotlin-feature.md` — the full implementation plan
> - `thoughts/shared/tickets/KT-005-jooq-codegen.md`
> - `thoughts/shared/tickets/KT-006-database-adapter.md`
> - `thoughts/shared/tickets/KT-007-bracket-config-feature.md`
> - `kotlin/src/main/kotlin/bracketskt/brackets.kt`
> - `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BalanceServiceEndpoint.kt`
> - `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/ApplicationGraph.kt`
> - `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/ApplicationGraphModule.kt`
> - `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/GrpcHandlersModule.kt`
> - `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/config/Config.kt`
> - `kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/ApplicationGraphTest.kt`
> - `kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/TestApplicationGraphModule.kt`
> - `kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/TestApplicationGraph.kt`
>
> **Your role:** You are the Architect for the database feature (PRs 3–5). Your job is to
> design the structure of the Kotlin implementation at a CRC-card level or coarser —
> subsystems, responsibilities, high-level collaborations and flow. You work from the DBA's
> schema design and the existing codebase. The TDD Designer will fill in the tactical details
> (exact method signatures, error contracts, type names) within the boundaries you establish.
> You are setting constraints and intent, not writing a complete spec.
>
> **Design and document the following, with rationale for each decision:**
>
> 1. **Layer boundaries.** Which layers exist (DB adapter, repository, service)? What are
>    the responsibilities of each? What collaborates with what? Should `BalanceServiceEndpoint`
>    depend on a repository abstraction or something narrower — and what is the testability
>    argument for your choice? Describe at responsibility level; the TDD Designer will name
>    the interfaces and define the signatures.
>
> 2. **Dagger module decomposition.** Name the modules and describe what each is responsible
>    for providing. Consider: should `DataSource`, Flyway lifecycle, and `DSLContext` be
>    separate providers so tests can override individual pieces? How does `DatabaseConfig`
>    flow from `ServiceAppConfig` into the graph? Describe the topology; the TDD Designer
>    will define the exact provider signatures.
>
> 3. **Repository responsibility.** What is the repository responsible for, and what does it
>    collaborate with? Should there be an interface separating the repository from its
>    consumers — and why? Consider: what would change if JOOQ were swapped for raw JDBC, or
>    if the repository became async? Design the responsibility boundary so those changes
>    don't propagate into the service layer.
>
> 4. **Startup and lifecycle.** When does Flyway run? Who is responsible for ensuring
>    migrations are applied before the `DSLContext` is used? Describe the sequencing
>    as Dagger sees it.
>
> 5. **Coroutine readiness.** JOOQ is blocking. The service uses `StreamObserver` callbacks,
>    not coroutines. Describe how the repository responsibility boundary should be drawn so
>    a future async migration is contained there and does not ripple into callers.
>
> 6. **Test surfaces.** For each layer, describe: what is tested at unit vs. integration
>    level? What kinds of test doubles are needed? Which Dagger modules would a test graph
>    replace? Keep at the level of "a fake in-memory implementation of the repository
>    interface" — the TDD Designer will write the actual fake.
>
> 7. **Explicitly deferred decisions.** List what you are deliberately leaving open for the
>    TDD Designer. This is not a weakness — it is the handoff boundary. Be clear about which
>    decisions must be made now to avoid expensive rework, and which can be made later.
>
> **Output:** Write your findings to:
> `thoughts/shared/research/2026-04-21-db-feature-architecture.md`
>
> Also produce a compressed companion:
> `thoughts/shared/research/2026-04-21-db-feature-architecture.compressed.md`
>
> The design doc is the primary reference for the TDD Designer and Coder. Write at
> responsibility and constraint level — not at method-signature level. Be explicit about
> what is fixed (TDD Designer must not deviate) versus what is deliberately open
> (TDD Designer's domain to decide).

---

### TDD Designer Agent Prompt

> **Constraints (apply before doing any other work):**
> - **VCS:** This repo uses `jj` (jujutsu). Never run `git` commands.
> - **Build:** Use `bazel` (not `bazelisk`) for all build and test commands.
> - **Style:** Run `ktfmt` on every `.kt` file you write or modify before considering your work done.
> - **Shell safety:** Never pass non-trivial text inline to shell commands — backticks, `---`, and
>   `*` get misinterpreted by shell parsers and security hooks. Write content to a temp file first,
>   then reference by path. Use unique names: `/tmp/polyglot-<purpose>-<context>.txt`.
>
> **Read these files before writing anything:**
>
> - `thoughts/shared/research/2026-04-21-db-feature-architecture.md` (or `.compressed.md`)
>   — the Architect's design doc; this is your primary constraint, not a script to follow
> - `thoughts/shared/research/2026-04-21-db-schema-design.md` — the DBA's schema design
> - `thoughts/shared/plans/2026-04-21-db-kotlin-feature.md` — implementation plan
> - `kotlin/src/test/kotlin/bracketskt/BracketsTest.kt` — existing test style
> - `kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/ApplicationGraphTest.kt`
> - `kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/TestApplicationGraphModule.kt`
> - `kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/BUILD.bazel`
> - `kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/FakeTelemetryModule.kt`
>
> **Your role:** You are the TDD Designer for the database feature (PRs 3–5). You are not
> primarily a test-writer — you are a tactical designer working test-first. The Architect has
> established the strategic boundaries: which layers exist, where the seams are, what the
> module decomposition should be. Within those boundaries, the detailed design is yours:
> interface names, method signatures, error contracts, type shapes, test doubles. You
> express your design decisions as tests. The Coder implements bodies; you define shapes.
>
> **Design responsibilities:**
> - Where the Architect has left decisions open (and they will have deliberately left many),
>   make those decisions yourself. The Architect's doc describes constraints and intent; you
>   translate those into concrete Kotlin types and APIs.
> - Define interfaces, data classes, and sealed types as needed. These definitions are your
>   design output — not placeholders for someone else to rethink.
> - Write tests that express the contract of each type and interface you define. Tests must
>   compile. Tests must fail before the Coder writes any implementation.
> - Do not write implementation logic. A `TODO()` body or an `abstract` method is design;
>   a real implementation body is the Coder's domain.
>
> **Design and test principles:**
> - Prefer narrow interfaces over wide ones. If `BalanceServiceEndpoint` only needs one method
>   from the repository, define an interface with one method, not the whole repository.
> - Prefer value types (data classes, sealed classes) at boundaries over primitive maps or
>   strings where the type carries meaning.
> - Use fakes over mocks. If a test needs a fake repository, write one — a few lines of
>   in-memory state. Mocks couple tests to implementation details.
> - Follow existing test patterns: JUnit 4 (`@Test`), Truth assertions.
> - If a design decision feels like it might conflict with the Architect's intent — not just
>   fill a gap, but potentially push against a stated constraint — check with the Architect
>   before committing to it. Don't second-guess on small tactical choices; escalate genuine
>   strategic tensions.
>
> **Cover at minimum:**
>
> 1. The repository contract (whatever interface/class shape you decide on):
>    - Enabled pairs loaded correctly from an in-memory SQLite DB (with migration applied)
>    - Disabled pairs ignored
>    - Empty DB → empty result, no crash
>    - close→open mapping direction correct
>
> 2. The service layer contract:
>    - Known pair set → correct balance result (supply pairs directly, no DB involved)
>    - Empty pair set → error response, not a crash
>    - Unbalanced input → correct error shape and message
>    - Characters not in the pair set treated as plain text
>
> 3. Dagger wiring:
>    - Test graph builds with in-memory SQLite `DatabaseConfig`
>    - Repository (or its interface) is injectable from test graph
>    - Disabling a pair in the test DB causes that pair's characters to be accepted as plain text end-to-end
>
> **Placement:** Write types and tests in appropriate packages under `kotlin/src/main/kotlin/...`
> (for type/interface definitions) and `kotlin/src/test/kotlin/...` (for tests and fakes). Add
> Bazel `kt_jvm_test` targets in the relevant `BUILD.bazel` files following existing patterns.
>
> **When you are done:** Tell the Coder precisely: which files you have written, which
> interfaces and types you have defined, which method bodies are `TODO()` for them to fill,
> and any decisions you made that deviate from or extend the Architect's doc (so the Coder
> understands the full contract). The Coder must not change any type or interface definition
> you have produced without discussing it with you first. If you and the Coder cannot agree,
> escalate to the Architect.

---

### Coder Agent Prompt

> **Constraints (apply before doing any other work):**
> - **VCS:** This repo uses `jj` (jujutsu). Never run `git` commands.
> - **Build:** Use `bazel` (not `bazelisk`) for all build and test commands.
> - **Style:** Run `ktfmt` on every `.kt` file you write or modify before considering your work done.
> - **Shell safety:** Never pass non-trivial text inline to shell commands — backticks, `---`, and
>   `*` get misinterpreted by shell parsers and security hooks. Write content to a temp file first,
>   then reference by path. Use unique names: `/tmp/polyglot-<purpose>-<context>.txt`.
>
> **Read these files before writing any implementation:**
>
> - `thoughts/shared/research/2026-04-21-db-feature-architecture.md` (or `.compressed.md`)
>   — the Architect's design doc; this is your primary reference
> - `thoughts/shared/plans/2026-04-21-db-kotlin-feature.md` — implementation plan
> - All type, interface, and test files written by the TDD Designer (they will tell you which files these are)
> - `kotlin/src/main/kotlin/bracketskt/brackets.kt`
> - `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BalanceServiceEndpoint.kt`
> - `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/ApplicationGraph.kt`
> - `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/config/Config.kt`
> - `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BUILD.bazel`
> - `kotlin/BUILD.bazel`
> - `MODULE.bazel`
>
> **Your role:** You are the Coder for the database feature (PRs 3–5). By the time you start,
> the TDD Designer will have laid out the skeleton of the service changes: interfaces defined,
> classes declared, module structure visible, method bodies stubbed with `TODO()`. The public
> contract is decided. Everything below the public surface is yours: private types, private
> functions, local names, internal helpers, utility extraction, how to structure internal state,
> which third-party library calls to make. You have full creative latitude in that space.
> If you discover that your implementation needs something that doesn't exist yet — a private
> helper type, an internal utility, an extracted function — create it. You don't need to ask.
>
> **Rules:**
> - Do not change public type definitions, interface signatures, class names, or public method
>   signatures produced by the TDD Designer without their agreement. You may propose changes —
>   implementation insight often reveals things the designer couldn't anticipate — but frame
>   them as collaborative requests, not unilateral edits. If you and the TDD Designer agree,
>   make the change. If you can't reach agreement, escalate to the Architect.
> - If a test is impossible to satisfy without changing the public API contract, that is a
>   reason to propose a change — bring it to the TDD Designer with a clear explanation.
>   Do not silently modify the test or the interface.
> - Run `ktfmt` on every `.kt` file you write or modify before considering any PR ready.
>   (`ktfmt <file>` or `ktfmt $(find kotlin -name "*.kt")`).
> - Follow existing Bazel patterns: `kt_jvm_library` for libraries, `java_binary` +
>   `runtime_deps` for binaries, `associates = [...]` for internal member access in tests.
>   Add new maven deps to `MODULE.bazel` `maven.install` artifacts.
> - Keep H2 out of service runtime deps — it is build-time only (used by JOOQ DDLDatabase).
> - Flyway must use `filesystem:` location (not classpath scanning) for Bazel runfiles
>   compatibility. See the plan for the `RUNFILES_DIR` resolution pattern.
> - The JOOQ `genrule` `outs` list must be exhaustive. Run codegen locally once, observe
>   all generated files, then list them all. The build will fail if any are missing.
>
> **For each PR (3, 4, 5 in order):**
> 1. Read the Architect's design and the TDD Designer's files for that PR's scope
> 2. Implement until `bazel test //kotlin/...` passes for that PR's scope
> 3. Run ktfmt on all changed files
> 4. Report what you implemented and any deviations from the design (even minor ones)
>
> **Escalation:** If you and the TDD Designer reach an impasse on a public contract question,
> bring the specific conflict to the Architect with a clear question. Do not resolve it
> unilaterally. The Coordinator can break deadlocks if the Architect cannot resolve them.

---

## Steps

### PR 1 — KT-007 (partial): Algorithm parameterization

**Branch:** `kt-007-algorithm-param`  
**Prereqs:** none — fully independent of all DB work  
**Agents:** direct implementation (no three-agent workflow needed)

Add an optional `pairs` parameter to `balancedBrackets` with the existing hardcoded map
as the default. Existing call sites and all existing tests continue to pass without
modification.

```kotlin
// brackets.kt — existing module-level vals stay unchanged
val closedParentheses = mapOf(')' to '(', '}' to '{', ']' to '[')
val openParentheses = closedParentheses.values.toSet()

// New primary form — pairs maps close→open (same direction as existing map)
fun balancedBrackets(text: String, pairs: Map<Char, Char> = closedParentheses) {
    val opens = pairs.values.toSet()
    // replace references to `closedParentheses` and `openParentheses` with `pairs` and `opens`
    ...
}
```

No new Bazel targets, no new deps, no config changes. Purely an algorithm API improvement.

**Acceptance:**
- `balancedBrackets` accepts an optional pair map
- All existing `BracketsTest` cases pass without modification
- `bazel test //kotlin/...` passes

---

### PR 2 — DB-001: Migration tooling and initial schema

**Branch:** `db-001-schema-migrations`  
**Prereqs:** none — fully independent  
**Agents:** direct implementation (no three-agent workflow needed)

1. Create `db/migrations/` at repo root.

2. Validate filename convention (required before writing migrations):
   - Configure Flyway with `sqlMigrationPrefix=""` and `sqlMigrationSeparator="_"`.
   - Write a small standalone test (scratch script is fine) to confirm Flyway accepts
     `20260421000000_create_bracket_pair.sql` as version `20260421000000`.
   - If Flyway rejects the empty-prefix config, fall back to separate subdirs
     (`flyway/` with `V1__create_bracket_pair.sql`, `dbmate/` with timestamp name,
     same SQL in both); document the fallback in `db/README.md`.

3. Write `db/migrations/20260421000000_create_bracket_pair.sql` (or `V1__...` if fallback):
   ```sql
   -- migrate:up
   CREATE TABLE bracket_pair (
       id         INTEGER  PRIMARY KEY,
       open_char  CHAR(1)  NOT NULL,
       close_char CHAR(1)  NOT NULL,
       enabled    BOOLEAN  NOT NULL DEFAULT TRUE
   );

   INSERT INTO bracket_pair (id, open_char, close_char) VALUES (1, '(', ')');
   INSERT INTO bracket_pair (id, open_char, close_char) VALUES (2, '[', ']');
   INSERT INTO bracket_pair (id, open_char, close_char) VALUES (3, '{', '}');
   ```

4. Write `db/README.md` documenting:
   - Dual-tool strategy (Flyway for JVM, dbmate for non-JVM)
   - File format convention (`-- migrate:up`, no `-- migrate:down`)
   - Filename convention (resolved from validation above)
   - SQLite/PostgreSQL/H2 portability constraints

5. Add Bazel `filegroup` to `db/BUILD.bazel`:
   ```python
   filegroup(
       name = "migrations",
       srcs = glob(["migrations/*.sql"]),
       visibility = ["//visibility:public"],
   )
   ```

6. Validate manually: run dbmate against the migration file and verify the table is created.

**Acceptance:** `db/migrations/` exists, migration file validated against dbmate,
`//db:migrations` filegroup defined, `db/README.md` written.

---

### Pre-PR 3 — Design phase (DBA + Architect)

**Agents:** DBA agent, then Architect agent (see prompts above)  
**Prereqs:** DB-001 merged (both agents need the actual schema)  
**Output:** `thoughts/shared/research/2026-04-21-db-schema-design.md` + `.compressed.md` (DBA)  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`thoughts/shared/research/2026-04-21-db-feature-architecture.md` + `.compressed.md` (Architect)

For this project, run the DBA agent first (the schema is the primary new driver), then the
Architect agent (reads the DBA's output). In the general pattern these two roles work
collaboratively and iteratively rather than sequentially — see the workflow doc for context.

Do not proceed to PRs 3–5 until both design docs exist and have been reviewed by the
session coordinator (the human or coordinating agent).

---

### PR 3 — KT-005: JOOQ codegen (Bazel genrule)

**Branch:** `kt-005-jooq-codegen`  
**Prereq:** DB-001 merged  
**Agents:** Tester then Coder (see prompts above); Architect available for consultation

**New maven artifacts in `MODULE.bazel`:**
- `org.jooq:jooq:3.19.x` (latest stable)
- `org.jooq:jooq-kotlin:3.19.x`
- `org.jooq:jooq-meta-extensions:3.19.x` (codegen only — DDLDatabase)
- `org.jooq:jooq-codegen:3.19.x` (codegen only — GenerationTool)
- `com.h2database:h2:2.x.x` (build-time only — DDLDatabase uses H2 internally to parse DDL)

**Codegen runner:**

Write a small Java/Kotlin main class (e.g. `JooqCodegenRunner`) that takes the DDL file
path and output directory as CLI arguments, then calls `GenerationTool.generate(...)`
programmatically with:
- `Database`: `org.jooq.meta.extensions.ddl.DDLDatabase`
- Input schema: the DDL path arg
- Target package: `com.geekinasuit.polyglot.brackets.db.jooq`
- Target directory: the output dir arg

Wire into Bazel:
```python
# Runner binary (deps include jooq-codegen, jooq-meta-extensions, h2 — build time only)
java_binary(
    name = "jooq_codegen_runner",
    main_class = "...",
    runtime_deps = [":jooq_codegen_runner_lib"],
)

# genrule: migration SQL → generated Java sources
genrule(
    name = "jooq_generated_srcs",
    srcs = ["//db:migrations"],
    outs = [
        "com/geekinasuit/polyglot/brackets/db/jooq/Tables.java",
        "com/geekinasuit/polyglot/brackets/db/jooq/tables/BracketPair.java",
        "com/geekinasuit/polyglot/brackets/db/jooq/tables/records/BracketPairRecord.java",
        # ... run codegen locally once to discover the full outs list
    ],
    cmd = "$(location :jooq_codegen_runner) $(locations //db:migrations) $(@D)",
    tools = [":jooq_codegen_runner"],
)

# Library wrapping generated sources; service code depends on this
kt_jvm_library(
    name = "jooq_db_lib",
    srcs = [":jooq_generated_srcs"],
    deps = ["@maven//:org_jooq_jooq"],
    visibility = ["//visibility:public"],
)
```

**Notes:**
- JOOQ codegen produces Java sources (`.java`), not Kotlin — this is expected and standard
- `jooq-kotlin` is a runtime extension (coroutine-friendly `fetchOne`, `map`, etc.); it adds
  no generated `.kt` files
- H2 is a build-time-only dep of the runner; it must not appear in service `runtime_deps`
- The `outs` list must be exhaustive — run codegen locally once to discover all output files
  before finalizing the genrule
- Add all generated paths under `kotlin/` to `.gitignore` (consistent with proto codegen policy)

**Acceptance:** `bazel build //kotlin/...` passes; `Tables`, `BracketPair`,
`BracketPairRecord` classes available; generated files not committed to git.

---

### PR 4 — KT-006: Database adapter, Flyway, Dagger wiring

**Branch:** `kt-006-database-adapter`  
**Prereq:** DB-001 merged (KT-005 not required — can be prepared in parallel with PR 3)  
**Agents:** Tester then Coder (see prompts above); Architect available for consultation

**New maven artifacts in `MODULE.bazel`:**
- `com.zaxxer:HikariCP:5.x.x`
- `org.xerial:sqlite-jdbc:3.x.x`
- `org.postgresql:postgresql:42.x.x`
- `org.flywaydb:flyway-core:10.x.x`
- `org.flywaydb:flyway-database-sqlite:10.x.x` (SQLite dialect support for Flyway 10+)

**`Config.kt` changes:**

Add `DatabaseConfig` and wire into `ServiceAppConfig`:
```kotlin
data class DatabaseConfig(
    val url: String = "jdbc:sqlite::memory:",
    val driver: String = defaultDriver(url),
    val maxPoolSize: Int = 5,
)

fun defaultDriver(url: String): String = when {
    url.startsWith("jdbc:sqlite") -> "org.sqlite.JDBC"
    url.startsWith("jdbc:postgresql") -> "org.postgresql.Driver"
    else -> error("Unsupported JDBC URL: $url")
}

data class ServiceAppConfig(
    val service: ServiceConfig = ServiceConfig(),
    val telemetry: TelemetryConfig = TelemetryConfig(),
    val database: DatabaseConfig = DatabaseConfig(),
)
```

**`service/application.yml` update:**
```yaml
service:
  host: localhost
  port: 8888
database:
  url: "jdbc:sqlite::memory:"
  # driver and max-pool-size use defaults
```

**`DatabaseModule` Dagger module** (shape may be refined by Architect design doc):
```kotlin
@Module
object DatabaseModule {
    @Provides @ApplicationScope
    fun dataSource(config: DatabaseConfig): DataSource {
        // 1. Resolve migrations path from runfiles (see note below)
        // 2. Build HikariCP pool from config
        // 3. Run Flyway with filesystem: location pointing to resolved path
        // 4. Return pool
    }

    @Provides @ApplicationScope
    fun dslContext(dataSource: DataSource, config: DatabaseConfig): DSLContext {
        val dialect = if (config.url.contains("sqlite")) SQLDialect.SQLITE
                      else SQLDialect.POSTGRES
        return DSL.using(dataSource, dialect)
    }
}
```

**Flyway runfiles path — key gotcha:**

Flyway must use `filesystem:` location, not classpath scanning. Classpath scanning does not
find files in Bazel runfiles trees. Resolve the path at runtime:
```kotlin
val migrationsPath = Paths.get(
    System.getenv("RUNFILES_DIR") ?: ".",
    "_main/db/migrations"
).toAbsolutePath().toString()

Flyway.configure()
    .locations("filesystem:$migrationsPath")
    .dataSource(dataSource)
    .load()
    .migrate()
```

**Wire into `ApplicationGraph`:**
- Add `DatabaseModule` to `@Component(modules = [...])` in `ApplicationGraph`
- `DatabaseConfig` is available via `ServiceAppConfig` — extract via a `@Provides` unwrapping
  method (exact approach deferred to Architect design)

**Bazel wiring:**
- Add `//db:migrations` to `data` of `brackets_service` `java_binary` in `kotlin/BUILD.bazel`
- Add new maven runtime deps to `service_lib` in `service/BUILD.bazel`

**Tests:**
- Include `DatabaseModule` in `TestApplicationGraph` with in-memory SQLite config
- Existing `graphBuilds` and `serverProvides` tests must continue to pass
- Verify Flyway migration runs without error on the test graph

**Acceptance:** `bazel test //kotlin/...` passes; service starts; Flyway migration applies
on startup; in-memory SQLite works in tests.

---

### PR 5 — KT-007 (remainder): Repository, service wiring, tests

**Branch:** `kt-007-bracket-config`  
**Prereqs:** PR 1 (algorithm param), KT-005, KT-006 all merged  
**Agents:** Tester then Coder (see prompts above); Architect available for consultation

**`BracketPairRepository`** in new package `com.geekinasuit.polyglot.brackets.db`
(exact interface shape from Architect design doc):
```kotlin
@ApplicationScope
class BracketPairRepository @Inject constructor(private val dsl: DSLContext) {
    fun loadEnabledPairs(): Map<Char, Char> =
        dsl.selectFrom(Tables.BRACKET_PAIR)
           .where(Tables.BRACKET_PAIR.ENABLED.isTrue)
           .fetch()
           .associate { row ->
               row.get(Tables.BRACKET_PAIR.CLOSE_CHAR).single() to
               row.get(Tables.BRACKET_PAIR.OPEN_CHAR).single()
           }
}
```

**Dagger wiring:**

Add a module providing the pair map at `@ApplicationScope`:
```kotlin
@Module
object BracketConfigModule {
    @Provides @ApplicationScope
    fun bracketPairs(repo: BracketPairRepository): Map<Char, Char> =
        repo.loadEnabledPairs()
}
```

Install `BracketConfigModule` in `ApplicationGraph`. If no pairs are enabled, the service
returns an error response rather than crashing — check for empty map in
`BalanceServiceEndpoint`.

**`BalanceServiceEndpoint` update:**

Inject `Map<Char, Char>` and pass it through:
```kotlin
class BalanceServiceEndpoint @Inject constructor(
    otel: OpenTelemetry,
    private val bracketPairs: Map<Char, Char>,
) : BalanceBracketsGrpc.AsyncService { ... }
```

**Tests:**
- `BracketPairRepositoryTest`: in-memory SQLite `DSLContext`, apply migration via Flyway,
  insert test rows, assert `loadEnabledPairs()` returns correct close→open map
- `BalanceServiceEndpointTest`: supply a known pair map as constructor arg (no DB needed)
- Disable-pair test: insert rows with `enabled=false` for `()`, assert those characters
  are treated as plain text by the algorithm
- `ApplicationGraphTest` updates: supply `DatabaseConfig` with in-memory SQLite so the
  full graph (now including `BracketConfigModule`) builds cleanly

**Bazel wiring:**
- New `kt_jvm_library` target for the `db` package (depends on `jooq_db_lib` from KT-005)
- Add it to `service_lib` deps in `service/BUILD.bazel`

**Acceptance:** `bazel test //kotlin/...` passes; bracket pairs are loaded from DB;
disabling a pair in the test DB causes the algorithm to treat those characters as plain text.

---

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Flyway rejects empty-prefix + single-`_` separator | Validate first (step 2 of PR 2); fallback to separate filename sets fully documented |
| JOOQ genrule `outs` must be listed exhaustively | Run codegen locally once before finalizing; discover all output files |
| Flyway classpath scan misses Bazel runfiles | Use `filesystem:` location with `RUNFILES_DIR` resolution (see PR 4 detail) |
| H2 DDL compatibility | Stick to safe subset; test locally |
| Dagger raw `Map<Char, Char>` binding collision | Use `@Named` qualifier or a wrapper type if Dagger complains |
| Architect/Tester/Coder deadlock | Coordinator breaks deadlock; Architect is authority on API shape |

---

## Acceptance Criteria (Summary)

- [ ] PR 1: `balancedBrackets` accepts optional pair map; all existing tests pass unchanged
- [ ] Architect doc: `thoughts/shared/research/2026-04-21-db-feature-architecture.md` exists and reviewed
- [ ] PR 2: `db/migrations/` + validated migration file + `//db:migrations` filegroup + `db/README.md`
- [ ] PR 3: JOOQ genrule produces `Tables`, `BracketPair`, `BracketPairRecord`; `.gitignore`d; `bazel build` passes
- [ ] PR 4: `DatabaseConfig` in `Config.kt`; `service/application.yml` default SQLite; `DatabaseModule` provides `DataSource` + `DSLContext`; Flyway runs on startup; `bazel test` passes
- [ ] PR 5: `BracketPairRepository` loads enabled pairs; `BalanceServiceEndpoint` uses injected map; all tests pass; `bazel test //kotlin/...` passes
