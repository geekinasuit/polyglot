---
id: KT-007
title: DB-backed bracket configuration — parameterize algorithm, repository, service integration
area: kotlin, database, feature
status: resolved
created: 2026-04-21
---

## Summary

Use the `bracket_pair` table (DB-001) to drive which bracket pairs the service recognizes. This requires: parameterizing the currently hardcoded bracket algorithm, implementing a JOOQ-backed repository that loads enabled pairs from the DB, and injecting that configuration into `BalanceServiceEndpoint`.

## Current State

`brackets.kt` hardcodes `closedParentheses` and `openParentheses` as module-level vals. The service cannot vary bracket pair definitions at runtime.

## Prerequisites

- DB-001 (schema and seed data)
- KT-005 (JOOQ generated classes)
- KT-006 (DataSource/DSLContext injectable)

## Goals / Acceptance Criteria

**Algorithm parameterization**
- [ ] `balancedBrackets(text: String)` gains an overload or parameter: `balancedBrackets(text: String, pairs: Map<Char, Char>)` where `pairs` maps close → open (consistent with existing map direction)
- [ ] Original zero-argument signature preserved or deprecated-via-default-arg using the existing hardcoded map, so existing tests continue to pass without modification

**Repository**
- [ ] `BracketPairRepository` class in a new `db` package under the service:
  - `@Inject constructor(dsl: DSLContext)`
  - `fun loadEnabledPairs(): Map<Char, Char>` — queries `bracket_pair` where `enabled = true`, maps `close_char → open_char`
  - Loaded once at `@ApplicationScope` (not per-request) — eager on startup is fine
- [ ] Dagger binding: `BracketPairRepository` bound at `ApplicationScope`; `Map<Char, Char>` provided from `loadEnabledPairs()` and injected wherever needed

**Service integration**
- [ ] `BalanceServiceEndpoint` injected with the loaded pair map and passes it to `balancedBrackets`
- [ ] If no enabled pairs exist in DB, service starts but returns an error response (not a crash)

**Tests**
- [ ] Unit test for `BracketPairRepository` using in-memory SQLite DSLContext (consistent with KT-006 test pattern)
- [ ] `BalanceServiceEndpoint` tests updated to supply a known pair map (not DB-dependent)
- [ ] At least one test verifies that disabling a pair in the test DB causes the algorithm to accept that pair's characters as plain text

**Build**
- [ ] `bazel test //kotlin/...` passes

## References

- Algorithm: `kotlin/src/main/kotlin/bracketskt/brackets.kt:7-13`
- Service endpoint: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/BalanceServiceEndpoint.kt`
- Dagger app scope: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/ApplicationGraphModule.kt`
- JOOQ generated tables: `com.geekinasuit.polyglot.brackets.db.jooq.Tables` (after KT-005)
