---
id: CI-003
title: Replace TCP readiness check with gRPC-level probe in Kotlin integration test
area: ci, testing, kotlin
status: open
created: 2026-04-21
---

## Summary

The Kotlin integration test script checks for TCP connectivity before sending requests, but Armeria's gRPC stack may not be ready to process requests at the moment the TCP socket opens. This causes intermittent socket-disconnection failures on server startup.

## Current State

`.buildkite/scripts/kotlin-integration-test.sh` uses `/dev/tcp/localhost/${PORT}` to detect when the service is ready. TCP-open does not guarantee gRPC is ready — Armeria accepts connections before its request pipeline is fully initialized. The result is an occasional reset/disconnection on the very first client call.

Mitigation in place: Buildkite step-level `retry.automatic` (added in CI-003's companion PR) reduces perceived flake rate, but does not fix the root cause.

## Goals / Acceptance Criteria

- [ ] Replace the TCP-only readiness loop with a probe that confirms gRPC is actually ready to handle requests — e.g. a short retry loop on the first real client call (with brief backoff), or a dedicated gRPC health-check probe if a health endpoint is added
- [ ] Server startup race is eliminated: first test invocation does not fail due to a partially-initialized gRPC stack
- [ ] No increase in average CI wall time (probe should be fast on the happy path)
- [ ] Both Linux and macOS integration test steps pass reliably

## References

- Script: `.buildkite/scripts/kotlin-integration-test.sh:50-58` (TCP readiness loop)
- Pipeline: `.buildkite/pipeline.yml` (Kotlin integration test steps)
- Server entry point: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/service.kt`
