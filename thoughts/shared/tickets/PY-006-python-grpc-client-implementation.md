---
id: PY-006
title: Implement Python gRPC client
area: python, grpc
status: open
created: 2026-04-25
---

## Summary

Implement the Python gRPC client for connecting to bracket balancing services, modeling it after the Kotlin client implementation.

## Current State

- No gRPC client code exists
- No command-line interface for client

## Goals

- Create client code that connects to gRPC service
- Implement command-line interface matching Kotlin client flags
- Accept `--host`, `--port`, input text arguments
- Handle connection errors and display results

## Acceptance Criteria

- Client can connect to running service
- CLI accepts same flags as Kotlin client
- Output format matches Kotlin semantically
- Error handling for connection failures

## References

- Kotlin reference: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/client/client.kt`
- AGENTS.md: "CLI entry points should accept the same flags (`--host`, `--port`, input argument) with the same defaults"