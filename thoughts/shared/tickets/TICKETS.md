# Tickets

Canonical summary of all open and resolved work items. Individual ticket files in this directory contain full detail. This file is the index — do not duplicate ticket content here, and do not maintain ticket inventories elsewhere (AGENTS.md, research docs, etc. should reference this file instead).

## How This Works with GitHub Issues

Ticket files are the source of truth. GitHub Issues are created when starting work on a ticket (not before, not at PR-open time):

```bash
kotlin /opt/geekinasuit/agents/tools/gh-ticket.main.kts -- create <ticket-path>
```

Reference issues from PRs with `see #N` — never `fixes #N` or `closes #N`. Issues are always closed manually once the full Definition of Done is met.

A GitHub Action (see `CI-001-github-action-ticket-close-sync.md`) will act as a safety net for cases where the PR didn't include the ticket update. That action is permitted to commit directly to `main` for bookkeeping-only changes — a narrow, explicit exception that applies to the GitHub Actions bot only. Agents must go through a branch and PR even for ticket bookkeeping.

---

## Ticket ID Categories

| Prefix | Meaning |
|---|---|
| `GO-NNN` | Go implementation work |
| `JAVA-NNN` | Java implementation work |
| `KT-NNN` | Kotlin implementation work |
| `PY-NNN` | Python implementation work |
| `RUST-NNN` | Rust implementation work |
| `TS-NNN` | TypeScript implementation work |
| `DB-NNN` | Database schema, migrations, and cross-language DB tooling |
| `BUILD-NNN` | Build system and tooling (Bazel, bzlmod, rules) |
| `CI-NNN` | CI, automation, and testing infrastructure |
| `OPT-NNN` | Performance optimization |
| `AGENT-NNN` | Agentic workflow and agent tooling changes |

---

## Open

| File | Area | Summary |
|---|---|---|
| `DB-001-schema-migrations-setup.md` | database, migrations, cross-language | Flyway (JVM) + dbmate (non-JVM) dual-tool migration setup; `db/migrations/`; initial `bracket_pair` schema |
| `KT-005-jooq-codegen.md` | kotlin, database, jooq, bazel | JOOQ + jooq-kotlin dependency + DDL-based codegen + Bazel genrule; prereq: DB-001 |
| `KT-006-database-adapter.md` | kotlin, database, dagger, configuration | HikariCP DataSource, Flyway on startup, Dagger DatabaseModule, SQLite/PG support; prereq: DB-001 |
| `KT-007-bracket-config-feature.md` | kotlin, database, feature | DB-backed bracket pairs: parameterize algorithm + BracketPairRepository + service wiring; prereq: DB-001, KT-005, KT-006 |
| `KT-004-kotlin-docker-deployment.md` | kotlin, docker, deployment | Docker container build for Kotlin service; staging + production profiles |
| `CI-001-github-action-ticket-close-sync.md` | automation, github, tickets | GitHub Action safety net: sync ticket state when issue is closed without a PR ticket update |
| `BUILD-001-grpc-kotlin-bzlmod-migration.md` | kotlin, bazel, grpc | Migrate to bzlmod-native `grpc_kotlin` when upstream supports it |
| `GO-002-go-grpc-library-wrong-proto-target.md` | golang, bazel | `go_grpc_library` wraps wrong proto (`//protobuf` → should be `//protobuf:balance_rpc`) |
| `PY-002-python-proto-integration-incomplete.md` | python, protobuf | Proto import disabled; `foo()` returns `None` |
| `KT-001-kotlin-grpc-interceptors-not-implemented.md` | kotlin, grpc | `wrapService()` interceptor hook exists but no interceptors implemented |
| `KT-002-kotlin-dagger-grpc-di.md` | kotlin, grpc, di | Introduce Dagger 2 DI for the Kotlin service via dagger-grpc |
| `KT-003-kotlin-dagger-client-di.md` | kotlin, grpc, di | Dagger DI for the gRPC client binary; prereq: server DI ticket |
| `CI-002-cross-language-e2e-matrix.md` | testing, ci, grpc, multi-language | Matrix test for every (client lang) × (server lang) combination; prereq: all gRPC client+server tickets |
| `GO-001-go-grpc-client-server.md` | golang, grpc | Implement gRPC service and client; prereq: proto target fix; model after Kotlin |
| `JAVA-001-java-grpc-client-server.md` | java, grpc | Implement gRPC service and client; model after Kotlin |
| `PY-001-python-grpc-client-server.md` | python, grpc | Implement gRPC service and client; prereq: proto fix; model after Kotlin |
| `RUST-001-rust-grpc-client-server.md` | rust, grpc | Add `tonic`; implement gRPC service and client; model after Kotlin |
| `TS-001-typescript-grpc-openapi-variant.md` | typescript, grpc, openapi | New language variant: gRPC svc+client (Kotlin-compatible) + OpenAPI REST; dual yarn/npm + Bazel build |

---

## Resolved

| Summary | Resolution |
|---|---|
| Machine-specific cache config in repo `.bazelrc` | Resolved directly: `--disk_cache` and `--remote_cache` lines removed from `.bazelrc`; developers configure cache in `user.bazelrc` (gitignored) or `~/.bazelrc` |
| Buildkite CI pipeline (issue #13, PR #14) | Implemented pipeline mirroring GitHub Actions: Linux steps via Docker plugin, macOS steps on native agent (`os=macos`). Fixes during stabilization: cluster assignment, arch-aware bazelisk download, `build-essential` for `rules_cc`, Go image bumped to 1.25, `$$`-escaped Buildkite variable interpolation in Docker command blocks. |
