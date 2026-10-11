# Manual legacy history recovery

`20261011_recover_legacy_student_history.sql` is a one-time, manual PostgreSQL migration prepared from `C:\Users\Arpan Tiwary\Desktop\learntrace_backup.sql`. It is not a Flyway/Liquibase migration and is not run by application startup.

The backup contains 12 non-null shared concept scores and 29 quiz attempts. The owner confirmed that they alone created the pre-isolation learning history, so this migration links those exact backup rows to the existing account whose normalized email is `arpan@example.com`.

Before running it:

1. Take and verify a fresh backup of the intended production PostgreSQL database.
2. Confirm the connected database and schema. The script checks the required tables, columns, unique student/concept constraint, existing account, legacy score values, and every attempt's ID and stored fields.
3. Run with `psql -v ON_ERROR_STOP=1 -f 20261011_recover_legacy_student_history.sql`.
4. If any preflight check fails, stop and investigate the live/backup difference; do not edit the manifest just to make it pass.
5. Confirm the transaction completes and then verify the target's progress and attempt counts using read-only queries before removing the dashboard's unassigned-history notice.

The script only inserts missing student/concept progress rows, preserving any progress already present for the account. It associates the 29 exact backup attempts only while their owner is null, preserves their existing IDs and result fields, and never modifies `concept.score`. Unexpected ownerless attempts or an attempt already assigned to another student cause the transaction to abort. Re-running after a successful run is safe.

The migration has not been run against production. Production database access was not available during preparation.
