---
id: BUILD-001
title: Migrate grpc_kotlin to bzlmod-native version when available
area: kotlin, bazel, grpc
status: open
created: 2026-03-05
github_issue: 15
plan: thoughts/shared/plans/2026-03-07-grpc-kotlin-bzlmod-migration.md
---

# Future: Migrate grpc_kotlin to bzlmod-native version when available

## Summary

`grpc_kotlin` version 1.5.0 (currently in use) does not support bzlmod natively. The project works around this with:

1. `--enable_workspace=true` in `.bazelrc` to keep the legacy WORKSPACE system active
2. Hard-coded legacy repo name aliases in `MODULE.bazel` (e.g. `com_google_guava_guava`, `kotlinpoet`) to satisfy `grpc_kotlin`'s internal dependency references
3. An explanatory comment in `MODULE.bazel:4-7` documenting the workaround

## When to Act

When a bzlmod-compatible release of `grpc_kotlin` becomes available, research and perform the migration. Key questions to answer at that time:

- Does the new version expose `kt_jvm_grpc_library` and `kt_jvm_proto_library` via bzlmod extension?
- Can the guava/kotlinpoet aliasing in `MODULE.bazel:65-70` be removed?
- Can `--enable_workspace=true` be dropped from `.bazelrc`?
- Does the custom `//util:kt_jvm_proto.bzl` macro still work, or does the new version supersede it?

## Research Starting Point

- Check https://github.com/grpc/grpc-kotlin for bzlmod support status
- Check the Bazel Central Registry: https://registry.bazel.build/modules/grpc_kotlin

## References

- `MODULE.bazel:4-7` — comment explaining the workaround
- `MODULE.bazel:65-70` — guava/kotlinpoet aliases
- `.bazelrc:1` — `--enable_workspace=true`
- `kotlin/BUILD.bazel:2` — loads `kt_jvm_grpc_library` from `@grpc_kotlin//:kt_jvm_grpc.bzl`
