---
id: KT-001
title: Kotlin gRPC server interceptor support not wired up
area: kotlin, grpc
status: open
created: 2026-03-05
---

# Incomplete: Kotlin gRPC server interceptor support not wired up

## Summary

The Kotlin gRPC server has scaffolding for `ServerInterceptor` support but no interceptors are implemented or passed. The hook exists but the line of development was not completed.

## Location

`kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/service.kt:34-39`

```kotlin
fun wrapService(
  bindableService: BindableService,
  vararg interceptors: ServerInterceptor,
): GrpcService {
  return GrpcService.builder().addService(bindableService).intercept(*interceptors).build()
}
```

The only call site (line 26) passes no interceptors:

```kotlin
val server = Server.builder().http(port).service(wrapService(BalanceServiceEndpoint())).build()
```

## Work Needed

1. Decide what interceptors are appropriate (e.g. request logging, error reporting, auth/metadata inspection)
2. Implement one or more `ServerInterceptor` implementations
3. Wire them into the `wrapService()` call in `BracketsService.run()`
4. Consider whether client-side interceptors (on `ManagedChannelBuilder`) are also needed

## Context

Discovered during codebase research on 2026-03-05. The `wrapService()` function signature already accepts `vararg interceptors: ServerInterceptor` and passes them through to Armeria's `GrpcService.builder().intercept()`, so the plumbing is ready — it just needs actual interceptor implementations.
