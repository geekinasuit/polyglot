# Agent Guide: polyglot

This file is for AI agents and future context windows. It captures working norms, design principles, and conventions for this repository that are not obvious from reading the code alone.

---

## Project Purpose

`polyglot` is an **exemplar project** — its goal is to demonstrate idiomatic, production-quality patterns for gRPC services and clients across multiple languages, all sharing a single Protobuf schema, all built with Bazel. It is also intended for easy IDE integration. Code here should be a good example of how to do things well, not just a proof-of-concept.

---

## Reading This Repo: Compressed Documents

The `thoughts/` directory contains research, plans, tickets, and handoffs. Many documents have two forms:

- `<name>.md` — human-readable, fully prose
- `<name>.compressed.md` — dense structured notation, token-efficient, losslessly equivalent

**Prefer the `.compressed.md` form for active context use.** It contains all the same information. Use the `.md` form only when producing output for human review or when the compressed form is absent.

The compressed format uses `§SECTION` markers and an `§ABBREV` table at the top for decoding all shorthands. It is self-describing.

---

## Repository Layout (Key Directories)

```
thoughts/shared/research/   # Research documents (read before implementing)
thoughts/shared/tickets/    # Ticket files + TICKETS.md index (check before starting new work)
thoughts/shared/plans/      # Implementation plans
thoughts/shared/handoffs/   # Session handoff documents
```

Before starting any non-trivial implementation work: check `thoughts/shared/tickets/TICKETS.md` for a summary of open and resolved work, and `thoughts/shared/research/` for relevant prior research.

---

## Working with Tickets

### TICKETS.md is the Canonical Index

`thoughts/shared/tickets/TICKETS.md` is the single source of truth for all open and resolved work. Individual files in that directory contain full detail; `TICKETS.md` contains the summary. **Do not maintain ticket inventories anywhere else** — other files (AGENT.md, research docs, plans) should reference `TICKETS.md` rather than listing tickets inline.

### Keeping TICKETS.md Current

**Any time you create, modify, resolve, or delete a ticket file, you must also update `TICKETS.md`** in the same operation. Specifically:

- **New ticket**: add a row to the Open table with file link, priority, area, and one-line summary
- **Ticket resolved**: move it from the Open table to the Resolved section; if the ticket file is deleted, note the resolution inline in the Resolved section (no file link needed)
- **Ticket updated** (priority, area, summary changed): update the corresponding row in TICKETS.md
- **Ticket deleted without resolution** (e.g. duplicate or invalid): remove its row from TICKETS.md entirely

Never leave TICKETS.md out of sync with the actual ticket files.

### Ticket File Conventions

- Filename: `<kebab-case-description>.md` in `thoughts/shared/tickets/`
- Frontmatter fields: `date`, `status` (open/resolved), `priority` (low/medium/high), `area` (comma-separated)
- Required sections: Summary, Current State (or Resolution if resolved), Goals or acceptance criteria, References (file paths with line numbers)

---

## Reference Implementation

**Kotlin is the reference implementation.** When implementing gRPC services or clients in any other language, model:
- The gRPC service/client design after the Kotlin variant
- The Bazel build structure after the Kotlin variant
- The CLI interface style after the Kotlin variant (CliktCommand pattern)

Other languages are expected to be interoperable with Kotlin: a Kotlin client must be able to talk to any language's server, and vice versa.

---

## Build System Principles

### Dual-Build by Default

Where practical, every language variant should be buildable via **two independent paths**:
1. **Bazel** — the canonical build for the repo as a whole
2. **The language's native/common build tool** — Go modules + `go build`/`make`, Maven/Gradle for Java, `yarn`/`npm` for TypeScript, `cargo` for Rust, `pip`/`pyproject` for Python

Neither build should require the other. The native build is for developer ergonomics and IDE integration; Bazel is for reproducible, cross-language builds.

