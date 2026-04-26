---
date: 2026-04-25
author: Qwen Code
ticket: PY-001
status: draft
---

# Python Parity Implementation Plan

## Goal

Bring the Python implementation to full feature parity with the Kotlin reference, using Python community norms and equivalent patterns for libraries, async handling, CLI, logging, and telemetry.

## Context

The Python project currently has a core bracket balancing library and basic tests, but lacks gRPC service/client, proper Bazel integration, and observability features. Kotlin provides the reference implementation with Armeria server, Dagger DI, Clikt CLI, OpenTelemetry telemetry, and coroutine-based async I/O.

## Approach

Follow a complexity-parity sprint sequence (not time-boxed) breaking work into coherent chunks that can be implemented, tested, and reviewed independently. Use TDD principles throughout, with test-first design for new components.

## Steps

### Sprint 1: Foundations (Library Selection & Structure)
**Complexity**: Low - Establishes baseline without deep implementation
**Tickets**: PY-009, PY-003
**Deliverables**:
- Finalize library choices in PY-009 (add to requirements.txt, MODULE.bazel)
- Restructure codebase to Pythonic layout (packages, tests/)
- Update Bazel targets for new structure
- Commit and merge foundation changes

### Sprint 2: gRPC Infrastructure
**Complexity**: Medium - Core communication layer
**Tickets**: PY-004, PY-002
**Deliverables**:
- Set up py_grpc_library for generated stubs
- Fix proto integration (enable imports, update foo())
- Basic gRPC channel/server setup tests
- Merge infrastructure PR

### Sprint 3: Service Implementation
**Complexity**: High - Business logic + server
**Tickets**: PY-005, PY-007 (service binary)
**Deliverables**:
- Implement BalanceService with telemetry, logging, error handling
- Add Bazel py_binary for service
- CLI flags for host/port/environment
- Unit tests for service logic
- Merge service PR

### Sprint 4: Client Implementation
**Complexity**: Medium - User-facing client
**Tickets**: PY-006, PY-007 (client binary)
**Deliverables**:
- Implement async client with telemetry
- CLI interface matching Kotlin
- Input handling (stdin), output formatting
- Bazel py_binary for client
- Merge client PR

### Sprint 5: Testing & Integration
**Complexity**: Medium-High - Validation and cross-component testing
**Tickets**: PY-008
**Deliverables**:
- Comprehensive unit tests for all components
- Integration tests for client-server communication
- Async test setup with pytest-asyncio
- End-to-end parity verification
- Final merge

## Risks

- Async patterns in Python may differ from Kotlin coroutines - validate with integration tests
- OpenTelemetry setup complexity - test with local OTLP collector
- Bazel Python rules edge cases - iterate on BUILD files

## Done

All sprints completed, Python variant interoperable with Kotlin (and other languages), full test coverage, and exemplar Python code quality.