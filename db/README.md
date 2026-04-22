# Database Migrations

SQL migrations for the `bracket_pair` configuration table, shared between Flyway (JVM/Kotlin service) and dbmate (local dev and inspection).

## External Dependency: dbmate

**dbmate** is required for local migration management outside the JVM service.

Install:
```
brew install dbmate          # macOS
```
Other platforms: https://github.com/amacneil/dbmate#installation

This is a **pre-build requirement** for any workflow that uses dbmate directly (local dev, CI scripts that run dbmate). The Kotlin service uses Flyway internally and does not require dbmate at runtime or build time.

## Filename Convention

Migrations follow the timestamp format: `{YYYYMMDDHHmmss}_{description}.sql`

Example: `20260421000000_create_bracket_pair.sql`

This format is dbmate's native convention. Flyway is configured to match it via:
- `sqlMigrationPrefix = ""`
- `sqlMigrationSeparator = "_"`

See `thoughts/shared/decisions/2026-04-21-migration-filename-convention.md` for the rationale.

## File Structure

Each migration file contains both markers:
- `-- migrate:up` — SQL to apply (required by dbmate; Flyway runs everything in the `up` block)
- `-- migrate:down` — intentionally empty; down migrations are not implemented in this project

## Running Migrations (dbmate)

```bash
# SQLite (local dev / testing)
DATABASE_URL="sqlite:./local.db" dbmate --migrations-dir db/migrations migrate

# PostgreSQL (production)
DATABASE_URL="postgres://user:pass@host/dbname" dbmate --migrations-dir db/migrations migrate
```

## DDL Portability

All DDL in this directory must be compatible with three parsers:

| Engine | Context |
|---|---|
| SQLite | Runtime — local dev and integration tests |
| PostgreSQL | Runtime — production |
| H2 | Build-time only — JOOQ DDLDatabase codegen (KT-005) |

Safe DDL subset: `INTEGER PRIMARY KEY`, `CHAR(1)`, `BOOLEAN NOT NULL DEFAULT TRUE`.

Avoid: `RETURNING`, `SERIAL`, stored procedures, `ADD COLUMN ... AFTER`, `GENERATED ALWAYS`.

## Bazel

```
//db:migrations   — filegroup of all *.sql files; used as data= dep in the Kotlin service binary
```
