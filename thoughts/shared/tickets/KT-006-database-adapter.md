---
id: KT-006
title: Database adapter layer — DataSource config, connection pool, Flyway, Dagger wiring
area: kotlin, database, dagger, configuration
status: resolved
created: 2026-04-21
resolved_date: 2026-04-22
github_issue: 49
---

## Summary

Add the runtime database layer to the Kotlin service: a `DatabaseConfig` Hoplite config stanza, a HikariCP `DataSource` factory supporting SQLite (local/test) and PostgreSQL (production), automatic Flyway migration on startup, and a Dagger module that binds `DataSource` and JOOQ `DSLContext` for injection.

## Resolution

`DatabaseConfig` added to `Config.kt` and wired into `ServiceAppConfig`. HikariCP `DataSource` factory in `DatabaseSupport.kt` supports SQLite (local/test) and PostgreSQL (prod), with dialect auto-detection. Flyway runs on DataSource creation; migration location resolves via `RUNFILES_DIR` → `JAVA_RUNFILES` → `classpath:db/migrations` (deploy jar fallback). `BracketsDbModule` binds `DataSource` and `DSLContext`. All deps added to `service_lib`. `bazel test //kotlin/...` passes. Implemented in PR #48; deploy-jar classpath fallback added in PR #52.

## Original State

The service had Hoplite config (ServiceConfig, TelemetryConfig) and a Dagger application graph. No database wiring existed.

## Prerequisites

- DB-001 (migration files needed for Flyway to run)
- KT-002 (Dagger DI — already resolved)

## Goals / Acceptance Criteria

- [ ] `DatabaseConfig` data class added to `Config.kt` and wired into `ServiceAppConfig`:
  - `url: String` (JDBC URL; e.g. `jdbc:sqlite::memory:` for dev, `jdbc:postgresql://host/db` for prod)
  - `driver: String` (defaults: `org.sqlite.JDBC` for sqlite URLs, `org.postgresql.Driver` for pg)
  - `maxPoolSize: Int = 5`
- [ ] `service/application.yml` updated with default SQLite in-memory config for local dev
- [ ] Dependencies added: `com.zaxxer:HikariCP`, `org.xerial:sqlite-jdbc`, `org.postgresql:postgresql`
- [ ] `DatabaseModule` Dagger module:
  - Provides `DataSource` (HikariCP pool configured from `DatabaseConfig`)
  - Provides `DSLContext` (JOOQ, dialect inferred from JDBC URL)
  - Installed in `ApplicationGraphModule`
- [ ] Flyway runs on `DataSource` creation (before `DSLContext` is provided) — applies any pending migrations from `db/migrations/`
- [ ] `db/migrations/` accessible as a Bazel `data` dependency of the service binary
- [ ] In-memory SQLite used in existing `ApplicationGraphTest` (or a new test component) — no real DB required for tests
- [ ] SQLite schema constraint noted for implementers: keep DDL within SQLite's subset (no `RETURNING`, limited `ALTER TABLE`)
- [ ] `bazel test //kotlin/...` passes

## References

- Config types: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/config/Config.kt`
- Dagger graph: `kotlin/src/main/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/ApplicationGraphModule.kt`
- Existing test graph: `kotlin/src/test/kotlin/com/geekinasuit/polyglot/brackets/service/dagger/TestApplicationGraphModule.kt`
- HikariCP: https://github.com/brettwooldridge/HikariCP
- JOOQ SQLite dialect: `SQLDialect.SQLITE`

## Notes

Downstream:
- KT-007 (brackets configuration feature) injects `DSLContext` from this module
