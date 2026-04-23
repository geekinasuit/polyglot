---
id: CI-005
title: macOS CI agent isolation — VMs or sandboxed environments
area: ci, buildkite, macos, infra
status: open
created: 2026-04-22
---

## Summary

macOS Buildkite steps currently run directly on the native macOS agent. This means builds share the same filesystem, Homebrew prefix, and tool versions as whatever is installed on the machine. A misconfigured or dirty agent environment can cause flaky or incorrect test results, and one build can pollute the next.

Investigate and implement a suitable isolation strategy for macOS CI steps — VMs (OrbStack, Tart, QEMU), ephemeral agent instances, or Bazel's own sandboxing — so that macOS builds are repeatable and self-contained.

## Current State

- macOS steps run natively on `reyy` (ARM64 dev machine) without containerization
- Buildkite agent is configured with `os: macos` queue tag
- Bazel sandboxing is in use (`--config=ci` likely enables it) but only covers Bazel actions, not pre-build setup or script steps
- No reset between builds; Homebrew packages and PATH are whatever the agent machine has at the time

## Goals / Acceptance Criteria

- [ ] Evaluate isolation options:
  - OrbStack VMs (already available on `reyy`) — spin up a fresh VM per build or per agent session
  - Tart (macOS VM runner purpose-built for CI, supports Apple Silicon)
  - Ephemeral Buildkite agent: start a fresh agent process per build via launchd
  - Rely on Bazel sandbox + explicit tool setup scripts (lighter; doesn't isolate pre-build steps)
- [ ] Choose and document the approach (ADR in `thoughts/shared/decisions/`)
- [ ] Implement: macOS CI steps run in an isolated environment; build does not inherit host state beyond what is explicitly provisioned
- [ ] Verify: running two builds back-to-back on a dirty host produces identical results
- [ ] Update CI-004 macOS env validation once isolation approach is chosen

## References

- Buildkite pipeline: `.buildkite/pipeline.yml` — `agents: os: macos` steps
- OrbStack VMs: already installed on `reyy` (`~/.orbstack/bin/orb`)
- Tart: https://github.com/cirruslabs/tart — macOS VM runner for Apple Silicon
- Bazel sandboxing: `.bazelrc` `--config=ci`
- infra repo: agent setup and launchd config for the macOS Buildkite agent

## Notes

Prereq for: CI-002 (cross-language e2e matrix) — that test suite will be sensitive to environment pollution.
Coordinate with CI-004 (env validation) — validation scripts should work inside whatever isolation layer is chosen.
