<!--COMPRESSED v1; source:2026-04-21-db-kotlin-feature.md-->
§META
date:2026-04-21 author:claude tickets:DB-001,KT-005,KT-006,KT-007 status:ready

§ABBREV
kt=kotlin db=db/migrations mm=MODULE.bazel mig=20260421000000_create_bracket_pair.sql
arch=2026-04-21-db-feature-architecture

§GOAL
Replace hardcoded closedParentheses map in brackets.kt with DB-backed config table.
Stack: JOOQ+HikariCP+Flyway+Dagger; Bazel-hermetic; five single-responsibility PRs.

§CONTEXT
no DB infra today; brackets.kt has module-level val closedParentheses
Dagger DI present (KT-002 resolved); ApplicationGraph+ServiceAppConfig = entry points
maven deps → mm maven.install (no pom.xml)
dep chain: DB-001 → KT-005||KT-006 (parallel) → KT-007 remainder

§APPROACH
PR-1(KT-007 partial) ──┐
PR-2(DB-001)         ──┤─→ PR-3(KT-005) ──┐
                        └─→ PR-4(KT-006) ──┴─→ PR-5(KT-007 remainder)
PR-1+PR-2: direct impl (no agent workflow)
PR-3–PR-5: four-agent workflow (DBA↔Architect[design phase]→TDD Designer→Coder)
  NOTE this project runs DBA first (schema is primary driver); general pattern is Architect-led round-trip

§CONSTRAINTS
DDL must satisfy THREE parsers:
  SQLite(runtime local/test) | PostgreSQL(runtime prod) | H2(build-time; DDLDatabase uses H2)
safe: INTEGER PRIMARY KEY; CHAR(1); BOOLEAN NOT NULL DEFAULT TRUE
avoid: RETURNING; SERIAL; stored procs; ADD COLUMN...AFTER; GENERATED ALWAYS

§AGENTS

WORKFLOW SEQUENCE (PRs 3–5):
  1. DBA + Architect [collaborative; this project runs DBA first as exception — schema is primary driver]
       DBA → db-schema-design.md; Architect [reads DBA output] → $arch.md
  2. For each PR(3,4,5) in order:
       a. TDD Designer → designs types+interfaces+APIs; writes tests (red); consults DBA(data)|Architect(strategic tensions)
       b. Coder → fills in bodies to make tests pass; consults DBA(data)|Architect(API conflicts)
       c. Coordinator breaks deadlocks if DBA+Architect can't resolve

AGENT RULES:
  DBA: authority on schema, migrations, seeding, DB portability; all data questions route here first
  Architect: authority on strategic API shape, Dagger graph, long-term fit; escalate strategic tensions (not every tactical choice)
  TDD Designer: translates Architect's strategic constraints into concrete types+interfaces+APIs via tests
    design authority over tactical details Architect left open; tests compile+fail; no impl bodies
    Coder must not change TDD Designer's type/interface/method contracts without TDD Designer agreement; escalate to Architect if unresolved
  Coder: full latitude on private impl (private types, fns, names, helpers); NO unilateral public shape changes;
    negotiate public changes with TDD Designer first, then Architect; ktfmt all .kt
  Coordinator: breaks deadlocks only — after both DBA+Architect consulted

CONSTRAINTS BY ROLE:
  DBA + Architect:
    compressed-format: read /opt/geekinasuit/agents/internal/workflows/compressed-format.compressed.md
      applies to .md output docs only — .sql and other non-prose files have NO compressed form
      must produce both .md + .compressed.md for research output doc
    shell-safety: never inline non-trivial text in shell cmds; write to /tmp/polyglot-<purpose>-<ctx>.txt first
  TDD Designer + Coder:
    VCS: jj only; never run git
    build: bazel (not bazelisk)
    style: ktfmt all modified .kt files before work is done
    shell-safety: same as above

