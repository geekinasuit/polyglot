---
id: PY-008
title: Add unit and integration tests for Python gRPC service and client
area: python, testing
status: open
created: 2026-04-25
---

## Summary

Add comprehensive tests for the Python gRPC service and client implementations, ensuring they work correctly and match the test coverage of other language variants.

## Current State

- Tests exist only for the core bracket balancing library
- No tests for gRPC service or client functionality

## Goals

- Add unit tests for service endpoint logic
- Add integration tests for client-server communication
- Test error handling and edge cases
- Follow testing philosophy: prefer unit tests, fakes over mocks

## Acceptance Criteria

- Service tests cover all RPC methods
- Client tests verify correct request/response handling
- Tests pass with `bazel test`
- Test coverage matches other language variants

## References

- Testing philosophy in AGENTS.md
- Kotlin tests: `kotlin/src/test/kotlin/bracketskt/BracketsTest.kt`
- Baseline: 8 unit test cases for bracket algorithm