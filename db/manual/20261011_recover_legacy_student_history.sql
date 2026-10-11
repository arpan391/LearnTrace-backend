-- MANUAL ONLY: recovered-history migration prepared from the local MySQL backup.
-- Do not run automatically at application startup. Take a verified PostgreSQL backup first.
-- Run against the intended production database with psql -v ON_ERROR_STOP=1 -f this_file.sql.
-- The script aborts on schema, target-account, score, or attempt identity mismatches.
-- It restores only the 12 score rows and 29 exact attempt rows present in the backup.
BEGIN;

CREATE TEMP TABLE _lt_recovery_assert (ok boolean NOT NULL CHECK (ok)) ON COMMIT DROP;
CREATE TEMP TABLE _lt_recovery_scores (concept_id bigint PRIMARY KEY, name text NOT NULL, subject text NOT NULL, score double precision NOT NULL) ON COMMIT DROP;
CREATE TEMP TABLE _lt_recovery_attempts (attempt_id bigint PRIMARY KEY, attempted_at timestamp without time zone NOT NULL, concept text NOT NULL, correct_answers integer NOT NULL, score double precision NOT NULL, subject text NOT NULL, total_questions integer NOT NULL, wrong_answers integer NOT NULL) ON COMMIT DROP;
CREATE TEMP TABLE _lt_recovery_target (student_id bigint PRIMARY KEY) ON COMMIT DROP;

INSERT INTO _lt_recovery_scores (concept_id, name, subject, score) VALUES
(1, 'Arrays', 'Data Structures', 100),
(2, 'Linked List', 'Data Structures', 100),
(3, 'Stack', 'Data Structures', 100),
(4, 'Queue', 'Data Structures', 100),
(5, 'Trees', 'Data Structures', 100),
(6, 'Recursion', 'Data Structures', 100),
(7, 'Graphs', 'Data Structures', 100),
(8, 'SQL', 'DBMS', 86),
(9, 'Keys', 'DBMS', 100),
(26, 'Java Basics', 'Java Programming', 44),
(32, 'Python Basics', 'Python Programming', 100),
(38, 'Searching', 'Algorithms', 80);

INSERT INTO _lt_recovery_attempts (attempt_id, attempted_at, concept, correct_answers, score, subject, total_questions, wrong_answers) VALUES
(1, TIMESTAMP '2026-10-02 20:15:53.349456', 'Full Subject Test', 5, 100, 'Data Structures', 5, 0),
(2, TIMESTAMP '2026-10-02 20:20:18.229319', 'Arrays', 1, 50, 'Data Structures', 2, 1),
(3, TIMESTAMP '2026-10-02 20:20:41.045420', 'Full Subject Test', 4, 80, 'Data Structures', 5, 1),
(4, TIMESTAMP '2026-10-07 10:21:50.779326', 'Arrays', 2, 100, 'Data Structures', 2, 0),
(5, TIMESTAMP '2026-10-07 10:21:59.826633', 'Trees', 1, 100, 'Data Structures', 1, 0),
(6, TIMESTAMP '2026-10-07 10:26:27.907141', 'Trees', 1, 100, 'Data Structures', 1, 0),
(7, TIMESTAMP '2026-10-07 10:30:46.600970', 'Arrays', 2, 100, 'Data Structures', 2, 0),
(8, TIMESTAMP '2026-10-07 10:35:04.436959', 'Arrays', 2, 100, 'Data Structures', 2, 0),
(9, TIMESTAMP '2026-10-07 11:29:18.429417', 'SQL', 6, 86, 'DBMS', 7, 1),
(10, TIMESTAMP '2026-10-07 12:10:11.927759', 'Graphs', 6, 100, 'Data Structures', 6, 0),
(11, TIMESTAMP '2026-10-07 12:32:18.191623', 'Graphs', 0, 0, 'Data Structures', 2, 2),
(12, TIMESTAMP '2026-10-07 13:34:40.593337', 'Arrays', 0, 0, 'Data Structures', 1, 1),
(13, TIMESTAMP '2026-10-07 13:34:54.886002', 'Linked List', 0, 0, 'Data Structures', 1, 1),
(14, TIMESTAMP '2026-10-07 13:35:24.410439', 'Queue', 0, 0, 'Data Structures', 1, 1),
(15, TIMESTAMP '2026-10-07 13:37:03.921517', 'Queue', 0, 0, 'Data Structures', 1, 1),
(16, TIMESTAMP '2026-10-07 13:37:22.309298', 'Queue', 0, 0, 'Data Structures', 1, 1),
(17, TIMESTAMP '2026-10-07 13:39:29.221512', 'Queue', 1, 100, 'Data Structures', 1, 0),
(18, TIMESTAMP '2026-10-07 14:42:34.794974', 'Arrays', 3, 100, 'Data Structures', 3, 0),
(19, TIMESTAMP '2026-10-09 20:17:37.123991', 'Linked List', 1, 100, 'Data Structures', 1, 0),
(20, TIMESTAMP '2026-10-09 20:20:11.011164', 'Graphs', 1, 100, 'Data Structures', 1, 0),
(21, TIMESTAMP '2026-10-09 20:20:26.834213', 'Recursion', 0, 0, 'Data Structures', 1, 1),
(22, TIMESTAMP '2026-10-09 20:30:36.361339', 'Recursion', 0, 0, 'Data Structures', 1, 1),
(23, TIMESTAMP '2026-10-09 20:30:47.289872', 'Recursion', 1, 100, 'Data Structures', 1, 0),
(24, TIMESTAMP '2026-10-09 20:31:23.573342', 'Stack', 4, 100, 'Data Structures', 4, 0),
(25, TIMESTAMP '2026-10-09 20:32:03.015394', 'Keys', 4, 80, 'DBMS', 5, 1),
(26, TIMESTAMP '2026-10-09 20:37:20.463412', 'Python Basics', 3, 100, 'Python Programming', 3, 0),
(27, TIMESTAMP '2026-10-09 21:26:36.427502', 'Keys', 1, 100, 'DBMS', 1, 0),
(28, TIMESTAMP '2026-10-09 21:34:05.263909', 'Searching', 4, 80, 'Algorithms', 5, 1),
(29, TIMESTAMP '2026-10-10 14:15:18.759062', 'Java Basics', 4, 44, 'Java Programming', 9, 5);

