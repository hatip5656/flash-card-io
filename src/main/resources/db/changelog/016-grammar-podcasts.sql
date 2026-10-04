--liquibase formatted sql

--changeset grammar-podcasts:1
ALTER TABLE grammar_lessons ADD COLUMN IF NOT EXISTS podcast_id TEXT;

--changeset grammar-podcasts:2
CREATE TABLE IF NOT EXISTS scheduler_status (
    job_name TEXT PRIMARY KEY,
    last_run_at TIMESTAMP,
    last_success_at TIMESTAMP,
    last_duration_ms INTEGER,
    items_processed INTEGER DEFAULT 0,
    items_failed INTEGER DEFAULT 0,
    next_run_at TIMESTAMP,
    status TEXT DEFAULT 'idle'
);
