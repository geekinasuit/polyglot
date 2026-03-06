---
date: 2026-03-05
status: open
priority: medium
area: bazel, build
---

# Fix: Cache configuration should not be in repo-controlled .bazelrc

## Summary

The repo-controlled `.bazelrc` contains two cache settings that should not be committed:

1. A machine-specific absolute path for the local disk cache:
   ```
   build --disk_cache /Users/cgruber/.cache/bazel
   ```
2. A commented-out remote cache endpoint:
   ```
   #build --remote_cache bigboi.local:8080
   ```

These settings are developer/machine-specific and will either break for other contributors or silently use the wrong paths on other machines.

## Fix

1. **Remove both cache lines from `.bazelrc`**
2. **Add `user.bazelrc` to `.gitignore`** — Bazel automatically imports `$(workspace_root)/user.bazelrc` if it exists; this is the standard place for per-developer local overrides
3. **Document** (e.g. in `README.md` or a `docs/` file) that developers should configure their cache in `user.bazelrc` or `~/.bazelrc`, for example:
   ```
   # user.bazelrc (gitignored, not committed)
   build --disk_cache /your/local/cache/path
   # build --remote_cache your-cache-host:port
   ```

## What stays in `.bazelrc`

Only settings that are correct for all contributors should remain:
- `common --enable_workspace=true --java_runtime_version=21`
- `build --enable_workspace`
- `info --enable_workspace`

## References

- `.bazelrc:5` — `build --disk_cache /Users/cgruber/.cache/bazel`
- `.bazelrc:6` — `#build --remote_cache bigboi.local:8080`
- Bazel docs on bazelrc: https://bazel.build/run/bazelrc
