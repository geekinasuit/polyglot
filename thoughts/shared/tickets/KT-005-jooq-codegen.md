---
id: KT-005
title: JOOQ integration and DDL-based code generation for Kotlin
area: kotlin, database, jooq, bazel
status: resolved
created: 2026-04-21
resolved_date: 2026-04-22
---

## Summary

Add JOOQ and its Kotlin extensions to the Kotlin build, configure code generation from the Flyway DDL migration files (no live database required at build time), and integrate the codegen step into the Bazel build.

## Resolution

`org.jooq:jooq`, `org.jooq:jooq-kotlin`, and `org.jooq:jooq-meta-extensions` added via bzlmod `maven.install`. `JooqCodegenRunner.kt` (`//kotlin/.../db/codegen:jooq_codegen_runner_lib`) runs DDLDatabase codegen against the migration SQL at build time. Bazel `genrule` (`//kotlin:jooq_generated_srcs`) produces `Tables.java`, `tables/BracketPair.java`, `tables/records/BracketPairRecord.java`, `Keys.java`, `DefaultCatalog.java`, `DefaultSchema.java`, exposed as `//kotlin:jooq_db_lib`. Generated files are `.gitignore`d. Implemented in PR #45.

## Original State

No JOOQ dependency existed. DB-001 was to provide the schema DDL in `db/migrations/`.

## Prerequisites

- DB-001 (schema DDL must exist before codegen can run)

## Goals / Acceptance Criteria

- [ ] `org.jooq:jooq` and `org.jooq:jooq-kotlin` added to `kotlin/pom.xml` (or Maven central via bzlmod `maven.install`)
- [ ] JOOQ code generation configured to run from DDL files via `jooq-meta-extensions` (DDLDatabase) — avoids requiring a live DB at build time, which preserves Bazel hermeticity
- [ ] Generated sources target package: `com.geekinasuit.polyglot.brackets.db.jooq`
- [ ] Bazel: a genrule or custom rule that runs JOOQ codegen and exposes generated sources as a `kt_jvm_library` target — generated files must be `.gitignore`d (consistent with proto codegen policy)
- [ ] Codegen produces at minimum: `Tables`, `tables.BracketPair`, `tables.records.BracketPairRecord`
- [ ] Kotlin extensions (`jooq-kotlin`) usable — coroutine-friendly `fetchOne`, `map` etc.
- [ ] Bazel build passes (`bazel build //kotlin/...`)

## References

- Schema: `db/migrations/<migration-file>` — see DB-001 for resolved filename convention (timestamp vs. `V1__` TBD)
- Proto codegen pattern to follow: `kotlin/BUILD.bazel` — `kt_jvm_proto_library` + generated sources in `_deploy.jar`; same `.gitignore` treatment
- JOOQ DDL codegen: https://www.jooq.org/doc/latest/manual/code-generation/codegen-ddl/
- JOOQ Kotlin extensions: https://www.jooq.org/doc/latest/manual/sql-building/kotlin-sql-building/

## Notes

Downstream:
- KT-007 (configuration feature) uses the generated `BracketPair` table classes
