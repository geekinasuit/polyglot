---
id: CI-004
title: Buildkite CI environment validation and explicit tool installation
area: ci, buildkite, docker
status: open
created: 2026-04-22
---

## Summary

The Buildkite pipeline currently relies on pre-installed tools in `local/ci-agent:latest` (Linux) and the native macOS agent environment without explicitly validating or installing them. The former GitHub Actions workflow installed some tools explicitly (e.g. `protoc-gen-go`, `protoc-gen-go-grpc` via `go install`). This was lost when we removed GitHub Actions.

Add environment validation steps and/or explicit tool installation to the pipeline so that missing tools produce clear, actionable errors rather than silent wrong-PATH failures.

## Current State

- Linux steps run in `local/ci-agent:latest` — protoc Go plugins and other tools are presumed present but not verified
- macOS steps run on the native agent — no validation that required tools (bazel, go, protoc plugins, etc.) are installed and at expected versions
- `make go-test && make go-run` (Go exec via Make, Linux) was the only step in the old GHA that installed its own deps; Buildkite relies on the Docker image having them

## Goals / Acceptance Criteria

- [ ] Audit `local/ci-agent:latest` Dockerfile (in the `infra` repo) to confirm all tools used by Buildkite pipeline steps are present and at tested versions
- [ ] For each tool that is not guaranteed by the image: add an explicit install step in the pipeline or a shared setup script
- [ ] Add a lightweight "env check" step (or script) that prints tool versions at build start; helps diagnose version drift without failing the build
- [ ] macOS agent: document the required tools and versions in a runbook or agent-setup script; consider a `brew bundle` or similar idempotent setup
- [ ] Failing env check should produce a clear message naming the missing tool/version, not a cryptic downstream error

## References

- Buildkite pipeline: `.buildkite/pipeline.yml`
- Go Make step: `.buildkite/scripts/` (or inline in pipeline)
- Former GHA step that installed protoc plugins: `.github/workflows/ci.yaml` (now deleted) — `go install github.com/golang/protobuf/protoc-gen-go@latest` and `go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest`
- CI agent Docker image: `infra` repo — `local/ci-agent:latest` Dockerfile

## Notes

Downstream: CI-005 (macOS agent isolation) may change how macOS tools are managed; coordinate so env validation covers both Linux Docker and macOS VM/sandbox paths.