DBA PROMPT:
  Constraints: compressed-format + shell-safety (see above)
  Read first:
    thoughts/shared/tickets/DB-001-schema-migrations-setup.md
    thoughts/shared/tickets/KT-006-database-adapter.md
    thoughts/shared/tickets/KT-007-bracket-config-feature.md
    thoughts/shared/plans/2026-04-21-db-kotlin-feature.md
  Role: DBA + data architect. Design schema, migrations, seeding, tooling.
  Design and document:
    1. Schema: finalize bracket_pair DDL; verify portability across SQLite/PostgreSQL/H2;
       rationale for each column type/constraint
    2. Migration filename convention: validate Flyway empty-prefix+single-_ approach;
       produce validated conclusion (works or fallback) with evidence
    3. Seed data strategy: INSERT in migration vs. separate mechanism;
       can tests reset/override seed data cleanly with in-memory SQLite?
    4. Future schema evolution: what changes are plausible? does initial schema foreclose any?
       don't over-engineer — just avoid unnecessary dead ends
    5. dbmate compatibility: confirm -- migrate:up (no down) works for both tools on same file
    6. Test data strategy: in-memory SQLite per test; schema+seed via Flyway; no shared state
    7. Bazel filegroup: //db:migrations glob sufficient? any edge cases?
  Output: thoughts/shared/research/2026-04-21-db-schema-design.md + .compressed.md
  Mark each decision: fixed (others must not deviate) | provisional (open to Architect/impl refinement)

