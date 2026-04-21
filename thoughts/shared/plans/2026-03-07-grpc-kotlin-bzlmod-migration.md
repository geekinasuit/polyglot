# grpc-kotlin bzlmod Migration Plan

## Overview

Remove the legacy WORKSPACE compatibility workarounds introduced to accommodate
`grpc_kotlin 1.5.0` before it was published to the Bazel Central Registry. The
BCR now carries `grpc_kotlin 1.5.0` with a proper `MODULE.bazel`, making the
workarounds unnecessary.

## Current State Analysis

The project already declares `bazel_dep(name = "grpc_kotlin", version = "1.5.0")`
and therefore already uses the BCR-patched version. Three workarounds remain from
when `grpc_kotlin` was not BCR-native:

1. **`.bazelrc` lines 1–3** — `--enable_workspace=true` (set twice, redundantly)
2. **`MODULE.bazel` lines 4–7** — explanatory comment about the workaround
3. **`MODULE.bazel` lines 65–70** — guava, kotlinpoet, and coroutines entries
   added specifically for grpc-kotlin's legacy repo name requirements
4. **`MODULE.bazel` line 79** — `com_google_guava_guava` and `grpc_kotlin_maven`
   explicitly listed in `use_repo(maven, ...)` to expose those compat repos

No project `BUILD.bazel` files reference `@com_google_guava_guava` or
`@grpc_kotlin_maven` directly — the workarounds are confined to module config.

### Why this is now safe to remove

- `grpc_kotlin 1.5.0`'s BCR `MODULE.bazel` declares its own `grpc_kotlin_maven`
  extension, meaning Bazel manages those transitive deps internally within the
  `grpc_kotlin` module.
- `kt_jvm_grpc.bzl` loads only from proper bzlmod module names (`@grpc-java`,
  `@protobuf`, `@rules_kotlin`, `@rules_java`).
- Internal grpc-kotlin BUILD files reference `@grpc_kotlin_maven//:...` (namespaced,
  not bare `@com_google_guava_guava`), which the BCR module resolves internally.

## Desired End State

- `.bazelrc` contains no `--enable_workspace` flags
- `MODULE.bazel` contains no grpc-kotlin compat entries or explanatory comment
- `bazel build //kotlin/...` and `bazel test //kotlin/...` pass cleanly

## What We're NOT Doing

- Upgrading `grpc_kotlin` to a different version (staying on `1.5.0`)
- Changing any `BUILD.bazel` files in the project
- Modifying the Kotlin service, client, or proto codegen targets
- Removing the custom `//util:kt_jvm_proto.bzl` macro (unrelated)

## Implementation

### Step 1 — Remove `.bazelrc` workspace flags

**File**: `.bazelrc`

Remove lines 1–3:
```
common --enable_workspace=true --java_runtime_version=21
build --enable_workspace
info --enable_workspace
```

Replace with:
```
common --java_runtime_version=21
```

### Step 2 — Clean up `MODULE.bazel`

**File**: `MODULE.bazel`

1. Delete lines 4–7 (the explanatory comment block).

2. Delete lines 65–70 (the grpc-kotlin compat maven entries):
   ```python
       # grpc-kotlin deps
       "com.google.guava:guava:33.3.1-android",
       "com.squareup:kotlinpoet:1.14.2",
       "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1",
       "org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.10.1",
   ```
   > Note: `kotlinx-coroutines-core` (without `-jvm` suffix, line 57) is a real
   > runtime dependency for the Kotlin client — keep it.

3. On line 79, remove `"com_google_guava_guava"` and `"grpc_kotlin_maven"` from
   the `use_repo` call, leaving only:
   ```python
   use_repo(maven, "maven")
   ```

### Step 3 — Verify

```bash
bazel build //kotlin/...
bazel test //kotlin/...
```

Both must pass cleanly with no WORKSPACE-related warnings or errors.

## Implementation Note

During implementation, removing `use_repo(..., "grpc_kotlin_maven")` caused the build to fail
with `No repository visible as '@grpc_kotlin_maven' from main repository`. The fix was to keep
`"grpc_kotlin_maven"` in the `use_repo` call — this exposes the repo created by grpc_kotlin's
internal BCR maven extension to our module via the shared `rules_jvm_external` extension. This
pattern is confirmed by grpc-kotlin's own bzlmod example (`bzl-examples/bzlmod/MODULE.bazel`).

The `com_google_guava_guava` entry and the guava/kotlinpoet maven artifacts were pure WORKSPACE-era
overhead and are now cleanly removed.

## Success Criteria

### Automated Verification
- [x] `bazel build //kotlin/...` succeeds with no errors
- [x] `bazel test //kotlin/...` passes
- [x] No `--enable_workspace` warnings in build output
- [x] All other language targets still build: `bazel build //...`

### Manual Verification
- [ ] Start the Kotlin gRPC service and connect with the client; confirm balanced/
      unbalanced responses work correctly

## Rollback

If the build fails after removing `--enable_workspace=true`, re-add it and open
an investigation issue with the specific error. Likely failure modes:
- A stale WORKSPACE file reference inside grpc-kotlin's own targets (investigate
  with `bazel query` to trace the dep)
- A `rules_jvm_external` lock file mismatch requiring `REPIN=1`

## References

- Ticket: `thoughts/shared/tickets/BUILD-001-grpc-kotlin-bzlmod-migration.md`
- Codebase overview: `thoughts/shared/research/2026-03-05-polyglot-codebase-overview.md`
- BCR module: `https://registry.bazel.build/modules/grpc_kotlin`
- grpc-kotlin bzlmod issue: `https://github.com/grpc/grpc-kotlin/issues/398`
- grpc-kotlin bzlmod PR (open): `https://github.com/grpc/grpc-kotlin/pull/618`