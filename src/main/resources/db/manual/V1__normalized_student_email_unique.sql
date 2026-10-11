-- Run manually after reviewing duplicate rows. This transaction does not
-- rewrite or delete student records. It fails without changing data if two
-- existing rows normalize to the same email.
BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM student
        WHERE email IS NOT NULL
        GROUP BY lower(btrim(email))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate normalized student emails exist; resolve them before creating the unique index';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_student_email_normalized
    ON student (lower(btrim(email)));

COMMIT;
