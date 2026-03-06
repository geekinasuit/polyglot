---
date: 2026-03-05
status: open
priority: low
area: golang, bazel
---

# Bug: `go_grpc_library` target wraps wrong proto source

## Summary

The `go_grpc_library` target in `golang/pkg/libs/brackets/BUILD.bazel:35` references `//protobuf` (which resolves to the `example.proto` target — the `Something` message), but it should reference `//protobuf:balance_rpc` (the `brackets_service.proto` — the `BalanceBrackets` gRPC service definition).

## Location

`golang/pkg/libs/brackets/BUILD.bazel:35-41`

```python
go_grpc_library(
    name = "grpc",
    # importpath = "github.com/geekinasuit/polyglot/golang/pkg/services/brackets/proto",
    protos = ["//protobuf"],   # <-- should be //protobuf:balance_rpc
    deps = ["@org_golang_google_grpc//:go_default_library"],
)
```

Note: the `importpath` is also commented out, suggesting this target is a placeholder/incomplete.

## Expected Fix

- Change `protos = ["//protobuf"]` to `protos = ["//protobuf:balance_rpc"]`
- Uncomment and set `importpath = "github.com/geekinasuit/polyglot/golang/pkg/services/brackets/proto"`
- This is a prerequisite for implementing a Go gRPC client/server (future work)

## Context

Discovered during codebase research on 2026-03-05. The Go gRPC client/server implementation is not yet written; this fix should be applied when that work begins.
