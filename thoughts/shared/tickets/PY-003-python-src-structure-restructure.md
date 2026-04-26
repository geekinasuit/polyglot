---
id: PY-003
title: Restructure Python codebase to match Kotlin src/main and src/test layout
area: python, structure
status: open
created: 2026-04-25
---

## Summary

Restructure the Python codebase to follow Python community conventions for project layout, while maintaining Bazel compatibility. Avoid forcing JVM-style `src/main/python` structure if it conflicts with Python norms. Organize code into appropriate packages and test directories that look natural to Python developers.

## Current State

- Python code is currently located in `python/brackets_py_lib/`
- Contains `brackets_lib.py`, `brackets_lib_test.py`, and `BUILD.bazel`
- Structure is functional but not following Python community best practices

## Goals

- Adopt Python-standard project structure (e.g., package directories with `__init__.py`, separate `tests/` directory)
- Maintain Bazel build compatibility
- Ensure code organization is intuitive for Python developers
- Follow PEP 8 and common Python packaging patterns

## Acceptance Criteria

- All existing tests pass
- Bazel `py_test` and `py_library` targets work
- Code can be imported as `com.geekinasuit.polyglot.brackets.lib`
- No regressions in functionality

## References

- Kotlin structure: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/`
- AGENTS.md: "mirror the Kotlin project's structure and layout where it maps naturally"