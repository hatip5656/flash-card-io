--liquibase formatted sql

--changeset flashcard:018 splitStatements:true
-- Image cache columns on words table
ALTER TABLE words ADD COLUMN IF NOT EXISTS image_url TEXT;
ALTER TABLE words ADD COLUMN IF NOT EXISTS image_photographer TEXT;

--changeset flashcard:019 splitStatements:true
-- Quiz sessions table for horizontal scaling
CREATE TABLE IF NOT EXISTS quiz_sessions (
    chat_id BIGINT PRIMARY KEY,
    questions JSONB,
    answers JSONB DEFAULT '[]',
    words JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

--changeset flashcard:020 splitStatements:true
-- Per-subscriber word interaction tracking
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS feed_count INTEGER DEFAULT 0;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS crush_count INTEGER DEFAULT 0;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS last_quizzed_at TIMESTAMPTZ;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS last_fed_at TIMESTAMPTZ;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS last_crushed_at TIMESTAMPTZ;

--changeset flashcard:021 splitStatements:true
-- Mastered word tracking
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS mastered BOOLEAN DEFAULT FALSE;
ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS mastered_at TIMESTAMPTZ;
