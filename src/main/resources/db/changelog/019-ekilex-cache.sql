--liquibase formatted sql

--changeset flashcard:019 splitStatements:true
-- Cache of Ekilex word IDs that have been checked for CEFR levels.
-- Prevents re-checking the same words on every discovery run.
CREATE TABLE IF NOT EXISTS ekilex_checked_words (
    word_id INTEGER PRIMARY KEY,
    word_value TEXT,
    has_cefr BOOLEAN DEFAULT FALSE,
    checked_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ekilex_checked_nocefr ON ekilex_checked_words (has_cefr);
