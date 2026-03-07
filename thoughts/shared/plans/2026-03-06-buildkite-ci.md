# Buildkite CI Pipeline Implementation Plan

## Overview

Create a Buildkite pipeline mirroring the existing 5-job GitHub Actions CI workflow. Builds run
on two platforms: Linux (via Docker plugin on the Mac agent) and macOS native. Mac native builds
use Bazel's built-in macOS seatbelt sandboxing (`sandbox-exec`). Caching uses Bazel's own disk
cache backed by local agent disk, with Docker volume mounts for Linux container steps.

## Current State Analysis

**GitHub Actions jobs (all on ubuntu-latest):**

| Job | Depends on | What it does |
|---|---|---|
| `bazel-build` | — | `bazel build //...` |
| `test` | build | `bazel test //...` |
| `test-go-exec-bazel` | build | `bazel run //golang/cmd/brackets` |
| `test-go-exec-make` | build | protoc + go install + `make go-test && make go-run` |
| `test-kotlin-exec-bazel` | build | starts gRPC service, runs client integration test |

**Relevant config:**
- Bazel 8.4.2 via bazelisk (`.bazelversion`)
- `.bazelrc`: `common --enable_workspace=true --java_runtime_version=21`
- No `.buildkite/` directory yet
- Mac Buildkite agent: running, default queue, no OS tags yet

## Desired End State

A fully working Buildkite pipeline at `.buildkite/pipeline.yml` that:
- Mirrors all 5 GitHub Actions jobs on Linux (Docker) + macOS native
- Uses the `docker#v5.11.0` plugin (open-source, official Buildkite plugin) for Linux steps
- Uses macOS seatbelt sandboxing for native Mac Bazel steps
- Uses Bazel disk cache via local disk (Docker volume mount for containers, fixed path for native)
- Has a dedicated script for the Kotlin integration test

**Verification:**
- Both platform builds succeed end-to-end in the Buildkite dashboard
- All test steps pass
- Bazel cache is warm on re-runs (second build noticeably faster than first)

## What We're NOT Doing

- No custom Docker image build/push pipeline (using `ubuntu:22.04` + inline bazelisk bootstrap;
  noted as a future improvement)
- No cloud cache backend (S3/GCS) — local disk only for now
- No Windows builds
- Not replacing GitHub Actions (both can run in parallel for now)
- Not adding `grpc_kotlin` bzlmod migration (separate concern)

## Key Discoveries

- `java_runtime_version=21` in `.bazelrc` is handled via Bazel's toolchain download — doesn't
  require JDK 21 pre-installed in the Docker image (Bazel fetches it). Only `curl`, `python3`,
  and `git` are strictly needed alongside bazelisk.
- The Kotlin integration test starts a background service and needs a shell wrapper script —
  this is unavoidable scripting but isolated in `.buildkite/scripts/kotlin-integration-test.sh`.
- macOS seatbelt sandboxing is supported via Bazel's `--sandbox_strategy=sandboxed` flag on
  macOS. This uses `sandbox-exec` with Bazel's built-in seatbelt profile.
- Bazelisk reads `.bazelversion` automatically — no `USE_BAZEL_VERSION` env var needed in Docker.
- Linux Docker steps and Mac native steps can share the same `--config=ci` flag; the underlying
  sandbox mechanism differs by OS but the flag is cross-platform compatible.

## Implementation Approach

Add a `ci` config to `.bazelrc` for sandboxing and disk cache flags. Create
`.buildkite/pipeline.yml` using `depends_on` step keys (not `wait` blocks) for finer-grained
dependency control — Linux and macOS build failures are independent. Linux steps use
`docker#v5.11.0` with `ubuntu:22.04`; Go-make step uses `golang:1.22-bookworm`. The Kotlin
integration test is wrapped in a standalone shell script shared by both platforms.

---

## Phase 1: Update `.bazelrc` with CI config

