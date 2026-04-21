---
id: TS-001
title: Add TypeScript variant with gRPC client/server and OpenAPI REST endpoints
area: typescript, grpc, openapi, bazel
status: open
created: 2026-03-05
---

# Feature: Add TypeScript variant with gRPC client/server and OpenAPI REST endpoints

## Summary

Add a TypeScript/Node.js implementation of the `BalanceBrackets` service as a new top-level language variant. This is the most feature-rich of the new language additions, as it should expose both gRPC and REST (OpenAPI) interfaces.

## Goals

### gRPC
- gRPC server: implement `BalanceBrackets` service, interoperable with Kotlin client and server
- gRPC client: connect to any compatible server (Kotlin, Go, etc.), send a `BalanceRequest`, print result
- Full bidirectional compatibility: a Kotlin client should talk to a TypeScript server and vice versa

### REST / OpenAPI
- Expose the same `Balance` operation as a RESTful HTTP endpoint
- Generate (or serve) an OpenAPI spec from the service definition
- Consider `grpc-gateway`, `connect-es` with REST support, or a dual-stack framework (e.g. `@grpc/grpc-js` + Express/Fastify with OpenAPI middleware)

### Build System
- **Idiomatic Node.js build**: buildable with `yarn` or `npm` as a standard TypeScript/Node project, without requiring Bazel
- **Bazel build**: also buildable with Bazel, consistent with all other language variants in the repo; structure and conventions modeled after the Kotlin variant
- Proto codegen should work in both build paths (yarn/npm for the native path, Bazel rules for the Bazel path)

### Algorithm
- Implement the same balanced bracket checker as the other languages
- Include the `foo()` proto demo function using the `Something` message from `example.proto`

## Open Design Questions (defer to research/planning phase)

- Which gRPC-Node library? (`@grpc/grpc-js` is most common; `connect-es` / `@connectrpc/connect` is a newer alternative with built-in REST/OpenAPI support)
- Which Bazel ruleset? (`rules_ts`, `aspect_rules_ts`, or `rules_nodejs`)
- How to handle proto codegen for the Node build path? (`buf`, `protoc` with `ts-proto` or `protoc-gen-js`)
- OpenAPI generation strategy: derive from proto annotations, or maintain a separate spec?

## References

- `protobuf/brackets_service.proto` — service definition
- `protobuf/example.proto` — `Something` message for the demo function
- `kotlin/` — reference implementation for both gRPC design and Bazel structure