-- Fail early unless the mapped production schema matches the current entities.
DO $$
DECLARE
  found_tables integer;
  found_columns integer;
  found_constraints integer;
BEGIN
  SELECT count(*) INTO found_tables
  FROM information_schema.tables
  WHERE table_schema = current_schema() AND table_name IN ('student','concept','quiz_attempt','student_concept_progress');
  IF found_tables <> 4 THEN RAISE EXCEPTION 'Expected four recovery tables in current schema; found %', found_tables; END IF;
  SELECT count(*) INTO found_columns
  FROM information_schema.columns
  WHERE table_schema = current_schema() AND (
    (table_name='student' AND column_name IN ('id','email')) OR
    (table_name='concept' AND column_name IN ('id','name','subject','score')) OR
    (table_name='quiz_attempt' AND column_name IN ('id','attempted_at','concept','correct_answers','score','subject','total_questions','wrong_answers','student_id')) OR
    (table_name='student_concept_progress' AND column_name IN ('id','student_id','concept_id','score','updated_at'))
  );
  IF found_columns <> 20 THEN RAISE EXCEPTION 'Recovery schema columns differ from the expected mapping; found % of 20', found_columns; END IF;
  SELECT count(*) INTO found_constraints FROM (
    SELECT tc.constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema=tc.constraint_schema
      AND kcu.constraint_name=tc.constraint_name
      AND kcu.table_schema=tc.table_schema
      AND kcu.table_name=tc.table_name
    WHERE tc.table_schema=current_schema()
      AND tc.table_name='student_concept_progress'
      AND tc.constraint_type='UNIQUE'
    GROUP BY tc.constraint_name
    HAVING count(*)=2
      AND count(DISTINCT kcu.column_name)=2
      AND sum(CASE WHEN kcu.column_name IN ('student_id','concept_id') THEN 1 ELSE 0 END)=2
  ) keys;
  IF found_constraints = 0 THEN RAISE EXCEPTION 'Missing unique student/concept progress constraint'; END IF;
END $$;

