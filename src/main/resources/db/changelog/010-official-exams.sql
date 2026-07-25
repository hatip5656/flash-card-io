--liquibase formatted sql

--changeset flashcard:040 splitStatements:true
CREATE TABLE IF NOT EXISTS official_exams (
    id TEXT PRIMARY KEY,
    cefr_level TEXT NOT NULL,
    title TEXT NOT NULL,
    title_tr TEXT,
    title_en TEXT,
    source TEXT,
    questions JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

--changeset flashcard:041 splitStatements:true
CREATE TABLE IF NOT EXISTS exam_attempts (
    id SERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    exam_id TEXT REFERENCES official_exams(id),
    score INTEGER NOT NULL,
    total INTEGER NOT NULL,
    answers JSONB,
    completed_at TIMESTAMP DEFAULT NOW()
);

--changeset flashcard:042 splitStatements:true
CREATE INDEX IF NOT EXISTS idx_exam_attempts_user ON exam_attempts(chat_id);
CREATE INDEX IF NOT EXISTS idx_official_exams_level ON official_exams(cefr_level);
