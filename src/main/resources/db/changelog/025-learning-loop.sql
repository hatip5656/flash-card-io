--liquibase formatted sql

--changeset learning-loop:1 splitStatements:true
-- One row per learner interaction with an outcome (answer, game word, lesson playback).
-- Feeds the learner model; nothing reads it for display directly.
CREATE TABLE IF NOT EXISTS learning_events (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    item_type TEXT NOT NULL,
    source TEXT,
    item_id TEXT,
    word_id TEXT,
    estonian TEXT,
    grammar_lesson_id TEXT,
    skill TEXT,
    question_type TEXT,
    is_correct BOOLEAN,
    user_answer TEXT,
    correct_answer TEXT,
    latency_ms INTEGER,
    meta JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_learning_events_user_time ON learning_events(chat_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_learning_events_user_type ON learning_events(chat_id, item_type, created_at DESC);

--changeset learning-loop:2 splitStatements:true
-- Per learner, per grammar lesson: how well the rule is known, and when to check it again.
CREATE TABLE IF NOT EXISTS grammar_mastery (
    chat_id BIGINT NOT NULL,
    grammar_lesson_id TEXT NOT NULL REFERENCES grammar_lessons(id) ON DELETE CASCADE,
    attempts INTEGER NOT NULL DEFAULT 0,
    correct INTEGER NOT NULL DEFAULT 0,
    exam_attempts INTEGER NOT NULL DEFAULT 0,
    exam_misses INTEGER NOT NULL DEFAULT 0,
    mastery REAL NOT NULL DEFAULT 0.5,
    last_practiced_at TIMESTAMPTZ,
    next_check_at TIMESTAMPTZ,
    PRIMARY KEY (chat_id, grammar_lesson_id)
);

--changeset learning-loop:3 splitStatements:true
-- What each official exam question tests, so a miss can be traced to a skill, grammar rule or words.
CREATE TABLE IF NOT EXISTS exam_question_tags (
    exam_id TEXT NOT NULL REFERENCES official_exams(id) ON DELETE CASCADE,
    question_index INTEGER NOT NULL,
    skill TEXT,
    grammar_lesson_ids JSONB NOT NULL DEFAULT '[]',
    key_words JSONB NOT NULL DEFAULT '[]',
    PRIMARY KEY (exam_id, question_index)
);

--changeset learning-loop:4 splitStatements:true
ALTER TABLE exam_attempts ADD COLUMN IF NOT EXISTS sections JSONB;
ALTER TABLE exam_attempts ADD COLUMN IF NOT EXISTS duration_seconds INTEGER;
ALTER TABLE exam_attempts ADD COLUMN IF NOT EXISTS server_graded BOOLEAN NOT NULL DEFAULT FALSE;

--changeset learning-loop:5 splitStatements:true
-- Teaching order within a level (prerequisites first); lessons without one sort after, by id.
ALTER TABLE grammar_lessons ADD COLUMN IF NOT EXISTS sort_order INTEGER;
UPDATE grammar_lessons SET sort_order = 1 WHERE id = 'a1-personal-pronouns';
UPDATE grammar_lessons SET sort_order = 2 WHERE id = 'a1-olema-present';
UPDATE grammar_lessons SET sort_order = 3 WHERE id = 'a1-nominative-case';
UPDATE grammar_lessons SET sort_order = 4 WHERE id = 'a1-numbers';
UPDATE grammar_lessons SET sort_order = 5 WHERE id = 'a1-question-words';
UPDATE grammar_lessons SET sort_order = 6 WHERE id = 'a1-basic-word-order';
UPDATE grammar_lessons SET sort_order = 7 WHERE id = 'a1-negation';
UPDATE grammar_lessons SET sort_order = 8 WHERE id = 'a1-present-tense';
UPDATE grammar_lessons SET sort_order = 9 WHERE id = 'a1-possessive';
UPDATE grammar_lessons SET sort_order = 10 WHERE id = 'a1-genitive-case';
UPDATE grammar_lessons SET sort_order = 11 WHERE id = 'a1-plural-formation';
UPDATE grammar_lessons SET sort_order = 12 WHERE id = 'a1-partitive-case';
UPDATE grammar_lessons SET sort_order = 13 WHERE id = 'a1-adjective-agreement';
UPDATE grammar_lessons SET sort_order = 14 WHERE id = 'a1-telling-time';
UPDATE grammar_lessons SET sort_order = 15 WHERE id = 'a1-adverbs-time-place';
UPDATE grammar_lessons SET sort_order = 16 WHERE id = 'a1-comitative-abessive';
UPDATE grammar_lessons SET sort_order = 17 WHERE id = 'a1-ma-da-infinitive';
UPDATE grammar_lessons SET sort_order = 18 WHERE id = 'a1-clothing-locations';
UPDATE grammar_lessons SET sort_order = 1 WHERE id = 'a2-numbers-11-100';
UPDATE grammar_lessons SET sort_order = 2 WHERE id = 'a2-conjunctions';
UPDATE grammar_lessons SET sort_order = 3 WHERE id = 'a2-inessive-elative';
UPDATE grammar_lessons SET sort_order = 4 WHERE id = 'a2-illative-case';
UPDATE grammar_lessons SET sort_order = 5 WHERE id = 'a2-allative-adessive-ablative';
UPDATE grammar_lessons SET sort_order = 6 WHERE id = 'a2-past-simple';
UPDATE grammar_lessons SET sort_order = 7 WHERE id = 'a2-frequency';
UPDATE grammar_lessons SET sort_order = 8 WHERE id = 'a2-consonant-gradation';
UPDATE grammar_lessons SET sort_order = 9 WHERE id = 'a2-comparatives';
UPDATE grammar_lessons SET sort_order = 10 WHERE id = 'a2-obligation';
UPDATE grammar_lessons SET sort_order = 11 WHERE id = 'a2-ma-da-infinitive';
UPDATE grammar_lessons SET sort_order = 12 WHERE id = 'a2-postpositions';
UPDATE grammar_lessons SET sort_order = 13 WHERE id = 'a2-present-perfect';
UPDATE grammar_lessons SET sort_order = 14 WHERE id = 'a2-reflexive-verbs';
UPDATE grammar_lessons SET sort_order = 1 WHERE id = 'b1-verb-rection';
UPDATE grammar_lessons SET sort_order = 2 WHERE id = 'b1-translative-case';
UPDATE grammar_lessons SET sort_order = 3 WHERE id = 'b1-terminative-essive-abessive';
UPDATE grammar_lessons SET sort_order = 4 WHERE id = 'b1-consonant-gradation-advanced';
UPDATE grammar_lessons SET sort_order = 5 WHERE id = 'b1-conditional';
UPDATE grammar_lessons SET sort_order = 6 WHERE id = 'b1-relative-clauses';
UPDATE grammar_lessons SET sort_order = 7 WHERE id = 'b1-indirect-questions';
UPDATE grammar_lessons SET sort_order = 8 WHERE id = 'b1-purpose-clauses';
UPDATE grammar_lessons SET sort_order = 9 WHERE id = 'b1-passive';
UPDATE grammar_lessons SET sort_order = 10 WHERE id = 'b1-participle-constructions';
UPDATE grammar_lessons SET sort_order = 11 WHERE id = 'b1-reported-speech';
UPDATE grammar_lessons SET sort_order = 1 WHERE id = 'b2-imperative';
UPDATE grammar_lessons SET sort_order = 2 WHERE id = 'b2-object-rules';
UPDATE grammar_lessons SET sort_order = 3 WHERE id = 'b2-pluperfect';
UPDATE grammar_lessons SET sort_order = 4 WHERE id = 'b2-nud-tud-participles';
UPDATE grammar_lessons SET sort_order = 5 WHERE id = 'b2-comitative-case';
UPDATE grammar_lessons SET sort_order = 6 WHERE id = 'b2-clause-connectors';
UPDATE grammar_lessons SET sort_order = 7 WHERE id = 'b2-verb-chains';
UPDATE grammar_lessons SET sort_order = 8 WHERE id = 'b2-quotative-mood';
UPDATE grammar_lessons SET sort_order = 9 WHERE id = 'b2-word-formation';
UPDATE grammar_lessons SET sort_order = 10 WHERE id = 'b2-word-order-advanced';
