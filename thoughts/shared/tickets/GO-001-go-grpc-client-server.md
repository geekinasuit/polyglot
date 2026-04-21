---
id: GO-001
title: Implement Go gRPC client and server
area: golang, grpc
status: open
created: 2026-03-05
---

# Feature: Implement Go gRPC client and server

## Summary

Bring the Go implementation up to parity with Kotlin by implementing a gRPC server and client for the `BalanceBrackets` service.

## Current State

- Core algorithm (`brackets.go`) and CLI binary (`golang/cmd/brackets/`) are complete
- `go_grpc_library` Bazel target exists but references the wrong proto (see `thoughts/shared/tickets/GO-002-go-grpc-library-wrong-proto-target.md`)
- No gRPC server or client code exists

## Goals

- gRPC server: implement `BalanceBrackets` service, listening on a configurable host/port
- gRPC client: connect to any compatible server (Kotlin or Go), send a `BalanceRequest`, print result
- Server and client should be interoperable with the Kotlin implementation
- CLI interface consistent with existing Go binary style (cobra)
- Bazel targets for both binaries
- Fix the `go_grpc_library` proto target bug as a prerequisite

## References

- `golang/pkg/libs/brackets/BUILD.bazel:35` — existing `go_grpc_library` placeholder
- `protobuf/brackets_service.proto` — service definition
- `kotlin/` — reference implementation for both gRPC design and Bazel structure
- `thoughts/shared/tickets/GO-002-go-grpc-library-wrong-proto-target.md` — prerequisite bug fix
