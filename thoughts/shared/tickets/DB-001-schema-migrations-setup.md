---
id: DB-001
title: Schema migration tooling and initial bracket_pair schema
area: database, migrations, cross-language
status: resolved
created: 2026-04-21
resolved_date: 2026-04-22
---

## Summary

Establish a canonical location for SQL schema files and configure a dual-tool migration strategy: **Flyway** (embedded Java API) for JVM-based implementations, **dbmate** (single static binary, no JVM dependency) for all other languages. Both tools read the same migration files. Down migrations are omitted throughout — they are of dubious value and create cross-tool compatibility issues.

## Resolution

`db/migrations/20260421000000_create_bracket_pair.sql` created with `bracket_pair` schema and seed data for the three standard pairs. Filename convention validated: dbmate timestamp format confirmed compatible with Flyway via `sqlMigrationPrefix=""` + `sqlMigrationSeparator="_"`. Migration files exposed as a Bazel `filegroup` (`//db:migrations`) and also bundled as classpath resources (`//db:migrations_resources`) for deploy-jar execution. Implemented in PR #43; deploy-jar classpath resource bundling added in PR #52.

## Original State

No SQL schema files or migration tooling existed. The bracket algorithm hardcoded its pair definitions in Kotlin source.

## Migration File Format

Files use dbmate's `-- migrate:up` marker with no `-- migrate:down` section. This works for both tools:

- **dbmate** treats `-- migrate:up` as a section marker and runs the SQL below it
- **Flyway** treats `-- migrate:up` as a plain SQL comment, ignores it, and runs the same SQL

Example:
```sql
-- migrate:up
CREATE TABLE bracket_pair (
    id    INTEGER  PRIMARY KEY,
    ...
);
```

## Filename Convention

dbmate uses `YYYYMMDDHHMMSS_description.sql`; Flyway defaults to `V1__description.sql`. These differ and must be reconciled before finalizing the approach. **Preferred path:** use dbmate's timestamp convention and configure Flyway to match:

```
flyway.sqlMigrationPrefix=   (empty)
flyway.sqlMigrationSeparator=_
```

With this config, Flyway parses `20260421000000_create_bracket_pair.sql` as version `20260421000000`, description `create_bracket_pair`. **This needs to be validated** — if Flyway rejects the timestamp-format version or the single-`_` separator causes problems, fall back to maintaining separate filename sets (same SQL content, different names in a `flyway/` and `dbmate/` subdirectory). Shared content is strongly preferred.

## Schema Constraints

All DDL must stay within SQLite's subset for portability across local (SQLite) and production (PostgreSQL) use:
- No `RETURNING` clause
- No stored procedures or triggers
- No `ADD COLUMN ... AFTER ...` (SQLite ignores column order anyway)
- Prefer `INTEGER PRIMARY KEY` over `SERIAL` (SQLite auto-increment compatibility)

## Goals / Acceptance Criteria

- [ ] `db/migrations/` directory created at repo root; brief `db/README.md` documents the dual-tool approach and file format conventions
- [ ] Filename convention validated: dbmate timestamp format confirmed compatible with Flyway (or fallback path documented and implemented)
- [ ] `20260421000000_create_bracket_pair.sql` (or `V1__...` if fallback) written:
  - `bracket_pair(id INTEGER PRIMARY KEY, open_char CHAR(1) NOT NULL, close_char CHAR(1) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE)`
  - Seed data: the three standard pairs `( )`, `[ ]`, `{ }` all enabled
  - Uses `-- migrate:up` marker; no `-- migrate:down` section
- [ ] Migration file validated against SQLite (dbmate or manual) and Flyway (Java API or CLI)
- [ ] `db/migrations/` exposed as a Bazel `filegroup` so Kotlin and future language targets can declare it as a `data` dependency

## References

- Bracket algorithm: `kotlin/src/main/kotlin/bracketskt/brackets.kt:7`
- Flyway: https://flywaydb.org — configurable prefix/separator via `FluentConfiguration`
- dbmate: https://github.com/amacneil/dbmate — single binary, supports SQLite + PostgreSQL

## Notes

Downstream:
- KT-005 (JOOQ codegen) reads the DDL from this migration via `jooq-meta-extensions` (DDLDatabase) — no live DB needed at build time
- KT-006 (DB adapter) runs Flyway programmatically on service startup against the configured DataSource
- Non-JVM language implementations (Go, Python, Rust, TS) run dbmate from CI or a local `make migrate` target
