<!--COMPRESSED v1; source:2026-04-21-multi-agent-tdd-workflow.md-->
§META
date:2026-04-21 researcher:claude+cgruber commit:7894102bc653 branch:main
repo:geekinasuit/polyglot topic:multi-agent TDD workflow pattern
tags:agents,tdd,architecture,workflow,pattern
status:draft — in use on DB feature; refine as experience accumulates

§SUMMARY
Six-role pattern for non-trivial multi-layer features (4 active, 2 TBD). Separates data design, strategic API
design, tactical type+test design, and implementation into specialist roles with clear handoffs.
First applied: DB feature (DB-001,KT-005,KT-006,KT-007).

§WHEN_TO_USE
apply when: feature spans multiple layers | meaningful design decisions at strategic+tactical level |
  benefits from TDD | complex enough single agent loses coherence
skip when: single-layer change, bug fix, config addition, one-file feature

§ROLES

Product Owner [TBD — not yet defined]
  domain: customer+business requirements; what system should do+why; priority tradeoffs; acceptance criteria
  sits upstream of Architect (defines problem space Architect designs solution for)
  not yet relevant: polyglot is an exemplar not a product; will matter when used as app template
  without PO: Architect absorbs this by default
  needs definition: how PO→Architect→TDD Designer flow works; how acceptance criteria become test surfaces

UI/UX Designer [TBD — CLI partially relevant now]
  domain: user-facing interaction design per modality
  modality specialisations: CLI | web | Android | iOS | desktop
  CLI relevant now: Kotlin service has CLI client; flag names, output format, error messages are UX decisions
    currently made implicitly by implementers — a CLI UX role would make them explicit+consistent
  other modalities: not yet relevant; will matter when polyglot used as app template
  sits between PO(what users need) and TDD Designer(how needs become types+behaviours)
  needs definition: how modality specialisation is specified in prompt; how UX decisions reach TDD Designer

DBA
  domain: schema, migrations, seeding, DB portability, test data strategy
  typical flow: Architect drives the need; DBA serves+negotiates schema to satisfy it — round-trip not handoff
  authority on data/schema questions; but serving architectural intent, not driving it
  not responsible for: Kotlin code, Dagger wiring, API design, test framework

Architect
  domain: CRC-card level or coarser — subsystems, responsibilities, high-level collaborations, flow
  typical flow: drives the need; DBA negotiates schema to serve it; both iterate to consensus
  co-produces Phase 1 with DBA — does not simply consume DBA output
  CAN recommend class structures ("a repository responsible for X, collaborating with Y")
  stops short of: exact method sigs, error contracts, internal state — TDD Designer's domain
  establishes constraints+intent, not complete spec
  not responsible for: schema details, migration tooling, exact method sigs, error contracts, test bodies

TDD Designer
  domain: tactical API completion + test-first expression
  NOT primarily a test-writer — tactical designer working test-first
  fills Architect's open space: interface names, method sigs, error contracts, type shapes, test doubles
  produced types+interfaces = public contract Coder must implement (not placeholders)
  design principles: narrow interfaces; value types over primitives at boundaries; fakes not mocks
    design for the call site: APIs are code callers will write; where a wrapper exposes domain-relevant
    methods directly(vs. callers reaching through to fields) prefer the wrapper; also improves testability
    (meaningful interface > struct of library objects for faking); judgment call — apply where result is
    genuinely cleaner, not reflexively
  not responsible for: implementation bodies, private helper extraction, internal state

Coder
  domain: private implementation — everything below the public surface
  full creative latitude: private types, fns, local names, helpers, utility extraction, internal state, lib calls
  may PROPOSE public contract changes (impl insight often reveals gaps) → collaborative request to TDD Designer
  not responsible for: public type defs, interface sigs, module boundaries (unless negotiated)

§WORKFLOW
Phase 1 (once, before impl):
  Architect ↔ DBA [collaborative, iterative — not sequential]
    Architect: establishes need, quality constraints, structural intent
    DBA: proposes schema to serve that intent; negotiates data model constraints back
    iterate to consensus → schema design doc (DBA) + API+Dagger design doc (Architect)
  NOTE this project ran DBA→Architect because retrofitting DB onto existing app (schema was primary driver)
    that's the exception; in greenfield features Architect drives, DBA serves

Phase 2 (per PR, in order):
  TDD Designer [reads both docs] → type defs + interface decls + class skeletons + tests(red)
    handoff: files written; contracts defined; TODO() bodies for Coder
  Coder [reads both docs + TDD Designer files] → implementation bodies + private helpers
    may propose public changes back to TDD Designer

§ESCALATION
| Question | First | If unresolved |
|---|---|---|
| schema/migration/data | DBA | Architect |
| strategic API/module boundaries | Architect | Coordinator |
| tactical type/interface | TDD Designer | Architect |
| public contract change proposal | TDD Designer | Architect |
| deadlock any two agents | Architect | Coordinator |
Coordinator = outer session; breaks deadlocks only

§CONSTRAINTS
subagents start cold — embed constraints directly in each prompt; never assume inheritance

