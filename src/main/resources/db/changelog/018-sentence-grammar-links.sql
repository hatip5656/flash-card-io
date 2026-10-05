--liquibase formatted sql

--changeset sentence-grammar:1
CREATE TABLE IF NOT EXISTS sentence_grammar_links (
    id SERIAL PRIMARY KEY,
    word_id TEXT NOT NULL,
    sentence_estonian TEXT NOT NULL,
    grammar_lesson_id TEXT NOT NULL REFERENCES grammar_lessons(id),
    confidence REAL DEFAULT 1.0,
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    created_by TEXT DEFAULT 'manual'
);

CREATE INDEX IF NOT EXISTS idx_sgl_word ON sentence_grammar_links(word_id);
CREATE INDEX IF NOT EXISTS idx_sgl_grammar ON sentence_grammar_links(grammar_lesson_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sgl_unique ON sentence_grammar_links(word_id, sentence_estonian, grammar_lesson_id);
