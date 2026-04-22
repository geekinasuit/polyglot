---
date: 2026-04-21
researcher: claude + cgruber
git_commit: 7894102bc653
branch: main
repository: geekinasuit/polyglot
topic: Multi-agent TDD workflow pattern
tags: agents, tdd, architecture, workflow, pattern
status: draft — in use on DB feature; refine as experience accumulates
last_updated: 2026-04-21
last_updated_by: claude
---

# Multi-Agent TDD Workflow Pattern

A pattern for implementing non-trivial features using specialist agents with distinct
roles and clear handoff points. Six roles are defined: four currently active (DBA, Architect,
TDD Designer, Coder) and two stubbed as TBD (Product Owner, UI/UX Designer). Designed for features that span multiple layers (data,
infrastructure, service logic) and benefit from separation of concerns at the agent level.

First applied: DB feature (DB-001, KT-005, KT-006, KT-007) in this repo.

---

## When to Use

Apply this pattern when a feature:
- Spans multiple architectural layers (e.g. data schema → infrastructure → service API)
- Has meaningful design decisions at both the strategic and tactical level
- Benefits from test-driven development with a clear public contract
- Is complex enough that a single agent would lose coherence across the full scope

For simple, single-layer changes (a bug fix, a config addition, a one-file feature),
this pattern is overkill. Apply judgment.

---

## Roles

### Product Owner *(TBD — not yet defined)*
**Domain:** Customer and business requirements — what the system should do and why, priority
tradeoffs, acceptance criteria from the user's perspective.

Not yet relevant to this project (polyglot is an exemplar, not a product), but will matter
when polyglot is used as a template for real applications. The Product Owner would sit
upstream of the Architect, defining the problem space the Architect designs a solution for.
Without a Product Owner, the Architect absorbs this responsibility by default.

*Needs definition: how the PO interacts with the Architect and TDD Designer; how acceptance
criteria flow into the test surfaces the TDD Designer defines.*

---

### UI/UX Designer *(TBD — partially relevant now)*
**Domain:** User-facing interaction design, appropriate to the modality. Possible
specialisations: CLI, web, Android, iOS, desktop.

**CLI is probably relevant now** — the Kotlin service has a CLI client, and decisions about
flag names, output format, and error messages are UX decisions that currently get made
implicitly by whoever is implementing. A CLI UX Designer role could make those decisions
explicit and consistent.

Other modalities (web, mobile, desktop) are not yet relevant to this project but will be
when polyglot is used as an app template. The UI/UX Designer would sit between the Product
Owner (what users need) and the TDD Designer (how those needs become concrete types and
behaviours), with the Architect ensuring the technical structure can support the UX intent.

*Needs definition: how the modality specialisation is specified in the prompt; how UX
decisions propagate into the TDD Designer's type and API choices.*

---

### DBA
**Domain:** Data layer — schema, migrations, seeding, DB portability, test data strategy.

The DBA is the authority on anything that touches the database directly. In a typical
feature, the Architect establishes what the system needs to do, and the DBA negotiates
the schema that best serves that purpose — it's a round-trip, not a one-way handoff.
The DBA brings data modelling expertise; the Architect brings system design intent; they
meet in the middle. All data/schema questions route to the DBA, but the DBA is serving the
architectural need, not driving it.

Not responsible for: Kotlin code, Dagger wiring, API design, test framework choices.

### Architect
**Domain:** Strategic API shape — layer boundaries, module decomposition, Dagger graph
structure, long-term extensibility, interface topology.

In a typical feature, the Architect drives the need: what does the system have to do, how
should it be structured, what are the quality constraints. The DBA then negotiates the schema
to serve that intent, and the two iterate toward a design that satisfies both. The Architect
does not simply consume the DBA's output — they co-produce Phase 1 with the DBA.