ARCHITECT PROMPT:
  Constraints: compressed-format + shell-safety (see above)
  Read first:
    thoughts/shared/research/2026-04-21-db-schema-design.md [DBA output — read this first]
    thoughts/shared/plans/2026-04-21-db-kotlin-feature.md
    thoughts/shared/tickets/KT-005,KT-006,KT-007
    kotlin/src/main/kotlin/bracketskt/brackets.kt
    kotlin/src/main/kotlin/.../service/BalanceServiceEndpoint.kt
    kotlin/src/main/kotlin/.../service/dagger/ApplicationGraph.kt
    kotlin/src/main/kotlin/.../service/dagger/ApplicationGraphModule.kt
    kotlin/src/main/kotlin/.../service/dagger/GrpcHandlersModule.kt
    kotlin/src/main/kotlin/.../config/Config.kt
    kotlin/src/test/kotlin/.../service/dagger/ApplicationGraphTest.kt
    kotlin/src/test/kotlin/.../service/dagger/TestApplicationGraphModule.kt
    kotlin/src/test/kotlin/.../service/dagger/TestApplicationGraph.kt
  Role: architect for PRs 3–5. CRC-card level or coarser — subsystems, responsibilities,
    high-level collaborations, flow. Set constraints+intent; TDD Designer fills tactical details.
  Design and document (with rationale):
    1. Layer boundaries: which layers(DB adapter/repository/service); responsibilities of each;
       what collaborates with what; should BalanceServiceEndpoint depend on narrow abstraction? (testability arg)
       describe at responsibility level — TDD Designer names interfaces+defines sigs
    2. Dagger module decomposition: name modules+responsibilities; can DataSource/Flyway/DSLContext
       be overridden independently in tests? how does DatabaseConfig flow into graph?
       describe topology — TDD Designer defines exact provider sigs
    3. Repository responsibility: what is it for? interface separating from consumers? why?
       what changes if JOOQ→JDBC or async? describe boundary so changes don't propagate to service layer
    4. Startup/lifecycle: when Flyway runs; who ensures migrations applied before DSLContext used
    5. Coroutine readiness: JOOQ blocking; describe how repository boundary contains future async migration
    6. Test surfaces per layer: unit vs integration; kinds of doubles needed; test Dagger modules
       keep at "fake in-memory impl of repository interface" level — TDD Designer writes the actual fake
    7. Explicitly deferred: list what TDD Designer decides; flag what must be settled now vs. later
  Output: thoughts/shared/research/$arch.md + .compressed.md
  Write at responsibility+constraint level — NOT method-signature level
  Mark fixed (TDD Designer must not deviate) vs. open (TDD Designer's domain)

TDD-DESIGNER PROMPT:
  Constraints: VCS(jj) + build(bazel) + ktfmt + shell-safety (see above)
  Read first:
    thoughts/shared/research/$arch.md [primary constraint — not a script; strategic boundaries only]
    thoughts/shared/research/2026-04-21-db-schema-design.md [DBA output]
    thoughts/shared/plans/2026-04-21-db-kotlin-feature.md
    kotlin/src/test/kotlin/bracketskt/BracketsTest.kt
    kotlin/src/test/kotlin/.../service/dagger/ApplicationGraphTest.kt
    kotlin/src/test/kotlin/.../service/dagger/TestApplicationGraphModule.kt
    kotlin/src/test/kotlin/.../service/dagger/BUILD.bazel
    kotlin/src/test/kotlin/.../service/dagger/FakeTelemetryModule.kt
  Role: TDD Designer for PRs 3–5. Tactical designer working test-first.
    Architect sets strategic boundaries; you fill the tactical space:
      interface names, method signatures, error contracts, type shapes, test doubles
    Your type/interface definitions are design output — not placeholders for Coder to rethink
  Design principles:
    prefer narrow interfaces; prefer value types (data class, sealed) over primitives at boundaries
    fakes over mocks; JUnit 4 + Truth; tests compile+fail before Coder starts
    where Architect left decisions open → make them yourself; document rationale in comments
    genuine strategic tensions (not tactical gaps) → escalate to Architect before committing
  Placement: interfaces+types in kotlin/src/main/kotlin/...; tests+fakes in kotlin/src/test/kotlin/...
    kt_jvm_test targets in BUILD.bazel per existing patterns
  Cover:
    Repository contract: enabled pairs loaded; disabled ignored; empty→no crash; close→open direction
    Service layer: known pairs→correct result(no DB); empty→error response; unbalanced→correct error; unknown chars=plain text
    Dagger wiring: test graph with in-memory SQLite; repository injectable; disable-pair end-to-end
  When done: tell Coder — files written; interfaces+types defined; TODO() bodies for them to fill;
    decisions that extend/deviate from Architect doc (so Coder understands full contract)
  Coder must not change your type/interface/sig definitions without your agreement; escalate to Architect if unresolved

CODER PROMPT:
  Constraints: VCS(jj) + build(bazel) + ktfmt + shell-safety (see above)
  Read first:
    thoughts/shared/research/$arch.md [primary reference]
    thoughts/shared/research/2026-04-21-db-schema-design.md [DBA output]
    thoughts/shared/plans/2026-04-21-db-kotlin-feature.md
    all type+interface+test files from TDD Designer (they will list these)
    kotlin/src/main/kotlin/bracketskt/brackets.kt
    kotlin/src/main/kotlin/.../service/BalanceServiceEndpoint.kt
    kotlin/src/main/kotlin/.../service/dagger/ApplicationGraph.kt
    kotlin/src/main/kotlin/.../config/Config.kt
    kotlin/src/main/kotlin/.../service/BUILD.bazel
    kotlin/BUILD.bazel; MODULE.bazel
  Role: Coder for PRs 3–5. TDD Designer has laid out the skeleton (interfaces, class declarations,
    module structure, TODO() stubs). Public contract is decided.
    Below the public surface: full creative latitude — private types, private functions, local names,
    internal helpers, utility extraction, internal state structure, lib call choices. Create freely.
  Rules:
    may PROPOSE public shape changes (implementation insight often reveals gaps) — collaborative request to TDD Designer, not unilateral edit
    TDD Designer agrees → make change; disagree → escalate to Architect
    impossible test = reason to propose a change; bring to TDD Designer with explanation; never silent change
    Bazel: kt_jvm_library; java_binary+runtime_deps; associates=[...]; new deps → mm maven.install
    H2 NOT in service runtime_deps (build-time only)
    Flyway: filesystem: location + RUNFILES_DIR resolution (see plan PR-4)
    JOOQ genrule outs: exhaustive; run codegen locally once first to discover all files
  Per PR(3,4,5 in order): read design docs+tests → implement → bazel test //kotlin/... passes → ktfmt → report deviations
  Deadlock: bring conflict to DBA(data)|Architect(API); Coordinator breaks if unresolved

§STEPS

PR-1 KT-007(partial) branch:kt-007-algorithm-param prereqs:NONE agents:direct
  fun balancedBrackets(text:String, pairs:Map<Char,Char>=closedParentheses)
    replace internal refs to closedParentheses/openParentheses with pairs/opens
  no new deps; no Bazel targets; no config changes; existing BracketsTest passes unchanged
  accept: bazel test //kotlin/... passes

PR-2 DB-001 branch:db-001-schema-migrations prereqs:NONE agents:direct
  1. create db/migrations/
  2. validate Flyway filename convention (sqlMigrationPrefix="" sqlMigrationSeparator="_")
       fallback if rejected: flyway/V1__... + dbmate/20260421000000_...(same SQL)
  3. write $mig: CREATE TABLE bracket_pair(id,open_char,close_char,enabled) + 3 seed INSERTs
  4. db/README.md: dual-tool; format; filename convention; portability constraints
  5. db/BUILD.bazel filegroup(migrations)
  6. validate with dbmate
  accept: //db:migrations defined; validated; README written

PRE-PR-3: design phase [DBA first in this project; Architect reads DBA output; see workflow note]
           DBA → thoughts/shared/research/2026-04-21-db-schema-design.md
           Architect → thoughts/shared/research/$arch.md
           both reviewed by coordinator before PR-3 starts

PR-3 KT-005 branch:kt-005-jooq-codegen prereq:DB-001 agents:TDD Designer→Coder [consult DBA+Architect]
  new mm: org.jooq:jooq:3.19.x; jooq-kotlin:3.19.x; jooq-meta-extensions:3.19.x[codegen];
          jooq-codegen:3.19.x[codegen]; com.h2database:h2:2.x.x[build-time only]
  codegen runner: JooqCodegenRunner(DDL path, out dir) → GenerationTool.generate()
    Database=DDLDatabase; pkg=com.geekinasuit.polyglot.brackets.db.jooq
  Bazel: java_binary(jooq_codegen_runner) → genrule(jooq_generated_srcs;outs exhaustive) →
         kt_jvm_library(jooq_db_lib;visibility=public)
  codegen→Java(.java) not Kotlin; jooq-kotlin=runtime only; outs must be exhaustive; .gitignored
  accept: bazel build //kotlin/... passes; Tables/BracketPair/BracketPairRecord available

PR-4 KT-006 branch:kt-006-database-adapter prereq:DB-001 [parallel with PR-3] agents:TDD Designer→Coder [consult DBA+Architect]
  new mm: HikariCP:5.x.x; sqlite-jdbc:3.x.x; postgresql:42.x.x;
          flyway-core:10.x.x; flyway-database-sqlite:10.x.x
  Config.kt: DatabaseConfig(url,driver,maxPoolSize=5); wire into ServiceAppConfig
  service/application.yml: database.url=jdbc:sqlite::memory:
  DatabaseModule: dataSource(config)→HikariCP+Flyway; dslContext(ds,config)→dialect from url
  FLYWAY GOTCHA: filesystem: location; Paths.get(RUNFILES_DIR?:".", "_main/db/migrations")
  wire DatabaseModule into ApplicationGraph
  Bazel: //db:migrations → data= of brackets_service; runtime deps in service BUILD.bazel
  tests: TestApplicationGraph with in-memory SQLite; existing tests pass
  accept: bazel test //kotlin/... passes; Flyway runs on startup

PR-5 KT-007(remainder) branch:kt-007-bracket-config prereqs:PR-1+KT-005+KT-006 agents:TDD Designer→Coder [consult DBA+Architect]
  new pkg com.geekinasuit.polyglot.brackets.db:
    BracketPairRepository @ApplicationScope @Inject(dsl:DSLContext)
      loadEnabledPairs():Map<Char,Char> — SELECT enabled=true; map close_char→open_char
  Dagger: BracketConfigModule provides Map<Char,Char> @ApplicationScope from repo.loadEnabledPairs()
  BalanceServiceEndpoint: inject Map<Char,Char>; pass to balancedBrackets; empty→error response
  tests:
    BracketPairRepositoryTest: in-memory SQLite; migration; assert loadEnabledPairs
    BalanceServiceEndpointTest: known pair map; no DB
    disable-pair test: () disabled→treated as plain text
    ApplicationGraphTest: DatabaseConfig in-memory SQLite; full graph builds
  Bazel: kt_jvm_library for db pkg; add to service_lib deps
  accept: bazel test //kotlin/... passes; disable-pair test passes

§RISKS
| Risk | Mitigation |
|---|---|
| Flyway rejects empty-prefix+single-_ | DBA validates; fallback documented |
| genrule outs must be exhaustive | Coder runs codegen locally once first |
| Flyway classpath vs runfiles | filesystem: + RUNFILES_DIR (see PR-4) |
| H2 DDL compat | DBA verifies DDL against H2; safe subset only |
| Dagger raw Map<Char,Char> binding | Architect decides: @Named or wrapper type |
| Agent deadlock | Escalate to DBA|Architect first; Coordinator breaks if unresolved |

§DONE
- [ ] PR-1: balancedBrackets(text,pairs=default); existing tests pass unchanged
- [ ] DBA doc: thoughts/shared/research/2026-04-21-db-schema-design.md exists+reviewed
- [ ] Architect doc: thoughts/shared/research/$arch.md exists+reviewed
- [ ] PR-2: db/migrations/ + validated migration + //db:migrations + README
- [ ] PR-3: JOOQ genrule → Tables/BracketPair/BracketPairRecord; .gitignored; bazel build passes
- [ ] PR-4: DatabaseConfig+yml; DatabaseModule+Flyway; bazel test passes
- [ ] PR-5: BracketPairRepository; BalanceServiceEndpoint uses injected map; all tests pass; bazel test //kotlin/... passes
