--liquibase formatted sql

--changeset podcast-videos:1
CREATE TABLE IF NOT EXISTS podcast_videos (
    podcast_id    TEXT PRIMARY KEY,
    lesson_id     TEXT NOT NULL,
    status        TEXT NOT NULL,           -- queued, rendering, done, failed
    progress      REAL DEFAULT 0,
    error         TEXT,
    video_url     TEXT,
    requested_at  TIMESTAMPTZ DEFAULT NOW(),
    completed_at  TIMESTAMPTZ
);

CREATE INDEX idx_podcast_videos_status ON podcast_videos(status);
CREATE INDEX idx_podcast_videos_lesson ON podcast_videos(lesson_id);
