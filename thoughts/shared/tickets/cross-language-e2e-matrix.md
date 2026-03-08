---
date: 2026-03-08
status: open
priority: low
area: testing, ci, grpc, multi-language
---

# Cross-language gRPC client/server e2e matrix test

## Summary

Once multiple languages have full gRPC client and server implementations, run a scheduled
matrix test that validates every `(client language) × (server language)` combination talks
to each other correctly over the wire.

## Motivation

The polyglot project's core claim is that the gRPC contract defined in `//protobuf:balance_rpc`
is language-agnostic. Today only Kotlin has both a client and a server. As Go, Java, Python,
and Rust gain full gRPC implementations (see their respective tickets), we need a way to
confirm cross-language interoperability is maintained — not just within a language.

## Proposed Approach

- A matrix workflow (GitHub Actions or Buildkite) parameterised by `client_lang` and
  `server_lang`.
- For each pair: start the server binary, run the client binary against it, assert on exit
  code and stdout.
- Run on a schedule (e.g. nightly or weekly), not per-PR — the test is too expensive to
  block merges.
- As languages are added to the matrix, the workflow is updated; the test itself does not
  need to change.

## Prerequisites

The following tickets must be substantially complete before the matrix is meaningful:

- `thoughts/shared/tickets/go-grpc-client-server.md`
- `thoughts/shared/tickets/java-grpc-client-server.md`
- `thoughts/shared/tickets/python-grpc-client-server.md`
- `thoughts/shared/tickets/rust-grpc-client-server.md`

## Context

Deferred from the Kotlin OTel instrumentation plan
(`thoughts/shared/plans/2026-03-07-kotlin-otel-instrumentation.md`) to keep that plan
focused. The Testcontainers-based OTel integration test in that plan is a related but
separate concern.