### Overview
Add a named `ci` config that enables macOS seatbelt sandboxing and sets a consistent disk cache
path. All pipeline steps pass `--config=ci`; native Mac and Linux-in-Docker both respect it.

### Changes Required

**File**: `.bazelrc`

Add a `build:ci` stanza at the end with:
- `--spawn_strategy=sandboxed` — activates `sandbox-exec` on macOS (seatbelt); on Linux
  inside Docker, activates Linux namespace sandboxing. Same flag, platform-appropriate behavior.
  Note: `--sandbox_strategy` was removed in Bazel 8; `--spawn_strategy` is the current form.
- `--disk_cache=/tmp/bazel-disk-cache` — consistent path used by all steps. Docker steps mount
  the host's cache directory to this container path; Mac native steps use the agent's `/tmp`.

### Success Criteria

#### Automated Verification:
- [x] `bazel build --config=ci //...` succeeds locally on macOS with no sandbox errors

#### Manual Verification:
- [ ] Sandboxing is visibly active: Bazel output should reference sandboxed strategy

---

## Phase 2: Create `.buildkite/pipeline.yml`

### Overview
9 steps total mirroring the 5 GitHub Actions jobs × 2 platforms, minus the go-make step which
is Linux-only (protoc + make toolchain, no value in duplicating natively).

### Pipeline structure

```
build-linux ──┬── test-linux ──┬── go-exec-bazel-linux
              │                ├── go-exec-make-linux
              │                └── kotlin-integration-linux

build-macos ──┴── test-macos ──┬── go-exec-bazel-macos
                               └── kotlin-integration-macos
```

Each step depends on its platform's build step via `depends_on: <key>`. Linux and macOS builds
are independent — a Linux failure does not block Mac tests and vice versa.

### File layout

Create `.buildkite/pipeline.yml` with a top-level `steps:` list. Each step has:
- `label` — emoji + description (`:bazel:`, `:apple:`, `:go:`, `:kotlin:`)
- `key` — used by `depends_on` in downstream steps
- `depends_on` — platform-appropriate build or test key
- `agents: { os: macos }` on Mac-native steps; no agent selector on Docker steps (runs on
  default queue, Docker plugin handles the container)
- `plugins: [docker#v5.11.0]` on Linux steps, with:
  - `image: ubuntu:22.04` for Bazel steps; `image: golang:1.22-bookworm` for go-make step
  - `volumes` mounting `/tmp/bk-bazel-cache` (host) → `/tmp/bazel-disk-cache` (container)
  - `volumes` mounting `/tmp/bk-go-cache` → `/go/pkg/mod` for the go-make step
- `command` — for most Bazel steps: `bazel <verb> --config=ci <target>`; for Docker steps,
  prefixed with a 3-line bazelisk bootstrap (install curl + python3 + git, download bazelisk
  binary to `/usr/local/bin/bazel`); the go-make step installs `protoc` + `protoc-gen-go` +
  `protoc-gen-go-grpc` then runs `make go-test && make go-run`; Kotlin integration steps
  delegate to `.buildkite/scripts/kotlin-integration-test.sh`

Top-level `env: BUILDKITE_CLEAN_CHECKOUT: "true"` ensures fresh checkout per build.

### Notes on Docker bootstrap
`ubuntu:22.04` requires bazelisk to be installed at runtime (~3 lines). This is the trade-off
for avoiding a custom Docker image. The bootstrap is the same across all `ubuntu:22.04` steps.
A future improvement (noted below) would replace this with a pre-built CI image.

### Success Criteria

#### Automated Verification:
- [ ] `buildkite-agent pipeline upload .buildkite/pipeline.yml` produces no YAML errors

