--liquibase formatted sql

--changeset exam-goals:1 splitStatements:true
-- What each exam is to the learning plan: 'goal' = the exams the learner is working toward (taken in full,
-- never shown in the feed), 'practice' = exams the feed draws questions from, NULL = not part of the plan
-- (e.g. the placement tests).
ALTER TABLE official_exams ADD COLUMN IF NOT EXISTS plan_role TEXT;
UPDATE official_exams SET plan_role = 'goal' WHERE kind = 'sample' AND plan_role IS NULL;
UPDATE official_exams SET plan_role = 'practice' WHERE kind IN ('diagnostic', 'exam') AND plan_role IS NULL;
CREATE INDEX IF NOT EXISTS idx_official_exams_plan_role ON official_exams(plan_role, cefr_level);

-- The level whose goal exams the learner is working toward; set by the placement test or by the learner.
-- Without a row the plan falls back to the learner's profile level.
CREATE TABLE IF NOT EXISTS learner_goals (
    chat_id BIGINT PRIMARY KEY,
    target_level TEXT NOT NULL,
    source TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
