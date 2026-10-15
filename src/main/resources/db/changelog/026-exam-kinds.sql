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
UPDATE official_exams SET skill = 'reading' WHERE kind = 'exam' AND skill IS NULL;
CREATE INDEX IF NOT EXISTS idx_official_exams_kind ON official_exams(kind);
