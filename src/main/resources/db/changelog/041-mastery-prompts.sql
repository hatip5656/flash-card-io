--liquibase formatted sql

--changeset mastery-prompts:1
-- Correct answers per word, and the count at which to ask "did you master this word?" next.
-- Counting starts now: older answers are not stored per learner.
ALTER TABLE sent_words ADD COLUMN correct_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sent_words ADD COLUMN mastery_ask_at INTEGER NOT NULL DEFAULT 10;
