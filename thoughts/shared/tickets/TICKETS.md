# Tickets

Canonical summary of all open and resolved work items. Individual ticket files in this directory contain full detail. This file is the index — do not duplicate ticket content here, and do not maintain ticket inventories elsewhere (AGENT.md, research docs, etc. should reference this file instead).

---

## Open

| File | Priority | Area | Summary |
|---|---|---|---|
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
