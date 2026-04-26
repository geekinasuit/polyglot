---
id: PY-005
title: Implement Python gRPC service endpoint
area: python, grpc
status: open
created: 2026-04-25
---

## Summary

Implement the Python gRPC service endpoint that provides the bracket balancing functionality, modeling it after the Kotlin `BalanceServiceEndpoint`.

## Current State

- Core bracket balancing algorithm exists in `brackets_lib.py`
- No gRPC service implementation
- No server startup code

## Goals

- Create `BalanceService` class implementing the gRPC service interface
- Handle `BalanceBrackets` RPC calls using the core algorithm
- Add proper error handling and response formatting
- Follow Python gRPC service patterns

## Acceptance Criteria

- Service can receive gRPC requests
- Bracket balancing logic is correctly invoked
- Responses match the protobuf schema
- Unit tests cover service logic

## References

- Kotlin reference: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BalanceServiceEndpoint.kt`
- Protobuf service: `protobuf/brackets_service.proto`