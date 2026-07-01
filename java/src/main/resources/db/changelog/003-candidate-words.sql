--liquibase formatted sql

--changeset flashcard:016 splitStatements:true
CREATE TABLE IF NOT EXISTS candidate_words (
    id SERIAL PRIMARY KEY,
    estonian TEXT UNIQUE,
    english TEXT,
    turkish TEXT,
    cefr_level TEXT,
    status TEXT DEFAULT 'pending',
    discovered_at TIMESTAMPTZ DEFAULT NOW(),
    translated_at TIMESTAMPTZ
);

--changeset flashcard:017 splitStatements:true
CREATE TABLE IF NOT EXISTS candidate_sentences (
    id SERIAL PRIMARY KEY,
    candidate_id INTEGER REFERENCES candidate_words(id) ON DELETE CASCADE,
    estonian TEXT,
    english TEXT,
    turkish TEXT,
    sort_order INTEGER DEFAULT 0
);
