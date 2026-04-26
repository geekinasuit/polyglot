---
id: PY-007
title: Add Bazel py_binary targets for Python service and client
area: python, bazel
status: open
created: 2026-04-25
---

## Summary

Add Bazel `py_binary` targets for the Python gRPC service and client executables, providing runnable binaries similar to the Kotlin `java_binary` targets.

## Current State

- No executable targets defined
- Only library and test targets exist

## Goals

- Add `py_binary` for brackets_service
- Add `py_binary` for brackets_client
- Include necessary runtime dependencies
- Ensure binaries can be run with `bazel run`

## Acceptance Criteria

- `bazel run //python:brackets_service` starts the service
- `bazel run //python:brackets_client` runs the client
- Binaries include all required dependencies

## References

- Kotlin equivalent: `java_binary` targets in `kotlin/BUILD.bazel`