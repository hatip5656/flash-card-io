--liquibase formatted sql

--changeset flashcard:030 splitStatements:true
CREATE TABLE IF NOT EXISTS grammar_shuffle (
    id SERIAL PRIMARY KEY,
    word_id TEXT REFERENCES words(id) ON DELETE CASCADE,
    cefr_level TEXT NOT NULL,
    estonian TEXT NOT NULL,
    estonian_tokens TEXT NOT NULL,
    turkish_tokens TEXT NOT NULL,
    english_tokens TEXT NOT NULL,
    turkish TEXT NOT NULL,
    english TEXT NOT NULL
);

--changeset flashcard:031 splitStatements:true
CREATE INDEX IF NOT EXISTS idx_grammar_shuffle_level ON grammar_shuffle(cefr_level);
CREATE INDEX IF NOT EXISTS idx_grammar_shuffle_word ON grammar_shuffle(word_id);
