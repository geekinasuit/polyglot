---
id: ADR-001
title: Migration filename convention — timestamp prefix, shared file for Flyway and dbmate
status: accepted
date: 2026-04-21
---

## Context

The project needs a migration tooling strategy that works for both JVM (Flyway, used by the Kotlin service) and non-JVM contexts (dbmate, used for local development and inspection). Both tools must be able to run the same `.sql` files.

Flyway defaults to `V{version}__{description}.sql` (prefix `V`, double-underscore separator).
dbmate defaults to `{timestamp}_{description}.sql` (timestamp version, single underscore).

The files need to satisfy both parsers. Three options were considered:

1. **Flyway default naming** (`V1__create_bracket_pair.sql`) — dbmate cannot parse this without custom config.
2. **Separate files per tool** — duplication, drift risk.
3. **Timestamp naming with Flyway custom config** — Flyway supports `sqlMigrationPrefix=""` and `sqlMigrationSeparator="_"`; dbmate uses this format natively.

## Decision

Use timestamp-based filenames (`{YYYYMMDDHHmmss}_{description}.sql`) and configure Flyway with:
- `sqlMigrationPrefix = ""`
- `sqlMigrationSeparator = "_"`

Both tools operate on the same files in `db/migrations/`.

Include both `-- migrate:up` and `-- migrate:down` markers in every file. Flyway ignores these as SQL comments. dbmate requires the `-- migrate:down` marker (even if the block is empty); down migrations are intentionally not implemented — the block is left empty with a comment.

## Consequences

- Single source of truth: one file per migration, used by both tools.
- Flyway configuration must explicitly set `sqlMigrationPrefix` and `sqlMigrationSeparator` in `DatabaseModule` (KT-006).
- dbmate validated on the initial migration: `Applied: 20260421000000_create_bracket_pair.sql`.
- The empty `-- migrate:down` block is a documentation convention — no rollback SQL is written.
- DBA agent (pre-PR-3) should review and confirm this decision holds for more complex future migrations.
