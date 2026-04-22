# db — Database Migrations

This directory contains SQL migration files for the `bracket_pair` schema.

## Dual-tool strategy

Two migration tools are in use:

| Tool | Context | Why |
|---|---|---|
| **Flyway** | JVM runtime (Kotlin service startup) | Embedded in the service; runs migrations on startup via Dagger `DatabaseModule` |
| **dbmate** | Non-JVM local dev and CI | Standalone binary; works without a JVM |

Both tools read the same SQL files in `migrations/`.

## File format

Each migration file contains a `-- migrate:up` section for the forward migration.
A `-- migrate:down` marker is present but empty — rollbacks are done via new forward
migrations, not reversals.

```sql
-- migrate:up
CREATE TABLE ...;

-- migrate:down
```

The `-- migrate:up` marker is required by dbmate; Flyway ignores it as a SQL comment.
The empty `-- migrate:down` satisfies dbmate's requirement that a down block exists.
Flyway also ignores the down marker as a comment.

## Filename convention

Files use a timestamp-based naming convention:

```
YYYYMMDDHHMMSS_description.sql
```

Example: `20260421000000_create_bracket_pair.sql`

**Flyway configuration required** (applied in `DatabaseModule`):

```kotlin
Flyway.configure()
    .sqlMigrationPrefix("")      // no "V" prefix
    .sqlMigrationSeparator("_")  // single underscore separates version from description
    ...
```

This was validated: Flyway 10.x accepts timestamp filenames with these settings,
parsing `20260421000000_create_bracket_pair.sql` as version `20260421000000`,
description `create bracket pair`.

**dbmate** accepts this format natively — it expects `YYYYMMDDHHMMSS_description.sql`.

## Portability constraints

All DDL must be compatible with three parsers:

| Database | Usage |
|---|---|
| **SQLite** | Runtime — local dev default |
| **PostgreSQL** | Runtime — production |
| **H2** | Build time (JOOQ DDLDatabase) and test time (in-memory, hermetic tests) |

**Safe column types:** `INTEGER PRIMARY KEY`, `CHAR(1)`, `BOOLEAN NOT NULL DEFAULT TRUE`

**Avoid:** `RETURNING`, `SERIAL`, stored procedures, `ADD COLUMN ... AFTER`,
`GENERATED ALWAYS AS`, `ON CONFLICT` (PostgreSQL-only syntax).

## Bazel

The `//db:migrations` filegroup exposes all migration files as Bazel data dependencies:

```python
# db/BUILD.bazel
filegroup(
    name = "migrations",
    srcs = glob(["migrations/*.sql"]),
    visibility = ["//visibility:public"],
)
```

Add `//db:migrations` to the `data` attribute of any Bazel target that needs the
migrations at runtime (e.g. the service binary, integration tests).
