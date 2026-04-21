---
id: KT-004
title: Docker container build for the Kotlin service
area: kotlin, docker, deployment
status: open
created: 2026-03-22
---

# Feature: Docker container build for the Kotlin service

## Summary

Package the Kotlin gRPC service as a Docker image suitable for staging and production deployment. Initial scope is the Kotlin service only; other language variants can follow once the pattern is established.

## Current State

- Kotlin service binary builds via Bazel (`java_binary` + `runtime_deps`)
- No container image build target exists
- No deployment configuration exists (compose, Helm, etc.)

## Goals

- Bazel `container_image` (or equivalent) target that produces a Docker image for the Kotlin service binary
- Image should be minimal (distroless or equivalent JVM base)
- Config via environment variables consistent with the existing Hoplite config system (env vars already supported via `DEPLOYMENT_ENV`, `POD_NAME`, `OTLP_ENDPOINT`, etc.)
- Separate staging and production profiles (e.g. via base image tag or Bazel config flag)
- Image should be pushable to a registry (registry target TBD)

## Out of Scope (for now)

- Client container (service is the deployment unit)
- Other language variants
- Kubernetes manifests / Helm chart (separate ticket if needed)

## References

- `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/` — service binary
- `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/config/Config.kt` — config data classes and env var resolution
- `kotlin/src/main/resources/` — runtime YAML configs (will need to be embedded or mounted)
- `MODULE.bazel` — dependency management; container rules to be added here
