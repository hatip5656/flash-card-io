--liquibase formatted sql

--changeset scheduler-history:1
CREATE TABLE IF NOT EXISTS scheduler_runs (
    id          BIGSERIAL PRIMARY KEY,
    job_name    TEXT NOT NULL,
    status      TEXT NOT NULL,           -- success, failed
    started_at  TIMESTAMPTZ NOT NULL,
    duration_ms INTEGER,
    items_processed INTEGER DEFAULT 0,
    items_failed    INTEGER DEFAULT 0,
    message     TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_scheduler_runs_job ON scheduler_runs(job_name, created_at DESC);
