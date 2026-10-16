--liquibase formatted sql

--changeset exam-attempt-verdicts:1 splitStatements:true
-- What the scoring rules made of an attempt (placement level, level check), and for goal exams the
-- readiness estimate for that skill just before the attempt, so predictions can be checked against results.
ALTER TABLE exam_attempts ADD COLUMN IF NOT EXISTS verdict JSONB;
ALTER TABLE exam_attempts ADD COLUMN IF NOT EXISTS predicted_score DOUBLE PRECISION;
CREATE INDEX IF NOT EXISTS idx_exam_attempts_exam ON exam_attempts(exam_id, completed_at);
