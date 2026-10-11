-- MANUAL ONLY: reviewed production recovery from live PostgreSQL rows.
-- Never run automatically at application startup. Verify a fresh production backup first.
-- Run with psql -v ON_ERROR_STOP=1 -f this_file.sql against the intended database.
-- The sole operator confirmed ownership of the live ownerless history and shared concept scores.
-- No per-student score or quiz values are embedded in this repository.
BEGIN;

CREATE TEMP TABLE _lt_recovery_target (student_id bigint PRIMARY KEY) ON COMMIT DROP;
CREATE TEMP TABLE _lt_recovery_scores (concept_id bigint PRIMARY KEY, name text NOT NULL, subject text NOT NULL, score double precision NOT NULL) ON COMMIT DROP;
CREATE TEMP TABLE _lt_recovery_attempts (attempt_id bigint PRIMARY KEY) ON COMMIT DROP;
CREATE TEMP TABLE _lt_progress_before ON COMMIT DROP AS SELECT id, student_id, concept_id, score, updated_at FROM student_concept_progress WHERE false;

DO $$
DECLARE
  found_tables integer;
  found_columns integer;
  found_constraints integer;
BEGIN
  SELECT count(*) INTO found_tables FROM information_schema.tables
  WHERE table_schema=current_schema() AND table_name IN ('student','concept','quiz_attempt','student_concept_progress');
  IF found_tables <> 4 THEN RAISE EXCEPTION 'Expected four recovery tables; found %', found_tables; END IF;

  SELECT count(*) INTO found_columns FROM information_schema.columns
  WHERE table_schema=current_schema() AND (
    (table_name='student' AND column_name IN ('id','email')) OR
    (table_name='concept' AND column_name IN ('id','name','subject','score')) OR
    (table_name='quiz_attempt' AND column_name IN ('id','student_id')) OR
    (table_name='student_concept_progress' AND column_name IN ('id','student_id','concept_id','score','updated_at'))
  );
  IF found_columns <> 13 THEN RAISE EXCEPTION 'Recovery schema columns differ from expected mapping; found % of 13', found_columns; END IF;

  SELECT count(*) INTO found_constraints FROM (
    SELECT tc.constraint_name FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu ON kcu.constraint_schema=tc.constraint_schema AND kcu.constraint_name=tc.constraint_name AND kcu.table_schema=tc.table_schema AND kcu.table_name=tc.table_name
    WHERE tc.table_schema=current_schema() AND tc.table_name='student_concept_progress' AND tc.constraint_type='UNIQUE'
    GROUP BY tc.constraint_name
    HAVING count(*)=2 AND count(DISTINCT kcu.column_name)=2 AND sum(CASE WHEN kcu.column_name IN ('student_id','concept_id') THEN 1 ELSE 0 END)=2
  ) keys;
  IF found_constraints = 0 THEN RAISE EXCEPTION 'Missing unique student/concept progress constraint'; END IF;
END $$;

-- Prevent the live source rows or target progress from changing during validation and recovery.
LOCK TABLE student, concept, quiz_attempt, student_concept_progress IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO _lt_recovery_target (student_id)
SELECT id FROM student WHERE lower(trim(email))='arpan@example.com';
DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n FROM _lt_recovery_target;
  IF n <> 1 THEN RAISE EXCEPTION 'Expected exactly one existing arpan@example.com account; found %', n; END IF;
END $$;

-- Read the latest live scores at execution time. Exact sentinels guard the named scores
-- called out for preservation; the count guard stops if the reviewed source set changed.
INSERT INTO _lt_recovery_scores (concept_id,name,subject,score)
SELECT id,name,subject,score FROM concept WHERE score IS NOT NULL;
DO $$
BEGIN
  IF (SELECT count(*) FROM _lt_recovery_scores) <> 15 THEN RAISE EXCEPTION 'Expected 15 current non-null concept scores; found %', (SELECT count(*) FROM _lt_recovery_scores); END IF;
  IF NOT EXISTS (SELECT 1 FROM _lt_recovery_scores WHERE name='Java Basics' AND subject='Java Programming' AND score=67) THEN RAISE EXCEPTION 'Live Java Basics score is not the confirmed 67'; END IF;
  IF NOT EXISTS (SELECT 1 FROM _lt_recovery_scores WHERE name='Threads' AND subject='Operating Systems' AND score=50) THEN RAISE EXCEPTION 'Live Threads score differs from the confirmed source'; END IF;
  IF NOT EXISTS (SELECT 1 FROM _lt_recovery_scores WHERE name='OSI Model' AND subject='Computer Networks' AND score=0) THEN RAISE EXCEPTION 'Live OSI Model score differs from the confirmed source'; END IF;
  IF NOT EXISTS (SELECT 1 FROM _lt_recovery_scores WHERE name='HTTP and HTTPS' AND subject='Computer Networks' AND score=100) THEN RAISE EXCEPTION 'Live HTTP and HTTPS score differs from the confirmed source'; END IF;
