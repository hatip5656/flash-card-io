--liquibase formatted sql

--changeset podcast:1
CREATE TABLE IF NOT EXISTS podcasts (
    id              TEXT PRIMARY KEY,
    chat_id         BIGINT NOT NULL,
    title           TEXT NOT NULL,
    description     TEXT,
    script          JSONB,
    audio_cache_key TEXT,
    duration_seconds INTEGER DEFAULT 0,
    cefr_level      TEXT,
    status          TEXT NOT NULL DEFAULT 'pending',
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_podcasts_chat_id ON podcasts(chat_id);
CREATE INDEX IF NOT EXISTS idx_podcasts_chat_status ON podcasts(chat_id, status);
