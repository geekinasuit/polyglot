---
id: JAVA-001
title: Implement Java gRPC client and server
area: java, grpc
status: open
created: 2026-03-05
---

# Feature: Implement Java gRPC client and server

## Summary

Bring the Java implementation up to parity with Kotlin by implementing a gRPC server and client for the `BalanceBrackets` service.

## Current State

- Core algorithm (`Brackets.java`) and tests are complete
- `java_proto_library` for `example.proto` exists; `brackets_service.proto` is not wired up in Java at all
- No gRPC server or client code exists

## Goals

- gRPC server: implement `BalanceBrackets` service, listening on a configurable host/port
- gRPC client: connect to any compatible server (Kotlin or Java), send a `BalanceRequest`, print result
- Server and client should be interoperable with the Kotlin implementation
- Add `java_grpc_library` (or equivalent) Bazel target for `brackets_service.proto`
- CLI interface (consider picocli or a similar library consistent with the project's style)
- Bazel targets for both binaries

## References

- `java/src/main/java/bracketsjava/BUILD.bazel` — existing Java build targets
- `protobuf/brackets_service.proto` — service definition
- `kotlin/` — reference implementation for both gRPC design and Bazel structure
