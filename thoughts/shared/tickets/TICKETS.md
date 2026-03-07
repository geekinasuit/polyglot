# Tickets

Canonical summary of all open and resolved work items. Individual ticket files in this directory contain full detail. This file is the index — do not duplicate ticket content here, and do not maintain ticket inventories elsewhere (AGENT.md, research docs, etc. should reference this file instead).

## How This Works with GitHub Issues

Ticket files are the source of truth. GitHub Issues are a thin, work-time-only layer used solely for PR cross-referencing (`fixes #N`):

- **Don't create a GitHub issue upfront** — only create one when you are about to open a PR for the work
- **Keep the GitHub issue sparse**: title, one-line summary, and this exact line so automation can find the ticket file:
  ```
  **Ticket:** `thoughts/shared/tickets/<filename>.md`
  ```
- **When opening a PR**: update the ticket file (`status: resolved`, `github_issue: N`) and this file (move row to Resolved) in the same PR branch. This is the primary mechanism — ticket state changes are reviewed and merged with the work.
- A GitHub Action (see `github-action-ticket-close-sync.md`) will act as a safety net for cases where the PR didn't include the ticket update. That action is permitted to commit directly to `main` for bookkeeping-only changes — a narrow, explicit exception to the no-direct-push rule that applies to the GitHub Actions bot only. Agents do not share this exception and must go through a branch and PR even for ticket bookkeeping.

---

## Open

| File | Priority | Area | Summary |
|---|---|---|---|
| `github-action-ticket-close-sync.md` | medium | automation, github, tickets | GitHub Action safety net: sync ticket state when issue is closed without a PR ticket update |
| `grpc-kotlin-bzlmod-migration.md` | low | kotlin, bazel | Migrate to bzlmod-native `grpc_kotlin` when upstream supports it |
| `go-grpc-library-wrong-proto-target.md` | low | golang, bazel | `go_grpc_library` wraps wrong proto (`//protobuf` → should be `//protobuf:balance_rpc`) |
| `python-proto-integration-incomplete.md` | low | python, protobuf | Proto import disabled; `foo()` returns `None` |
| `kotlin-grpc-interceptors-not-implemented.md` | low | kotlin, grpc | `wrapService()` interceptor hook exists but no interceptors implemented |
| `go-grpc-client-server.md` | medium | golang, grpc | Implement gRPC service and client; prereq: proto target fix; model after Kotlin |
| `java-grpc-client-server.md` | medium | java, grpc | Implement gRPC service and client; model after Kotlin |
| `python-grpc-client-server.md` | medium | python, grpc | Implement gRPC service and client; prereq: proto fix; model after Kotlin |
| `rust-grpc-client-server.md` | medium | rust, grpc | Add `tonic`; implement gRPC service and client; model after Kotlin |
| `typescript-grpc-openapi-variant.md` | medium | typescript, grpc, openapi | New language variant: gRPC svc+client (Kotlin-compatible) + OpenAPI REST; dual yarn/npm + Bazel build |

---

## Resolved

| Summary | Resolution |
|---|---|
| Machine-specific cache config in repo `.bazelrc` | Resolved directly: `--disk_cache` and `--remote_cache` lines removed from `.bazelrc`; developers configure cache in `user.bazelrc` (gitignored) or `~/.bazelrc` |
| Buildkite CI pipeline (issue #13, PR #14) | Implemented pipeline mirroring GitHub Actions: Linux steps via Docker plugin, macOS steps on native agent (`os=macos`). Fixes during stabilization: cluster assignment, arch-aware bazelisk download, `build-essential` for `rules_cc`, Go image bumped to 1.25, `$$`-escaped Buildkite variable interpolation in Docker command blocks. |