#### Manual Verification:
- [ ] First full build passes end-to-end in the Buildkite dashboard
- [ ] Dependency graph shows correct ordering (test steps wait for build steps)
- [ ] Linux and Mac builds run independently (one failure doesn't cascade to the other platform)

---

## Phase 3: Create `.buildkite/scripts/kotlin-integration-test.sh`

### Overview
Extract the GitHub Actions inline Kotlin integration test into a standalone executable shell
script. It must work on both Linux (Docker container) and macOS (native). The script:
1. Bootstraps bazelisk if not already present (no-op on macOS where bazel is in PATH)
2. Builds `//kotlin:brackets_client` and `//kotlin:brackets_service` with `--config=ci`
3. Starts the service as a background process on a fixed port (19999)
4. Registers a `trap ... EXIT` for reliable cleanup on success, failure, or signal
5. Polls TCP until the service is ready (same timeout approach as GitHub Actions)
6. Runs two client invocations: one balanced input (expect "Brackets are balanced"), one
   unbalanced (expect "Brackets are NOT balanced"); exits non-zero on mismatch
7. Uses `/tmp/` for test input files to avoid working directory issues inside containers

Key improvement over GitHub Actions version: `trap` handles cleanup unconditionally, replacing
the fragile `if: always()` + env-var PID approach.

Make the script executable (`chmod +x`) as part of creation.

### Success Criteria

#### Automated Verification:
- [ ] `shellcheck .buildkite/scripts/kotlin-integration-test.sh` passes with no errors
- [ ] Script is executable: `test -x .buildkite/scripts/kotlin-integration-test.sh`

#### Manual Verification:
- [ ] Script runs to completion locally on macOS against pre-built binaries

---

## Phase 4: Buildkite Website Setup

### Overview
Create the pipeline on buildkite.com and configure the Mac agent with an OS tag so that
Mac-native pipeline steps are routed correctly.

### Steps

**4a. Tag the Mac agent with `os=macos`**

Edit the Buildkite agent config (typically
`/usr/local/etc/buildkite-agent/buildkite-agent.cfg` on macOS). Add `os=macos` to the `tags`
field alongside `queue=default`. Restart the agent. Verify the tag appears in the Buildkite
agent list in the dashboard.

**4b. Create the pipeline on buildkite.com**

New pipeline → connect to `geekinasuit/polyglot` → pipeline file path:
`.buildkite/pipeline.yml` → "Read steps from repository". Add the GitHub webhook URL that
Buildkite provides into GitHub repository settings → Webhooks.

**4c. Verify Docker is available on the Mac agent**

Run `docker info` and `docker run --rm ubuntu:22.04 echo hello` on the agent machine. The
Docker plugin requires Docker Desktop (or equivalent) to be running.

### Success Criteria

#### Manual Verification:
- [ ] Pipeline visible in Buildkite dashboard
- [ ] Pushing a commit triggers a build automatically
- [ ] Mac agent shows `os=macos` tag in the Buildkite agents list
- [ ] At least one full build passes end-to-end

---

## Future Improvements (Not in Scope)

1. **Custom CI Docker image** — Build a `geekinasuit/bazel-ci:8.4.2` image (Dockerfile in
   `.buildkite/`) with bazelisk, python3, and git pre-installed. Publish to Docker Hub or
   `ghcr.io`. Pipeline commands become single-line `bazel build --config=ci //...`.

2. **Remote cache** — Re-enable the commented-out `bigboi.local:8080` remote cache in `.bazelrc`
   under `build:ci`. Would dramatically speed up cross-agent builds.

3. **Linux agent** — Add a dedicated Linux Buildkite agent (tagged `os=linux`) for native Linux
   builds, replacing the Docker-on-Mac approach.

4. **Buildkite Test Analytics** — Upload Bazel build events for flakiness tracking.

## References

- GitHub Actions workflow: `.github/workflows/ci.yaml`
- Bazel config: `.bazelrc`, `.bazelversion` (8.4.2)
- Docker plugin: https://github.com/buildkite-plugins/docker-buildkite-plugin (Apache-2.0)
- Bazel sandboxing docs: https://bazel.build/docs/sandboxing
