---
date: 2026-04-22
status: accepted
---

# ADR: Seed standard bracket pairs in the migration file

## Status

Accepted

## Context

The `bracket_pair` table requires three rows representing the standard bracket pairs:
`( )`, `[ ]`, `{ }`. These must be present in every environment (local dev, CI, production)
for the service to function correctly. Two strategies were considered:

1. **Inline in migration**: INSERT statements in `20260421000000_create_bracket_pair.sql`,
   run atomically with the schema creation by both Flyway and dbmate.
2. **Separate seed mechanism**: a separate SQL file, script, or application-level bootstrap
   that inserts the rows after schema creation, run independently of the migration tool.

## Decision

Insert the three standard pairs directly in the migration file, not via a separate seed
mechanism.

## Consequences

**Positive:**
- Every environment that runs migrations (Flyway or dbmate) automatically gets the standard
  pairs with no additional step. There is no risk of a "schema applied but seeds not run"
  state.
- The migration file is self-contained and auditable: a reader sees exactly what state the
  DB is in after version `20260421000000`.
- Test databases using H2 in-memory + Flyway get the seed data automatically, which is
  the correct baseline for most tests. Tests needing different data add rows or issue DELETEs
  in their own setup.

**Negative / accepted trade-offs:**
- Removing the standard pairs from a production deployment requires a new forward migration
  (DELETE statements) — they cannot be excluded at install time. This is acceptable because
  such a requirement is speculative and a targeted DELETE migration is trivial.
- The seed rows use explicit `id` values (1, 2, 3). Future inserts must not reuse these IDs.
  Convention: new standard pairs start at id 4 or higher.

**Not an issue:**
- Test reproducibility is not harmed: H2 in-memory databases are created fresh per test
  class, so the seeded state is always the post-migration baseline. Tests that need a
  different starting state manipulate data after Flyway runs.
