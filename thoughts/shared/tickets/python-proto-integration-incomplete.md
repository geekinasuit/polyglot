---
date: 2026-03-05
status: open
priority: low
area: python, protobuf
---

# Incomplete: Python proto integration not wired up

## Summary

The Python brackets library has proto support stubbed but not connected. The `example.proto`-generated code is available as a Bazel dependency, but the import and usage in `brackets_lib.py` are commented out.

## Location

`python/brackets_py_lib/brackets_lib.py:1,30-32`

```python
# import protobuf.example_pb2 as pb   # <-- commented out

def foo():
    # return pb.Something(1, "foo", None)  # <-- commented out
    return None
```

The Bazel target `//python/brackets_py_lib:example_proto_py` (`py_proto_library` wrapping `//protobuf`) exists and is declared as a dep of `:brackets_py_lib`, but the generated module is not imported at runtime.

## Work Needed

1. Determine correct import path for the generated Python proto module under Bazel
2. Uncomment and fix the import in `brackets_lib.py`
3. Implement `foo()` to return a real `Something` proto instance
4. Update `testFoo()` in `brackets_lib_test.py` (currently just `pass`) to assert the returned proto's fields

## Context

Discovered during codebase research on 2026-03-05. This is analogous to the working proto demos in Go (`Something{}` struct), Java (`Something.newBuilder()`), and Rust (`Something { ... }`).
