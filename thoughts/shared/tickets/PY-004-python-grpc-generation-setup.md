---
id: PY-004
title: Set up gRPC code generation for Python using py_grpc_library
area: python, grpc, bazel
status: open
created: 2026-04-25
---

## Summary

Add Bazel `py_grpc_library` targets to generate Python gRPC stubs from the shared protobuf definitions, mirroring the Kotlin `kt_jvm_grpc_library` setup.

## Current State

- `py_proto_library` exists for `//protobuf` but no gRPC stubs are generated
- Proto import is disabled in `brackets_lib.py` due to missing gRPC dependencies

## Goals

- Add `py_grpc_library` target in `python/BUILD.bazel`
- Generate gRPC service and client stubs
- Enable proto imports in Python code

## Acceptance Criteria

- Bazel can build `py_grpc_library` target
- Generated Python files appear in Bazel output
- Proto messages can be imported and used in Python code

## References

- Kotlin equivalent: `kt_jvm_grpc_library` in `kotlin/BUILD.bazel`
- Protobuf target: `//protobuf:balance_rpc`