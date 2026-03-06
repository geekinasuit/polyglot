---
date: 2026-03-05
status: open
priority: medium
area: rust, grpc
---

# Feature: Implement Rust gRPC client and server

## Summary

Bring the Rust implementation up to parity with Kotlin by implementing a gRPC server and client for the `BalanceBrackets` service.

## Current State

- Core algorithm (`lib.rs`) and tests are complete
- `rust_prost_library` for `example.proto` exists and is used
- No gRPC dependency (`tonic`) exists in `Cargo.toml`; `brackets_service.proto` is not wired up

## Goals

- gRPC server: implement `BalanceBrackets` service using `tonic`, listening on a configurable host/port
- gRPC client: connect to any compatible server (Kotlin or Rust), send a `BalanceRequest`, print result
- Server and client should be interoperable with the Kotlin implementation
- Add `tonic` to `Cargo.toml` workspace dependencies
- Add Bazel targets for gRPC codegen from `brackets_service.proto` (likely `rust_prost_library` with tonic plugin, or `rules_rust_prost` equivalent)
- CLI interface (consider `clap`, consistent with idiomatic Rust)
- Bazel targets for both binaries

## References

- `rust/bracketslib/Cargo.toml` — existing Rust dependencies
- `rust/bracketslib/BUILD.bazel` — existing Bazel targets
- `protobuf/brackets_service.proto` — service definition
- `kotlin/` — reference implementation for both gRPC design and Bazel structure
