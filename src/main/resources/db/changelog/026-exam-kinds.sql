--liquibase formatted sql

--changeset exam-kinds:1 splitStatements:true
-- kind: 'exam' (the reading papers the learning plan and readiness use), 'screening' (placement),
-- 'diagnostic' and 'sample' (per-level, per-skill checks). scoring holds the rules the grader applies
-- beyond right/wrong counts: the screening gate and placement bands, or a level check's pass mark.
ALTER TABLE official_exams ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'exam';
ALTER TABLE official_exams ADD COLUMN IF NOT EXISTS skill TEXT;
ALTER TABLE official_exams ADD COLUMN IF NOT EXISTS variant INTEGER;
ALTER TABLE official_exams ADD COLUMN IF NOT EXISTS sort_order INTEGER;
ALTER TABLE official_exams ADD COLUMN IF NOT EXISTS scoring JSONB;
-- NULL = untimed. The reading papers get the limits the app used to hardcode.
ALTER TABLE official_exams ADD COLUMN IF NOT EXISTS time_limit_minutes INTEGER;
UPDATE official_exams SET skill = 'reading' WHERE kind = 'exam' AND skill IS NULL;
UPDATE official_exams SET time_limit_minutes = CASE cefr_level WHEN 'A2' THEN 40 WHEN 'B1' THEN 55 WHEN 'B2' THEN 70 END
  WHERE kind = 'exam' AND time_limit_minutes IS NULL;
-- The reading papers' pass mark, which the app and server used to hardcode as 60%.
UPDATE official_exams SET scoring = '{"passMark": 0.6}'::jsonb WHERE kind = 'exam' AND scoring IS NULL;
CREATE INDEX IF NOT EXISTS idx_official_exams_kind ON official_exams(kind);