The Architect works at CRC-card level or coarser: subsystems, responsibilities, high-level
collaborations and flow. They may recommend class structures ("there should be a repository
responsible for loading pairs, collaborating with the DSL context") but stop short of exact
method signatures, error contracts, or internal state. Those details are the TDD Designer's
domain. The Architect establishes constraints and intent, not a complete spec.

Not responsible for: schema details, migration tooling, exact method signatures, error
contracts, concrete type names, test bodies.

### TDD Designer
**Domain:** Tactical API completion + test-first expression of that design.

The TDD Designer is not primarily a test-writer — they are a tactical designer working
test-first. The Architect establishes strategic boundaries; the TDD Designer fills the
space within those boundaries: interface names, method signatures, error contracts, type
shapes, test doubles. Their design decisions are expressed as tests. The code they produce
(type definitions, interface declarations, class skeletons with `TODO()` bodies) constitutes
the public contract that the Coder must implement.

Design principles:
- Prefer narrow interfaces over wide ones
- Prefer value types (data classes, sealed classes) over primitives at boundaries where
  the type carries meaning
- Use fakes over mocks — a few lines of in-memory state couples less than a mock framework
- Where the Architect left a decision open, make it yourself; document the rationale
- **Design for the call site.** A public API is not just a contract — it is the code that
  callers will write. Where a wrapper type could expose domain-relevant methods directly
  (rather than requiring callers to reach into fields), prefer the wrapper. A call to
  `telemetry.recordRequest(attrs)` is cleaner than `telemetry.requestCounter.add(1, attrs)`.
  The same principle improves testability: a wrapper with a meaningful interface is easier
  to fake than a struct of library objects. This is a judgment call, not an absolute — apply
  it where the result is genuinely cleaner, not as a reflex to wrap everything.

Not responsible for: implementation bodies, private helper extraction, internal state structure.

### Coder
**Domain:** Private implementation — everything below the public surface.

The Coder has full creative latitude in the private space: private types, private functions,
local names, internal helpers, utility extraction, internal state management, third-party
library call choices. They do not need permission to create things in the private space.

The Coder may also propose changes to the public contract when implementation insight reveals
gaps or problems the TDD Designer couldn't anticipate from the outside. These are collaborative
requests to the TDD Designer — not unilateral edits.

Not responsible for: public type definitions, interface signatures, module boundaries (unless
negotiated), test bodies.

---

## Workflow Sequence

```
Phase 1: Design (runs once, before any implementation)

  Architect ←→ DBA  [collaborative, iterative]
    Architect: establishes system need, quality constraints, structural intent
    DBA: proposes schema to serve that intent; negotiates data model constraints back
    iterate until both are satisfied
    └─→ produces: schema design doc (DBA) + API/Dagger design doc (Architect)

Phase 2: Implementation (per PR, in order)

  TDD Designer  [reads both design docs]
    └─→ produces: type definitions, interface declarations, class skeletons, tests (red)
         └─→ handoff to Coder: files written, contracts defined, TODO() bodies to fill

  Coder  [reads both design docs + TDD Designer's files]
    └─→ produces: implementation bodies, private helpers
         └─→ may propose public contract changes back to TDD Designer
```

Phase 1 runs once for the whole feature. Phase 2 repeats per PR if the feature spans
multiple PRs (TDD Designer and Coder work through each PR in order before moving to the next).

**Note on this project's unusual sequencing:** The DB feature in this repo ran DBA first,
then Architect, because we were retrofitting a database requirement onto an existing
application — the schema was the primary new driver, and the Architect was adapting to it.
That's the exception, not the rule. In a greenfield feature, the Architect drives the need
and the DBA serves it.

---

## Escalation Chain

| Question type | First stop | If unresolved |
|---|---|---|
| Schema, migration, data | DBA | Architect |
| Strategic API shape, module boundaries | Architect | Coordinator |
| Tactical type/interface details | TDD Designer | Architect |
| Public contract change proposal | TDD Designer | Architect |
| Deadlock between any two agents | Architect | Coordinator |

The **Coordinator** is the outer session (human or orchestrating agent) running the subagents.
The Coordinator breaks deadlocks only — after the relevant specialist agents have been consulted.

---

## Constraints by Role

These constraints must be embedded in each agent's prompt directly — subagents start cold
and do not inherit the session's system prompt or bootstrap chain.

### DBA and Architect
```
Compressed format: Read /opt/geekinasuit/agents/internal/workflows/compressed-format.compressed.md
  before producing output docs. Applies to .md research docs only — .sql, .kt, and other
  non-prose files have no compressed form. Produce both .md and .compressed.md for your output.
Shell safety: Never pass non-trivial text inline to shell commands. Write to a temp file
  first, reference by path. Use unique names: /tmp/<repo>-<purpose>-<context>.txt
```

### TDD Designer and Coder
```
VCS: This repo uses jj (jujutsu). Never run git commands.
Build: Use bazel (not bazelisk) for all build and test commands.
Style: Run ktfmt on every .kt file you write or modify before considering work done.
Shell safety: (same as above)
Bazel output tree: Do not read files under bazel-out/ or bazel-bin/. Those paths are
  config-stamped, volatile, and not a readable API. Use narrower signals instead:
  - Build/test success: bazel exit code + stderr
  - Dependency graph: bazel query 'deps(...)'
  - Codegen output shape: run the generator to a temp dir, then `find /tmp/out -name "*.java"`
    (or equivalent) — this gives the actual generator output, not a cached artifact
  - Generated code content: write a test that imports the expected type and asserts on it;
    do not read the generated .java/.kt source files directly. Tests verify what matters
    (the type is accessible and correctly shaped) and run in CI.
  `find` on a known output directory is fine for discovering file shape; reading file
  content from bazel-out is almost never the right approach.
```

Adjust the VCS/build/style constraints to match the target repo's toolchain. The shell
safety rule applies universally.

---

## Agent Prompt Templates

The prompts below are templates. Replace `<SCHEMA_DOC>`, `<ARCH_DOC>`, `<PLAN>`,
`<TICKETS>`, and file paths with the actual values for the feature being implemented.

### DBA Prompt Template

```
Constraints:
- Compressed format: [see above]
- Shell safety: [see above]

Read first:
  <PLAN>
  <TICKETS relevant to data layer>

Role: DBA and data architect for <FEATURE>. Design schema, migrations, seeding, test data
strategy. The Architect will read your output — be precise about anything that constrains
the application layer (column types, nullability, migration sequencing, test isolation).

Design and document:
1. Schema: finalize DDL; verify portability across target DB engines; rationale per choice
2. Migration tooling: filename conventions, tool compatibility, validation
3. Seed data strategy: where seed data lives; how tests reset/override it cleanly
4. Future evolution: what changes are plausible; does initial schema foreclose any paths
5. Test data strategy: how integration tests get a clean, reproducible DB state
6. Build integration: how migration files are exposed to the build system

Output: <SCHEMA_DOC>.md + <SCHEMA_DOC>.compressed.md
Mark each decision: fixed | provisional
```

### Architect Prompt Template

```
Constraints:
- Compressed format: [see above]
- Shell safety: [see above]

Read first:
  <SCHEMA_DOC> [DBA output — read this first]
  <PLAN>
  <TICKETS relevant to application layer>
  <relevant existing source files>

Role: Architect for <FEATURE>. Design API surfaces, module decomposition, Dagger graph,
test seams. Not implementation. The TDD Designer will fill the tactical details you leave
open — establish constraints and intent, not a complete spec.

Design and document:
1. Layer boundaries: what interfaces exist at each layer boundary; testability rationale
2. Module decomposition: which modules, what each provides; can pieces be overridden
   independently in tests?
3. Dependency injection wiring: how config and dependencies flow through the graph
4. Lifecycle and startup sequencing: who is responsible for what, in what order
5. Future-proofing: what likely changes should the design not foreclose
6. Test surfaces: what is tested at unit vs. integration level; what doubles are needed
7. Explicit open decisions: what you are deliberately leaving to the TDD Designer

Output: <ARCH_DOC>.md + <ARCH_DOC>.compressed.md
Mark: fixed (TDD Designer and Coder must not deviate) | provisional (TDD Designer may refine)
```

### TDD Designer Prompt Template

```
Constraints:
- VCS, build, style, shell safety: [see above]
- Bazel output tree: [see above — do not read bazel-out/; use find for shape, tests for content]

Read first:
  <ARCH_DOC> [primary constraint — strategic boundaries, not a complete spec]
  <SCHEMA_DOC> [DBA output]
  <PLAN>
  <existing test files for style reference>

Role: TDD Designer for <FEATURE>. Tactical designer working test-first. The Architect has
set the strategic boundaries; you fill the space within them: interface names, method
signatures, error contracts, type shapes, test doubles. Your type and interface definitions
are design output — not placeholders for the Coder to rethink.

Design and test principles:
- Prefer narrow interfaces; prefer value types over primitives at boundaries
- Write fakes, not mocks; JUnit 4 + Truth (or project-standard test framework)
- Where Architect left a decision open, make it yourself; document rationale in comments
- Genuine strategic tensions → check with Architect before committing
- Tests must compile and fail before Coder starts
- Design for the call site: a public API is code that callers will write. Where a wrapper
  type could expose domain-relevant methods directly rather than requiring callers to reach
  through to underlying fields, prefer the wrapper. This also improves testability — a type
  with a meaningful interface is easier to fake than a struct of library objects. Judgment
  call; apply where it produces genuinely cleaner code, not reflexively.

Coverage (adapt to feature):
- Each repository/data-access contract: happy path, edge cases, error conditions
- Each service-layer contract: with direct inputs (no DB), empty/error states
- Dagger/DI wiring: test graph builds; components are injectable; end-to-end scenario

Placement: type/interface definitions in main source tree; tests and fakes in test tree
Add build targets following existing patterns.

Handoff: tell Coder — files written, interfaces defined, TODO() bodies for them to fill,
any decisions that extend or deviate from Architect doc.
Coder must not change your type/interface/sig definitions without agreement + escalation.
```

### Coder Prompt Template

```
Constraints:
- VCS, build, style, shell safety: [see above]
- Bazel output tree: [see above — do not read bazel-out/; use find for shape, tests for content]

Read first:
  <ARCH_DOC> [primary reference]
  <SCHEMA_DOC> [DBA output]
  <PLAN>
  <all files produced by TDD Designer — they will list these>
  <relevant existing source files>

Role: Coder for <FEATURE>. The TDD Designer has laid out the skeleton: interfaces defined,
classes declared, module structure visible, method bodies stubbed with TODO(). The public
contract is decided. Your domain is everything below the public surface: private types,
private functions, local names, internal helpers, utility extraction, internal state,
third-party library call choices. You have full creative latitude here — create freely.

You may also propose changes to the public contract when implementation reveals gaps the
designer couldn't anticipate from the outside. Bring these as collaborative requests to
the TDD Designer. If you agree, proceed. If not, escalate to the Architect.

Rules:
- No unilateral changes to public types, interfaces, signatures, or class names
- Propose public changes to TDD Designer; escalate disagreements to Architect
- Impossible test → explain the conflict to TDD Designer; escalate if unresolved
- [repo-specific build rules, style tools, dependency management]

Per PR: read design docs + TDD Designer files → implement → [build+test command] passes
→ run formatter → report any deviations from the design docs.
```

---

## Notes and Refinements

*(Add observations here as the pattern is used in practice)*

- **2026-04-21:** First defined during DB feature planning session. Pattern not yet validated
  in practice — treat prompt templates as provisional until at least one full feature cycle
  completes.

---

## Open Questions

- **Sync vs. async between TDD Designer and Coder.** Synchronous back-and-forth is the
  preferred model — more collaborative, closer to pair programming. The open question is
  coordination overhead: how hard is it to run two agents in a genuine dialogue vs. clean
  sequential handoffs? Need to explore in practice before committing to either as default.
  Current plan uses async as the simpler starting point.

- **Minimum feature size to justify DBA + Architect separately.** The "two perspectives"
  argument holds even for small things — pair programming is valuable on a one-liner. But
  there's probably a floor somewhere. A feature with one table and one endpoint might only
  need the Architect. Track against real examples as the pattern is used; let evidence drive
  the answer rather than theory.

- **Specialist overhead vs. value as the roster grows.** The pattern currently defines six
  roles (four active, two TBD). At some point the coordination cost of spinning up, briefing, and routing between
  specialists exceeds the value of the separation. This tradeoff is manageable now but will
  need revisiting when real app features (with genuine UX and product requirements) are in
  scope. The question isn't just "is this role valuable in theory" but "does this feature
  have enough complexity in this role's domain to justify it."

- **Architecture doc currency and granularity.** Keep the Architect doc in sync as
  implementation proceeds, but hold it at CRC-card level or coarser — subsystems, overall
  responsibilities, high-level collaborations and flow. The Architect *can* recommend class
  structures, but names them at the level of "there should be a repository responsible for
  loading pairs, collaborating with the DSL context" rather than specifying exact method
  signatures, error contracts, or internal state. That detailed level is the TDD Designer's
  domain. The granularity boundary is the key distinction between the two roles: Architect
  works at subsystem/responsibility/flow resolution; TDD Designer works at
  interface/signature/contract resolution.

  Implementation-level details that the TDD Designer fills in do not need to propagate back
  up; only changes that affect stated responsibilities, subsystem boundaries, or high-level
  collaborations warrant an Architect doc update. All architecture documents go out of date
  eventually — that's acceptable. A periodic refresh from code analysis is healthier than
  trying to keep perfect sync.