END $$;

-- First run must see all 33 confirmed ownerless attempts. A rerun is a no-op only
-- after every ownerless attempt is gone and at least 33 attempts belong to the target.
INSERT INTO _lt_recovery_attempts (attempt_id)
SELECT id FROM quiz_attempt WHERE student_id IS NULL;
DO $$
DECLARE unowned_count integer; target_count integer;
BEGIN
  SELECT count(*) INTO unowned_count FROM _lt_recovery_attempts;
  SELECT count(*) INTO target_count FROM quiz_attempt q JOIN _lt_recovery_target t ON t.student_id=q.student_id;
  IF unowned_count NOT IN (0,33) THEN RAISE EXCEPTION 'Expected either 33 ownerless attempts or a completed recovery; found %', unowned_count; END IF;
  IF unowned_count=0 AND target_count < 33 THEN RAISE EXCEPTION 'No ownerless attempts remain but fewer than 33 are owned by the target'; END IF;
  IF unowned_count=33 AND (SELECT count(*) FROM quiz_attempt WHERE student_id IS NULL) <> 33 THEN RAISE EXCEPTION 'Ownerless attempt source changed during review'; END IF;
END $$;

INSERT INTO _lt_progress_before
SELECT p.id,p.student_id,p.concept_id,p.score,p.updated_at
FROM student_concept_progress p JOIN _lt_recovery_target t ON t.student_id=p.student_id;

-- Insert missing rows only. Existing student progress, including newer scores, is preserved.
INSERT INTO student_concept_progress (student_id,concept_id,score,updated_at)
SELECT t.student_id,s.concept_id,s.score,CURRENT_TIMESTAMP
FROM _lt_recovery_scores s CROSS JOIN _lt_recovery_target t
ON CONFLICT (student_id,concept_id) DO NOTHING;

-- Assign only currently ownerless attempts. Other students' rows are never changed.
UPDATE quiz_attempt q SET student_id=t.student_id
FROM _lt_recovery_target t
WHERE q.student_id IS NULL AND EXISTS (SELECT 1 FROM _lt_recovery_attempts a WHERE a.attempt_id=q.id);

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM _lt_progress_before b LEFT JOIN student_concept_progress p ON p.id=b.id
    WHERE p.id IS NULL OR p.student_id IS DISTINCT FROM b.student_id OR p.concept_id IS DISTINCT FROM b.concept_id OR p.score IS DISTINCT FROM b.score OR p.updated_at IS DISTINCT FROM b.updated_at)
    THEN RAISE EXCEPTION 'Pre-existing student progress changed during recovery'; END IF;
  IF (SELECT count(*) FROM _lt_recovery_scores s JOIN _lt_recovery_target t ON true JOIN student_concept_progress p ON p.student_id=t.student_id AND p.concept_id=s.concept_id) <> 15
    THEN RAISE EXCEPTION 'Not all 15 historical score concepts have target progress'; END IF;
  IF EXISTS (SELECT 1 FROM _lt_recovery_scores s JOIN _lt_recovery_target t ON true JOIN student_concept_progress p ON p.student_id=t.student_id AND p.concept_id=s.concept_id
    WHERE NOT EXISTS (SELECT 1 FROM _lt_progress_before b WHERE b.concept_id=s.concept_id) AND p.score IS DISTINCT FROM s.score)
    THEN RAISE EXCEPTION 'A newly recovered score differs from the live source'; END IF;
  IF EXISTS (SELECT 1 FROM quiz_attempt WHERE student_id IS NULL) THEN RAISE EXCEPTION 'Ownerless quiz attempts remain after recovery'; END IF;
  IF (SELECT count(*) FROM quiz_attempt q JOIN _lt_recovery_target t ON t.student_id=q.student_id) < 33 THEN RAISE EXCEPTION 'Fewer than 33 attempts are visible to the target'; END IF;
  IF (SELECT count(*) FROM _lt_recovery_attempts a JOIN quiz_attempt q ON q.id=a.attempt_id JOIN _lt_recovery_target t ON t.student_id=q.student_id) <> (SELECT count(*) FROM _lt_recovery_attempts)
    THEN RAISE EXCEPTION 'A staged historical attempt is not owned by the target'; END IF;
END $$;

SELECT 'live legacy scores verified' AS result,count(*) AS records FROM _lt_recovery_scores;
SELECT 'ownerless attempts linked on this run' AS result,count(*) AS records FROM _lt_recovery_attempts;
COMMIT;
