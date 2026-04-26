---
id: PY-009
title: Research and select Python community-standard libraries for gRPC microservice implementation
area: python, libraries
status: open
created: 2026-04-25
---

## Summary

As an exemplar Python project, select libraries that represent current Python community best practices for gRPC microservices, CLI argument parsing, logging, telemetry/metrics, and other facilities. Prefer well-established, actively maintained libraries that Python developers would expect to see in a production-quality project.

## Current State

- Basic gRPC functionality will use `grpcio` (standard)
- No decisions made for CLI parsing, logging, telemetry, or microservice framework choices
- Project should demonstrate idiomatic Python development patterns

## Goals

Based on Kotlin patterns observed (Armeria server, Dagger DI, Clikt CLI, OpenTelemetry, KotlinLogging, coroutines):

- **gRPC Framework**: `grpcio` with asyncio for async patterns (equivalent to Kotlin coroutines); `grpc-interceptor` for middleware/hooks
- **CLI Args**: `click` for rich CLI interface (equivalent to Clikt, more Pythonic than argparse)
- **Logging**: `logging` with `structlog` for structured logging (equivalent to KotlinLogging/slf4j structured logs)
- **Telemetry/Metrics**: `opentelemetry-distro` + `opentelemetry-exporter-otlp-proto-grpc` for traces, counters, histograms (direct equivalent to Kotlin OpenTelemetry usage)
- **Config**: `pyyaml` for YAML config loading (matches Kotlin's config patterns)
- **DI/Microservice Framework**: No heavy DI framework (minimal deps); manual dependency passing or `dependency-injector` if needed. Simple gRPC server launch (equivalent to Armeria but Pythonic)
- **Async Patterns**: asyncio throughout (equivalent to Kotlin suspend functions/coroutines)
- **Testing**: `pytest` + `pytest-asyncio` for async tests
- Ensure selections align with Bazel MODULE.bazel dependencies and Python community norms

## Acceptance Criteria

- Library choices documented and justified based on Python community adoption
- Dependencies added to `requirements.txt` and Bazel MODULE.bazel
- Code examples demonstrate proper usage patterns
- Libraries are actively maintained and have good community support

## References

- AGENTS.md: "minimal dependencies, prefer standard library or well-established ecosystem libraries"
- Python gRPC best practices: https://grpc.io/docs/languages/python/
- Exemplar goal: project should look like a GOOD Python project to Python developers