When a file must exist in both build graphs (e.g., Go's `generate.go`), include a comment explaining its dual role.

### Bazel Conventions

- **bzlmod only** (`MODULE.bazel`). Do not add a `WORKSPACE` file. The current `--enable_workspace=true` flag in `.bazelrc` exists solely as a compatibility shim for `grpc_kotlin` and should be removed once that module supports bzlmod natively.
- All Kotlin executables use `java_binary(runtime_deps=...)` — not `kt_jvm_binary`. This is the `rules_kotlin` convention.
- Package-pinning `BUILD.bazel` files (empty or near-empty) at language root directories are intentional — do not add targets to them without good reason.
- **Never commit generated proto code.** All generated code is derived at build time from `//protobuf` targets. Generated files belong in `.gitignore`.
- Kotlin tests use `associates = [...]` to access `internal`-scoped members — do not change visibility modifiers to work around this.

### Proto as Contract Boundary

`//protobuf:protobuf` (example.proto) and `//protobuf:balance_rpc` (brackets_service.proto) are the sole canonical sources for all language implementations. Changes to the service contract happen here first, then propagate to all language variants.

---

## Testing Philosophy

### Prefer Unit Tests

Write unit tests first. The bracket algorithm and its error cases are pure functions — they need no infrastructure. Reach for higher-level tests only when unit tests genuinely cannot cover the scenario.

### Prefer Fakes and Stubs Over Mocks

When isolating a component for testing, prefer hand-written fakes or stubs over mock frameworks. Mocks couple tests to implementation details; fakes test behavior. If a mock framework is truly necessary, use it sparingly and only for boundaries you do not own.

### Integration and Contract Tests

- Prefer **pairwise contract tests** (client ↔ server, one language at a time) over full multi-language integration suites.
- Integration tests should cover **use-case scenarios** (balanced input, unbalanced input, error paths, connection failure), not chase code coverage metrics.
- Cross-language interoperability tests (e.g., Kotlin client → Go server) are valuable but should be scoped to the contract surface, not implementation internals.

### Test Parity Across Languages

Each language implementation should have equivalent test coverage. The Go and Java implementations provide a useful baseline: 8 unit test cases covering empty input, balanced, unclosed opener, extra closer, bracket type mismatch, complex sentence, mixed brackets, and complex mismatch. New language implementations should match this coverage.

---

## Code Style Principles

### Idiomatic First, Structurally Familiar Second

Each language implementation should be **idiomatic for that language** — naming, package layout, error handling, and tooling should feel native to a practitioner of that language. Do not mechanically port Kotlin idioms.

At the same time, **mirror the Kotlin project's structure and layout where it maps naturally**. The goal: someone familiar with the Kotlin implementation should find the structure of any other language variant recognizable (similar separation of library, service, client, tests), while someone fluent in that language should find the code easy to navigate without knowledge of Kotlin.

Concretely:
- Maintain a similar separation of concerns: core algorithm library, gRPC service, gRPC client, tests
- Use analogous directory depth and naming where the language's conventions permit
- CLI entry points should accept the same flags (`--host`, `--port`, input argument) with the same defaults
- Error output and success output should be semantically equivalent across languages
- The shared proto interface and error message strings are fixed — do not diverge from them

### Minimal Dependencies

Prefer standard library or well-established ecosystem libraries. Avoid pulling in heavy frameworks for simple tasks. The Kotlin implementation's use of Armeria is intentional and considered — do not change it without a clear reason.

### No Interceptors Yet (Hook Exists)

The Kotlin `wrapService()` function accepts `vararg interceptors: ServerInterceptor` but none are currently passed. This is a known incomplete line of development (ticketed). Do not add interceptors speculatively — wait for a concrete use case.

---

## Version Control

This repository is backed by GitHub and uses **jj (Jujutsu)** as the preferred VCS. However, jj may not be installed in every checkout environment.

- **If `jj` is available locally**: use it for all commit/branch/log operations.
- **If `jj` is not available**: fall back to `git` — jj maintains a compatible git backend so standard git commands work.
- Use the `gh` CLI for GitHub API interactions (PRs, issues, repo queries) regardless of which VCS tool is in use.

### Branch and PR Requirements

**It is never permissible to push directly to `main`.** All changes must go through a branch or bookmark and be submitted as a pull request:

- With jj: create a bookmark (`jj bookmark create <name>`), push it, open a PR via `gh pr create`
- With git: create a branch, push it, open a PR via `gh pr create`

**All PRs must be reviewed and merged by a human.** This applies even when the PR was authored entirely by an agent acting under the user's GitHub account. An agent must not merge its own PRs — it may open them, describe them, and request review, but the merge action requires explicit human approval.

---

## Thoughts Directory Workflow

### Creating Tickets

Tickets live in `thoughts/shared/tickets/`. Filename convention: `<kebab-case-description>.md`. Include at minimum:
- YAML frontmatter: `date`, `status`, `priority`, `area`
- Summary of the problem or goal
- Current state
- Goals or acceptance criteria
- Relevant file references

### Creating Research Documents

Research documents live in `thoughts/shared/research/`. Filename convention: `YYYY-MM-DD-<description>.md` (with `.compressed.md` companion). Always produce both forms. Frontmatter fields: `date`, `researcher`, `git_commit`, `branch`, `repository`, `topic`, `tags`, `status`, `last_updated`, `last_updated_by`.

### Creating Plans

Implementation plans live in `thoughts/shared/plans/`. A plan should be created before implementing any non-trivial feature. Plans can reference tickets. Research before planning; plan before implementing.

---

## Known Incomplete Work

See `thoughts/shared/tickets/TICKETS.md` for the canonical summary of open and resolved work items, and `thoughts/shared/tickets/` for individual ticket files.

---

## What Good Looks Like (Reference Points)

| Concern | Reference |
|---|---|
| gRPC service impl | `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BalanceServiceEndpoint.kt` |
| gRPC server startup | `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/service.kt` |
| gRPC client | `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/client/client.kt` |
| Bazel binary target | `kotlin/BUILD.bazel` — `java_binary` + `runtime_deps` |
| Bazel proto codegen | `kotlin/BUILD.bazel` — `kt_jvm_proto_library` + `kt_jvm_grpc_library` |
| Unit tests | `kotlin/src/test/kotlin/bracketskt/BracketsTest.kt` |
| Custom Bazel macro | `util/kt_jvm_proto.bzl` |
| Proto definitions | `protobuf/brackets_service.proto`, `protobuf/example.proto` |
