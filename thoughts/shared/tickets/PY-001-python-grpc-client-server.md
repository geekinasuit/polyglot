---
id: PY-001
title: Implement Python gRPC client and server
area: python, grpc
status: open
created: 2026-03-05
---

# Feature: Implement Python gRPC client and server

## Summary

Bring the Python implementation up to parity with Kotlin by implementing a gRPC server and client for the `BalanceBrackets` service.

## Current State

- Core algorithm (`brackets_lib.py`) and tests are complete
- `py_proto_library` for `example.proto` exists but the import is disabled (see `thoughts/shared/tickets/PY-002-python-proto-integration-incomplete.md`)
- No gRPC service proto wired up; no server or client code exists

## Goals

- gRPC server: implement `BalanceBrackets` service, listening on a configurable host/port
- gRPC client: connect to any compatible server (Kotlin or Python), send a `BalanceRequest`, print result
- Server and client should be interoperable with the Kotlin implementation
- Add `py_grpc_library` (or equivalent) Bazel target for `brackets_service.proto`
- CLI interface (consider `click` or `argparse`, consistent with idiomatic Python)
- Bazel targets for both binaries
- Resolve the proto import issue (see prerequisite ticket) as part of this work

## References

- `python/brackets_py_lib/BUILD.bazel` — existing Python build targets
- `protobuf/brackets_service.proto` — service definition
- `kotlin/` — reference implementation for both gRPC design and Bazel structure
- `thoughts/shared/tickets/PY-002-python-proto-integration-incomplete.md` — prerequisite