DBA + Architect:
  compressed-format: read /opt/geekinasuit/agents/internal/workflows/compressed-format.compressed.md
    applies to .md docs only — .sql/.kt/other non-prose = no compressed form
    produce both .md + .compressed.md for output docs
  shell-safety: no inline non-trivial text in shell cmds; write to /tmp/<repo>-<purpose>-<ctx>.txt

TDD Designer + Coder:
  VCS: jj only; never git
  build: bazel not bazelisk (or project equivalent)
  style: run formatter (ktfmt/gofmt/etc.) on all modified source files before done
  shell-safety: same as above
  bazel-out: DO NOT read files under bazel-out/|bazel-bin/ — config-stamped, volatile, not a readable API
    build/test success → exit code+stderr
    dep graph → bazel query 'deps(...)'
    codegen output shape → run generator to /tmp/out; find /tmp/out -name "*.java" (actual generator output, not cache)
    generated code content → write import test; do NOT read generated .java/.kt source
    find on known output dir is fine for shape; reading content from bazel-out is almost never right
[adjust VCS/build/style per target repo toolchain; shell-safety+bazel-out rules are universal]

§PROMPT_TEMPLATES

DBA:
  constraints: compressed-format + shell-safety
  read: <PLAN>; <TICKETS:data layer>
  role: DBA+data architect for <FEATURE>; Architect reads your output — constrain them accurately
  design: schema DDL+portability; migration tooling+validation; seed strategy; future evolution;
    test data isolation; build system integration
  output: <SCHEMA_DOC>.md + .compressed.md; mark fixed|provisional

Architect:
  constraints: compressed-format + shell-safety
  read: <SCHEMA_DOC>[first]; <PLAN>; <TICKETS:app layer>; relevant source files
  role: architect for <FEATURE>; design strategic constraints+intent; leave tactical space for TDD Designer
  design: layer boundaries+testability rationale; module decomposition+independent overridability;
    DI wiring; lifecycle sequencing; future-proofing; test surfaces by layer; explicit open decisions
  output: <ARCH_DOC>.md + .compressed.md; mark fixed|provisional

TDD Designer:
  constraints: VCS + build + style + shell-safety + bazel-out
  read: <ARCH_DOC>[primary constraint, not complete spec]; <SCHEMA_DOC>; <PLAN>; existing test files
  role: tactical designer test-first; fill Architect's open space with concrete types+tests
    your type+interface defs = design output, not placeholders
  principles: narrow interfaces; value types at boundaries; fakes not mocks; open decisions→make them+document
    design for call site: prefer wrappers that expose domain methods over structs of library objects;
    improves readability+testability; judgment call — apply where genuinely cleaner, not reflexively
  cover: repository contract(happy/edge/error); service layer(direct input/empty/error); DI wiring+e2e
  placement: type+interface defs in main src; tests+fakes in test src; add build targets
  handoff: tell Coder — files; interfaces; TODO() bodies; deviations from Arch doc
  Coder must not change your sigs without TDD Designer agreement + possible Architect escalation

Coder:
  constraints: VCS + build + style + shell-safety + bazel-out
  read: <ARCH_DOC>; <SCHEMA_DOC>; <PLAN>; all TDD Designer files (they list these); relevant src
  role: fill implementation bodies; public contract is fixed; private domain is yours
  private domain: private types, fns, local names, helpers, utility extraction, internal state, lib calls — create freely
  may propose public changes: collaborative request to TDD Designer; agree→proceed; disagree→Architect
  impossible test: explain to TDD Designer; escalate if unresolved; never silent change
  per PR: read docs+tests → implement → build+test passes → format → report deviations

§NOTES
2026-04-21: first defined; not yet validated in practice — treat as provisional until one full feature cycle completes

§OPEN_QUESTIONS
sync vs async TDD Designer↔Coder:
  preferred=synchronous(more collaborative, closer to pair programming)
  open=coordination overhead — how hard in practice? explore before committing to default
  current plan=async(simpler starting point)

minimum feature size for DBA+Architect separately:
  "two perspectives" valuable even on small things (cf. pair programming on one-liners)
  probably a floor somewhere; a single-table+single-endpoint feature may only need Architect
  track against real examples; let evidence drive, not theory

specialist overhead vs. value as roster grows:
  defined roles: 6 (4 active: DBA,Architect,TDD Designer,Coder; 2 TBD: PO,UI/UX)
  at some point coordination cost (spin up, brief, route) > value of separation
  question: "does this feature have enough complexity in this role's domain to justify it?"
  not yet critical; revisit when real app features with genuine UX+product requirements are in scope

Architect doc currency + granularity:
  hold at CRC-card level or coarser: subsystems, responsibilities, high-level collaborations, flow
  Architect CAN name classes but at responsibility level ("a repository for X collaborating with Y")
  NOT exact method sigs, error contracts, internal state — that's TDD Designer resolution
  granularity boundary IS the role boundary: Architect=subsystem/responsibility/flow; TDD Designer=interface/sig/contract
  TDD Designer details do NOT propagate back up to Arch doc
  update Arch doc only when responsibilities, subsystem boundaries, or high-level collaborations change
  all arch docs go out of date — acceptable; periodic refresh from code analysis > perfect sync
