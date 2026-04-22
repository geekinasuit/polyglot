<!--COMPRESSED v1; source:2026-04-22-seed-data-in-migration.md-->
§META
date:2026-04-22 status:accepted

§SUMMARY
Insert standard bracket pairs (( ) [ ] { }) directly in migration file, not via separate seed mechanism.

§DECISION
Inline INSERT statements in 20260421000000_create_bracket_pair.sql; run atomically with schema creation.

§RATIONALE
[+] Every env that runs migrations gets seed data automatically; no "schema applied but seeds not run" state
[+] Migration file is self-contained; auditable
[+] H2 in-memory test DBs get seed data automatically — correct baseline for most tests
[-] Removing standard pairs from prod requires a new forward migration (DELETE); accepted trade-off
[-] Seed rows use explicit IDs (1,2,3); future inserts must use id≥4; convention documented
