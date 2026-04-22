<!--COMPRESSED v1; source:2026-04-22-db-schema-design.md-->
§META
date:2026-04-22 researcher:claude(DBA-agent) tickets:DB-001,KT-005,KT-006,KT-007 status:complete
topic:bracket_pair schema design+migration strategy

§ABBREV
src=2026-04-22-db-schema-design.md
mig=db/migrations/20260421000000_create_bracket_pair.sql
h2url=jdbc:h2:mem:<name>;DB_CLOSE_DELAY=-1
rdir=RUNFILES_DIR/_main/db/migrations

§SUMMARY
DBA design doc; Architect reads this first. 7 decisions; fixed|provisional noted.
Migration file exists+confirmed correct. Flyway filename convention validated in PR2.
H2 in-memory for tests (not SQLite). Seed data inline in migration. Filegroup sufficient.

§SCHEMA
file:mig status:confirmed-correct no-corrections-needed
DDL:
  id INTEGER PRIMARY KEY — SQLite:rowid-alias(explicit-ids-in-seeds→no-SERIAL conflict); PG:plain-int; H2:native; safe:all-3
  open_char CHAR(1) NOT NULL — SQLite:text-affinity(length-advisory-not-enforced); PG:fixed-1; H2:native; app-enforces-length; safe:all-3
  close_char CHAR(1) NOT NULL — same as open_char
  enabled BOOLEAN NOT NULL DEFAULT TRUE — SQLite:INTEGER-0/1(BOOLEAN=affinity); PG:native; H2:native; seed-omits-col→default-TRUE(=1-on-sqlite); safe:all-3
avoid: RETURNING; SERIAL; GENERATED-ALWAYS; ADD-COLUMN-AFTER; stored-procs; ON-CONFLICT-DO-UPDATE
decision:fixed

§FILENAME_CONVENTION
format:YYYYMMDDHHMMSS_description.sql
flyway-config: sqlMigrationPrefix="" sqlMigrationSeparator="_"
flyway-parses: version=20260421000000 desc="create bracket pair"
evidence: PR2 validated; db/README.md documents; db/schema.sql shows successful dbmate run
  schema.sql: INSERT INTO schema_migrations VALUES ('20260421000000')
fallback(separate flyway/+dbmate/ dirs): NOT needed; single migrations/ dir confirmed
decision:fixed

§SEED_DATA
strategy: inline in migration (not separate seed mechanism)
rationale: reference-data(not user-data); every env needs all 3 pairs; atomic with schema create
test-reset: H2 in-memory per test-class→fresh DB; override via INSERT|DELETE in test-setup; no shared state
tradeoff: removing standard pairs requires forward migration(not install-time exclusion); acceptable(speculative requirement; trivial migration)
decision:fixed

§FUTURE_EVOLUTION
all-additive; no existing columns altered
sort_order INTEGER → new nullable|defaulted col in new migration
category VARCHAR → nullable col
locale VARCHAR|FK-to-locale-table → nullable col(BCP47) | FK
per-user-config → new user_bracket_pair join table; id is natural FK target
forecloses: nothing material; INTEGER PRIMARY KEY (not UUID) fine for small reference table
decision:provisional

§DBMATE_COMPAT
file ends: "-- migrate:down" (empty) — REQUIRED by dbmate even if no rollback SQL
dbmate: up-marker=section-start; down-marker=section-start(empty=no-rollback-defined; valid)
flyway: both markers=plain SQL comments; ignored; runs all non-comment SQL
no-separate-file-format needed
decision:fixed

§TEST_DATA_STRATEGY
db:H2 in-memory (NOT SQLite)
url-pattern: h2url
MODE=PostgreSQL: omitted(provisional; cross-platform DDL safe-subset→default mode avoids false PG-specific behavior; Architect|TDD-Designer may add if surfacing-PG-issues needed)
why-H2: already required dep(JOOQ DDLDatabase codegen); no new dep; in-process(no FS state); hermetic
why-not-sqlite: extra dep; FS state; no benefit over H2 here
pattern per test-class:
  1. create fresh DataSource with unique mem DB name
  2. apply Flyway(same mig files from //db:migrations) in @Before|beforeEach
  3. insert test-specific rows after Flyway
  4. no shared mutable state between test classes
CRITICAL: DB_CLOSE_DELAY=-1 required — H2 drops mem-DB on last-connection-close; HikariCP opens|closes independently; without this→DB dropped between Flyway+test query
flyway-location: filesystem:rdir (NOT classpath scan; Bazel runfiles not on classpath)
flyway-config(REQUIRED — without prefix+separator Flyway looks for V*__*.sql and finds nothing):
  .sqlMigrationPrefix("")
  .sqlMigrationSeparator("_")
  .locations("filesystem:$migrationsPath")  // resolved from RUNFILES_DIR
bazel: test targets need data=["//db:migrations"]
decision:fixed (except MODE=PostgreSQL which is provisional)

§BAZEL_FILEGROUP
target: //db:migrations
def: glob(["migrations/*.sql"]) visibility:public
ordering: glob→lexicographic; timestamp filenames→lexicographic=chronological; correct
subdirs: none currently; glob matches only migrations/*.sql(non-recursive); if subdirs added→update to migrations/**/*.sql
edge: non-migration .sql in migrations/→included+picked-up-by-Flyway; convention:only-migration-files in migrations/
usage: add to data=(not deps) of service binary+test targets+JOOQ codegen genrule
decision:fixed
