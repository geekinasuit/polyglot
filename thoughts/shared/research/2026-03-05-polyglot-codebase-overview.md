---
date: 2026-03-05T00:00:00-08:00
researcher: Claude (claude-opus-4-6)
git_commit: 3b2a7997f63a0a8c39747000a9e9daa4b291e9e5
branch: HEAD (detached)
repository: polyglot
topic: "Codebase overview: Kotlin gRPC design, Bazel build system, multi-language patterns"
tags: [research, codebase, kotlin, grpc, bazel, protobuf, go, java, python, rust]
status: complete
last_updated: 2026-03-05
last_updated_by: Claude (claude-opus-4-6)
---

# Research: Polyglot Codebase Overview

**Date**: 2026-03-05
**Researcher**: Claude (claude-opus-4-6)
**Git Commit**: `3b2a7997f63a0a8c39747000a9e9daa4b291e9e5`
**Branch**: HEAD (detached, at tag/commit for commit 3b2a799)
**Repository**: polyglot

## Research Question

Comprehensive overview of the polyglot codebase: Kotlin gRPC client/server design, structure and layout, Bazel build system, and other language implementations (recognized as less current than Kotlin).

## Summary

`polyglot` is a multi-language exemplar project demonstrating a single domain algorithm (balanced bracket checking) implemented across Go, Java, Kotlin, Python, and Rust, all sharing a single Protobuf schema, built entirely with Bazel bzlmod. **Kotlin is the only language with a full gRPC service and client**; all other languages only demonstrate protobuf message consumption. The project uses Armeria (not grpc-java's netty server) on the server side, grpc-kotlin coroutine stubs on the client side, and a custom Starlark macro for Kotlin proto-only codegen.

---

## Detailed Findings

### Project Structure

```
polyglot/
├── MODULE.bazel              # bzlmod: all external deps for all languages
├── MODULE.bazel.lock
├── BUILD.bazel               # empty, pins root package
├── .bazelrc                  # flags: Java 21, disk cache, enable_workspace
├── Makefile                  # native Go toolchain commands only (not Bazel)
├── go.work / go.work.sum     # Go workspace: 2 modules
├── Cargo.toml / Cargo.lock   # Rust workspace: 1 member (rust/bracketslib)
├── requirements.txt          # Python deps: pytruth, protobuf, six, wheel
├── protobuf/
│   ├── BUILD.bazel           # proto_library targets: :protobuf, :balance_rpc
│   ├── example.proto         # Something message (id, name, labels)
│   └── brackets_service.proto# BalanceBrackets service + messages
├── kotlin/                   # PRIMARY: full gRPC service + client
├── java/                     # brackets algorithm + proto message only
├── golang/                   # brackets algorithm + proto message only
├── python/                   # brackets algorithm + proto message only (proto import disabled)
├── rust/                     # brackets algorithm + proto message only
├── util/
│   ├── BUILD.bazel           # deliberately empty
│   └── kt_jvm_proto.bzl      # custom Starlark macro for Kotlin proto-only codegen
└── thoughts/                 # empty scaffold (shared/research, plans, handoffs, tickets)
```

---

### Kotlin: gRPC Service Design

#### Directory Layout

```
kotlin/
├── BUILD.bazel                          # proto/grpc codegen targets + java_binary wrappers
├── README.md
└── src/
    ├── main/kotlin/
    │   ├── bracketskt/
    │   │   ├── BUILD.bazel              # kt_jvm_library + custom kt_jvm_proto_library
    │   │   └── brackets.kt             # core algorithm (pure library)
    │   └── com/geekinasuit/polyglot/brackets/
    │       ├── client/
    │       │   ├── BUILD.bazel          # kt_jvm_library: client_lib
    │       │   └── client.kt           # gRPC client (CliktCommand + runBlocking)
    │       └── service/
    │           ├── BUILD.bazel          # kt_jvm_library: service_lib
    │           ├── BalanceServiceEndpoint.kt  # gRPC service impl
    │           └── service.kt          # Armeria server startup
    └── test/kotlin/bracketskt/
        ├── BUILD.bazel
        └── BracketsTest.kt
```

#### gRPC Server

- **File**: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BalanceServiceEndpoint.kt`
- Implements `BalanceBracketsGrpc.AsyncService` (generated Java async interface) **and** `io.grpc.BindableService`
- The `balance()` method uses the **classic Java `StreamObserver` callback pattern** (not coroutines), calling `responseObserver.onNext()` then `responseObserver.onCompleted()` (lines 37–38)
- Uses `BalanceResponse.newBuilder().apply { ... }.build()` with Kotlin `apply` scope function
- Three-path response logic: success → `isBalanced=true`, `BracketsNotBalancedException` → `isBalanced=false/succeeded=true`, any other `Exception` → `succeeded=false`
- `bindService()` override delegates to the static generated `BalanceBracketsGrpc.bindService(this)` (line 41)

- **File**: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/service.kt`
- `BracketsService : CliktCommand` with `host` (default `"localhost"`) and `port` (default `8888`) CLI options via delegated properties
- Uses **Armeria** (not grpc-java netty) as the HTTP/gRPC server: `Server.builder().http(port).service(wrapService(...)).build()`
- `wrapService()` wraps a `BindableService` via `GrpcService.builder().addService().intercept(*interceptors).build()` — interceptors vararg exists as a hook but is always called empty
- Server lifecycle: `server.closeOnJvmShutdown()` + `server.start().join()` (blocking main thread)
- Main: `fun main(vararg args: String) = BracketsService().main(args)` → Kotlin synthesizes `ServiceKt` class

#### gRPC Client

- **File**: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/client/client.kt`
- `BracketsClient : CliktCommand` with `host`, `port`, and `text` (file `inputStream` argument) options
- `run()` is wrapped in `runBlocking { }` — bridges blocking Clikt into coroutine scope
- Channel: `ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()` (standard grpc-java, no Armeria)
- Stub: `BalanceBracketsGrpcKt.BalanceBracketsCoroutineStub(channel)` — the grpc-kotlin coroutine stub
- `stub.balance(request)` is a `suspend` function call inside `runBlocking`
- Response handled with a subject-less `when` expression: `!response.succeeded`, `response.isBalanced`, else
- Main: `fun main(vararg args: String) = BracketsClient().main(args)` → synthesized `ClientKt` class

#### Kotlin Patterns Used

| Pattern | Location | Notes |
|---|---|---|
| Extension function | `brackets.kt:41` | `ArrayDeque<E>.push` wraps `addLast` |
| `apply` scope | `BalanceServiceEndpoint.kt:18` | Builder configured inline |
| `runBlocking` bridge | `client.kt:28` | Clikt `run()` → coroutine scope |
| Trailing lambda syntax | `service.kt:27` | `thenRun { log.info { ... } }` |
| Elvis + throw | `brackets.kt:27` | `removeLastOrNull() ?: throw ...` |
| `vararg` + spread `*` | `service.kt:35,38` | Future interceptor hook |
| Delegated CLI properties | `client.kt:24-26`, `service.kt:21-22` | `by option()` / `by argument()` |
| Kotlin proto DSL | `brackets.kt:44-49` | `something { id = 2; labels += "a" }` |
| KotlinLogging lazy lambda | `service.kt:27` | `log.info { "..." }` |
| `withIndex()` + destructure | `brackets.kt:20` | `for ((i, c) in ...)` |
| `in` operator in `when` | `brackets.kt:22,25` | `in openParentheses` branch |

#### Key Library Choices

| Library | Version | Role |
|---|---|---|
| Armeria + armeria-grpc | 1.26.4 | HTTP/2 server (wraps gRPC services) |
| grpc-netty-shaded | 1.71.0 | Client transport |
| grpc-kotlin-stub | 1.4.1 | `CoroutineStub` base class |
| kotlinx-coroutines-core | 1.10.1 | `runBlocking` |
| clikt-jvm | 5.0.1 | CLI argument parsing |
| kotlin-logging-jvm | 5.1.0 | SLF4J Kotlin wrapper |
| protobuf-kotlin | 4.29.3 | Kotlin proto DSL builder extensions |

---

### Protobuf Definitions

**`protobuf/example.proto`** — shared message proto (no service):
- Package `example_protos`, Java package `com.geekinasuit.polyglot.example.protos`
- `Something` message: `int64 id`, `string name`, `repeated string labels`
- Go package: `github.com/geekinasuit/polyglot/golang/pkg/libs/brackets`

**`protobuf/brackets_service.proto`** — service definition:
- Package `brackets_service`, Java package `com.geekinasuit.polyglot.brackets.service.protos`
- Service `BalanceBrackets` with one RPC: `Balance(BalanceRequest) returns (BalanceResponse)`
- `BalanceRequest`: `string statement`
- `BalanceResponse`: `bool succeeded`, `string error`, `bool is_balanced`
- Go package: `github.com/geekinasuit/polyglot/golang/pkg/services/brackets/proto`

---

### Bazel Build System

#### Module System (bzlmod)

- `MODULE.bazel` (116 lines) is the sole external dependency declaration
- No `WORKSPACE` file (legacy system disabled except where `--enable_workspace` is forced for `grpc_kotlin` compatibility)
- Major rulesets and their versions:

| Ruleset | Version |
|---|---|
| rules_go | 0.57.0 |
| gazelle | 0.45.0 |
| rules_kotlin | 2.1.9 |
| rules_jvm_external | 6.8 |
| rules_java | 8.14.0 |
| rules_rust + rules_rust_prost | 0.65.0 |
| rules_python | 1.6.3 |
| protobuf | 32.1 |
| grpc | 1.74.1 |
| grpc-java | 1.74.0 |
| grpc_kotlin | 1.5.0 |

**Notable bzlmod workaround**: `grpc_kotlin` does not fully support bzlmod and hard-codes legacy repo names like `com_google_guava_guava`. The `MODULE.bazel` works around this by explicitly naming `com_google_guava_guava` in `use_repo` (line 79) and declaring `kotlinpoet` + guava as Maven artifacts with those exact names (lines 65–70). `--enable_workspace=true` is required in `.bazelrc` to support this.

#### .bazelrc Configuration

- `common --enable_workspace=true --java_runtime_version=21` — workspace compatibility mode + Java 21 runtime
- `build --disk_cache /Users/cgruber/.cache/bazel` — machine-specific local disk cache (absolute path)
- `#build --remote_cache bigboi.local:8080` — remote cache server exists but currently disabled

#### Proto Compilation Per Language

| Language | Bazel Rule | Source Proto | Output |
|---|---|---|---|
| Java | `java_proto_library` (`@protobuf//bazel`) | `//protobuf:protobuf` | Java message classes |
| Kotlin (messages, custom) | `kt_jvm_proto_library` (`//util:kt_jvm_proto.bzl`) | `//protobuf:protobuf` | Kotlin DSL extensions |
| Kotlin (messages, grpc-kotlin) | `kt_jvm_proto_library` (`@grpc_kotlin`) | `//protobuf:balance_rpc` | Kotlin message classes |
| Kotlin (gRPC stubs) | `kt_jvm_grpc_library` (`@grpc_kotlin`) | `//protobuf:balance_rpc` | `CoroutineStub` + `AsyncService` |
| Go (messages) | `go_proto_library` (`@rules_go//proto`) | `//protobuf:protobuf` | Go proto package |
| Go (gRPC) | `go_grpc_library` (`@rules_go//proto`) | `//protobuf:protobuf` | Go gRPC client/server |
| Python | `py_proto_library` (`@protobuf//bazel`) | `//protobuf:protobuf` | Python proto classes |
| Rust | `rust_prost_library` (`@rules_rust_prost`) | `//protobuf:protobuf` | Prost-generated Rust types |

#### Build Conventions

- **`java_binary` wrapping `runtime_deps`**: Kotlin executables are declared as `java_binary` targets (not `kt_jvm_binary`) in `kotlin/BUILD.bazel`. The Kotlin library is provided via `runtime_deps`. This is the standard pattern with `rules_kotlin`.
- **Two Kotlin proto paths**: `bracketskt` (example.proto, no gRPC) uses the custom local macro; the gRPC service uses `@grpc_kotlin`'s native rules. The custom macro is lighter-weight for proto-only generation.
- **Go proto embedding**: `go_library` uses `embed = [":proto"]` to merge generated proto types directly into the hand-written library package under the same `importpath`.
- **`associates` in Kotlin tests**: `associates = ["//kotlin/src/main/kotlin/bracketskt"]` grants test access to Kotlin `internal`-scoped members.
- **Package pinning**: Empty `BUILD.bazel` files at `//`, `//rust`, `//python`, `//util` exist only to define Bazel package boundaries.

#### Custom Macro: `util/kt_jvm_proto.bzl`

Copied from `grpc/grpc-kotlin` at commit `a969a91` (Apache 2.0). Provides `kt_jvm_proto_library` for Kotlin protobuf DSL generation (separate from gRPC stub generation).

Key implementation steps:
1. Collect transitive descriptor sets from `ProtoInfo` providers
2. Run `protoc --kotlin_out=<dir>` via `ctx.actions.run()`
3. Zip generated `.kt` files into a `.srcjar` using `@bazel_tools//tools/zip:zipper`
4. Create a `kt_jvm_library` from the `.srcjar`, exporting the underlying `java_proto_library`

Internal targets are named `<name>_DO_NOT_DEPEND_java_proto` and `<name>_DO_NOT_DEPEND_kt_proto` — the naming convention signals implementation details not for external use.

---

### Other Language Implementations

All non-Kotlin languages implement the same algorithm and same `foo()` proto demonstration function. None implement a gRPC service or client.

#### Shared Algorithm Pattern

All implementations use a stack-based bracket checker with identical error message formats:
- Close with no opener: `"closing bracket {c} with no opening bracket at char {i+1}"`
- Bracket type mismatch: `"closing bracket {c} at char {i+1} mismatched with last opening bracket {open}"`
- Unclosed openers: `"opening brackets without closing brackets found: [{stack}]"`

#### Go

- **Package structure**: 2 Go modules in 1 workspace: `golang/pkg/libs/brackets` (lib) and `golang/cmd/brackets` (CLI binary)
- **Stack**: `linkedliststack` from `github.com/emirpasic/gods`
- **CLI**: `github.com/spf13/cobra`, default text `"Hello, (world)!"`
- **Proto demo**: `Something{}` struct literal with `Id: 2`, `Name: "blah"`, `Labels: []string{"Q", "R"}`
- **Native build**: `generate.go` contains `//go:generate` directives for running `protoc` outside Bazel; `Makefile` drives this with `go-generate`, `go-build`, `go-test`, `go-run`
- **Key file**: `golang/pkg/libs/brackets/generate.go` — listed in Bazel srcs (line 8) with comment noting it's for native go build only

#### Java

- **Stack**: `ArrayDeque<Character>`
- **Proto demo**: `Something.newBuilder()` with id=1, name="Foo", labels ["A", "B"] via `addAllLabels`
- **Tests**: 8 test methods using Truth + JUnit 4, identical behavioral coverage to other languages
- **No gRPC**: Only `java_proto_library` for example.proto; `brackets_service.proto` is not wired up in Java

#### Python

- **Stack**: Python `list` with `append`/`pop`
- **Proto demo**: `foo()` returns `None` — the proto import is commented out (`# import protobuf.example_pb2 as pb`)
- **Test framework**: `unittest.TestCase` + `pytruth` (`truth.truth.AssertThat`)
- **Proto import**: Intentionally disabled; the Bazel target `example_proto_py` exists but the library doesn't use it at runtime

#### Rust

- **Stack**: `Vec<char>` with `push`/`pop`
- **Proto**: Uses `prost`-generated types via `rust_prost_library`; `use protobuf::example_protos::Something`
- **Proto demo**: `foo()` returns `Option<Something>` with struct literal syntax
- **Test framework**: `assertor` crate (with `anyhow` feature)
- **No direct prost/tonic deps** in `Cargo.toml` — proto codegen is entirely Bazel-managed

---

## Code References

- `protobuf/brackets_service.proto:12-14` — Service definition: `BalanceBrackets` with `Balance` RPC
- `protobuf/example.proto:14-18` — `Something` message used by all language demos
- `kotlin/BUILD.bazel:4-15` — `kt_jvm_proto_library` + `kt_jvm_grpc_library` for service proto
- `kotlin/BUILD.bazel:17-33` — `java_binary` wrappers for client and service
- `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BalanceServiceEndpoint.kt:15` — Service impl: `BalanceBracketsGrpc.AsyncService + BindableService`
- `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/service.kt:26` — Armeria server construction
- `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/service.kt:34-39` — `wrapService()` with interceptor hook
- `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/client/client.kt:36-37` — Channel + coroutine stub construction
- `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/client/client.kt:28` — `runBlocking` bridge
- `kotlin/src/main/kotlin/bracketskt/brackets.kt:41` — `ArrayDeque<E>.push` extension
- `util/kt_jvm_proto.bzl:21-67` — `protoc --kotlin_out` + srcjar packaging implementation
- `util/kt_jvm_proto.bzl:97` — Public `kt_jvm_proto_library` macro signature
- `kotlin/src/main/kotlin/bracketskt/BUILD.bazel:13-19` — Custom macro usage for example.proto
- `golang/pkg/libs/brackets/generate.go` — `//go:generate` directives for native Go proto build
- `golang/pkg/libs/brackets/BUILD.bazel:4-16` — `go_library` with `embed = [":proto"]` pattern
- `kotlin/src/test/kotlin/bracketskt/BUILD.bazel:6` — `associates` for Kotlin `internal` member access
- `MODULE.bazel:65-70` — guava/kotlinpoet workaround for `grpc_kotlin` bzlmod incompatibility
- `.bazelrc:1` — `--enable_workspace=true` required for grpc_kotlin
- `.bazelrc:5` — machine-specific disk cache path

---

## Architecture Insights

1. **Kotlin is the reference implementation**: Only Kotlin implements the full gRPC service+client. Other languages demonstrate the algorithm and proto message binding but are not yet brought up to the same level.

2. **Armeria as gRPC server**: The server uses Armeria instead of the grpc-java netty server. Armeria provides a higher-level HTTP/2 framework that accepts standard `BindableService` implementations. This is an intentional architectural choice — the service impl itself uses standard grpc-java interfaces, keeping it portable.

3. **Asymmetric client/server**: The server uses Armeria but the client uses `grpc-netty-shaded` directly. The `BalanceBracketsCoroutineStub` is the grpc-kotlin coroutine stub, enabling `suspend` function call style on the client.

4. **Hybrid gRPC style**: The service implementation uses the Java callback style (`StreamObserver`) while the client uses the Kotlin coroutine stub. This is because the `AsyncService` interface is the generated Java interface — grpc-kotlin generates coroutine stubs primarily for the client side.

5. **Two proto codegen paths in Kotlin**: The project uses both the custom `//util:kt_jvm_proto.bzl` macro (for `example.proto`, no service) and `@grpc_kotlin`'s native rules (for `brackets_service.proto`, full gRPC). The split avoids pulling grpc-kotlin's heavier dependency chain into the simple message library.

6. **bzlmod with workspace escape hatch**: The project is fully bzlmod but requires `--enable_workspace=true` because `grpc_kotlin` 1.5.0 doesn't support bzlmod natively. This is called out explicitly in `MODULE.bazel` lines 4–7.

7. **Dual build systems for Go**: Go can be built with either Bazel (`bazel build //golang/...`) or native Go tools (`make go-build`). The `generate.go` file with `//go:generate` directives appears in both build graphs — Bazel lists it as a source (with a clarifying comment), while `make go-generate` actually runs it.

8. **Proto as the contract boundary**: All language implementations share `//protobuf:protobuf` and `//protobuf:balance_rpc` as their sole proto sources. Generated code is never checked in — always derived at build time.

---

## Historical Context (from thoughts/)

No prior research documents exist in `thoughts/`. The directory scaffold (`shared/research/`, `shared/plans/`, `shared/handoffs/`, `shared/tickets/`) is in place with `.gitkeep` files but contains no content. This is the first research document for this project.

---

## Open Questions

None remaining.

## Resolved / Ticketed

- **Cache config in repo `.bazelrc`** — resolved: machine-specific cache config removed from `.bazelrc`; developers should configure cache in `user.bazelrc` (gitignored) or `~/.bazelrc`
- **`grpc_kotlin` bzlmod migration** — planned when a bzlmod-native version becomes available; ticketed: `thoughts/shared/tickets/grpc-kotlin-bzlmod-migration.md`
- **Go `go_grpc_library` wrong proto target** (`golang/pkg/libs/brackets/BUILD.bazel:35`) — ticketed: `thoughts/shared/tickets/go-grpc-library-wrong-proto-target.md`
- **Python proto import disabled** — incomplete work, ticketed: `thoughts/shared/tickets/python-proto-integration-incomplete.md`
- **Kotlin gRPC interceptors not wired up** — incomplete work, ticketed: `thoughts/shared/tickets/kotlin-grpc-interceptors-not-implemented.md`
- **gRPC service/client for Go, Java, and Rust** — confirmed future work; Kotlin is the current reference implementation
