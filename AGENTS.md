# Agent Guide: polyglot

**Stop. Check for a compressed form before reading further:**
1. Does `AGENTS.compressed.md` exist in this directory?
2. **Yes** — read that file; follow its instructions only; do not continue reading this file.
3. **No** — continue reading below.

This file is for AI agents and future context windows. It captures working norms, design principles, and conventions for this repository that are not obvious from reading the code alone.

---

## Project Purpose

`polyglot` is an **exemplar project** — its goal is to demonstrate idiomatic, production-quality patterns for gRPC services and clients across multiple languages, all sharing a single Protobuf schema, all built with Bazel. It is also intended for easy IDE integration. Code here should be a good example of how to do things well, not just a proof-of-concept.

---

## Reading This Repo: Compressed Documents

The `thoughts/` directory contains research, plans, tickets, and handoffs. Many documents have two forms:

- `<name>.md` — human-readable, fully prose
- `<name>.compressed.md` — dense structured notation, token-efficient, losslessly equivalent

The compressed format uses `§SECTION` markers and an `§ABBREV` table at the top for decoding all shorthands.

## No Inline Content in Shell Commands

Never pass non-trivial text inline to any shell command or tool call. Characters such as backticks, `---`, and `*` are misinterpreted by shell argument parsers and security hooks even when the content is benign.

Always write content to a file first, then reference it by path:
- `gh pr create/edit` body: write to a temp file, then `--body-file /tmp/...`
- `gh api` comment body: write to a temp file, then `-F body=@/tmp/...`
- Agent subagent prompts: write to a temp file, then in the Agent prompt say "Read your instructions from /tmp/... and execute them"

**Use unique temp file names** to avoid collisions with other agents running concurrently. Include repo name and purpose (e.g. `/tmp/polyglot-pr-body-32.md`, `/tmp/polyglot-prompt-kt002.md`). Generic names like `/tmp/pr-body.md` will collide.

**Use `cat >` via Bash (not the Write tool) for temp files.** The Write tool requires reading a file before overwriting it if it already exists — a leftover file from a prior agent causes an explicit error that can be missed, leaving stale content in place. `cat >` always overwrites unconditionally.

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

`thoughts/shared/tickets/TICKETS.md` is the single source of truth for all open and resolved work. Individual files in that directory contain full detail; `TICKETS.md` contains the summary. **Do not maintain ticket inventories anywhere else** — other files (AGENTS.md, research docs, plans) should reference `TICKETS.md` rather than listing tickets inline.

### Keeping TICKETS.md Current

**Any time you create, modify, resolve, or delete a ticket file, you must also update `TICKETS.md`** in the same operation.

Never leave TICKETS.md out of sync with the actual ticket files.

### Ticket ID Categories

Ticket IDs use a category prefix followed by a sequential number within that category:

| Prefix | Meaning |
|---|---|
| `GO-NNN` | Go implementation work |
| `JAVA-NNN` | Java implementation work |
| `KT-NNN` | Kotlin implementation work |
| `PY-NNN` | Python implementation work |
| `RUST-NNN` | Rust implementation work |
| `TS-NNN` | TypeScript implementation work |
| `BUILD-NNN` | Build system and tooling (Bazel, bzlmod, rules) |
| `CI-NNN` | CI, automation, and testing infrastructure |
| `OPT-NNN` | Performance optimization |
| `AGENT-NNN` | Agentic workflow and agent tooling changes |

Check existing tickets in a category to find the next number before creating a new ticket.

### Ticket File Conventions

- Filename: `<ID>-<kebab-case-description>.md` in `thoughts/shared/tickets/`
- Frontmatter fields: `id`, `title`, `area`, `status` (open/resolved), `created`
- Optional frontmatter: `github_issue: N` once a GitHub issue has been created
- Required sections: Summary, Current State (or Resolution if resolved), Goals or acceptance criteria, References (file paths with line numbers)

A ticket is `resolved` only when **all** described work is complete — including any manual, migration, or verification steps, not just merged code.

### GitHub Issues

Create a GitHub issue when **starting work** on a ticket (not before, not at PR-open time):

```bash
kotlin /opt/geekinasuit/agents/tools/gh-ticket.main.kts -- create <ticket-path>
```

Reference issues from PRs with `see #N` — never `fixes #N` or `closes #N`. Issues are always closed manually once the full Definition of Done is met.

### Safety Net: GitHub Action for Missed Ticket Updates

A GitHub Action (see ticket `CI-001-github-action-ticket-close-sync.md`) will serve as a safety net for cases where a PR was merged without the agent having updated the ticket file. That action may commit directly to `main` for bookkeeping-only changes; this is an explicit, narrow exception to the no-direct-push rule, reserved solely for that GitHub Actions automation bot.

**This exception does not extend to agents.** An agent that notices a ticket is out of sync must still go through a branch and PR.

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

### Kotlin Formatting: ktfmt

All Kotlin files in this repository are formatted with **`ktfmt`** (the Google/Meta formatter,
default style). Before committing any Kotlin file changes:

```
ktfmt <changed-kotlin-files>
```

Or to format all Kotlin files at once:

```
ktfmt $(find kotlin -name "*.kt")
```

`ktfmt` must be installed (`brew install ktfmt` on macOS). A formatting-only commit is a valid,
self-contained PR — it carries no behavioral changes and can be reviewed and merged independently.

Do not mix formatting changes with behavioral changes in the same commit. If a file you are
editing is not yet formatted, format it in a separate preceding commit.

---

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

## Thoughts Directory Workflow

### Creating Research Documents

Research documents live in `thoughts/shared/research/`. Filename convention: `YYYY-MM-DD-<description>.md` (with `.compressed.md` companion). Always produce both forms. Frontmatter fields: `date`, `researcher`, `git_commit`, `branch`, `repository`, `topic`, `tags`, `status`, `last_updated`, `last_updated_by`.

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

---

## Geekinasuit Agent Rules (mandatory bootstrap — complete before responding to user)

Load exactly one of the following — first match wins. "Stop here" means stop searching
for more chain files; the bootstrap is not complete until the matched file is read.

1. If `/opt/geekinasuit/agents/internal/AGENTS.compressed.md` exists, read it; else if
   `/opt/geekinasuit/agents/internal/AGENTS.md` exists, read it. Follow its contents
   before proceeding. Stop chain search here (do not check steps 2 or 3).
2. If `/opt/geekinasuit/agents/public/AGENTS.compressed.md` exists, read it; else if
   `/opt/geekinasuit/agents/public/AGENTS.md` exists, read it. Follow its contents
   before proceeding. Stop chain search here (do not check step 3).
3. If `~/.geekinasuit/agents/public/AGENTS.compressed.md` exists, read it; else if
   `~/.geekinasuit/agents/public/AGENTS.md` exists, read it. Follow its contents
   before proceeding. Stop chain search here.

If none of the above paths exist, skip this section — it is not required for
contributor work and will not be present on most machines.