-- Keep the checked records stable until the verified assignment commits.
LOCK TABLE student, concept, quiz_attempt, student_concept_progress IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO _lt_recovery_target (student_id)
SELECT id FROM student WHERE lower(trim(email)) = 'arpan@example.com';
DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n FROM _lt_recovery_target;
  IF n <> 1 THEN RAISE EXCEPTION 'Expected exactly one existing arpan@example.com account; found %', n; END IF;
  IF (SELECT count(*) FROM _lt_recovery_scores) <> 12 THEN RAISE EXCEPTION 'Backup score manifest count changed'; END IF;
  IF (SELECT count(*) FROM _lt_recovery_attempts) <> 29 THEN RAISE EXCEPTION 'Backup attempt manifest count changed'; END IF;
  IF (SELECT count(*) FROM concept WHERE score IS NOT NULL) <> 12 THEN RAISE EXCEPTION 'Live shared score count differs from backup; investigate before recovery'; END IF;
  IF (SELECT count(*) FROM _lt_recovery_scores s JOIN concept c ON c.id=s.concept_id AND c.name=s.name AND c.subject=s.subject AND c.score=s.score) <> 12 THEN RAISE EXCEPTION 'Live concept IDs, names, subjects, or scores differ from backup'; END IF;
  IF (SELECT count(*) FROM _lt_recovery_attempts b JOIN quiz_attempt q ON q.id=b.attempt_id AND q.attempted_at=b.attempted_at AND q.concept=b.concept AND q.correct_answers=b.correct_answers AND q.score=b.score AND q.subject=b.subject AND q.total_questions=b.total_questions AND q.wrong_answers=b.wrong_answers WHERE q.student_id IS NULL OR q.student_id=(SELECT student_id FROM _lt_recovery_target)) <> 29 THEN RAISE EXCEPTION 'Live attempt identities or ownership differ from backup'; END IF;
  IF EXISTS (SELECT 1 FROM _lt_recovery_attempts b JOIN quiz_attempt q ON q.id=b.attempt_id WHERE q.student_id IS NOT NULL AND q.student_id<>(SELECT student_id FROM _lt_recovery_target)) THEN RAISE EXCEPTION 'A backup attempt is already assigned to another account'; END IF;
  IF EXISTS (SELECT 1 FROM quiz_attempt q WHERE q.student_id IS NULL AND NOT EXISTS (SELECT 1 FROM _lt_recovery_attempts b WHERE b.attempt_id=q.id AND b.attempted_at=q.attempted_at AND b.concept=q.concept AND b.correct_answers=q.correct_answers AND b.score=q.score AND b.subject=q.subject AND b.total_questions=q.total_questions AND b.wrong_answers=q.wrong_answers)) THEN RAISE EXCEPTION 'Unexpected ownerless quiz attempt exists; investigate before recovery'; END IF;
END $$;

CREATE TEMP TABLE _lt_progress_before ON COMMIT DROP AS
SELECT p.concept_id, p.score
FROM student_concept_progress p
JOIN _lt_recovery_target t ON t.student_id=p.student_id;

-- Insert only missing student/concept rows; existing newer progress is untouched.
INSERT INTO student_concept_progress (student_id, concept_id, score, updated_at)
SELECT t.student_id, c.id, s.score, CURRENT_TIMESTAMP
FROM _lt_recovery_scores s
JOIN concept c ON c.id=s.concept_id AND c.name=s.name AND c.subject=s.subject AND c.score=s.score
CROSS JOIN _lt_recovery_target t
WHERE NOT EXISTS (SELECT 1 FROM student_concept_progress p WHERE p.student_id=t.student_id AND p.concept_id=c.id);

-- Attach only exact backup attempts. Other null-owner rows cause preflight failure.
UPDATE quiz_attempt q SET student_id=(SELECT student_id FROM _lt_recovery_target)
WHERE q.student_id IS NULL AND EXISTS (SELECT 1 FROM _lt_recovery_attempts b WHERE b.attempt_id=q.id AND b.attempted_at=q.attempted_at AND b.concept=q.concept AND b.correct_answers=q.correct_answers AND b.score=q.score AND b.subject=q.subject AND b.total_questions=q.total_questions AND b.wrong_answers=q.wrong_answers);

DO $$
BEGIN
  IF (SELECT count(*) FROM _lt_recovery_scores s JOIN concept c ON c.id=s.concept_id JOIN _lt_recovery_target t ON true JOIN student_concept_progress p ON p.student_id=t.student_id AND p.concept_id=c.id WHERE (EXISTS (SELECT 1 FROM _lt_progress_before b WHERE b.concept_id=c.id) AND p.score IS NOT DISTINCT FROM (SELECT b.score FROM _lt_progress_before b WHERE b.concept_id=c.id)) OR (NOT EXISTS (SELECT 1 FROM _lt_progress_before b WHERE b.concept_id=c.id) AND p.score=s.score)) <> 12 THEN RAISE EXCEPTION 'Recovered progress failed verification or pre-existing progress changed'; END IF;
  IF (SELECT count(*) FROM _lt_recovery_attempts b JOIN quiz_attempt q ON q.id=b.attempt_id AND q.attempted_at=b.attempted_at AND q.concept=b.concept AND q.correct_answers=b.correct_answers AND q.score=b.score AND q.subject=b.subject AND q.total_questions=b.total_questions AND q.wrong_answers=b.wrong_answers JOIN _lt_recovery_target t ON q.student_id=t.student_id) <> 29 THEN RAISE EXCEPTION 'Recovered attempt ownership failed verification'; END IF;
END $$;

SELECT 'legacy scores verified' AS result, count(*) AS records FROM _lt_recovery_scores;
SELECT 'legacy attempts verified' AS result, count(*) AS records FROM _lt_recovery_attempts;
COMMIT;
