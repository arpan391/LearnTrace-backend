# Manual legacy history recovery

`20261011_recover_legacy_student_history.sql` is a guarded, idempotent manual PostgreSQL recovery script. It is never run by application startup and contains no individual quiz records or per-student score values. It reads the latest live source rows within its transaction.

The existing owner confirmed that all 33 currently ownerless quiz attempts and all 15 current non-null `concept.score` values belong to `arpan@example.com`. The script checks that exact count, the existing target account, required schema and uniqueness constraint, and the confirmed Java Basics (67), Threads (50), OSI Model (0), and HTTP and HTTPS (100) source values. Any mismatch aborts the transaction.

Before running it:

1. Rotate the exposed database credential and verify the backend can connect using the replacement credential.
2. Create and verify a fresh backup of the intended production database.
3. Confirm the connected database and schema.
4. Run with `psql -v ON_ERROR_STOP=1 -f 20261011_recover_legacy_student_history.sql`.
5. Verify 15 target concept progress rows, the preserved values for any pre-existing progress, all 33 recovered attempts, and account isolation with read-only queries.

The transaction locks the source and target tables. It inserts missing progress only, preserving existing newer progress and timestamps. It assigns only ownerless attempts, and the target ID is resolved from the existing normalized email. It never modifies `concept.score` or another student's records. A rerun after successful recovery inserts no duplicate progress and makes no additional attempt ownership changes.
